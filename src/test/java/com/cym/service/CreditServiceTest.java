package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.testsupport.InMemorySqlHelper;

import cn.hutool.core.util.ReflectUtil;

/** creditKey 認證：空值與未知值拒絕，已知值放行。 */
class CreditServiceTest {
	private CreditService service;

	@BeforeEach
	void setUp() {
		service = new CreditService();
		ReflectUtil.setFieldValue(service, "sqlHelper", new InMemorySqlHelper());
	}

	@Test
	void nullAndEmptyKeysAreRejected() {
		assertFalse(service.check(null));
		assertFalse(service.check(""));
	}

	@Test
	void unknownKeyIsRejected() {
		service.make("1");
		assertFalse(service.check("not-a-known-key"));
	}

	@Test
	void keyFromMakeIsAccepted() {
		String key = service.make("1");
		assertTrue(service.check(key));
	}
}
