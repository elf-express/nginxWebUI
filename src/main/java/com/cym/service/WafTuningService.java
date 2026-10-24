package com.cym.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import com.cym.config.HomeConfig;
import com.cym.model.Server;
import com.cym.model.WafEventRule;
import com.cym.model.WafRuleSetting;
import com.cym.service.WafRuleCatalog.Catalog;
import com.cym.service.WafRuleCatalog.Rule;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * WAF 規則調整（E-395）：設定正本在 WafRuleSetting，套用時產生 crowdsec 的自訂 appsec-config（nginxwebui/custom），
 * 寫進與 crowdsec 共用的目錄，由 crowdsec 容器內的 nwu-waf-watch.sh 驗證後重新載入並寫回結果。
 */
@Component
public class WafTuningService {
	public static final String DIR = "crowdsec-waf";
	public static final String SCOPE_RULE = "rule";
	public static final String SCOPE_CATEGORY = "category";
	public static final String INHERIT = "inherit";
	static final Set<String> VPATCH_ACTIONS = Set.of("block", "log", "off");
	static final Set<String> CRS_ACTIONS = Set.of("count", "log", "off");
	private static final Pattern HOST = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*");
	private static final String REQ_HOST = "lower(split(req.Host, \":\")[0])";

	@Inject
	SqlHelper sqlHelper;
	@Inject
	HomeConfig homeConfig;
	@Inject
	OperateLogService operateLogService;

	/** 共用目錄不存在或沒有規則目錄 */
	public static class NoCatalogException extends RuntimeException {
		public NoCatalogException() {
			super("no_catalog");
		}
	}

	public record Hosts(List<String> exact, List<String> suffixes, List<String> skipped) {
	}

	public Path dir() {
		return Path.of(homeConfig.home, DIR);
	}

	public Catalog catalog() {
		return WafRuleCatalog.load(dir().resolve("catalog"));
	}

	static String defaultAction(String source) {
		return WafRuleCatalog.SOURCE_VPATCH.equals(source) ? "block" : "count";
	}

	static boolean allowed(String source, String action) {
		return action != null && (WafRuleCatalog.SOURCE_VPATCH.equals(source) ? VPATCH_ACTIONS : CRS_ACTIONS).contains(action);
	}

	public static boolean allowedAction(String source, String action) {
		return allowed(source, action);
	}

	public static String defaultActionOf(String source) {
		return defaultAction(source);
	}

	static String sourceOfCategory(String category) {
		return category.startsWith(WafRuleCatalog.SOURCE_VPATCH + ":") ? WafRuleCatalog.SOURCE_VPATCH : WafRuleCatalog.SOURCE_CRS;
	}

	/** 單條設定（不是 inherit、且是該來源允許的處理方式）優先，其次分類設定，最後是來源預設 */
	public static String effectiveAction(Rule r, Map<String, WafRuleSetting> rules, Map<String, WafRuleSetting> categories) {
		WafRuleSetting rs = rules.get(r.key());
		if (rs != null && allowed(r.source(), rs.getAction())) {
			return rs.getAction();
		}
		WafRuleSetting cs = categories.get(r.category());
		if (cs != null && allowed(r.source(), cs.getAction())) {
			return cs.getAction();
		}
		return defaultAction(r.source());
	}

	public static List<String> splitIds(String csv) {
		List<String> ids = new ArrayList<>();
		if (StrUtil.isNotBlank(csv)) {
			for (String id : csv.split(",")) {
				if (StrUtil.isNotBlank(id) && !ids.contains(id.trim())) {
					ids.add(id.trim());
				}
			}
		}
		return ids;
	}

	/** 單條與分類排除站台的聯集 */
	static Set<String> excludedServers(Rule r, Map<String, WafRuleSetting> rules, Map<String, WafRuleSetting> categories) {
		Set<String> ids = new TreeSet<>();
		WafRuleSetting rs = rules.get(r.key());
		WafRuleSetting cs = categories.get(r.category());
		if (rs != null) {
			ids.addAll(splitIds(rs.getExcludedServerIds()));
		}
		if (cs != null) {
			ids.addAll(splitIds(cs.getExcludedServerIds()));
		}
		return ids;
	}

