package com.cym.utils;

import java.net.InetAddress;

import cn.hutool.core.util.StrUtil;

/** IPv4／IPv6 位址與 CIDR 包含判斷；先過 NetGuard，不做 DNS 解析。 */
public final class IpMatch {
	private IpMatch() {
	}

	public static boolean isSingleIp(String s) {
		return StrUtil.isNotBlank(s) && s.trim().indexOf('/') < 0 && NetGuard.isValidCidr(s.trim());
	}

	public static boolean contains(String cidrOrIp, String ip) {
		if (!NetGuard.isValidCidr(cidrOrIp) || !isSingleIp(ip)) {
			return false;
		}
		String c = cidrOrIp.trim();
		int slash = c.indexOf('/');
		byte[] net = bytes(slash < 0 ? c : c.substring(0, slash));
		byte[] addr = bytes(ip.trim());
		if (net == null || addr == null || net.length != addr.length) {
			return false;
		}
		int prefix = slash < 0 ? net.length * 8 : Integer.parseInt(c.substring(slash + 1));
		if (prefix > net.length * 8) {
			return false;
		}
		for (int i = 0; i < prefix; i++) {
			int mask = 0x80 >> (i % 8);
			if ((net[i / 8] & mask) != (addr[i / 8] & mask)) {
				return false;
			}
		}
		return true;
	}

	private static byte[] bytes(String literal) {
		try {
			return InetAddress.getByName(literal).getAddress();
		} catch (Exception e) {
			return null;
		}
	}
}
