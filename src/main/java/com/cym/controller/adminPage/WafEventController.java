package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;

import com.cym.model.WafEvent;
import com.cym.model.WafEventRule;
import com.cym.service.OperateLogService;
import com.cym.service.ServerService;
import com.cym.service.WafCategories;
import com.cym.service.WafEventService;
import com.cym.service.WafEventSyncService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;

/** 「CrowdSec 防護」頁的 WAF 事件分頁（E-394）：事件列表、同步狀態、立即同步、保留天數 */
@Controller
@Mapping("/adminPage/crowdsecGuard/waf")
public class WafEventController extends BaseController {
	static final int DEFAULT_LIMIT = 20;
	static final int MAX_LIMIT = 200;

	@Inject
	WafEventService wafEventService;
	@Inject
	WafEventSyncService wafEventSyncService;
	@Inject
	ServerService serverService;
	@Inject
	OperateLogService operateLogService;

	/** serverId、category、action、ip 篩選；days 為 0 或空白表示保留期內全部 */
	@Mapping("events")
	public JsonResult events(String serverId, String category, String action, String ip, Integer days, Integer page,
			Integer limit) {
		int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
		int curr = page == null || page <= 0 ? 1 : page;
		WafEventService.Query q = new WafEventService.Query(StrUtil.trimToNull(serverId), knownCategory(category),
				knownAction(action), StrUtil.trimToNull(ip), days, curr, size);
		WafEventService.Result r = wafEventService.list(q, System.currentTimeMillis(),
				serverService.getListByProxyType(new String[] { "0" }));
		Map<String, Object> obj = new LinkedHashMap<>();
		obj.put("records", rows(r.rows()));
		obj.put("count", r.count());
		return renderSuccess(obj);
	}

	@Mapping("status")
	public JsonResult status() {
		Map<String, Object> obj = wafEventSyncService.status(System.currentTimeMillis()).toMap();
		obj.put("categories", WafCategories.ALL);
		return renderSuccess(obj);
	}

	@Mapping("sync")
	public JsonResult sync() {
		return renderSuccess(wafEventSyncService.sync(System.currentTimeMillis()).toMap());
	}

	@Mapping("retention")
	public JsonResult retention(String days) {
		Integer n = WafEventSyncService.parseRetention(days);
		if (n == null) {
			return renderError(m.get("crowdsecGuardStr.wafRetentionInvalid"));
		}
		wafEventSyncService.setRetentionDays(n);
		operateLogService.addLog("", "[CrowdSec] WAF retention " + n + " days", getAdmin().getName());
		return renderSuccess();
	}

	/** 只接受 WafCategories.ALL 內的類別，其他當成不篩 */
	static String knownCategory(String category) {
		String c = StrUtil.trimToNull(category);
		return c != null && WafCategories.ALL.contains(c) ? c : null;
	}

	/** 只接受 block、log，其他當成不篩 */
	static String knownAction(String action) {
		String a = StrUtil.trimToNull(action);
		return WafEventService.ACTION_BLOCK.equals(a) || WafEventService.ACTION_LOG.equals(a) ? a : null;
	}

	/** 給畫面的列：事件欄位加上 rules；合併用的 eventKey、查詢用的 ipKey 與 categories 不輸出 */
	public static List<Map<String, Object>> rows(List<WafEventService.Row> rows) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (WafEventService.Row row : rows) {
			WafEvent e = row.event();
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", e.getId());
			item.put("eventTime", e.getEventTime());
			item.put("ip", e.getIp());
			item.put("country", e.getCountry());
			item.put("host", e.getHost());
			item.put("method", e.getMethod());
			item.put("uri", e.getUri());
			item.put("userAgent", e.getUserAgent());
			item.put("ruleSource", e.getRuleSource());
			item.put("action", e.getAction());
			item.put("message", e.getMessage());
			List<Map<String, Object>> rules = new ArrayList<>();
			for (WafEventRule r : row.rules()) {
				Map<String, Object> rule = new LinkedHashMap<>();
				rule.put("ruleName", r.getRuleName());
				rule.put("ruleSource", r.getRuleSource());
				rule.put("category", r.getCategory());
				rule.put("message", r.getMessage());
				rules.add(rule);
			}
			item.put("rules", rules);
			list.add(item);
		}
		return list;
	}
}
