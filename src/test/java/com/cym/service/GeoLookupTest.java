package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 用手工組出的最小 mmdb（單一節點、所有 IPv4 指向同一筆資料）驗證 maxmind-db 4.2.0 能把記錄解成 Map。 */
class GeoLookupTest {
	/** 放 target/ 下而不用 @TempDir：Windows 上被 memory map 的檔案要等 GC 才能刪，@TempDir 的清理會失敗；mvn clean 會清掉 */
	private final Path tmp = Path.of("target", "geo-lookup-test", String.valueOf(System.nanoTime()));

	@BeforeEach
	void createDir() throws Exception {
		Files.createDirectories(tmp);
	}

	@Test
	void missingFilesGiveEmpty() {
		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		assertTrue(geo.country("203.0.113.5").isEmpty());
		assertTrue(geo.asn("203.0.113.5").isEmpty());
	}

	@Test
	void readsCountryAndAsn() throws Exception {
		ByteArrayOutputStream country = new ByteArrayOutputStream();
		map(country, 1);
		str(country, "country");
		map(country, 1);
		str(country, "iso_code");
		str(country, "TW");
		write("GeoLite2-Country.mmdb", country.toByteArray());

		ByteArrayOutputStream asn = new ByteArrayOutputStream();
		map(asn, 2);
		str(asn, "autonomous_system_number");
		asn.write(new byte[] { (byte) 0xC2, 0x10, 0x26 }); // uint32 4134
		str(asn, "autonomous_system_organization");
		str(asn, "Chinanet");
		write("GeoLite2-ASN.mmdb", asn.toByteArray());

		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		try {
			assertEquals("TW", geo.country("203.0.113.5").orElseThrow());
			GeoLookup.Asn a = geo.asn("203.0.113.5").orElseThrow();
			assertEquals(4134L, a.number());
			assertEquals("Chinanet", a.org());
			assertTrue(geo.country("not-an-ip").isEmpty());
		} finally {
			geo.close(); // Windows 上對映中的檔案無法刪除
		}
	}

	private void write(String name, byte[] data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// 搜尋樹：1 個節點、record 24 bit，左右都指向資料區第 0 筆（值 = node_count + 16 + 0）
		out.write(new byte[] { 0, 0, 17, 0, 0, 17 });
		out.write(new byte[16]);
		out.write(data);
		out.write(new byte[] { (byte) 0xAB, (byte) 0xCD, (byte) 0xEF });
		out.write("MaxMind.com".getBytes(StandardCharsets.US_ASCII));
		map(out, 8);
		str(out, "node_count");
		out.write(new byte[] { (byte) 0xC1, 1 });
		str(out, "record_size");
		out.write(new byte[] { (byte) 0xA1, 24 });
		str(out, "ip_version");
		out.write(new byte[] { (byte) 0xA1, 4 });
		str(out, "database_type");
		str(out, "Test");
		str(out, "languages");
		out.write(new byte[] { 0x00, 0x04 }); // 空陣列
		str(out, "binary_format_major_version");
		out.write(new byte[] { (byte) 0xA1, 2 });
		str(out, "binary_format_minor_version");
		out.write(new byte[] { (byte) 0xA0 });
		str(out, "build_epoch");
		out.write(new byte[] { 0x04, 0x02, 0x65, 0x00, 0x00, 0x00 }); // uint64
		Files.write(tmp.resolve(name), out.toByteArray());
	}

	private static void map(ByteArrayOutputStream out, int size) {
		out.write(0xE0 | size);
	}

	private static void str(ByteArrayOutputStream out, String s) throws Exception {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		if (b.length < 29) {
			out.write(0x40 | b.length);
		} else {
			out.write(0x40 | 29);
			out.write(b.length - 29);
		}
		out.write(b);
	}
}