	public static Map<String, WafRuleSetting> byKey(List<WafRuleSetting> settings, String scope) {
		Map<String, WafRuleSetting> map = new HashMap<>();
		for (WafRuleSetting s : settings) {
			if (scope.equals(s.getScope()) && s.getRuleKey() != null) {
				map.put(s.getRuleKey(), s);
			}
		}
		return map;
	}

	/** server_name 展開成可比對 req.Host 的名稱：完全名稱與後綴；regex、www.*、_ 等無法比對的列在 skipped */
	public static Hosts hostsOf(String serverName) {
		List<String> exact = new ArrayList<>();
		List<String> suffixes = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		if (StrUtil.isBlank(serverName)) {
			return new Hosts(exact, suffixes, skipped);
		}
		for (String raw : serverName.trim().split("\\s+")) {
			String n = raw.toLowerCase(Locale.ROOT);
			if (n.startsWith("*.") && HOST.matcher(n.substring(2)).matches()) {
				suffixes.add(n.substring(1));
			} else if (n.startsWith(".") && HOST.matcher(n.substring(1)).matches()) {
				exact.add(n.substring(1));
				suffixes.add(n);
			} else if (HOST.matcher(n).matches()) {
				exact.add(n);
			} else {
				skipped.add(raw);
			}
		}
		return new Hosts(exact, suffixes, skipped);
	}

	/** 這些站台的 host 條件；沒有可比對的名稱時回 null */
	static String hostFilter(Collection<String> serverIds, Map<String, Hosts> hostsByServer) {
		Set<String> exact = new TreeSet<>();
		Set<String> suffixes = new TreeSet<>();
		for (String id : serverIds) {
			Hosts h = hostsByServer.get(id);
			if (h != null) {
				exact.addAll(h.exact());
				suffixes.addAll(h.suffixes());
			}
		}
		List<String> parts = new ArrayList<>();
		if (!exact.isEmpty()) {
			parts.add(REQ_HOST + " in " + quoted(exact));
		}
		for (String s : suffixes) {
			parts.add(REQ_HOST + " endsWith " + q(s));
		}
		if (parts.isEmpty()) {
			return null;
		}
		return parts.size() == 1 ? parts.get(0) : "(" + String.join(" || ", parts) + ")";
	}

	private static String q(String s) {
		return "\"" + s + "\"";
	}

	private static String quoted(Collection<String> items) {
		return items.stream().map(WafTuningService::q).collect(Collectors.joining(", ", "[", "]"));
	}

	private static String remove(Rule r) {
		return WafRuleCatalog.SOURCE_VPATCH.equals(r.source()) ? "RemoveInBandRuleByName(" + q(r.key()) + ")" : "RemoveOutBandRuleByID(" + r.key() + ")";
	}

