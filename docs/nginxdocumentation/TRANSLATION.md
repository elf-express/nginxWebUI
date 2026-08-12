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

## 狀態標記（見 README.md）

| 標記 | 意義 |
|------|------|
| ✅ | 已人工繁中校對 |
| 🔧 | 部分校對／精要 |
| ⬜ | 仍為英文抓取稿 |
| ⚠ | 曾被機器誤翻，待重寫 |
