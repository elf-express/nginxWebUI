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
	private boolean bouncerActive = true;

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
		svc.bouncerActive = () -> bouncerActive;
		// 正式環境是 ServerService.getListByProxyType({"0"})；記憶體 SqlHelper 的 IN 不轉型，這裡直接過濾 HTTP 站台
		svc.httpServers = () -> sql.findAll(Server.class).stream().filter(s -> Integer.valueOf(0).equals(s.getProxyType())).toList();
	}

	private void list(String type, String name, String ips) {
		DenyAllow da = new DenyAllow();
		da.setType(type);
		da.setName(name);
		da.setIp(ips);
		sql.insertOrUpdate(da);
	}

	private void geo(String serverId, int mode, String countries) {
		geo(serverId, mode, countries, true);
	}

	private void geo(String serverId, int mode, String countries, boolean enable) {
		GeoRule r = new GeoRule();
		r.setServerId(serverId);
		r.setMode(mode);
		r.setCountries(countries);
		r.setEnable(enable);
		sql.insertOrUpdate(r);
	}

	/** 啟用的 HTTP 站台 */
	private String server(String name) {
		return server(name, 0, true);
	}

	private String server(String name, int proxyType, boolean enable) {
		Server s = new Server();
		s.setServerName(name);
		s.setProxyType(proxyType);
		s.setEnable(enable);
		return sql.insertOrUpdate(s);
	}

	private IpLookupService.Layer layer(IpLookupService.Result r, String name) {
		return r.layers().stream().filter(l -> l.layer().equals(name)).findFirst().orElseThrow();
	}

	private Map<String, Object> site(IpLookupService.Layer geo, String serverId) {
		return geo.details().stream().filter(d -> serverId.equals(d.get("scope"))).findFirst().orElseThrow();
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
		String sid = server("site.example.com");
		geo(null, 0, "TW,JP");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("blocked", layer(r, "geo").state());
		assertEquals("geo", r.blockedBy());
		Map<String, Object> d = site(layer(r, "geo"), sid);
		assertEquals("global", d.get("source"));
		assertEquals(0, d.get("mode"));
		assertEquals(true, d.get("blocked"));
	}

	@Test
	void onlyGlobalRuleAppliesToEverySite() {
		String a = server("a.example.com");
		String b = server("b.example.com");
		geo(null, 1, "CN");
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals("blocked", g.state());
		assertEquals(2, g.details().size());
		for (String sid : List.of(a, b)) {
			assertEquals("global", site(g, sid).get("source"));
			assertEquals(true, site(g, sid).get("blocked"));
		}
	}

	@Test
	void serverRuleOverridesGlobal() {
		// 全域封鎖 CN；shop 有專屬規則只允許 CN → shop 依專屬規則放行，其他站台依全域規則封鎖
		geo(null, 1, "CN");
		String shop = server("shop.example.com");
		String blog = server("blog.example.com");
		geo(shop, 0, "CN");
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals("blocked", g.state());
		assertEquals("server", site(g, shop).get("source"));
		assertEquals(false, site(g, shop).get("blocked"));
		assertEquals("global", site(g, blog).get("source"));
		assertEquals(true, site(g, blog).get("blocked"));
	}

	@Test
	void serverRuleOverridingGlobalOnEverySiteIsAllowed() {
		// 唯一的站台有專屬規則放行：全域規則雖然會擋，但沒有任何站台套用它
		geo(null, 1, "CN");
		String shop = server("shop.example.com");
		geo(shop, 0, "CN");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("allowed", layer(r, "geo").state());
		assertEquals("none", r.blockedBy());
		assertEquals(1, layer(r, "geo").details().size());
	}

	@Test
	void serverRuleWithoutCountriesDoesNotFallBackToGlobal() {
		// ConfService 選到站台專屬規則後，countries 為空就不產生 if，不會再回退全域規則
		geo(null, 0, "TW");
		String shop = server("shop.example.com");
		geo(shop, 0, "");
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals("allowed", g.state());
		Map<String, Object> d = site(g, shop);
		assertEquals("server", d.get("source"));
		assertEquals(false, d.get("hasCountries"));
		assertEquals(false, d.get("blocked"));
	}

	@Test
	void disabledServerRuleFallsBackToGlobal() {
		geo(null, 0, "TW");
		String shop = server("shop.example.com");
		geo(shop, 1, "RU", false);
		Map<String, Object> d = site(layer(svc.lookup("203.0.113.5"), "geo"), shop);
		assertEquals("global", d.get("source"));
		assertEquals(true, d.get("blocked"));
	}

	@Test
	void siteWithoutAnyRuleIsListedAsNone() {
		String shop = server("shop.example.com");
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals("allowed", g.state());
		assertEquals("none", site(g, shop).get("source"));
		assertEquals(false, site(g, shop).get("blocked"));
	}

	@Test
	void disabledAndStreamServersAreNotListed() {
		geo(null, 0, "TW");
		String on = server("on.example.com");
		server("off.example.com", 0, false);
		server("tcp", 1, true);
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals(1, g.details().size());
		assertEquals(on, g.details().get(0).get("scope"));
	}

	@Test
	void noHttpServerMeansNoCountryBlocking() {
		// 沒有啟用的 HTTP 站台時 nginx 沒有任何 server 會執行國家封鎖的 if
		geo(null, 0, "TW");
		server("off.example.com", 0, false);
		IpLookupService.Layer g = layer(svc.lookup("203.0.113.5"), "geo");
		assertEquals("allowed", g.state());
		assertTrue(g.details().isEmpty());
	}

	@Test
	void unknownCountryWhenNoMmdb() {
		country = GeoLookup.Country.unavailable();
		String sid = server("site.example.com");
		geo(null, 0, "TW");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("unknown", layer(r, "geo").state());
		assertNull(r.country());
		// 判斷不了是否封鎖：不帶 blocked
		assertNull(site(layer(r, "geo"), sid).get("blocked"));
		assertEquals("global", site(layer(r, "geo"), sid).get("source"));
	}

	@Test
	void noMmdbIsNotUnknownWhenNoSiteUsesACountryRule() {
		country = GeoLookup.Country.unavailable();
		geo(null, 0, "TW");
		String shop = server("shop.example.com");
		geo(shop, 0, "");
		assertEquals("allowed", layer(svc.lookup("203.0.113.5"), "geo").state());
		// 沒有任何啟用的 HTTP 站台：同樣不是 unknown
		sql.deleteById(shop, Server.class);
		assertEquals("allowed", layer(svc.lookup("203.0.113.5"), "geo").state());
	}

	@Test
	void noCountryRecordInAllowModeIsBlocked() {
		// 資料庫有、但查不到這個 IP 的國家：nginx 的 $geoip2_data_country_code 是空字串，白名單模式走 default 1（封鎖）
		country = GeoLookup.Country.none();
		server("site.example.com");
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
		server("site.example.com");
		geo(null, 1, "CN,RU");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("allowed", layer(r, "geo").state());
		assertEquals("none", r.blockedBy());
	}

	@Test
	void blankEntryInCountryListNeverMatchesMissingCountry() {
		// ConfService 會略過空白項目，所以空字串國家不會因為清單有空項目而命中
		country = GeoLookup.Country.none();
		server("site.example.com");
		geo(null, 1, "CN,,RU");
		assertEquals("allowed", layer(svc.lookup("203.0.113.5"), "geo").state());
	}

	@Test
	void layerUnknownOrUnavailableWithoutAnyBlockIsUndetermined() {
		country = GeoLookup.Country.unavailable();
		server("site.example.com");
		geo(null, 0, "TW");
		assertEquals("undetermined", svc.lookup("203.0.113.5").blockedBy());

		country = GeoLookup.Country.of("TW");
		svc.decisionsOf = ip -> {
			throw new CrowdSecClient.LapiUnavailableException("down");
		};
		assertEquals("undetermined", svc.lookup("203.0.113.5").blockedBy());

		svc.decisionsOf = ip -> {
			throw new IllegalStateException("boom");
		};
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("crowdsec_error", layer(r, "crowdsec").reason());
		assertEquals("undetermined", r.blockedBy());
	}

	@Test
	void bouncerKeyNotConfiguredIsNotUndetermined() {
		// 沒設 bouncer 金鑰時 nginx 不載入 bouncer，CrowdSec 不可能擋人；CrowdSec 層照舊顯示無法查詢，但整體結果是確定的
		canRead = false;
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("unavailable", layer(r, "crowdsec").state());
		assertEquals(CrowdSecClient.NotConfiguredException.BOUNCER_KEY, layer(r, "crowdsec").reason());
		assertEquals("none", r.blockedBy());
	}

	@Test
	void bouncerKeyNotConfiguredStillUndeterminedWhenCountryUnknown() {
		canRead = false;
		country = GeoLookup.Country.unavailable();
		server("site.example.com");
		geo(null, 0, "TW");
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
	void slashZeroAndAllLinesAreNotMatchesBecauseNginxSkipsThem() {
		// ConfService.collectIps 略過 all 與 /0，這些行不會出現在 allowlist geo 與 allow／deny 設定裡
		list("allow", "allow world v4", "0.0.0.0/0");
		list("allow", "allow world v6", "::/0");
		list("allow", "allow all", "all");
		list("deny", "deny world", "0.0.0.0/0\nall");
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("none", layer(r, "allowlist").state());
		assertEquals("none", layer(r, "denylist").state());
		assertEquals("none", r.blockedBy());
		assertEquals("none", layer(svc.lookup("2001:db8::1"), "allowlist").state());
	}

	@Test
	void normalRangeStillMatchesNextToSkippedLines() {
		list("allow", "office", "0.0.0.0/0\n203.0.113.0/24");
		list("deny", "bad", "all\n198.51.100.0/24");
		assertEquals(List.of("office"), layer(svc.lookup("203.0.113.5"), "allowlist").lists());
		assertEquals(List.of("bad"), layer(svc.lookup("198.51.100.9"), "denylist").lists());
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
	void banWithoutActiveBouncerIsNotEnforced() {
		// 憑證有效、LAPI 有 ban，但本機缺 Lua 模組或包裝檔：nginx 不載入 bouncer，這個 ban 不會擋人
		bouncerActive = false;
		decisions = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\",\"origin\":\"crowdsec\",\"scenario\":\"crowdsecurity/http-probing\",\"duration\":\"3h59m\"}]";
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("notEnforced", layer(r, "crowdsec").state());
		assertEquals(1, layer(r, "crowdsec").decisions().size());
		assertEquals("none", r.blockedBy());
	}

	@Test
	void banWithActiveBouncerIsBlocked() {
		bouncerActive = true;
		decisions = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"}]";
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("blocked", layer(r, "crowdsec").state());
		assertEquals("crowdsec", r.blockedBy());
	}

	@Test
	void noDecisionWithoutActiveBouncerIsStillNone() {
		bouncerActive = false;
		decisions = "[{\"id\":8,\"type\":\"captcha\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"}]";
		IpLookupService.Result r = svc.lookup("203.0.113.5");
		assertEquals("none", layer(r, "crowdsec").state());
		assertEquals("none", r.blockedBy());
		decisions = "null";
		assertEquals("none", layer(svc.lookup("203.0.113.5"), "crowdsec").state());
	}

	@Test
	void notEnforcedDoesNotHideUndetermined() {
		bouncerActive = false;
		decisions = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"}]";
		country = GeoLookup.Country.unavailable();
		server("site.example.com");
		geo(null, 0, "TW");
		assertEquals("undetermined", svc.lookup("203.0.113.5").blockedBy());
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
