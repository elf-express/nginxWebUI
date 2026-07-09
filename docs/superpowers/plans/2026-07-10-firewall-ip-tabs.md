# 防火牆管理 — IP 資料庫 tab 重構 + GeoIP 交叉驗證 + 黑白名單拆分 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「防護與憑證」頁的 4 tab 改成 6 tab(IP資料庫 | 黑名單 | 白名單 | 國家存取控制 | ASN封鎖 | 證書管理),GeoIP 表格加檔案 stat + 交叉驗證 + Cloudflare 列,DenyAllow 加黑/白類型。

**Architecture:** 純讀取 + 加欄位 + UI 重排,不碰 nginx.conf 生成(`ConfService.buildDenyAllow` 完全不動)。判定邏輯抽成純函式便於單元測試;黑白類型用 DenyAllow 新 `type` 欄,migration 反查 server 引用自動歸類。

**Tech Stack:** Java 17 + Solon 3.10.7(非 Spring)、Freemarker + Layui + jQuery、SqlHelper 自研 ORM、maxmind-db 4.1.0、solon-test(JUnit 5)、Playwright E2E。

## Global Constraints

- 每個新使用者可見字串必須同步三份 `messages*.properties`(簡 `messages.properties` / 繁 `messages_zh_TW.properties` / 英 `messages_en_US.properties`);CJK 值用 `\uXXXX` escape(檔案是 ISO-8859-1)。
- i18n key 慣例 `<page>Str.<field>`(如 `geoipStr.status`);JS 全域(如 `geoipStr`)由 common.html 從 messageHeaders 自動產生。
- 主鍵一律 `SnowFlakeUtils.getId()`(String 存、Long 生);新增可不指定 ID 交給 `insertOrUpdate`。
- 不得引入 `<a href="javascript:...">` 偽連結;動作用 `<button type="button">`,icon-only 控件要 `aria-label`。
- 前端第三方 lib 一律 vendor 到 `static/lib/`,不可外網 CDN。
- 不碰 `ConfService.buildDenyAllow`(nginx.conf 黑白名單生成)。denyId=黑名單、allowId=白名單 的引用語意不變。
- `GEOIP_DIR = System.getProperty("geoip.dir", "/etc/nginx/geoip/")`(GeoipService.java:39)已是路徑單一來源;realip.conf 路徑用 `GEOIP_DIR + REALIP_CONF_NAME`,不硬編第二處。
- 測試前需 `mvn package`(E2E helpers 動態解析 `target/nginxWebUI-*.jar`);PATH 的 java 必須是 17。
- 每個 task 完成即 commit(worktree 內,controller/主 session 直接 commit,不委派 subagent commit)。

---

## Baseline(動工前一次性,不 commit)

- [ ] **B1: 確認 baseline build 綠**

Run: `cd /e/nginxWebUI/.claude/worktrees/firewall-ip-tabs && mvn clean package -DskipTests`
Expected: `BUILD SUCCESS`,產出 `target/nginxWebUI-5.2.5.jar`。若紅,先修環境(Java 17)再開工。

- [ ] **B2: 確認既有 GeoIP E2E 綠(回歸基準)**

Run: `npx playwright test tests/e2e/23-geoip-version.spec.js --config=playwright.config.js`
Expected: PASS。記錄結果作為改動後的回歸對照。

---

## Phase A — 後端資料層(model / DTO / 純函式)

### Task 1: DenyAllow 加 type 欄位

**Files:**
- Modify: `src/main/java/com/cym/model/DenyAllow.java`

**Interfaces:**
- Produces: `DenyAllow.getType()` / `setType(String)` — 值 `"deny"`(黑名單)或 `"allow"`(白名單),`@InitValue("deny")`。

- [ ] **Step 1: 加 type 欄位 + getter/setter**

在 `lastFetchAt` 欄位後(line 33 之後)加:

```java
	/**
	 * 名單類型：deny=黑名單、allow=白名單。舊資料(null)由 InitConfig migration 反查引用歸類。
	 */
	@InitValue("deny")
	String type;
```

在 `setLastFetchAt` 後(line 73 之後)加:

```java
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
```

`@InitValue` 已由現有 import(`com.cym.sqlhelper.config.InitValue`,line 4)涵蓋,無需新 import。

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS(SqlHelper 啟動時會 auto-add 缺欄,不需手寫 DDL)。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/model/DenyAllow.java
git commit -m "feat(denyAllow): add type column (deny/allow) with @InitValue"
```

---

### Task 2: GeoIP 狀態判定純函式 + 單元測試(核心)

**Files:**
- Create: `src/main/java/com/cym/ext/GeoipStatus.java`
- Modify: `src/main/java/com/cym/service/GeoipService.java`(加 static `evaluateStatus` + 常數)
- Create: `src/test/java/com/cym/service/GeoipStatusTest.java`

**Interfaces:**
- Produces:
  - `GeoipStatus`(record):`status()`(`"ok"`|`"warn"`)、`reasons()`(`List<GeoipStatus.Reason>`)。
  - `GeoipStatus.Reason`(record):`code()`(`"fileStale"`|`"buildStale"`|`"corrupt"`)、`days()`(`Integer`,corrupt 為 null)。
  - `GeoipService.evaluateStatus(Long lastModifiedAt, String buildDate, long now, boolean isCloudflare)` → `GeoipStatus`。純函式,不碰 IO/i18n。
  - `GeoipService.DAY_MS`(long 常數)。

- [ ] **Step 1: 寫 GeoipStatus DTO(record)**

Create `src/main/java/com/cym/ext/GeoipStatus.java`:

```java
package com.cym.ext;

import java.util.List;

/**
 * GeoIP/IP 資料庫交叉驗證結果(view DTO)。
 * evaluateStatus 純函式回傳 reason code + days,由 GeoipService 再套 i18n 模板成顯示文字。
 */
public record GeoipStatus(String status, List<Reason> reasons) {
	/** code: fileStale(檔案最後修改距今 > 7 天) / buildStale(建置日期距今 > 14 天) / corrupt(讀版本失敗)。days: corrupt 為 null。 */
	public record Reason(String code, Integer days) {
	}
}
```

- [ ] **Step 2: 寫失敗的單元測試**

Create `src/test/java/com/cym/service/GeoipStatusTest.java`:

```java
package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.ext.GeoipStatus;

/**
 * 交叉驗證判定(距今基準,三條規則)邊界值測試。now 固定注入,不碰 IO/i18n。
 * 規則①(mmdb+Cloudflare 通用):檔案最後修改距今 > 7 天 → warn/fileStale
 * 規則②(僅 mmdb):build date 距今 > 14 天 → warn/buildStale
 * 規則③(僅 mmdb):build date null 且檔案存在 → warn/corrupt
 */
