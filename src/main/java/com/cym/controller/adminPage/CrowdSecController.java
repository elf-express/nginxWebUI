package com.cym.controller.adminPage;

import java.util.LinkedHashMap;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.service.CrowdSecChallenge;
import com.cym.service.CrowdSecClient;
import com.cym.service.CrowdSecNginxConf;
import com.cym.service.CrowdSecWhitelistService;
import com.cym.service.SettingService;
import com.cym.utils.BaseController;
import com.cym.utils.ChallengeSecrets;
import com.cym.utils.CrowdSecCredentials;
import com.cym.utils.JsonResult;
import com.cym.utils.NetGuard;

import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/crowdsec")
public class CrowdSecController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	/** Default duration for ban / whitelist when client omits it. */
	private static final String DEFAULT_DURATION = "4h";

	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;
	@Inject
	CrowdSecNginxConf crowdSecNginxConf;
	@Inject
	CrowdSecWhitelistService crowdSecWhitelistService;
	@Inject
	CrowdSecChallenge crowdSecChallenge;

	CrowdSecCredentials credentials = CrowdSecCredentials.system();

	/**
	 * 連線設定：位址（唯讀）、bouncer 金鑰與 machine 密碼（只回遮罩值與來源）、寬限秒數、Lua 包裝寫的狀態檔。
	 * 不讀資料庫的 crowdsecUrl / crowdsecApiKey。
	 */
	@Mapping("getConfig")
	public JsonResult getConfig() {
		Map<String, Object> config = new LinkedHashMap<>();
		String lapiUrl = credentials.lapiUrl();
		config.put("lapiUrl", lapiUrl == null ? "" : lapiUrl);
		config.put("lapiUrlEnv", CrowdSecCredentials.LAPI_URL_ENV);
		config.put("bouncerKey", secretView(credentials.bouncerKey(), CrowdSecCredentials.KEY_FILE_NAME,
				CrowdSecCredentials.KEY_ENV));
		config.put("machineId", credentials.machineId());
		config.put("machinePassword", secretView(credentials.machinePassword(),
				CrowdSecCredentials.MACHINE_PASSWORD_FILE_NAME, CrowdSecCredentials.MACHINE_PASSWORD_ENV));
		config.put("graceSeconds", crowdSecNginxConf.graceSeconds());
		config.put("state", crowdSecNginxConf.readState());
		config.put("configured", crowdSecClient.isConfigured());
		return renderSuccess(config);
	}

	static Map<String, Object> secretView(CrowdSecCredentials.Secret secret, String fileName, String envName) {
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("masked", secret.masked());
		view.put("source", secret.source().name());
		view.put("configured", secret.isPresent());
		view.put("file", CrowdSecCredentials.secretFilePath(fileName));
		view.put("env", envName);
		return view;
	}

	/** 只存寬限秒數（10–3600 的整數）；套用設定時才寫進 nginx 設定 */
	@Mapping("saveConfig")
	public JsonResult saveConfig(String graceSeconds) {
		Integer seconds = parseGrace(graceSeconds);
		if (seconds == null) {
			return renderError(msgOr("crowdsecStr.invalidGrace", "invalid_grace"));
		}
		settingService.set(CrowdSecNginxConf.GRACE_KEY, String.valueOf(seconds));
		return renderSuccess(seconds);
	}

	static Integer parseGrace(String text) {
		if (text == null) {
			return null;
		}
		try {
			int seconds = Integer.parseInt(text.trim());
			if (seconds < CrowdSecNginxConf.MIN_GRACE || seconds > CrowdSecNginxConf.MAX_GRACE) {
				return null;
			}
			return seconds;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 驗證頁設定：方式、樣式、外部網址、有效時間、兩把金鑰（只回遮罩值與來源）、目前能否套用、上次套用的失敗原因。
	 * enabled = bouncer 金鑰與 LAPI 位址都有（CrowdSec 未設定時畫面整區停用）。
	 */
	@Mapping("getChallenge")
	public JsonResult getChallenge() {
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("enabled", credentials.bouncerKey().isPresent() && credentials.lapiUrl() != null);
		view.put("mode", crowdSecChallenge.mode());
		view.put("style", crowdSecChallenge.style());
		view.put("externalUrl", crowdSecChallenge.externalUrl());
		view.put("ttlMinutes", crowdSecChallenge.ttlMinutes());
		ChallengeSecrets secrets = crowdSecChallenge.secrets();
		view.put("cookieSecret", secretView(secrets.peekCookieSecret(), ChallengeSecrets.COOKIE_FILE_NAME,
				ChallengeSecrets.COOKIE_ENV));
		view.put("ticketSecret", secretView(secrets.ticketSecret(), ChallengeSecrets.TICKET_FILE_NAME,
				ChallengeSecrets.TICKET_ENV));
		view.put("problem", crowdSecChallenge.problem());
		view.put("lastError", crowdSecChallenge.lastError());
		return renderSuccess(view);
	}

	/** 存驗證頁設定；套用設定時才寫進 nginx。選外部驗證站時網址必須合法且已設定交接票金鑰 */
	@Mapping("saveChallenge")
	public JsonResult saveChallenge(String mode, String style, String externalUrl, String ttlMinutes) {
		boolean external = CrowdSecChallenge.MODE_EXTERNAL.equals(mode);
		if (!external && !CrowdSecChallenge.MODE_BUILTIN.equals(mode)
				|| !CrowdSecChallenge.STYLE_AUTO.equals(style) && !CrowdSecChallenge.STYLE_CHECKBOX.equals(style)) {
			return renderError(msgOr("crowdsecStr.invalidChallengeOption", "invalid_option"));
		}
		Integer ttl = CrowdSecChallenge.parseTtl(ttlMinutes);
		if (ttl == null) {
			return renderError(msgOr("crowdsecStr.invalidTtl", "invalid_ttl"));
		}
		String url = externalUrl == null ? "" : externalUrl.trim();
		if ((external || !url.isEmpty()) && !CrowdSecChallenge.validExternalUrl(url)) {
			return renderError(msgOr("crowdsecStr.invalidExternalUrl", "invalid_external_url"));
		}
		if (external && !crowdSecChallenge.secrets().ticketSecret().isPresent()) {
			return renderError(msgOr("crowdsecStr.ticketSecretRequired", "ticket_secret_required"));
		}
		settingService.set(CrowdSecChallenge.MODE_KEY, mode);
		settingService.set(CrowdSecChallenge.STYLE_KEY, style);
		settingService.set(CrowdSecChallenge.EXTERNAL_URL_KEY, url);
		settingService.set(CrowdSecChallenge.TTL_KEY, String.valueOf(ttl));
		return renderSuccess();
	}

	/** 預覽驗證頁：與 nginx 送出的同一份 HTML，題目是空的、不能通過 */
	@Mapping("challengePreview")
	public void challengePreview(Context ctx, String style) {
		ctx.contentType("text/html;charset=utf-8");
		ctx.output(crowdSecChallenge.previewHtml(
				CrowdSecChallenge.STYLE_CHECKBOX.equals(style) ? CrowdSecChallenge.STYLE_CHECKBOX : CrowdSecChallenge.STYLE_AUTO));
	}

	/** 預覽頁載入的檔案（ALTCHA 與 page.js / page.css）；不認得的名稱或 image 內沒有這個檔時回 404 */
	@Mapping("challengeAsset")
	public void challengeAsset(Context ctx, String name) {
		CrowdSecChallenge.Asset asset = crowdSecChallenge.previewAsset(name);
		if (asset == null) {
			ctx.status(404);
			return;
		}
		ctx.contentType(asset.contentType());
		ctx.output(asset.body());
	}

	/** 實際測 LAPI（bouncer 金鑰）與 machine 登入 */
	@Mapping("status")
	public JsonResult status() {
		return renderSuccess(crowdSecClient.probe().toMap());
	}

	@Mapping("alerts")
	public JsonResult alerts(int limit, int page) {
		JsonResult missing = missingCredentials(true, false);
		if (missing != null) {
			return missing;
		}
		try {
			return renderSuccess(crowdSecClient.listAlertsRaw(limit));
		} catch (Exception e) {
			return fail(e);
		}
	}

	@Mapping("decisions")
	public JsonResult decisions(int limit, int page) {
		JsonResult missing = missingCredentials(false, true);
		if (missing != null) {
			return missing;
		}
		try {
			int offset = (page - 1) * limit;
			return renderSuccess(crowdSecClient.listDecisionsRaw(limit, offset));
		} catch (Exception e) {
			return fail(e);
		}
	}

	/**
	 * Ban a single IP — delegates to {@link CrowdSecClient#banIp}.
	 * Keeps legacy response shape (success with empty obj on OK).
	 * Client-side gate: blank duration → {@code 4h}; IP via NetGuard.
	 */
	@Mapping("addDecision")
	public JsonResult addDecision(String ip, String duration, String reason) {
		JsonResult missing = missingCredentials(true, false);
		if (missing != null) {
			return missing;
		}
		JsonResult gate = gateIpAndDuration(ip, duration);
		if (gate != null) {
			return gate;
		}
		if (StrUtil.isBlank(duration)) {
			duration = DEFAULT_DURATION;
		} else {
			duration = duration.trim();
		}
		try {
			String value = ip.trim();
			int slash = value.indexOf('/');
			if (slash < 0) {
				crowdSecClient.banIp(value, duration, reason);
			} else {
				// CIDR → scope=range（Lua bouncer 不處理 scope=ip 帶前綴的值）；/32、/128 視為單一 IP
				String host = value.substring(0, slash);
				String prefix = value.substring(slash + 1);
				boolean singleV4 = host.indexOf(':') < 0 && "32".equals(prefix);
				boolean singleV6 = host.indexOf(':') >= 0 && "128".equals(prefix);
				if (singleV4 || singleV6) {
					crowdSecClient.banIp(host, duration, reason);
				} else {
					crowdSecClient.banRange(value, duration, reason);
				}
			}
			return renderSuccess();
		} catch (Exception e) {
			return fail(e);
		}
	}

	/**
	 * Ban a CIDR range ({@code scope=range}).
	 *
	 * @param range CIDR e.g. {@code 1.2.3.0/24}
	 */
	@Mapping("addRangeDecision")
	public JsonResult addRangeDecision(String range, String duration, String reason) {
		JsonResult missing = missingCredentials(true, false);
		if (missing != null) {
			return missing;
		}
		JsonResult gate = gateIpAndDuration(range, duration);
		if (gate != null) {
			return gate;
		}
		if (StrUtil.isBlank(duration)) {
			duration = DEFAULT_DURATION;
		} else {
			duration = duration.trim();
		}
		try {
			crowdSecClient.banRange(range.trim(), duration, reason);
			return renderSuccess();
		} catch (Exception e) {
			return fail(e);
		}
	}

	/**
	 * 加入白名單（舊端點，保留給舊呼叫端）：刪除該 IP 的 CrowdSec decisions 並寫入 DenyAllow 白名單。
	 * duration、reason、syncDenyAllow 不再使用。
	 */
	@Mapping("whitelistIp")
	public JsonResult whitelistIp(String ip, String duration, String reason, String syncDenyAllow) {
		try {
			return renderSuccess(crowdSecWhitelistService.whitelist(ip, getAdmin().getName()));
		} catch (IllegalArgumentException e) {
			return renderError(msgOr("crowdsecStr.invalidCidr", NetGuard.ERR_INVALID_CIDR));
		}
	}

	/**
	 * Delete decisions whose reason starts with {@code reasonPrefix}.
	 * Returns deleted count in {@code obj}.
	 */
	@Mapping("deleteByReason")
	public JsonResult deleteByReason(String reasonPrefix) {
		JsonResult missing = missingCredentials(true, true);
		if (missing != null) {
			return missing;
		}
		try {
			int n = crowdSecClient.deleteDecisionsByReasonPrefix(reasonPrefix);
			return renderSuccess(n);
		} catch (Exception e) {
			return fail(e);
		}
	}

	@Mapping("deleteDecision")
	public JsonResult deleteDecision(String decisionId) {
		JsonResult missing = missingCredentials(true, false);
		if (missing != null) {
			return missing;
		}
		try {
			crowdSecClient.deleteDecision(decisionId);
			return renderSuccess();
		} catch (Exception e) {
			return fail(e);
		}
	}

	// ── validation / error helpers ──────────────────────────────────────

	/**
	 * 依端點實際用到的憑證檢查：machine（新增、刪除）要 LAPI 位址＋machine 密碼，bouncer（讀名單）要 LAPI 位址＋bouncer 金鑰。
	 *
	 * @return null if OK; otherwise a renderError JsonResult 指出缺哪一種
	 */
	private JsonResult missingCredentials(boolean needMachine, boolean needBouncerKey) {
		if (needMachine && !crowdSecClient.isConfigured()) {
			return renderError(msgOr("crowdsecStr.machineNotConfigured", CrowdSecClient.NotConfiguredException.MACHINE));
		}
		if (needBouncerKey && !crowdSecClient.canRead()) {
			return renderError(
					msgOr("crowdsecStr.bouncerKeyNotConfigured", CrowdSecClient.NotConfiguredException.BOUNCER_KEY));
		}
		return null;
	}

	/**
	 * Shared pre-check for addDecision / addRangeDecision: CIDR + duration (blank → default later).
	 *
	 * @return null if OK; otherwise a renderError JsonResult
	 */
	private JsonResult gateIpAndDuration(String ipOrRange, String duration) {
		if (StrUtil.isBlank(ipOrRange) || !NetGuard.isValidCidr(ipOrRange.trim())) {
			return renderError(msgOr("crowdsecStr.invalidCidr", NetGuard.ERR_INVALID_CIDR));
		}
		String dur = duration;
		if (StrUtil.isBlank(dur)) {
			dur = DEFAULT_DURATION;
		}
		if (!NetGuard.isValidDuration(dur)) {
			return renderError(msgOr("crowdsecStr.invalidDuration", NetGuard.ERR_INVALID_DURATION));
		}
		return null;
	}

	/**
	 * Log full exception; map known codes to i18n; never return e.getMessage() for unknown.
	 */
	private JsonResult fail(Exception e) {
		if (e instanceof CrowdSecClient.MachineAuthException) {
			logger.warn("crowdsec machine login rejected");
			return renderError(msgOr("crowdsecStr.machineInvalid", CrowdSecClient.MachineAuthException.CODE));
		}
		if (e instanceof CrowdSecClient.LapiUnavailableException) {
			logger.warn("crowdsec lapi unavailable: {}", e.getMessage());
			return renderError(msgOr("crowdsecStr.statusUnreachable", CrowdSecClient.LapiUnavailableException.CODE));
		}
		if (e instanceof CrowdSecClient.NotConfiguredException) {
			return e.getMessage().equals(CrowdSecClient.NotConfiguredException.BOUNCER_KEY)
					? renderError(msgOr("crowdsecStr.bouncerKeyNotConfigured", e.getMessage()))
					: renderError(msgOr("crowdsecStr.machineNotConfigured", e.getMessage()));
		}
		logger.error("crowdsec api failed", e);
		if (e instanceof IllegalArgumentException) {
			String msg = e.getMessage();
			if (NetGuard.ERR_INVALID_CIDR.equals(msg)) {
				return renderError(msgOr("crowdsecStr.invalidCidr", msg));
			}
			if (NetGuard.ERR_INVALID_DURATION.equals(msg)) {
				return renderError(msgOr("crowdsecStr.invalidDuration", msg));
			}
			if (NetGuard.ERR_INVALID_REASON.equals(msg)) {
				return renderError(msgOr("crowdsecStr.invalidReason", msg));
			}
		}
		return renderError(msgOr("crowdsecStr.error", "crowdsec_error"));
	}

	/** MessageUtils if key resolves; else English fallback. */
	private String msgOr(String key, String fallback) {
		try {
			String s = m.get(key);
			if (StrUtil.isNotBlank(s) && !s.equals(key)) {
				return s;
			}
		} catch (Exception ignored) {
			// fall through
		}
		return fallback;
	}
}
