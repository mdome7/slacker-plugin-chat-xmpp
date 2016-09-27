package com.labs2160.slacker.plugin.chat.xmpp.hipchat;

public enum Emoticon {

	DOH("doh"), SHRUG("shrug"), RUKM("areyoukiddingme"), BOOM("boom");

	private String code;

	private Emoticon(String code) {
		this.code = code;
	}

	@Override
    public String toString() {
		return "(" + code + ")";
	}
}