public class GeoipStatusTest {

	private static final long DAY = 24L * 60 * 60 * 1000;
	// 固定「現在」為某 epoch(2026-07-10 00:00 附近的任意值),避免依賴系統時鐘
	private static final long NOW = 1_800_000_000_000L;

	private static String at(long daysAgo) {
		// 回傳 daysAgo 天前的 "yyyy.MM.dd" — 用 GeoipService 相同格式
		return cn.hutool.core.date.DateUtil.format(new java.util.Date(NOW - daysAgo * DAY), "yyyy.MM.dd");
	}

	@Test
	void file6Days_ok() {
		// 檔案最後修改 6 天前、build 剛更新 → 全部 ok
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 6 * DAY, at(0), NOW, false);
		assertEquals("ok", s.status());
		assertTrue(s.reasons().isEmpty());
	}

	@Test
	void file7DaysExact_ok() {
		// 剛好 7 天(> 嚴格,不觸發)
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 7 * DAY, at(0), NOW, false);
		assertEquals("ok", s.status());
	}

	@Test
	void file8Days_fileStale() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 8 * DAY, at(0), NOW, false);
		assertEquals("warn", s.status());
		assertEquals(1, s.reasons().size());
		assertEquals("fileStale", s.reasons().get(0).code());
		assertEquals(8, s.reasons().get(0).days());
	}

	@Test
	void build13Days_ok() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW, at(13), NOW, false);
		assertEquals("ok", s.status());
	}

	@Test
	void build15Days_buildStale() {
		GeoipStatus s = GeoipService.evaluateStatus(NOW, at(15), NOW, false);
		assertEquals("warn", s.status());
		assertEquals("buildStale", s.reasons().get(0).code());
		assertEquals(15, s.reasons().get(0).days());
	}

	@Test
	void buildNullFileFresh_corrupt() {
		// build date 讀失敗(null)但檔案新 → 只觸發規則③ corrupt
		GeoipStatus s = GeoipService.evaluateStatus(NOW, null, NOW, false);
		assertEquals("warn", s.status());
		assertEquals(1, s.reasons().size());
		assertEquals("corrupt", s.reasons().get(0).code());
	}

	@Test
	void bothStale_twoReasons() {
		// 檔案 9 天 + build 20 天 → 規則①②同時觸發,收集兩條
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 9 * DAY, at(20), NOW, false);
		assertEquals("warn", s.status());
		List<GeoipStatus.Reason> r = s.reasons();
		assertEquals(2, r.size());
		assertEquals("fileStale", r.get(0).code());
		assertEquals("buildStale", r.get(1).code());
	}

	@Test
	void cloudflare_onlyRule1() {
		// Cloudflare 列(isCloudflare=true):無 build date,只套規則①;檔案 10 天 → fileStale
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 10 * DAY, null, NOW, true);
		assertEquals("warn", s.status());
		assertEquals(1, s.reasons().size());
		assertEquals("fileStale", s.reasons().get(0).code());
	}

	@Test
	void cloudflareFresh_ok() {
		// Cloudflare 列檔案新(3 天)→ ok,不因無 build date 觸發規則③
		GeoipStatus s = GeoipService.evaluateStatus(NOW - 3 * DAY, null, NOW, true);
		assertEquals("ok", s.status());
	}
}
```

- [ ] **Step 3: 跑測試確認 FAIL**

Run: `mvn -q test -Dtest=GeoipStatusTest`
Expected: 編譯失敗 / FAIL —「evaluateStatus not defined」。

- [ ] **Step 4: 實作 evaluateStatus + 常數**

在 `GeoipService.java`:
1. 頂部 import 區(現有 import 之後)確認有 `java.util.ArrayList`、`java.util.List`(已有,line 5、7)。加 `import com.cym.ext.GeoipStatus;`(接在 line 16 `import com.cym.ext.GeoipDbInfo;` 後)。
2. 在 `versionCache` 欄位(line 52)附近、`DOWNLOAD_TIMEOUT_MS` 後加常數:

```java
	/** 一天毫秒數。 */
	public static final long DAY_MS = 24L * 60 * 60 * 1000;

	/** realip.conf 檔名;路徑一律 GEOIP_DIR + REALIP_CONF_NAME,不硬編第二處(spec 路徑單一來源)。 */
	public static final String REALIP_CONF_NAME = "realip.conf";
```

3. 加 static 純函式(放在 `readBuildDate` 之後,line 152 後):

```java
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
```

- [ ] **Step 5: 跑測試確認 PASS**

Run: `mvn -q test -Dtest=GeoipStatusTest`
Expected: PASS(10 tests)。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cym/ext/GeoipStatus.java src/main/java/com/cym/service/GeoipService.java src/test/java/com/cym/service/GeoipStatusTest.java
git commit -m "feat(geoip): add evaluateStatus cross-verify pure function + boundary unit tests"
```

---

### Task 3: GeoipDbInfo 擴充欄位

**Files:**
- Modify: `src/main/java/com/cym/ext/GeoipDbInfo.java`

**Interfaces:**
- Produces: `GeoipDbInfo` 新增 `filePath`(String)、`lastModifiedAt`(Long)、`lastModifiedStr`(String)、`status`(String `"ok"`|`"warn"`)、`statusReasons`(`List<String>` 已套 i18n 的原因文字)、`cloudflare`(Boolean,true=Cloudflare 列)。各附 getter/setter。

- [ ] **Step 1: 加欄位 + getter/setter**

在 `scheduleTime` 欄位(line 27)後加:

```java
	/** 檔案絕對路徑 */
	private String filePath;
	/** 檔案最後修改 epoch millis;不存在為 null */
	private Long lastModifiedAt;
	/** 檔案最後修改格式化字串 yyyy-MM-dd HH:mm;不存在為 null */
	private String lastModifiedStr;
	/** 交叉驗證狀態:ok=正常、warn=待確認 */
	private String status;
	/** 待確認原因文字(已套 i18n);正常時空 list */
	private java.util.List<String> statusReasons;
	/** 是否為 Cloudflare IP 清單列(realip.conf,無 mmdb build date) */
	private Boolean cloudflare;
```

在 `setScheduleTime`(line 105-107)後加對應 getter/setter:

