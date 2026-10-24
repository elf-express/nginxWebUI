package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.config.HomeConfig;
import com.cym.model.OperateLog;
import com.cym.model.Server;
import com.cym.model.WafEventRule;
import com.cym.model.WafRuleSetting;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.service.WafRuleCatalog.Catalog;
import com.cym.service.WafRuleCatalog.Rule;

class WafTuningServiceTest {
	static final String ENV = "crowdsecurity/vpatch-env-access";
	static final String GIT = "crowdsecurity/vpatch-git-config";
	static final String PHP = "crowdsecurity/vpatch-CVE-2017-9841";
	static final Catalog CAT = new Catalog(List.of(
			new Rule(ENV, "vpatch", "vpatch:_other", "Generic .env access", "", List.of()),
			new Rule(GIT, "vpatch", "vpatch:_other", "Generic .git access", "", List.of()),
			new Rule(PHP, "vpatch", "vpatch:PHPUnit", "PHPUnit - RCE", "", List.of("CVE-2017-9841")),
			new Rule("942100", "crs", "crs-sqli", "SQLi libinjection", "", List.of()),
			new Rule("942200", "crs", "crs-sqli", "SQLi comment", "", List.of()),
			new Rule("941100", "crs", "crs-xss", "XSS libinjection", "", List.of())), null, null);

	static WafRuleSetting s(String scope, String key, String action, String excluded) {
		WafRuleSetting w = new WafRuleSetting();
		w.setScope(scope);
		w.setRuleKey(key);
		w.setAction(action);
		w.setExcludedServerIds(excluded);
		return w;
	}

	static String render(List<WafRuleSetting> settings, Map<String, WafTuningService.Hosts> hosts) {
		return WafTuningService.render(CAT, settings, hosts);
	}

	@Test
	void noSettingsRendersTheImageDefault() throws Exception {
		String expected = Files.readString(Path.of("docker/crowdsec/nwu-custom.yaml"), StandardCharsets.UTF_8).replace("\r\n", "\n");
		assertEquals(expected, render(List.of(), Map.of()));
	}

	@Test
	void ruleSettingWinsOverCategoryAndInheritFallsBack() {
		String y = render(List.of(s("category", "crs-sqli", "log", ""), s("rule", "942100", "count", ""), s("rule", "942200", "inherit", "")), Map.of());
		assertTrue(y.contains("{.id < 911000 || .id >= 949000 || .id in [942200]})"), y);
		assertFalse(y.contains("942100"), y);
	}

	@Test
	void offRulesAreRemovedOnLoad() {
		String y = render(List.of(s("rule", GIT, "off", ""), s("category", "crs-xss", "off", "")), Map.of());
		assertTrue(y.contains("on_load:\n - apply:\n   - RemoveInBandRuleByName(\"" + GIT + "\")\n   - RemoveOutBandRuleByID(941100)\n"), y);
	}

	@Test
	void vpatchLogOnlyAllowsAndCancelsTheEvent() {
		String y = render(List.of(s("rule", ENV, "log", "")), Map.of());
		assertTrue(y.contains(" - filter: IsInBand && all(evt.Appsec.MatchedRules, {.name in [\"" + ENV + "\"]})\n   apply:\n    - SetRemediation(\"allow\")\n    - CancelEvent()\n"), y);
		assertTrue(y.indexOf("SetRemediation") < y.indexOf("SendAlert()"), "vpatch hook comes before SendAlert");
	}

	@Test
	void exclusionsUseTheServerNames() {
		Map<String, WafTuningService.Hosts> hosts = Map.of("s1", new WafTuningService.Hosts(List.of("a.com"), List.of(".b.com"), List.of()));
		String y = render(List.of(s("rule", "942100", "inherit", "s1"), s("rule", PHP, "block", "s1,missing")), hosts);
		String cond = "(lower(split(req.Host, \":\")[0]) in [\"a.com\"] || lower(split(req.Host, \":\")[0]) endsWith \".b.com\")";
		assertTrue(y.contains("pre_eval:\n"), y);
		assertTrue(y.contains(" - filter: IsInBand && " + cond + "\n   apply:\n    - RemoveInBandRuleByName(\"" + PHP + "\")\n"), y);
		assertTrue(y.contains(" - filter: IsOutBand && " + cond + "\n   apply:\n    - RemoveOutBandRuleByID(942100)\n"), y);
	}

