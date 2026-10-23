package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.model.Server;
import com.cym.testsupport.FakeLapi;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.utils.CrowdSecCredentials;

class IpLookupServiceTest {
	private InMemorySqlHelper sql;
	private IpLookupService svc;
	private GeoLookup.Country country = GeoLookup.Country.of("CN");
	private String decisions = "null";
	private boolean canRead = true;

	@BeforeEach
	void setUp() {
		sql = new InMemorySqlHelper();
		DenyAllowService das = new DenyAllowService();
		das.sqlHelper = sql;
		svc = new IpLookupService();
		svc.sqlHelper = sql;
		svc.denyAllowService = das;
		svc.countryOf = ip -> country;
		svc.asnOf = ip -> Optional.of(new GeoLookup.Asn(4134, "Chinanet"));
		svc.canReadCrowdSec = () -> canRead;
		svc.decisionsOf = ip -> decisions;
	}

	private void list(String type, String name, String ips) {
		DenyAllow da = new DenyAllow();
		da.setType(type);
		da.setName(name);
		da.setIp(ips);
		sql.insertOrUpdate(da);
	}

	private void geo(String serverId, int mode, String countries) {
		GeoRule r = new GeoRule();
		r.setServerId(serverId);
		r.setMode(mode);
		r.setCountries(countries);
		r.setEnable(true);
		sql.insertOrUpdate(r);
	}

	private String server(String name) {
		Server s = new Server();
		s.setServerName(name);
		return sql.insertOrUpdate(s);
	}

