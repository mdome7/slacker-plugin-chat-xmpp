package com.labs2160.slacker.plugin.chat.xmpp.hipchat;

import com.labs2160.slacker.api.Endpoint;
import com.labs2160.slacker.api.Resource;
import com.labs2160.slacker.api.SlackerException;
import com.labs2160.slacker.api.response.SlackerOutput;
import com.labs2160.slacker.api.response.TextOutput;
import com.labs2160.slacker.plugin.chat.xmpp.OutputUtil;
import com.labs2160.slacker.plugin.chat.xmpp.XMPPResource;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

public class HipChatEndpoint implements Endpoint {

    private final static Logger logger = LoggerFactory.getLogger(HipChatEndpoint.class);

    private XMPPResource xmpp;

    private Map<String, Chat> people;

    private Map<String, MultiUserChat> rooms;

    public HipChatEndpoint() {
        people = new HashMap<>();
        rooms = new HashMap<>();
    }

    @Override
    public void setComponents(Map<String, Resource> resources, Properties config) {
        this.xmpp = (XMPPResource) resources.get(config.getProperty("XMPPResourceRef"));

        final String peopleList = config.getProperty("people", "");
        logger.debug("People: {}", peopleList);
        StringTokenizer st = new StringTokenizer(peopleList, ", "); // comma-separated
        while(st.hasMoreTokens()) {
            people.put(st.nextToken(), null);
        }

        final String mucRooms = config.getProperty("mucRooms", "");
        logger.debug("Rooms: {}", mucRooms);
        st = new StringTokenizer(mucRooms, ", "); // comma-separated
        while(st.hasMoreTokens()) {
            rooms.put(st.nextToken(), null);
        }
    }

    @Override
    public boolean deliverResponse(SlackerOutput output) throws SlackerException {
        boolean success = false;
        Message msg = createResponseMessage(output);

        success |= deliverToRooms(msg);
        success |= deliverToPeople(msg);

        return success;
    }

    private boolean deliverToPeople(Message msg) {
        boolean success = true;
        if (!people.isEmpty()) logger.debug("Delivering message to people: {}", people.keySet());
        for (String person : people.keySet()) {
            try {
                Chat chat = people.get(person);

                if (chat == null) {
                    chat = ChatManager.getInstanceFor(xmpp.getConnection())
                            .createChat(person);
                    logger.info("Starting chat with {}", person);
                    people.put(person, chat);
                }
                chat.sendMessage(msg);

            } catch (Exception e) {
                success = false;
                logger.warn("Could not deliver message to person \"{}\" - {}", person, e.getMessage());
            }
        }
        return success;
    }

    private boolean deliverToRooms(Message msg) {
        boolean success = true;
        if (!rooms.isEmpty()) logger.debug("Delivering message to rooms {}", rooms.keySet());
        for (String roomId : rooms.keySet()) {
            try {
                MultiUserChat chat = rooms.get(roomId);
                if (chat == null) {
                    MultiUserChatManager mucm = MultiUserChatManager.getInstanceFor(xmpp.getConnection());
                    chat = mucm.getMultiUserChat(roomId + "@" + xmpp.getMucDomain());
                    logger.info("Joining room {}", roomId);
                    rooms.put(roomId, chat);
                }
                if (!chat.isJoined()) {
                    try {
                        chat.join(xmpp.getMucNickname());
                    } catch (SmackException e) {
                        logger.warn("Could not join room \"{}\" - {}", roomId, e.getMessage());
                    }
                }
                chat.sendMessage(msg);

            } catch (Exception e) {
                success = false;
                logger.warn("Could not deliver message to room \"{}\" - {}", roomId, e.getMessage());
            }
        }
        return success;
    }

    private Message createResponseMessage(SlackerOutput output) {
        Message responseMsg = new Message();
        if (output instanceof TextOutput) {
            XHTMLExtension xhtmlExtension = new XHTMLExtension();
            TextOutput to = (TextOutput) output;
            responseMsg.setBody(to.getMessage());
            String html = OutputUtil.plainTextToJabberHtml(to.getMessage());
            logger.debug(html);
            xhtmlExtension.addBody(html);
            logger.debug("Message: {}", html);
            responseMsg.addExtension(xhtmlExtension);
        } else {
            responseMsg.setBody("Error - response type " + output.getClass().getSimpleName() + " not yet supported");
        }

        return responseMsg;
    }
}