```java
	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public Long getLastModifiedAt() {
		return lastModifiedAt;
	}

	public void setLastModifiedAt(Long lastModifiedAt) {
		this.lastModifiedAt = lastModifiedAt;
	}

	public String getLastModifiedStr() {
		return lastModifiedStr;
	}

	public void setLastModifiedStr(String lastModifiedStr) {
		this.lastModifiedStr = lastModifiedStr;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public java.util.List<String> getStatusReasons() {
		return statusReasons;
	}

	public void setStatusReasons(java.util.List<String> statusReasons) {
		this.statusReasons = statusReasons;
	}

	public Boolean getCloudflare() {
		return cloudflare;
	}

	public void setCloudflare(Boolean cloudflare) {
		this.cloudflare = cloudflare;
	}
```

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/ext/GeoipDbInfo.java
git commit -m "feat(geoip): extend GeoipDbInfo with file stat + status + cloudflare fields"
```

---

## Phase B — 後端服務層(GeoipService / DenyAllowService)

### Task 4: GeoipService 移快取即時讀 + 填 stat/status + Cloudflare 列

**Files:**
- Modify: `src/main/java/com/cym/service/GeoipService.java`(`getDbInfos`、移除 `versionCache` 與 `getVersionCached`、`download` 內 `versionCache.remove`)

**Interfaces:**
- Consumes: Task 2 的 `evaluateStatus`、`DAY_MS`、`REALIP_CONF_NAME`;Task 3 的 GeoipDbInfo 新 setter。
- Produces: `getDbInfos()` 回傳含 stat/status/statusReasons 的列 + 尾端一列 Cloudflare。內部 `buildReasonTexts(GeoipStatus)` 把 reason code 套 i18n(需 `@Inject MessageUtils m`)。

- [ ] **Step 1: 注入 MessageUtils(供 reason 文字 i18n)**

在 `@Inject SettingService settingService;`(line 35-36)後加:

```java
	@Inject
	com.cym.utils.MessageUtils m;
```

- [ ] **Step 2: 移除 versionCache 與 getVersionCached、改 getDbInfos 即時讀 + 填新欄位**

刪除 `versionCache` 欄位(line 51-52)與 `getVersionCached` 方法(line 121-130)。
`download` 內 `versionCache.remove(dbKey);`(line 194)整行刪除。
把 `getDbInfos()`(line 67-106)整段換成:

```java
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

			// 交叉驗證:檔案存在才判定(不存在→ status 空,前端顯示「未下載」)
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
			texts.add(r.days() != null ? tmpl.replace("{days}", String.valueOf(r.days())) : tmpl);
		}
		return texts;
	}
```

- [ ] **Step 3: 編譯 + 既有單元測試回歸**

Run: `mvn -q test -Dtest=GeoipStatusTest`
Expected: BUILD SUCCESS + PASS(evaluateStatus 未動,仍綠;確認移快取沒破壞)。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cym/service/GeoipService.java
git commit -m "fix(geoip): remove versionCache (stale version root cause) + add file stat/status + Cloudflare row"
```

---

### Task 5: GeoipService.downloadCloudflare(Java 抓 CF IP 寫 realip.conf,jar+Docker 通用)

**Files:**
- Modify: `src/main/java/com/cym/service/GeoipService.java`

**Interfaces:**
- Produces: `GeoipService.downloadCloudflare()` → `boolean`。抓 cloudflare ips-v4/v6,生成 `GEOIP_DIR + REALIP_CONF_NAME`(格式對齊 update-geoip-cf.sh:`set_real_ip_from` + 本機信任網段 + `real_ip_header CF-Connecting-IP` + `real_ip_recursive on`),先寫 .tmp 再 move。

- [ ] **Step 1: 實作 downloadCloudflare**

在 `download` 方法(line 203 `}` 結束)後、class 結尾 `}` 前加:

```java
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
```

需確認 import 有 `cn.hutool.core.util.StrUtil`(若無則加 `import cn.hutool.core.util.StrUtil;`);`HttpUtil`(line 21)、`FileUtil`(line 20)、`DateUtil`(line 19)已有。

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/service/GeoipService.java
git commit -m "feat(geoip): add downloadCloudflare (Java fetch CF IPs → realip.conf, jar+Docker)"
```

---

### Task 6: GeoipController 加 downloadCloudflare 端點

**Files:**
- Modify: `src/main/java/com/cym/controller/adminPage/GeoipController.java`

**Interfaces:**
- Consumes: Task 5 `geoipService.downloadCloudflare()`。
- Produces: `POST /adminPage/geoip/downloadCloudflare` → JsonResult。

- [ ] **Step 1: 加端點**

在 `download` 方法(line 40 `}`)後、class 結尾前加:

```java
	/** 手動更新 Cloudflare Real IP 清單(realip.conf)。 */
	@Mapping("downloadCloudflare")
	public JsonResult downloadCloudflare() {
		boolean ok = geoipService.downloadCloudflare();
		if (ok) {
			return renderSuccess();
		}
		return renderError(m.get("geoipStr.downloadFail"));
	}
```

`m` 由 BaseController 提供(已注入)。

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/controller/adminPage/GeoipController.java
git commit -m "feat(geoip): add /downloadCloudflare endpoint"
```

---

### Task 7: DenyAllowService — type 過濾 / usedBy 共用 / 衝突檢查 / migration

**Files:**
- Modify: `src/main/java/com/cym/service/DenyAllowService.java`
- Create: `src/test/java/com/cym/service/DenyAllowTypeTest.java`

**Interfaces:**
- Consumes: `SqlHelper`、`ConditionAndWrapper`、`Server.getDenyId/getAllowId`、`csvContainsId`(既有 static)。
- Produces:
  - `searchByType(Page page, String type)` → `Page`(依 type 過濾;type 空=全部)。
  - `resolveTypeByReference(String daId, List<Server> servers, String httpDenyId, String httpAllowId, String streamDenyId, String streamAllowId)` → `String`(`"allow"` 若被任一 allowId 引用、否則 `"deny"`;純函式便於測)。
  - `findConflictIps(DenyAllow da, String type)` → `List<String>`(該名單中已存在於「另一 type」名單的 IP;空=無衝突)。

- [ ] **Step 1: 寫失敗測試(resolveTypeByReference + findConflictIps)**

Create `src/test/java/com/cym/service/DenyAllowTypeTest.java`:

```java
package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.model.Server;

/**
 * type 反查歸類邏輯(純函式)測試。findConflictIps 依賴 DB,由 E2E 驗;此處只測 resolveTypeByReference。
 */
public class DenyAllowTypeTest {

	private Server server(String denyId, String allowId) {
		Server s = new Server();
		s.setDenyId(denyId);
		s.setAllowId(allowId);
		return s;
	}

	@Test
	void referencedByAllowId_allow() {
		List<Server> servers = new ArrayList<>();
		servers.add(server(null, "da1,da2"));
		assertEquals("allow", DenyAllowService.resolveTypeByReference("da1", servers, null, null, null, null));
	}

	@Test
	void referencedByDenyId_deny() {
		List<Server> servers = new ArrayList<>();
		servers.add(server("da1", null));
		assertEquals("deny", DenyAllowService.resolveTypeByReference("da1", servers, null, null, null, null));
	}

	@Test
	void notReferenced_defaultDeny() {
		assertEquals("deny", DenyAllowService.resolveTypeByReference("da9", new ArrayList<>(), null, null, null, null));
	}

	@Test
	void referencedByHttpAllowGlobal_allow() {
		assertEquals("allow", DenyAllowService.resolveTypeByReference("da1", new ArrayList<>(), null, "da1", null, null));
	}

	@Test
	void conflictBothDenyAndAllow_deny() {
		// 同時被 server allowId 與 http global denyId 引用(矛盾)→ 歸 deny
		List<Server> servers = new ArrayList<>();
		servers.add(server(null, "da1"));
		assertEquals("deny", DenyAllowService.resolveTypeByReference("da1", servers, "da1", null, null, null));
	}
}
```

