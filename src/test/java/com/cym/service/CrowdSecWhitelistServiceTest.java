package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.DenyAllow;
import com.cym.model.OperateLog;
import com.cym.testsupport.InMemorySqlHelper;

class CrowdSecWhitelistServiceTest {
	private InMemorySqlHelper sql;
	private CrowdSecWhitelistService svc;
	private final List<String> deletedFor = new ArrayList<>();
	private final Map<String, String> settingStore = new HashMap<>();
	private boolean configured = true;
	private RuntimeException failure;

	@BeforeEach
	void setUp() {
		sql = new InMemorySqlHelper();
		DenyAllowService das = new DenyAllowService();
		das.sqlHelper = sql;
		OperateLogService ols = new OperateLogService();
		ols.sqlHelper = sql;
		SettingService settings = new SettingService() {
			@Override
			public String get(String key) {
				return settingStore.get(key);
			}

			@Override
			public void set(String key, String value) {
				settingStore.put(key, value);
			}
		};
		svc = new CrowdSecWhitelistService();
		svc.sqlHelper = sql;
		svc.denyAllowService = das;
		svc.operateLogService = ols;
		svc.settingService = settings;
		svc.crowdsecReady = () -> configured;
		svc.deleteIp = ip -> {
			if (failure != null) {
				throw failure;
			}
			deletedFor.add(ip);
			return new CrowdSecClient.DeleteResult(1, 1);
		};
	}

	private List<DenyAllow> allows() {
		return sql.findAll(DenyAllow.class).stream().filter(d -> "allow".equals(d.getType())).toList();
	}

	@Test
	void deletesDecisionsAndAddsAllowlist() {
		CrowdSecWhitelistService.Outcome o = svc.whitelist("203.0.113.5", "admin");
		assertEquals("added", o.denyAllow());
		assertEquals("ok", o.crowdsec());
		assertEquals(1, o.deleted());
		assertEquals(1, o.rangesKept());
		assertEquals(List.of("203.0.113.5"), deletedFor);
		assertEquals("203.0.113.5", allows().get(0).getIp());
		assertEquals(1, sql.findAll(OperateLog.class).size());
	}

	@Test
	void existingAllowEntryNotDuplicated() {
		DenyAllow da = new DenyAllow();
		da.setType("allow");
		da.setName("office");
		da.setIp("203.0.113.0/24");
		sql.insertOrUpdate(da);
		assertEquals("exists", svc.whitelist("203.0.113.5", "admin").denyAllow());
		assertEquals(1, allows().size());
	}

	private void allow(String name, String ips) {
		DenyAllow da = new DenyAllow();
		da.setType("allow");
		da.setName(name);
		da.setIp(ips);
		sql.insertOrUpdate(da);
	}

	@Test
	void slashZeroAllAndGarbageLinesDoNotCountAsAlreadyAllowed() {
		// ConfService.collectIps 略過 all、/0 與非法行，nginx 的白名單裡沒有這些，所以照常新增
		allow("world v4", "0.0.0.0/0");
		allow("world v6 and junk", "::/0\nall\n203.0.113.5/abc\nnot-an-ip");
		CrowdSecWhitelistService.Outcome o = svc.whitelist("203.0.113.5", "admin");
		assertEquals("added", o.denyAllow());
		assertEquals(3, allows().size());
		assertTrue(allows().stream().anyMatch(a -> "203.0.113.5".equals(a.getIp())));
	}

	@Test
	void normalRangeNextToSlashZeroStillCountsAsAllowed() {
		allow("mixed", "0.0.0.0/0\n203.0.113.0/24");
		assertEquals("exists", svc.whitelist("203.0.113.5", "admin").denyAllow());
		assertEquals(1, allows().size());
	}

	@Test
	void crowdsecNotConfiguredStillWritesAllowlist() {
		configured = false;
		CrowdSecWhitelistService.Outcome o = svc.whitelist("203.0.113.5", "admin");
		assertEquals("notConfigured", o.crowdsec());
		assertEquals("added", o.denyAllow());
		assertTrue(deletedFor.isEmpty());
	}

	@Test
	void crowdsecFailureStillWritesAllowlist() {
		failure = new CrowdSecClient.MachineAuthException();
		CrowdSecWhitelistService.Outcome o = svc.whitelist("203.0.113.5", "admin");
		assertEquals("failed", o.crowdsec());
		assertEquals(CrowdSecClient.MachineAuthException.CODE, o.error());
		assertEquals(1, allows().size());
	}

	@Test
	void lapiUnavailableFailureCarriesItsOwnErrorCode() {
		failure = new CrowdSecClient.LapiUnavailableException("down");
		CrowdSecWhitelistService.Outcome o = svc.whitelist("203.0.113.5", "admin");
		assertEquals("failed", o.crowdsec());
		assertEquals(CrowdSecClient.LapiUnavailableException.CODE, o.error());
	}

	private void deny(String name, String ips) {
		DenyAllow da = new DenyAllow();
		da.setType("deny");
		da.setName(name);
		da.setIp(ips);
		sql.insertOrUpdate(da);
	}

	@Test
	void reportsDenylistsHoldingTheExactIpWithoutTouchingThem() {
		deny("bad ips", "198.51.100.1\n203.0.113.5\n");
		deny("bad range", "203.0.113.0/24");
		deny("other", "203.0.113.50");
		CrowdSecWhitelistService.Outcome o = svc.whitelist("203.0.113.5", "admin");
		assertEquals(List.of("bad ips"), o.denyConflicts());
		assertEquals("added", o.denyAllow());
		// 不改使用者的黑名單
		List<String> denyIps = sql.findAll(DenyAllow.class).stream().filter(d -> "deny".equals(d.getType()))
				.map(DenyAllow::getIp).toList();
		assertTrue(denyIps.contains("198.51.100.1\n203.0.113.5\n"));
		assertEquals(3, denyIps.size());
	}

	@Test
	void noDenyConflictsWhenNoListHasTheExactIp() {
		deny("bad range", "203.0.113.0/24");
		assertTrue(svc.whitelist("203.0.113.5", "admin").denyConflicts().isEmpty());
	}

	@Test
	void rejectsCidrAndGarbage() {
		assertThrows(IllegalArgumentException.class, () -> svc.whitelist("203.0.113.0/24", "admin"));
		assertThrows(IllegalArgumentException.class, () -> svc.whitelist("example.com", "admin"));
		assertTrue(allows().isEmpty());
	}

	@Test
	void legacyCleanupSetsFlagOnlyOnSuccess() {
		svc.deleteLegacy = () -> {
			throw new CrowdSecClient.LapiUnavailableException("down");
		};
		assertFalse(svc.cleanupLegacyWhitelistDecisions());
		svc.deleteLegacy = () -> 2;
		assertTrue(svc.cleanupLegacyWhitelistDecisions());
		svc.deleteLegacy = () -> {
			throw new AssertionError("must not run again");
		};
		assertTrue(svc.cleanupLegacyWhitelistDecisions());
	}
}
