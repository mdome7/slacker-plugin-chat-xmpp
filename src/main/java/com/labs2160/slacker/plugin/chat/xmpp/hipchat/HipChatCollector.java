package com.labs2160.slacker.plugin.chat.xmpp.hipchat;

import com.labs2160.slacker.api.*;
import com.labs2160.slacker.plugin.chat.xmpp.OutputUtil;
import com.labs2160.slacker.plugin.chat.xmpp.XMPPResource;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
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
import java.util.concurrent.Future;

public class HipChatCollector implements RequestCollector, ChatManagerListener, ChatMessageListener {

    /**
     * period between empty messages sent to HipChat server to keep connection alive
     */
    private final static String KEEP_ALIVE_SCHEDULE = "*/1 * * * *";

    private final static Logger logger = LoggerFactory.getLogger(HipChatCollector.class);

    private XMPPResource xmpp;

    private XMPPTCPConnection conn;

    private Map<String, MultiUserChat> rooms;

    private RequestHandler handler;

    private String msgInvalidRequest;

    private String msgError;

    private String msgFatalError;

    /**
     * chat used for keepalive messages
     */
    private Chat keepAliveChat;

    public HipChatCollector() { rooms = new HashMap<>(); }

    public HipChatCollector(XMPPResource xmpp) {
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
            rooms.put(st.nextToken(), null);
        }

        msgInvalidRequest = config.getProperty("msgInvalidRequest",
                Emoticon.SHRUG + " I could not understand your gibberish - type 'help' to speak my language");
        msgError = config.getProperty("msgError",
                Emoticon.DOH + " Sorry, I'm a little hungover and can't deal with that right now");
        msgFatalError = config.getProperty("msgFatalError",
                Emoticon.BOOM + " Sorry, I encountered an unexpected error");
    }

    @Override
    public void start(RequestHandler handler) {
        this.handler = handler;
        xmpp.login();
        try {
            this.keepAliveChat = ChatManager.getInstanceFor(conn).createChat(xmpp.getUser()); // loopback chat
            ChatManager.getInstanceFor(conn).addChatListener(this);
            joinRooms();
            logger.info("HipChat: connected={}, authenticated={}", conn.isAuthenticated(), conn.isAuthenticated());
        } catch (SmackException e) {
            throw new IllegalStateException("Cannot initialize HipChat - " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() { xmpp.shutdown(); }

    @Override
    public boolean isActive() {
        return xmpp.isActive();
    }

    /**
     * Handler function for ChatManagerListener.
     */
    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        logger.debug("Incoming chat with user {} createdLocally={}", chat.getParticipant(), createdLocally);
        if (!createdLocally) {
            chat.addMessageListener(this);
        }
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

    /**
     * Add a room to join at startup.  Do not add the Conference (MUC) domain.
     * e.g. 1234_my_room (not 1234_my_room@muc.domain)
     * @param roomId
     */
    public void addRoom(String roomId) {
        logger.debug("Room added: {}", roomId);
        this.rooms.put(roomId, null);
    }

    private void joinRoom(final String roomId) throws XMPPException, SmackException {
        logger.info("Joining room: {}", roomId);
        MultiUserChatManager mucm = MultiUserChatManager.getInstanceFor(conn);
        final MultiUserChat chat = mucm.getMultiUserChat(roomId + "@" + xmpp.getMucDomain());
        chat.addMessageListener(new MessageListener() {
            @Override
            public void processMessage(Message message) {
            // only process messages not set by me and starts with the mucKeyword
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

    /**
     * Invoke the request handler and respond with the result.
     * Handler function for ChatMessageListener.
     */
    @Override
    public void processMessage(Chat chat, Message msg) {
        final Message responseMsg = process(msg);
        xmpp.sendMessage(chat, responseMsg);
    }

    public Message process(Message msg) {
        Message responseMsg = new Message();
        try {
            String body = msg.getBody();
            if (body == null || body.trim().length() == 0) {
                logger.trace("Empty message from {}", msg.getFrom());
            } else {
                logger.debug("Message from {}: {}", msg.getFrom(), msg.getBody());
                String [] requestTokens = body.split("\\s+");

                if (xmpp.getMucKeyword().equals(requestTokens[0])) {
                    requestTokens = Arrays.copyOfRange(requestTokens, 1, requestTokens.length);
                }

                Future<SlackerResponse> future = handler.handle(new SlackerRequest("hipchat", requestTokens));
                responseMsg = createResponseMessage(future.get());
            }
        } catch (NoArgumentsFoundException e) {
            logger.warn("Missing arguments {}, request={} ({})", msg.getFrom(), msg.getBody(), e.getMessage());
            responseMsg.setBody(Emoticon.RUKM + " You need to supply arguments");
        } catch (InvalidRequestException e) {
            logger.warn("Invalid request from {}, request={} ({})", msg.getFrom(), msg.getBody(), e.getMessage());
            responseMsg.setBody(this.msgInvalidRequest);
        } catch (SlackerException e) {
            logger.error("Error while trying to handle HipChat message from {}", msg.getFrom(), e);
            responseMsg.setBody(this.msgError);
        } catch (Exception e) {
            logger.error("Fatal error while trying to handle HipChat message from {}", msg.getFrom(), e);
            responseMsg.setBody(this.msgFatalError);
        }
        return responseMsg;
    }

    private Message createResponseMessage(SlackerResponse response) {
        Message responseMsg = new Message();
        responseMsg.setBody(response.getMessage());

        XHTMLExtension xhtmlExtension = new XHTMLExtension();
        String html = OutputUtil.cleanResponse(response);
        logger.debug(html);
        xhtmlExtension.addBody(html);
        responseMsg.addExtension(xhtmlExtension);
        return responseMsg;
    }

    @Override
    public SchedulerTask [] getSchedulerTasks() {
        SchedulerTask keepAlive = new SchedulerTask(KEEP_ALIVE_SCHEDULE) {
            @Override
            public void execute() {
                logger.trace("Sending keepalive msg to HipChat server");
                xmpp.sendMessage(keepAliveChat, " ");
            }
        };
        return new SchedulerTask [] { keepAlive }; // just one
    }
}
