package com.labs2160.slacker.plugin.chat.xmpp.hipchat;

import com.labs2160.slacker.api.RequestHandler;
import com.labs2160.slacker.api.Resource;
import com.labs2160.slacker.plugin.chat.xmpp.OutputUtil;
import com.labs2160.slacker.plugin.chat.xmpp.XMPPResource;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RunWith(MockitoJUnitRunner.class)
public class HipChatCollectorIT {

    final static String ROOM_ID = "8618_test";

    @Mock
    private static RequestHandler handler;

    private static XMPPResource xmpp;

    private static HipChatCollector collector;

    @BeforeClass
    public static void before() {
        xmpp = new XMPPResource();
        xmpp.setConfiguration(getConfiguration());
        Map<String,Resource> resources = new HashMap<>();
        resources.put("xmpp", xmpp);
        Properties props = new Properties();
        props.put("XMPPResourceRef", "xmpp");
        collector = new HipChatCollector();
        collector.setComponents(resources, props);
        collector.start(handler);
    }

    @Test
    public void testSendIndividualMessage() throws NotConnectedException, InterruptedException {
        Chat chat = ChatManager.getInstanceFor(xmpp.getConnection())
                .createChat("8618_364462@chat.hipchat.com");
        System.out.println("Chatting with " + chat.getParticipant());
        chat.sendMessage(createMessage("test individual msg"));

        Thread.sleep(3000);
        chat.close();

        System.out.println("waiting");
        Thread.sleep(1000*60*5);
    }

    @Test
    @Ignore
    public void testSendRoomMessage() throws NotConnectedException, XMPPException, NoResponseException, InterruptedException {
        MultiUserChatManager mucm = MultiUserChatManager.getInstanceFor(xmpp.getConnection());
        final MultiUserChat chat = mucm.getMultiUserChat(ROOM_ID + "@conf.hipchat.com");
        chat.join("Chef the Robot");
        chat.sendMessage(createMessage("hi there"));

        Thread.sleep(3000);
    }

    private static Properties getConfiguration() {
        Properties config = new Properties();
        for(String key : new String [] {"user", "host", "password", "mucNickname", "mucDomain", "mucKeyword"}) {
            String value = System.getProperty(key);
            if (value != null) config.put(key, value);
        }
        return config;
    }

    private Message createMessage(String msg) {
        Message responseMsg = new Message();
        responseMsg.setBody(msg);

        XHTMLExtension xhtmlExtension = new XHTMLExtension();
        String html = OutputUtil.plainTextToJabberHtml(msg);
        System.out.println("HTML Message: " + html);
        xhtmlExtension.addBody(html);
        responseMsg.addExtension(xhtmlExtension);
        return responseMsg;
    }
}
