# 防火牆管理 — IP 資料庫 tab 重構 + GeoIP 交叉驗證 + 黑白名單拆分

日期:2026-07-10
來源:使用者 mockup(防火牆管理系統 — 分頁 UI 規格)+ brainstorming 對齊
狀態:待 review

## 1. 背景與動機

兩個實際痛點:
1. **GeoIP 版本號不可信** — `GeoipService` 用 `versionCache` 記憶體快取版本,只在「手動下載」時清([GeoipService.java:121-130](../../../src/main/java/com/cym/service/GeoipService.java#L121))。當排程 / cron(update-geoip-cf.sh / ScheduleTask)在背景更新 mmdb,快取沒清 → UI 顯示舊版本(顯示 07.04、實際 07.07),無從驗證。
2. **黑白名單分不清** — `黑白名單IP` tab 把黑名單、白名單混在一起,術語不直觀,使用者選不對。

## 2. 目標

1. tab 從 4 個變 6 個:`IP資料庫 | 黑名單 | 白名單 | 國家存取控制 | ASN 封鎖 | 證書管理`。
2. IP資料庫 tab 只留 GeoIP/IP 資料庫狀態,版本**即時讀取不快取**,並加**檔案 stat + 交叉驗證 + Cloudflare 列**。
3. DenyAllow 名單加「黑/白類型」,黑名單、白名單各自獨立 tab 管理。

**非目標**:不碰 nginx.conf 生成邏輯(全是讀取 + 加欄位);不動 GeoRule/ASN/證書分頁的既有功能。

## 3. 詳細設計

### 3.1 後端 — GeoipService(治本 + 擴充)
- **移除 `versionCache`**:`getDbInfos` 每次即時 `readBuildDate(f)`,不再快取(治本,解決顯示舊版本)。若擔心效能,mmdb metadata 讀取極快(FileMode.MEMORY 只讀 header),可接受。
- **每列加欄位**:`filePath`(絕對路徑)、`lastModifiedAt`(`f.lastModified()`)、`lastModifiedStr`、`status`、`statusReasons`。
- **新增 Cloudflare 列**:讀 realip.conf 的 stat(存在/大小/最後修改),`displayName`=「Cloudflare IP 清單」,`filePath` 顯示實際 realip.conf 路徑,無 mmdb build date(version=null,不套規則②③)。手動更新 = 觸發 update-geoip-cf.sh(或呼叫既有排程邏輯)。
- **realip.conf 路徑單一來源**:GeoipService 讀取的 realip.conf 路徑,與 update-geoip-cf.sh 的輸出路徑,取自**同一設定來源**(共用 constant/config,如 `GEOIP_DIR + "/realip.conf"`),不在兩處各自硬編,避免路徑漂移。

### 3.2 後端 — 交叉驗證判定(距今基準,使用者定案)
`GeoipDbInfo` 加 `status`("ok" | "warn")+ `statusReasons`(List<String>)。每列判定:
- **規則 ①(mmdb + Cloudflare 通用)**:`檔案最後修改距今 > 7 天` → warn,原因「檔案已 N 天未更新,排程可能未執行」。
- **規則 ②(僅 mmdb)**:`build date 距今 > 14 天` → warn,原因「資料建置日期為 N 天前,可能下載到舊資料」。
- **規則 ③(僅 mmdb)**:`build date 讀取失敗(null 且檔案存在)` → warn,原因「無法讀取版本,檔案可能損壞」。
- 無觸發 → status=ok。多規則同時觸發 → `statusReasons` 收集全部。
- 「距今」以伺服器當下時間為基準(非兩日期互比)。
- **判定抽成純函式** `evaluateStatus(lastModifiedAt, buildDate, now, isCloudflare)` → `(status, reasons)`,不碰 IO,`now` 由參數注入 → 單元測試可餵固定時間測邊界值(§3.5)。

### 3.3 後端 — DenyAllow 加類型
- `DenyAllow` model 加 `String type`(值:`deny` / `allow`),`@InitValue("deny")`。
- **Migration(反查引用自動歸類)**:對每個現有名單反查它被哪些 Server 引用 —— 被 `allowId` 引用 → `allow`(白名單)、被 `denyId` 引用 → `deny`(黑名單)、未被引用 → 預設 `deny`。同時被 allowId 與 denyId 引用(跨 server 矛盾)→ 歸 `deny` 並 log 警告。於 InitConfig 一次性執行,使用者零手動。
- **新增/編輯衝突檢查**:名單存檔前檢查其每個 IP/CIDR 是否已存在於「另一 type」的名單;有衝突則提示使用者(列出衝突 IP 與所在名單),**不得靜默建立**。
- `DenyAllowController` 列表接受 `type` 過濾參數;新增/編輯時帶 type + 跑衝突檢查。
- 反向代理引用:黑名單(type=deny)供 `denyId` 選、白名單(type=allow)供 `allowId` 選(引用端下拉依 type 過濾;不改 conf 生成)。

### 3.4 前端 — protectionCert/index.html
- **6 個頂層 tab**:原 tab-1「黑白名單IP」改名「IP資料庫」、內容只留 GeoIP 表格;新增「黑名單」「白名單」兩 tab(重用現有 DenyAllow 名單 UI,各自 type 過濾);國家/ASN/證書不動。
- **IP資料庫表格**照 mockup:`資料庫 | 內部建置日期(mmdb metadata) | 檔案路徑 | 檔案大小 | 檔案最後修改 | 更新方式 | 狀態 | 操作`。狀態欄:正常(綠)/待確認(橙)+ 待確認時列出 `statusReasons` 全部原因文字。操作:每列「手動更新」+ 頂部「重新驗證全部」(重新拉 versions JSON)。
- `GeoipController /versions` 回傳擴充後的 `GeoipDbInfo`(含新欄位),前端動態刷新。

### 3.5 i18n + 測試
- 新字串同步三份 `messages*.properties`(繁/簡/英):tab 名(IP資料庫/黑名單/白名單)、新欄位表頭、狀態(正常/待確認)、三條原因文字模板、Cloudflare 列名。CJK 用 `\uXXXX`。
- **判定邏輯(§3.2 三條規則)用單元測試(solon-test/JUnit)覆蓋邊界值**:對純函式 `evaluateStatus` 餵固定 `now`,測檔案最後修改 6/7/8 天、build date 13/14/15 天、build date null(檔案存在)、Cloudflare 列(無 build date 只套規則①)、多規則同時觸發。邊界值精準覆蓋由單元測試負責。
- Playwright E2E **只驗 UI 呈現**:① 6 個 tab 存在 ② IP資料庫表格有 Cloudflare 列 + 新欄位 ③ 黑名單/白名單 tab 各自過濾 ④ 狀態為待確認時**顯示原因文字**(判定邏輯本身不在 E2E 重測)。

## 4. 改動檔案清單

| 檔案 | 動作 |
|---|---|
| `service/GeoipService.java` | 移除快取即時讀;加 stat/status 欄;加 Cloudflare 列 |
| `ext/GeoipDbInfo.java` | 加 filePath/lastModifiedStr/status/statusReasons |
| `model/DenyAllow.java` | 加 type 欄(@InitValue "deny") |
| `controller/adminPage/DenyAllowController.java` | 列表依 type 過濾;存檔前黑白衝突檢查 |
| `config/InitConfig.java` | 現有名單 type migration(反查 allowId/denyId 引用自動歸類) |
| `WEB-INF/view/adminPage/protectionCert/index.html` | 6 tab + 表格擴充 + 黑白拆分 |
| `static/js/adminPage/protectionCert/*.js` | tab/表格/黑白名單 JS |
| `messages*.properties` ×3 | 新 i18n |
| `src/test/java/com/cym/service/GeoipStatusTest.java` | 判定邏輯單元測試(邊界值 7/14 天、null) |
| `tests/e2e/NN-*.spec.js` | 新 E2E(只驗 UI 呈現) |

## 5. 風險與回退
- 全是讀取 + 加欄位 + UI 重排,**不碰 conf 生成**,風險低。
- 移除 versionCache:每次讀 mmdb metadata,效能影響極小(header-only)。
- type migration:現有名單一律歸黑名單,若使用者原本把某清單當白名單引用(allowId),需手動改該名單 type 為 allow — spec 註記,實作時於 release note 提醒。
- 回退:純新增/UI 改動,git revert 即還原。

## 6. 實作方式
- Spec review 通過後,**依 `superpowers:using-git-worktrees` 建立獨立 worktree**,不直接改主工作區。
- worktree 內走 TDD + code review + verification,完成後再整合。
