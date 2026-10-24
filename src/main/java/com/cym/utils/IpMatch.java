package com.cym.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import cn.hutool.core.util.StrUtil;

/** IPv4／IPv6 位址與 CIDR 包含判斷；先過 NetGuard，不做 DNS 解析。 */
public final class IpMatch {
	private IpMatch() {
	}

	public static boolean isSingleIp(String s) {
		return StrUtil.isNotBlank(s) && s.trim().indexOf('/') < 0 && NetGuard.isValidCidr(s.trim());
	}

	/** 兩個單一 IP 是否為同一個位址（依位元組比對，IPv6 的壓縮、完整、大小寫寫法視為相同）；任一不是單一 IP 回 false */
	public static boolean sameIp(String a, String b) {
		return isSingleIp(a) && isSingleIp(b) && contains(a.trim(), b);
	}

	/**
	 * 單一 IP 的固定寫法（InetAddress.getHostAddress：IPv4 原樣，IPv6 完整小寫不壓縮），資料庫等值查詢用；
	 * 不是單一 IP（含 NetGuard 不收的寫法）回 null
	 */
	public static String canonical(String ip) {
		if (!isSingleIp(ip)) {
			return null;
		}
		byte[] b = bytes(ip.trim());
		try {
			return b == null ? null : InetAddress.getByAddress(b).getHostAddress();
		} catch (UnknownHostException e) {
			return null;
		}
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
		int prefix;
		try {
			prefix = slash < 0 ? net.length * 8 : Integer.parseInt(c.substring(slash + 1));
		} catch (NumberFormatException e) {
			return false;
		}
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
