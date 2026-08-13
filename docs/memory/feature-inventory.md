# Feature inventory / 功能盤點

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。這是「這個 fork 相對上游多了什麼」的清單。

**UI/UX：** 批量參數輸入 · TLS 預設值修正 · conf 縮排 + CodeMirror 語法highlight · 登入密碼顯示切換 ·
預設 http 參數／模板 · HTTP 參數分組（`HttpController.GROUP_DEFS`）· 模板分組 ·
**模板自動套用多選標籤**（`Template.def` 經 `TemplateDefUtils`：`http`/`server`/`server1`/`server2`/`stream`/`location`/`upstream`）·
IP/DenyAllow 標籤化 · 編輯模式 · conf 錯誤診斷 · 語言切換（國旗 SVG）· 品牌 logo 上傳 + header 200×60 對齊 ·
HTTP 參數面板 phase 2/3：三態啟用模式 + nginx 模組可用性過濾（specs 28–31；5.2.7 起面板移至 http 參數配置頁 ——
全域設定歸全域頁，server 精靈只留逐站步驟①Location ②server 參數）。

**Accessibility（Wave 1/2 稽核，持續中）：** 全站偽連結 `<a href="javascript:">` → `<button>` 遷移
（header、sidebar、表格操作、modal、captcha）· 語意地標（`<nav>` sidebar、關鍵頁 `<h1>`）· icon 按鈕 `aria-label`。
由 specs 27-a11y-buttons 加上 crawler 式斷言把關。

**Security：** CrowdSec（IDS + bouncer）· GeoIP2 國家封鎖 ·
**ASN 大區段封鎖走 CrowdSec range**（ipverse as-metadata catalog `AsMeta` + as-ip-blocks prefix 抓取；
防護 profile `light`/`manual`/`strict` 存在 `protection.profile`；intent `AsBlockIntent` 的 reason 是
`nginxwebui:as-ban:AS{n}`；AsnRule 的 nginx `$blocked_asn` map 已棄用，只能透過 `asn.nginxMapEnabled` opt-in）·
Protection Cert · Real-IP 模組 ·
**DenyAllow 黑／白名單 —— 全站自動生效**（`type` deny/allow，`@InitValue("deny")`；
種入 6 條惡意 IP feed 規則 + 每日 URL 更新 + 非同步首次抓取；規則透過 `ConfService.buildDenyAllow`
在 http/stream 層自動套用 —— allow 先於 deny、預設 allow、**沒有 per-server 綁定**
（舊綁定 UI 已於 5.2.6 移除，Settings/欄位保留但不讀）；存檔時拒絕跨型別 IP 衝突；
CrowdSec 誤判白名單可選擇同步到 DenyAllow allow）·
**防火牆頁 = 6 個 tab**（IP 資料庫／黑名單／白名單／GeoIP 國家／ASN／Protection Cert）。

**GeoIP DB 模組（v5.2.0+）：** header 以 2×2 grid 顯示 Country/City/ASN/Cloudflare 狀態
（4 列直排會撐破 60px header）（`GeoipService` 透過 maxmind-db；build-date 以檔案 mtime 為 key 快取 ——
避免每個 request 重讀 ~80MB mmdb）· ProtectionCert Tab-1 的 IP 資料庫表格
（版本／排程／手動下載／狀態交叉驗證：`GeoipService.evaluateStatus` + `reverifyAll` + 每檔的 stat/status 欄位）·
**Cloudflare Real-IP 自動下載**（`/adminPage/geoip/downloadCloudflare` → `realip.conf`，同表格內有 Cloudflare 狀態列）·
`GeoipController` 的 `/adminPage/geoip/{versions,download,downloadCloudflare,…}` · Java/Hutool 下載（jar + Docker）。

**nginx 模組（Docker slim）：** ~31 個動態模組；`MODULE_CATALOG` 決定載入順序；
已剔除無維護／高風險模組（fair、舊 geoip、perl、upload*…）。
Stream 連線數限制模板用 zone 名 `s_conn_perip`（不可與 HTTP 的 `conn_limit` 撞名）。

**Monitoring/Ops：** nginx 模組自動偵測（`/adminPage/monitor/nginxInfo`）· Site Resource · 連通性測試。

**nginx docs MCP：** 見 [mcp.md](mcp.md)。

**Deploy/Test：** 測試用驗證碼 · Compose stack（PG18 + CrowdSec）·
**CrowdSec = 自建 `nginxwebui-crowdsec`（官方 base + 烤 config）** · 可選的 `security` profile ·
**master 觸發發版：CI 版本閘控建 2 個 image（nginxwebui + nginxwebui-crowdsec，amd64）+ 自動打 tag + 自動 GitHub Release**
（細節見 [release-flow.md](release-flow.md)）· **geoip MMDB 在建置期烤進 image（離線可用）** ·
`.gitattributes` LF · Playwright E2E 套件（離線 CDN 守衛 + a11y crawler）·
`@claude` mention 回應器（[.github/workflows/claude.yml](../../.github/workflows/claude.yml)）·
**存檔路徑強化（5.2.6）：** `nginx -t` precheck 15 秒逾時 + 無法執行／逾時→SKIPPED 不回滾（修死鎖）·
realip.conf 啟動 placeholder · ORM 綁定正規化（Boolean→'1'/'0' + 啟動 migration）+ DML SQLException 不再靜默 ·
geoip2+map 變數多時自動補 http 的 `variables_hash_max_size`/`variables_hash_bucket_size` 預設。
