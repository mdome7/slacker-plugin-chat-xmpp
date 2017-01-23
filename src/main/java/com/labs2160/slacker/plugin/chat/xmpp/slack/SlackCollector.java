package com.labs2160.slacker.plugin.chat.xmpp.slack;

import com.labs2160.slacker.api.*;
import com.labs2160.slacker.api.response.SlackerOutput;
import com.labs2160.slacker.api.response.TextOutput;
import com.labs2160.slacker.plugin.chat.xmpp.OutputUtil;
import com.labs2160.slacker.plugin.chat.xmpp.XMPPResource;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Collector that listens for requests via messages in Slack.
 */
public class SlackCollector implements RequestCollector, ChatManagerListener, ChatMessageListener {

    private final static Logger logger = LoggerFactory.getLogger(SlackCollector.class);

    private XMPPResource xmpp;

    private XMPPTCPConnection conn;

    private Map<String, MultiUserChat> rooms;

    private RequestHandler handler;

    public SlackCollector() {
        this.rooms = new HashMap<>();
    }

    public SlackCollector(XMPPResource xmpp) {
        this();
        this.xmpp = xmpp;
        this.conn = xmpp.getConnection();
    }

    @Override
    public void setComponents(Map<String, Resource> resources, Properties config) {
        this.xmpp = (XMPPResource) resources.get(config.getProperty("XMPPResourceRef"));
        this.conn = xmpp.getConnection();
        StringTokenizer st = new StringTokenizer(config.getProperty("mucRooms", ""), ", "); // comma-separated
        while(st.hasMoreTokens()) {
            // Add a room to join at startup.  Do not add the Conference (MUC) domain.
            // e.g. 1234_my_room (not 1234_my_room@muc.domain)
            rooms.put(st.nextToken(), null);
        }
    }

    @Override
    public void start(RequestHandler handler) {
        this.handler = handler;
        xmpp.login();
        try {
            ChatManager.getInstanceFor(conn).addChatListener(this);
            joinRooms();
            logger.info("Slack: connected={}, authenticated={}", conn.isAuthenticated(), conn.isAuthenticated());
        } catch (SmackException e) {
            throw new IllegalStateException("Cannot initialize Slack - " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        logger.debug("SlackChat disconnecting");
        conn.disconnect();
    }

    @Override
    public boolean isActive() {
        return conn.isConnected();
    }

    public XMPPConnection getConnection() {
        return conn;
    }

    /**
     * Handler function for ChatManagerListener.
     */
    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        logger.debug("Incoming chat createdLocally={}", createdLocally);
        if (!createdLocally) {
            chat.addMessageListener(this);
        }
    }

    /**
     * Invoke the request handler and respond with the result.
     * Handler function for ChatMessageListener.
     */
    @Override
    public void processMessage(Chat chat, Message msg) {
        final Message responseMsg = process(msg);
        xmpp.sendMessage(chat, responseMsg);
    }

    public void joinRooms() throws SmackException {
        for (String roomId : rooms.keySet()) {
            try {
                joinRoom(roomId);
            } catch (NoResponseException | XMPPException | NotConnectedException e) {
                logger.warn("Could not join room \"{}\" - {}", roomId, e.getMessage());
            }
        }
    }

    private void joinRoom(final String roomId) throws XMPPException, SmackException {
        logger.info("Joining room: {}", roomId);
        MultiUserChatManager mucm = MultiUserChatManager.getInstanceFor(conn);
        final MultiUserChat chat = mucm.getMultiUserChat(roomId + "@" + xmpp.getMucDomain());
        chat.addMessageListener(new MessageListener() {
            @Override
            public void processMessage(Message message) {
                // only process messages not set by me and starts with the mucKeyword
                logger.debug("Room({}) {}", roomId, message.getBody());
                if (message.getBody() != null &&
                        message.getFrom().indexOf(xmpp.getMucNickname()) < 0  &&
                        message.getBody().startsWith(xmpp.getMucKeyword())) {
                    logger.debug("Message from {} - {}", message.getFrom(), message.getBody());
                    Message responseMsg = process(message);
                    try {
                        chat.sendMessage(responseMsg);
                    } catch (NotConnectedException | XMPPException e) {
                        logger.warn("Cannot send response to room: {} - {}", roomId, e.getMessage());
                    }
                }
            }
        });

        rooms.put(roomId, chat);
        if (!chat.isJoined()) {
            chat.join(xmpp.getMucNickname());
        }
    }

    public Message process(Message msg) {
        Message responseMsg = new Message();
        try {
            String body = msg.getBody();
            if (body == null || body.trim().length() == 0) {
                logger.trace("Empty message from {}", msg.getFrom());
            } else {
                logger.debug("Message from {}: {}", msg.getFrom(), msg.getBody());
                String [] requestTokens = body.split(" ");

                if (xmpp.getMucKeyword().equals(requestTokens[0])) {
                    requestTokens = Arrays.copyOfRange(requestTokens, 1, requestTokens.length);
                }

                try {
                    Future<SlackerOutput> future = handler.handle(new SlackerRequest("slackchat", requestTokens));
                    responseMsg = createResponseMessage(future.get());
                } catch (ExecutionException ee) { // ExecutionException is just a wrapper
                    throw ee.getCause() != null ? (Exception) ee.getCause() : ee;
                }
            }
        } catch (NoArgumentsFoundException e) {
            logger.warn("Missing arguments {}, request={} ({})", msg.getFrom(), msg.getBody(), e.getMessage());
            responseMsg.setBody(" You need to supply arguments");
        } catch (InvalidRequestException e) {
            logger.warn("Invalid request from {}, request={} ({})", msg.getFrom(), msg.getBody(), e.getMessage());
            responseMsg.setBody(" I could understand your gibberish");
        } catch (SlackerException e) {
            logger.error("Error while trying to handle SlackChat message from {}", msg.getFrom(), e);
            responseMsg.setBody(" I'm not able to help you out right now.");
        } catch (Exception e) {
            logger.error("Fatal error while trying to handle SlackChat message from {}", msg.getFrom(), e);
            responseMsg.setBody(" I'm not able to help you out right now.");
        }
        return responseMsg;
    }

    private Message createResponseMessage(SlackerOutput output) {
        Message responseMsg = new Message();
        if (output instanceof TextOutput) {
            XHTMLExtension xhtmlExtension = new XHTMLExtension();
            TextOutput to = (TextOutput) output;
            responseMsg.setBody(to.getMessage());
            String html = OutputUtil.plainTextToJabberHtml(to.getMessage());
            xhtmlExtension.addBody(html);
            responseMsg.addExtension(xhtmlExtension);
        } else {
            responseMsg.setBody("Error - response type " + output.getClass().getSimpleName() + " not yet supported");
        }

        return responseMsg;
    }
}