	private IpLookupService.Layer layer(IpLookupService.Result r, String name) {
		return r.layers().stream().filter(l -> l.layer().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void layersInNginxOrder() {
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals(List.of("allowlist", "geo", "denylist", "crowdsec"), r.layers().stream().map(IpLookupService.Layer::layer).toList());
		assertEquals("CN", r.country());
		assertEquals(4134L, r.asn().number());
		assertEquals("none", r.blockedBy());
	}

	@Test
	void globalAllowModeBlocksUnlistedCountry() {
		geo(null, 0, "TW,JP");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("blocked", layer(r, "geo").state());
		assertEquals("geo", r.blockedBy());
	}

	@Test
	void serverRuleOverridesGlobal() {
		geo(null, 1, "CN");
		String sid = server("shop.example.com");
		geo(sid, 0, "CN");
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals("blocked", g.state());
		assertTrue(g.details().stream().anyMatch(d -> d.get("scope").equals("global") && d.get("blocked").equals(true)));
		assertTrue(g.details().stream().anyMatch(d -> d.get("scope").equals(sid) && d.get("blocked").equals(false)));
	}

	@Test
	void unknownCountryWhenNoMmdb() {
		country = GeoLookup.Country.unavailable();
		geo(null, 0, "TW");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("unknown", layer(r, "geo").state());
		assertNull(r.country());
	}

	@Test
	void noCountryRecordInAllowModeIsBlocked() {
		// 資料庫有、但查不到這個 IP 的國家：nginx 的 $geoip2_data_country_code 是空字串，白名單模式走 default 1（封鎖）
		country = GeoLookup.Country.none();
		geo(null, 0, "TW,JP");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("blocked", layer(r, "geo").state());
		assertEquals("geo", r.blockedBy());
		assertNull(r.country());
	}

	@Test
	void noCountryRecordInDenyModeIsAllowed() {
		// 黑名單模式 default 0（放行）
		country = GeoLookup.Country.none();
		geo(null, 1, "CN,RU");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("allowed", layer(r, "geo").state());
		assertEquals("none", r.blockedBy());
	}

	@Test
	void blankEntryInCountryListNeverMatchesMissingCountry() {
		// ConfService 會略過空白項目，所以空字串國家不會因為清單有空項目而命中
		country = GeoLookup.Country.none();
		geo(null, 1, "CN,,RU");
		assertEquals("allowed", layer(svc.lookup("203.0.113.5"), "geo").state());
	}

	@Test
	void layerUnknownOrUnavailableWithoutAnyBlockIsUndetermined() {
		country = GeoLookup.Country.unavailable();
		geo(null, 0, "TW");
		assertEquals("undetermined", svc.lookup("203.0.113.5").blockedBy());

		country = GeoLookup.Country.of("TW");
		canRead = false;
		assertEquals("undetermined", svc.lookup("203.0.113.5").blockedBy());
	}

	@Test
	void realBlockWinsOverUndetermined() {
		canRead = false;
		list("deny", "bad", "203.0.113.5");
		assertEquals("denylist", svc.lookup("203.0.113.5").blockedBy());
	}

	@Test
	void denylistMatchesCidr() {
		list("deny", "bad nets", "198.51.100.0/24\n192.0.2.1");
		IpLookupService.Result r = svc.lookup("198.51.100.9");
		assertEquals("match", layer(r, "denylist").state());
		assertEquals(List.of("bad nets"), layer(r, "denylist").lists());
		assertEquals("denylist", r.blockedBy());
	}

	@Test
	void crowdsecBanReported() {
		decisions = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\",\"origin\":\"crowdsec\",\"scenario\":\"crowdsecurity/http-probing\",\"duration\":\"3h59m\"}]";
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("blocked", layer(r, "crowdsec").state());
		assertEquals(1, layer(r, "crowdsec").decisions().size());
		assertEquals("crowdsec", r.blockedBy());
	}

	@Test
	void onlyBanDecisionsBlock() {
		decisions = "[{\"id\":8,\"type\":\"captcha\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\",\"origin\":\"crowdsec\"},"
				+ "{\"id\":9,\"type\":\"whitelist\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\",\"origin\":\"cscli\"}]";
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("none", layer(r, "crowdsec").state());
		assertEquals(2, layer(r, "crowdsec").decisions().size());
		assertEquals("none", r.blockedBy());

		decisions = "[{\"id\":8,\"type\":\"captcha\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"},"
				+ "{\"id\":7,\"type\":\"Ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"}]";
		r = svc.lookup("203.0.113.5");
		assertEquals("blocked", layer(r, "crowdsec").state());
		assertEquals("crowdsec", r.blockedBy());
	}

	@Test
	void unreachableLapiGivesLapiUnavailableReason() throws Exception {
		// 用真的 CrowdSecClient 連到已關閉的 LAPI：連線失敗要歸為 crowdsec_lapi_unavailable，不是 crowdsec_error
		FakeLapi lapi = new FakeLapi("nginxwebui", "machine-pass-0123456789", "bouncer-key-0123456789");
		Map<String, String> env = new HashMap<>();
		env.put("CROWDSEC_LAPI_URL", lapi.url());
		env.put("CROWDSEC_BOUNCER_KEY", "bouncer-key-0123456789");
		lapi.close();
		CrowdSecClient client = new CrowdSecClient();
		client.credentials = new CrowdSecCredentials(env::get, Path.of("target", "no-secrets-ip-lookup"));
		svc.canReadCrowdSec = client::canRead;
		svc.decisionsOf = client::decisionsForIpRaw;

		IpLookupService.Layer c = layer(svc.lookup("203.0.113.5"), "crowdsec");

		assertEquals("unavailable", c.state());
		assertEquals(CrowdSecClient.LapiUnavailableException.CODE, c.reason());
	}

	@Test
	void crowdsecUnavailableKeepsOtherLayers() {
		canRead = false;
		list("deny", "bad", "203.0.113.5");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("unavailable", layer(r, "crowdsec").state());
		assertEquals("match", layer(r, "denylist").state());
	}

	@Test
	void crowdsecErrorBecomesUnavailableWithReason() {
		svc.decisionsOf = ip -> {
			throw new CrowdSecClient.LapiUnavailableException("down");
		};
		IpLookupService.Layer c = layer(svc.lookup("203.0.113.5"), "crowdsec");
		assertEquals("unavailable", c.state());
		assertEquals(CrowdSecClient.LapiUnavailableException.CODE, c.reason());
	}

	@Test
	void allowlistWinsOverEveryLayer() {
		list("allow", "office", "203.0.113.0/24");
		list("deny", "bad", "203.0.113.5");
		geo(null, 0, "TW");
		decisions = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"}]";
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("match", layer(r, "allowlist").state());
		assertEquals("skipped", layer(r, "geo").state());
		assertEquals("skipped", layer(r, "denylist").state());
		assertEquals("skipped", layer(r, "crowdsec").state());
		assertEquals("allowlist", r.blockedBy());
	}
}
