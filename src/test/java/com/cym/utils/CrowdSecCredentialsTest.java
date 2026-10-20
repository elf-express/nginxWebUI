package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CrowdSec 金鑰與位址讀取：secrets 檔優先，其次環境變數。 */
class CrowdSecCredentialsTest {
	private static final String DOTS = "••••••";

	@TempDir
	Path secrets;

	private final Map<String, String> env = new HashMap<>();

	private CrowdSecCredentials creds() {
		return new CrowdSecCredentials(env::get, secrets);
	}

	@Test
	void secretFileWinsOverEnv() throws Exception {
		Files.writeString(secrets.resolve("crowdsec_bouncer_key"), "from-file\n");
		env.put("CROWDSEC_BOUNCER_KEY", "from-env");

		CrowdSecCredentials.Secret secret = creds().bouncerKey();

		assertEquals("from-file", secret.value());
		assertEquals(CrowdSecCredentials.Source.SECRETS_FILE, secret.source());
		assertTrue(secret.isPresent());
	}

	@Test
	void envUsedWhenNoFile() {
		env.put("CROWDSEC_BOUNCER_KEY", " from-env ");

		CrowdSecCredentials.Secret secret = creds().bouncerKey();

		assertEquals("from-env", secret.value());
		assertEquals(CrowdSecCredentials.Source.ENV, secret.source());
	}

	@Test
	void blankSecretFileFallsBackToEnv() throws Exception {
		Files.writeString(secrets.resolve("crowdsec_bouncer_key"), "\n  \n");
		env.put("CROWDSEC_BOUNCER_KEY", "from-env");

		CrowdSecCredentials.Secret secret = creds().bouncerKey();

		assertEquals("from-env", secret.value());
		assertEquals(CrowdSecCredentials.Source.ENV, secret.source());
	}

	@Test
	void missingIsNone() {
		CrowdSecCredentials.Secret secret = creds().bouncerKey();

		assertFalse(secret.isPresent());
		assertEquals(CrowdSecCredentials.Source.NONE, secret.source());
		assertEquals("", secret.masked());
	}

	@Test
	void blankEnvIsNone() {
		env.put("CROWDSEC_BOUNCER_KEY", "   ");

		assertFalse(creds().bouncerKey().isPresent());
		assertEquals(CrowdSecCredentials.Source.NONE, creds().bouncerKey().source());
	}

	@Test
	void maskShowsLastFour() {
		assertEquals(DOTS + "3456", new CrowdSecCredentials.Secret("abcdef123456", CrowdSecCredentials.Source.ENV).masked());
		assertEquals(DOTS, new CrowdSecCredentials.Secret("abc", CrowdSecCredentials.Source.ENV).masked());
		assertEquals(DOTS, new CrowdSecCredentials.Secret("abcd", CrowdSecCredentials.Source.ENV).masked());
	}

	@Test
	void placeholderKeyFromEnvIsNotSet() {
		env.put("CROWDSEC_BOUNCER_KEY", " your-bouncer-key-here ");

		CrowdSecCredentials.Secret secret = creds().bouncerKey();

		assertFalse(secret.isPresent());
		assertEquals(CrowdSecCredentials.Source.NONE, secret.source());
	}

	@Test
	void placeholderKeyInSecretFileFallsBackToEnv() throws Exception {
		Files.writeString(secrets.resolve("crowdsec_bouncer_key"), "your-bouncer-key-here\n");
		env.put("CROWDSEC_BOUNCER_KEY", "from-env");

		CrowdSecCredentials.Secret secret = creds().bouncerKey();

		assertEquals("from-env", secret.value());
		assertEquals(CrowdSecCredentials.Source.ENV, secret.source());
	}

	@Test
	void secretToStringIsMasked() {
		String raw = "abcdef-secret-123456";
		String text = new CrowdSecCredentials.Secret(raw, CrowdSecCredentials.Source.ENV).toString();

		assertFalse(text.contains(raw));
		assertTrue(text.contains(DOTS + "3456"));
	}

	@Test
	void lapiUrlTrimsTrailingSlash() {
		env.put("CROWDSEC_LAPI_URL", "http://crowdsec:8080/");

		assertEquals("http://crowdsec:8080", creds().lapiUrl());
	}

	@Test
	void urlsAreNullWhenBlank() {
		env.put("CROWDSEC_LAPI_URL", "  ");

		assertNull(creds().lapiUrl());
		assertNull(creds().appsecUrl());
	}

	@Test
	void appsecUrlIsReturned() {
		env.put("CROWDSEC_APPSEC_URL", " http://crowdsec:7422 ");

		assertEquals("http://crowdsec:7422", creds().appsecUrl());
	}

	@Test
	void resolverDefaults() {
		assertEquals("127.0.0.11", creds().resolver());
		env.put("CROWDSEC_RESOLVER", "10.0.0.2");
		assertEquals("10.0.0.2", creds().resolver());
	}
}
