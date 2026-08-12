package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.service.AsnMetaService.AsMetaRow;

import cn.hutool.core.io.IoUtil;

/**
 * Fixture-only parse tests for AsnMetaService — no network, no Solon boot.
 */
public class AsnMetaParseTest {

	@Test
	public void parseSample_hasHostingAndFields() {
		InputStream in = getClass().getResourceAsStream("/asn/as-meta-sample.json");
		assertNotNull(in);
		String json = IoUtil.read(in, StandardCharsets.UTF_8);
		List<AsMetaRow> rows = AsnMetaService.parseAsJson(json);
		assertTrue(rows.size() >= 2);
		AsMetaRow contabo = rows.stream().filter(r -> "51167".equals(r.asn)).findFirst().orElse(null);
		assertNotNull(contabo);
		assertEquals("hosting", contabo.category);
		assertEquals("DE", contabo.countryCode);
		assertEquals("CONTABO", contabo.handle);
		assertEquals("Contabo GmbH", contabo.description);
		assertEquals("stub", contabo.networkRole);
		assertEquals("authoritative", contabo.origin);
		assertEquals("2026-08-11", contabo.lastAnnounced);
	}

	@Test
	public void parseSample_skipsBlankAsn_keepsZero() {
		String json = "[{\"asn\":\"\",\"metadata\":{\"handle\":\"X\"}},{\"asn\":0,\"metadata\":{\"handle\":\"Z\",\"countryCode\":\"ZZ\"}}]";
		List<AsMetaRow> rows = AsnMetaService.parseAsJson(json);
		assertEquals(1, rows.size());
		assertEquals("0", rows.get(0).asn);
		assertEquals("Z", rows.get(0).handle);
		assertEquals("ZZ", rows.get(0).countryCode);
	}

	@Test
	public void parseSample_nullMetadata_ok() {
		String json = "[{\"asn\":1,\"lastAnnounced\":\"2026-01-01\"}]";
		List<AsMetaRow> rows = AsnMetaService.parseAsJson(json);
		assertEquals(1, rows.size());
		assertEquals("1", rows.get(0).asn);
		assertNull(rows.get(0).handle);
		assertNull(rows.get(0).category);
		assertEquals("2026-01-01", rows.get(0).lastAnnounced);
	}
}
