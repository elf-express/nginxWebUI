package com.cym.service;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.ext.GeoipDbInfo;
import com.cym.ext.GeoipStatus;
import com.maxmind.db.Reader;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;

/**
 * GeoIP MMDB 資料庫：讀版本（maxmind-db build date）+ 手動下載（Hutool 抓 mirror）。
 *
 * 設計重點：
 * - 版本（build date）即時讀取 mmdb metadata（header-only，Reader 讀完即關），不快取；
 *   如此排程/手動更新後版本立即反映，無需清快取。
 * - 所有讀版本路徑容錯：檔案不存在 / 讀失敗一律回 null，不丟例外（header 每頁都載，不能卡）。
 * - 下載走 Java（Hutool），jar 與 Docker 都能用；先寫 .tmp 再 move，避免半截檔。
 */
@Component
public class GeoipService {
	Logger logger = LoggerFactory.getLogger(getClass());

	@Inject
	SettingService settingService;

	@Inject
	com.cym.utils.MessageUtils m;

	/** MMDB 目錄；預設與 nginx 設定一致（InitConfig 寫死 /etc/nginx/geoip/），可由系統屬性 geoip.dir 覆寫（dev/測試用）。 */
	public static final String GEOIP_DIR = System.getProperty("geoip.dir", "/etc/nginx/geoip/");

	/** 下載逾時（City 庫約 60MB，給寬一點）。 */
	private static final int DOWNLOAD_TIMEOUT_MS = 120_000;

	/** 一天毫秒數。 */
	public static final long DAY_MS = 24L * 60 * 60 * 1000;

	/** realip.conf 檔名;路徑一律 GEOIP_DIR + REALIP_CONF_NAME,不硬編第二處(spec 路徑單一來源)。 */
	public static final String REALIP_CONF_NAME = "realip.conf";

	/** 三個資料庫：{ key, 檔名, 下載 URL }（mirror 沿用 scripts/update-geoip-cf.sh 的 P3TERX）。 */
	private static final String[][] DBS = {
			{ "country", "GeoLite2-Country.mmdb", "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb" },
			{ "city", "GeoLite2-City.mmdb", "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb" },
			{ "asn", "GeoLite2-ASN.mmdb", "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-ASN.mmdb" },
	};

	/**
	 * 讀取排程時間 geoip.fetchTime，驗證 "HH:mm"（00:00-23:59）格式；
	 * 未設定或格式無效一律 fallback "03:00"，避免無效值讓排程靜默失效（code review I-1）。
	 */
	public String getFetchTime() {
		String t = settingService.get("geoip.fetchTime");
		if (t != null && t.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
			return t;
		}
		return "03:00";
	}

	/** 給 header 下拉與防護頁表格用:資料庫的版本 / 檔案 stat / 交叉驗證狀態 / 排程,尾端加 Cloudflare 列。 */
	public List<GeoipDbInfo> getDbInfos() {
		List<GeoipDbInfo> list = new ArrayList<>();
		String fetchTime = getFetchTime();
		long now = System.currentTimeMillis();

		for (String[] db : DBS) {
			String key = db[0];
			String fileName = db[1];
			File f = new File(GEOIP_DIR, fileName);

			GeoipDbInfo info = new GeoipDbInfo();
			info.setKey(key);
			info.setFileName(fileName);
			info.setDisplayName(displayName(key));
			info.setExists(f.exists());
			info.setFilePath(f.getAbsolutePath());
			info.setCloudflare(false);

			String buildDate = null;
			Long lastModifiedAt = null;
			if (f.exists()) {
				buildDate = readBuildDate(f); // 即時讀,不快取(治本:排程/cron 背景更新後版本立即反映)
				info.setVersion(buildDate);
				info.setSizeStr(FileUtil.readableFileSize(f.length()));
				lastModifiedAt = f.lastModified();
				info.setLastModifiedAt(lastModifiedAt);
				info.setLastModifiedStr(DateUtil.format(new Date(lastModifiedAt), "yyyy-MM-dd HH:mm"));
			}

			String updatedAt = settingService.get("geoip." + key + ".updatedAt");
			if (updatedAt != null) {
				try {
					long ts = Long.parseLong(updatedAt);
					info.setLastUpdateAt(ts);
					info.setLastUpdateStr(DateUtil.format(new Date(ts), "yyyy-MM-dd HH:mm"));
				} catch (NumberFormatException ignore) {
					// 髒資料忽略
				}
			}

			info.setScheduleStr("Daily " + fetchTime);
			info.setScheduleTime(fetchTime);

			if (f.exists()) {
				GeoipStatus st = evaluateStatus(lastModifiedAt, buildDate, now, false);
				info.setStatus(st.status());
				info.setStatusReasons(buildReasonTexts(st));
			}

			list.add(info);
		}

		// Cloudflare IP 清單列(realip.conf;只套規則①,無 mmdb build date)
		list.add(buildCloudflareInfo(now));

		return list;
	}

