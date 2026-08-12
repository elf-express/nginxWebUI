package com.cym.controller.adminPage;

import java.util.LinkedHashMap;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;

import com.cym.service.CrowdSecClient;
import com.cym.service.SettingService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

@Controller
@Mapping("/adminPage/crowdsec")
public class CrowdSecController extends BaseController {
	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;

	@Mapping("getConfig")
	public JsonResult getConfig() {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		Map<String, String> config = new LinkedHashMap<String, String>();
		config.put("url", url != null ? url : "");
		if (StrUtil.isNotBlank(apiKey) && apiKey.length() > 8) {
			config.put("apiKey", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
		} else {
			config.put("apiKey", "");
		}
		config.put("configured", crowdSecClient.isConfigured() ? "true" : "false");
		return renderSuccess(config);
	}

	@Mapping("saveConfig")
	public JsonResult saveConfig(String url, String apiKey) {
		settingService.set("crowdsecUrl", url);
		if (StrUtil.isNotBlank(apiKey) && apiKey.contains("****")) {
			return renderSuccess();
		}
		settingService.set("crowdsecApiKey", apiKey);
		return renderSuccess();
	}

	@Mapping("status")
	public JsonResult status() {
		String url = settingService.get("crowdsecUrl");
		if (StrUtil.isBlank(url)) {
			return renderSuccess("notConfigured");
		}
		try {
			HttpResponse resp = HttpRequest.get(trimBase(url) + "/v1/health").timeout(5000).execute();
			if (resp.isOk()) {
				return renderSuccess("connected");
			} else {
				return renderSuccess("disconnected");
			}
		} catch (Exception e) {
			return renderSuccess("disconnected");
		}
	}

	@Mapping("alerts")
	public JsonResult alerts(int limit, int page) {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		if (StrUtil.isBlank(url) || StrUtil.isBlank(apiKey)) {
			return renderError("notConfigured");
		}
		try {
			int offset = (page - 1) * limit;
			HttpResponse resp = HttpRequest.get(trimBase(url) + "/v1/alerts?limit=" + limit + "&offset=" + offset)
					.header("X-Api-Key", apiKey).timeout(10000).execute();
			return renderSuccess(resp.body());
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	@Mapping("decisions")
	public JsonResult decisions(int limit, int page) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			int offset = (page - 1) * limit;
			return renderSuccess(crowdSecClient.listDecisionsRaw(limit, offset));
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	/**
	 * Ban a single IP — delegates to {@link CrowdSecClient#banIp}.
	 * Keeps legacy response shape (success with empty obj on OK).
	 */
	@Mapping("addDecision")
	public JsonResult addDecision(String ip, String duration, String reason) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			crowdSecClient.banIp(ip, duration, reason);
			return renderSuccess();
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	/**
	 * Ban a CIDR range ({@code scope=range}).
	 *
	 * @param range CIDR e.g. {@code 1.2.3.0/24}
	 */
	@Mapping("addRangeDecision")
	public JsonResult addRangeDecision(String range, String duration, String reason) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			crowdSecClient.banRange(range, duration, reason);
			return renderSuccess();
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	/**
	 * Whitelist a single IP via LAPI {@code type=whitelist}.
	 */
	@Mapping("whitelistIp")
	public JsonResult whitelistIp(String ip, String duration, String reason) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			crowdSecClient.whitelistIp(ip, duration, reason);
			return renderSuccess();
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	/**
	 * Delete decisions whose reason starts with {@code reasonPrefix}.
	 * Returns deleted count in {@code obj}.
	 */
	@Mapping("deleteByReason")
	public JsonResult deleteByReason(String reasonPrefix) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			int n = crowdSecClient.deleteDecisionsByReasonPrefix(reasonPrefix);
			return renderSuccess(n);
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	@Mapping("deleteDecision")
	public JsonResult deleteDecision(String decisionId) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			crowdSecClient.deleteDecision(decisionId);
			return renderSuccess();
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	private static String trimBase(String url) {
		if (url == null) {
			return "";
		}
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}
}