	/** 產生 nginxwebui/custom；沒有任何設定時與 docker/crowdsec/nwu-custom.yaml 相同 */
	public static String render(Catalog cat, List<WafRuleSetting> settings, Map<String, Hosts> hostsByServer) {
		Map<String, WafRuleSetting> rules = byKey(settings, SCOPE_RULE);
		Map<String, WafRuleSetting> categories = byKey(settings, SCOPE_CATEGORY);
		List<Rule> off = new ArrayList<>();
		List<String> logVpatch = new ArrayList<>();
		List<String> logCrs = new ArrayList<>();
		Map<String, List<Rule>> excluded = new TreeMap<>();
		List<Rule> sorted = new ArrayList<>(cat.rules());
		sorted.sort((a, b) -> a.source().equals(b.source()) ? a.key().compareTo(b.key()) : b.source().compareTo(a.source()));
		for (Rule r : sorted) {
			String action = effectiveAction(r, rules, categories);
			boolean vpatch = WafRuleCatalog.SOURCE_VPATCH.equals(r.source());
			if ("off".equals(action)) {
				off.add(r);
				continue;
			}
			if ("log".equals(action)) {
				(vpatch ? logVpatch : logCrs).add(r.key());
			}
			String filter = hostFilter(excludedServers(r, rules, categories), hostsByServer);
			if (filter != null) {
				excluded.computeIfAbsent((vpatch ? "IsInBand && " : "IsOutBand && ") + filter, k -> new ArrayList<>()).add(r);
			}
		}
		StringBuilder y = new StringBuilder();
		y.append("# Generated by nginxWebUI (CrowdSec Guard > WAF rules). Do not edit by hand.\n");
		y.append("name: nginxwebui/custom\n");
		y.append("default_remediation: ban\n");
		y.append("inband_rules:\n - crowdsecurity/base-config\n - crowdsecurity/vpatch-*\n");
		y.append("outofband_rules:\n - crowdsecurity/crs\n");
		if (!off.isEmpty()) {
			y.append("on_load:\n - apply:\n");
			off.forEach(r -> y.append("   - ").append(remove(r)).append('\n'));
		}
		if (!excluded.isEmpty()) {
			y.append("pre_eval:\n");
			excluded.forEach((filter, rs) -> {
				y.append(" - filter: ").append(filter).append("\n   apply:\n");
				rs.forEach(r -> y.append("    - ").append(remove(r)).append('\n'));
			});
		}
		y.append("on_match:\n");
		if (!logVpatch.isEmpty()) {
			y.append(" - filter: IsInBand && all(evt.Appsec.MatchedRules, {.name in ").append(quoted(logVpatch))
					.append("})\n   apply:\n    - SetRemediation(\"allow\")\n    - CancelEvent()\n");
		}
		y.append(" - filter: IsOutBand == true\n   apply:\n    - SendAlert()\n");
		if (!logCrs.isEmpty()) {
			y.append(" - filter: IsOutBand && all(evt.Appsec.MatchedRules, {.id < 911000 || .id >= 949000 || .id in [")
					.append(String.join(", ", logCrs)).append("]})\n   apply:\n    - CancelEvent()\n");
		}
		return y.toString();
	}

	/** vpatch 事件的結果：命中的 vpatch 規則全部是「只記錄」時為 log，否則 block */
	public static String vpatchActionOf(Collection<String> ruleNames, Map<String, String> effective) {
		boolean any = false;
		for (String n : ruleNames) {
			if (n == null || !n.startsWith("crowdsecurity/vpatch-")) {
				continue;
			}
			any = true;
			if (!"log".equals(effective.getOrDefault(n, "block"))) {
				return "block";
			}
		}
		return any ? "log" : "block";
	}

	/** 目前每條規則的有效處理方式；沒有規則目錄時只看單條設定 */
	public Map<String, String> effectiveActions() {
		List<WafRuleSetting> settings = sqlHelper.findAll(WafRuleSetting.class);
		Map<String, WafRuleSetting> rules = byKey(settings, SCOPE_RULE);
		Map<String, WafRuleSetting> categories = byKey(settings, SCOPE_CATEGORY);
		Map<String, String> map = new HashMap<>();
		Catalog cat = catalog();
		if (cat == null) {
			rules.forEach((k, s) -> map.put(k, s.getAction()));
			return map;
		}
		for (Rule r : cat.rules()) {
			map.put(r.key(), effectiveAction(r, rules, categories));
		}
		return map;
	}

