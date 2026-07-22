package com.cym.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.DenyAllow;
import com.cym.model.Server;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

@Component
public class DenyAllowService {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Inject
	SqlHelper sqlHelper;

	public Page search(Page page) {
		page = sqlHelper.findPage(page, DenyAllow.class);

		return page;
	}

	/**
	 * 檢查 csv 字串（"id1,id2,id3"）內是否包含目標 id。供 server.denyId / allowId 多選 CSV
	 * 後、controller 計算 usedBy 與 conf 生成端比對使用。
	 */
	public static boolean csvContainsId(String csv, String id) {
		if (csv == null || csv.isEmpty() || id == null) {
			return false;
		}
		for (String s : csv.split(",")) {
			if (id.equals(s.trim())) {
				return true;
			}
		}
		return false;
	}

	/** 依 type 過濾分頁(type 空 → 全部)。 */
	public Page searchByType(Page page, String type) {
		if (StrUtil.isBlank(type)) {
			return sqlHelper.findPage(page, DenyAllow.class);
		}
		return sqlHelper.findPage(new ConditionAndWrapper().eq("type", type), page, DenyAllow.class);
	}

	/**
	 * 預設惡意 IP 黑名單(seed-on-empty:InitConfig 首次啟動播種,使用者可自行刪改)。
	 * ip 先空,播種後由 InitConfig 非同步首抓 + ScheduleTask 每日排程更新;
	 * fetchTime 錯開避免同一分鐘齊發。來源皆為純文字 IP/CIDR 清單,parser(fetchAndUpdate)可解析。
	 */
	public static List<DenyAllow> defaultBlocklistRules() {
		String[][] defs = {
				{ "Spamhaus DROP", "https://www.spamhaus.org/drop/drop.txt", "03:10" },
				{ "Blocklist.de All", "https://lists.blocklist.de/lists/all.txt", "03:20" },
				{ "ET Compromised IPs", "https://rules.emergingthreats.net/blockrules/compromised-ips.txt", "03:30" },
				{ "CINS Army Bad Guys", "https://cinsscore.com/list/ci-badguys.txt", "03:40" },
				{ "Feodo Tracker Botnet C2", "https://feodotracker.abuse.ch/downloads/ipblocklist.txt", "03:50" },
				{ "GreenSnow", "https://blocklist.greensnow.co/greensnow.txt", "04:00" },
		};
		List<DenyAllow> rules = new ArrayList<>();
		for (String[] d : defs) {
			DenyAllow da = new DenyAllow();
			da.setName(d[0]);
			da.setSourceUrl(d[1]);
			da.setFetchTime(d[2]);
			da.setType("deny");
			rules.add(da);
		}
		return rules;
	}

	/** 依 type 取全部(非分頁),供引用端下拉。 */
	public List<DenyAllow> listByType(String type) {
		return sqlHelper.findListByQuery(new ConditionAndWrapper().eq("type", type), DenyAllow.class);
	}

	/**
	 * 反查引用決定 type(純函式):被任一 allowId(server / http global / stream global)引用且未被 denyId 引用 → allow;
	 * 否則(含被 denyId 引用、矛盾同時被兩者引用、未被引用)→ deny。
	 */
	public static String resolveTypeByReference(String daId, List<Server> servers,
			String httpDenyId, String httpAllowId, String streamDenyId, String streamAllowId) {
		boolean referencedByDeny = csvContainsId(httpDenyId, daId) || csvContainsId(streamDenyId, daId);
		boolean referencedByAllow = csvContainsId(httpAllowId, daId) || csvContainsId(streamAllowId, daId);
		if (servers != null) {
			for (Server s : servers) {
				if (csvContainsId(s.getDenyId(), daId)) {
					referencedByDeny = true;
				}
				if (csvContainsId(s.getAllowId(), daId)) {
					referencedByAllow = true;
				}
			}
		}
		if (referencedByAllow && !referencedByDeny) {
			return "allow";
		}
		return "deny";
	}

