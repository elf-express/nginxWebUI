# nginx 官方文件在地化與 translate-docs 工具

日期：2026-08-12
狀態：已完成（文件處理）／MCP 設計待續

## 背景

`docs/nginxdocumentation/` 收了 150 頁從 nginx.org 抓下來的文件，原本要當作 nginx MCP 的知識庫。
檢查時發現 002／006 兩頁的設定範例被機器翻譯毀掉，貼進 `nginx.conf` 會直接語法錯誤：

```
伺服器{                   應為 server {
地點/{                    應為 location / {
根/數據/www；             應為 root /data/www;   連分號都成了全形
錯誤\_log /路徑/到/日誌； 應為 error_log /path/to/log;
活動{                     應為 events {
殺死 -s 退出 1628         應為 kill -s QUIT 1628
轉儲二進位記憶體          應為 dump binary memory   （gdb 腳本也被翻）
```

## 為什麼「瀏覽器當下翻」沒事，「事後翻檔案」會壞

這是這次最重要的結論，有三層原因。

**一、翻譯的輸入單位不同。** 瀏覽器 UserScript 走訪的是 DOM 文字節點，`<pre>`／`<code>`／`<table>`
各自是獨立元素且在跳過清單裡，HTML 結構天生就是護欄，程式碼碰不到。而 markdown 是扁平字串，
程式碼與散文躺在同一條文字流，邊界只能用正則猜；更麻煩的是 `#`、`[]()`、反引號這些**結構標記
本身也是純文字**，會被送進引擎然後被改寫。

**二、端點不同。** UserScript 的主引擎是 `translate-pa.googleapis.com/v1/translateHtml`——Google
專門翻 HTML 的端點，保留標籤並用 `<a i="N">` 標記位置。本工具用的騰訊 transmart 是
`type: 'plain'` 純文字端點，對結構毫無概念。

**三、「當下翻很好」只在畫面上成立。** 002／006 正是當下翻譯的產物，也是 150 頁裡唯二被翻壞的。
推測是抓取工具把 `<pre>` 轉成 `> ` 引用塊時，帶進去的已經是譯文——TWP 在畫面上跳過了程式碼，
轉檔那一步沒有。後來重抓的英文原文反而乾淨。

結論：對 markdown 做事後翻譯，必須自己重建一套護欄。`scripts/translate-docs.js` 就是那套護欄。

## 工具設計

`scripts/translate-docs.js`，通用 markdown 批次翻譯，非 nginx 專用。核心不是呼叫 API，是保護層。

**保護（送翻前換成 `@N@` 佔位符，翻完還原）**：fenced code、`<table>`/`<pre>`/`<code>` 區塊、
反引號行內程式碼、URL、markdown 連結、跳脫過的指令名（`ngx\_http\_core\_module`）。
行首 markdown 標記（`#`、`-`、`>`）連同縮排整段切下來，永不送翻。

佔位符格式是實測選的，不是猜的：`⟦N⟧` 被騰訊改寫成 `「N」`、`<xN/>` 直接被吞，
`@N@`／`%N%`／`[[N]]`／`{N}`／`#N#` 在騰訊與 Google 都能原樣返回，選 `@` 是因為它不跟
markdown 連結、nginx 大括號或標題語法相撞。

markdown 連結拆成三段（`[`、label、`](url)`）：整個包起來 label 就永遠翻不到，
只遮 `](url)` 又會留下孤立的 `[`，引擎會自己補一個 `]` 造成錯位。

**三道安全閥，每一道都退回原文而不是產出壞內容**：
1. 佔位符沒有全數歸位 → 該行保留原文
2. 遮罩後不足兩個英文單字 → 不送翻（引擎對 `Syntax: @0@ @1@;` 會整句亂生成）
3. 行層級守恆檢查（反引號、方括號、連結標記、`#`、ASCII 分號、HTML 標籤數量）＋檔案層級複查

檢查放在行層級是刻意的：`007page.md` 有 2468 個反引號，早期的檔案級回滾讓它只因兩個對不上
就整份 5000 行都不翻。

**冪等與人工保護**：原文備份在各目錄的 `.translate-backup/`，重跑從備份讀。腳本另外記錄自己的
產出為 `<name>.out`，發現目前檔案與它不符就判定「有人手動校對過」而跳過，`--force` 才覆蓋。

## 處理結果

| 項目 | 數字 |
|---|---|
| 檔案 | 150 |
| 翻譯 | 2634 行 |
| 結構修復（`--repair`，零 API 呼叫） | 154 行 / 94 檔 |
| 移除導覽表格（`--strip-nav`） | 135 行 / 105 檔 |
| 安全閥回滾、維持英文原文 | 27 檔 |
| 誤譯特徵／佔位符殘留／標題破壞 | 全部 0 |
| 指令語法表格（保留） | 947 |
| 引用塊轉 code fence（`scripts/fence-code-blocks.js`） | 822 區塊 / 111 檔 |
| 　└ 判為散文、維持引用塊 | 442 區塊 |
| 　└ 內容指紋不符而放棄的檔案 | 0 |

002／006 是人工校對版（`> 翻譯：zh-TW（人工校對）`），所有指令、路徑、gdb/lldb 腳本保留原文，
設定範例改用 code fence，並經 nginx.org 原文逐條核對。

## 使用方式

```bash
node scripts/translate-docs.js --help
node scripts/translate-docs.js --dir <目錄> --dry-run --print 5   # 先看送翻內容
node scripts/translate-docs.js --dir <目錄> --lang ja             # 換語言
node scripts/translate-docs.js --dir <目錄> --repair              # 只修結構，不翻譯
```

## 已知限制

- 27 檔因安全閥回滾維持英文原文。內容完好，只是沒翻；英文對 AI 檢索其實不吃虧。
- 騰訊譯文用詞偏中國大陸習慣（「設置」「模塊」「支持」），字是繁體但不是台灣慣用語。
- `microsoft` 引擎的 `edge.microsoft.com/translate/auth` 目前回 404，留著備查。
- `docs/nginxdocumentation/` 未納入 git，翻譯成果只存在於工作目錄，靠 `.translate-backup/` 保護。

## 未竟事項：nginx MCP

原始需求是搭 nginx MCP，已定案但尚未實作的設計決策：

- 方案 A：內嵌進 nginxWebUI（`solon-ai-mcp` + `@McpServerEndpoint(STREAMABLE, "/mcp")`），
  工具方法直接注入既有 `ServerService`／`ConfService`／`NginxService`，不繞 HTTP、不換 token
- 認證：`--mcp.token` 啟動參數，opt-in；沒設就不註冊 endpoint，升版對既有部署零影響
- 權限：讀取全開，寫入走白名單且寫入前 `nginx -t`，失敗回滾
- 執行位置：與 nginx 同一台，對外走 streamable HTTP
