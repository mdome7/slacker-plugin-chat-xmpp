package com.labs2160.slacker.plugin.chat.xmpp;

import com.labs2160.slacker.api.response.SlackerOutput;
import com.labs2160.slacker.api.response.TextOutput;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;

public final class OutputUtil {

    public static String plainTextToJabberHtml(String plainText) {
        String cleaned = StringEscapeUtils.escapeHtml4(plainText).replaceAll("\n", "<br/>");
        return "<html xmlns='http://jabber.org/protocol/xhtml-im'><body xmlns='http://www.w3.org/1999/xhtml'><p>"
                + cleaned + "</p></body></html>";
    }

}
