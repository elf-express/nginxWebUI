package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 用手工組出的最小 mmdb（單一節點、所有 IPv4 指向同一筆資料）驗證 maxmind-db 4.2.0 能把記錄解成 Map。 */
class GeoLookupTest {
	/** 放 target/ 下，mvn clean 會清掉 */
	private final Path tmp = Path.of("target", "geo-lookup-test", String.valueOf(System.nanoTime()));

	@BeforeEach
	void createDir() throws Exception {
		Files.createDirectories(tmp);
	}

	@Test
	void missingFilesGiveEmpty() {
		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		assertFalse(geo.country("203.0.113.5").available());
		assertNull(geo.country("203.0.113.5").code());
		assertTrue(geo.asn("203.0.113.5").isEmpty());
	}

	@Test
	void databaseWithoutCountryRecordIsAvailableButHasNoCode() throws Exception {
		// 資料庫存在，但這個 IP 沒有記錄（搜尋樹指向 node_count）：nginx 的 $geoip2_data_country_code 是空字串
		write("GeoLite2-Country.mmdb", new byte[0], 1);
		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		try {
			GeoLookup.Country c = geo.country("203.0.113.5");
			assertTrue(c.available());
			assertNull(c.code());
		} finally {
			geo.close();
		}
	}

	@Test
	void recordWithoutCountryKeyIsAvailableButHasNoCode() throws Exception {
		ByteArrayOutputStream country = new ByteArrayOutputStream();
		map(country, 1);
		str(country, "traits");
		str(country, "none");
		write("GeoLite2-Country.mmdb", country.toByteArray());
		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		try {
			GeoLookup.Country c = geo.country("203.0.113.5");
			assertTrue(c.available());
			assertNull(c.code());
		} finally {
			geo.close();
		}
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
			assertTrue(geo.country("203.0.113.5").available());
			assertEquals("TW", geo.country("203.0.113.5").code());
			GeoLookup.Asn a = geo.asn("203.0.113.5").orElseThrow();
			assertEquals(4134L, a.number());
			assertEquals("Chinanet", a.org());
			assertFalse(geo.country("not-an-ip").available());
		} finally {
			geo.close(); // Windows 上對映中的檔案無法刪除
		}
	}

	private void writeCountry(String code) throws Exception {
		ByteArrayOutputStream country = new ByteArrayOutputStream();
		map(country, 1);
		str(country, "country");
		map(country, 1);
		str(country, "iso_code");
		str(country, code);
		write("GeoLite2-Country.mmdb", country.toByteArray());
	}

	@Test
	void databaseCanBeDeletedAndReplacedAfterLookup() throws Exception {
		// GeoipService.download() 會換掉 mmdb：查詢過後檔案不能被鎖住（Windows 上 memory map 的檔案無法刪除或覆寫）
		writeCountry("TW");
		Path file = tmp.resolve("GeoLite2-Country.mmdb");
		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		try {
			assertEquals("TW", geo.country("203.0.113.5").code());
			Files.delete(file);
			writeCountry("JP");
			file.toFile().setLastModified(file.toFile().lastModified() + 10_000);
			assertEquals("JP", geo.country("203.0.113.5").code());
		} finally {
			geo.close();
		}
	}

	@Test
	void readerIsReusedUntilModifiedTimeChanges() throws Exception {
		writeCountry("TW");
		File file = tmp.resolve("GeoLite2-Country.mmdb").toFile();
		long mtime = file.lastModified();
		GeoLookup geo = new GeoLookup();
		geo.dir = tmp.toString();
		try {
			assertEquals("TW", geo.country("203.0.113.5").code());
			// 內容換了但修改時間相同：沿用快取的 Reader，不重讀
			writeCountry("JP");
			assertTrue(file.setLastModified(mtime));
			assertEquals("TW", geo.country("203.0.113.5").code());
			// 修改時間變了：重讀
			assertTrue(file.setLastModified(mtime + 10_000));
			assertEquals("JP", geo.country("203.0.113.5").code());
		} finally {
			geo.close();
		}
	}

	private void write(String name, byte[] data) throws Exception {
		write(name, data, 17);
	}

	/** record 為搜尋樹左右節點的值：17 指向資料區第 0 筆，1（= node_count）表示這段 IP 沒有記錄 */
	private void write(String name, byte[] data, int record) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// 搜尋樹：1 個節點、record 24 bit，左右都指向資料區第 0 筆（值 = node_count + 16 + 0）
		out.write(new byte[] { 0, 0, (byte) record, 0, 0, (byte) record });
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
