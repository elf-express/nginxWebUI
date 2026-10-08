package com.cym.controller.adminPage;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;

import com.cym.service.SettingService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

/**
 * 前端 SPA 的 i18n 字典：回傳目前語系的完整 messages，key 為「前綴.名稱」全名。
 */
@Controller
@Mapping("/adminPage/i18n")
public class I18nController extends BaseController {
	@Inject
	SettingService settingService;

	@Mapping("")
	public JsonResult index() {
		String lang = resolveLang(settingService.get("lang"));

		Properties properties = m.getProperties();
		if ("en_US".equals(lang)) {
			properties = m.getPropertiesEN();
		}
		if ("zh_TW".equals(lang)) {
			properties = m.getPropertiesTW();
		}

		Map<String, Object> result = new HashMap<>();
		result.put("lang", lang);
		result.put("messages", toMessages(properties));
		return renderSuccess(result);
	}

	static String resolveLang(String settingLang) {
		if ("en_US".equals(settingLang) || "zh_TW".equals(settingLang)) {
			return settingLang;
		}
		return "zh";
	}

	static Map<String, String> toMessages(Properties properties) {
		Map<String, String> messages = new TreeMap<>();
		for (String key : properties.stringPropertyNames()) {
			messages.put(key, properties.getProperty(key));
		}
		return messages;
	}
}
