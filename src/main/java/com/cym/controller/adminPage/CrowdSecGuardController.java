package com.cym.controller.adminPage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.Server;
import com.cym.service.BlockedRequestLogReader;
import com.cym.service.CrowdSecClient;
import com.cym.service.CrowdSecWhitelistService;
import com.cym.service.HttpServerSelector;
import com.cym.service.IpLookupService;
import com.cym.service.OperateLogService;
import com.cym.service.ServerService;
import com.cym.service.WafEventService;
import com.cym.utils.BaseController;
import com.cym.utils.IpMatch;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

/** 「CrowdSec 防護」頁：IP 查詢、最近被擋下的 IP、封鎖名單、手動封鎖／解除、加入白名單 */
@Controller
@Mapping("/adminPage/crowdsecGuard")
public class CrowdSecGuardController extends BaseController {
	private static final Logger logger = LoggerFactory.getLogger(CrowdSecGuardController.class);
	static final int MAX_MINUTES = 7 * 24 * 60;
	/** IP 查詢一併列出這個 IP 最近的 WAF 事件，最多幾筆 */
	static final int RECENT_WAF_EVENTS = 10;

	@Inject
	IpLookupService ipLookupService;
	@Inject
	CrowdSecClient crowdSecClient;
	@Inject
	CrowdSecWhitelistService crowdSecWhitelistService;
	@Inject
	CrowdSecController crowdSecController;
	@Inject
	OperateLogService operateLogService;
	@Inject
	ServerService serverService;
	@Inject
	WafEventService wafEventService;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView) {
		modelAndView.view("/adminPage/crowdsecGuard/index.html");
		return modelAndView;
	}

	@Mapping("servers")
	public JsonResult servers() {
		List<Map<String, String>> list = new ArrayList<>();
		for (Server s : sqlHelper.findAll(Server.class)) {
			Map<String, String> row = new LinkedHashMap<>();
			row.put("id", s.getId());
			row.put("name", serverLabel(s));
			list.add(row);
		}
		return renderSuccess(list);
	}

	/** serverName 為空時用說明（descr） */
	private static String serverLabel(Server s) {
		return StrUtil.isNotBlank(s.getServerName()) ? s.getServerName() : StrUtil.nullToEmpty(s.getDescr());
	}

	@Mapping("ipLookup")
	public JsonResult ipLookup(String ip) {
		if (!IpMatch.isSingleIp(ip)) {
			return renderError(m.get("crowdsecGuardStr.invalidIp"));
		}
		Map<String, Object> obj = new LinkedHashMap<>();
		obj.put("result", ipLookupService.lookup(ip));
		Map<String, String> names = new HashMap<>();
		for (Server s : sqlHelper.findAll(Server.class)) {
			names.put(s.getId(), serverLabel(s));
		}
		obj.put("serverNames", names);
		obj.put("wafEvents", WafEventController.rows(wafEventService.recentForIp(ip, RECENT_WAF_EVENTS)));
		return renderSuccess(obj);
	}

	@Mapping("blocked")
	public JsonResult blocked(String serverId, Integer minutes) {
		int span = minutes == null || minutes <= 0 ? 60 : Math.min(minutes, MAX_MINUTES);
		List<Server> httpServers = serverService.getListByProxyType(new String[] { "0" });
		Predicate<String> filter = null;
		if (StrUtil.isNotBlank(serverId)) {
			filter = hostFilter(httpServers, serverId);
		}
		Path log = Path.of(homeConfig.home, "log", "access.log");
		BlockedRequestLogReader.Result r = readBlocked(log, filter, Instant.now().minusSeconds(span * 60L));
		Map<String, Object> obj = new LinkedHashMap<>();
		obj.put("rows", blockedRows(r.rows(), httpServers));
		obj.put("hostSupported", r.hostSupported());
		obj.put("coverFrom", r.coverFrom() == null ? null : r.coverFrom().toEpochMilli());
		obj.put("coverTo", r.coverTo() == null ? null : r.coverTo().toEpochMilli());
		obj.put("truncated", r.truncated());
		return renderSuccess(obj);
	}

	/**
	 * 選了某個站台時的 host 篩選：依 nginx 選 server 的規則（{@link HttpServerSelector}），只留這個站台在候選站台內的 host
	 * （同名等分不出站台的 host，每個候選站台都列出）。
	 * 站台不存在、停用、不是 HTTP 站台，或沒有任何 host 會落到它（沒有可比對的名稱也不是預設站台）時，一行都不符合。
	 */
	static Predicate<String> hostFilter(List<Server> httpServers, String serverId) {
		HttpServerSelector selector = new HttpServerSelector(httpServers);
		return host -> selector.candidates(host).contains(serverId);
	}

	/**
	 * 回傳給畫面的行。host 在同一優先層級有多個候選站台時（{@link HttpServerSelector#candidates}），
	 * ambiguous 為 true，candidates 列出候選站台名稱（名稱後附 listen，區分同名站台）；其他行 ambiguous 為 false、不帶 candidates。
	 */
	static List<Map<String, Object>> blockedRows(List<BlockedRequestLogReader.Row> rows, List<Server> httpServers) {
		HttpServerSelector selector = new HttpServerSelector(httpServers);
		Map<String, String> labels = new HashMap<>();
		for (Server s : httpServers) {
			if (s != null) {
				String label = serverLabel(s);
				labels.put(s.getId(), StrUtil.isBlank(s.getListen()) ? label : label + " (" + s.getListen().trim() + ")");
			}
		}
		List<Map<String, Object>> list = new ArrayList<>();
		for (BlockedRequestLogReader.Row row : rows) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("ip", row.ip());
			item.put("host", row.host());
			item.put("count", row.count());
			item.put("lastTime", row.lastTime().toEpochMilli());
			item.put("statuses", row.statuses());
			List<String> ids = selector.candidates(row.host());
			item.put("ambiguous", ids.size() > 1);
			if (ids.size() > 1) {
				item.put("candidates", ids.stream().map(id -> labels.getOrDefault(id, id)).toList());
			}
			list.add(item);
		}
		return list;
	}

	/**
	 * 讀被擋下的請求。hostFilter 為 null 表示不篩站台；篩選時沒有 host 的行不算，
	 * 涵蓋時間、hostSupported、truncated 仍依整份日誌回傳。
	 */
	static BlockedRequestLogReader.Result readBlocked(Path log, Predicate<String> hostFilter, Instant since) {
		return BlockedRequestLogReader.readMatching(log, BlockedRequestLogReader.DEFAULT_MAX_BYTES, hostFilter, since);
	}

	@Mapping("bans")
	public JsonResult bans(String scope, String q, Integer page, Integer limit) {
		if (!crowdSecClient.canRead()) {
			return renderError(m.get("crowdsecStr.bouncerKeyNotConfigured"));
		}
		boolean community = "community".equals(scope);
		int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 200);
		int curr = page == null || page <= 0 ? 1 : page;
		try {
			JSONArray all = CrowdSecClient.parseArray(crowdSecClient.listBansRaw(community));
			Map<String, Long> created = community ? Map.of() : createdTimes();
			List<Map<String, Object>> filtered = new ArrayList<>();
			for (int i = 0; i < all.size(); i++) {
				JSONObject o = all.getJSONObject(i);
				if (o == null || (StrUtil.isNotBlank(q) && !o.getStr("value", "").contains(q.trim()))) {
					continue;
				}
				Map<String, Object> row = new LinkedHashMap<>(o);
				row.put("createdAt", created.get(String.valueOf(o.get("id"))));
				filtered.add(row);
			}
			int from = Math.min((curr - 1) * size, filtered.size());
			Map<String, Object> obj = new LinkedHashMap<>();
			obj.put("records", filtered.subList(from, Math.min(from + size, filtered.size())));
			obj.put("count", filtered.size());
			obj.put("createdAvailable", !community && !created.isEmpty());
			return renderSuccess(obj);
		} catch (Exception e) {
			logger.warn("crowdsec bans failed: {}", e.getMessage());
			return renderError(m.get("crowdsecStr.statusUnreachable"));
		}
	}

	/** decision id → alert created_at（毫秒）；沒有 machine 憑證或失敗回空 Map，畫面顯示「—」 */
	private Map<String, Long> createdTimes() {
		Map<String, Long> map = new HashMap<>();
		if (!crowdSecClient.isConfigured()) {
			return map;
		}
		try {
			JSONArray alerts = CrowdSecClient.parseArray(crowdSecClient.activeDecisionAlertsRaw());
			for (int i = 0; i < alerts.size(); i++) {
				JSONObject a = alerts.getJSONObject(i);
				JSONArray ds = a == null ? null : a.getJSONArray("decisions");
				if (ds == null) {
					continue;
				}
				long at = Instant.parse(a.getStr("created_at")).toEpochMilli();
				for (int j = 0; j < ds.size(); j++) {
					map.put(String.valueOf(ds.getJSONObject(j).get("id")), at);
				}
			}
		} catch (Exception e) {
			logger.debug("crowdsec created times unavailable: {}", e.getMessage());
		}
		return map;
	}

	/** 手動封鎖：驗證與寫入沿用 CrowdSecController.addDecision（同一套 CIDR／duration 規則） */
	@Mapping("ban")
	public JsonResult ban(String ip, String duration, String reason) {
		JsonResult r = crowdSecController.addDecision(ip, duration, reason);
		if (r.isSuccess()) {
			operateLogService.addLog("", "[CrowdSec] ban " + ip + " " + StrUtil.blankToDefault(duration, "4h")
					+ (StrUtil.isBlank(reason) ? "" : " (" + reason + ")"), getAdmin().getName());
		}
		return r;
	}

	@Mapping("unban")
	public JsonResult unban(String decisionId, String value) {
		JsonResult r = crowdSecController.deleteDecision(decisionId);
		if (r.isSuccess()) {
			operateLogService.addLog("", "[CrowdSec] unban " + StrUtil.nullToEmpty(value) + " (decision " + decisionId + ")",
					getAdmin().getName());
		}
		return r;
	}

	@Mapping("whitelist")
	public JsonResult whitelist(String ip) {
		if (!IpMatch.isSingleIp(ip)) {
			return renderError(m.get("crowdsecGuardStr.invalidIp"));
		}
		return renderSuccess(crowdSecWhitelistService.whitelist(ip, getAdmin().getName()));
	}
}