- [ ] **Step 2: 跑測試確認 FAIL**

Run: `mvn -q test -Dtest=DenyAllowTypeTest`
Expected: FAIL —「resolveTypeByReference not defined」。

- [ ] **Step 3: 實作三個方法**

在 `DenyAllowService.java` 加(import 需 `com.cym.model.Server`、`com.cym.sqlhelper.utils.ConditionAndWrapper`、`java.util.List`、`java.util.LinkedHashSet` 已有 / 補齊):

```java
	/** 依 type 過濾分頁(type 空 → 全部)。 */
	public Page searchByType(Page page, String type) {
		if (StrUtil.isBlank(type)) {
			return sqlHelper.findPage(page, DenyAllow.class);
		}
		return sqlHelper.findPage(new ConditionAndWrapper().eq("type", type), page, DenyAllow.class);
	}

	/**
	 * 反查引用決定 type(純函式):被任一 allowId(server / http global / stream global)引用 → allow;
	 * 否則(含被 denyId 引用、或矛盾同時被兩者引用、或未被引用)→ deny。
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
		// 矛盾(同時被黑白引用)→ deny(較安全) + 呼叫端 log
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
```

- [ ] **Step 4: 跑測試確認 PASS**

Run: `mvn -q test -Dtest=DenyAllowTypeTest`
Expected: PASS(5 tests)。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cym/service/DenyAllowService.java src/test/java/com/cym/service/DenyAllowTypeTest.java
git commit -m "feat(denyAllow): add type filter + reference-based type resolver + conflict check"
```

---

## Phase C — 後端控制層(controller / migration)

### Task 8: DenyAllowController.addOver 加衝突檢查 + type 傳遞

**Files:**
- Modify: `src/main/java/com/cym/controller/adminPage/DenyAllowController.java`

**Interfaces:**
- Consumes: Task 7 `denyAllowService.findConflictIps`。DenyAllow 表單已含 `type`(Solon 自動綁定新欄位)。
- Produces: `addOver` 衝突時回 `renderError(msg)` 列出衝突 IP,不存檔。

- [ ] **Step 1: 在 addOver 開頭加衝突檢查**

把 `addOver`(line 92-109)改為(在 fetch/removeSame 之前先檢查衝突):

```java
	@Mapping("addOver")
	public JsonResult addOver(DenyAllow denyAllow) {
		// 黑白衝突檢查:同一 IP 已存在於另一 type 名單時提示,不靜默建立
		java.util.List<String> conflicts = denyAllowService.findConflictIps(denyAllow, denyAllow.getType());
		if (!conflicts.isEmpty()) {
			String preview = conflicts.size() > 5
					? StrUtil.join(", ", conflicts.subList(0, 5)) + " ..."
					: StrUtil.join(", ", conflicts);
			return renderError(m.get("denyAllowStr.typeConflict").replace("{ips}", preview));
		}

		// 若填了來源 URL，立即抓一次；之後每天 fetchTime 排程繼續抓
		if (StrUtil.isNotBlank(denyAllow.getSourceUrl())) {
			boolean ok = denyAllowService.fetchAndUpdate(denyAllow);
			if (!ok) {
				logger.warn("Immediate fetch failed for {} ({}), saving record anyway", denyAllow.getName(), denyAllow.getSourceUrl());
			}
		} else {
			denyAllowService.removeSame(denyAllow);
		}

		sqlHelper.insertOrUpdate(denyAllow);

		return renderSuccess();
	}
```

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/controller/adminPage/DenyAllowController.java
git commit -m "feat(denyAllow): reject cross-type IP conflict on save with detailed message"
```

---

### Task 9: ProtectionCertController 拆黑白分頁 + 共用 usedBy

**Files:**
- Modify: `src/main/java/com/cym/controller/adminPage/ProtectionCertController.java`

**Interfaces:**
- Consumes: Task 7 `searchByType`。
- Produces: view model 加 `blackPage`(type=deny)、`whitePage`(type=allow),移除舊 `daPage`;`geoipDbInfos` 不變(已含新欄位)。

- [ ] **Step 1: 抽 buildExts 私有方法 + 改 index 拆黑白**

把 `index`(line 37-107)的 DenyAllow 區塊改為:先算共用引用資料,再對 black/white 各建 exts。把 line 39-84(daPage 那段)換成:

```java
		List<Server> allServers = sqlHelper.findAll(Server.class);
		String httpDenyId = settingService.get("denyId");
		String httpAllowId = settingService.get("allowId");
		String streamDenyId = settingService.get("denyIdStream");
		String streamAllowId = settingService.get("allowIdStream");

		// 黑名單分頁(type=deny)
		Page blackPage = new Page();
		blackPage.setCurr(page.getCurr());
		blackPage.setLimit(page.getLimit());
		blackPage = denyAllowService.searchByType(blackPage, "deny");
		blackPage.setRecords(buildExts((List<DenyAllow>) blackPage.getRecords(), allServers,
				httpDenyId, httpAllowId, streamDenyId, streamAllowId));
		modelAndView.put("blackPage", blackPage);

		// 白名單分頁(type=allow)
		Page whitePage = new Page();
		whitePage.setCurr(1);
		whitePage.setLimit(page.getLimit());
		whitePage = denyAllowService.searchByType(whitePage, "allow");
		whitePage.setRecords(buildExts((List<DenyAllow>) whitePage.getRecords(), allServers,
				httpDenyId, httpAllowId, streamDenyId, streamAllowId));
		modelAndView.put("whitePage", whitePage);
```

在 class 內加私有方法(取代兩 controller 重複邏輯):

