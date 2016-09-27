package com.labs2160.slacker.plugin.chat.xmpp.hipchat;

import com.labs2160.slacker.api.Endpoint;
import com.labs2160.slacker.api.Resource;
import com.labs2160.slacker.api.SlackerException;
import com.labs2160.slacker.api.SlackerResponse;
import com.labs2160.slacker.plugin.chat.xmpp.XMPPResource;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

public class HipChatEndpoint implements Endpoint {

    private final static Logger logger = LoggerFactory.getLogger(HipChatCollector.class);

    private XMPPResource xmpp;

    private Map<String, MultiUserChat> rooms;

    public HipChatEndpoint() { rooms = new HashMap<>(); }

    @Override
    public void setComponents(Map<String, Resource> resources, Properties config) {
        this.xmpp = (XMPPResource) resources.get(config.getProperty("XMPPResourceRef"));

        StringTokenizer st = new StringTokenizer(config.getProperty("mucRooms", ""), ", "); // comma-separated
        while(st.hasMoreTokens()) {
            rooms.put(st.nextToken(), null);
        }
    }

    @Override
    public boolean deliverResponse(SlackerResponse response) throws SlackerException {
        logger.info("Delivering message '{}' to rooms {}", response.getMessage(), rooms.keySet().toString());
        for (String roomId : rooms.keySet()) {
            try {
                MultiUserChatManager mucm = MultiUserChatManager.getInstanceFor(xmpp.getConnection());
                final MultiUserChat chat = mucm.getMultiUserChat(roomId + "@" + xmpp.getMucDomain());
                if (!chat.isJoined()) {
                    try {
                        chat.join(xmpp.getMucNickname());
                    } catch (SmackException e) {
                        logger.warn("Could not join room \"{}\" - {}", roomId, e.getMessage());
                    }
                }
                chat.sendMessage(response.getMessage());
            } catch (XMPPException | SmackException.NotConnectedException e) {
                logger.warn("Could not deliver message \"{}\" - {}", roomId, e.getMessage());
            }
        }
        return true;
    }
}