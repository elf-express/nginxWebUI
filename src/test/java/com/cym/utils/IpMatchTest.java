package com.cym.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpMatchTest {
	@Test
	void ipv4() {
		assertTrue(IpMatch.contains("203.0.113.0/24", "203.0.113.77"));
		assertFalse(IpMatch.contains("203.0.113.0/24", "203.0.114.1"));
		assertTrue(IpMatch.contains("203.0.113.77", "203.0.113.77"));
		assertTrue(IpMatch.contains("203.0.113.77/32", "203.0.113.77"));
		assertFalse(IpMatch.contains("203.0.113.78", "203.0.113.77"));
	}

	@Test
	void ipv6() {
		assertTrue(IpMatch.contains("2001:db8::/32", "2001:db8:1::5"));
		assertFalse(IpMatch.contains("2001:db8::/32", "2001:db9::1"));
		assertTrue(IpMatch.contains("2001:db8::1", "2001:0db8:0:0:0:0:0:1"));
	}

	@Test
	void mixedFamiliesAndGarbageNeverMatch() {
		assertFalse(IpMatch.contains("203.0.113.0/24", "2001:db8::1"));
		assertFalse(IpMatch.contains("example.com", "203.0.113.1"));
		assertFalse(IpMatch.contains("203.0.113.0/24", "not-an-ip"));
		assertFalse(IpMatch.contains(null, "203.0.113.1"));
	}

	@Test
	void singleIp() {
		assertTrue(IpMatch.isSingleIp("203.0.113.1"));
		assertTrue(IpMatch.isSingleIp("2001:db8::1"));
		assertFalse(IpMatch.isSingleIp("203.0.113.0/24"));
		assertFalse(IpMatch.isSingleIp("example.com"));
		assertFalse(IpMatch.isSingleIp(" "));
	}
}
