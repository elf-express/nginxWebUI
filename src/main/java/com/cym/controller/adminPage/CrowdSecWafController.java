package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.WafRuleSetting;
import com.cym.service.WafRuleCatalog;
import com.cym.service.WafRuleCatalog.Catalog;
import com.cym.service.WafRuleCatalog.Rule;
import com.cym.service.WafTuningService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** 「CrowdSec 防護 → WAF 規則」（E-395）：規則目錄與設定、套用、套用結果 */
@Controller
@Mapping("/adminPage/crowdsecGuard/wafRules")
public class CrowdSecWafController extends BaseController {
	private static final Logger logger = LoggerFactory.getLogger(CrowdSecWafController.class);
	static final long HIT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

	@Inject
	WafTuningService wafTuningService;

	@Mapping("list")
	public JsonResult rules() {
		Catalog cat = wafTuningService.catalog();
		Map<String, Object> obj = new LinkedHashMap<>();
		obj.put("available", cat != null);
		obj.put("last", wafTuningService.status(null));
		if (cat == null) {
			obj.put("rules", List.of());
			obj.put("categories", List.of());
			obj.put("skippedNames", Map.of());
			return renderSuccess(obj);
		}
		obj.put("generatedAt", cat.generatedAt());
		obj.put("crowdsec", cat.crowdsec());
		obj.putAll(rulesView(cat, sqlHelper.findAll(WafRuleSetting.class), wafTuningService.hitsSince(System.currentTimeMillis() - HIT_WINDOW_MS),
				wafTuningService.hostsByServer().keySet()));
		obj.put("skippedNames", wafTuningService.skippedNames());
		obj.put("inSync", wafTuningService.inSync());
		return renderSuccess(obj);
	}

	/** serverIds：目前存在的站台；排除站台只列這些，已刪除站台的 id 不回傳給前端 */
	static Map<String, Object> rulesView(Catalog cat, List<WafRuleSetting> settings, Map<String, Integer> hits, Set<String> serverIds) {
		Map<String, WafRuleSetting> rules = WafTuningService.byKey(settings, WafTuningService.SCOPE_RULE);
		Map<String, WafRuleSetting> categories = WafTuningService.byKey(settings, WafTuningService.SCOPE_CATEGORY);
		List<Map<String, Object>> ruleRows = new ArrayList<>();
		Map<String, Map<String, Object>> catRows = new LinkedHashMap<>();
		for (Rule r : cat.rules()) {
			int h = hits.getOrDefault(r.key(), 0);
			WafRuleSetting rs = rules.get(r.key());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("key", r.key());
			row.put("source", r.source());
			row.put("category", r.category());
			row.put("label", r.label());
			row.put("description", r.description());
			row.put("cves", r.cves());
			row.put("hits", h);
			row.put("action", WafTuningService.effectiveAction(r, rules, categories));
			row.put("setting", rs == null || WafTuningService.INHERIT.equals(rs.getAction()) ? null : rs.getAction());
			row.put("excludedServerIds", rs == null ? List.of() : existing(rs.getExcludedServerIds(), serverIds));
			ruleRows.add(row);
			Map<String, Object> c = catRows.computeIfAbsent(r.category(), k -> {
				WafRuleSetting cs = categories.get(k);
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("key", k);
				m.put("source", r.source());
				m.put("ruleCount", 0);
				m.put("hits", 0);
				m.put("action", cs != null && WafTuningService.allowedAction(r.source(), cs.getAction()) ? cs.getAction()
						: WafTuningService.defaultActionOf(r.source()));
				m.put("setting", cs == null ? null : cs.getAction());
				m.put("excludedServerIds", cs == null ? List.of() : existing(cs.getExcludedServerIds(), serverIds));
				return m;
			});
			c.put("ruleCount", (Integer) c.get("ruleCount") + 1);
			c.put("hits", (Integer) c.get("hits") + h);
		}
		List<Map<String, Object>> cats = new ArrayList<>(catRows.values());
		cats.sort(Comparator.comparing((Map<String, Object> m) -> WafRuleCatalog.SOURCE_VPATCH.equals(m.get("source")) ? 0 : 1)
				.thenComparing(m -> (String) m.get("key")));
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("rules", ruleRows);
		view.put("categories", cats);
		return view;
	}

	static List<String> existing(String csv, Set<String> serverIds) {
		return WafTuningService.splitIds(csv).stream().filter(serverIds::contains).toList();
	}

	static List<WafRuleSetting> parseChanges(String json) {
		if (StrUtil.isBlank(json)) {
			throw new IllegalArgumentException("changes");
		}
		JSONArray arr = JSONUtil.parseArray(json);
		List<WafRuleSetting> list = new ArrayList<>();
		for (int i = 0; i < arr.size(); i++) {
			JSONObject o = arr.getJSONObject(i);
			WafRuleSetting s = new WafRuleSetting();
			s.setScope(o.getStr("scope"));
			s.setRuleKey(o.getStr("ruleKey"));
			s.setAction(o.getStr("action"));
			s.setExcludedServerIds(o.getStr("excludedServerIds", ""));
			list.add(s);
		}
		return list;
	}

	@Mapping("apply")
	public JsonResult apply(String changes) {
		List<WafRuleSetting> list;
		try {
			list = parseChanges(changes);
		} catch (RuntimeException e) {
			return renderError(m.get("crowdsecWafStr.invalidChange").replace("{0}", "changes"));
		}
		try {
			return renderSuccess(Map.of("requestId", wafTuningService.apply(list, getAdmin().getName())));
		} catch (WafTuningService.NoCatalogException e) {
			return renderError(m.get("crowdsecWafStr.noCatalog"));
		} catch (IllegalArgumentException e) {
			return renderError(m.get("crowdsecWafStr.invalidChange").replace("{0}", e.getMessage()));
		} catch (Exception e) {
			logger.warn("WAF apply failed: {}", e.getMessage());
			return renderError(m.get("crowdsecWafStr.writeFailed"));
		}
	}

	@Mapping("status")
	public JsonResult applyStatus(String requestId) {
		return renderSuccess(wafTuningService.status(requestId));
	}
}
