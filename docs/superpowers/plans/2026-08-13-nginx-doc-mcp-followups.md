# nginx docs MCP — 合併後的後續項目

日期：2026-08-13
來源：`feat/mcp` 合併前的兩輪獨立審查（一輪看認證／MCP 層／文件，一輪看 parser／service／checker／測試）
狀態：全部**非阻斷**，功能已合併並通過 Java 112 + Playwright 179

> **讀這份文件前請先看這一句：** 下面每一項都標了證據等級。
> 「實測」＝有人跑過並貼出輸出；「讀碼推論」＝只從程式碼判斷、**未以執行證明**。
> 不要把讀碼推論當成已知事實直接寫進修法或 commit message —— 這條分支上已經有數次
> 「看起來顯然如此」的說法被實測推翻（`worker_process` 其實走 prefix bucket、
> 跳過 `frontInterceptor` 並不會省下 session、一份宣稱「多一個 `}`」的設定其實括號平衡）。

---

## 一、設定檢查器（`NginxConfChecker`）

### F-1 尾隨大括號仍會讓堆疊算錯（實測）

`listen 80; }` 這種寫法：該行不以 `}` 開頭、也不以 `;` 結尾，於是整行被跳過，`}` 從未被 pop，
之後每一行都多算一層。實測會把 `worker_processes 4;` 報成「不能用在 server」。

**這是目前唯一還會誤報的形狀。** 它已被工具的但書誠實涵蓋（「只要有 `}` 與其他內容寫在同一行，
或大括號不平衡，context 就可能判斷錯誤」），所以不是「被相信的誤報」，但仍是誤報。

修法要與前置 `}` 對稱：從行尾逐個吃掉 `}` 並 pop，再讓剩下的部分走正常流程。
**注意**：不能單純數大括號 —— `location ~ ^/api/v[0-9]{1,2}/ {` 的正則裡就有 `{`。
`pushBlocks` 現有的守衛（該行不含 `}` 且每段開頭 token 都像區塊名才逐段推）是既有範本。
需要自己的測試輪，不宜順手夾帶。

### F-2 誤拼的區塊名會讓整個區塊靜音（實測）

`serer {` 不在 `DIRECTIVE_BLOCKS` 裡 → `OPAQUE` → 裡面所有指令都不檢查
（實測 `proxy_pas http://x;` 與 `worker_connections 1;` 都靜音）。

這是 opaque-by-default 的必然代價，而那個預設是對的 —— 它正是讓 `geoip2 { }` 這類第三方區塊
保持安靜的機制。但對一個以「抓拼字錯誤」為賣點的工具，這個盲區值得寫進工具描述。

### F-3 大輸入沒有上限（實測）

`nginx_check_config` 的成本主要來自 `suggest()`。已加長度差守衛後仍建議設輸入行數上限並明說截斷：

| 輸入 | 耗時（守衛加入前） |
|---|---|
| 5 KB | 545 ms |
| 53 KB | 1,475 ms |
| 269 KB | 6,251 ms |
| 200 × 2,000 字元 token | 14,273 ms |

端點需認證，所以不是匿名 DoS，但一次大貼上會佔住請求執行緒數秒。
**未測**：實際崩潰點（只壓到 269 KB）。

### F-4 巢狀 `if` 的訊息指錯行與原因（實測）

`location { if { if { add_header …; } } }` 會報 `add_header 不能用在 if`，
但真正的錯誤是巢狀 `if` 本身（語料裡 `if` 的 context 是 `server, location`）。
訊息指向的行與原因都不對。

### F-5 一行多條指令只檢查第一條（實測）

`listen 80; worker_connections 1024;` 只報 `listen`。

### F-6 大小寫錯的指令名被形狀過濾靜音（實測）

`Listen 80;` → 0 則。`DIRECTIVE_NAME` 的形狀過濾排在 `suggest()` 之前，所以連候選都給不出來。
nginx 指令名大小寫敏感，這是真錯誤。

### F-7 大括號不平衡時家族偵測會鎖死（實測）

`http { server {` 少關一層之後接 `stream { server { … } }`，`peekFirst()` 仍是 `http`，
stream 裡的 HTTP-only 指令因此放行。屬於 garbage-in 類別，合法設定的家族切換實測正確。

---

## 二、搜尋（`NginxDocService.search` / `nginx_search`）

### F-8 命中位置取每頁第一個，實務上常落在目錄裡（實測）

`pageTitle()` 取第一個 `## ` 標題，而 **150 頁裡有 131 頁**的第一個 `## ` 是「目錄」。
加上每頁只取第一個命中，結果是多數命中被標成「目錄」且片段落在目錄清單而非內文。

改法方向（任一即可，不必全做）：模組頁改抓 `## Module …` 那行或退回 `> Source:` 的 URL 推導；
命中位置跳過文件開頭的目錄區塊。

### F-9 片段沒有經過 `stripHtml`（實測）

`NginxDocParser.stripHtml` 已經存在且用在別處，但搜尋片段是生的 markdown／HTML，
模型會收到 `<table cellspacing="0"><tbody><tr><th>Syntax:</th>…`。

---

## 三、索引與服務層

### F-10 索引發布沒有同步（**讀碼推論，未以執行證明**）

`NginxDocService` 的集合是普通可變 map，由 `InitConfig` 在主執行緒寫入、Undertow worker 讀取，
沒有 `volatile`、沒有 final、沒有同步。實務上 worker pool 在 `InitConfig` 之後才起
（實測索引 log 早於 `Undertow: Started` 約 0.6 秒），所以目前能動。