	/**
	 * 存檔前黑白衝突檢查:回傳此名單中「已存在於另一 type 名單」的 IP 清單(空=無衝突)。
	 * type=deny 時查所有 allow 名單、反之亦然;排除自己(同 id)。
	 */
	public List<String> findConflictIps(DenyAllow da, String type) {
		List<String> conflicts = new java.util.ArrayList<>();
		if (da == null || StrUtil.isBlank(da.getIp()) || StrUtil.isBlank(type)) {
			return conflicts;
		}
		String otherType = "deny".equals(type) ? "allow" : "deny";
		List<DenyAllow> others = sqlHelper.findListByQuery(new ConditionAndWrapper().eq("type", otherType), DenyAllow.class);
		LinkedHashSet<String> otherIps = new LinkedHashSet<>();
		for (DenyAllow o : others) {
			if (da.getId() != null && da.getId().equals(o.getId())) {
				continue;
			}
			if (StrUtil.isNotBlank(o.getIp())) {
				for (String ip : o.getIp().split("\n")) {
					otherIps.add(ip.trim());
				}
			}
		}
		for (String ip : da.getIp().split("\n")) {
			if (otherIps.contains(ip.trim())) {
				conflicts.add(ip.trim());
			}
		}
		return conflicts;
	}

	public void removeSame(DenyAllow denyAllow) {
		if (StrUtil.isNotEmpty(denyAllow.getIp())) {
			LinkedHashSet<String> set = new LinkedHashSet<String>();

			String[] ips = denyAllow.getIp().split("\n");

			for (String ip : ips) {
				set.add(ip.trim());
			}

			denyAllow.setIp(StrUtil.join("\n", set));
		}
	}

	/**
	 * 從 da.sourceUrl 抓黑名單清單、parse IP、覆寫 da.ip 與 da.lastFetchAt。
	 * 不執行 DB write — 呼叫端決定何時 insertOrUpdate / updateById。
	 *
	 * 由 DenyAllowController.addOver()（即時抓）與 ScheduleTask.fetchDenyAllowLists()
	 * （每日定時抓）共用。
	 *
	 * @return 抓取成功且 ip 已更新時回 true；URL 空、HTTP 失敗、或解析後 0 個 IP 時回 false
	 */
	public boolean fetchAndUpdate(DenyAllow da) {
		if (da == null || StrUtil.isBlank(da.getSourceUrl())) {
			return false;
		}
		try {
			// 用 HttpRequest 顯式跟 5 次 redirect，並帶 UA（部分 CDN / Cloudflare 對空 UA 或 hutool 預設 UA 會拒絕）
			HttpResponse response = HttpRequest.get(da.getSourceUrl())
					.setMaxRedirectCount(5)
					.timeout(30000)
					.header("User-Agent", "nginxWebUI/DenyAllow-fetcher")
					.execute();
			if (!response.isOk()) {
				logger.warn("DenyAllow fetch HTTP {}: {} ({})", response.getStatus(), da.getName(), da.getSourceUrl());
				return false;
			}
			String body = response.body();
			if (StrUtil.isBlank(body)) {
				logger.warn("DenyAllow fetch returned empty body: {} ({})", da.getName(), da.getSourceUrl());
				return false;
			}

			// 用 LinkedHashSet 同步去重、保留首次出現的順序
			LinkedHashSet<String> ips = new LinkedHashSet<>();
			int rawCount = 0;
			for (String line : body.split("\r?\n")) {
				String s = line.trim();
				if (s.isEmpty() || s.startsWith("#") || s.startsWith(";")) {
					continue;
				}
				int spaceIdx = s.indexOf(' ');
				if (spaceIdx > 0) {
					s = s.substring(0, spaceIdx);
				}
				int tabIdx = s.indexOf('\t');
				if (tabIdx > 0) {
					s = s.substring(0, tabIdx);
				}
				if (s.matches("^[0-9a-fA-F:.\\/]+$") || s.equalsIgnoreCase("all")) {
					rawCount++;
					ips.add(s);
				}
			}

			if (ips.isEmpty()) {
				logger.warn("DenyAllow fetch parsed 0 IPs: {} ({})", da.getName(), da.getSourceUrl());
				return false;
			}

			da.setIp(String.join("\n", ips));
			da.setLastFetchAt(System.currentTimeMillis());
			int dupes = rawCount - ips.size();
			if (dupes > 0) {
				logger.info("Fetched DenyAllow list '{}' from {} → {} IPs ({} duplicates removed)",
						da.getName(), da.getSourceUrl(), ips.size(), dupes);
			} else {
				logger.info("Fetched DenyAllow list '{}' from {} → {} IPs", da.getName(), da.getSourceUrl(), ips.size());
			}
			return true;
		} catch (Exception e) {
			logger.error("Failed to fetch DenyAllow list " + da.getName() + " (" + da.getSourceUrl() + ")", e);
			return false;
		}
	}

}
