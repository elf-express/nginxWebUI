package com.cym.utils;

import java.util.regex.Pattern;

import cn.hutool.core.util.StrUtil;

/**
 * Input guards for CrowdSec / ASN ban paths (pure static).
 * Spec: docs/superpowers/specs/2026-08-13-asn-security-harden-design.md
 */
public final class NetGuard {
	private NetGuard() {}

	public static final String ERR_INVALID_CIDR = "invalid_cidr";
	public static final String ERR_INVALID_DURATION = "invalid_duration";
	public static final String ERR_INVALID_REASON = "invalid_reason";

	private static final Pattern DURATION = Pattern.compile("^\\d+[smhdwMy]?$");
	// IPv4 with optional /0-32
	private static final Pattern IPV4 = Pattern.compile(
			"^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(/([0-9]|[12]\\d|3[0-2]))?$");
	// IPv6 host: only hex digits and colon (no DNS hostnames)
	private static final Pattern IPV6_HOST = Pattern.compile("(?i)^[0-9a-f:]+$");

	public static boolean isValidCidr(String s) {
		if (StrUtil.isBlank(s)) {
			return false;
		}
		String t = s.trim();
		if (t.indexOf(' ') >= 0 || t.indexOf('\t') >= 0) {
			return false;
		}
		if ("all".equalsIgnoreCase(t)) {
			return false;
		}
		int slash = t.indexOf('/');
		String host = slash >= 0 ? t.substring(0, slash) : t;
		String pref = slash >= 0 ? t.substring(slash + 1) : null;
		if (pref != null) {
			// prefix must be digits only (reject "/ 32", "/+32")
			if (!pref.matches("^\\d+$")) {
				return false;
			}
			try {
				int p = Integer.parseInt(pref);
				if (p < 0) {
					return false;
				}
			} catch (NumberFormatException e) {
				return false;
			}
		}
		if (IPV4.matcher(t).matches()) {
			return true;
		}
		// IPv6: hex+colon only — never DNS
		if (!host.contains(":")) {
			return false;
		}
		if (!IPV6_HOST.matcher(host).matches()) {
			return false;
		}
		try {
			java.net.InetAddress addr = java.net.InetAddress.getByName(host);
			if (!(addr instanceof java.net.Inet6Address)) {
				return false;
			}
			if (pref == null) {
				return true;
			}
			int p = Integer.parseInt(pref);
			return p >= 0 && p <= 128;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isValidDuration(String s) {
		if (s == null) {
			return false;
		}
		return DURATION.matcher(s.trim()).matches();
	}

	public static boolean isBlankOrValidDuration(String s) {
		return StrUtil.isBlank(s) || isValidDuration(s);
	}

	public static boolean isAllowedWebuiReason(String reason) {
		if (reason == null) {
			return false;
		}
		String r = reason.trim();
		return StrUtil.isNotBlank(r) && r.startsWith("nginxwebui:");
	}
}
