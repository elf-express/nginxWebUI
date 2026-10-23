package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.model.Server;
import com.cym.testsupport.InMemorySqlHelper;

class IpLookupServiceTest {
	private InMemorySqlHelper sql;
	private IpLookupService svc;
	private String country = "CN";
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
		svc.countryOf = ip -> Optional.ofNullable(country);
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
		country = null;
		geo(null, 0, "TW");
		assertEquals("unknown", layer(svc.lookup("203.0.113.5"), "geo").state());
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
