# HTTP 參數 Panel — module availability filter 設計

> phase 3 剩餘 backlog 子項「module availability filter」設計 spec。
> 日期:2026-07-05 · 基礎:dev @ 4757ff8c(phase 2/3 已 merged)

## 目標
server「設置 http 參數」panel 中,依 nginx 實際載入的 module,對「需要特定 module 才能運作」的 http 指令(geoip、brotli)做偵測:偵測不到必要 module 時**紅字/紅 badge 警示提醒安裝並引導去基礎設定頁**,而非當可選項灰掉停用。偵測須**同時支援動態 .so 與靜態 compiled-in**,呈現須**接續既有基礎設定頁的 module UI 保持一致**。

## 背景與動機
- GeoIP(國家/城市/ASN 封鎖)是本 fork 這幾個月的**核心防禦 feature**,geoip2 module 為必備。
- elf-express Docker image 已內建 24 個 nginx module(geoip、geoip2、brotli、stream 系列…),都是刻意安裝、產品都要用到的 —— **不是可有可無的可選項**。
- nginx module 兩種安裝形態:**動態(.so + load_module)** 與 **靜態(compiled-in,`./configure --add-module`,無 .so)**。現有只偵測動態 .so → 遇 static 誤報未裝。

## 範圍
http panel 中需要額外 module 的指令群組只有兩個:

| group | 需要的 module | 類別 |
|---|---|---|
| geoip | geoip2 (ngx_http_geoip2_module) | required(必要) |
| brotli | brotli (ngx_http_brotli filter/static) | required(必要) |

其餘 group(base/realip/gzip/headers/proxy/logging)用 nginx 內建,不需額外 module,不受影響。

## 現有 module 機制全貌(調查結果)
| 層 | 內容 | 盲點 |
|---|---|---|
| 資料模型 | `Module` table(name=.so、descrKey、enable、seq),註解「Nginx 動態模組管理」;InitConfig seed geoip2/brotli 等 .so | 只存 .so |
| 偵測 | NginxService:`SAFE_MODULES` + `DEPENDENCY_MAP`;`getAllModules`/`getEnabledModulePaths`/`hasGeoIp2Module`;`getNginxVersion` 用 `nginx -v`(無 -V) | 全動態 .so,零 static |
| 呈現①基礎設定頁 | `/adminPage/basic`(`BasicController.index`)傳 `moduleList`+`modulesOnDisk`+`isLinux`;`basic/index.html` **`<#if isLinux!false>`** 包住 Layui `moduleTable`(模組名/說明/狀態);磁碟有→`switch`、磁碟無→灰 badge「磁碟上未找到」/「N/A」;`basic/index.js` switch→`setModuleEnable` + `MODULE_DEPS` 依賴自動;描述用 `moduleStr[descrKey]`(已有 `descrHttpGeoip2`/`descrBrotliFilter`/`descrBrotliStatic`) | 只列 .so |
| 呈現②Geo/Header | Geo 頁 + Header `nginxInfo` 用 `hasGeoIp2` | 同盲點 |

## 設計

### 偵測 —— 雙軌(動態 .so OR 靜態 compiled-in),只 Linux 有效
- **動態(.so)**:`getEnabledModulePaths()`(DB Module 啟用 + MODULE_DIR 磁碟存在)含該 module 名。
- **靜態(compiled-in)**:`nginx -V 2>&1` 的 configure arguments 含該 module 名(`--add-module`/`--add-dynamic-module`/`--with-http_xxx_module`)。static 編譯進 binary 者無 .so,只能這樣看。
- 新增 `NginxService.hasModule(String keyword)`:動態命中 OR 靜態命中 → true(非 Linux 回 false)。
- 新增 `NginxService.getNginxConfigureArgs()`:跑 `nginxExe + " -V 2>&1"` 拿輸出(Linux only)。
- `hasBrotliModule()` = `hasModule("brotli")`。
- keyword(plan 定案):geoip 用 `"geoip2"`、brotli 用 `"brotli"`;寧可誤判「有」也不誤判「沒有」。

