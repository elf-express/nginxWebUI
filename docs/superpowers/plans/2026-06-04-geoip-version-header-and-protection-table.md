# GeoIP 版本資訊顯示 + 手動下載 實作計畫

> **For agentic workers:** 用 superpowers:executing-plans / subagent-driven-development 逐 task 執行。步驟用 `- [ ]` checkbox 追蹤。

**日期：** 2026-06-04
**狀態：** 已完成（header 採堆疊顯示版、防護頁表格、maxmind-db 讀版本、Java 下載、23 號測試 + 修 3 個既有過時測試，全綠）
**目標版本：** 下一個 minor（建議 5.2.0）
**前置：** 5.1.1（sidecar baked image + 部署文件不綁版本已 ship）

---

## Goal

兩個使用者可見功能 + 一份 E2E 測試：

1. **Header 顯示 GeoIP 版本** — 在頁面 header 顯示三個 MMDB 資料庫（Country / City / ASN）各自的「最後版本日期」（例 `2026.06.01`，來自 MMDB metadata 的真實 build date）。
2. **防護與憑證頁的 GeoIP 資訊表格** — 在「防護與憑證」頁 Tab 1（黑名單 IP）**表格前面**，新增一個 GeoIP 資訊表格，欄位：資料庫(Country/City/ASN) / 版本 / 排程時間 / 手動下載按鈕。
3. **Playwright 測試** — 新增 `23-geoip-version.spec.js` 涵蓋上述兩個流程。

## 已確認決策（向使用者問過）

| 決策 | 選擇 |
|---|---|
| 版本日期來源 | **加 maxmind-db 函式庫**讀 MMDB metadata 的真實 build date |
| 手動下載實作 | **Java（Hutool HttpRequest）**直接從 mirror 抓 mmdb（jar + Docker 都能用） |
| 排程時間欄位 | **唯讀顯示**目前固定排程（Docker cron 每週三、六 03:00 UTC）+ 上次更新時間 |

## Tech Stack

Java 8 · Solon 3.3.3（`@Component` / `@Inject` / `@Mapping`）· SqlHelper（只用 SettingService K-V，不建新表）· Hutool `HttpUtil` / `HttpRequest`（已在用）· **新增 `com.maxmind.db:maxmind-db`（Java 8 相容版，見下）** · Layui + Freemarker · Playwright E2E

---

## Context（為什麼 + 現況證據）

使用者要在 UI 看到 GeoIP 三個資料庫的版本日期,並能手動觸發更新 —— 目前**完全沒有**這能力(探查證實):

| 現況 | 證據 |
|---|---|
| 3 個 MMDB 路徑硬編在 nginx 設定 | `InitConfig.java:137-142`（`/etc/nginx/geoip/GeoLite2-{Country,City,ASN}.mmdb`，`auto_reload 60m`）|
| **沒有任何 Java 讀過 MMDB 版本** | pom.xml 無 maxmind 依賴、全 repo 無 `getMetadata()`/`buildDate` |
| 下載靠 shell script（**僅 Docker 內**） | `scripts/update-geoip-cf.sh`（P3TERX mirror）+ `entrypoint.sh:4` + `Dockerfile:53` cron 週三六 03:00 UTC |
| header 注入點 | `AppFilter.frontInterceptor()`（每個 adminPage 請求跑，`ctx.attrSet(...)` 餵 Freemarker）|
| 防護與憑證頁 | `ProtectionCertController` + `protectionCert/index.html`（4 tab：黑名單IP / GeoIP / ASN / 憑證），黑名單表在 Tab 1 |
| 伺服端觸發按鈕範例 | DenyAllow 的 `fetchAndUpdate` + ajax；下載檔給瀏覽器則是 `ExportController` + `DownloadedFile`（本案用前者：伺服端抓檔，不是給瀏覽器下載）|

**mirror URL（沿用 `update-geoip-cf.sh`）：**
`https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-{Country,City,ASN}.mmdb`

---

## 實作 Tasks

### Part 0 — 依賴
- [ ] `pom.xml` 加 `com.maxmind.db:maxmind-db`，**用 Java 8 相容版本 `2.1.0`**（3.x 需 Java 11，會編譯失敗）。
- [ ] `mvn clean package -DskipTests` 確認 Java 8 能編譯（若 2.1.0 不相容則退 `1.3.1`）。

