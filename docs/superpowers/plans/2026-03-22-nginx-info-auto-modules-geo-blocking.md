# Nginx 資訊顯示 + 自動載入模組 + 國家存取控制 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 三個功能：(1) Header 顯示 Nginx 版本與模組數量 (2) 自動偵測並載入 Nginx 模組取代手動管理 (3) 在黑白名單頁面新增國家存取控制 tab

**Architecture:**
- Nginx 版本與模組資訊透過 `nginx -v` 和掃描 `/usr/lib/nginx/modules/` 取得，在 header 顯示
- 自動模組載入：ConfService 生成 nginx.conf 時自動掃描可用模組，按依賴關係排序，跳過 Basic 表中的 load_module
- 國家存取控制：新增 GeoRule model，`map` 指令在 `buildConf()` 的 http block 層級生成，`if` 在 `bulidBlockServer()` 中加入 server block

**Tech Stack:** Java 8 + Solon 3.3.3, Layui + jQuery + Freemarker, SQLite/PostgreSQL (SqlHelper ORM), Playwright E2E testing

---

## File Structure

### Feature 1: Header Nginx 版本顯示

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `src/main/java/com/cym/service/NginxService.java` | getNginxVersion()、getAvailableModules()、getModulePaths() |
| Modify | `src/main/java/com/cym/controller/adminPage/MonitorController.java` | 新增 `/nginxInfo` API |
| Modify | `src/main/resources/WEB-INF/view/adminPage/header.html` | Nginx 版本顯示 + 模組彈窗 |
| Modify | `src/main/resources/messages.properties` | nginxStr.* i18n |
| Modify | `src/main/resources/messages_en_US.properties` | nginxStr.* i18n |
| Modify | `src/main/resources/messages_zh_TW.properties` | nginxStr.* i18n |

### Feature 2: 自動載入模組

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/main/java/com/cym/service/ConfService.java:93-99` | 自動生成 load_module，跳過 Basic 表的 load_module |
| Modify | `src/main/java/com/cym/config/InitConfig.java:119-127` | 移除 load_module 預設值 |

### Feature 3: 國家存取控制

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `src/main/java/com/cym/model/GeoRule.java` | mode, countries, serverId, enable |
| Create | `src/main/java/com/cym/controller/adminPage/GeoController.java` | CRUD API + 國家清單 |
| Modify | `src/main/java/com/cym/service/ConfService.java` | buildConf() 加 map block，bulidBlockServer() 加 if |
| Modify | `src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html` | Layui tab + 國家選擇 UI |
| Create | `src/main/resources/static/js/adminPage/denyAllow/geo.js` | 國家選擇互動邏輯 |
| Modify | `src/main/resources/messages*.properties` | geoStr.* i18n |

### Testing

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `tests/e2e/10-nginx-info.spec.js` | Nginx 版本 API + 基本參數頁面 |
| Create | `tests/e2e/11-geo-blocking.spec.js` | 國家存取控制 tab + API + 儲存 |

---

## Task 1: NginxService — 版本與模組偵測

**Files:**
- Create: `src/main/java/com/cym/service/NginxService.java`

- [ ] **Step 1: 建立 NginxService**

重點：
- `getNginxVersion()` — 執行 `nginx -v 2>&1` 取得版本號
- `getAvailableModules()` — 掃描 `/usr/lib/nginx/modules/*.so`，按依賴拓撲排序
- `getModulePaths()` — 回傳排序後的完整路徑清單（供 ConfService 使用）
- 依賴關係 map：`ngx_stream_geoip2_module` 依賴 `ngx_stream_module` 等
- 非 Linux 環境回傳空值/空清單（安全降級）

```java
package com.cym.service;

import java.io.File;
import java.util.*;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import com.cym.utils.SystemTool;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;

@Component
public class NginxService {
    @Inject
    SettingService settingService;

    private static final String MODULE_DIR = "/usr/lib/nginx/modules";

    // 依賴：key 需要 value 先載入
    private static final Map<String, String> DEPS = new LinkedHashMap<>();
    static {
        DEPS.put("ngx_stream_geoip2_module.so", "ngx_stream_module.so");
        DEPS.put("ngx_stream_geoip_module.so", "ngx_stream_module.so");
        DEPS.put("ngx_stream_js_module.so", "ngx_stream_module.so");
        DEPS.put("ngx_stream_keyval_module.so", "ngx_stream_module.so");
    }

    public String getNginxVersion() {
        if (!SystemTool.isLinux()) return null;
        try {
            String exe = settingService.get("nginxExe");
            if (StrUtil.isEmpty(exe)) exe = "nginx";
            String result = RuntimeUtil.execForStr("/bin/sh", "-c", exe + " -v 2>&1");
            if (StrUtil.isNotEmpty(result) && result.contains("/")) {
                return result.substring(result.lastIndexOf("/") + 1).trim();
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    public List<String> getAvailableModules() {
        if (!SystemTool.isLinux()) return new ArrayList<>();
        File dir = new File(MODULE_DIR);
        if (!dir.exists()) return new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".so"));
        if (files == null) return new ArrayList<>();
        List<String> modules = new ArrayList<>();
        for (File f : files) modules.add(f.getName());
        return sortByDependency(modules);
    }

    /** 回傳排序好的完整模組路徑 */
    public List<String> getModulePaths() {
        List<String> paths = new ArrayList<>();
        for (String m : getAvailableModules()) {
            paths.add(MODULE_DIR + "/" + m);
        }
        return paths;
    }

    /** 檢查是否有 GeoIP2 模組 */
    public boolean hasGeoIp2Module() {
        return getAvailableModules().contains("ngx_http_geoip2_module.so");
    }

    private List<String> sortByDependency(List<String> modules) {
        Set<String> set = new HashSet<>(modules);
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String m : modules) addWithDep(m, set, sorted, visited);
        return sorted;
    }

    private void addWithDep(String m, Set<String> avail, List<String> sorted, Set<String> visited) {
        if (visited.contains(m)) return;
        visited.add(m);
        String dep = DEPS.get(m);
        if (dep != null && avail.contains(dep)) addWithDep(dep, avail, sorted, visited);
        sorted.add(m);
    }
}
```

- [ ] **Step 2: MonitorController 加入 nginxInfo API**

在 `MonitorController.java` 注入 `NginxService`，新增：

```java
@Inject
NginxService nginxService;

