package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.model.Admin;
import com.cym.service.CrowdSecClient;
import com.cym.service.CrowdSecNginxConf;
import com.cym.service.CrowdSecWhitelistService;
import com.cym.service.SettingService;
import com.cym.testsupport.FakeLapi;
import com.cym.utils.CrowdSecCredentials;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** CrowdSec 連線設定 API：只回遮罩值與來源、只存寬限秒數、不讀資料庫舊設定。 */
class CrowdSecControllerTest {
	private static final String KEY = "bouncer-plaintext-key-a3f9";
	private static final String PASS = "machine-plaintext-pass-77c1";

	@TempDir
	Path tmp;

	private final Map<String, String> env = new HashMap<>();
	private final Map<String, String> settings = new HashMap<>();
	private Map<String, Object> state;
	private CrowdSecController controller;

	@BeforeEach
	void setUp() {
		env.clear();
		settings.clear();
		state = null;
		SettingService settingService = new SettingService() {
			@Override
			public String get(String key) {
				return settings.get(key);
			}

			@Override
			public void set(String key, String value) {
				settings.put(key, value);
			}
		};
		CrowdSecCredentials creds = new CrowdSecCredentials(env::get, tmp.resolve("secrets"));
		CrowdSecNginxConf nginxConf = new CrowdSecNginxConf() {
			@Override
			public Map<String, Object> readState() {
				return state;
			}
		};
		ReflectUtil.setFieldValue(nginxConf, "settingService", settingService);
		CrowdSecClient client = new CrowdSecClient();
		ReflectUtil.setFieldValue(client, "credentials", creds);

		controller = new CrowdSecController();
		ReflectUtil.setFieldValue(controller, "settingService", settingService);
		ReflectUtil.setFieldValue(controller, "crowdSecNginxConf", nginxConf);
		ReflectUtil.setFieldValue(controller, "crowdSecClient", client);
		ReflectUtil.setFieldValue(controller, "credentials", creds);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> config() {
		JsonResult result = controller.getConfig();
		assertTrue(result.isSuccess());
		return (Map<String, Object>) result.getObj();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> child(Map<String, Object> map, String key) {
		return (Map<String, Object>) map.get(key);
	}

	@Test
	void getConfigNeverReturnsPlaintext() {
		env.put("CROWDSEC_BOUNCER_KEY", KEY);
		env.put("CROWDSEC_MACHINE_PASSWORD", PASS);
		env.put("CROWDSEC_LAPI_URL", "http://crowdsec:8080/");

		Map<String, Object> obj = config();
		String json = JSONUtil.toJsonStr(obj);

		assertFalse(json.contains(KEY), json);
		assertFalse(json.contains(PASS), json);
		assertEquals("••••••a3f9", child(obj, "bouncerKey").get("masked"));
		assertEquals("ENV", child(obj, "bouncerKey").get("source"));
		assertEquals(Boolean.TRUE, child(obj, "bouncerKey").get("configured"));
		assertEquals("••••••77c1", child(obj, "machinePassword").get("masked"));
		assertEquals("http://crowdsec:8080", obj.get("lapiUrl"));
		assertEquals("nginxwebui", obj.get("machineId"));
		assertEquals(120, obj.get("graceSeconds"));
		assertEquals(Boolean.TRUE, obj.get("configured"));
	}

	@Test
	void secretsFileSourceIsShown() throws Exception {
		Files.createDirectories(tmp.resolve("secrets"));
		Files.writeString(tmp.resolve("secrets").resolve("crowdsec_machine_password"), PASS + "\n");

		Map<String, Object> obj = config();

		assertEquals("SECRETS_FILE", child(obj, "machinePassword").get("source"));
		assertFalse(JSONUtil.toJsonStr(obj).contains(PASS));
	}

	@Test
	void notConfiguredTellsWhichVariableOrFile() {
		Map<String, Object> obj = config();

		assertEquals("", obj.get("lapiUrl"));
		assertEquals("CROWDSEC_LAPI_URL", obj.get("lapiUrlEnv"));
		Map<String, Object> key = child(obj, "bouncerKey");
		assertEquals(Boolean.FALSE, key.get("configured"));
		assertEquals("", key.get("masked"));
		assertEquals("NONE", key.get("source"));
		assertEquals("/run/secrets/crowdsec_bouncer_key", key.get("file"));
		assertEquals("CROWDSEC_BOUNCER_KEY", key.get("env"));
		Map<String, Object> machine = child(obj, "machinePassword");
		assertEquals("/run/secrets/crowdsec_machine_password", machine.get("file"));
		assertEquals("CROWDSEC_MACHINE_PASSWORD", machine.get("env"));
		assertEquals(Boolean.FALSE, obj.get("configured"));
		assertNull(obj.get("state"));
	}

	@Test
	void getConfigIncludesState() {
		state = new LinkedHashMap<>();
		state.put("lastOk", 1761123456L);
		state.put("lapiOk", true);

		assertSame(state, config().get("state"));
	}

	@Test
	void legacyDbSettingsAreIgnored() {
		settings.put("crowdsecUrl", "http://old-lapi:8080");
		settings.put("crowdsecApiKey", "old-db-api-key-1234567890");

		Map<String, Object> obj = config();

		assertEquals("", obj.get("lapiUrl"));
		assertFalse(JSONUtil.toJsonStr(obj).contains("old-db-api-key"));
		assertEquals(Boolean.FALSE, obj.get("configured"));
	}

	@Test
	void saveConfigStoresOnlyGraceSeconds() {
		JsonResult result = controller.saveConfig(" 45 ");

		assertTrue(result.isSuccess());
		assertEquals(Map.of("crowdsec.graceSeconds", "45"), settings);
		assertEquals(45, config().get("graceSeconds"));
	}

	@Test
	void saveConfigRejectsOutOfRange() {
		for (String value : new String[] { "9", "3601", "abc", "", null, "12.5" }) {
			assertFalse(controller.saveConfig(value).isSuccess(), String.valueOf(value));
		}
		assertTrue(settings.isEmpty());
		assertTrue(controller.saveConfig("10").isSuccess());
		assertTrue(controller.saveConfig("3600").isSuccess());
	}

	@Test
	@SuppressWarnings("unchecked")
	void statusWithoutLapiIsNotConfigured() {
		JsonResult result = controller.status();

		Map<String, Object> obj = (Map<String, Object>) result.getObj();
		assertEquals("notConfigured", obj.get("lapi"));
		assertEquals("notConfigured", obj.get("machine"));
	}

	private static JSONObject firstDecision(FakeLapi lapi) {
		assertEquals(1, lapi.alertBodies.size());
		return JSONUtil.parseArray(lapi.alertBodies.get(0)).getJSONObject(0)
				.getJSONArray("decisions").getJSONObject(0);
	}

	private void useLapi(FakeLapi lapi, String machinePassword) {
		env.put("CROWDSEC_LAPI_URL", lapi.url());
		env.put("CROWDSEC_MACHINE_PASSWORD", machinePassword);
	}

	@Test
	void addDecisionReportsInvalidMachineCredentialsWithoutLeakingPassword() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, "wrong-" + PASS);

			JsonResult result = controller.addDecision("192.0.2.1", "4h", "nginxwebui:t");

			assertFalse(result.isSuccess());
			assertEquals(CrowdSecClient.MachineAuthException.CODE, result.getMsg());
			assertFalse(String.valueOf(result.getMsg()).contains(PASS));
			assertFalse(JSONUtil.toJsonStr(result).contains(PASS));
		}
	}

	@Test
	void decisionsAndDeleteByReasonWithoutBouncerKeyReportBouncerKeyNotConfigured() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, PASS);

			JsonResult list = controller.decisions(10, 1);
			JsonResult delete = controller.deleteByReason("nginxwebui:as-ban:AS");

			assertFalse(list.isSuccess());
			assertEquals(CrowdSecClient.NotConfiguredException.BOUNCER_KEY, list.getMsg());
			assertFalse(delete.isSuccess());
			assertEquals(CrowdSecClient.NotConfiguredException.BOUNCER_KEY, delete.getMsg());
			assertEquals(0, lapi.loginAttempts.get());
		}
	}

	@Test
	void writesWithoutMachinePasswordReportMachineNotConfiguredButListWorks() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			env.put("CROWDSEC_LAPI_URL", lapi.url());
			env.put("CROWDSEC_BOUNCER_KEY", KEY);

			JsonResult add = controller.addDecision("192.0.2.1", "4h", "nginxwebui:t");
			JsonResult range = controller.addRangeDecision("198.51.100.0/24", "4h", "nginxwebui:t");
			JsonResult delete = controller.deleteDecision("1");
			JsonResult list = controller.decisions(10, 1);

			assertEquals(CrowdSecClient.NotConfiguredException.MACHINE, add.getMsg());
			assertEquals(CrowdSecClient.NotConfiguredException.MACHINE, range.getMsg());
			assertEquals(CrowdSecClient.NotConfiguredException.MACHINE, delete.getMsg());
			assertFalse(add.isSuccess());
			assertTrue(list.isSuccess());
			assertEquals(1, lapi.decisionQueries.size());
		}
	}

	@Test
	void alertsAndDeleteDecisionWithoutMachinePasswordSendNoRequest() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			env.put("CROWDSEC_LAPI_URL", lapi.url());
			env.put("CROWDSEC_BOUNCER_KEY", KEY);

			JsonResult alerts = controller.alerts(10, 1);
			JsonResult delete = controller.deleteDecision("1");

			for (JsonResult result : new JsonResult[] { alerts, delete }) {
				assertFalse(result.isSuccess());
				assertEquals(CrowdSecClient.NotConfiguredException.MACHINE, result.getMsg());
			}
			assertEquals(0, lapi.loginAttempts.get());
			assertTrue(lapi.userAgents.isEmpty(), "no LAPI request expected: " + lapi.userAgents);
		}
	}

	private CrowdSecController controllerWithWhitelist(CrowdSecWhitelistService service) {
		CrowdSecController c = new CrowdSecController() {
			@Override
			public Admin getAdmin() {
				Admin admin = new Admin();
				admin.setName("boss");
				return admin;
			}
		};
		ReflectUtil.setFieldValue(c, "crowdSecWhitelistService", service);
		return c;
	}

	@Test
	void whitelistIpDelegatesToWhitelistServiceWithoutNeedingMachinePassword() {
		List<String> calls = new ArrayList<>();
		CrowdSecWhitelistService service = new CrowdSecWhitelistService() {
			@Override
			public Outcome whitelist(String ip, String adminName) {
				calls.add(ip + "|" + adminName);
				return new Outcome("added", "notConfigured", 0, 0, null, List.of());
			}
		};

		JsonResult result = controllerWithWhitelist(service).whitelistIp("192.0.2.1", "4h", "ignored", "true");

		assertTrue(result.isSuccess());
		assertEquals(List.of("192.0.2.1|boss"), calls);
		assertEquals("added", ((CrowdSecWhitelistService.Outcome) result.getObj()).denyAllow());
	}

	@Test
	void whitelistIpRejectsNonSingleIp() {
		CrowdSecWhitelistService service = new CrowdSecWhitelistService() {
			@Override
			public Outcome whitelist(String ip, String adminName) {
				throw new IllegalArgumentException("invalid_ip");
			}
		};

		JsonResult result = controllerWithWhitelist(service).whitelistIp("192.0.2.0/24", null, null, null);

		assertFalse(result.isSuccess());
	}

	@Test
	void addDecisionReportsLapiUnavailableWhenLoginFailsServerSide() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, PASS);
			lapi.loginStatus = 503;

			JsonResult result = controller.addDecision("192.0.2.1", "4h", "nginxwebui:t");

			assertFalse(result.isSuccess());
			assertEquals(CrowdSecClient.LapiUnavailableException.CODE, result.getMsg());
			assertFalse(JSONUtil.toJsonStr(result).contains(PASS));
		}
	}

	@Test
	void addDecisionWithCidrWritesRangeScope() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, PASS);

			assertTrue(controller.addDecision(" 198.51.100.0/24 ", "4h", "nginxwebui:t").isSuccess());
			JSONObject d = firstDecision(lapi);
			assertEquals("range", d.getStr("scope"));
			assertEquals("198.51.100.0/24", d.getStr("value"));
		}
	}

	@Test
	void addDecisionWithIpv6CidrWritesRangeScope() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, PASS);

			assertTrue(controller.addDecision("2001:db8::/32", "4h", "nginxwebui:t").isSuccess());
			JSONObject d = firstDecision(lapi);
			assertEquals("range", d.getStr("scope"));
			assertEquals("2001:db8::/32", d.getStr("value"));
		}
	}

	@Test
	void addDecisionWithSingleHostPrefixWritesIpScopeWithoutPrefix() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, PASS);

			assertTrue(controller.addDecision("192.0.2.7/32", "4h", "nginxwebui:t").isSuccess());
			assertTrue(controller.addDecision("2001:db8::7/128", "4h", "nginxwebui:t").isSuccess());
			assertEquals(2, lapi.alertBodies.size());
			JSONObject v4 = JSONUtil.parseArray(lapi.alertBodies.get(0)).getJSONObject(0)
					.getJSONArray("decisions").getJSONObject(0);
			JSONObject v6 = JSONUtil.parseArray(lapi.alertBodies.get(1)).getJSONObject(0)
					.getJSONArray("decisions").getJSONObject(0);
			assertEquals("ip", v4.getStr("scope"));
			assertEquals("192.0.2.7", v4.getStr("value"));
			assertEquals("ip", v6.getStr("scope"));
			assertEquals("2001:db8::7", v6.getStr("value"));
		}
	}

	@Test
	void addDecisionWithPlainIpStillWritesIpScope() throws Exception {
		try (FakeLapi lapi = new FakeLapi("nginxwebui", PASS, KEY)) {
			useLapi(lapi, PASS);

			assertTrue(controller.addDecision("192.0.2.9", "4h", "nginxwebui:t").isSuccess());
			JSONObject d = firstDecision(lapi);
			assertEquals("ip", d.getStr("scope"));
			assertEquals("192.0.2.9", d.getStr("value"));
		}
	}
}
