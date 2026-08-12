# nginx 設定結構

教學用簡化結構樹。**指令可否出現在某一層，以 [nginx.org](https://nginx.org/en/docs/) 各模組文件的 Context 欄位為準**；本檔若與官網衝突，以官網為準。

- 樹中**未標模組**者多為 nginx **本體**（`http` / `server` / `location` / `events` 等），不依賴動態 `.so`。
- 標註 `[模組]` 者需 `load_module`（本專案清單見 `NginxService.MODULE_CATALOG`，約 31 個動態模組）。
- 模組未載入則對應區塊／指令不存在。

**官方文檔關鍵頁（繁中校對）：** [nginxdocumentation/README.md](nginxdocumentation/README.md)  
**翻譯規範：** [TRANSLATION.md](nginxdocumentation/TRANSLATION.md)（勿用瀏覽器 auto-translate 批次改 md）

### 官網依據（Context 來源）

| 主題 | 官方頁面 |
|------|----------|
| main / events / load_module | [ngx_core_module](https://nginx.org/en/docs/ngx_core_module.html) |
| HTTP `proxy_pass` | [ngx_http_proxy_module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass) → Context: `location`, `if in location`, `limit_except` |
| HTTP `if` / `return` / `rewrite` | [ngx_http_rewrite_module](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html) → `if`: `server`, `location` |
| stream 區塊 / listen | [ngx_stream_core_module](https://nginx.org/en/docs/stream/ngx_stream_core_module.html) → `stream` Context: `main` |
| stream `proxy_pass` | [ngx_stream_proxy_module](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_pass) → Context: `server` |
| stream `proxy_protocol`（對上游） | 同上 `#proxy_protocol` → `stream`, `server` |
| stream listen `proxy_protocol`（收客戶端） | [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) 參數 |

---

## 一、完整結構樹

```
main（頂層；Context: main 的指令寫在此）
│   worker_processes / worker_rlimit_nofile / pid / user / include / env …
│   load_module                         [動態模組 .so]
│   error_log
│
├── events { }                          [core]
│       worker_connections / multi_accept / use
│
├── http { }                            [core http]
│   │
│   ├── 宣告／定義（多在 http 層，與 server 並列；
│   │   有的是 block { }，有的是單行 directive）
│   │   ├── upstream { }
│   │   ├── map { }
│   │   ├── geo { }                     （core；非 GeoIP2）
│   │   ├── split_clients { }
│   │   ├── geoip2 { }                  [ngx_http_geoip2]
│   │   ├── keyval_zone …               [ngx_http_keyval]
│   │   ├── lua_shared_dict …           [ngx_http_lua]
│   │   ├── js_import / js_path …       [ngx_http_js]
│   │   ├── proxy_cache_path …
│   │   ├── limit_conn_zone …
│   │   ├── limit_req_zone …
│   │   ├── log_format …
│   │   ├── types { }
│   │   └── charset_map { }
│   │
│   └── server { }                      （可多個；尚有大量 server 層指令，下圖只強調路由結構）
│       │   listen / server_name / ssl_* / root / access_log / error_page …
│       │
│       ├── if { }                      [rewrite] Context: server, location
│       │
│       └── location {
│           │   proxy_pass              [http_proxy]
│           │     Context: location | if in location | limit_except
│           │     （不可寫在 http / server 頂層）
│           │
│           ├── location { }            可巢狀
│           ├── if { }                  [rewrite]
│           └── limit_except { }        （其內亦可 proxy_pass，見官網 Context）
│           }
│       }
│
├── stream { }                          [ngx_stream] Context: main
│   │
│   ├── 宣告／定義
│   │   ├── upstream { }
│   │   ├── map { }
│   │   ├── geo { }
│   │   ├── split_clients { }
│   │   ├── geoip2 { }                  [ngx_stream_geoip2]
│   │   ├── keyval_zone …               [ngx_stream_keyval]
│   │   ├── js_import / js_path …       [ngx_stream_js]
│   │   ├── limit_conn_zone …
│   │   └── log_format …
│   │
│   └── server {
│           listen …                    （可含 udp / ssl / proxy_protocol 等參數）
│           proxy_pass …                [stream_proxy] Context: server
│           無 location
│           無 HTTP rewrite 模組的 if
│       }
│
├── mail { }                            [ngx_mail]
│   │   auth_http …                     （mail 認證代理實務上幾乎必要）
│   │   proxy_pass_error_message …
│   │
│   └── server {
│           listen / protocol / starttls
│       }
│
└── rtmp { }                            [ngx_rtmp]
    │   server_names_hash_bucket_size …
    │
    └── server {
        │   listen
        │
        └── application { }
        }
```

---

## 二、四個頂層區塊的關鍵差異

| | http | stream | mail | rtmp |
|---|:--:|:--:|:--:|:--:|
| `location` | 有 | **無** | 無 | 無 |
| `if`（rewrite 模組） | `server` / `location` | **無** | 無 | 無 |
| `upstream` | 有 | 有 | 無 | 無 |
| `map` / `geo` | 有 | 有 | 無 | 無 |
| `proxy_pass` Context | `location` · `if in location` · `limit_except` | **`server` only** | 認證走 `auth_http` | — |
| 變數集 | 完整 | 精簡 | 極少 | 專屬 |
| nginxWebUI 支援 | 有 | 有 | **無專頁**（可用自訂參數） | **無專頁** |

`mail` 與 `rtmp` 需以自訂參數或 `include` 配置。

---

## 三、變數不跨區塊

http 宣告的 `$geoip2_data_country_code`，在 stream 中為未定義變數:

```
nginx: [emerg] unknown "geoip2_data_country_code" variable
```

stream 需在 `stream { }` 內重新宣告一次 geoip2。同一個 `.mmdb` 可被兩邊各自開啟，不衝突，但記憶體約兩份。

---

## 四、zone 名稱與模組

共享記憶體 zone 以 **名稱 + 所屬模組** 區分。`ngx_http_limit_conn` 與 `ngx_stream_limit_conn` 為不同模組，**同名**會衝突:

```
nginx: [emerg] the shared memory zone "conn_limit" is already declared for a different use
```

本專案 http 常用 `conn_limit` / `req_limit`；stream 請用獨立名（如 `s_conn_perip`）。

---

## 五、指令層級對照（對齊官網 Context）

### 5.1 同名但不同模組

| 指令 | http（官網 Context） | stream（官網 Context） | 差異 |
|---|---|---|---|
| `proxy_pass` | `location`, `if in location`, `limit_except` | **`server`** | http 可帶 URI 路徑；stream 為位址／upstream，無 `http://` scheme |
| `proxy_protocol` | 多見於 `listen … proxy_protocol`（**收**客戶端 PROXY 頭） | ① `listen … proxy_protocol`（**收**）② `proxy_protocol on\|off` 指令（**送**給上游） | 收／送語意不同，最易誤用 |
| 逾時 | `proxy_connect_timeout` / `proxy_send_timeout` / `proxy_read_timeout` 等 | `proxy_timeout`（`stream`, `server`）等 | 命名與拆分不同 |
| `limit_conn` | `http` / `server` / `location` | `stream` / `server` | 語意相近 |
| `limit_conn_status` | 有 | **無** | L4 無 HTTP 狀態碼 |
| `limit_req*` | 有 | **無** | stream 無此模組 |
| `add_header` | `http` / `server` / `location` | **無** | L4 無回應標頭 |
| `ip_hash` | upstream | **無** | stream 用 `hash $remote_addr consistent` |
| `keepalive` | upstream | **無** | 後端連線池為 http 專屬 |

### 5.2 stream 常見專屬／常用

| 指令 | Context（概要） | 說明 |
|---|---|---|
| `proxy_timeout` | stream / server | 雙向 idle |
| `proxy_socket_keepalive` | stream / server | 對上游 socket keepalive |
| `proxy_responses` / `proxy_requests` | stream / server | 多用於 UDP |
| `ssl_preread` | server（ssl_preread 模組） | 讀 SNI 但不終結 TLS |
| `js_access` / `js_preread` / `js_filter` | 見 stream_js 文件 | njs 存取／預讀／過濾 |
| `preread_buffer_size` / `preread_timeout` | stream / server | preread 階段 |

---

## 六、繼承規則（最易出錯）

以下為 **replace**（下層出現任一個，上層同名系列不繼承）:

| 指令 | 影響 |
|---|---|
| `add_header` | server 或 location 出現任一個，http 層的安全標頭全部失效 |
| `proxy_set_header` | location 出現任一個，server 層的 Host、X-Real-IP 等全部遺失 |

不會報錯，`nginx -t` 仍可能通過，屬靜默行為。

---

## 七、宣告與使用的配對

橫跨兩層，缺一半會失敗或行為異常:

| 宣告（層級） | 使用（層級） | 缺宣告時常見現象 |
|---|---|---|
| `map` → `$var` (http) | `if ($var)` 等 (server/location) | `unknown "var" variable` |
| `limit_conn_zone` (http) | `limit_conn` (server/location) | `unknown limit_conn_zone "…"` |
| `limit_req_zone` (http) | `limit_req` (server/location) | `unknown limit_req_zone "…"` |
| `geoip2` → `$var` (stream) | `map $var` 等 (stream) | `unknown "…" variable` |
| `log_format` (http/stream) | `access_log … format名` | `unknown log format "…"` |
| `upstream` (http/stream) | `proxy_pass` (location / stream server) | upstream 不存在；若用變數當 host 且未 `resolver` 則可能 `no resolver defined` |
| `proxy_cache_path` (http) | `proxy_cache` (location 等) | `zone "…" is unknown` |

此為參數模板「宣告層 / 使用層」分開設計的依據；單一 tag 無法表達配對關係。

---

## 八、njs 補足的 stream 能力

stream 無 HTTP rewrite 的 `if`。常見替代:

| 需求 | 無 njs | 有 njs |
|---|---|---|
| 條件拒絕連線 | map 導向黑洞 upstream | `js_access` + `s.deny()` |
| 依協定內容路由 | 困難 | `js_preread` 讀前導位元組 |
| 動態黑名單 | 改設定 + reload | `js_access` + keyval 等 |

`js_preread` 偏阻塞，應設 `preread_timeout`。

---

## 九、模組與區塊對應（本專案相關摘錄）

| 模組 | 提供的區塊或指令 | 所屬層級 |
|---|---|---|
| （core） | `events` / `http` / `server` / `location` | 本體 |
| `ngx_stream` | `stream { }` | main |
| `ngx_mail` | `mail { }` | main |
| `ngx_rtmp` | `rtmp { }` | main |
| `ngx_http_geoip2` | `geoip2 { }` | http |
| `ngx_stream_geoip2` | `geoip2 { }` | stream |
| `ngx_http_js` | `js_import` / `js_path` / `js_content` 等 | http |
| `ngx_stream_js` | `js_access` / `js_preread` / `js_filter` 等 | stream |
| `ngx_http_keyval` | `keyval_zone` / `keyval` | http |
| `ngx_stream_keyval` | `keyval_zone` / `keyval` | stream |
| `ngx_http_lua` | `lua_shared_dict` / `*_by_lua*` | http |
| `ngx_http_dynamic_healthcheck` | `check` 等 | upstream |
| `ngx_http_vhost_traffic_status` | `vhost_traffic_status_zone` 等 | http |
| `ngx_http_naxsi` | `SecRulesEnabled` / `DeniedUrl` 等 | http / server / location |
| `ngx_http_headers_more` | `more_set_headers` / `more_clear_headers` | http / server / location |
| `ngx_http_cache_purge` | `proxy_cache_purge` | location |
| `ngx_nchan` | `nchan_publisher` / `nchan_subscriber` 等 | location |
| `ngx_http_acme` | `acme_issuer` / `acme_certificate` 等 | http / server |

完整動態模組清單與 `load_module` 順序見 `NginxService.MODULE_CATALOG`。

---

## 十、已知缺口（產品）

| 缺口 | 說明 |
|---|---|
| stream 流量監控 | VTS 僅 http；stream 需 STS 等額外模組 |
| mail / rtmp 設定產出 | nginxWebUI 主要產出 http + stream |
| stream 速率限制 | 無 `limit_req`；L4 多靠 `limit_conn` 或 OS |

---

## 十一、與參數模板 tag 的對照

| 模板 tag（內部碼） | 對應結構 |
|---|---|
| `http` | `http { }` 頂層宣告／定義 |
| `server` | HTTP `server { }` |
| `location` | HTTP `location { }` |
| `upstream` | HTTP `upstream { }` |
| `stream` | `stream { }` 頂層 |
| `server1` | stream **TCP** `server { }` |
| `server2` | stream **UDP** `server { }` |

非法組合（如 HTTP `if` 勾 `stream`）由 `TemplateDefUtils` 禁用；細節見 `CLAUDE.md`「Parameter templates」。
