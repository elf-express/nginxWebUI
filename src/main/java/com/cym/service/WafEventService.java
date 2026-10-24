package com.cym.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;

import com.cym.model.Server;
import com.cym.model.WafEvent;
import com.cym.model.WafEventRule;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.bean.Sort.Direction;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.ConditionOrWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.IpMatch;

import cn.hutool.core.util.StrUtil;

/**
 * WAF 事件（E-394）：存入同步來的命中、依站台／類別／結果／IP／期間查詢、清理。
 * 時間欄位存 epoch 毫秒；SqlHelper 欄位都是 TEXT，13 位數字的字串比較與數值比較結果相同。
 */
@Component
public class WafEventService {
	public static final String ACTION_BLOCK = "block";
	public static final String ACTION_LOG = "log";
	static final long DAY_MS = 86_400_000L;
	static final int ID_CHUNK = 500;
	static final int MAX_LIMIT = 200;
	static final int MAX_DAYS = 3650;
	static final int MAX_PAGE = 100_000;

	@Inject
	SqlHelper sqlHelper;
	@Inject
	GeoLookup geoLookup;

	Function<String, String> countryOf;

	@Init
	public void init() {
		countryOf = ip -> geoLookup.country(ip).code();
	}

	/** 查詢條件：serverId／category／action／ip 空白表示不篩；days 為 null 或 0 表示不限期間（保留期內全部） */
	public record Query(String serverId, String category, String action, String ip, Integer days, int page, int limit) {
	}

	/** 一個事件與它命中的規則 */
	public record Row(WafEvent event, List<WafEventRule> rules) {
	}

	public record Result(List<Row> rows, long count) {
	}

	/** 同一個請求：IP、方法、網址、秒都相同 */
	static String eventKey(WafAlertParser.Hit h) {
		return h.ip() + "|" + StrUtil.nullToEmpty(h.method()) + "|" + StrUtil.nullToEmpty(h.uri()) + "|" + h.time().getEpochSecond();
	}

	/**
	 * 去重鍵：alert id 加建立時間（秒）。CrowdSec 資料庫重建後 alert id 會從 1 重來，只看 id 會把保留期內的新 alert 當成舊的。
	 */
	static String alertKey(WafAlertParser.Hit h) {
		return h.alertId() + "@" + h.time().getEpochSecond();
	}

	/**
	 * 存入一批命中，回新增的事件數。已存過的 alert（alertKey）跳過（重疊拉取、翻頁重複都會遇到）；
	 * 同一個請求的 vpatch 與 CRS alert 併成一個事件（也併進先前已存的同一個請求）。
	 */
	public synchronized int store(List<WafAlertParser.Hit> hits) {
		Set<String> seen = existingAlertIds(hits);
		List<WafAlertParser.Hit> fresh = new ArrayList<>();
		for (WafAlertParser.Hit h : hits) {
			if (seen.add(alertKey(h))) {
				fresh.add(h);
			}
		}
		fresh.sort(Comparator.comparing(WafAlertParser.Hit::time));
		Map<String, WafEvent> byKey = loadEvents(fresh);
		Set<String> dirty = new LinkedHashSet<>();
		int added = 0;
		for (WafAlertParser.Hit h : fresh) {
			String key = eventKey(h);
			WafEvent e = byKey.get(key);
			if (e == null) {
				e = newEvent(h, key);
				merge(e, h);
				sqlHelper.insert(e);
				added++;
			} else {
				merge(e, h);
				dirty.add(key);
			}
			byKey.put(key, e);
			for (WafAlertParser.Rule r : h.rules()) {
				WafEventRule row = new WafEventRule();
				row.setEventId(e.getId());
				row.setAlertId(alertKey(h));
				row.setEventTime(e.getEventTime());
				row.setRuleName(r.name());
				row.setRuleSource(r.source());
				row.setCategory(r.category());
				row.setMessage(r.message());
				sqlHelper.insert(row);
			}
		}
		for (String key : dirty) {
			sqlHelper.updateById(byKey.get(key));
		}
		return added;
	}

