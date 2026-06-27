package com.cym.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.ext.GeoipDbInfo;
import com.maxmind.db.Reader;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;

/**
 * GeoIP MMDB 資料庫：讀版本（maxmind-db build date）+ 手動下載（Hutool 抓 mirror）。
 *
 * 設計重點：
 * - 版本（build date）走記憶體快取，避免 header 每個請求都讀檔。
 * - 所有讀版本路徑容錯：檔案不存在 / 讀失敗一律回 null，不丟例外（header 每頁都載，不能卡）。
 * - 下載走 Java（Hutool），jar 與 Docker 都能用；先寫 .tmp 再 move，避免半截檔。
 */
@Component
public class GeoipService {
	Logger logger = LoggerFactory.getLogger(getClass());

	@Inject
	SettingService settingService;

	/** MMDB 目錄；預設與 nginx 設定一致（InitConfig 寫死 /etc/nginx/geoip/），可由系統屬性 geoip.dir 覆寫（dev/測試用）。 */
	public static final String GEOIP_DIR = System.getProperty("geoip.dir", "/etc/nginx/geoip/");

	/** 下載逾時（City 庫約 60MB，給寬一點）。 */
	private static final int DOWNLOAD_TIMEOUT_MS = 120_000;

	/** 三個資料庫：{ key, 檔名, 下載 URL }（mirror 沿用 scripts/update-geoip-cf.sh 的 P3TERX）。 */
	private static final String[][] DBS = {
			{ "country", "GeoLite2-Country.mmdb", "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb" },
			{ "city", "GeoLite2-City.mmdb", "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb" },
			{ "asn", "GeoLite2-ASN.mmdb", "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-ASN.mmdb" },
	};

	/** version(build date) 記憶體快取：key -> "yyyy.MM.dd"。下載後清掉重讀。 */
	private final Map<String, String> versionCache = new ConcurrentHashMap<>();

	/** 給 header 下拉與防護頁表格用：三個資料庫的版本 / 上次更新 / 排程。 */
	public List<GeoipDbInfo> getDbInfos() {
		List<GeoipDbInfo> list = new ArrayList<>();
		// 排程時間由 geoip.fetchTime 設定（預設 03:00），與 ScheduleTask.fetchGeoip() 一致
		String fetchTime = settingService.get("geoip.fetchTime");
		if (fetchTime == null || fetchTime.isEmpty()) {
			fetchTime = "03:00";
		}
		for (String[] db : DBS) {
			String key = db[0];
			String fileName = db[1];
			File f = new File(GEOIP_DIR, fileName);

			GeoipDbInfo info = new GeoipDbInfo();
			info.setKey(key);
			info.setFileName(fileName);
			info.setDisplayName(displayName(key));
			info.setExists(f.exists());

			if (f.exists()) {
				info.setVersion(getVersionCached(key, f));
				info.setSizeStr(FileUtil.readableFileSize(f.length()));
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

			// 排程由 Java @Scheduled（ScheduleTask.fetchGeoip）每日於 geoip.fetchTime 執行（JAR/Docker 通用）。前端表格實際顯示 i18n geoipStr.scheduleValue。
			info.setScheduleStr("Daily " + fetchTime);

			list.add(info);
		}
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

	private String getVersionCached(String key, File f) {
		String v = versionCache.get(key);
		if (v == null) {
			v = readBuildDate(f);
			if (v != null) {
				versionCache.put(key, v);
			}
		}
		return v;
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
			Date buildDate = reader.getMetadata().getBuildDate();
			if (buildDate == null) {
				return null;
			}
			return DateUtil.format(buildDate, "yyyy.MM.dd");
		} catch (Exception e) {
			logger.warn("讀取 MMDB 版本失敗 {}: {}", f.getName(), e.getMessage());
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
			File tmp = new File(dir, fileName + ".tmp");
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
				versionCache.remove(dbKey);
				logger.info("GeoIP {} 已更新（{}）", fileName, FileUtil.readableFileSize(size));
			} catch (Exception e) {
				logger.error("下載 GeoIP {} 失敗: {}", fileName, e.getMessage());
				FileUtil.del(tmp);
				allOk = false;
			}
		}
		return allOk;
	}
}
