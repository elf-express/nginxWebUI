package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.config.HomeConfig;
import com.cym.testsupport.TestMessages;
import com.cym.utils.ChallengeSecrets;

import cn.hutool.core.util.ReflectUtil;

/** 驗證頁設定、套用時寫出的 challenge.conf 與頁面。 */
class CrowdSecChallengeTest {
	private static final String TICKET = "ticket-plaintext-key-7d21";
	private static final String COOKIE = "cookie-plaintext-key-4b88";

	@TempDir
	Path tmp;

	private final Map<String, String> env = new HashMap<>();
	private final Map<String, String> settings = new HashMap<>();
	private CrowdSecChallenge challenge;
	private Path altcha;
	private String home;

	@BeforeEach
	void setUp() throws Exception {
		env.clear();
		settings.clear();
		home = tmp.resolve("home").toString().replace('\\', '/') + "/";
		Files.createDirectories(tmp.resolve("home"));
		altcha = tmp.resolve("altcha.js");
		Files.writeString(altcha, "// altcha");
		Files.writeString(tmp.resolve("altcha.css"), "/* altcha */");
		Files.writeString(tmp.resolve("altcha-worker.js"), "// worker");
		HomeConfig homeConfig = new HomeConfig();
		homeConfig.home = home;
		SettingService settingService = new SettingService() {
			@Override public String get(String key) { return settings.get(key); }
			@Override public void set(String key, String value) { settings.put(key, value); }
		};
		challenge = new CrowdSecChallenge();
		ReflectUtil.setFieldValue(challenge, "homeConfig", homeConfig);
		ReflectUtil.setFieldValue(challenge, "settingService", settingService);
		ReflectUtil.setFieldValue(challenge, "messageUtils", TestMessages.load());
		ReflectUtil.setFieldValue(challenge, "secrets",
				new ChallengeSecrets(env::get, tmp.resolve("no-secrets"), Path.of(home + "crowdsec/challenge_cookie_secret")));
		ReflectUtil.setFieldValue(challenge, "altchaPath", altcha.toString());
	}

	private String read(String path) throws Exception {
		return Files.readString(Path.of(path));
	}

	@Test
	void defaults() {
		assertEquals("builtin", challenge.mode());
		assertEquals("auto", challenge.style());
		assertEquals("", challenge.externalUrl());
		assertEquals(60, challenge.ttlMinutes());
		assertNull(challenge.problem());
	}

	@Test
	void invalidSettingsFallBackToDefaults() {
		settings.put(CrowdSecChallenge.MODE_KEY, "other");
		settings.put(CrowdSecChallenge.STYLE_KEY, "other");
		settings.put(CrowdSecChallenge.TTL_KEY, "4");
		assertEquals("builtin", challenge.mode());
		assertEquals("auto", challenge.style());
		assertEquals(60, challenge.ttlMinutes());
	}

	@Test
	void parseTtlRange() {
		assertEquals(5, CrowdSecChallenge.parseTtl("5"));
		assertEquals(1440, CrowdSecChallenge.parseTtl(" 1440 "));
		assertNull(CrowdSecChallenge.parseTtl("4"));
		assertNull(CrowdSecChallenge.parseTtl("1441"));
		assertNull(CrowdSecChallenge.parseTtl("1.5"));
		assertNull(CrowdSecChallenge.parseTtl(null));
	}

	@Test
	void externalUrlFormat() {
		assertTrue(CrowdSecChallenge.validExternalUrl("https://verify.example.workers.dev/"));
		assertTrue(CrowdSecChallenge.validExternalUrl("http://fake-ext:8090/check?site=a"));
		assertFalse(CrowdSecChallenge.validExternalUrl("ftp://verify.example/"));
		assertFalse(CrowdSecChallenge.validExternalUrl("https://"));
		assertFalse(CrowdSecChallenge.validExternalUrl("https://verify.example/a b"));
		assertFalse(CrowdSecChallenge.validExternalUrl("https://verify.example/#x"));
		assertFalse(CrowdSecChallenge.validExternalUrl("javascript:alert(1)"));
		assertFalse(CrowdSecChallenge.validExternalUrl(null));
	}

	@Test
	void externalModeNeedsTicketSecretAndUrl() {
		settings.put(CrowdSecChallenge.MODE_KEY, "external");
		settings.put(CrowdSecChallenge.EXTERNAL_URL_KEY, "https://verify.example/");
		assertEquals(CrowdSecChallenge.ERR_TICKET_SECRET, challenge.problem());
		env.put(ChallengeSecrets.TICKET_ENV, TICKET);
		assertNull(challenge.problem());
		settings.put(CrowdSecChallenge.EXTERNAL_URL_KEY, "");
		assertEquals(CrowdSecChallenge.ERR_EXTERNAL_URL, challenge.problem());
	}