但依 JMM 這是 data race，而且 `load()` 是 public —— 任何在執行期呼叫它的東西都會讀到
部分發布的 map。修法：把索引整包換成不可變快照，指派給一個 `volatile` 欄位。

### F-11 `pageTitle` / `pageSource` 的 split limit 陷阱（合成重現）

`md.split("\n", 40)` 與 `md.split("\n", 10)` 的最後一個元素會裝著整份文件的剩餘部分。
若 `## ` 首次出現在第 40 行（或 `> Source:` 在第 10 行），title／source 會變成整頁內容。
**實測全部 150 頁目前 0 命中**，但設計明講使用者會自行新增 `151page.md`。

### F-12 缺頁容忍度沒有測試（實測）

`MISSING_RUN = 20`（連續 20 個缺號才停止探測）。語料是連號 001–150，所以把 20 改成 1
測試照樣全綠 —— 被守住的是「硬寫死上限」的移除，容忍度本身沒有覆蓋。

### F-13 回傳型別的可變性不一致（實測）

`search()` 與 `byModule()` 回傳活的 `ArrayList`（`byModule("proxy").add("x")` 會成功），
其餘查詢方法都 `List.copyOf`。只有 `suggest()` 有不可變性測試。

---

## 四、MCP 端點與部署

### F-14 認證失敗完全不留 log（實測）

錯誤的 bearer token 產生 401 但沒有任何 log，所以對 `/mcp` 猜 token 對運維是隱形的 ——
而 CrowdSec 讀的是 nginx 的 log 不是 app 的。也沒有 token 強度下限（`--mcp.token=1` 會被接受）。
一行 `logger.warn`（不要回顯送來的 token）即可。

### F-15 `/mcp` 是裸前綴比對（實測）

`/mcpfoo`、`/mcp-admin`、`/mcpx/y` 全部被 token 閘吃掉（無 auth 401、有 auth 404）。
今天 fail-closed 所以無害，但等於吞掉整個 `/mcp*` 命名空間，未來要加 `/mcpsettings` 頁面會變成
莫名其妙的 401。有效 auth 下 `/mcp/sse` 回 404，代表端點確實只有 `/mcp`，
改成 `path.equals(ENDPOINT) || path.startsWith(ENDPOINT + "/")` 更緊也一樣安全。

### F-16 設計文件的 jar 體積數字已過時（實測）

`docs/superpowers/specs/2026-08-12-nginx-doc-mcp-design.md` 仍寫「jar 從 42.6 MB 增至約 44.6 MB」。
實測 43.1 → 51.9 MiB（+8.8 MiB / +20.4%），已明確裁示接受。
成因：jtokkit 3.2 MB、reactor-core 1.9 MB、jackson 2.3 MB —— 五個 LLM dialect 其實只佔 0.11 MiB（1.2%），
所以排除 dialect 幾乎省不到東西。設計文件的數字要同步。

### F-17 `.mcp.json` 與設計文件排除 codegraph 的理由自相矛盾（**論證問題，未測行為**）

設計文件用「沒建索引的人一 clone 就遇到查不出原因的連線失敗」當理由排除 codegraph，
卻交付了一份需要 `NGINX_WEBUI_MCP_TOKEN` 與一個跑起來的實例的 `.mcp.json`。同一個論證適用。

**未測**：Claude Code 對未設定的 `${NGINX_WEBUI_MCP_TOKEN}` 的實際行為。
官方文件說變數未設定且無預設時 config 仍會載入、`claude mcp list` 顯示警告、未展開的 `${VAR}` 原文照送
—— 若屬實，fresh clone 會拿到 401 而非連線失敗。要下結論前需實測。

---

## 五、這條分支學到、值得帶到下一次的兩條規則

1. **列舉式的但書要嘛窮盡，要嘛寫成類別，絕不能是封閉清單。**
   舊的 caveat 寫「若大括號不平衡，或 `}` 與新區塊寫在同一行」，而 `} }` 兩個條件都不成立
   —— 使用者去數括號發現平衡，反而更相信那個誤報。**一個被相信的誤報比沒寫但書更糟。**
   現在的寫法是類別加可操作的驗證程序（把每個 `}` 分行再跑一次比對）。

2. **輸出被縮減時一定要讓呼叫端知道。**
   `nginx_context` 一開始就做對了（印「共 N 條／以下列出前 M 條」），
   `nginx_search` 的 `limit` 卻靜默截到 30。同一個專案裡出現兩種待遇，就是缺口所在。

---

## 附：這批項目是怎麼找到的

值得記錄，因為前面七輪任務審查加一輪全分支審查**都沒找到上面大部分項目**。差別在方法：

- 一輪實際起 jar，用 `curl --path-as-is` 打 15 種路徑變形（`/%6dcp`、`//mcp`、`/mcp%2f`、`/mcp;a=b`…）
  與 6 種 auth 變形，證明認證閘打不穿 —— 而不是讀碼論證它應該沒問題。
- 一輪把源碼改壞編到 override 目錄跑**突變測試**：11 個突變有 9 個變紅且是對的測試抓到，
  順帶證明 `search()` 可以完全忽略查詢字串而 81/81 照樣全綠 —— 那是唯一 stub 也能過的缺口。

讀碼推論找不到這些。下次要對關鍵路徑下結論時，優先花時間在「實際跑起來打它」與「把它改壞看測試會不會紅」。
