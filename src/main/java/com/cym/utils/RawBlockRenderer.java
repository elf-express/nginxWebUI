package com.cym.utils;

/** Renders a validated block while leaving the user's body text untouched. */
public final class RawBlockRenderer {
	private RawBlockRenderer() { }

	public static String render(String header, String body, String context) {
		String source = NginxBlockCodec.render(header, body, context);
		if (!source.endsWith("}")) {
			throw new IllegalArgumentException("rendered block is missing its closing brace");
		}
		int close = source.length() - 1;
		if (close == 0 || source.charAt(close - 1) != '\n' && source.charAt(close - 1) != '\r') {
			source = source.substring(0, close) + "\n" + source.substring(close);
		}
		return source;
	}
}
