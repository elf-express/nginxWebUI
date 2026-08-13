package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Pure static helpers for AsnBlockService — no DB / Solon.
 */
public class AsnBlockServiceTest {

	@Test
	public void reasonTag_format() {
		assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("51167"));
		assertEquals("nginxwebui:as-ban:AS0", AsnBlockService.reasonTagForAsn("0"));
		assertEquals("nginxwebui:as-ban:AS123", AsnBlockService.reasonTagForAsn("  123  "));
	}

	@Test
	public void reasonTag_acceptsAsPrefix() {
		assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("AS51167"));
		assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("51167"));
		assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("as51167"));
		assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn(" AS51167 "));
	}

	@Test
	public void reasonTag_rejectsNonDigits() {
		IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
				() -> AsnBlockService.reasonTagForAsn(""));
		assertEquals("invalid_asn", e1.getMessage());
		IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
				() -> AsnBlockService.reasonTagForAsn(null));
		assertEquals("invalid_asn", e2.getMessage());
		IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class,
				() -> AsnBlockService.reasonTagForAsn("51-167"));
		assertEquals("invalid_asn", e3.getMessage());
		assertThrows(IllegalArgumentException.class, () -> AsnBlockService.reasonTagForAsn("AS"));
		assertThrows(IllegalArgumentException.class, () -> AsnBlockService.reasonTagForAsn("ASN51167"));
	}

	@Test
	public void light_cannotCreateIntent() {
		assertFalse(AsnBlockService.canCreateIntentForProfile("light"));
		assertTrue(AsnBlockService.canCreateIntentForProfile("manual"));
		assertTrue(AsnBlockService.canCreateIntentForProfile("strict"));
		assertFalse(AsnBlockService.canCreateIntentForProfile(null));
		assertFalse(AsnBlockService.canCreateIntentForProfile(""));
		assertFalse(AsnBlockService.canCreateIntentForProfile("other"));
	}

	@Test
	public void suggestCandidates_strictOnly() {
		assertTrue(AsnBlockService.canSuggestCandidatesForProfile("strict"));
		assertFalse(AsnBlockService.canSuggestCandidatesForProfile("manual"));
		assertFalse(AsnBlockService.canSuggestCandidatesForProfile("light"));
		assertFalse(AsnBlockService.canSuggestCandidatesForProfile(null));
	}

	@Test
	public void normalizeProfile_defaultsToLight() {
		assertEquals("light", AsnBlockService.normalizeProfile(null));
		assertEquals("light", AsnBlockService.normalizeProfile(""));
		assertEquals("light", AsnBlockService.normalizeProfile("heavy"));
		assertEquals("light", AsnBlockService.normalizeProfile("LIGHT"));
		assertEquals("light", AsnBlockService.normalizeProfile("light"));
		assertEquals("manual", AsnBlockService.normalizeProfile("manual"));
		assertEquals("strict", AsnBlockService.normalizeProfile("strict"));
	}

	@Test
	public void isValidProfile_exactCaseSensitive() {
		assertTrue(AsnBlockService.isValidProfile("light"));
		assertTrue(AsnBlockService.isValidProfile("manual"));
		assertTrue(AsnBlockService.isValidProfile("strict"));
		assertFalse(AsnBlockService.isValidProfile(null));
		assertFalse(AsnBlockService.isValidProfile(""));
		assertFalse(AsnBlockService.isValidProfile("heavy"));
		assertFalse(AsnBlockService.isValidProfile("LIGHT"));
		assertFalse(AsnBlockService.isValidProfile("Manual"));
		assertFalse(AsnBlockService.isValidProfile(" STRICT "));
	}
}