```java
	/** DenyAllow → DenyAllowExt(含 ipCount / usedBy / lastFetchAtStr)。 */
	private List<DenyAllowExt> buildExts(List<DenyAllow> list, List<Server> allServers,
			String httpDenyId, String httpAllowId, String streamDenyId, String streamAllowId) {
		List<DenyAllowExt> exts = new ArrayList<DenyAllowExt>();
		for (DenyAllow denyAllow : list) {
			DenyAllowExt ext = new DenyAllowExt();
			ext.setDenyAllow(denyAllow);
			ext.setIpCount(StrUtil.isBlankIfStr(denyAllow.getIp()) ? 0 : denyAllow.getIp().split("\n").length);

			List<String> usedBy = new ArrayList<String>();
			String daId = denyAllow.getId();
			if (DenyAllowService.csvContainsId(httpDenyId, daId) || DenyAllowService.csvContainsId(httpAllowId, daId)) {
				usedBy.add("HTTP Global");
			}
			if (DenyAllowService.csvContainsId(streamDenyId, daId) || DenyAllowService.csvContainsId(streamAllowId, daId)) {
				usedBy.add("Stream Global");
			}
			for (Server s : allServers) {
				if (DenyAllowService.csvContainsId(s.getDenyId(), daId) || DenyAllowService.csvContainsId(s.getAllowId(), daId)) {
					String label = StrUtil.isNotEmpty(s.getServerName()) ? s.getServerName() : s.getListen();
					usedBy.add("Server: " + label);
				}
			}
			ext.setUsedBy(usedBy);

			if (denyAllow.getLastFetchAt() != null) {
				ext.setLastFetchAtStr(DateUtil.format(new java.util.Date(denyAllow.getLastFetchAt()), "yyyy-MM-dd HH:mm"));
			}
			exts.add(ext);
		}
		return exts;
	}
```

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。(前端 Task 11 會改用 blackPage/whitePage;此步驟後舊 index.html 的 `daPage` 引用會在 Task 11 一併換掉 —— 兩 task 之間 build 綠但頁面暫未渲染 DenyAllow 列表,可接受,因 E2E 在 Task 15 才跑。)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/controller/adminPage/ProtectionCertController.java
git commit -m "refactor(protectionCert): split DenyAllow into black/white pages, extract buildExts"
```

---

### Task 10: InitConfig — type migration(反查引用自動歸類)

**Files:**
- Modify: `src/main/java/com/cym/config/InitConfig.java`

**Interfaces:**
- Consumes: Task 7 `DenyAllowService.resolveTypeByReference`;`settingService` flag `denyAllowTypeMigrated`。

- [ ] **Step 1: 在 start() 加 migration 區塊**

在既有 migration 群組後(如 line 267 `httpGroupMigrated` 區塊之後)加:

```java
		// 遷移:為既有 DenyAllow 反查引用歸類 type(被 allowId 引用→allow、否則→deny)
		if (!"1".equals(settingService.get("denyAllowTypeMigrated"))) {
			List<Server> daServers = sqlHelper.findAll(Server.class);
			String httpDenyId = settingService.get("denyId");
			String httpAllowId = settingService.get("allowId");
			String streamDenyId = settingService.get("denyIdStream");
			String streamAllowId = settingService.get("allowIdStream");
			List<DenyAllow> daList = sqlHelper.findAll(DenyAllow.class);
			int migrated = 0;
			for (DenyAllow da : daList) {
				if (StrUtil.isNotEmpty(da.getType())) {
					continue; // 已有 type 不動
				}
				String resolved = com.cym.service.DenyAllowService.resolveTypeByReference(
						da.getId(), daServers, httpDenyId, httpAllowId, streamDenyId, streamAllowId);
				da.setType(resolved);
				sqlHelper.updateById(da);
				migrated++;
			}
			settingService.set("denyAllowTypeMigrated", "1");
			logger.info("Migration: assigned type to {} existing DenyAllow records (reference-based)", migrated);
		}
```

需確認 import 有 `com.cym.model.DenyAllow`、`com.cym.model.Server`(InitConfig 應已 import Server;若無則加)。

- [ ] **Step 2: 編譯驗證**

Run: `mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/config/InitConfig.java
git commit -m "feat(init): migrate existing DenyAllow type by reference (allowId→allow else deny)"
```

---

## Phase D — 前端(view / JS)+ i18n

### Task 11: protectionCert/index.html — 6 tab 重構 + GeoIP 表格擴充

**Files:**
- Modify: `src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html`

**Interfaces:**
- Consumes: `blackPage` / `whitePage`(Task 9)、`geoipDbInfos`(含新欄位)、i18n(Task 14)。

- [ ] **Step 1: 換 tab 標題為 6 個**

把 tab 標題 `<ul>`(line 79-84)換成:

```html
				<ul class="layui-tab-title">
					<li class="layui-this">${geoipStr.title}</li>
					<li>${denyAllowStr.blackTab}</li>
					<li>${denyAllowStr.whiteTab}</li>
					<li>${geoStr.title}</li>
					<li>${asnStr.title}</li>
					<li>${menuStr.cert}</li>
				</ul>
```

- [ ] **Step 2: Tab 1 改為純 IP資料庫(擴充 GeoIP 表格 + 重新驗證按鈕),移除 DenyAllow 表格**

把 Tab 1(line 88-197 整個 `<div class="layui-tab-item layui-show">`)換成:

```html
					<!-- ==================== Tab 1: IP 資料庫 ==================== -->
					<div class="layui-tab-item layui-show">
						<div style="margin:10px 0;">
							<button type="button" class="layui-btn layui-btn-sm layui-btn-primary" onclick="geoipNS.reverify()"><i class="layui-icon layui-icon-refresh"></i> ${geoipStr.reverifyAll}</button>
						</div>
						<table class="layui-table" lay-size="sm">
							<thead>
								<tr>
									<th>${geoipStr.database}</th>
									<th>${geoipStr.buildDate}</th>
									<th>${geoipStr.filePath}</th>
									<th>${geoipStr.fileSize}</th>
									<th>${geoipStr.lastModified}</th>
									<th>${geoipStr.updateWay}</th>
									<th>${geoipStr.status}</th>
									<th>${commonStr.operation}</th>
								</tr>
							</thead>
							<tbody id="geoipTableBody">
								<#if geoipDbInfos??>
								<#list geoipDbInfos as g>
								<tr>
									<td>${g.displayName}</td>
									<td>
										<#if g.cloudflare>
											<span style="color:#bbb;">--</span>
										<#elseif g.version??>
											<span style="color:#666;">${g.version}</span>
										<#else>
											<span style="color:#bbb;">${geoipStr.notDownloaded}</span>
										</#if>
									</td>
									<td><span style="color:#999;font-size:12px;word-break:break-all;">${g.filePath}</span></td>
									<td><#if g.sizeStr??>${g.sizeStr}<#else><span style="color:#bbb;">--</span></#if></td>
									<td><#if g.lastModifiedStr??><span style="color:#666;">${g.lastModifiedStr}</span><#else><span style="color:#bbb;">--</span></#if></td>
									<td><span style="color:#666;">${geoipStr.scheduleValue?replace("{time}", g.scheduleTime)}</span></td>
									<td>
										<#if !g.exists>
											<span class="layui-badge layui-bg-gray">${geoipStr.notDownloaded}</span>
										<#elseif g.status?? && g.status == "warn">
											<span class="layui-badge layui-bg-orange">${geoipStr.statusWarn}</span>
											<#if g.statusReasons??>
												<div style="color:#FF5722;font-size:12px;margin-top:4px;">
													<#list g.statusReasons as reason>
														<div>· ${reason}</div>
													</#list>
												</div>
											</#if>
										<#else>
											<span class="layui-badge layui-bg-green">${geoipStr.statusOk}</span>
										</#if>
									</td>
									<td>
										<#if g.cloudflare>
											<button type="button" class="layui-btn layui-btn-sm" onclick="geoipNS.downloadCloudflare()"><i class="layui-icon layui-icon-download-circle"></i> ${geoipStr.download}</button>
										<#else>
											<button type="button" class="layui-btn layui-btn-sm" onclick="geoipNS.download('${g.key}')"><i class="layui-icon layui-icon-download-circle"></i> ${geoipStr.download}</button>
										</#if>
									</td>
								</tr>
								</#list>
								</#if>
							</tbody>
						</table>
					</div>
