package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import cn.hutool.crypto.SecureUtil;

/** 管理員密碼雜湊：bcrypt 與舊版 MD5 格式的相容。 */
class EncodePassUtilsTest {

	@Test
	void encodeProducesDifferentSaltedBcryptHashes() {
		String a = EncodePassUtils.encode("Admin123");
		String b = EncodePassUtils.encode("Admin123");

		assertNotEquals(a, b);
		for (String hash : new String[] { a, b }) {
			assertTrue(hash.startsWith("$2a$12$"), hash);
			assertEquals(60, hash.length());
			assertTrue(EncodePassUtils.matches("Admin123", hash));
		}
	}

	@Test
	void matchesRejectsWrongPassword() {
		assertFalse(EncodePassUtils.matches("Wrong123", EncodePassUtils.encode("Admin123")));
	}

	@Test
	void legacyHashStillVerifies() {
		String legacy = EncodePassUtils.encodeLegacy("Admin123");

		assertEquals(SecureUtil.md5("Admin123") + SecureUtil.md5("nginxWebUI"), legacy);
		assertTrue(EncodePassUtils.matches("Admin123", legacy));
		assertFalse(EncodePassUtils.matches("Wrong123", legacy));
	}

	@Test
	void needsUpgradeOnlyForNonBcryptValues() {
		assertTrue(EncodePassUtils.needsUpgrade(EncodePassUtils.encodeLegacy("Admin123")));
		assertFalse(EncodePassUtils.needsUpgrade(EncodePassUtils.encode("Admin123")));
		assertFalse(EncodePassUtils.needsUpgrade(""));
		assertFalse(EncodePassUtils.needsUpgrade(null));
	}

	@Test
	void bcryptVariantPrefixesAreRecognized() {
		String hash = EncodePassUtils.encode("Admin123");

		for (String prefix : new String[] { "$2b$", "$2y$" }) {
			String variant = prefix + hash.substring(4);
			assertFalse(EncodePassUtils.needsUpgrade(variant), variant);
			assertTrue(EncodePassUtils.matches("Admin123", variant), variant);
			assertFalse(EncodePassUtils.matches("Wrong123", variant), variant);
		}
	}

	@Test
	void matchesHandlesEmptyAndBrokenInput() {
		String hash = EncodePassUtils.encode("Admin123");

		assertFalse(EncodePassUtils.matches(null, hash));
		assertFalse(EncodePassUtils.matches("", hash));
		assertFalse(EncodePassUtils.matches("Admin123", null));
		assertFalse(EncodePassUtils.matches("Admin123", ""));
		assertFalse(EncodePassUtils.matches("Admin123", "$2a$12$broken"));
	}

	@Test
	void encodeKeepsEmptyValues() {
		assertEquals("", EncodePassUtils.encode(""));
		assertNull(EncodePassUtils.encode(null));
	}
}
