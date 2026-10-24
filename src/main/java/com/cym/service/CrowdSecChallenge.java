package com.cym.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.config.HomeConfig;
import com.cym.utils.ChallengeSecrets;
import com.cym.utils.CrowdSecCredentials.Secret;
import com.cym.utils.MessageUtils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 自架驗證頁（E-387）的設定與套用：驗證方式、樣式、外部網址、有效時間存在 setting；
 * 套用設定時寫 challenge.conf（金鑰與 Lua 參數，權限 600）、四個頁面與 page.js / page.css 給 nginx Lua 讀。
 * 頁面不含 inline script 與 style。
 */
@Component
public class CrowdSecChallenge {
	public static final String MODE_KEY = "crowdsec.challenge.mode";
	public static final String STYLE_KEY = "crowdsec.challenge.style";
	public static final String EXTERNAL_URL_KEY = "crowdsec.challenge.externalUrl";
	public static final String TTL_KEY = "crowdsec.challenge.ttlMinutes";
	public static final String MODE_BUILTIN = "builtin";
	public static final String MODE_EXTERNAL = "external";
	public static final String STYLE_AUTO = "auto";
	public static final String STYLE_CHECKBOX = "checkbox";
	public static final int DEFAULT_TTL = 60;
	public static final int MIN_TTL = 5;
	public static final int MAX_TTL = 1440;
	public static final String ALTCHA_JS = "/usr/local/lib/nginxwebui/html/altcha.js";
	/** image 內的 ALTCHA 檔（strict CSP 版：樣式與 Worker 是獨立的同源檔案），與 altcha.js 同一個目錄 */
	static final List<String> ALTCHA_FILES = List.of("altcha.js", "altcha.css", "altcha-worker.js");
	/** 驗證頁與訊息頁共用的 script 與樣式：保留路徑上的檔名 → jar 內的資源，套用設定時寫到頁面目錄 */
	static final Map<String, String> PAGE_ASSETS = Map.of("page.js", "crowdsec/challenge-page.js",
			"page.css", "crowdsec/challenge-page.css");
	static final String RESERVED_PREFIX = "/.well-known/nwu-challenge/";
	/** 後台預覽改用的網址（需登入），見 CrowdSecController.challengeAsset */
	public static final String PREVIEW_ASSET_URL = "/adminPage/crowdsec/challengeAsset?name=";

	/** 套用失敗原因（畫面依代碼顯示） */
	public static final String ERR_TICKET_SECRET = "TICKET_SECRET_MISSING";
	public static final String ERR_EXTERNAL_URL = "EXTERNAL_URL_MISSING";
	public static final String ERR_ALTCHA = "ALTCHA_MISSING";
	public static final String ERR_WRITE = "WRITE_FAILED";

	static final String PAGE_PREFIX = "challengeStr.";
	private static final Logger logger = LoggerFactory.getLogger(CrowdSecChallenge.class);

	@Inject
	HomeConfig homeConfig;
	@Inject
	SettingService settingService;
	@Inject
	MessageUtils messageUtils;

	ChallengeSecrets secrets;
	String altchaPath = ALTCHA_JS;
	private volatile String lastError;

	public ChallengeSecrets secrets() {
		if (secrets == null) {
			secrets = ChallengeSecrets.system(homeConfig.home);
		}
		return secrets;
	}

	public String mode() {
		return MODE_EXTERNAL.equals(settingService.get(MODE_KEY)) ? MODE_EXTERNAL : MODE_BUILTIN;
	}

	public String style() {
		return STYLE_CHECKBOX.equals(settingService.get(STYLE_KEY)) ? STYLE_CHECKBOX : STYLE_AUTO;
	}

	public String externalUrl() {
		String value = settingService.get(EXTERNAL_URL_KEY);
		return value == null ? "" : value.trim();
	}

	/** 有效時間（分鐘）：設定值不合法時用預設 60 */
	public int ttlMinutes() {
		Integer ttl = parseTtl(settingService.get(TTL_KEY));
		return ttl == null ? DEFAULT_TTL : ttl;
	}