	@Test
	void categoryExclusionAppliesToItsRulesAndOffRulesAreNotExcluded() {
		Map<String, WafTuningService.Hosts> hosts = Map.of("s1", new WafTuningService.Hosts(List.of("a.com"), List.of(), List.of()));
		String y = render(List.of(s("category", "crs-sqli", "count", "s1"), s("rule", "942200", "off", "")), hosts);
		assertTrue(y.contains(" - filter: IsOutBand && lower(split(req.Host, \":\")[0]) in [\"a.com\"]\n   apply:\n    - RemoveOutBandRuleByID(942100)\n"), y);
		assertFalse(y.contains("pre_eval:\n - filter: IsOutBand && lower(split(req.Host, \":\")[0]) in [\"a.com\"]\n   apply:\n    - RemoveOutBandRuleByID(942100)\n    - RemoveOutBandRuleByID(942200)"), y);
	}

	@Test
	void invalidActionsFallBackToTheDefault() {
		Map<String, WafRuleSetting> rules = Map.of("942100", s("rule", "942100", "block", ""));
		assertEquals("count", WafTuningService.effectiveAction(CAT.byKey().get("942100"), rules, Map.of()));
		assertEquals("block", WafTuningService.effectiveAction(CAT.byKey().get(ENV), Map.of(), Map.of()));
	}

	@Test
	void hostsOfServerNames() {
		WafTuningService.Hosts h = WafTuningService.hostsOf("A.com *.b.com .c.com www.* ~^x\\.com$ _ \"q.com\" 10.0.0.1");
		assertEquals(List.of("a.com", "c.com", "10.0.0.1"), h.exact());
		assertEquals(List.of(".b.com", ".c.com"), h.suffixes());
		assertEquals(List.of("www.*", "~^x\\.com$", "_", "\"q.com\""), h.skipped());
		assertEquals(List.of(), WafTuningService.hostsOf(null).exact());
	}

	@Test
	void vpatchEventIsLogOnlyWhenEveryMatchedVpatchRuleIsLogOnly() {
		Map<String, String> eff = Map.of(ENV, "log", GIT, "block");
		assertEquals("log", WafTuningService.vpatchActionOf(List.of(ENV, "942100"), eff));
		assertEquals("block", WafTuningService.vpatchActionOf(List.of(ENV, GIT), eff));
		assertEquals("block", WafTuningService.vpatchActionOf(List.of(PHP), eff));
		assertEquals("block", WafTuningService.vpatchActionOf(List.of(), eff));
	}

