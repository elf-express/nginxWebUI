package com.cym.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.model.Param;

/**
 * Template.def multi-select + safety lock (pure functions).
 */
public class TemplateDefUtilsTest {

	private Param p(String name) {
		Param x = new Param();
		x.setName(name);
		return x;
	}

	@Test
	void normalize_multiValueSortedLowercase() {
		assertEquals("http,server,location", TemplateDefUtils.normalize("location, HTTP ,server"));
		assertEquals("", TemplateDefUtils.normalize(null));
		assertEquals("", TemplateDefUtils.normalize("bogus"));
	}

	@Test
	void contains_multi() {
		assertTrue(TemplateDefUtils.contains("http,server", "server"));
		assertFalse(TemplateDefUtils.contains("http,server", "stream"));
	}

	@Test
	void allowedContexts_if_onlyServerLocation() {
		List<String> a = TemplateDefUtils.allowedContexts(Arrays.asList(p("if")));
		assertEquals(Arrays.asList("server", "location"), a);
	}

	@Test
	void allowedContexts_limitReqZone_onlyHttp() {
		List<String> a = TemplateDefUtils.allowedContexts(Arrays.asList(p("limit_req_zone")));
		assertEquals(Arrays.asList("http"), a);
	}

	@Test
	void allowedContexts_sslPreread_streamStack() {
		List<String> a = TemplateDefUtils.allowedContexts(Arrays.asList(p("ssl_preread")));
		assertEquals(Arrays.asList("stream", "server1", "server2"), a);
	}

	@Test
	void allowedContexts_httpAndStreamConflict_empty() {
		List<String> a = TemplateDefUtils.allowedContexts(Arrays.asList(p("if"), p("ssl_preread")));
		assertTrue(a.isEmpty());
	}

	@Test
	void normalizeAndFilter_stripsStreamWhenIfPresent() {
		String out = TemplateDefUtils.normalizeAndFilter("server,stream,server1", Arrays.asList(p("if")));
		assertEquals("server", out);
	}

	@Test
	void isSafeForStreamServer_blocksHttpOnly() {
		assertFalse(TemplateDefUtils.isSafeForStreamServer("if"));
		assertFalse(TemplateDefUtils.isSafeForStreamServer("proxy_set_header"));
		assertFalse(TemplateDefUtils.isSafeForStreamServer("add_header"));
		assertTrue(TemplateDefUtils.isSafeForStreamServer("limit_conn"));
		assertTrue(TemplateDefUtils.isSafeForStreamServer("proxy_timeout"));
	}

	@Test
	void normalizeDirectiveName_ifFragment() {
		assertEquals("if", TemplateDefUtils.normalizeDirectiveName("if ($x) {"));
		assertEquals("if_modified_since", TemplateDefUtils.normalizeDirectiveName("if_modified_since"));
	}

	@Test
	void emptyParams_allContexts() {
		assertEquals(TemplateDefUtils.ALL, TemplateDefUtils.allowedContexts(new ArrayList<>()));
	}
}
