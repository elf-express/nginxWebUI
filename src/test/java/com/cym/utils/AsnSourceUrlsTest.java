package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class AsnSourceUrlsTest {
	@Test
	public void metaAndPrefixUrls_exact() {
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json",
			AsnSourceUrls.META_JSON_URL);
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv",
			AsnSourceUrls.META_CSV_URL);
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/51167/ipv4-aggregated.txt",
			AsnSourceUrls.prefixIpv4Url("51167"));
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/51167/ipv6-aggregated.txt",
			AsnSourceUrls.prefixIpv6Url("51167"));
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/51167/aggregated.json",
			AsnSourceUrls.prefixAggregatedJsonUrl("51167"));
		assertEquals("https://lens.ipverse.net/", AsnSourceUrls.LENS_HOME);
		assertEquals("https://github.com/ipverse/as-metadata", AsnSourceUrls.REPO_AS_METADATA);
		assertEquals("https://github.com/ipverse/as-ip-blocks", AsnSourceUrls.REPO_AS_IP_BLOCKS);
		assertEquals("https://github.com/ipverse/tools", AsnSourceUrls.REPO_TOOLS);
		assertEquals(
			"https://github.com/ipverse/tools/blob/main/crowdsec/README.md",
			AsnSourceUrls.REPO_TOOLS_CROWDSEC_README);
		assertEquals(
			"https://github.com/ipverse/as-ip-blocks/releases/latest/download/as-ip-blocks.tar.gz",
			AsnSourceUrls.AS_IP_BLOCKS_TARBALL);
		// forbidden legacy fragment must not appear in builders
		assertFalse(AsnSourceUrls.prefixIpv4Url("1").contains("asn-ip"));
	}
}