### 順修現有 hasGeoIp2Module static 誤報 bug
現有 `hasGeoIp2Module()` 只認 .so → geoip2 若 static 編譯進 nginx 會誤報未裝。改用雙軌 `hasModule("geoip2")` 一併修好。
- blast radius:2 caller(`MonitorController.nginxInfo`、`GeoController.hasGeoIp2`),都讀取,改雙軌只會更準,不破壞。

### 非 Linux fallback(對齊既有樣板)
- 非 Linux(Windows dev / E2E)一律視為「已裝」、不警示 —— 避免誤報。
- 實作對齊基礎設定頁:panel 警示用 **`<#if isLinux>`** 包住(基礎設定頁 module 區塊就是 `<#if isLinux!false>`),非 Linux 整段不 render;ServerController 也僅 Linux 才算 `missingRequiredModules`,雙保險。

### 前端呈現(panel)—— 接續既有 module UI
- ServerController 傳入 `missingRequiredModules`(List<String>,group name;非 Linux 為空)+ `isLinux`。
- panel 中若 `isLinux` 且某 group 在 `missingRequiredModules`:於該 group 標題旁顯示**紅色 Layui badge**「必要·未偵測」(沿用基礎設定頁 badge 元件,但紅色強調「必要」,有別於一般 module 的灰 badge「磁碟上未找到」)。
- badge 旁**引導文字**指向基礎設定頁(`/adminPage/basic`):「請至基礎設定啟用或確認已安裝」,形成 panel 警示 → 基礎設定開關的動線。
- **不灰、不 disable** checkbox —— 指令保持可勾可用。

### 後端(無 enforce)
- 都是必要 module、不 disable,`saveEnable` 不改。本功能 = 雙軌偵測 + 前端警示(接續既有 UI)+ 順修一個既有 bug。

## 資料流
```
ServerController.index()
  → SystemTool.isLinux()? (hasGeoIp2Module / hasBrotliModule 各自雙軌判定) : 空
  → missingRequiredModules(List<String>) + isLinux → panel
server/index.html panel
  → <#if isLinux && missingRequiredModules?seq_contains(group)> 紅 badge + 引導 </#if>
hasModule(keyword): Linux? (getEnabledModulePaths 含 keyword) OR (nginx -V 2>&1 含 keyword) : false
```

## i18n(3 檔同步)
- `serverStr.httpParamModuleMissing` =「必要·未偵測」(badge 文字,簡/繁/英)。
- `serverStr.httpParamModuleMissingTip` =「此為必要 module,未偵測到 —— 請至基礎設定啟用或確認已安裝」(引導,簡/繁/英)。
- 細節 plan 定案(是否合併單一 key)。

## 測試(E2E)
- Windows(非 Linux)→ fallback → **geoip/brotli 都不應出現 badge 警示、checkbox 不灰**。
- 新增 spec(推測 31):非 Linux fallback:panel 開啟,geoip/brotli group 無 module badge、checkbox 非 disabled。
- **可測性限制**:E2E 跑 Windows,測不到 Linux「偵測不到 → 紅 badge」正向路徑、也測不到雙軌(.so / nginx -V)邏輯;該部分靠 code review + 邏輯正確性保障。

## 非目標(YAGNI)
- 不做 stream 段 module(另一子項)。
- 不做「一鍵安裝 module」。
- 不改 saveEnable / 不做 enforce disable。
- 不動 phase 3 三態(locked/mutex)邏輯。
- 不改基礎設定頁 module 管理(僅管動態 .so 屬合理)。

## 元件清單
| 檔案 | 變更 |
|---|---|
| NginxService.java | 新增 `hasModule(keyword)`(雙軌:.so OR nginx -V)+ `getNginxConfigureArgs()`;新增 `hasBrotliModule()`=hasModule("brotli");改 `hasGeoIp2Module()`=hasModule("geoip2")(順修 static bug) |
| ServerController.java | index() 算 `missingRequiredModules`(Linux 才判定)+ `isLinux` 傳 panel |
| server/index.html | panel group 標題旁 `<#if isLinux && ...>` 紅 badge + 引導(接續既有 badge 樣式) |
| messages*.properties ×3 | 新增 module 警示 badge + 引導 i18n key |
| tests/e2e/31-*.spec.js | 非 Linux fallback E2E |