	private String displayName(String key) {
		if ("country".equals(key)) {
			return "Country";
		}
		if ("city".equals(key)) {
			return "City";
		}
		if ("asn".equals(key)) {
			return "ASN";
		}
		return key;
	}

	/** 組 Cloudflare 列:讀 realip.conf stat(路徑=GEOIP_DIR + REALIP_CONF_NAME,單一來源)。 */
	private GeoipDbInfo buildCloudflareInfo(long now) {
		File f = new File(GEOIP_DIR, REALIP_CONF_NAME);
		GeoipDbInfo info = new GeoipDbInfo();
		info.setKey("cloudflare");
		info.setFileName(REALIP_CONF_NAME);
		info.setDisplayName("Cloudflare IP");
		info.setExists(f.exists());
		info.setFilePath(f.getAbsolutePath());
		info.setCloudflare(true);
		info.setScheduleStr("Daily " + getFetchTime());
		info.setScheduleTime(getFetchTime());
		if (f.exists()) {
			info.setSizeStr(FileUtil.readableFileSize(f.length()));
			long lm = f.lastModified();
			info.setLastModifiedAt(lm);
			info.setLastModifiedStr(DateUtil.format(new Date(lm), "yyyy-MM-dd HH:mm"));
			GeoipStatus st = evaluateStatus(lm, null, now, true);
			info.setStatus(st.status());
			info.setStatusReasons(buildReasonTexts(st));
		}
		return info;
	}

	/** reason code → i18n 顯示文字(套 geoipStr.reason* 模板,{days} 代入天數)。 */
	private List<String> buildReasonTexts(GeoipStatus st) {
		List<String> texts = new ArrayList<>();
		for (GeoipStatus.Reason r : st.reasons()) {
			String tmpl;
			switch (r.code()) {
			case "fileStale":
				tmpl = m.get("geoipStr.reasonFileStale");
				break;
			case "buildStale":
				tmpl = m.get("geoipStr.reasonBuildStale");
				break;
			case "corrupt":
			default:
				tmpl = m.get("geoipStr.reasonCorrupt");
				break;
			}
			// i18n key 缺失時 MessageUtils.get 回 null(Properties.getProperty),fallback code 名避免 NPE
			if (tmpl == null) {
				tmpl = r.code();
			}
			texts.add(r.days() != null ? tmpl.replace("{days}", String.valueOf(r.days())) : tmpl);
		}
		return texts;
	}