```

- [ ] **Step 3: 新增 Tab 2(黑名單)、Tab 3(白名單),重用 DenyAllow 表格結構**

在 Tab 1 `</div>` 後插入兩個新 tab。黑名單用 `blackPage`、白名單用 `whitePage`,兩者結構同(參考原 line 128-196 的 DenyAllow toolbar + 表格),差別:type hidden 值 + namespace 呼叫帶 type。範本(黑名單,白名單複製後把 `black`→`white`、`'deny'`→`'allow'`、`blackPage`→`whitePage`):

```html
					<!-- ==================== Tab 2: 黑名單 ==================== -->
					<div class="layui-tab-item">
						<div style="margin-bottom:10px;">
							<button type="button" class="layui-btn layui-btn-sm layui-btn-normal" onclick="denyAllowNS.add('deny')"><i class="layui-icon layui-icon-add-circle-fine"></i> ${commonStr.add}</button>
							<button type="button" class="layui-btn layui-btn-danger layui-btn-sm" onclick="denyAllowNS.delMany('black')"><i class="layui-icon layui-icon-delete"></i> ${commonStr.delAll}</button>
						</div>
						<table class="layui-table layui-form" lay-size="sm">
							<thead>
								<tr>
									<th style="width:20px;"><input type="checkbox" lay-filter="blackCheckAll" lay-skin="primary"></th>
									<th>${denyAllowStr.name}</th>
									<th>${denyAllowStr.ipCount}</th>
									<th>${denyAllowStr.lastFetchAt}</th>
									<th>${denyAllowStr.usedBy}</th>
									<th>${commonStr.operation}</th>
								</tr>
							</thead>
							<tbody>
								<#list blackPage.records as ext>
								<tr>
									<td><input type="checkbox" name="blackIds" title="" lay-skin="primary" value="${ext.denyAllow.id}"></td>
									<td>${ext.denyAllow.name}</td>
									<td>${ext.ipCount}</td>
									<td><#if ext.lastFetchAtStr??><span style="color:#666;">${ext.lastFetchAtStr}</span><#else><span style="color:#bbb;">${denyAllowStr.neverFetched}</span></#if></td>
									<td>
										<#if ext.usedBy?? && (ext.usedBy?size > 0)>
											<#list ext.usedBy as ref><span class="layui-badge layui-bg-blue" style="margin:2px;">${ref}</span></#list>
										<#else><span style="color:#999;">--</span></#if>
									</td>
									<td>
										<button type="button" class="layui-btn layui-btn-sm" onclick="denyAllowNS.edit('${ext.denyAllow.id}')"><i class="layui-icon layui-icon-edit"></i> ${commonStr.edit}</button>
										<button type="button" class="layui-btn layui-btn-sm layui-btn-danger" onclick="denyAllowNS.del('${ext.denyAllow.id}')"><i class="layui-icon layui-icon-delete"></i> ${commonStr.del}</button>
									</td>
								</tr>
								</#list>
								<#if blackPage.records?size == 0>
								<tr><td colspan="99" class="empty-state"><i class="layui-icon layui-icon-face-surprised" aria-hidden="true"></i><p>${commonStr.noData}</p></td></tr>
								</#if>
							</tbody>
						</table>
					</div>
```

(白名單同結構:`black`→`white`、`'deny'`→`'allow'`、`blackPage`→`whitePage`、`blackCheckAll`→`whiteCheckAll`、`blackIds`→`whiteIds`。)

- [ ] **Step 4: DenyAllow 新增/編輯 Modal 加 type hidden 欄位**

在 `daAddForm`(line 405-407 附近,`<input type="hidden" name="id" id="daId">` 後)加:

```html
				<input type="hidden" name="type" id="daType" value="deny">
```

- [ ] **Step 5: 手動驗證頁面渲染(不 commit 前先 build+起服務目視)**

Run: `mvn -q package -DskipTests` 後起測試服務,開 `/adminPage/protectionCert`,確認 6 個 tab 標題出現、Tab1 表格 8 欄 + Cloudflare 列、Tab2/3 各自列表。

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html
git commit -m "feat(protectionCert): 6-tab restructure — IP database + black/white split + expanded GeoIP table"
```

---

### Task 12: denyAllow.js — type 支援(add 帶 type、黑白各自 delMany/checkAll)

**Files:**
- Modify: `src/main/resources/static/js/adminPage/protectionCert/denyAllow.js`

**Interfaces:**
- Consumes: `#daType` hidden 欄位。
- Produces: `add(type)`、`delMany(scope)` 支援 black/white;edit 讀回 type。

- [ ] **Step 1: add 帶 type、edit 回填 type**

`add`(line 192-202)簽名加 type:

```javascript
	function add(type) {
		$("#daId").val("");
		$("#daName").val("");
		$("#daSourceUrl").val("");
		$("#daFetchTime").val("");
		$("#daType").val(type || 'deny');
		ipTags = [];
		searchFilter = '';
		$('#daTagSearch').val('');
		renderTags();
		showWindow(commonStr.add);
	}
```

`edit` 的 success 內(line 276-281 附近)加回填 type:

```javascript
						$("#daType").val(denyAllow.type || 'deny');
```

- [ ] **Step 2: delMany 支援 black/white scope**

`delMany`(line 314-345)改讀對應 checkbox name:

