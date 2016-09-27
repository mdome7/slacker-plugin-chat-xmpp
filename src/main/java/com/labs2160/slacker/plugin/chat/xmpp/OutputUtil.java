package com.labs2160.slacker.plugin.chat.xmpp;

import org.apache.commons.lang3.StringEscapeUtils;

import com.labs2160.slacker.api.SlackerResponse;

public final class OutputUtil {

    public static String cleanResponse(SlackerResponse response) {
        // in the future, we may take advantage of response.getAttachedMedia()
        String html = response.getMessage();
        String cleaned = StringEscapeUtils.escapeHtml4(html).replaceAll("\n", "<br/>");
        return "<html xmlns='http://jabber.org/protocol/xhtml-im'><body xmlns='http://www.w3.org/1999/xhtml'><p>"
                + cleaned + "</p></body></html>";
    }

}