	static void validate(WafRuleSetting s, Catalog cat, Set<String> serverIds) {
		String key = StrUtil.nullToEmpty(s.getRuleKey());
		String source;
		if (SCOPE_RULE.equals(s.getScope())) {
			Rule r = cat.byKey().get(key);
			if (r == null) {
				throw new IllegalArgumentException(key);
			}
			source = r.source();
			if (INHERIT.equals(s.getAction())) {
				source = null;
			}
		} else if (SCOPE_CATEGORY.equals(s.getScope())) {
			if (!cat.categories().contains(key)) {
				throw new IllegalArgumentException(key);
			}
			source = sourceOfCategory(key);
		} else {
			throw new IllegalArgumentException(key);
		}
		if (source != null && !allowed(source, s.getAction())) {
			throw new IllegalArgumentException(key);
		}
		for (String id : splitIds(s.getExcludedServerIds())) {
			if (!serverIds.contains(id)) {
				throw new IllegalArgumentException(key);
			}
		}
	}

	/** 處理方式等於預設且沒有排除站台的設定不存，已存在的刪除 */
	static boolean isDefault(WafRuleSetting s) {
		if (!splitIds(s.getExcludedServerIds()).isEmpty()) {
			return false;
		}
		if (SCOPE_RULE.equals(s.getScope())) {
			return INHERIT.equals(s.getAction()) || StrUtil.isBlank(s.getAction());
		}
		return defaultAction(sourceOfCategory(s.getRuleKey())).equals(s.getAction());
	}

	/** 先寫 custom.yaml 再寫 request，兩者都先寫暫存檔再 rename，監看程式不會讀到寫一半的檔案 */
	static void writeApply(Path dir, String yaml, String requestId) throws IOException {
		Files.createDirectories(dir);
		writeAtomic(dir.resolve("custom.yaml"), yaml);
		writeAtomic(dir.resolve("request"), requestId + "\n");
	}

