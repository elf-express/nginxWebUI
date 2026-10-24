package com.cym.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

import com.cym.utils.MessageUtils;

/** 從 classpath 載入三份 messages*.properties 的 MessageUtils（不經 Solon 注入）。 */
public final class TestMessages {
	private TestMessages() {
	}

	public static MessageUtils load() {
		MessageUtils m = new MessageUtils();
		m.setProperties(properties("messages.properties"));
		m.setPropertiesTW(properties("messages_zh_TW.properties"));
		m.setPropertiesEN(properties("messages_en_US.properties"));
		return m;
	}

	private static Properties properties(String name) {
		Properties p = new Properties();
		try (InputStream in = TestMessages.class.getClassLoader().getResourceAsStream(name)) {
			p.load(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return p;
	}
}
