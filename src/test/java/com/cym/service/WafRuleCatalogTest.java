package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WafRuleCatalogTest {
	static final String VPATCH = "[{\"name\":\"crowdsecurity/vpatch-CVE-2017-9841\",\"description\":\"PHPUnit RCE\",\"label\":\"PHPUnit - RCE\",\"cves\":[\"CVE-2017-9841\"]},"
			+ "{\"name\":\"crowdsecurity/vpatch-env-access\",\"description\":\"env\",\"label\":\"Generic .env access\",\"cves\":[]},"
			+ "{\"name\":\"\",\"label\":\"x - y\"},"
			+ "{\"name\":\"crowdsecurity/vpatch-bad\\\"name\",\"label\":\"Bad - quote\"}]";
	static final String CRS = "911011\t\t\n911100\t1\tMethod is not allowed by policy\n913100\t1\tFound User-Agent associated with security scanner\n"
			+ "942100\t1\tSQL Injection Attack Detected via libinjection\n942101\t2\tSQL Injection Attack Detected via libinjection\n942100\t1\tSQL Injection Attack Detected via libinjection\n"
			+ "944100\t\tRemote Command Execution: Suspicious Java class detected\nbad line\n";

	@Test
	void vpatchCategoryIsTheProductBeforeTheDash() {
		assertEquals("vpatch:Atlassian Confluence", WafRuleCatalog.vpatchCategory("Atlassian Confluence - Privilege Escalation"));
		assertEquals("vpatch:_other", WafRuleCatalog.vpatchCategory("Generic .env access"));
		assertEquals("vpatch:_other", WafRuleCatalog.vpatchCategory(null));
	}

	@Test
	void crsCategoryUsesTheFirstThreeDigits() {
		assertEquals("crs-protocol", WafRuleCatalog.crsCategory("911100"));
		assertEquals("crs-protocol", WafRuleCatalog.crsCategory("922110"));
		assertEquals("crs-scanner", WafRuleCatalog.crsCategory("913100"));
		assertEquals("crs-sqli", WafRuleCatalog.crsCategory("942100"));
		assertEquals("crs-java", WafRuleCatalog.crsCategory("944100"));
		assertEquals("crs-other", WafRuleCatalog.crsCategory("949110"));
	}

	@Test
	void parseKeepsPl1CrsRulesWithAMessage() {
		WafRuleCatalog.Catalog c = WafRuleCatalog.parse(VPATCH, CRS, "{\"generatedAt\":\"2026-10-24T00:00:00Z\",\"crowdsec\":\"v1.8.1\"}");
		List<String> keys = c.rules().stream().map(WafRuleCatalog.Rule::key).toList();
		assertEquals(List.of("crowdsecurity/vpatch-CVE-2017-9841", "crowdsecurity/vpatch-env-access", "911100", "913100", "942100", "944100"), keys);
		WafRuleCatalog.Rule php = c.byKey().get("crowdsecurity/vpatch-CVE-2017-9841");
		assertEquals("vpatch", php.source());
		assertEquals("vpatch:PHPUnit", php.category());
		assertEquals(List.of("CVE-2017-9841"), php.cves());
		assertEquals("SQL Injection Attack Detected via libinjection", c.byKey().get("942100").label());
		assertEquals("v1.8.1", c.crowdsec());
		assertEquals("2026-10-24T00:00:00Z", c.generatedAt());
	}

	@Test
	void loadReturnsNullWhenAFileIsMissing(@TempDir Path dir) throws Exception {
		assertNull(WafRuleCatalog.load(dir));
		Files.writeString(dir.resolve("vpatch.json"), VPATCH);
		assertNull(WafRuleCatalog.load(dir));
		Files.writeString(dir.resolve("crs.tsv"), CRS);
		WafRuleCatalog.Catalog c = WafRuleCatalog.load(dir);
		assertNotNull(c);
		assertNull(c.generatedAt());
	}

	@Test
	void loadReturnsNullOnBrokenJson(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("vpatch.json"), "[{");
		Files.writeString(dir.resolve("crs.tsv"), CRS);
		assertNull(WafRuleCatalog.load(dir));
	}
}