```javascript
	function delMany(scope) {
		if (confirm(commonStr.confirmDel)) {
			var inputName = scope === 'white' ? 'whiteIds' : 'blackIds';
			var ids = [];
			$("input[name='" + inputName + "']").each(function() {
				if ($(this).prop("checked")) {
					ids.push($(this).val());
				}
			});
			if (ids.length == 0) {
				layer.msg(commonStr.unselected);
				return;
			}
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/denyAllow/del',
				data: { id: ids.join(",") },
				dataType: 'json',
				success: function(data) {
					if (data.success) { location.reload(); } else { layer.msg(data.msg); }
				},
				error: function() { layer.alert(commonStr.errorInfo); }
			});
		}
	}
```

- [ ] **Step 3: checkAll 事件改綁 black/white**

把 `form.on('checkbox(daCheckAll)'...)`(line 349-356)換成兩個:

```javascript
		form.on('checkbox(blackCheckAll)', function(data) {
			$("input[name='blackIds']").prop("checked", data.elem.checked);
			form.render();
		});
		form.on('checkbox(whiteCheckAll)', function(data) {
			$("input[name='whiteIds']").prop("checked", data.elem.checked);
			form.render();
		});
```

- [ ] **Step 4: 手動驗證 + Commit**

Run: 起服務,黑名單 tab 新增一筆(確認存進 type=deny)、白名單新增一筆(type=allow)。

```bash
git add src/main/resources/static/js/adminPage/protectionCert/denyAllow.js
git commit -m "feat(denyAllow): JS type support — add(type), scoped delMany/checkAll, edit refill type"
```

---

### Task 13: geoip.js — Cloudflare 更新 + 重新驗證全部

**Files:**
- Modify: `src/main/resources/static/js/adminPage/protectionCert/geoip.js`

**Interfaces:**
- Produces: `geoipNS.downloadCloudflare()`、`geoipNS.reverify()`。

- [ ] **Step 1: 加 downloadCloudflare + reverify**

在 `download` 函式後、`ns.download = download;` 前加:

```javascript
	function downloadCloudflare() {
		layer.confirm(geoipStr.download + ' (Cloudflare) ?', { icon: 3 }, function(idx) {
			layer.close(idx);
			var loading = layer.msg(geoipStr.downloading, { icon: 16, time: 0, shade: 0.1 });
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/geoip/downloadCloudflare',
				dataType: 'json',
				success: function(data) {
					layer.close(loading);
					if (data.success) {
						layer.msg(geoipStr.downloadSuccess, { icon: 1 });
						setTimeout(function() { location.reload(); }, 1000);
					} else {
						layer.msg(data.msg || geoipStr.downloadFail, { icon: 2 });
					}
				},
				error: function() { layer.close(loading); layer.msg(geoipStr.downloadFail, { icon: 2 }); }
			});
		});
	}

	// 重新驗證全部:重新拉 versions JSON,即時反映最新 stat/status(不需重下載)
	function reverify() {
		var loading = layer.msg(geoipStr.reverifying, { icon: 16, time: 0, shade: 0.1 });
		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/geoip/versions',
			dataType: 'json',
			success: function() {
				layer.close(loading);
				location.reload();
			},
			error: function() { layer.close(loading); layer.msg(commonStr.errorInfo, { icon: 2 }); }
		});
	}

	ns.downloadCloudflare = downloadCloudflare;
	ns.reverify = reverify;
```

- [ ] **Step 2: 手動驗證 + Commit**

Run: 起服務,IP資料庫 tab 點「重新驗證全部」→ 頁面刷新;Cloudflare 列點下載 → 觸發 downloadCloudflare。

```bash
git add src/main/resources/static/js/adminPage/protectionCert/geoip.js
git commit -m "feat(geoip): JS downloadCloudflare + reverifyAll"
```

---

### Task 14: i18n — 三份 properties 同步新 key

**Files:**
- Modify: `src/main/resources/messages.properties`(簡)
- Modify: `src/main/resources/messages_zh_TW.properties`(繁)
- Modify: `src/main/resources/messages_en_US.properties`(英)

**Interfaces:**
- Produces: 下列 key,三份都要有;CJK 用 `\uXXXX`。

新增 key(值以繁中語意示範,簡/英對應翻譯):

| key | 繁(zh_TW) | 簡(messages) | 英(en_US) |
|---|---|---|---|
| `geoipStr.title` | IP 資料庫 | IP 数据库 | IP Database |
| `geoipStr.buildDate` | 內部建置日期 | 内部建置日期 | Build Date |
| `geoipStr.filePath` | 檔案路徑 | 文件路径 | File Path |
| `geoipStr.fileSize` | 檔案大小 | 文件大小 | File Size |
| `geoipStr.lastModified` | 檔案最後修改 | 文件最后修改 | Last Modified |
| `geoipStr.updateWay` | 更新方式 | 更新方式 | Update Method |
| `geoipStr.status` | 狀態 | 状态 | Status |
| `geoipStr.statusOk` | 正常 | 正常 | OK |
| `geoipStr.statusWarn` | 待確認 | 待确认 | Check Needed |
| `geoipStr.reverifyAll` | 重新驗證全部 | 重新验证全部 | Re-verify All |
| `geoipStr.reverifying` | 驗證中... | 验证中... | Verifying... |
| `geoipStr.reasonFileStale` | 檔案已 {days} 天未更新,排程可能未執行 | 文件已 {days} 天未更新,排程可能未执行 | File not updated for {days} days; scheduler may not be running |
| `geoipStr.reasonBuildStale` | 資料建置日期為 {days} 天前,可能下載到舊資料 | 数据建置日期为 {days} 天前,可能下载到旧数据 | Data built {days} days ago; may have downloaded stale data |
| `geoipStr.reasonCorrupt` | 無法讀取版本,檔案可能損壞 | 无法读取版本,文件可能损坏 | Cannot read version; file may be corrupt |
| `denyAllowStr.blackTab` | 黑名單 | 黑名单 | Blacklist |
| `denyAllowStr.whiteTab` | 白名單 | 白名单 | Whitelist |
| `denyAllowStr.typeConflict` | 下列 IP 已存在於另一類型名單,無法建立:{ips} | 下列 IP 已存在于另一类型名单,无法建立:{ips} | These IPs already exist in the opposite-type list: {ips} |

- [ ] **Step 1: 三份各加上述 key**

繁/簡直接寫中文(properties 檔存 ISO-8859-1,但既有 zh_TW/messages 內含中文的方式沿用該檔現行編碼慣例 —— 對照鄰近既有 `geoipStr.*` key 的寫法插入,保持一致);英文檔寫 ASCII。逐一比對三份 key 數量一致。

- [ ] **Step 2: 驗證 i18n 載入**