	@Test
	void prepareWritesConfAndPages() throws Exception {
		env.put(ChallengeSecrets.COOKIE_ENV, COOKIE);
		settings.put(CrowdSecChallenge.TTL_KEY, "30");
		assertTrue(challenge.prepare());
		assertNull(challenge.lastError());
		assertEquals("MODE=builtin\nTTL_SECONDS=1800\nEXTERNAL_URL=\nCOOKIE_SECRET=" + COOKIE + "\nTICKET_SECRET=\n",
				read(challenge.confPath()));
		for (String page : new String[] { "challenge.html", "http-blocked.html", "verify-failed.html", "callback-failed.html" }) {
			assertTrue(Files.isRegularFile(Path.of(challenge.pagesDir() + page)), page);
		}
		for (Map.Entry<String, String> asset : challenge.assets().entrySet()) {
			assertEquals(asset.getValue(), read(challenge.pagesDir() + asset.getKey()), asset.getKey());
		}
	}

	@Test
	void prepareExternalWritesTicketSecretAndUrl() throws Exception {
		env.put(ChallengeSecrets.TICKET_ENV, TICKET);
		settings.put(CrowdSecChallenge.MODE_KEY, "external");
		settings.put(CrowdSecChallenge.EXTERNAL_URL_KEY, "https://verify.example/check");
		assertTrue(challenge.prepare());
		String conf = read(challenge.confPath());
		assertTrue(conf.contains("MODE=external\n"), conf);
		assertTrue(conf.contains("EXTERNAL_URL=https://verify.example/check\n"), conf);
		assertTrue(conf.contains("TICKET_SECRET=" + TICKET + "\n"), conf);
		assertTrue(conf.matches("(?s).*COOKIE_SECRET=[0-9a-f]{64}\n.*"), "cookie secret generated");
	}