	/** 這批命中對應的、已存在的事件（eventKey → 事件），分塊一次查出 */
	private Map<String, WafEvent> loadEvents(List<WafAlertParser.Hit> hits) {
		List<String> keys = hits.stream().map(WafEventService::eventKey).distinct().toList();
		Map<String, WafEvent> byKey = new LinkedHashMap<>();
		for (int i = 0; i < keys.size(); i += ID_CHUNK) {
			List<String> chunk = keys.subList(i, Math.min(i + ID_CHUNK, keys.size()));
			for (WafEvent e : sqlHelper.findListByQuery(new ConditionAndWrapper().in("eventKey", chunk), WafEvent.class)) {
				byKey.putIfAbsent(e.getEventKey(), e);
			}
		}
		return byKey;
	}

	private WafEvent newEvent(WafAlertParser.Hit h, String key) {
		WafEvent e = new WafEvent();
		e.setEventKey(key);
		e.setEventTime(h.time().toEpochMilli());
		e.setIp(h.ip());
		e.setIpKey(IpMatch.canonical(h.ip()));
		e.setMethod(h.method());
		e.setUri(h.uri());
		try {
			e.setCountry(countryOf.apply(h.ip()));
		} catch (RuntimeException ex) {
			e.setCountry(null);
		}
		return e;
	}

	/** 把一個 alert 的規則與欄位併進事件：有 in-band 命中就是 block（E-395 改規則處理方式時改這裡），其他保留原值或 log */
	static void merge(WafEvent e, WafAlertParser.Hit h) {
		Set<String> sources = new TreeSet<>(split(e.getRuleSource()));
		Set<String> categories = new LinkedHashSet<>(split(e.getCategories()));
		for (WafAlertParser.Rule r : h.rules()) {
			sources.add(r.source());
			categories.add(r.category());
		}
		e.setRuleSource(String.join(",", sources));
		e.setCategories("," + String.join(",", categories) + ",");
		if (h.inBand()) {
			e.setAction(ACTION_BLOCK);
		} else if (e.getAction() == null) {
			e.setAction(ACTION_LOG);
		}
		if (e.getHost() == null) {
			e.setHost(h.host());
		}
		if (e.getUserAgent() == null) {
			e.setUserAgent(h.userAgent());
		}
		if (StrUtil.isBlank(e.getMessage())) {
			e.setMessage(h.rules().stream().map(WafAlertParser.Rule::message).filter(StrUtil::isNotBlank).findFirst().orElse(null));
		}
	}

