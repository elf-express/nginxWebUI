package com.cym.service;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.utils.IpMatch;
import com.maxmind.db.Reader;

/**
 * 查 IP 的國家與 ASN（GeoLite2 Country／ASN mmdb，與 nginx geoip2 同一份）。
 * Reader 依（檔名, 修改時間）快取並用 MEMORY_MAPPED，不把整個檔案讀進 heap；
 * 國家查詢分三種結果：資料庫不可用、沒有國家記錄、國家代碼（見 {@link Country}）。
 */
@Component
public class GeoLookup {
	private static final Logger logger = LoggerFactory.getLogger(GeoLookup.class);

	public record Asn(long number, String org) {
	}

	private record Cached(long mtime, Reader reader) {
	}

	String dir = GeoipService.GEOIP_DIR;
	private final Map<String, Cached> readers = new ConcurrentHashMap<>();

	/**
	 * 國家查詢結果。available 為 false 表示資料庫不存在或讀不了；
	 * available 為 true 而 code 為 null 表示資料庫有、但這個 IP 沒有國家記錄（nginx 的國家代碼變數為空字串）。
	 */
	public record Country(boolean available, String code) {
		public static Country unavailable() {
			return new Country(false, null);
		}

		public static Country none() {
			return new Country(true, null);
		}

		public static Country of(String code) {
			return new Country(true, code);
		}
	}

	/** 資料庫是否可用，以及查到的資料（可用但沒有記錄時 data 為 null） */
	private record Found(boolean available, Map<?, ?> data) {
	}

	private static final Found UNAVAILABLE = new Found(false, null);

	public Country country(String ip) {
		Found found = lookup("GeoLite2-Country.mmdb", ip);
		if (!found.available()) {
			return Country.unavailable();
		}
		Map<?, ?> rec = found.data();
		if (rec == null || !(rec.get("country") instanceof Map<?, ?> c) || c.get("iso_code") == null) {
			return Country.none();
		}
		return Country.of(String.valueOf(c.get("iso_code")));
	}

	public Optional<Asn> asn(String ip) {
		Map<?, ?> rec = lookup("GeoLite2-ASN.mmdb", ip).data();
		if (rec == null || !(rec.get("autonomous_system_number") instanceof Number n)) {
			return Optional.empty();
		}
		Object org = rec.get("autonomous_system_organization");
		return Optional.of(new Asn(n.longValue(), org == null ? "" : String.valueOf(org)));
	}

	private Found lookup(String file, String ip) {
		if (!IpMatch.isSingleIp(ip)) {
			return UNAVAILABLE;
		}
		File f = new File(dir, file);
		if (!f.isFile()) {
			return UNAVAILABLE;
		}
		try {
			Cached c = readers.compute(file, (k, old) -> {
				if (old != null && old.mtime == f.lastModified()) {
					return old;
				}
				closeQuietly(old);
				try {
					return new Cached(f.lastModified(), new Reader(f, Reader.FileMode.MEMORY_MAPPED));
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			return new Found(true, c.reader.get(InetAddress.getByName(ip.trim()), Map.class));
		} catch (Exception e) {
			logger.warn("geo lookup failed for {} in {}: {}", ip, file, e.getMessage());
			return UNAVAILABLE;
		}
	}

	/** 關閉並清掉所有快取的 Reader */
	public void close() {
		readers.values().forEach(GeoLookup::closeQuietly);
		readers.clear();
	}

	private static void closeQuietly(Cached c) {
		if (c != null) {
			try {
				c.reader.close();
			} catch (Exception ignored) {
				// 舊 reader 關閉失敗不影響查詢
			}
		}
	}
}
