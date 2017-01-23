package com.labs2160.slacker.plugin.chat.xmpp.hipchat;

import com.labs2160.slacker.api.Resource;
import com.labs2160.slacker.api.SlackerException;
import com.labs2160.slacker.api.response.TextOutput;
import com.labs2160.slacker.plugin.chat.xmpp.XMPPResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by michaeldometita on 10/17/16.
 */
public class HipChatEndpointIT {
    private static Logger logger = LoggerFactory.getLogger(HipChatEndpointIT.class);

    private static XMPPResource xmpp;

    private static HipChatEndpoint endpoint;

    @BeforeClass
    public static void before() {
        xmpp = new XMPPResource();
        Properties props = getConfiguration();
        xmpp.setConfiguration(props);
        xmpp.login();

        Map<String,Resource> resources = new HashMap<>();
        resources.put("xmpp", xmpp);
        props.put("XMPPResourceRef", "xmpp");
        endpoint = new HipChatEndpoint();
        endpoint.setComponents(resources, props);
    }

    @AfterClass
    public static void after() {
        logger.info("Shutting down");
        xmpp.shutdown();
    }

    @Test
    public void testDeliverTextOutput() throws Exception {
        final String img = "http://ec2-54-227-103-114.compute-1.amazonaws.com/render.png?from=-5days&bgcolor=FFFFFF&height=1000&width=1200&fontSize=12&fgcolor=333333&target=rads-worker_ec2-production_*.*.com.bluekai.analytics.rads.listeners.ProfileManagerLoaderListener.reload-time.max&hideLegend=true&title=PROD%20Reload%20Times";
        final TextOutput output = new TextOutput(img);
        endpoint.deliverResponse(output);
        Thread.sleep(50L);
    }

    private static Properties getConfiguration() {
        Properties config = new Properties();
        //, "mucRooms"
        for(String key : new String [] {"user", "host", "password", "mucNickname", "mucDomain", "mucKeyword", "mucRooms", "people"}) {
            String value = System.getProperty(key);
            if (value != null) config.put(key, value);
        }
        return config;
    }
}
