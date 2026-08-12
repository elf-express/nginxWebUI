package com.cym.utils;

/**
 * Canonical external sources for ASN catalog / big-block prefixes.
 * Spec: docs/superpowers/specs/2026-08-12-ipverse-asn-crowdsec-design.md §11
 * Do not use legacy repo path ipverse/asn-ip.
 */
public final class AsnSourceUrls {
	private AsnSourceUrls() {}

	public static final String LENS_HOME = "https://lens.ipverse.net/";
	public static final String REPO_AS_METADATA = "https://github.com/ipverse/as-metadata";
	public static final String REPO_AS_IP_BLOCKS = "https://github.com/ipverse/as-ip-blocks";
	public static final String REPO_TOOLS = "https://github.com/ipverse/tools";
	public static final String REPO_TOOLS_CROWDSEC_README =
			"https://github.com/ipverse/tools/blob/main/crowdsec/README.md";
	public static final String REPO_COUNTRY_IP_BLOCKS = "https://github.com/ipverse/country-ip-blocks";
	public static final String REPO_GEO_IP_BLOCKS = "https://github.com/ipverse/geo-ip-blocks";
	public static final String REPO_AS_OVERLAY = "https://github.com/ipverse/as-overlay";

	/** Primary full catalog (includes category / networkRole). */
	public static final String META_JSON_URL =
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json";
	/** Lightweight 4-column catalog (no category). */
	public static final String META_CSV_URL =
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv";

	public static final String AS_IP_BLOCKS_TARBALL =
			"https://github.com/ipverse/as-ip-blocks/releases/latest/download/as-ip-blocks.tar.gz";

	private static final String AS_IP_BLOCKS_AS_BASE =
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/";

	public static String prefixIpv4Url(String asn) {
		return AS_IP_BLOCKS_AS_BASE + digits(asn) + "/ipv4-aggregated.txt";
	}

	public static String prefixIpv6Url(String asn) {
		return AS_IP_BLOCKS_AS_BASE + digits(asn) + "/ipv6-aggregated.txt";
	}

	public static String prefixAggregatedJsonUrl(String asn) {
		return AS_IP_BLOCKS_AS_BASE + digits(asn) + "/aggregated.json";
	}

	private static String digits(String asn) {
		if (asn == null) {
			throw new IllegalArgumentException("asn null");
		}
		String a = asn.trim();
		if (a.regionMatches(true, 0, "AS", 0, 2)) {
			a = a.substring(2).trim();
		}
		if (!a.matches("\\d+")) {
			throw new IllegalArgumentException("invalid asn: " + asn);
		}
		return a;
	}
}
