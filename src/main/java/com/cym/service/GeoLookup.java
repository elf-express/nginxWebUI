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
 * Reader 依（檔名, 修改時間）快取並用 MEMORY_MAPPED，不把整個檔案讀進 heap；檔案不存在回 empty。
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

	public Optional<String> country(String ip) {
		Map<?, ?> rec = lookup("GeoLite2-Country.mmdb", ip);
		if (rec == null || !(rec.get("country") instanceof Map<?, ?> c) || c.get("iso_code") == null) {
			return Optional.empty();
		}
		return Optional.of(String.valueOf(c.get("iso_code")));
	}

	public Optional<Asn> asn(String ip) {
		Map<?, ?> rec = lookup("GeoLite2-ASN.mmdb", ip);
		if (rec == null || !(rec.get("autonomous_system_number") instanceof Number n)) {
			return Optional.empty();
		}
		Object org = rec.get("autonomous_system_organization");
		return Optional.of(new Asn(n.longValue(), org == null ? "" : String.valueOf(org)));
	}

	private Map<?, ?> lookup(String file, String ip) {
		if (!IpMatch.isSingleIp(ip)) {
			return null;
		}
		File f = new File(dir, file);
		if (!f.isFile()) {
			return null;
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
			return c.reader.get(InetAddress.getByName(ip.trim()), Map.class);
		} catch (Exception e) {
			logger.warn("geo lookup failed for {} in {}: {}", ip, file, e.getMessage());
			return null;
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
