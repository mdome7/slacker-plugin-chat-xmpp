package com.labs2160.slacker.plugin.chat.xmpp;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

/**
 * Integration test to try out connection settings.
 */
public class XMPPResourceIT {
    private XMPPResource xmpp;

    public XMPPResource getXMPPResource(String propsFilename) throws IOException {
        System.out.println("Loading properties from " + propsFilename);
        Properties p = new Properties();
        p.load(this.getClass().getResourceAsStream(propsFilename));
        xmpp = new XMPPResource();
        xmpp.setConfiguration(p);
        return xmpp;
    }

    @Test
    public void testHipChatConnectionLogin() throws IOException, XMPPException, SmackException {
        XMPPResource xmpp = getXMPPResource("/hipchat_conf.properties.tmp");
        xmpp.getConnection().connect();
        xmpp.getConnection().login();
    }

    @Test
    public void testSlackConnectionLogin() throws IOException, XMPPException, SmackException {
        XMPPResource xmpp = getXMPPResource("/slack_conf.properties.tmp");
        xmpp.getConnection().connect();
        xmpp.getConnection().login();
    }

}
