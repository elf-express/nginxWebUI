package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.model.AsBlockIntent;
import com.cym.testsupport.FakeLapi;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.utils.CrowdSecCredentials;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** ASN Manual 推送與撤銷經 machine 帳號寫進（假）LAPI。 */
class AsnBlockServicePushTest {
	private static final String PASSWORD = "machine-pass-0123456789";
	private static final String TAG = "nginxwebui:as-ban:AS64500";

	@TempDir
	Path tmp;

	private FakeLapi lapi;
	private InMemorySqlHelper sql;

	@BeforeEach
	void setUp() throws Exception {
		lapi = new FakeLapi("nginxwebui", PASSWORD, "bouncer-key-0123456789");
		sql = new InMemorySqlHelper();
	}

	@AfterEach
	void tearDown() {
		lapi.close();
	}

	private AsnBlockService service(String machinePassword) {
		CrowdSecClient client = new CrowdSecClient();
		Map<String, String> env = Map.of("CROWDSEC_LAPI_URL", lapi.url(), "CROWDSEC_MACHINE_PASSWORD", machinePassword);
		client.credentials = new CrowdSecCredentials(env::get, tmp.resolve("no-secrets"));
		AsnBlockService service = new AsnBlockService() {
			@Override
			List<String> fetchAggregatedCidrs(String asn) {
				return List.of("198.51.100.0/24", "2001:db8::/32");
			}
		};
		ReflectUtil.setFieldValue(service, "sqlHelper", sql);
		ReflectUtil.setFieldValue(service, "settingService", new SettingService() {
			@Override
			public String get(String key) {
				return AsnBlockService.SETTING_PROFILE.equals(key) ? AsnBlockService.PROFILE_MANUAL : null;
			}
		});
		ReflectUtil.setFieldValue(service, "crowdSecClient", client);
		return service;
	}

	private String intent() {
		AsBlockIntent intent = new AsBlockIntent();
		intent.setAsn("64500");
		intent.setDuration("24h");
		intent.setReasonTag(TAG);
		intent.setStatus(AsBlockIntent.STATUS_CANDIDATE);
		return sql.insertOrUpdate(intent);
	}

	@Test
	void manualPushWritesOneAlertPerRange() {
		String id = intent();

		service(PASSWORD).pushIntent(id);

		assertEquals(2, lapi.alertBodies.size());
		JSONObject d = JSONUtil.parseArray(lapi.alertBodies.get(0)).getJSONObject(0)
				.getJSONArray("decisions").getJSONObject(0);
		assertEquals("range", d.getStr("scope"));
		assertEquals("198.51.100.0/24", d.getStr("value"));
		assertEquals(TAG, d.getStr("scenario"));
		assertEquals("24h", d.getStr("duration"));
		assertEquals("ban", d.getStr("type"));
		assertEquals(1, lapi.logins.get());
		AsBlockIntent saved = sql.findById(id, AsBlockIntent.class);
		assertEquals(AsBlockIntent.STATUS_ACTIVE, saved.getStatus());
		assertEquals("", saved.getLastError());
	}

	@Test
	void wrongMachinePasswordStopsPushWithClearError() {
		String id = intent();

		CrowdSecClient.MachineAuthException e = assertThrows(CrowdSecClient.MachineAuthException.class,
				() -> service("wrong-password").pushIntent(id));

		assertEquals("crowdsec_machine_unauthorized", e.getMessage());
		assertEquals(1, lapi.loginAttempts.get());
		assertTrue(lapi.alertBodies.isEmpty());
		AsBlockIntent saved = sql.findById(id, AsBlockIntent.class);
		assertEquals(AsBlockIntent.STATUS_FAILED, saved.getStatus());
		assertEquals("machine_unauthorized", saved.getLastError());
	}

	@Test
	void revokeByReasonTagDeletesByScenario() {
		String id = intent();

		int n = service(PASSWORD).revokeByReasonTag(TAG);

		assertEquals(2, n);
		assertEquals(List.of("/v1/decisions?scenario=" + TAG), lapi.deletes);
		assertEquals(AsBlockIntent.STATUS_REVOKED, sql.findById(id, AsBlockIntent.class).getStatus());
	}

	@Test
	void crowdSecConfiguredFollowsMachineCredentials() {
		assertTrue(service(PASSWORD).isCrowdSecConfigured());
		assertFalse(service("").isCrowdSecConfigured());
	}
}
