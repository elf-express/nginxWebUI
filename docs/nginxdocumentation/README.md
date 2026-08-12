# nginx 官方文檔擷取 · 本專案校對狀態

來源： [https://nginx.org/en/docs/](https://nginx.org/en/docs/)  
翻譯規範：[TRANSLATION.md](TRANSLATION.md)  
區塊結構（本專案 31 模組）：[../nginx結構.md](../nginx結構.md)

> **不要用** `scripts/auto-translate.js`（瀏覽器網頁外掛）批次改這些 `.md`。  
> 該腳本會誤翻 `nginx -s quit`、`kill -s QUIT` 等字面值，且破壞 Markdown。

## 狀態圖例

| 標記 | 意義 |
|------|------|
| ✅ | 已人工繁中校對（可當操作參考） |
| ⬜ | 仍為英文擷取稿（或僅標題中文化） |
| ⚠ | 曾有機器誤翻殘留；**未**列入下方 ✅ 清單者請改看官方原文 |

## 已校對（✅）

| 檔案 | 主題 |
|------|------|
| [001page.md](001page.md) | 索引 |
| [002page.md](002page.md) | 新手指南 |
| [005page.md](005page.md) | 控制 nginx / 訊號 |
| [009page.md](009page.md) | 連線處理方法 |
| [019page.md](019page.md) | 雜湊表 |
| [021page.md](021page.md) | HTTPS 伺服器 |
| [023page.md](023page.md) | HTTP 負載平衡 |
| [024page.md](024page.md) | access（allow/deny） |
| [030page.md](030page.md) | auth_request |
| [054page.md](054page.md) | limit_conn（HTTP） |
| [055page.md](055page.md) | limit_req |
| [057page.md](057page.md) | map |
| [067page.md](067page.md) | realip |
| [089page.md](089page.md) | 請求如何處理 |
| [090page.md](090page.md) | server_name |
| [091page.md](091page.md) | WebSocket |
| [093page.md](093page.md) | 安裝 |
| [125page.md](125page.md) | stream limit_conn |
| [143page.md](143page.md) | stream 工作階段 phase |
| [144page.md](144page.md) | 命令列參數 |
| [145page.md](145page.md) | 計量單位 |

## 建議下一波（常用、體積大）

| 檔案 | 主題 | 優先 |
|------|------|------|
| 103page.md | ngx_core_module | 高 |
| 035page.md | ngx_http_core_module | 高（極長 → 宜做精要） |
| 064page.md | ngx_http_proxy_module | 高 |
| 083page.md | ngx_http_upstream_module | 高 |
| 076page.md | ngx_http_ssl_module | 中 |
| 069page.md | ngx_http_rewrite_module | 中 |
| 056page.md | ngx_http_log_module | 中 |
| 120page.md | ngx_stream_core_module | 中 |
| 132page.md | ngx_stream_proxy_module | 中 |
| 053 / 052 | keyval / js | 依功能 |

## 檔名對照

每個 `NNNpage.md` 開頭有：

```markdown
> Source: https://nginx.org/en/docs/...
```

以 `Source` 為準查官方原文；校對頁會另標「翻譯：zh-TW（人工校對）」。
