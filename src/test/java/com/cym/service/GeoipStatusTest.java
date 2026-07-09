package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.ext.GeoipStatus;

/**
 * 交叉驗證判定(距今基準,三條規則)邊界值測試。now 固定注入,不碰 IO/i18n。
 */
public class GeoipStatusTest {

	private static final long DAY = 24L * 60 * 60 * 1000;
	private static final long NOW = 1_800_000_000_000L;

	private static String at(long daysAgo) {
		return cn.hutool.core.date.DateUtil.format(new java.util.Date(NOW - daysAgo * DAY), "yyyy.MM.dd");
	}

	@Test
	void file6Days_ok() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 6 * DAY, at(0), NOW, false);
		assertEquals("ok", s.status());
		assertTrue(s.reasons().isEmpty());
	}

	@Test
	void file7DaysExact_ok() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 7 * DAY, at(0), NOW, false);
		assertEquals("ok", s.status());
	}

	@Test
	void file8Days_fileStale() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 8 * DAY, at(0), NOW, false);
		assertEquals("warn", s.status());
		assertEquals(1, s.reasons().size());
		assertEquals("fileStale", s.reasons().get(0).code());
		assertEquals(8, s.reasons().get(0).days());
	}

	@Test
	void build13Days_ok() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW, at(13), NOW, false);
		assertEquals("ok", s.status());
	}

	@Test
	void build15Days_buildStale() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW, at(15), NOW, false);
		assertEquals("warn", s.status());
		assertEquals("buildStale", s.reasons().get(0).code());
		assertEquals(15, s.reasons().get(0).days());
	}

	@Test
	void buildNullFileFresh_corrupt() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW, null, NOW, false);
		assertEquals("warn", s.status());
		assertEquals(1, s.reasons().size());
		assertEquals("corrupt", s.reasons().get(0).code());
	}

	@Test
	void bothStale_twoReasons() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 9 * DAY, at(20), NOW, false);
		assertEquals("warn", s.status());
		List<GeoipStatus.Reason> r = s.reasons();
		assertEquals(2, r.size());
		assertEquals("fileStale", r.get(0).code());
		assertEquals("buildStale", r.get(1).code());
	}

	@Test
	void cloudflare_onlyRule1() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 10 * DAY, null, NOW, true);
		assertEquals("warn", s.status());
		assertEquals(1, s.reasons().size());
		assertEquals("fileStale", s.reasons().get(0).code());
	}

	@Test
	void cloudflareFresh_ok() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 3 * DAY, null, NOW, true);
		assertEquals("ok", s.status());
	}
}
