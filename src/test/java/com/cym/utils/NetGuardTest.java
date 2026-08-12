package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class NetGuardTest {
	@Test
	public void cidr_ipv4_ok() {
		assertTrue(NetGuard.isValidCidr("1.2.3.0/24"));
		assertTrue(NetGuard.isValidCidr("8.8.8.8"));
		assertFalse(NetGuard.isValidCidr("all"));
		assertFalse(NetGuard.isValidCidr("1.2.3.0/33"));
		assertFalse(NetGuard.isValidCidr("not-a-cidr"));
		assertFalse(NetGuard.isValidCidr("1.2.3.0/24 evil"));
	}

	@Test
	public void cidr_ipv6_ok() {
		assertTrue(NetGuard.isValidCidr("2001:db8::/32"));
		assertTrue(NetGuard.isValidCidr("::1"));
		assertFalse(NetGuard.isValidCidr("gggg::/32"));
		assertFalse(NetGuard.isValidCidr("2001:db8::/ 32"));
		assertFalse(NetGuard.isValidCidr("2001:db8::/+32"));
		assertFalse(NetGuard.isValidCidr("example.com"));
	}

	@Test
	public void duration_ok() {
		assertTrue(NetGuard.isValidDuration("24h"));
		assertTrue(NetGuard.isValidDuration("30m"));
		assertTrue(NetGuard.isValidDuration("7d"));
		assertTrue(NetGuard.isValidDuration("60"));
		assertFalse(NetGuard.isValidDuration(""));
		assertFalse(NetGuard.isValidDuration("24 hours"));
		assertFalse(NetGuard.isValidDuration("-1h"));
		assertTrue(NetGuard.isBlankOrValidDuration(""));
		assertTrue(NetGuard.isBlankOrValidDuration(null));
		assertFalse(NetGuard.isBlankOrValidDuration("xx"));
	}

	@Test
	public void reason_prefix() {
		assertTrue(NetGuard.isAllowedWebuiReason("nginxwebui:as-ban:AS1"));
		assertTrue(NetGuard.isAllowedWebuiReason("nginxwebui:fp-whitelist"));
		assertTrue(NetGuard.isAllowedWebuiReason("  nginxwebui:x  "));
		assertFalse(NetGuard.isAllowedWebuiReason("cscli manual"));
		assertFalse(NetGuard.isAllowedWebuiReason(""));
		assertFalse(NetGuard.isAllowedWebuiReason(null));
	}
}
