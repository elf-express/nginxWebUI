package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.model.Server;

/**
 * type 反查歸類(純函式)測試。findConflictIps 依賴 DB,由 E2E 驗;此處只測 resolveTypeByReference。
 */
public class DenyAllowTypeTest {

	private Server server(String denyId, String allowId) {
		Server s = new Server();
		s.setDenyId(denyId);
		s.setAllowId(allowId);
		return s;
	}

	@Test
	void referencedByAllowId_allow() {
		List<Server> servers = new ArrayList<>();
		servers.add(server(null, "da1,da2"));
		assertEquals("allow", DenyAllowService.resolveTypeByReference("da1", servers, null, null, null, null));
	}

	@Test
	void referencedByDenyId_deny() {
		List<Server> servers = new ArrayList<>();
		servers.add(server("da1", null));
		assertEquals("deny", DenyAllowService.resolveTypeByReference("da1", servers, null, null, null, null));
	}

	@Test
	void notReferenced_defaultDeny() {
		assertEquals("deny", DenyAllowService.resolveTypeByReference("da9", new ArrayList<>(), null, null, null, null));
	}

	@Test
	void referencedByHttpAllowGlobal_allow() {
		assertEquals("allow", DenyAllowService.resolveTypeByReference("da1", new ArrayList<>(), null, "da1", null, null));
	}

	@Test
	void conflictBothDenyAndAllow_deny() {
		// 同時被 server allowId 與 http global denyId 引用(矛盾)→ 歸 deny
		List<Server> servers = new ArrayList<>();
		servers.add(server(null, "da1"));
		assertEquals("deny", DenyAllowService.resolveTypeByReference("da1", servers, "da1", null, null, null));
	}
}
