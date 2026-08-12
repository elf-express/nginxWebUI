# nginx 官方文檔 · 翻譯規範（本專案）

> 目標：讓操作者能安全讀中文說明，**不把指令與 directive 翻壞**。  
> 不使用瀏覽器 `auto-translate.js` 批次蓋檔。

## 語言

- 正文：**繁體中文（台灣用語優先）**
- 可保留常見術語的英文括註：例如「優雅關閉（graceful shutdown）」

## 絕對不翻譯（原文保留）

| 類別 | 範例 |
|------|------|
| 指令名稱 / 參數字面值 | `stop` `quit` `reload` `reopen` |
| Unix 訊號 | `TERM` `INT` `QUIT` `HUP` `USR1` `USR2` `WINCH` `KILL` |
| shell 命令 | `nginx -s quit`、`kill -s QUIT 1628`、`ps -ax \| grep nginx` |
| directive 名稱 | `worker_processes` `proxy_pass` `limit_req_zone` |
| 變數 | `$remote_addr` `$request_uri` `$geoip2_data_country_code` |
| 路徑 / 埠 / URL / MIME | `/etc/nginx`、`8080`、`http://localhost` |
| 模組檔名 | `ngx_http_proxy_module`、`ndk_http_module.so` |
| 程式碼區塊與 conf 片段 | 整段 ` ``` ` / 縮排 conf 保持英文 |

## 要翻譯

- 標題、段落說明、列表描述
- 表格中「意義／語境／預設行為」欄
- 註解文字（conf 內 `# comment` 可譯成中文註解，**勿改指令本體**）

## 結構慣例

每份已校對頁面頂部：

```markdown
# <中文標題>

> Source: <官方原文 URL>  
> 翻譯：zh-TW（人工校對）· 指令／directive／路徑保留原文
```

## 優先級（與 nginxWebUI 使用場景對齊）

1. **操作**：新手、控制訊號、CLI 開關、語法單位、事件模型  
2. **HTTP 行為**：請求處理、server_name、負載平衡、HTTPS、WebSocket  
3. **常用模組**：core、access、limit_req/conn、map、realip、rewrite、log、headers、proxy、upstream、ssl  
4. **stream**：stream 處理、stream core/proxy/limit_conn  
5. 其餘模組：按需補譯；未校對頁維持英文原文

## 程式碼區塊：引用塊 → code fence（2026-08-12）

抓取工具把 nginx.org 的 `<pre>` 存成了 markdown 引用塊（`> `），於是設定範例在渲染時跟說明文字長得一樣，也拿不到語法highlight。[scripts/fence-code-blocks.js](../../scripts/fence-code-blocks.js) 一次把它們轉成 code fence：**822 個區塊 / 111 個檔案**；另外 442 個區塊判定為散文，維持引用塊不動。

轉換只改呈現形式，不動程式碼字元。這條約束是機器驗證的：「內容指紋」把所有標記與空白剝掉後比對字元序列，轉換前後必須完全相同，對不上就整檔退回。唯一的例外是區塊尾端的空行會被丟掉。

重跑乾跑（**預設就是乾跑，不加 `--apply` 不會寫任何檔**）：

```bash
node scripts/fence-code-blocks.js --dir docs/nginxdocumentation --sample 3
```

語料已經轉換並進版控，**不需要也不應該再跑一次 `--apply`**。工具寫的 `.translate-backup/` 沒有進版控，真正能還原的是 git。

### 已知殘留（已評估，刻意不修）

看到下面這幾種狀況，那是當初權衡後接受的結果，不是工具壞掉：

- **`022page.md` 第 49、73 行：中文說明被包進標成 `nginx` 的 fence。** 這兩處是「一句中文說明 + 一條 `rewrite …;`」的巢狀 note box，分號讓整塊命中程式碼訊號。要修得做行層級切分，那會把「內容一個字元都沒動」從機器可驗的不變量降級成啟發式判斷——為了 2 塊賠掉 822 塊的驗證基礎不划算。同一批判斷救回了 226 行真正的程式碼。
- **約 18 個檔 / 27 個區塊的程式碼裡會看到字面上的星號。** nginx.org 用 `<b>` 標示範例裡要強調的片段，抓取工具把它存成了 markdown 的粗體標記：在引用塊裡渲染成粗體，進了 fence 就變成看得見的 `*` `*`。這是抓取階段就造成的損傷，拿掉星號等於從內容裡刪字元。
- **9 個區塊語言標註錯誤**（822 塊中；只影響 highlight 配色，內容無誤）：`063page.md` 的四塊標成 `c`——第 46 行其實是內嵌 `perl_set` 的 nginx 設定（`$r->header_in` 命中了 C 的 `->` 規則，這一塊是本次工作造成的退步），第 74、137、242 行是 Perl 模組原始碼；另外五塊標成 `nginx` 但不是 nginx 設定：`102page.md:20`（DTrace 腳本）、`108page.md:17`（njs REPL）、`113page.md:54`（shell session）、`069page.md:191`（rewrite 指令追蹤輸出）、`013page.md:17`（HTTP chunked 回應）。還有一批區塊完全沒有語言標註，那是推斷不出來時的保守兜底，不是錯誤。

## 狀態標記（見 README.md）

| 標記 | 意義 |
|------|------|
| 已校對 | 已人工繁中校對 |
| 部分校對 | 部分校對／精要 |
| 未校對 | 仍為英文抓取稿 |
| 待重寫 | 曾被機器誤翻，待重寫 |
