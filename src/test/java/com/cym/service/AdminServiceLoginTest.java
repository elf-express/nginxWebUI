package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.Admin;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.utils.EncodePassUtils;

import cn.hutool.core.util.ReflectUtil;

/** 登入：bcrypt 驗證與舊 MD5 雜湊登入成功後的自動升級。 */
class AdminServiceLoginTest {
	private InMemorySqlHelper sql;
	private AdminService service;

	@BeforeEach
	void setUp() {
		sql = new InMemorySqlHelper();
		service = new AdminService();
		ReflectUtil.setFieldValue(service, "sqlHelper", sql);
	}

	private Admin addAdmin(String name, String storedPass) {
		Admin admin = new Admin();
		admin.setName(name);
		admin.setPass(storedPass);
		sql.insert(admin);
		return admin;
	}

	private String storedPass(Admin admin) {
		return sql.findById(admin.getId(), Admin.class).getPass();
	}

	@Test
	void sameRawPasswordGivesDifferentStoredHashesAndBothLogin() {
		Admin a = addAdmin("alice", EncodePassUtils.encode("Admin123"));
		Admin b = addAdmin("bob", EncodePassUtils.encode("Admin123"));

		assertNotEquals(storedPass(a), storedPass(b));
		assertNotNull(service.login("alice", "Admin123"));
		assertNotNull(service.login("bob", "Admin123"));
	}

	@Test
	void legacyHashIsUpgradedOnSuccessfulLogin() {
		Admin admin = addAdmin("alice", EncodePassUtils.encodeLegacy("Admin123"));

		Admin logged = service.login("alice", "Admin123");

		assertNotNull(logged);
		assertEquals(admin.getId(), logged.getId());
		assertFalse(EncodePassUtils.needsUpgrade(storedPass(admin)));
		assertNotNull(service.login("alice", "Admin123"));
		assertNull(service.login("alice", "Wrong123"));
	}

	@Test
	void wrongPasswordLeavesLegacyHashUntouched() {
		String legacy = EncodePassUtils.encodeLegacy("Admin123");
		Admin admin = addAdmin("alice", legacy);

		assertNull(service.login("alice", "Wrong123"));
		assertEquals(legacy, storedPass(admin));
	}

	@Test
	void unknownOrEmptyCredentialsReturnNull() {
		addAdmin("alice", EncodePassUtils.encode("Admin123"));

		assertNull(service.login("nobody", "Admin123"));
		assertNull(service.login("", "Admin123"));
		assertNull(service.login(null, "Admin123"));
		assertNull(service.login("alice", ""));
		assertNull(service.login("alice", null));
	}
}
