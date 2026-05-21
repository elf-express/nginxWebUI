package com.cym.controller.adminPage;

import java.util.LinkedHashMap;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;

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
		config.put("configured", StrUtil.isNotBlank(url) ? "true" : "false");
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
			HttpResponse resp = HttpRequest.get(url + "/v1/health").timeout(5000).execute();
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
			HttpResponse resp = HttpRequest.get(url + "/v1/alerts?limit=" + limit + "&offset=" + offset)
					.header("X-Api-Key", apiKey).timeout(10000).execute();
			return renderSuccess(resp.body());
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	@Mapping("decisions")
	public JsonResult decisions(int limit, int page) {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		if (StrUtil.isBlank(url) || StrUtil.isBlank(apiKey)) {
			return renderError("notConfigured");
		}
		try {
			int offset = (page - 1) * limit;
			HttpResponse resp = HttpRequest.get(url + "/v1/decisions?limit=" + limit + "&offset=" + offset)
					.header("X-Api-Key", apiKey).timeout(10000).execute();
			return renderSuccess(resp.body());
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	@Mapping("addDecision")
	public JsonResult addDecision(String ip, String duration, String reason) {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		if (StrUtil.isBlank(url) || StrUtil.isBlank(apiKey)) {
			return renderError("notConfigured");
		}
		try {
			String jsonBody = "{\"duration\":\"" + duration + "\",\"reason\":\"" + reason
					+ "\",\"scope\":\"ip\",\"value\":\"" + ip + "\",\"type\":\"ban\"}";
			HttpResponse resp = HttpRequest.post(url + "/v1/decisions").header("X-Api-Key", apiKey)
					.header("Content-Type", "application/json").body(jsonBody).timeout(10000).execute();
			return renderSuccess(resp.body());
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}

	@Mapping("deleteDecision")
	public JsonResult deleteDecision(String decisionId) {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		if (StrUtil.isBlank(url) || StrUtil.isBlank(apiKey)) {
			return renderError("notConfigured");
		}
		try {
			HttpResponse resp = HttpRequest.delete(url + "/v1/decisions/" + decisionId)
					.header("X-Api-Key", apiKey).timeout(10000).execute();
			return renderSuccess(resp.body());
		} catch (Exception e) {
			return renderError(e.getMessage());
		}
	}
}
