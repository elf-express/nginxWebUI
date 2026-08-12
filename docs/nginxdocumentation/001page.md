# nginx 文檔索引（本專案校對版）

> Source: https://nginx.org/en/docs/  
> 翻譯：zh-TW 索引 · 指令／路徑保留原文  
> 完整狀態與規範見 [README.md](README.md)、[TRANSLATION.md](TRANSLATION.md)

本目錄為 [nginx.org 英文文檔](https://nginx.org/en/docs/) 的擷取與**關鍵頁人工繁中校對**。未標 ✅ 的頁面仍可能是英文原文或舊機器譯稿，**請勿盲目照抄指令**。

結構樹（對應本專案 31 模組）見：[nginx結構.md](../nginx結構.md)

---

## 操作與概念（已校對）

| 主題 | 檔案 |
|------|------|
| 新手指南 | [002page.md](002page.md) |
| 控制 nginx（訊號） | [005page.md](005page.md) |
| 連線處理方法（epoll 等） | [009page.md](009page.md) |
| 雜湊表設定 | [019page.md](019page.md) |
| 命令列參數（`-s`/`-t`/`-c`） | [144page.md](144page.md) |
| 計量單位（`8k`/`1h`） | [145page.md](145page.md) |
| 安裝 | [093page.md](093page.md) |

## HTTP 行為（已校對）

| 主題 | 檔案 |
|------|------|
| 請求如何處理 / server 選擇 | [089page.md](089page.md) |
| server_name | [090page.md](090page.md) |
| HTTP 負載平衡 | [023page.md](023page.md) |
| HTTPS 伺服器 | [021page.md](021page.md) |
| WebSocket 代理 | [091page.md](091page.md) |

## 常用模組（已校對）

| 模組 | 檔案 |
|------|------|
| `ngx_http_access_module` | [024page.md](024page.md) |
| `ngx_http_limit_conn_module` | [054page.md](054page.md) |
| `ngx_http_limit_req_module` | [055page.md](055page.md) |
| `ngx_http_map_module` | [057page.md](057page.md) |
| `ngx_http_realip_module` | [067page.md](067page.md) |
| `ngx_http_auth_request_module` | [030page.md](030page.md) |

## Stream（已校對）

| 主題 | 檔案 |
|------|------|
| TCP/UDP 工作階段階段 | [143page.md](143page.md) |
| `ngx_stream_limit_conn_module` | [125page.md](125page.md) |

---

## 官方原文入口（未全譯）

- [模組指令字母索引](https://nginx.org/en/docs/dirindex.html) → 本目錄 `008page.md`（英文）
- [變數字母索引](https://nginx.org/en/docs/varindex.html) → `148page.md`（英文）
- [核心模組 ngx_core_module](https://nginx.org/en/docs/ngx_core_module.html) → `103page.md`
- [ngx_http_core_module](https://nginx.org/en/docs/http/ngx_http_core_module.html) → `035page.md`（體積大，待精要）
- [ngx_http_proxy_module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html) → `064page.md`
- [ngx_http_upstream_module](https://nginx.org/en/docs/http/ngx_http_upstream_module.html) → `083page.md`
- [ngx_http_ssl_module](https://nginx.org/en/docs/http/ngx_http_ssl_module.html) → `076page.md`
- [ngx_stream_core_module](https://nginx.org/en/docs/stream/ngx_stream_core_module.html) → `120page.md`
- [ngx_stream_proxy_module](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html) → `132page.md`

其餘 `NNNpage.md` 請對照檔內 `Source:` 連結閱讀官方原文。

---

## 安全提醒（讀文件時）

1. **訊號／CLI 字面值不要翻譯**：必須是 `quit` / `reload` / `stop`，不是「退出／重新載入」。  
2. **stream 沒有 HTTP 的 `if` / `location`**。  
3. **已移除危險模組**（fair、legacy geoip、perl、upload* 等）見 Dockerfile / `MODULE_CATALOG`，文件若提到這些模組僅供對照歷史，本映像不提供。
