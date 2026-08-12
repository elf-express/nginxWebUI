# nginx 如何處理 TCP/UDP 工作階段

> Source: https://nginx.org/en/docs/stream/stream_processing.html  
> 翻譯：zh-TW（人工校對）

---

## nginx 如何處理 TCP/UDP 工作階段

客戶端的 TCP/UDP 工作階段依序經過多個 **phase（階段）** 處理：

| Phase | 說明 | 典型模組／指令 |
|-------|------|----------------|
| `Post-accept` | 接受連線後的第一階段 | [`ngx_stream_realip_module`](https://nginx.org/en/docs/stream/ngx_stream_realip_module.html) |
| `Pre-access` | 存取前的初步檢查 | [`ngx_stream_limit_conn_module`](https://nginx.org/en/docs/stream/ngx_stream_limit_conn_module.html)、[`ngx_stream_set_module`](https://nginx.org/en/docs/stream/ngx_stream_set_module.html) |
| `Access` | 真正處理資料前的客戶端存取限制 | [`ngx_stream_access_module`](https://nginx.org/en/docs/stream/ngx_stream_access_module.html)；njs 的 [`js_access`](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_access) |
| `SSL` | TLS/SSL 終結 | [`ngx_stream_ssl_module`](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html) |
| `Preread` | 先讀取初始位元組到 [preread buffer](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#preread_buffer_size)，供模組分析（如 SNI） | [`ngx_stream_ssl_preread_module`](https://nginx.org/en/docs/stream/ngx_stream_ssl_preread_module.html)；njs [`js_preread`](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_preread) |
| `Content` | **必經**階段：實際處理資料，通常 [proxy](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html) 到 [upstream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html)，或 [return](https://nginx.org/en/docs/stream/ngx_stream_return_module.html) 固定值 | njs [`js_filter`](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_filter) |
| `Log` | 最後記錄工作階段結果 | [`ngx_stream_log_module`](https://nginx.org/en/docs/stream/ngx_stream_log_module.html) |

### 與 HTTP 的重要差異（本專案）

- stream 的 `server { }` **沒有** `location`，也**沒有** HTTP 式的 `if`。
- 國家／IP 封鎖等應使用 stream 專用模組（如 `access`、`geoip2`、`map` + 變數）或在 Pre-access/Access 階段處理，**不要**把 HTTP 的 `if ($geoip2...) { return 403; }` 貼進 `stream { }`。
- 需先 `load_module` stream 與相關 `.so`（見本專案 `MODULE_CATALOG` 順序：`ngx_stream_module.so` 先於 `ngx_stream_*`）。

相關結構總覽：[`docs/nginx結構.md`](../nginx結構.md)。
