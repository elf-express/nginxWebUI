package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.ext.HttpGroupExt;
import com.cym.model.Http;
import com.cym.service.ConfService;
import com.cym.service.HttpService;
import com.cym.service.SettingService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;
import com.cym.utils.SnowFlakeUtils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Controller
@Mapping("/adminPage/http")
public class HttpController extends BaseController {
	@Inject
	HttpService httpService;
	@Inject
	SettingService settingService;
	@Inject
	MessageUtils m;
	@Inject
	ConfService confService;

	// 分組定義：groupName → { i18n displayName key, i18n description key, module note key }
	private static final String[][] GROUP_DEFS = {
		{ "base",    "httpGroup.base",    "httpGroup.baseDesc",    "" },
		{ "realip",  "httpGroup.realip",  "httpGroup.realipDesc",  "" },
		{ "geoip",   "httpGroup.geoip",   "httpGroup.geoipDesc",   "httpGroup.moduleGeoip2" },
		{ "gzip",    "httpGroup.gzip",    "httpGroup.gzipDesc",    "" },
		{ "brotli",  "httpGroup.brotli",  "httpGroup.brotliDesc",  "httpGroup.moduleBrotli" },
		{ "headers", "httpGroup.headers", "httpGroup.headersDesc", "" },
		{ "proxy",   "httpGroup.proxy",   "httpGroup.proxyDesc",   "" },
		{ "logging", "httpGroup.logging", "httpGroup.loggingDesc", "" },
	};

