# nginx 文件 MCP 設計

日期：2026-08-12
狀態：設計定案，待實作

## 目標

把 `docs/nginxdocumentation/` 的 150 頁 nginx 官方文件，變成 nginxWebUI 內建的 MCP server，
讓 AI 客戶端能精準查詢指令定義、反查某個 context 能用哪些指令，並拿設定草稿對照官方文件做檢查。

全部工具唯讀、純文字分析，不碰檔案系統也不碰資料庫。

## 為什麼可行：資料是結構化的

這不是「把一堆 markdown 丟給全文搜尋」。nginx.org 每個指令都有固定格式的定義表格，
全語料 **947 個，格式零變異**（947/947 都有 Syntax／Default／Context 三個欄位）：

```html
<tr><th>Syntax:</th><td><code><strong>aio</strong> on | off | threads[=pool];</code></td></tr>
<tr><th>Default:</th><td><pre>aio off;</pre></td></tr>
<tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code></td></tr>
```

指令名直接包在 `<strong>` 裡，模組名在頁面標題（`## Module ngx\_http\_acme\_module`），
官方連結在檔頭（`> Source:`）。所以可以建出**精準索引**——問「`proxy_pass` 能用在哪些 context」
會得到確切答案，而不是相似度猜測。

其中 359 個指令的 Default 是 `—`（無預設值），這是唯一需要特別處理的形態。

context 分佈（第一個值）：http 587 · stream 146 · mail 47 · server 32 · main 30 · location 29 · upstream 26 · 其餘零星。

## 架構

```
docs/nginxdocumentation/*.md   →  打包進 jar 的 resources
            ↓ 啟動時解析一次
      NginxDocIndex（記憶體）
        directives  Map<name, NginxDirective>   947 筆
        byModule    Map<module, List<name>>
        byContext   Map<context, List<name>>    反查用，啟動時建好
        pages       List<DocPage>               全文搜尋用
            ↓
      NginxDocService（@Component）
            ↓
      NginxDocMcpServer（@McpServerEndpoint）
```

**選擇「啟動時解析 md」而非「建置期預解析成 JSON」**：md 是單一真實來源，不可能不同步。
預解析的 JSON 一旦忘了重跑就會悄悄過期，而過期的索引會讓 AI 引用錯誤的指令定義——
那正是這整件事要避免的。啟動成本估計數百毫秒，jar 從 42.6 MB 增至約 44.6 MB（+5%）。
這也與專案既有做法一致（GeoIP 的 mmdb 同樣烤進 image）。

### 檔案與責任

| 檔案 | 責任 |
|---|---|
| `model/NginxDirective.java` | 資料模型，無邏輯 |
| `utils/NginxDocParser.java` | md → NginxDirective。純函式，不依賴 Solon，可單獨測 |
| `service/NginxDocService.java` | 建索引與查詢，`@Component` |
| `mcp/NginxDocMcpServer.java` | MCP 協定門面，`@ToolMapping` 方法一律不超過 20 行 |

分層的理由是可測試性：Parser 是純函式，能直接對 947 個真實表格跑單元測試而不必啟動 Solon。
MCP 類只做參數描述與結果格式化，日後加「操作 nginxWebUI」那組工具時不會與現有工具糾纏。

### Parser 用內容特徵判斷，不用檔名

```
有 <table cellspacing="0"> 且 > Source: 指向 nginx.org   →  解析成 directive 索引
其他 md                                                   →  只進全文搜尋
```

這條規則讓專案自寫的文件（例如未來的 IP 管理說明）可以被 MCP 搜尋到，
但**不會污染指令索引**——`nginx_directive("proxy_pass")` 永遠只回官方定義。
新增任何一種文件都不需要改 Parser。

## 五個工具

全部唯讀。回傳一律是給 AI 讀的純文字，附官方連結供追溯。

### 1. `nginx_directive(name)`

單一指令的完整定義。

輸入 `proxy_pass`，回傳 Syntax、Default（無預設值時明說「無」而非留白）、
可用的 Context 清單、所屬模組、說明段落、官方連結。

**查無此指令時回傳候選建議**，而不是空結果——指令名很長又容易拼錯
（`proxy_read_timeout` vs `proxy_send_timeout`），建議清單比「找不到」有用得多。
候選以「前綴相符」與「編輯距離 ≤ 2」取聯集，最多 8 筆。

### 2. `nginx_search(query, limit=10)`

全文搜尋，回傳命中片段與出處（頁面標題 + 官方連結）。
用於「我想做 X，該用什麼指令」這種還不知道指令名的問題。

### 3. `nginx_module(name)`

列出某模組的所有指令（名稱 + 單行 Syntax）。

接受完整名稱 `ngx_http_proxy_module`，也接受簡寫。簡寫若對應到多個模組
（`proxy` 同時符合 http／stream／mail 三個），**回傳候選清單要求指定**，
而不是猜一個回傳——猜錯會讓 AI 拿 stream 的指令去寫 http 設定。
唯一相符時才直接回傳該模組內容。