	/**
	 * 用 maxmind-db 讀 MMDB metadata 的 build date → "yyyy.MM.dd"。
	 * 用 FileMode.MEMORY（非 MEMORY_MAPPED）避免 Windows 下記憶體映射鎖檔、影響後續覆蓋。
	 * 檔案不存在 / 讀失敗回 null（不丟例外）。
	 */
	public String readBuildDate(File f) {
		if (f == null || !f.exists()) {
			return null;
		}
		try (Reader reader = new Reader(f, Reader.FileMode.MEMORY)) {
			// maxmind-db 4.x: Metadata 改為 record,getBuildDate() 移除 → 改用 buildTime()(Instant)
			Instant buildTime = reader.getMetadata().buildTime();
			if (buildTime == null) {
				return null;
			}
			return DateUtil.format(Date.from(buildTime), "yyyy.MM.dd");
		} catch (Exception e) {
			logger.warn("讀取 MMDB 版本失敗 {}: {}", f.getName(), e.getMessage());
			return null;
		}
	}

	/**
	 * 交叉驗證判定(距今基準,純函式,不碰 IO/i18n)。
	 * 規則①(mmdb + Cloudflare 通用):檔案最後修改距今 > 7 天 → fileStale。
	 * 規則②(僅 mmdb):build date 距今 > 14 天 → buildStale。
	 * 規則③(僅 mmdb):build date 讀取失敗(null,檔案存在時呼叫)→ corrupt。
	 * 多規則同時觸發 → reasons 收集全部;無觸發 → status=ok。
	 *
	 * @param lastModifiedAt 檔案最後修改 epoch ms(null 代表檔案不存在,不套規則①)
	 * @param buildDate      mmdb 建置日期 "yyyy.MM.dd"(null=讀失敗;Cloudflare 列傳 null)
	 * @param now            現在 epoch ms(測試注入)
	 * @param isCloudflare   true=Cloudflare 列(只套規則①)
	 */
	public static GeoipStatus evaluateStatus(Long lastModifiedAt, String buildDate, long now, boolean isCloudflare) {
		List<GeoipStatus.Reason> reasons = new ArrayList<>();

		// 規則①:檔案最後修改距今 > 7 天(嚴格 >)
		if (lastModifiedAt != null) {
			long ageMs = now - lastModifiedAt;
			if (ageMs > 7L * DAY_MS) {
				reasons.add(new GeoipStatus.Reason("fileStale", (int) (ageMs / DAY_MS)));
			}
		}

		if (!isCloudflare) {
			if (buildDate != null) {
				// 規則②:build date 距今 > 14 天
				Long buildMs = parseBuildDate(buildDate);
				if (buildMs != null) {
					long ageMs = now - buildMs;
					if (ageMs > 14L * DAY_MS) {
						reasons.add(new GeoipStatus.Reason("buildStale", (int) (ageMs / DAY_MS)));
					}
				}
			} else {
				// 規則③:檔案存在但 build date 讀失敗
				reasons.add(new GeoipStatus.Reason("corrupt", null));
			}
		}

		return new GeoipStatus(reasons.isEmpty() ? "ok" : "warn", reasons);
	}