	/** 5–1440 的整數，其他回 null */
	public static Integer parseTtl(String text) {
		if (text == null) {
			return null;
		}
		try {
			int minutes = Integer.parseInt(text.trim());
			return minutes < MIN_TTL || minutes > MAX_TTL ? null : minutes;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** http:// 或 https:// 開頭、有主機名稱、不含空白引號角括號與 #，長度 512 以內 */
	public static boolean validExternalUrl(String url) {
		if (url == null) {
			return false;
		}
		String value = url.trim();
		if (value.length() > 512 || !value.matches("(?i)https?://[^\\s\"'<>#]+")) {
			return false;
		}
		try {
			String host = new URI(value).getHost();
			return host != null && !host.isEmpty();
		} catch (URISyntaxException e) {
			return false;
		}
	}

	/** 依目前設定與金鑰能不能套用：null 表示可以，否則回 ERR_* 代碼 */
	public String problem() {
		if (MODE_EXTERNAL.equals(mode())) {
			if (!secrets().ticketSecret().isPresent()) {
				return ERR_TICKET_SECRET;
			}
			if (!validExternalUrl(externalUrl())) {
				return ERR_EXTERNAL_URL;
			}
		}
		return null;
	}

	/** 上次套用設定的失敗原因（ERR_* 代碼），成功或還沒套用過時為 null */
	public String lastError() {
		return lastError;
	}

	public String confPath() {
		return homeConfig.home + "crowdsec/challenge.conf";
	}

	public String pagesDir() {
		return homeConfig.home + "crowdsec/challenge/";
	}

	/**
	 * 套用設定時呼叫：寫 challenge.conf 與頁面並回 true。不能用時刪掉 challenge.conf、記下原因並回 false，
	 * 呼叫端就不載入驗證、bouncer 只收 ban 決策。
	 */
	public boolean prepare() {
		String problem = problem();
		if (problem == null && !altchaFilesPresent()) {
			problem = ERR_ALTCHA;
		}
		if (problem != null) {
			return disable(problem, null);
		}
		try {
			Secret cookie = secrets().cookieSecret();
			Secret ticket = secrets().ticketSecret();
			Path dir = Paths.get(pagesDir());
			Files.createDirectories(dir);
			for (Map.Entry<String, String> page : buildPages(style()).entrySet()) {
				writeIfChanged(dir.resolve(page.getKey()), page.getValue(), false);
			}
			for (Map.Entry<String, String> asset : assets().entrySet()) {
				writeIfChanged(dir.resolve(asset.getKey()), asset.getValue(), false);
			}
			writeIfChanged(Paths.get(confPath()), buildConf(cookie.value(), ticket.value()), true);
			lastError = null;
			return true;
		} catch (IOException | RuntimeException e) {
			return disable(ERR_WRITE, e);
		}
	}

	private Path altchaFile(String name) {
		return Paths.get(altchaPath).resolveSibling(name);
	}

	private boolean altchaFilesPresent() {
		for (String name : ALTCHA_FILES) {
			if (!Files.isRegularFile(altchaFile(name))) {
				return false;
			}
		}
		return true;
	}

	private boolean disable(String code, Exception e) {
		lastError = code;
		logger.error("CrowdSec challenge is not applied ({}){}", code, e == null ? "" : ": " + e.getMessage());
		try {
			Files.deleteIfExists(Paths.get(confPath()));
		} catch (IOException ex) {
			logger.warn("cannot remove {}: {}", confPath(), ex.getMessage());
		}
		return false;
	}

	/** challenge.conf 內容（每行 KEY=VALUE，金鑰明文，只寫進權限 600 的檔案） */
	String buildConf(String cookieSecret, String ticketSecret) {
		List<String> lines = new ArrayList<>();
		lines.add("MODE=" + mode());
		lines.add("TTL_SECONDS=" + ttlMinutes() * 60);
		lines.add("EXTERNAL_URL=" + (MODE_EXTERNAL.equals(mode()) ? externalUrl() : ""));
		lines.add("COOKIE_SECRET=" + cookieSecret);
		lines.add("TICKET_SECRET=" + (ticketSecret == null ? "" : ticketSecret));
		return StrUtil.join("\n", lines) + "\n";
	}

	private static void writeIfChanged(Path file, String content, boolean ownerOnly) throws IOException {
		boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
		Files.createDirectories(file.getParent());
		if (!Files.exists(file)) {
			if (ownerOnly && posix) {
				Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			} else {
				Files.createFile(file);
			}
		} else if (ownerOnly && posix) {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
		}
		if (!content.equals(Files.readString(file, StandardCharsets.UTF_8))) {
			Files.writeString(file, content, StandardCharsets.UTF_8);
		}
	}

	/**
	 * 四個頁面：檔名 → HTML。簡／繁／英字串都放進頁面，由瀏覽器語言切換；
	 * {{IP}}、{{TIME}}、{{CHALLENGE}}、{{RETURN}} 留給 Lua 回應時填入。
	 */
	public Map<String, String> buildPages(String style) {
		String strings = stringsJson();
		String noscript = fallback("challengeStr.noscript");
		boolean checkbox = STYLE_CHECKBOX.equals(style);
		Map<String, String> pages = new LinkedHashMap<>();
		pages.put("challenge.html", resource("crowdsec/challenge-page.html")
				.replace("@@STYLE@@", checkbox ? STYLE_CHECKBOX : STYLE_AUTO)
				.replace("@@AUTO@@", checkbox ? "" : "auto=\"onload\"")
				.replace("@@HINT_KEY@@", checkbox ? "challengeStr.checkboxHint" : "challengeStr.autoHint")
				.replace("@@HINT@@", fallback(checkbox ? "challengeStr.checkboxHint" : "challengeStr.autoHint"))
				.replace("@@NOSCRIPT@@", noscript)
				.replace("@@STRINGS@@", strings));
		pages.put("http-blocked.html", messagePage("challengeStr.httpTitle", "challengeStr.httpBody", false, strings));
		pages.put("verify-failed.html", messagePage("challengeStr.verifyFailedTitle", "challengeStr.verifyFailedBody", true, strings));
		pages.put("callback-failed.html", messagePage("challengeStr.callbackFailedTitle", "challengeStr.callbackFailedBody", true, strings));
		return pages;
	}

	/** page.js 與 page.css 的內容（檔名 → 內容），套用設定時與頁面一起寫出，由 Lua 在保留路徑上提供 */
	public Map<String, String> assets() {
		Map<String, String> assets = new LinkedHashMap<>();
		for (Map.Entry<String, String> asset : PAGE_ASSETS.entrySet()) {
			assets.put(asset.getKey(), resource(asset.getValue()));
		}
		return assets;
	}

	/** 預覽頁要載入的檔案 */
	public record Asset(String contentType, byte[] body) {
	}

	/**
	 * 後台預覽用：page.js / page.css 取自 jar，ALTCHA 檔取自 image；不認得的名稱或 image 內沒有這個檔時回 null。
	 */
	public Asset previewAsset(String name) {
		if (name == null) {
			return null;
		}
		String type = name.endsWith(".css") ? "text/css; charset=utf-8" : "application/javascript; charset=utf-8";
		if (PAGE_ASSETS.containsKey(name)) {
			return new Asset(type, resource(PAGE_ASSETS.get(name)).getBytes(StandardCharsets.UTF_8));
		}
		if (!ALTCHA_FILES.contains(name) || !Files.isRegularFile(altchaFile(name))) {
			return null;
		}
		try {
			return new Asset(type, Files.readAllBytes(altchaFile(name)));
		} catch (IOException e) {
			logger.warn("cannot read {}: {}", altchaFile(name), e.getMessage());
			return null;
		}
	}

	/** 預覽：與 nginx 送出的驗證頁同一份 HTML，題目留空，只能看樣子不能通過；檔案改由後台網址提供 */
	public String previewHtml(String style) {
		String html = buildPages(style).get("challenge.html");
		for (String asset : ALTCHA_FILES) {
			html = html.replace("\"" + RESERVED_PREFIX + asset + "\"", "\"" + PREVIEW_ASSET_URL + asset + "\"");
		}
		for (String asset : PAGE_ASSETS.keySet()) {
			html = html.replace("\"" + RESERVED_PREFIX + asset + "\"", "\"" + PREVIEW_ASSET_URL + asset + "\"");
		}
		return html
				.replace("{{IP}}", "203.0.113.10")
				.replace("{{TIME}}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
				.replace("{{CHALLENGE}}", "")
				.replace("{{RETURN}}", "/");
	}

	private String messagePage(String titleKey, String bodyKey, boolean retry, String strings) {
		return resource("crowdsec/challenge-message.html")
				.replace("@@TITLE_KEY@@", titleKey)
				.replace("@@BODY_KEY@@", bodyKey)
				.replace("@@TITLE@@", fallback(titleKey))
				.replace("@@BODY@@", fallback(bodyKey))
				.replace("@@RETRY@@", retry
						? "<p><a href=\"/\" data-i18n=\"challengeStr.retry\">" + fallback("challengeStr.retry") + "</a></p>"
						: "")
				.replace("@@STRINGS@@", strings);
	}

	/** 三種語言的 challengeStr.* 字串，JSON（已處理 </ 以免提早結束 script） */
	String stringsJson() {
		JSONObject all = new JSONObject(new LinkedHashMap<>());
		all.set("zh-TW", pick(messageUtils.getPropertiesTW()));
		all.set("zh-CN", pick(messageUtils.getProperties()));
		all.set("en", pick(messageUtils.getPropertiesEN()));
		return JSONUtil.toJsonStr(all).replace("</", "<\\/");
	}

	private static Map<String, String> pick(Properties props) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String key : props.stringPropertyNames()) {
			if (key.startsWith(PAGE_PREFIX)) {
				out.put(key, props.getProperty(key));
			}
		}
		return out;
	}

	/** 無 JavaScript 時直接顯示的文字：同一個 key 的繁中與英文，以「 / 」連接並做 HTML 跳脫 */
	private String fallback(String key) {
		List<String> out = new ArrayList<>();
		for (Properties p : new Properties[] { messageUtils.getPropertiesTW(), messageUtils.getPropertiesEN() }) {
			String value = p.getProperty(key);
			if (StrUtil.isNotBlank(value)) {
				out.add(value);
			}
		}
		return HtmlUtil.escape(StrUtil.join(" / ", out));
	}

	private static String resource(String name) {
		try (InputStream in = CrowdSecChallenge.class.getClassLoader().getResourceAsStream(name)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot read resource " + name, e);
		}
	}
}
