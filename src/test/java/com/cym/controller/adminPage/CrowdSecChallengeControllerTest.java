package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.config.HomeConfig;
import com.cym.service.CrowdSecChallenge;
import com.cym.service.SettingService;
import com.cym.testsupport.TestMessages;
import com.cym.utils.ChallengeSecrets;
import com.cym.utils.CrowdSecCredentials;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONUtil;

/** 驗證頁設定 API：只回遮罩值與來源、驗證輸入、外部驗證站需要交接票金鑰。 */
class CrowdSecChallengeControllerTest {
	private static final String TICKET = "ticket-plaintext-key-7d21";
	private static final String COOKIE = "cookie-plaintext-key-4b88";

	@TempDir
	Path tmp;

	private final Map<String, String> env = new HashMap<>();
	private final Map<String, String> settings = new HashMap<>();
	private CrowdSecController controller;

	@BeforeEach
	void setUp() throws Exception {
		env.clear();
		settings.clear();
		SettingService settingService = new SettingService() {
			@Override public String get(String key) { return settings.get(key); }
			@Override public void set(String key, String value) { settings.put(key, value); }
		};
		HomeConfig homeConfig = new HomeConfig();
		homeConfig.home = tmp.resolve("home").toString().replace('\\', '/') + "/";
		CrowdSecChallenge challenge = new CrowdSecChallenge();
		ReflectUtil.setFieldValue(challenge, "homeConfig", homeConfig);
		ReflectUtil.setFieldValue(challenge, "settingService", settingService);
		ReflectUtil.setFieldValue(challenge, "messageUtils", TestMessages.load());
		ReflectUtil.setFieldValue(challenge, "secrets", new ChallengeSecrets(env::get, tmp.resolve("secrets"),
				Path.of(homeConfig.home + "crowdsec/challenge_cookie_secret")));

		controller = new CrowdSecController();
		ReflectUtil.setFieldValue(controller, "settingService", settingService);
		ReflectUtil.setFieldValue(controller, "crowdSecChallenge", challenge);
		ReflectUtil.setFieldValue(controller, "credentials", new CrowdSecCredentials(env::get, tmp.resolve("secrets")));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> view() {
		JsonResult result = controller.getChallenge();
		assertTrue(result.isSuccess());
		return (Map<String, Object>) result.getObj();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> child(Map<String, Object> map, String key) {
		return (Map<String, Object>) map.get(key);
	}

	@Test
	void defaultsWithoutCrowdSec() {
		Map<String, Object> v = view();
		assertEquals(Boolean.FALSE, v.get("enabled"));
		assertEquals("builtin", v.get("mode"));
		assertEquals("auto", v.get("style"));
		assertEquals("", v.get("externalUrl"));
		assertEquals(60, v.get("ttlMinutes"));
		assertEquals("NONE", child(v, "cookieSecret").get("source"));
		assertEquals("/run/secrets/nwu_challenge_secret", child(v, "cookieSecret").get("file"));
		assertEquals("NWU_CHALLENGE_TICKET_SECRET", child(v, "ticketSecret").get("env"));
		assertNull(v.get("problem"));
		assertNull(v.get("lastError"));
	}

	@Test
	void enabledWhenBouncerKeyAndLapiUrlAreSet() {
		env.put("CROWDSEC_BOUNCER_KEY", "bouncer-key-1234");
		env.put("CROWDSEC_LAPI_URL", "http://crowdsec:8080");
		assertEquals(Boolean.TRUE, view().get("enabled"));
	}

	@Test
	void secretsAreMaskedOnly() {
		env.put(ChallengeSecrets.COOKIE_ENV, COOKIE);
		env.put(ChallengeSecrets.TICKET_ENV, TICKET);
		Map<String, Object> v = view();
		String json = JSONUtil.toJsonStr(v);
		assertFalse(json.contains(COOKIE), json);
		assertFalse(json.contains(TICKET), json);
		assertEquals("••••••4b88", child(v, "cookieSecret").get("masked"));
		assertEquals("ENV", child(v, "ticketSecret").get("source"));
	}

	@Test
	void generatedCookieSecretShowsItsSource() throws Exception {
		Path generated = tmp.resolve("home/crowdsec/challenge_cookie_secret");
		Files.createDirectories(generated.getParent());
		Files.writeString(generated, "0123456789abcdef0123456789abcdef");
		assertEquals("GENERATED", child(view(), "cookieSecret").get("source"));
	}

	@Test
	void getDoesNotGenerateTheCookieSecret() {
		view();
		assertFalse(Files.exists(tmp.resolve("home/crowdsec/challenge_cookie_secret")));
	}

	@Test
	void saveStoresAllFields() {
		assertTrue(controller.saveChallenge("builtin", "checkbox", "", "30").isSuccess());
		assertEquals(Map.of(CrowdSecChallenge.MODE_KEY, "builtin", CrowdSecChallenge.STYLE_KEY, "checkbox",
				CrowdSecChallenge.EXTERNAL_URL_KEY, "", CrowdSecChallenge.TTL_KEY, "30"), settings);
		Map<String, Object> v = view();
		assertEquals("checkbox", v.get("style"));
		assertEquals(30, v.get("ttlMinutes"));
	}

	@Test
	void saveRejectsInvalidInput() {
		assertFalse(controller.saveChallenge("other", "auto", "", "60").isSuccess());
		assertFalse(controller.saveChallenge("builtin", "other", "", "60").isSuccess());
		for (String ttl : new String[] { "4", "1441", "abc", "", null }) {
			assertFalse(controller.saveChallenge("builtin", "auto", "", ttl).isSuccess(), String.valueOf(ttl));
		}
		assertFalse(controller.saveChallenge("builtin", "auto", "not a url", "60").isSuccess());
		assertTrue(settings.isEmpty());
	}

	@Test
	void externalNeedsValidUrlAndTicketSecret() {
		assertFalse(controller.saveChallenge("external", "auto", "", "60").isSuccess());
		assertFalse(controller.saveChallenge("external", "auto", "https://verify.example/", "60").isSuccess(),
				"no ticket secret yet");
		assertTrue(settings.isEmpty());
		env.put(ChallengeSecrets.TICKET_ENV, TICKET);
		assertTrue(controller.saveChallenge("external", "auto", " https://verify.example/ ", "60").isSuccess());
		assertEquals("https://verify.example/", settings.get(CrowdSecChallenge.EXTERNAL_URL_KEY));
		assertNull(view().get("problem"));
	}

	@Test
	void problemShowsWhenTicketSecretDisappears() {
		env.put(ChallengeSecrets.TICKET_ENV, TICKET);
		assertTrue(controller.saveChallenge("external", "auto", "https://verify.example/", "60").isSuccess());
		env.remove(ChallengeSecrets.TICKET_ENV);
		assertEquals(CrowdSecChallenge.ERR_TICKET_SECRET, view().get("problem"));
	}
}