@Mapping("nginxInfo")
public JsonResult nginxInfo() {
    Map<String, Object> info = new HashMap<>();
    info.put("version", nginxService.getNginxVersion());
    info.put("modules", nginxService.getAvailableModules());
    info.put("hasGeoIp2", nginxService.hasGeoIp2Module());
    return renderSuccess(info);
}
```

- [ ] **Step 3: 編譯** `mvn clean package -DskipTests` → BUILD SUCCESS

- [ ] **Step 4: Commit** `feat: add NginxService for version and module detection`

---

## Task 2: Header 顯示 Nginx 版本

**Files:**
- Modify: `src/main/resources/WEB-INF/view/adminPage/header.html`
- Modify: `src/main/resources/messages.properties`, `messages_en_US.properties`, `messages_zh_TW.properties`

- [ ] **Step 1: 加 i18n 字串**

三個 messages 檔加入 `nginxStr.*`（version、modules、moduleCount、moduleList）

- [ ] **Step 2: 修改 header.html**

在 nav 最前面加 `<li id="nginxVersionItem" style="display:none">`，點擊彈出模組清單。

底部加 JS：`$.get(ctx + '/adminPage/monitor/nginxInfo', ...)` 填入版本資訊，`showModuleList()` 用 `layer.open()` 顯示模組表格。

注意：i18n 變數在 Freemarker 模板中用 `${nginxStr.moduleCount}` 引用，嵌在 JS 字串裡。

- [ ] **Step 3: 編譯** → BUILD SUCCESS

- [ ] **Step 4: Commit** `feat: display Nginx version and module count in header`

---

## Task 3: 自動載入模組（ConfService 整合）

**Files:**
- Modify: `src/main/java/com/cym/service/ConfService.java:91-99`
- Modify: `src/main/java/com/cym/config/InitConfig.java:119-127`

- [ ] **Step 1: 修改 ConfService.buildConf()**

注入 NginxService。在 `NgxConfig ngxConfig = new NgxConfig();` 之後、讀取 Basic 之前：

```java
// 自動偵測模組並生成 load_module（Linux 環境）
if (SystemTool.isLinux()) {
    List<String> modulePaths = nginxService.getModulePaths();
    for (String path : modulePaths) {
        NgxParam ngxParam = new NgxParam();
        ngxParam.addValue("load_module " + path);
        ngxConfig.addEntry(ngxParam);
    }
}
```

修改 Basic 迴圈，跳過 load_module：

```java
List<Basic> basicList = sqlHelper.findAll(new Sort("seq", Direction.ASC), Basic.class);
for (Basic basic : basicList) {
    if ("load_module".equals(basic.getName().trim())) {
        continue; // 已由自動偵測處理
    }
    NgxParam ngxParam = new NgxParam();
    ngxParam.addValue(basic.getName().trim() + " " + basic.getValue().trim());
    ngxConfig.addEntry(ngxParam);
}
```

- [ ] **Step 2: 修改 InitConfig — 移除 load_module 預設值**

刪除 `if (count == 0)` 區塊中所有 `basics.add(new Basic("load_module", ...))` 行及其註解。

- [ ] **Step 3: 編譯** → BUILD SUCCESS

- [ ] **Step 4: Commit** `feat: auto-detect and load nginx modules with dependency sorting`

---

## Task 4: GeoRule Model

**Files:**
- Create: `src/main/java/com/cym/model/GeoRule.java`

- [ ] **Step 1: 建立 Model**

```java
@Table
public class GeoRule extends BaseModel {
    @InitValue("0")
    Integer mode;        // 0=允許(白名單), 1=封鎖(黑名單)
    String countries;    // 逗號分隔國家代碼 "TW,JP,US"
    String serverId;     // null=全域(http 層級)
    @InitValue("true")
    Boolean enable;
    // getters/setters, 無參建構子
}
```

- [ ] **Step 2: 編譯** → BUILD SUCCESS（SqlHelper 自動建表）

- [ ] **Step 3: Commit** `feat: add GeoRule model for country access control`

---

## Task 5: GeoController — API

**Files:**
- Create: `src/main/java/com/cym/controller/adminPage/GeoController.java`

- [ ] **Step 1: 建立 Controller**

API endpoints:
- `GET /adminPage/geo/list` — 取得所有 GeoRule
- `GET /adminPage/geo/detail?serverId=` — 取得特定 server 或全域的 GeoRule
- `POST /adminPage/geo/addOver` — 新增或更新
- `POST /adminPage/geo/del?id=` — 刪除
- `GET /adminPage/geo/countries` — 回傳按大洲分組的國家清單
- `GET /adminPage/geo/hasGeoIp2` — 回傳是否有 GeoIP2 模組

國家清單用靜態方法 `getCountryList()` 回傳 `List<Map>` 結構，按大洲分組（asia, europe, northAmerica, southAmerica, oceania, africa），每個國家有 code, nameZh, nameEn。

考慮將國家清單抽到 JSON 資源檔 `src/main/resources/countries.json` 以便維護（可選）。

注入 NginxService 提供 `hasGeoIp2Module()` 檢查。

- [ ] **Step 2: 編譯** → BUILD SUCCESS

- [ ] **Step 3: Commit** `feat: add GeoController for country access control API`

---

## Task 6: ConfService — 生成 map + if 指令

**Files:**
- Modify: `src/main/java/com/cym/service/ConfService.java`

- [ ] **Step 1: 在 buildConf() 的 http block 中生成 map block**

位置：在 `buildDenyAllow(ngxBlockHttp, "http", "http", confExt)` (line 119) 之後，添加 server 之前。

遍歷所有啟用的 GeoRule，為每個生成 map block：

```java
// 國家存取控制 — map 指令放在 http block
List<GeoRule> geoRules = sqlHelper.findAll(GeoRule.class);
for (GeoRule rule : geoRules) {
    if (rule.getEnable() == null || !rule.getEnable() || StrUtil.isEmpty(rule.getCountries())) {
        continue;
    }
    String mapVarName = "geo_block_" + (StrUtil.isEmpty(rule.getServerId()) ? "global" : rule.getServerId().replace("-", "").substring(0, Math.min(rule.getServerId().replace("-", "").length(), 12)));

    // 用 NgxBlock 生成 map block
    NgxBlock mapBlock = new NgxBlock();
    mapBlock.addValue("map $geoip2_data_country_code $" + mapVarName);

    String[] codes = rule.getCountries().split(",");
    NgxParam defaultParam = new NgxParam();
    if (rule.getMode() == 0) {
        // 白名單：預設封鎖
        defaultParam.addValue("default 1");
    } else {
        // 黑名單：預設允許
        defaultParam.addValue("default 0");
    }
    mapBlock.addEntry(defaultParam);

    for (String code : codes) {
        NgxParam codeParam = new NgxParam();
        if (rule.getMode() == 0) {
            codeParam.addValue(code.trim() + " 0");
        } else {
            codeParam.addValue(code.trim() + " 1");
        }
        mapBlock.addEntry(codeParam);
    }

    ngxBlockHttp.addEntry(mapBlock);
}
```

- [ ] **Step 2: 在 bulidBlockServer() 中加入 if 指令**

在 `bulidBlockServer()` 方法中（處理完 `buildDenyAllow` 之後），查詢該 server 的 GeoRule 並加入 if：

```java
// 國家存取控制 — if 指令放在 server block
ConditionAndWrapper geoCondition = new ConditionAndWrapper();
geoCondition.eq("serverId", server.getId()).eq("enable", true);
GeoRule geoRule = sqlHelper.findOneByQuery(geoCondition, GeoRule.class);
if (geoRule == null) {
    // 嘗試全域規則
    ConditionAndWrapper globalCondition = new ConditionAndWrapper();
    globalCondition.isNull("serverId").eq("enable", true);
    geoRule = sqlHelper.findOneByQuery(globalCondition, GeoRule.class);
}
if (geoRule != null && StrUtil.isNotEmpty(geoRule.getCountries())) {
    String mapVarName = "geo_block_" + (StrUtil.isEmpty(geoRule.getServerId()) ? "global" : geoRule.getServerId().replace("-", "").substring(0, Math.min(geoRule.getServerId().replace("-", "").length(), 12)));

    NgxBlock ifBlock = new NgxBlock();
    ifBlock.addValue("if ($" + mapVarName + " = 1)");
    NgxParam returnParam = new NgxParam();
    returnParam.addValue("return 403");
    ifBlock.addEntry(returnParam);
    ngxBlockServer.addEntry(ifBlock);
}
```

- [ ] **Step 3: 編譯** → BUILD SUCCESS

- [ ] **Step 4: Commit** `feat: generate GeoIP map/if directives in ConfService`

---

## Task 7: 黑白名單頁面 — 國家存取控制 Tab UI

**Files:**
- Modify: `src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html`
- Create: `src/main/resources/static/js/adminPage/denyAllow/geo.js`
- Modify: `src/main/resources/messages*.properties`

- [ ] **Step 1: 加 i18n 字串**

三個 messages 檔加入 `geoStr.*`（title, mode, allow, deny, countries, search, selected, selectAll, clearAll, asia, europe, northAmerica, southAmerica, oceania, africa, noGeoip, saved）

- [ ] **Step 2: 修改 denyAllow/index.html**

用 Layui `layui-tab layui-tab-brief` 包裝，兩個 tab：
- Tab 1: `${menuStr.denyAllow}` — 原有 IP 黑白名單（原封不動搬進第一個 tab-item）
- Tab 2: `${geoStr.title}` — 國家存取控制

國家 tab 內容：
- 模式 radio（允許/封鎖）
- 搜尋輸入框
- `layui-collapse` 折疊面板（按大洲分組，每組有全選按鈕）
- 已選國家標籤顯示區
- 提交/清除按鈕

底部引入 `geo.js`。

- [ ] **Step 3: 建立 geo.js**

關鍵邏輯：
- `loadGeoData()` — AJAX 載入國家清單 + 現有規則
- `renderCountries()` — 動態渲染折疊面板 + checkbox
- 搜尋過濾（`$('#geoSearch').on('input', ...)`）
- `selectContinent(key)` — 全選大洲
- `updateSelectedDisplay()` — 已選國家標籤（可點擊移除）
- `saveGeoRule()` — POST 到 `/adminPage/geo/addOver`
- `clearGeoRule()` — 清空選擇

注意：使用 `layui.use(['element', 'form'], ...)` 確保 Layui 元件可用。

GeoIP2 模組檢查：載入時呼叫 `/adminPage/geo/hasGeoIp2`，如果不可用則顯示提示訊息並禁用表單。

- [ ] **Step 4: 編譯** → BUILD SUCCESS

- [ ] **Step 5: 手動驗證**

1. 登入 → 黑白名單IP → 確認有兩個 tab
2. 切換到國家 tab → 選擇模式、勾選國家、提交
3. 啟用配置 → 校驗 → 確認 map 和 if 指令正確

- [ ] **Step 6: Commit** `feat: add country access control tab UI`

---

## Task 8: Playwright 測試

**Files:**
- Create: `tests/e2e/10-nginx-info.spec.js`
- Create: `tests/e2e/11-geo-blocking.spec.js`

- [ ] **Step 1: 撰寫 10-nginx-info.spec.js**

測試項目：
- `nginxInfo API 回傳正確結構`（version, modules, hasGeoIp2 欄位存在）
- `基本參數頁面可正常載入`（確認有 worker_processes, events）
- `啟用配置頁面可正常載入`

注意：API 呼叫使用完整 URL `BASE_URL + '/adminPage/monitor/nginxInfo'`

- [ ] **Step 2: 撰寫 11-geo-blocking.spec.js**

測試項目：
- `黑白名單頁面應有兩個 tab`
- `國家存取控制 tab 可正常切換`
- `國家 API 回傳正確結構`（有 asia continent，countries 有 code/nameZh）
- `可以儲存國家規則`

注意：Layui checkbox 操作用 `page.evaluate()`

- [ ] **Step 3: 執行測試** `npm test` → 全部通過

- [ ] **Step 4: Commit** `test: add Playwright tests for nginx info and geo blocking`

---

## Task 9: 最終整合測試

- [ ] **Step 1: 編譯並啟動 Docker** `mvn clean package -DskipTests && docker compose up -d --build`

- [ ] **Step 2: 手動整合驗證**

1. Header 是否顯示 Nginx 版本（如 `Nginx 1.28.0 (27 個模組)`）
2. 點擊版本是否彈出模組清單
3. 基本參數頁面的 load_module 行是否仍在但不影響配置生成
4. 啟用配置 → 校驗成功，load_module 順序正確
5. 黑白名單IP → 兩個 tab 正常切換
6. 國家存取控制 → 選台灣（白名單）→ 提交 → 啟用配置 → 校驗成功
7. 檢查 nginx.conf 有正確的 `map` 和 `if` block

- [ ] **Step 3: 執行全套 Playwright** `npm test` → 11 個測試檔案全部通過

- [ ] **Step 4: Commit** 針對遺漏的修正，逐一 commit

---

## 風險與注意事項

1. **ConfService 改動風險高** — Task 3 和 Task 6 修改核心配置生成邏輯。每次改動後都要 `docker exec` 校驗 nginx.conf，確保現有功能不受影響。

2. **map 指令用 NgxBlock 生成** — nginx parser 的 `NgxBlock` 用於 block 指令（server, upstream, map 等），`NgxParam` 用於 flat 指令。map 是 block 所以必須用 `NgxBlock`。

3. **GeoIP2 依賴** — 國家存取控制需要 `ngx_http_geoip2_module.so` 和 GeoLite2-Country.mmdb。前端載入時呼叫 `hasGeoIp2` API 檢查，不可用時顯示提示並禁用表單。

4. **serverId 處理** — map 變數名用 serverId 的前 12 碼（移除 `-`），需要用 `Math.min()` 防止 substring 溢出。

5. **現有 load_module 數據** — ConfService 跳過 Basic 表的 load_module，舊數據不需刪除，不影響功能。用戶可在基本參數頁面手動刪除。

6. **測試環境差異** — Playwright 跑在 Windows（localhost:18080），自動模組偵測和 GeoIP2 檢查只在 Linux 有效。測試 API 回傳值時需考慮 null/empty 情況。

7. **Layui tab 不影響現有功能** — IP 黑白名單的表單和 JS 邏輯完全不動，只是用 tab 容器包裝。測試時確認原有 IP 黑白名單功能正常。
