package com.cym.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.DenyAllow;
import com.cym.sqlhelper.bean.Page;
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