### Part 1 — 後端：讀版本 + 下載（核心）
- [ ] 新增 DTO `model/GeoipDbInfo.java`（純 POJO）：`key`/`displayName`/`fileName`/`version`/`exists`/`sizeStr`/`lastUpdateAt`/`lastUpdateStr`/`scheduleStr`。
- [ ] 新增 `service/GeoipService.java`（`@Component` + `@Inject SettingService`）：`getDbInfos()`（記憶體快取版本）、`readBuildDate(File)`（maxmind-db Reader.getMetadata().getBuildDate() → yyyy.MM.dd，失敗回 null）、`download(key)`（Hutool 抓檔 → 寫 Setting updatedAt → 清快取，key=all 抓三個）；dir 容錯 mkdirs。
- [ ] 新增 `controller/adminPage/GeoipController.java`（`@Mapping("/adminPage/geoip")`）：`versions`(GET JsonResult)、`download`(POST db 參數)。

### Part 2 — Header 顯示版本（需求 1）
- [ ] `AppFilter.frontInterceptor()` 注入 `geoipService`，`ctx.attrSet("geoipDbInfos", geoipService.getDbInfos())`。
- [ ] `header.html` 右側 nav 加 `GeoIP` nav-item + `layui-nav-child` 下拉列三庫版本。

### Part 3 — 防護與憑證頁表格（需求 2）
- [ ] `ProtectionCertController.index()` 注入 `geoipService` + `put("geoipDbInfos", ...)`。
- [ ] `protectionCert/index.html` Tab 1 黑名單表格**之前**插入 GeoIP 表格(資料庫/版本/排程時間/下載按鈕),頁尾引入 `geoip.js`。
- [ ] 新增 `static/js/adminPage/protectionCert/geoip.js`：`downloadGeoip(db)` ajax POST → 成功 reload。

### Part 4 — i18n（三份同步）
- [ ] 三份 properties 加 `geoipStr.*`：title/database/version/schedule/scheduleValue/download/downloading/downloadSuccess/downloadFail/country/city/asn/lastUpdate/notDownloaded/headerLabel。

### Part 5 — Playwright（需求 4）
- [ ] `tests/e2e/23-geoip-version.spec.js`：A header 有 GeoIP item；B 防護頁 Tab1 表格 3 列+下載鈕+排程含 03:00；C 點下載觸發後端回應（外網下載 graceful，不斷言檔案成功）；API `versions` 回 country/city/asn。

---

## Verification
1. `mvn clean package -DskipTests`（先驗 maxmind-db Java 8 相容）。
2. 本地 jar + `--project.testCaptcha=1234`：header 下拉 + 防護頁表格（dev 無 mmdb 顯「尚未下載」）。
3. Docker（有 mmdb）：版本顯真實日期；手動下載成功刷新。
4. `npm run test:fast` 跑 23 號;`npm run report`。

## 風險 / 注意
- **maxmind-db 版本**：3.x 需 Java 11，務必 2.1.0（或 1.3.1）。
- dev/jar 無 mmdb：讀版本容錯回「尚未下載」，不可丟例外卡 header。
- 下載打外網：失敗要 graceful。
- nginx geoip2 `auto_reload 60m` 自動載新檔，本計畫不強制 reload。
- header 版本走記憶體快取，避免每請求讀檔。

## Out of scope
- 排程改 UI 可編輯（需搬到 Java @Scheduled）。
- Cloudflare real-IP 仍由 shell script。
- City 封鎖功能（本次只顯版本）。

## 影響檔案
**新增(5)：** `model/GeoipDbInfo.java`、`service/GeoipService.java`、`controller/adminPage/GeoipController.java`、`static/js/adminPage/protectionCert/geoip.js`、`tests/e2e/23-geoip-version.spec.js`
**修改(8)：** `pom.xml`、`config/AppFilter.java`、`controller/adminPage/ProtectionCertController.java`、`WEB-INF/view/adminPage/header.html`、`WEB-INF/view/adminPage/protectionCert/index.html`、`messages.properties`、`messages_zh_TW.properties`、`messages_en_US.properties`