	/** 暫存檔用隨機檔名建立（不跟隨共用目錄裡預先放的檔案或 symlink），rename 只取代目標本身，不會寫進 symlink 指向的檔案 */
	private static void writeAtomic(Path target, String content) throws IOException {
		Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), "." + target.getFileName() + ".", ".tmp");
		try {
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			try {
				Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
			} catch (UnsupportedOperationException e) {
				// 非 POSIX 檔案系統沒有權限位元
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
	}

	/** 共用目錄裡的檔案可能被另一個容器換成 symlink：只讀一般檔案，symlink、不存在或讀不到都回 null */
	static String readRegular(Path file) {
		try {
			if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
				return null;
			}
			try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			return null;
		}
	}

	public Map<String, Hosts> hostsByServer() {
		Map<String, Hosts> map = new HashMap<>();
		for (Server s : sqlHelper.findAll(Server.class)) {
			map.put(s.getId(), hostsOf(s.getServerName()));
		}
		return map;
	}

	/** 存設定、產生並寫入自訂 appsec-config、留操作日誌；回套用編號 */
	public synchronized String apply(List<WafRuleSetting> changes, String adminName) throws IOException {
		Catalog cat = catalog();
		if (cat == null) {
			throw new NoCatalogException();
		}
		Map<String, Hosts> hosts = hostsByServer();
		for (WafRuleSetting c : changes) {
			c.setExcludedServerIds(String.join(",", splitIds(c.getExcludedServerIds())));
			validate(c, cat, hosts.keySet());
		}
		for (WafRuleSetting c : changes) {
			WafRuleSetting old = sqlHelper.findOneByQuery(new ConditionAndWrapper().eq("scope", c.getScope()).eq("ruleKey", c.getRuleKey()), WafRuleSetting.class);
			if (isDefault(c)) {
				if (old != null) {
					sqlHelper.deleteById(old.getId(), WafRuleSetting.class);
				}
				continue;
			}
			if (old != null) {
				c.setId(old.getId());
			}
			sqlHelper.insertOrUpdate(c);
		}
		String yaml = render(cat, sqlHelper.findAll(WafRuleSetting.class), hosts);
		Path custom = dir().resolve("custom.yaml");
		String before = StrUtil.nullToEmpty(readRegular(custom));
		String id = String.valueOf(nextRequestId(System.currentTimeMillis(), readRequest()));
		writeApply(dir(), yaml, id);
		StringBuilder summary = new StringBuilder("[CrowdSec WAF] apply " + id + ":");
		for (WafRuleSetting c : changes) {
			summary.append(' ').append(c.getScope()).append(' ').append(c.getRuleKey()).append('=').append(c.getAction());
			if (StrUtil.isNotBlank(c.getExcludedServerIds())) {
				summary.append(" exclude[").append(c.getExcludedServerIds()).append(']');
			}
			summary.append(';');
		}
		operateLogService.addLog(before, summary + "\n" + yaml, adminName);
		return id;
	}

	/** 套用編號單調遞增：現在毫秒，但不小於目前 request 檔的編號 + 1；讀不到或不是數字就用現在毫秒 */
	static long nextRequestId(long nowMillis, String currentRequest) {
		try {
			return Math.max(nowMillis, Long.parseLong(StrUtil.nullToEmpty(currentRequest).trim()) + 1);
		} catch (NumberFormatException e) {
			return nowMillis;
		}
	}

	private String readRequest() {
		return readRegular(dir().resolve("request"));
	}

	/** 資料庫設定產生的 YAML 與 CrowdSec 目前生效的設定是否一致；沒有規則目錄時視為一致 */
	public boolean inSync() {
		Catalog cat = catalog();
		if (cat == null) {
			return true;
		}
		String lastGood = readRegular(dir().resolve("last-good.yaml"));
		return inSync(cat, sqlHelper.findAll(WafRuleSetting.class), hostsByServer(), lastGood);
	}

	private static String normalize(String yaml) {
		return yaml.replace("\r\n", "\n");
	}

	/** lastGood 為 null（沒有成功套用過）時，生效的是 image 預設設定 */
	static boolean inSync(Catalog cat, List<WafRuleSetting> settings, Map<String, Hosts> hostsByServer, String lastGood) {
		String expected = render(cat, settings, hostsByServer);
		String current = lastGood != null ? lastGood : render(cat, List.of(), Map.of());
		return normalize(expected).equals(normalize(current));
	}

	/** state：none（沒送出過）／pending（結果還沒回來）／ok／failed；requestId 為 null 時看目前的 request 檔 */
	public Map<String, Object> status(String requestId) {
		Path d = dir();
		String id = requestId;
		if (StrUtil.isBlank(id)) {
			String request = readRegular(d.resolve("request"));
			id = request == null ? null : request.trim();
		}
		return statusOf(StrUtil.blankToDefault(id, null), readRegular(d.resolve("result.json")));
	}

	static Map<String, Object> statusOf(String requestId, String resultJson) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (requestId == null) {
			map.put("state", "none");
			return map;
		}
		map.put("requestId", requestId);
		map.put("state", "pending");
		if (StrUtil.isBlank(resultJson)) {
			return map;
		}
		try {
			JSONObject o = JSONUtil.parseObj(resultJson);
			if (requestId.equals(o.getStr("id")) && Set.of("ok", "failed").contains(o.getStr("status"))) {
				map.put("state", o.getStr("status"));
				map.put("message", o.getStr("message", ""));
				map.put("at", o.getStr("at"));
			}
		} catch (RuntimeException e) {
			// 監看程式寫到一半：當作還沒回來
		}
		return map;
	}

	/** since 之後各規則的命中數（WafEventRule.ruleName → 次數） */
	public Map<String, Integer> hitsSince(long sinceMillis) {
		Map<String, Integer> hits = new HashMap<>();
		for (WafEventRule rule : sqlHelper.findListByQuery(new ConditionAndWrapper().gte("eventTime", sinceMillis), WafEventRule.class)) {
			if (rule.getRuleName() != null) {
				hits.merge(rule.getRuleName(), 1, Integer::sum);
			}
		}
		return hits;
	}

	/** 站台 id → 無法用來排除的 server_name */
	public Map<String, List<String>> skippedNames() {
		Map<String, List<String>> map = new LinkedHashMap<>();
		hostsByServer().forEach((id, h) -> {
			if (!h.skipped().isEmpty()) {
				map.put(id, h.skipped());
			}
		});
		return map;
	}
}
