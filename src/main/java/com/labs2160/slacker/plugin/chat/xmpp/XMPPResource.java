package com.labs2160.slacker.plugin.chat.xmpp;

import com.labs2160.slacker.api.Resource;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;


/**
 * TLS is required for all connections.
 * The nickname used when joining rooms must match the name on the HipChat account.
 * Valid auth types are jabber:iq:auth and SASL PLAIN.
 * Communication with users from other HipChat groups is not permitted.
 * Connections are expected to be long-lived, so any clients connecting repeatedly may be rate limited.
 * Connections are dropped after 150s of inactivity. We suggest sending a single space (" ") as keepalive data every 60 seconds.
 * Room history is automatically sent when joining a room unless your JID resource is "bot".
 */
public class XMPPResource implements Resource {

    public final static int DEFAULT_PORT = 5222;

    private final static Logger logger = LoggerFactory.getLogger(XMPPResource.class);

    private XMPPTCPConnection conn;

    /** Jabber ID */
    private String user;

    private String host;

    private int port;

    /** keyword used for this to react to message in a multi-user chat room - must be the first word of the message */
    private String mucKeyword;

    /** multi-user chat room nickname */
    private String mucNickname;

    /** multi-user chat (MUC) domain (e.g. conf.hipchat.com) */
    private String mucDomain;

    private XMPPTCPConnectionConfiguration config;

    public XMPPResource() { }

    // Used for testing purposes
    public XMPPResource(String host, String user, String password, String mucNickname, String mucDomain, String mucKeyword) {
        this();
        final Properties configuration = new Properties();
        configuration.put("host", host);
        configuration.put("user", user);
        configuration.put("password", password);
        configuration.put("mucNickname", mucNickname);
        configuration.put("mucDomain", mucDomain);
        configuration.put("mucKeyword", mucKeyword);
        setConfiguration(configuration);
    }

    @Override
    public void setConfiguration(Properties configuration) {
        host = getRequiredConfigParam(configuration, "host");
        port = Integer.parseInt(configuration.getProperty("port", "" + DEFAULT_PORT));
        user = getRequiredConfigParam(configuration, "user");
        mucDomain = getRequiredConfigParam(configuration, "mucDomain");
        mucNickname = getRequiredConfigParam(configuration, "mucNickname");
        mucKeyword = getRequiredConfigParam(configuration, "mucKeyword");

        if (user.indexOf("@") < 0) {
            logger.warn("User \"{}\" does not have host info. Jabber user IDs usually has format: <user_id>@<host> - e.g. {}@{}",
                    user, user, host
            );
        }

        config = XMPPTCPConnectionConfiguration.builder()
                .setHost(host).setPort(port)
                .setServiceName(host)
                .setUsernameAndPassword(user, configuration.getProperty("password"))
                .setResource("bot")
                .setConnectTimeout(10000)
                .setSendPresence(true)
                .build();
        logger.debug("user={}, host={}, port={}, mucNickname={}, mucDomain={}, mucKeyword={}", user, host, port, mucNickname, mucDomain, mucKeyword);
        conn = new XMPPTCPConnection(config);
    }

    private String getRequiredConfigParam(Properties configuration, String key) {
        final String value = configuration.getProperty(key);
        if (value == null || value.trim().length() == 0) {
            throw new IllegalStateException("Configuration parameter \"" + key + "\" must be specified");
        }
        return value;
    }

    public void login() {
        connect(false);
        try {
            conn.login();
        } catch(SmackException | IOException | XMPPException e) {
            throw new IllegalStateException("Cannot initialize XMPP connection to " + host + ":" + port +
                    " using user=" + user + ", mucDomain=" + mucDomain + " - " + e.getMessage(), e);
        }
    }

    public void shutdown() {
        logger.debug("Disconnecting");
        conn.disconnect();
    }

    public boolean isActive() {
        return conn.isConnected();
    }

    public boolean sendMessage(Chat chat, String message) {
        final Message msg = new Message();
        msg.setBody(message);
        return sendMessage(chat, msg, 2);
    }

    public boolean sendMessage(Chat chat, Message msg) {
        return sendMessage(chat, msg, 2);
    }

    private boolean sendMessage(Chat chat, Message msg, int attempts) {
        try {
            chat.sendMessage(msg);
            return true;
        } catch (NotConnectedException e) {
            logger.warn("Cannot send message - {} ({} attempts left)", e.getMessage(), attempts);
            if (attempts > 0 && connect(true)) {
                return sendMessage(chat, msg, --attempts); // retry
            } else {
                logger.error("Failed to send response to {}, body={}", chat.getParticipant(), msg.getBody());
            }
            return false;
        }
    }

    private boolean connect(boolean quietly) {
        if (! conn.isConnected()) {
            try {
                logger.debug("Connecting to server {}:{}", host, port);
                conn.connect();
            } catch (SmackException | IOException | XMPPException e) {
                if (quietly) {
                    logger.warn("Could not connect to server; " + e.getMessage(), e);
                    return false; // connect failed, don't throw Exception
                } else {
                    throw new IllegalStateException("Cannot connect to server; " + e.getMessage(), e);
                }
            }
        }
        return true;
    }

    public XMPPTCPConnection getConnection() {
        return conn;
    }

    public String getUser() { return user; }

    public String getMucDomain() { return mucDomain; }

    public String getMucNickname() { return mucNickname; }

    public String getMucKeyword() { return mucKeyword; }
}