	/** "yyyy.MM.dd" → 當天 00:00 epoch ms;解析失敗回 null。 */
	private static Long parseBuildDate(String buildDate) {
		try {
			return DateUtil.parse(buildDate, "yyyy.MM.dd").getTime();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 手動下載：key = country / city / asn / all。
	 * 先寫 .tmp 再 move（原子性）；成功後記錄 updatedAt 並清版本快取。
	 * 回傳是否全部成功。
	 */
	public boolean download(String key) {
		List<String[]> targets = new ArrayList<>();
		for (String[] db : DBS) {
			if ("all".equalsIgnoreCase(key) || db[0].equalsIgnoreCase(key)) {
				targets.add(db);
			}
		}
		if (targets.isEmpty()) {
			return false;
		}

		File dir = new File(GEOIP_DIR);
		if (!dir.exists() && !dir.mkdirs()) {
			logger.error("GeoIP 目錄無法建立: {}", GEOIP_DIR);
			return false;
		}

		boolean allOk = true;
		for (String[] db : targets) {
			String dbKey = db[0];
			String fileName = db[1];
			String url = db[2];
			File tmp = new File(dir, fileName + "." + System.nanoTime() + ".tmp"); // 唯一檔名,避免手動與排程下載對同檔競爭(review Minor #1)
			File dest = new File(dir, fileName);
			try {
				long size = HttpUtil.downloadFile(url, tmp, DOWNLOAD_TIMEOUT_MS);
				if (size <= 0 || !tmp.exists()) {
					logger.error("下載 GeoIP {} 失敗：回傳大小 {}", fileName, size);
					FileUtil.del(tmp);
					allOk = false;
					continue;
				}
				FileUtil.move(tmp, dest, true);

				settingService.set("geoip." + dbKey + ".updatedAt", String.valueOf(System.currentTimeMillis()));
				logger.info("GeoIP {} 已更新（{}）", fileName, FileUtil.readableFileSize(size));
			} catch (Exception e) {
				logger.error("下載 GeoIP {} 失敗: {}", fileName, e.getMessage());
				FileUtil.del(tmp);
				allOk = false;
			}
		}
		return allOk;
	}

	private static final String CF_V4_URL = "https://www.cloudflare.com/ips-v4";
	private static final String CF_V6_URL = "https://www.cloudflare.com/ips-v6";
	/** 本機/內網信任來源(對齊 scripts/update-geoip-cf.sh 的 LOCAL_TRUST)。 */
	private static final String[] LOCAL_TRUST = { "127.0.0.1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16" };

	/**
	 * 手動更新 Cloudflare Real IP 清單:抓 ips-v4/v6 生成 realip.conf(jar 與 Docker 通用)。
	 * 格式對齊 update-geoip-cf.sh;先寫 .tmp 再 move(原子性)。回傳是否成功。
	 * 注意:僅產生檔案,不執行 nginx -s reload(交由既有排程 / 使用者手動 reload)。
	 */
	public boolean downloadCloudflare() {
		File dir = new File(GEOIP_DIR);
		if (!dir.exists() && !dir.mkdirs()) {
			logger.error("GeoIP 目錄無法建立: {}", GEOIP_DIR);
			return false;
		}
		try {
			String v4 = HttpUtil.get(CF_V4_URL, 30_000);
			String v6 = HttpUtil.get(CF_V6_URL, 30_000);
			if (StrUtil.isBlank(v4) || StrUtil.isBlank(v6)) {
				logger.error("Cloudflare IP 清單抓取為空(v4={}, v6={})", StrUtil.isBlank(v4), StrUtil.isBlank(v6));
				return false;
			}
			StringBuilder sb = new StringBuilder();
			sb.append("# Cloudflare Real IP - Updated by nginxWebUI ")
					.append(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")).append("\n\n# IPv4\n");
			for (String line : v4.split("\\r?\\n")) {
				if (StrUtil.isNotBlank(line)) {
					sb.append("set_real_ip_from ").append(line.trim()).append(";\n");
				}
			}
			sb.append("\n# IPv6\n");
			for (String line : v6.split("\\r?\\n")) {
				if (StrUtil.isNotBlank(line)) {
					sb.append("set_real_ip_from ").append(line.trim()).append(";\n");
				}
			}
			sb.append("\n# Local / Docker / Private Network Trust\n");
			for (String cidr : LOCAL_TRUST) {
				sb.append("set_real_ip_from ").append(cidr).append(";\n");
			}
			sb.append("\nreal_ip_header CF-Connecting-IP;\nreal_ip_recursive on;\n");

			File tmp = new File(dir, REALIP_CONF_NAME + "." + System.nanoTime() + ".tmp");
			File dest = new File(dir, REALIP_CONF_NAME);
			FileUtil.writeString(sb.toString(), tmp, java.nio.charset.StandardCharsets.UTF_8);
			FileUtil.move(tmp, dest, true);
			logger.info("Cloudflare Real IP 清單已更新: {}", dest.getAbsolutePath());
			return true;
		} catch (Exception e) {
			logger.error("Cloudflare Real IP 清單更新失敗: {}", e.getMessage());
			return false;
		}
	}
}
