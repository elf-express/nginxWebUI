package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.utils.CrowdSecCredentials.Secret;
import com.cym.utils.CrowdSecCredentials.Source;

/** 驗證頁金鑰來源：secrets 檔優先、環境變數後備、cookie 金鑰自動產生（600）、交接票金鑰不自動產生。 */
class ChallengeSecretsTest {
	@TempDir
	Path tmp;

	private final Map<String, String> env = new HashMap<>();
	private Path secrets;
	private Path generated;
	private ChallengeSecrets cs;

	@BeforeEach
	void setUp() throws Exception {
		env.clear();
		secrets = tmp.resolve("secrets");
		Files.createDirectories(secrets);
		generated = tmp.resolve("home/crowdsec/challenge_cookie_secret");
		cs = new ChallengeSecrets(env::get, secrets, generated);
	}

	@Test
	void secretsFileWinsOverEnv() throws Exception {
		Files.writeString(secrets.resolve(ChallengeSecrets.COOKIE_FILE_NAME), " file-cookie-secret-1234\n");
		Files.writeString(secrets.resolve(ChallengeSecrets.TICKET_FILE_NAME), "file-ticket-secret-5678");
		env.put(ChallengeSecrets.COOKIE_ENV, "env-cookie");
		env.put(ChallengeSecrets.TICKET_ENV, "env-ticket");
		assertEquals(new Secret("file-cookie-secret-1234", Source.SECRETS_FILE), cs.cookieSecret());
		assertEquals(new Secret("file-ticket-secret-5678", Source.SECRETS_FILE), cs.ticketSecret());
		assertFalse(Files.exists(generated), "no generated file when a secret is configured");
	}

	@Test
	void envIsTheFallback() throws Exception {
		Files.writeString(secrets.resolve(ChallengeSecrets.COOKIE_FILE_NAME), "   ");
		env.put(ChallengeSecrets.COOKIE_ENV, " env-cookie-secret ");
		env.put(ChallengeSecrets.TICKET_ENV, "env-ticket-secret");
		assertEquals(new Secret("env-cookie-secret", Source.ENV), cs.cookieSecret());
		assertEquals(new Secret("env-ticket-secret", Source.ENV), cs.ticketSecret());
	}

	@Test
	void ticketSecretIsNeverGenerated() {
		assertEquals(Source.NONE, cs.ticketSecret().source());
		assertFalse(cs.ticketSecret().isPresent());
		assertFalse(Files.exists(generated));
	}

	@Test
	void peekDoesNotGenerate() {
		assertEquals(Source.NONE, cs.peekCookieSecret().source());
		assertFalse(Files.exists(generated));
	}

	@Test
	void cookieSecretIsGeneratedOnceAndKept() throws Exception {
		Secret first = cs.cookieSecret();
		assertEquals(Source.GENERATED, first.source());
		assertTrue(first.value().matches("[0-9a-f]{64}"), first.masked());
		assertEquals(first, cs.cookieSecret(), "same value on the next apply");
		assertEquals(first, cs.peekCookieSecret());
		assertEquals(first.value(), Files.readString(generated).trim());
	}

	@Test
	void generatedFileIsOwnerOnly() throws Exception {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		cs.cookieSecret();
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(generated)));
	}

	@Test
	void valuesWithNewlinesAreIgnored() throws Exception {
		env.put(ChallengeSecrets.TICKET_ENV, "abc\ndef");
		assertFalse(cs.ticketSecret().isPresent());
	}

	@Test
	void toStringNeverShowsTheValue() throws Exception {
		env.put(ChallengeSecrets.TICKET_ENV, "ticket-plaintext-9f3c");
		assertFalse(cs.ticketSecret().toString().contains("ticket-plaintext"));
		assertEquals("••••••9f3c", cs.ticketSecret().masked());
	}
}