	private static List<String> split(String csv) {
		return csv == null ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	private Set<String> existingAlertIds(List<WafAlertParser.Hit> hits) {
		List<String> ids = hits.stream().map(WafEventService::alertKey).distinct().toList();
		Set<String> found = new HashSet<>();
		for (int i = 0; i < ids.size(); i += ID_CHUNK) {
			List<String> chunk = ids.subList(i, Math.min(i + ID_CHUNK, ids.size()));
			found.addAll(sqlHelper.findPropertiesByQuery(new ConditionAndWrapper().in("alertId", chunk), WafEventRule.class, "alertId"));
		}
		return found;
	}

	/**
	 * 查詢，新到舊。站台依 nginx 選 server 的規則判斷（與「最近被擋下的 IP」相同，{@link HttpServerSelector}）：
	 * 只留候選站台包含 serverId 的 host；IP 是單一位址時等值比對（IPv6 任何寫法都相同），否則比對部分字串。
	 */
	@SuppressWarnings("unchecked")
	public Result list(Query q, long now, List<Server> httpServers) {
		ConditionAndWrapper c = new ConditionAndWrapper();
		Long from = fromTime(q, now);
		if (from != null) {
			c.gte("eventTime", from);
		}
		if (StrUtil.isNotBlank(q.category())) {
			c.like("categories", "," + q.category().trim() + ",");
		}
		if (StrUtil.isNotBlank(q.action())) {
			c.eq("action", q.action().trim());
		}
		if (StrUtil.isNotBlank(q.ip())) {
			String key = IpMatch.canonical(q.ip());
			if (key != null) {
				c.eq("ipKey", key);
			} else {
				c.like("ip", q.ip().trim());
			}
		}
		if (StrUtil.isNotBlank(q.serverId())) {
			List<String> hosts = hostsOf(q.serverId().trim(), httpServers, from);
			if (hosts.isEmpty()) {
				return new Result(List.of(), 0);
			}
			if (hosts.size() <= ID_CHUNK) {
				c.in("host", hosts);
			} else {
				ConditionOrWrapper any = new ConditionOrWrapper();
				for (int i = 0; i < hosts.size(); i += ID_CHUNK) {
					any.or(new ConditionAndWrapper().in("host", hosts.subList(i, Math.min(i + ID_CHUNK, hosts.size()))));
				}
				c.and(any);
			}
		}
		Page<WafEvent> page = new Page<>();
		page.setCurr(Math.max(1, Math.min(q.page(), MAX_PAGE)));
		page.setLimit(Math.max(1, Math.min(q.limit(), MAX_LIMIT)));
		page = sqlHelper.findPage(c, newestFirst(), page, WafEvent.class);
		return new Result(withRules((List<WafEvent>) page.getRecords()), page.getCount());
	}

	/** 期間下限（epoch 毫秒）；days 為 null 或 0 以下表示不限，最多回溯 MAX_DAYS 天 */
	private static Long fromTime(Query q, long now) {
		return q.days() != null && q.days() > 0 ? now - Math.min(q.days(), MAX_DAYS) * DAY_MS : null;
	}

	/** 這個 IP 最近的事件；不是單一 IP 回空清單 */
	@SuppressWarnings("unchecked")
	public List<Row> recentForIp(String ip, int limit) {
		String key = IpMatch.canonical(ip);
		if (key == null) {
			return List.of();
		}
		Page<WafEvent> page = new Page<>();
		page.setCurr(1);
		page.setLimit(Math.max(1, Math.min(limit, MAX_LIMIT)));
		page = sqlHelper.findPage(new ConditionAndWrapper().eq("ipKey", key), newestFirst(), page, WafEvent.class);
		return withRules((List<WafEvent>) page.getRecords());
	}

	/** 刪掉 cutoff（epoch 毫秒）之前的事件與規則，回刪掉的事件數 */
	public long cleanup(long cutoff) {
		long n = sqlHelper.findCountByQuery(new ConditionAndWrapper().lt("eventTime", cutoff), WafEvent.class);
		sqlHelper.deleteByQuery(new ConditionAndWrapper().lt("eventTime", cutoff), WafEventRule.class);
		sqlHelper.deleteByQuery(new ConditionAndWrapper().lt("eventTime", cutoff), WafEvent.class);
		return n;
	}

	private static Sort newestFirst() {
		return new Sort("eventTime", Direction.DESC).add("id", Direction.DESC);
	}

	/** 資料表裡出現過（from 不為 null 時只看該時間之後）、會落到這個站台的 host */
	List<String> hostsOf(String serverId, List<Server> httpServers, Long from) {
		HttpServerSelector selector = new HttpServerSelector(httpServers);
		Set<String> hosts = new TreeSet<>();
		ConditionAndWrapper seen = new ConditionAndWrapper().isNotNull("host");
		if (from != null) {
			seen.gte("eventTime", from);
		}
		for (String h : sqlHelper.findPropertiesByQuery(seen, WafEvent.class, "host")) {
			if (StrUtil.isNotBlank(h)) {
				hosts.add(h);
			}
		}
		return hosts.stream().filter(h -> selector.candidates(h).contains(serverId)).toList();
	}

	private List<Row> withRules(List<WafEvent> events) {
		List<String> ids = events.stream().map(WafEvent::getId).filter(Objects::nonNull).toList();
		Map<String, List<WafEventRule>> byEvent = new LinkedHashMap<>();
		for (int i = 0; i < ids.size(); i += ID_CHUNK) {
			List<String> chunk = ids.subList(i, Math.min(i + ID_CHUNK, ids.size()));
			// 沒給排序時 SqlHelper 預設 id 倒序；規則要依存入順序列出
			for (WafEventRule r : sqlHelper.findListByQuery(new ConditionAndWrapper().in("eventId", chunk),
					new Sort("id", Direction.ASC), WafEventRule.class)) {
				byEvent.computeIfAbsent(r.getEventId(), k -> new ArrayList<>()).add(r);
			}
		}
		List<Row> rows = new ArrayList<>();
		for (WafEvent e : events) {
			rows.add(new Row(e, byEvent.getOrDefault(e.getId(), List.of())));
		}
		return rows;
	}
}