Run: `mvn -q package -DskipTests`,起服務切三語言,確認新表頭 / 狀態 / tab 名正確顯示、無 `??key??`。

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages*.properties
git commit -m "i18n(firewall): add geoip status + black/white tab + conflict keys (zh_TW/zh_CN/en_US)"
```

---

### Task 15: E2E — 6 tab + Cloudflare 列 + 黑白過濾 + 待確認原因(只驗 UI)

**Files:**
- Create: `tests/e2e/32-firewall-ip-tabs.spec.js`

**Interfaces:**
- Consumes: 全部前面 task 的 UI。判定邏輯本身由 GeoipStatusTest 覆蓋,E2E 只驗呈現。

- [ ] **Step 1: 寫 E2E(4 個斷言)**

Create `tests/e2e/32-firewall-ip-tabs.spec.js`:

```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('防火牆管理 — 6 tab + IP 資料庫交叉驗證', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab-title');
  });

  test('① 6 個頂層 tab 存在', async ({ page }) => {
    const tabs = page.locator('.layui-tab-title > li');
    await expect(tabs).toHaveCount(6);
    const texts = (await tabs.allTextContents()).join('|');
    expect(texts).toMatch(/IP\s?資料庫|IP\s?数据库|IP Database/);
    expect(texts).toMatch(/黑名單|黑名单|Blacklist/);
    expect(texts).toMatch(/白名單|白名单|Whitelist/);
  });

  test('② IP資料庫表格有 Cloudflare 列 + 8 欄表頭', async ({ page }) => {
    const headers = await page.locator('#geoipTableBody').locator('xpath=../thead//th').allTextContents();
    expect(headers.length).toBe(8);
    const bodyText = await page.locator('#geoipTableBody').textContent();
    expect(bodyText).toMatch(/Cloudflare/);
  });

  test('③ 黑名單 / 白名單 tab 可切換且各自有新增按鈕', async ({ page }) => {
    // 切到黑名單 tab(第 2 個)
    await page.locator('.layui-tab-title > li').nth(1).click();
    await expect(page.locator("button[onclick=\"denyAllowNS.add('deny')\"]")).toBeVisible();
    // 切到白名單 tab(第 3 個)
    await page.locator('.layui-tab-title > li').nth(2).click();
    await expect(page.locator("button[onclick=\"denyAllowNS.add('allow')\"]")).toBeVisible();
  });

  test('④ 狀態欄呈現正常/待確認徽章', async ({ page }) => {
    // 至少有一個狀態徽章(green ok / orange warn / gray 未下載)
    const badges = page.locator('#geoipTableBody .layui-badge');
    await expect(badges.first()).toBeVisible();
    // 若有待確認,必伴隨原因文字(· 開頭);此斷言在有 warn 時才驗
    const warnBadge = page.locator('#geoipTableBody .layui-bg-orange');
    if (await warnBadge.count() > 0) {
      const rowText = await warnBadge.first().locator('xpath=ancestor::td').textContent();
      expect(rowText).toMatch(/·/);
    }
  });
});
```

- [ ] **Step 2: build + 跑 E2E**

Run: `mvn -q package -DskipTests && npx playwright test tests/e2e/32-firewall-ip-tabs.spec.js`
Expected: 4 passed。

- [ ] **Step 3: 全套 E2E 回歸(確認沒破壞既有)**

Run: `npm run test:fast`
Expected: 全綠(特別是 23-geoip-version 若斷言舊 4 欄表格需同步更新 → 見下方 Self-Review 註)。

- [ ] **Step 4: Commit**

```bash
git add tests/e2e/32-firewall-ip-tabs.spec.js
git commit -m "test(e2e): firewall 6-tab UI — tabs, Cloudflare row, black/white filter, status reasons"
```

---

## Self-Review(寫完計畫的檢查)

**Spec 覆蓋對照:**
- §3.1 移 versionCache → Task 4 ✓;filePath/lastModified/status/statusReasons → Task 3+4 ✓;Cloudflare 列 → Task 4 ✓;realip 路徑單一來源(GEOIP_DIR + REALIP_CONF_NAME)→ Task 2/4 ✓;手動更新 Java 版 → Task 5+6 ✓。
- §3.2 距今三規則 + 純函式 + reasons 收集全部 → Task 2 ✓。
- §3.3 type 欄 @InitValue → Task 1 ✓;反查 migration → Task 7+10 ✓;衝突檢查 → Task 7+8 ✓;引用下拉 type 過濾 → **見下方待確認**。
- §3.4 6 tab + 表格擴充 + 黑白拆 → Task 11 ✓;versions JSON 擴充前端刷新 → Task 13 reverify ✓。
- §3.5 i18n ×3 → Task 14 ✓;判定單元測試 → Task 2 ✓;E2E 只驗 UI → Task 15 ✓。

**待實作者注意 / 已知邊界:**
1. **既有 E2E `23-geoip-version.spec.js`** 可能斷言舊 GeoIP 表格(4 欄 / `geoipStr.version` 表頭)。Task 11 改表格後若該 spec 紅,需同步更新其斷言(屬本次改動範圍,實作 Task 15 Step 3 時處理)。
2. **§3.3「引用端下拉依 type 過濾」**:server / http / stream 反向代理設定頁的 denyId(選黑名單)/ allowId(選白名單)下拉,理想上各自只列對應 type。本計畫未含該前端改動(那是 server/http/stream 編輯頁,非 protectionCert 頁),因 spec 核心是 protectionCert 重構。**若要一併做**,需另加 task 改 `HttpController.getDenyAllow` / `StreamController.getDenyAllow` / server 編輯頁下拉來源依 type 過濾 —— 建議獨立小 plan,避免本 plan 範圍膨脹。實作前與使用者確認是否納入。
3. **Cloudflare 手動更新在 Docker**:Task 5 的 Java `downloadCloudflare` 會覆寫 update-geoip-cf.sh 生成的同檔;兩者格式一致,下次 cron 再覆寫,無害。但 Java 版不會 `nginx -s reload`(僅產檔),與 sh 版差異已在方法註解標明。

**Placeholder 掃描:** 無 TBD/TODO;每個 code step 附完整 code。
**型別一致:** `evaluateStatus` 簽名(Task 2)與呼叫端(Task 4)一致;`GeoipStatus.Reason.code/days`(Task 2)與 `buildReasonTexts`(Task 4)一致;`searchByType`/`resolveTypeByReference`/`findConflictIps`(Task 7)與呼叫端(Task 8/9/10)一致。

---

## 執行順序建議

Phase A(Task 1-3)→ B(4-7)→ C(8-10)→ D(11-15)。後端每 task 可獨立 build 驗證;前端 Task 11 依賴 Task 9 的 blackPage/whitePage。E2E(Task 15)最後跑,需先 `mvn package`。