	/** 三態 mode:核心不可關(後端 enforce enable=true)。 */
	public static final Set<String> LOCKED_GROUPS = Set.of("base", "realip");
	/** 三態 mode:建議互斥(前端存檔時 warn,不強制)。 */
	public static final Set<String> MUTEX_GROUPS = Set.of("geoip");

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView) {
		List<Http> httpList = httpService.findAll();

		// 按 groupName 分組，保持 seq 順序
		LinkedHashMap<String, List<Http>> groupMap = new LinkedHashMap<>();
		// 先按預定義順序建立空列表
		for (String[] def : GROUP_DEFS) {
			groupMap.put(def[0], new ArrayList<>());
		}
		// 自訂組
		String customKey = "_custom";
		groupMap.put(customKey, new ArrayList<>());

		for (Http http : httpList) {
			String gn = http.getGroupName();
			if (StrUtil.isBlank(gn)) {
				groupMap.get(customKey).add(http);
			} else if (groupMap.containsKey(gn)) {
				groupMap.get(gn).add(http);
			} else {
				// 來自模板的動態組
				if (!groupMap.containsKey(gn)) {
					groupMap.put(gn, new ArrayList<>());
				}
				groupMap.get(gn).add(http);
			}
		}

		// 建構 HttpGroupExt 列表
		List<HttpGroupExt> groupList = new ArrayList<>();
		for (Map.Entry<String, List<Http>> entry : groupMap.entrySet()) {
			if (entry.getValue().isEmpty()) continue;

			HttpGroupExt ext = new HttpGroupExt();
			ext.setGroupName(entry.getKey());
			ext.setHttpList(entry.getValue());

			// 查找預定義分組的 i18n
			boolean found = false;
			for (String[] def : GROUP_DEFS) {
				if (def[0].equals(entry.getKey())) {
					ext.setDisplayName(m.get(def[1]));
					ext.setDescription(m.get(def[2]));
					ext.setModuleNote(StrUtil.isNotEmpty(def[3]) ? m.get(def[3]) : null);
					found = true;
					break;
				}
			}
			if (!found) {
				if (customKey.equals(entry.getKey())) {
					ext.setDisplayName(m.get("httpGroup.custom"));
					ext.setDescription(m.get("httpGroup.customDesc"));
				} else {
					// 來自模板的動態組，直接用 groupName 顯示
					ext.setDisplayName(entry.getKey());
					ext.setDescription("");
				}
			}

			groupList.add(ext);
		}

		modelAndView.put("httpList", httpList);
		modelAndView.put("groupList", groupList);

		modelAndView.view("/adminPage/http/index.html");
		return modelAndView;
	}

	@Mapping("addOver")
	public JsonResult addOver(Http http) {
		if (StrUtil.isEmpty(http.getId())) {
			http.setSeq(SnowFlakeUtils.getId());
		}
		sqlHelper.insertOrUpdate(http);

		return renderSuccess();
	}

	@Mapping("addTemplate")
	public JsonResult addTemplate(String templateId) {
		httpService.addTemplate(templateId);

		return renderSuccess();
	}

	@Mapping("detail")
	public JsonResult detail(String id) {
		return renderSuccess(sqlHelper.findById(id, Http.class));
	}

	@Mapping("del")
	public JsonResult del(String id) {
		String[] ids = id.split(",");
		sqlHelper.deleteByIds(ids, Http.class);

		return renderSuccess();
	}

	@Mapping("addGiudeOver")
	public JsonResult addGiudeOver(String json, Boolean logStatus, Boolean webSocket, Boolean mimeTypes) {
		List<Http> https = JSONUtil.toList(JSONUtil.parseArray(json), Http.class);

		if (mimeTypes) {
			Http http = new Http();
			http.setName("include");
			http.setValue("mime.types");
			http.setUnit("");
			https.add(http);

			http = new Http();
			http.setName("default_type");
			http.setValue("application/octet-stream");
			http.setUnit("");
			https.add(http);
		}

		if (logStatus) {
			Http http = new Http();
			http.setName("access_log");
			http.setValue(homeConfig.home + "log/access.log");
			http.setUnit("");
			https.add(http);

			http = new Http();
			http.setName("error_log");
			http.setValue(homeConfig.home + "log/error.log");
			http.setUnit("");
			https.add(http);
		}

		if (webSocket) {
			Http http = new Http();
			http.setName("map");
			http.setValue("$http_upgrade $connection_upgrade {\r\n" //
					+ "    default upgrade;\r\n" //
					+ "    '' close;\r\n" + "}\r\n");//
			http.setUnit("");
			https.add(http);
		}

		httpService.setAll(https);

		return renderSuccess();
	}

	@Mapping("setOrder")
	public JsonResult setOrder(String id, Integer count) {
		httpService.setSeq(id, count);
		return renderSuccess();
	}

	@Mapping("setEnable")
	public JsonResult setEnable(Http http) {
		sqlHelper.updateById(http);
		return renderSuccess();
	}

	/**
	 * http 參數 panel 存檔:全域 update Http.enable（勾選=true，未勾=false），
	 * 存檔前跑 nginx -t 預檢，失敗則 rollback。不自動 reload。
	 */
	@Mapping("saveEnable")
	public synchronized JsonResult saveEnable(String checkedIds) {
		Set<String> checked = new HashSet<>();
		if (StrUtil.isNotEmpty(checkedIds)) {
			for (String id : checkedIds.split(",")) {
				if (StrUtil.isNotBlank(id)) {
					checked.add(id.trim());
				}
			}
		}

		List<Http> httpList = sqlHelper.findAll(Http.class);
		Map<String, Boolean> oldEnable = new HashMap<>();
		for (Http http : httpList) {
			oldEnable.put(http.getId(), http.getEnable());
		}

		// 套用新 enable（只更新有變動的）；locked group 強制 enable=true（後端 enforce，防繞過）
		for (Http http : httpList) {
			boolean want = checked.contains(http.getId())
					|| (http.getGroupName() != null && LOCKED_GROUPS.contains(http.getGroupName()));
			if (!Objects.equals(http.getEnable(), want)) {
				http.setEnable(want);
				sqlHelper.updateById(http);
			}
		}

		String precheck = confService.precheckConf();
		if (precheck == null) {
			return renderSuccess(m.get("serverStr.httpParamSaved"));
		}
		if ("SKIPPED".equals(precheck)) {
			return renderSuccess(m.get("serverStr.httpParamPrecheckSkipped"));
		}
		// 預檢失敗 → rollback
		for (Http http : httpList) {
			Boolean old = oldEnable.get(http.getId());
			if (!Objects.equals(http.getEnable(), old)) {
				http.setEnable(old);
				sqlHelper.updateById(http);
			}
		}
		return renderError(m.get("serverStr.httpParamPrecheckFail") + "<br>" + precheck.replace("\n", "<br>"));
	}
}