	@Test
	void confIsOwnerOnly() throws Exception {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		assertTrue(challenge.prepare());
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(Path.of(challenge.confPath()))));
	}

	@Test
	void prepareRefusesExternalWithoutTicketSecretAndRemovesOldConf() throws Exception {
		assertTrue(challenge.prepare());
		settings.put(CrowdSecChallenge.MODE_KEY, "external");
		settings.put(CrowdSecChallenge.EXTERNAL_URL_KEY, "https://verify.example/");
		assertFalse(challenge.prepare());
		assertEquals(CrowdSecChallenge.ERR_TICKET_SECRET, challenge.lastError());
		assertFalse(Files.exists(Path.of(challenge.confPath())), "stale challenge.conf removed");
	}

	@Test
	void prepareRefusesWithoutAltcha() throws Exception {
		Files.delete(altcha);
		assertFalse(challenge.prepare());
		assertEquals(CrowdSecChallenge.ERR_ALTCHA, challenge.lastError());
	}

	@Test
	void prepareRefusesWithoutAltchaStylesOrWorker() throws Exception {
		Files.delete(tmp.resolve("altcha-worker.js"));
		assertFalse(challenge.prepare());
		assertEquals(CrowdSecChallenge.ERR_ALTCHA, challenge.lastError());
		Files.writeString(tmp.resolve("altcha-worker.js"), "// worker");
		Files.delete(tmp.resolve("altcha.css"));
		assertFalse(challenge.prepare());
		assertEquals(CrowdSecChallenge.ERR_ALTCHA, challenge.lastError());
	}

	@Test
	void prepareReportsWriteFailure() throws Exception {
		// crowdsec 是檔案不是目錄 → 建不了 challenge/ 與 challenge.conf
		Files.writeString(Path.of(home + "crowdsec"), "x");
		env.put(ChallengeSecrets.COOKIE_ENV, COOKIE);
		assertFalse(challenge.prepare());
		assertEquals(CrowdSecChallenge.ERR_WRITE, challenge.lastError());
	}

	@Test
	void autoAndCheckboxPagesDiffer() {
		String auto = challenge.buildPages("auto").get("challenge.html");
		String checkbox = challenge.buildPages("checkbox").get("challenge.html");
		assertTrue(auto.contains("auto=\"onload\""), auto);
		assertTrue(auto.contains("data-style=\"auto\""));
		assertFalse(checkbox.contains("auto=\"onload\""));
		assertTrue(checkbox.contains("data-style=\"checkbox\""));
		assertTrue(checkbox.contains("data-i18n=\"challengeStr.checkboxHint\""));
	}

	@Test
	void pagesKeepLuaPlaceholdersAndCarryThreeLanguages() {
		Map<String, String> pages = challenge.buildPages("auto");
		String page = pages.get("challenge.html");
		for (String placeholder : new String[] { "{{IP}}", "{{TIME}}", "{{CHALLENGE}}", "{{RETURN}}" }) {
			assertTrue(page.contains(placeholder), placeholder);
		}
		assertFalse(page.contains("@@"), "no Java placeholder left");
		assertTrue(page.contains("\"zh-TW\"") && page.contains("\"zh-CN\"") && page.contains("\"en\""));
		assertTrue(page.contains("正在確認您不是機器人") && page.contains("正在确认您不是机器人")
				&& page.contains("Checking that you are not a robot"));
		assertTrue(page.contains("/.well-known/nwu-challenge/altcha.js"));
		assertFalse(page.matches("(?s).*https?://.*"), "no external resource in the challenge page");
		String http = pages.get("http-blocked.html");
		assertTrue(http.contains("{{IP}}") && http.contains("{{TIME}}") && http.contains("HTTPS"));
		assertTrue(pages.get("verify-failed.html").contains("challengeStr.verifyFailedBody"));
		assertTrue(pages.get("callback-failed.html").contains("challengeStr.callbackFailedBody"));
		for (String html : pages.values()) {
			assertFalse(html.contains("@@"));
			assertFalse(html.contains("href=\"javascript:"));
			assertTrue(html.contains("<h1"));
		}
	}

	@Test
	void previewFillsPlaceholders() {
		String html = challenge.previewHtml("checkbox");
		assertFalse(html.contains("{{"), html);
		assertTrue(html.contains("203.0.113.10"));
		assertTrue(html.contains("challengejson=\"\""));
	}

	/** 網站送 Content-Security-Policy "default-src 'self'" 時頁面仍要能用：沒有 inline script、style 與事件屬性 */
	@Test
	void pagesWorkUnderDefaultSrcSelf() {
		for (String style : new String[] { "auto", "checkbox" }) {
			for (Map.Entry<String, String> page : challenge.buildPages(style).entrySet()) {
				String name = style + " " + page.getKey();
				String html = page.getValue();
				assertFalse(html.contains("<style"), name);
				assertFalse(Pattern.compile("\\sstyle=").matcher(html).find(), name);
				assertFalse(Pattern.compile("\\son[a-z]+=").matcher(html).find(), name);
				Matcher script = Pattern.compile("<script([^>]*)>").matcher(html);
				while (script.find()) {
					String attrs = script.group(1);
					assertTrue(attrs.matches(" src=\"/\\.well-known/nwu-challenge/[a-z.-]+\" defer")
							|| attrs.equals(" type=\"application/json\" id=\"nwu-strings\""), name + ": " + script.group());
				}
				assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/.well-known/nwu-challenge/page.css\">"), name);
				assertTrue(html.contains("<script src=\"/.well-known/nwu-challenge/page.js\" defer></script>"), name);
				assertTrue(html.contains("id=\"nwu-strings\">{\"zh-TW\""), name);
			}
		}
		String page = challenge.buildPages("auto").get("challenge.html");
		assertTrue(page.contains("<link rel=\"stylesheet\" href=\"/.well-known/nwu-challenge/altcha.css\">"), page);
		assertTrue(page.contains("workerurl=\"/.well-known/nwu-challenge/altcha-worker.js\""), page);
		assertTrue(page.indexOf("nwu-challenge/page.js") < page.indexOf("nwu-challenge/altcha.js"),
				"page.js listens for the widget before altcha.js defines it");
	}

	@Test
	void assetsCarryTheScriptAndTheStyles() {
		Map<String, String> assets = challenge.assets();
		assertEquals(Set.of("page.js", "page.css"), assets.keySet());
		String js = assets.get("page.js");
		assertTrue(js.contains("nwu-strings") && js.contains("\"verified\"") && js.contains("nwu-form"), js);
		assertFalse(js.contains("@@"));
		String css = assets.get("page.css");
		assertTrue(css.contains(".nwu-meta{color:#666;"), css);
		assertFalse(css.contains("#888"), css);
	}

	@Test
	void previewUsesAdminAssetUrls() {
		for (String style : new String[] { "auto", "checkbox" }) {
			String html = challenge.previewHtml(style);
			for (String asset : new String[] { "altcha.js", "altcha.css", "altcha-worker.js", "page.js", "page.css" }) {
				assertTrue(html.contains("\"/adminPage/crowdsec/challengeAsset?name=" + asset + "\""), style + " " + asset);
				assertFalse(html.contains("/.well-known/nwu-challenge/" + asset), style + " " + asset);
			}
		}
	}

	@Test
	void previewAssetsComeFromTheImageAndTheJar() throws Exception {
		CrowdSecChallenge.Asset js = challenge.previewAsset("page.js");
		assertEquals("application/javascript; charset=utf-8", js.contentType());
		assertEquals(challenge.assets().get("page.js"), new String(js.body(), StandardCharsets.UTF_8));
		assertEquals("text/css; charset=utf-8", challenge.previewAsset("page.css").contentType());
		assertEquals("// altcha", new String(challenge.previewAsset("altcha.js").body(), StandardCharsets.UTF_8));
		assertEquals("text/css; charset=utf-8", challenge.previewAsset("altcha.css").contentType());
		assertEquals("// worker", new String(challenge.previewAsset("altcha-worker.js").body(), StandardCharsets.UTF_8));
		Files.delete(tmp.resolve("altcha.css"));
		assertNull(challenge.previewAsset("altcha.css"), "missing image file -> 404");
		for (String bad : new String[] { null, "", "../altcha.js", "altcha.LICENSE.txt", "challenge.html", "page.js/" }) {
			assertNull(challenge.previewAsset(bad), String.valueOf(bad));
		}
	}

	@Test
	void stringsJsonCannotCloseTheScript() {
		assertFalse(challenge.stringsJson().contains("</"));
	}
}