	@Test
	void validateRejectsUnknownKeysAndWrongActions() {
		Set<String> servers = Set.of("s1");
		WafTuningService.validate(s("rule", ENV, "inherit", "s1"), CAT, servers);
		WafTuningService.validate(s("category", "crs-sqli", "log", ""), CAT, servers);
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("rule", "999999", "log", ""), CAT, servers));
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("rule", "942100", "block", ""), CAT, servers));
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("category", "vpatch:_other", "inherit", ""), CAT, servers));
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("rule", ENV, "log", "nope"), CAT, servers));
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("x", ENV, "log", ""), CAT, servers));
	}

	@Test
	void emptySettingsAreDeletedInsteadOfStored() {
		assertTrue(WafTuningService.isDefault(s("rule", ENV, "inherit", "")));
		assertTrue(WafTuningService.isDefault(s("category", "crs-sqli", "count", "")));
		assertFalse(WafTuningService.isDefault(s("category", "crs-sqli", "count", "s1")));
		assertFalse(WafTuningService.isDefault(s("rule", ENV, "log", "")));
	}

	@Test
	void writeApplyWritesTheConfigBeforeTheRequest(@TempDir Path dir) throws Exception {
		WafTuningService.writeApply(dir, "name: x\n", "123");
		assertEquals("name: x\n", Files.readString(dir.resolve("custom.yaml")));
		assertEquals("123\n", Files.readString(dir.resolve("request")));
		try (var s = Files.list(dir)) {
			assertEquals(2, s.count(), "no temp files left");
		}
	}

	@Test
	void statusOfComparesTheRequestWithTheResult() {
		assertEquals("none", WafTuningService.statusOf(null, null).get("state"));
		assertEquals("pending", WafTuningService.statusOf("2", "{\"id\":\"1\",\"status\":\"ok\"}").get("state"));
		Map<String, Object> ok = WafTuningService.statusOf("2", "{\"id\":\"2\",\"status\":\"ok\",\"message\":\"\",\"at\":\"2026-10-24T00:00:00Z\"}");
		assertEquals("ok", ok.get("state"));
		assertEquals("2026-10-24T00:00:00Z", ok.get("at"));
		assertEquals("failed", WafTuningService.statusOf("2", "{\"id\":\"2\",\"status\":\"failed\",\"message\":\"bad\"}").get("state"));
		assertEquals("pending", WafTuningService.statusOf("2", "{broken").get("state"));
	}

	@Test
	void validateRejectsNullActions() {
		Set<String> servers = Set.of("s1");
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("rule", "942100", null, ""), CAT, servers));
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("category", "crs-sqli", null, ""), CAT, servers));
	}

	@Test
	void validateChecksCategoryActionsAgainstTheirSource() {
		Set<String> servers = Set.of("s1");
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("category", "crs-sqli", "block", ""), CAT, servers));
		WafTuningService.validate(s("category", "vpatch:PHPUnit", "block", ""), CAT, servers);
		assertThrows(IllegalArgumentException.class, () -> WafTuningService.validate(s("category", "vpatch:PHPUnit", "count", ""), CAT, servers));
	}

	@Test
	void vpatchCategoryDefaultIsBlock() {
		assertTrue(WafTuningService.isDefault(s("category", "vpatch:PHPUnit", "block", "")));
		assertFalse(WafTuningService.isDefault(s("category", "vpatch:PHPUnit", "log", "")));
	}

	@Test
	void crsLogOnlyCancelsTheEventAfterTheAlertIsSent() {
		String y = render(List.of(s("rule", "942100", "log", "")), Map.of());
		assertTrue(y.contains(" - filter: IsOutBand && all(evt.Appsec.MatchedRules, {.id < 911000 || .id >= 949000 || .id in [942100]})\n   apply:\n    - CancelEvent()\n"), y);
		assertTrue(y.indexOf("SendAlert()") < y.lastIndexOf("CancelEvent()"), y);
		assertFalse(render(List.of(s("rule", "942100", "count", "")), Map.of()).contains("CancelEvent()"));
	}

	@Test
	void ruleAndCategoryExclusionsAreUnited() {
		Map<String, WafTuningService.Hosts> hosts = Map.of(
				"s1", new WafTuningService.Hosts(List.of("a.com"), List.of(), List.of()),
				"s2", new WafTuningService.Hosts(List.of("b.com"), List.of(), List.of()));
		String y = render(List.of(s("rule", "942100", "inherit", "s1"), s("category", "crs-sqli", "count", "s2")), hosts);
		assertTrue(y.contains(" - filter: IsOutBand && lower(split(req.Host, \":\")[0]) in [\"a.com\", \"b.com\"]\n   apply:\n    - RemoveOutBandRuleByID(942100)\n"), y);
		assertTrue(y.contains(" - filter: IsOutBand && lower(split(req.Host, \":\")[0]) in [\"b.com\"]\n   apply:\n    - RemoveOutBandRuleByID(942200)\n"), y);
	}

	/** apply／hitsSince 的整合測試用：記憶體資料庫 + 暫存 home */
	static class Env {
		final InMemorySqlHelper db = new InMemorySqlHelper();
		final WafTuningService service = new WafTuningService();
		final Path wafDir;

		Env(Path home) {
			HomeConfig homeConfig = new HomeConfig();
			homeConfig.home = home.toString();
			OperateLogService logs = new OperateLogService();
			logs.sqlHelper = db;
			service.sqlHelper = db;
			service.homeConfig = homeConfig;
			service.operateLogService = logs;
			wafDir = home.resolve(WafTuningService.DIR);
		}

		void writeCatalog() throws Exception {
			Path catalog = wafDir.resolve("catalog");
			Files.createDirectories(catalog);
			Files.writeString(catalog.resolve("vpatch.json"), "[{\"name\":\"" + ENV + "\",\"label\":\"Generic .env access\"},{\"name\":\"" + GIT
					+ "\",\"label\":\"Generic .git access\"}]");
			Files.writeString(catalog.resolve("crs.tsv"), "942100\t1\tSQLi libinjection\n942200\t1\tSQLi comment\n");
		}

		List<WafRuleSetting> settings() {
			return db.findAll(WafRuleSetting.class);
		}
	}

	@Test
	void applyWithoutACatalogFails(@TempDir Path home) {
		Env env = new Env(home);
		assertThrows(WafTuningService.NoCatalogException.class, () -> env.service.apply(List.of(s("rule", ENV, "log", "")), "admin"));
		assertTrue(env.settings().isEmpty());
	}

	@Test
	void applyStoresTheSettingWritesTheConfigAndLogsIt(@TempDir Path home) throws Exception {
		Env env = new Env(home);
		env.writeCatalog();
		String id = env.service.apply(List.of(s("rule", ENV, "log", "")), "admin");
		assertEquals(1, env.settings().size());
		assertEquals(ENV, env.settings().get(0).getRuleKey());
		assertEquals("log", env.settings().get(0).getAction());
		String yaml = Files.readString(env.wafDir.resolve("custom.yaml"));
		assertTrue(yaml.contains("SetRemediation(\"allow\")"), yaml);
		assertEquals(id + "\n", Files.readString(env.wafDir.resolve("request")));
		List<OperateLog> logs = env.db.findAll(OperateLog.class);
		assertEquals(1, logs.size());
		assertEquals("admin", logs.get(0).getAdminName());
		assertTrue(logs.get(0).getAfterConf().startsWith("[CrowdSec WAF] apply " + id), logs.get(0).getAfterConf());
	}

	@Test
	void applyingTheDefaultDeletesTheStoredRow(@TempDir Path home) throws Exception {
		Env env = new Env(home);
		env.writeCatalog();
		env.service.apply(List.of(s("rule", ENV, "log", "")), "admin");
		assertEquals(1, env.settings().size());
		env.service.apply(List.of(s("rule", ENV, "inherit", "")), "admin");
		assertTrue(env.settings().isEmpty());
		assertFalse(Files.readString(env.wafDir.resolve("custom.yaml")).contains("SetRemediation"));
	}

	@Test
	void anInvalidChangeLeavesEverythingUntouched(@TempDir Path home) throws Exception {
		Env env = new Env(home);
		env.writeCatalog();
		env.service.apply(List.of(s("rule", ENV, "log", "")), "admin");
		String yamlBefore = Files.readString(env.wafDir.resolve("custom.yaml"));
		String requestBefore = Files.readString(env.wafDir.resolve("request"));
		assertThrows(IllegalArgumentException.class, () -> env.service.apply(List.of(s("rule", GIT, "off", ""), s("rule", "999999", "log", "")), "admin"));
		assertEquals(1, env.settings().size());
		assertEquals(ENV, env.settings().get(0).getRuleKey());
		assertEquals(yamlBefore, Files.readString(env.wafDir.resolve("custom.yaml")));
		assertEquals(requestBefore, Files.readString(env.wafDir.resolve("request")));
		assertEquals(1, env.db.findAll(OperateLog.class).size());
	}

	@Test
	void applyExpandsExcludedServersIntoHostConditions(@TempDir Path home) throws Exception {
		Env env = new Env(home);
		env.writeCatalog();
		Server server = new Server();
		server.setServerName("a.com");
		env.db.insert(server);
		env.service.apply(List.of(s("rule", "942100", "inherit", server.getId())), "admin");
		assertTrue(Files.readString(env.wafDir.resolve("custom.yaml")).contains("IsOutBand && lower(split(req.Host, \":\")[0]) in [\"a.com\"]"));
	}

	@Test
	void hitsSinceCountsEveryEventFromTheGivenTime(@TempDir Path home) {
		Env env = new Env(home);
		for (long t : new long[] { 100, 200, 300 }) {
			env.db.insert(hit(t, ENV));
		}
		env.db.insert(hit(300, "942100"));
		env.db.insert(hit(50, "942100"));
		Map<String, Integer> hits = env.service.hitsSince(200);
		assertEquals(Map.of(ENV, 2, "942100", 1), hits);
	}

	static WafEventRule hit(long time, String ruleName) {
		WafEventRule r = new WafEventRule();
		r.setEventTime(time);
		r.setRuleName(ruleName);
		return r;
	}
}
