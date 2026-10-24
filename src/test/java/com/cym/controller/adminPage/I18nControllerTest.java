package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * SPA 用 i18n 字典（純函式）。
 */
public class I18nControllerTest {

	private static final String[] FILES = { "messages.properties", "messages_zh_TW.properties", "messages_en_US.properties" };

	private Properties load(String name) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
			properties.load(in);
		}
		return properties;
	}

	@Test
	void resolveLang_unknownFallsBackToZh() {
		assertEquals("zh", I18nController.resolveLang(null));
		assertEquals("zh", I18nController.resolveLang(""));
		assertEquals("zh", I18nController.resolveLang("ja"));
		assertEquals("zh_TW", I18nController.resolveLang("zh_TW"));
		assertEquals("en_US", I18nController.resolveLang("en_US"));
	}

	@Test
	void toMessages_keepsEveryKey() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			Map<String, String> messages = I18nController.toMessages(properties);
			assertEquals(properties.stringPropertyNames().size(), messages.size(), file);
			assertEquals(properties.getProperty("basicStr.add"), messages.get("basicStr.add"), file);
		}
	}

	/**
	 * common.html 把每個 key 輸出成 JS 的「前綴.名稱 = 值」；key 多一個點（a.b.c）會在執行時丟 TypeError，
	 * 後面所有 *Str 全域變數都不見，頁面上的 JS 文案整批變成 undefined。
	 */
	@Test
	void everyKeyIsPrefixDotName() throws IOException {
		for (String file : FILES) {
			for (String key : load(file).stringPropertyNames()) {
				assertTrue(key.matches("[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*"), file + ": " + key);
			}
		}
	}

	@Test
	void allLanguagesHaveSameKeys() throws IOException {
		Map<String, String> zh = I18nController.toMessages(load(FILES[0]));
		for (String file : FILES) {
			assertEquals(zh.keySet(), I18nController.toMessages(load(file)).keySet(), file);
		}
	}
}
