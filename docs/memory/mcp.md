# nginx docs MCP server

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。
> 合併後的待辦見 [docs/superpowers/plans/2026-08-13-nginx-doc-mcp-followups.md](../superpowers/plans/2026-08-13-nginx-doc-mcp-followups.md)。

把 `docs/nginxdocumentation/` 的 150 頁 nginx 官方文件變成內建的 MCP server，
讓 AI 客戶端查得到真正的指令定義，而不是自己編一個出來。

## 規模與組成

**969 條指令定義 / 803 個相異名稱 / 99 個模組 / 15 個 context**，啟動時從打包進 jar 的
markdown 建索引（`NginxDocService.loadFromClasspath`，由 `NginxDocParser` 解析）。

其中 947 條來自 nginx.org 的 HTML 表格，22 條來自本專案手寫的 zh-TW 摘要頁
（7 頁官方模組頁沒有表格，正好涵蓋 nginxWebUI 自己會產生的設定：Real-IP、rate limit、
連線數限制、allow/deny、map）。`NginxDirective.origin()` 區分兩者，因為
**`defaultValue == null` 的語意依來源而不同**：`OFFICIAL_TABLE` 是「官方寫 —，確實沒有預設值」，
`PROJECT_SUMMARY` 是「摘要頁沒列出」。混用會讓 MCP 對 `limit_req_status` 說「無預設值」，
而它實際預設 `503` —— 對 AI 講錯 nginx 語意，正是這個功能存在的理由要防的事。

## 五個唯讀工具（見 `NginxDocMcpServer`）

`nginx_directive` · `nginx_search` · `nginx_module` · `nginx_context`（反查）· `nginx_check_config`

## 端點與認證

`@McpServerEndpoint(channel = STREAMABLE_STATELESS, mcpEndpoint = "/mcp")` —— 無狀態通道：
裸 POST 回純 JSON，不需 `initialize` 握手，也沒有 SSE 包裝。

**opt-in `--mcp.token=<token>`**：不設定則 bean 根本不註冊（`@Condition(onProperty = "mcp.token")`）、
索引不解析、`AppFilter` 回 404；設定了則 `Authorization: Bearer <token>` 不符回 401。

> **三處必須一致**：`@Condition`、`AppFilter`、`InitConfig` 要共用 `NginxDocMcpServer.TOKEN_KEY`
> 與 `NginxDocMcpServer.ENDPOINT` 兩個常數，**且**同用 `Solon.cfg().getByExpr`。
> `cfg().get()` 不查環境變數而 `getByExpr()` 會 —— 混用會造出「端點註冊了卻永遠 404」。
> 端點路徑那個常數比 token key 更不能半套改名：token key 改一半只是永遠 404（fail-closed），
> **路徑改一半會變成未認證即可存取**（fail-open）。

client 設定在 `.mcp.json`（token 與 URL 都走環境變數）。

## 兩個只有手測會踩到的坑

> `mcp.token` 含 `.`，不是合法 POSIX shell 識別字 —— `export mcp.token=x` 在 sh/bash 是**語法錯誤**。
> 文件一律以 `--mcp.token=` 啟動參數為主；Docker 走 `BOOT_OPTIONS` 或 compose `environment:`。

> 手動 curl 測 `/mcp` 時 **`Accept: application/json, text/event-stream` 是必要的**（兩種型態都要列）。
> 少了它、或只寫 `application/json`，一律 **400 且 body 完全是空的**，不會告訴你原因 ——
> 很容易誤判成功能壞掉。MCP client 自己會帶對，只有手測會踩到。

## 設定檢查器的分寸

`nginx_check_config` 的原則是**「只回報能確定的問題 —— 誤報的代價高於漏報」**。
它檢查區塊「內」的指令，以及開區塊那一行本身（`if` 誤寫在 http 層這類 nginx 會拒絕啟動的錯位）；
不檢查語法細節與參數值。已知的漏報都是刻意的，並寫在工具描述裡。

有問題的回應會帶一句但書，說明 context 判斷是逐行括號追蹤推得的，並附可操作的驗證法。
**寫但書時要記得：列舉式的但書要嘛窮盡，要嘛寫成類別，絕不能是封閉清單** ——
一個被相信的誤報比沒寫但書更糟。

## E2E

[tests/e2e/35-mcp.spec.js](../../tests/e2e/35-mcp.spec.js)：未設 token 的共用 server 驗 404；
另起帶 token 的 18081 實例驗 401、工具清單、預設值語意分流、以及 `AppFilter` 的全域回歸。