### 4. `nginx_context(context)`

**反查**：某個 context 裡能用哪些指令。輸入 `location`，回傳所有 Context 含 location 的指令。

這是寫設定時最常問的問題，也是全文搜尋做不到的事——它需要的是結構化索引。
`byContext` 在啟動時建好，查詢是 O(1)。

### 5. `nginx_check_config(conf)`

把一段 conf 文字（呼叫方貼進來的，不是讀現行設定）對照官方文件檢查：

- **指令是否存在** — 抓拼字錯誤
- **是否用在合法 context** — 例如 `if` 寫在 `http` 層、stream 區塊裡用了 HTTP-only 指令
- 回報格式：`行號 · 指令 · 問題 · 官方連結`

context 判斷需要追蹤區塊巢狀。**優先嘗試專案既有的 `com.github.odiszapc:nginxparser` 依賴**
（pom.xml 已有），若其 API 不適用，退回只追蹤 `{` `}` 與區塊關鍵字
（http/server/location/upstream/stream/events/mail）的簡易掃描——判斷 context 只需要知道
「目前在哪一層」，不需要完整語法樹。

檢查器只回報**能確定的問題**。無法判斷的一律不報——誤報會讓使用者不信任整個工具。

## 認證與部署

沿用最初 brainstorming 的定案：**`--mcp.token` 啟動參數，opt-in**。

- 沒設 token → 完全不註冊 `/mcp` 端點。既有部署升級後行為零變化。
- 有設 → `AppFilter` 對 `/mcp` 前綴檢查 `Authorization: Bearer <token>`。
- Docker 走 `.env` → `BOOT_OPTIONS`，與現有的參數傳遞方式一致。

不新增容器、不新增 port——nginxWebUI 現有的 12300 直接多一條 `/mcp`。

### 專案級 `.mcp.json`（進版控）

```json
{
  "mcpServers": {
    "nginx-docs": {
      "type": "http",
      "url": "http://localhost:12300/mcp",
      "headers": { "Authorization": "Bearer ${NGINX_WEBUI_MCP_TOKEN}" }
    }
  }
}
```

token 走環境變數，不進版控。

**codegraph 不放進來**：它需要 `.codegraph/` 索引，而該目錄在 `.gitignore` 第 69 行被排除，
是每個人自己建的本機產物。註冊在專案級會讓沒建索引的人一 clone 就遇到查不出原因的連線失敗。
CLAUDE.md 自己也寫明「沒有 `.codegraph/` 就整個跳過——建索引是使用者的決定」。
它維持個人全域設定即可。

## 錯誤處理

MCP 工具**永不向外拋例外**，一律回傳可讀的錯誤字串——協定層的例外對 AI 來說是不透明的失敗。

| 情況 | 行為 |
|---|---|
| 指令查無 | 回傳候選建議清單 |
| 模組／context 查無 | 回傳該類別的有效值清單 |
| 搜尋無命中 | 明說無命中，並建議改用 `nginx_search` 換關鍵字 |
| conf 解析失敗 | 回報解析失敗的行號，不猜測 |
| 索引未建立（理論上不會發生） | 明確錯誤而非空結果 |

## 測試

| 層 | 方式 |
|---|---|
| Parser | 單元測試，對真實語料的 947 個表格跑一次，斷言解析出 947 筆且無欄位遺失；另測 `—` 預設值與跳脫底線（`ngx\_http\_proxy\_module`）兩個已知形態 |
| Service | 單元測試：指令查詢、候選建議、context 反查、模組簡寫 |
| check_config | 單元測試：合法設定不誤報、`if` 放錯層要抓到、拼錯指令要抓到 |
| MCP 端點 | Playwright E2E（`tests/e2e/NN-mcp.spec.js`）直接對 `/mcp` 打 JSON-RPC，驗證 `initialize`／`tools/list`／`tools/call`，以及**沒設 token 時端點不存在** |

沿用專案既有的 `node --test`（單元）與 Playwright（E2E）兩套，不引入新框架。

## 不做的事（YAGNI）

- **不做向量檢索**。947 個結構化指令加全文搜尋已足夠，嵌入模型會帶來模型下載、
  版本管理與離線部署問題，與專案「離線可用」的原則衝突。
- **不做操作類工具**（讀寫 server/location、跑 `nginx -t`、reload）。那是最初定案的第二項，
  範圍與風險都不同（需要寫入白名單與回滾），留待下一輪。
- **不做 UI 查詢入口**。先確認 MCP 這條路好用，再決定要不要多一套 UI ＋ 三份 i18n ＋ Playwright。

## 未竟事項

實作前需驗證：`com.github.odiszapc:nginxparser` 的 API 是否適合做 context 追蹤。
若不適合，退回簡易括號掃描（已在上文說明），不阻擋設計。
