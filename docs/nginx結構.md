# nginx 設定結構

依保留的 31 個模組實際可用的區塊範圍。標註 `[模組]` 者為動態模組提供,模組未載入則該區塊不存在。

**官方文檔關鍵頁（繁中校對）：** [nginxdocumentation/README.md](nginxdocumentation/README.md)  
（新手／控制訊號／負載平衡／HTTPS／limit\_\*／stream 等；規範見 [TRANSLATION.md](nginxdocumentation/TRANSLATION.md)）

---

## 一、完整結構樹

```
main（頂層,不包在任何區塊內）
│   worker_processes / worker_rlimit_nofile / pid / user
│   load_module
│   error_log
│
├── events { }
│       worker_connections / multi_accept / use
│
├── http {
│   │
│   ├── 宣告型區塊（被其他設定依賴,單獨啟用無作用）
│   │   ├── upstream { }
│   │   ├── map { }
│   │   ├── geo { }
│   │   ├── split_clients { }
│   │   ├── geoip2 { }                  [ngx_http_geoip2]
│   │   ├── keyval_zone                 [ngx_http_keyval]
│   │   ├── lua_shared_dict             [ngx_http_lua]
│   │   ├── js_import / js_path         [ngx_http_js]
│   │   ├── proxy_cache_path
│   │   ├── limit_conn_zone
│   │   ├── limit_req_zone
│   │   ├── log_format
│   │   ├── types { }
│   │   └── charset_map { }
│   │
│   └── server {
│       │   listen / server_name / ssl_*
│       │   error_page
│       │
│       ├── if { }
│       │
│       └── location {
│           │   proxy_pass（http 層只能在此）
│           │
│           ├── location { }            可巢狀
│           ├── if { }
│           └── limit_except { }
│           }
│       }
│   }
│
├── stream {                            [ngx_stream]
│   │
│   ├── 宣告型區塊
│   │   ├── upstream { }
│   │   ├── map { }
│   │   ├── geo { }
│   │   ├── split_clients { }
│   │   ├── geoip2 { }                  [ngx_stream_geoip2]
│   │   ├── keyval_zone                 [ngx_stream_keyval]
│   │   ├── js_import / js_path         [ngx_stream_js]
│   │   ├── limit_conn_zone
│   │   └── log_format
│   │
│   └── server {
│           listen / proxy_pass
│           無 location、無 if
│       }
│   }
│
├── mail {                              [ngx_mail]
│   │   auth_http（必填,nginx 自身不驗證帳密）
│   │   proxy_pass_error_message
│   │
│   └── server {
│           listen / protocol / starttls
│       }
│   }
│
└── rtmp {                              [ngx_rtmp]
    │   server_names_hash_bucket_size
    │
    └── server {
        │   listen
        │
        └── application { }
        }
    }
```

---

## 二、四個頂層區塊的關鍵差異

| | http | stream | mail | rtmp |
|---|:--:|:--:|:--:|:--:|
| `location` | 有 | **無** | 無 | 無 |
| `if` | 有 | **無** | 無 | 無 |
| `upstream` | 有 | 有 | 無 | 無 |
| `map` / `geo` | 有 | 有 | 無 | 無 |
| `proxy_pass` 位置 | location | server | 由 auth_http 決定 | — |
| 變數集 | 完整 | 精簡 | 極少 | 專屬 |
| nginxWebUI 支援 | 有 | 有 | **無** | **無** |

`mail` 與 `rtmp` 需以自訂參數欄位或 `include` 方式配置。

---

## 三、變數不跨區塊

http 宣告的 `$geoip2_data_country_code`,在 stream 中為未定義變數:

```
nginx: [emerg] unknown "geoip2_data_country_code" variable
```

stream 需在 `stream { }` 內重新宣告一次 geoip2 區塊。同一個 `.mmdb` 檔可被兩邊各自開啟,不衝突,但記憶體佔用為兩份。

---

## 四、zone 名稱全域唯一

共享記憶體 zone 以 **name + 模組指標** 為唯一鍵。`ngx_http_limit_conn` 與 `ngx_stream_limit_conn` 為不同模組,同名將被判定為衝突:

```
nginx: [emerg] the shared memory zone "conn_limit" is already declared for a different use
```

現有 http 層使用 `conn_limit` 與 `req_limit`,stream 層必須另取名稱。建議加 `s_` 前綴以利辨識。

---

## 五、指令層級對照

### 5.1 同名但不同模組的指令

| 指令 | http | stream | 差異 |
|---|:--:|:--:|---|
| `proxy_pass` | location | server | http 可帶路徑,stream 僅位址且無 scheme |
| `proxy_protocol` | listen 參數（**接收**） | 獨立指令（**送出**） | 語意相反,最易誤用 |
| `proxy_timeout` | 無 | server / stream | http 拆為 read / send 兩個 |
| `limit_conn` | http / server / location | stream / server | 相同 |
| `limit_conn_status` | 有 | **無** | L4 無狀態碼 |
| `limit_req*` | 有 | **無** | stream 無此模組 |
| `add_header` | http / server / location | **無** | L4 無標頭 |
| `ip_hash` | upstream | **無** | stream 用 `hash $remote_addr consistent` |
| `keepalive` | upstream | **無** | 後端長連線池為 http 專屬 |

### 5.2 stream 專屬指令

| 指令 | 層級 | 說明 |
|---|---|---|
| `proxy_timeout` | stream / server | 雙向 idle,預設僅 10m |
| `proxy_socket_keepalive` | stream / server | nginx → 後端的 keepalive |
| `proxy_responses` | server | 僅 UDP |
| `proxy_requests` | server | 僅 UDP |
| `ssl_preread` | server | 讀取 SNI 但不終結 TLS |
| `js_access` | server | njs 決定放行或拒絕 |
| `js_preread` | server | 讀取前導位元組後再決定路由 |

---

## 六、繼承規則（最易出錯處）

以下指令的繼承方式為 **replace**:下層只要出現任何一個,上層的全部不繼承。

| 指令 | 影響 |
|---|---|
| `add_header` | server 或 location 出現任一個,http 層的安全標頭全部失效 |
| `proxy_set_header` | location 出現任一個,server 層的 Host、X-Real-IP 全部遺失 |

此規則不會產生任何錯誤訊息,`nginx -t` 照樣通過,屬靜默失效。

---

## 七、宣告與使用的配對

以下功能橫跨兩個層級,缺任一半即失敗:

| 宣告（層級） | 使用（層級） | 缺宣告時的錯誤 |
|---|---|---|
| `map` → `$var` (http) | `if ($var)` (server) | `unknown "var" variable` |
| `limit_conn_zone` (http) | `limit_conn` (server) | `unknown limit_conn_zone "..."` |
| `limit_req_zone` (http) | `limit_req` (server) | `unknown limit_req_zone "..."` |
| `geoip2` → `$var` (stream) | `map $var` (stream) | `unknown "..." variable` |
| `log_format` (http/stream) | `access_log` (server) | `unknown log format "..."` |
| `upstream` (http/stream) | `proxy_pass` (location/server) | `no resolver defined` 或 host not found |
| `proxy_cache_path` (http) | `proxy_cache` (location) | `zone "..." is unknown` |

此為模板系統中 `declares` / `requires` 欄位的設計依據。樹狀結構無法表達此類關係。

---

## 八、njs 補足的 stream 能力

stream 無 `if`,原本需以 map 導向黑洞 upstream 的做法,njs 可直接處理:

| 需求 | 無 njs | 有 njs |
|---|---|---|
| 條件拒絕連線 | map 導向 `127.0.0.1:1` | `js_access` + `s.deny()` |
| 依協定內容路由 | 不可能 | `js_preread` 讀取前導位元組 |
| 動態黑名單 | 改設定 + reload | `js_access` + keyval,免 reload |

`js_preread` 為阻塞式,需設定 `preread_timeout`,否則會拖慢每一條連線建立。

---

## 九、模組與區塊對應

| 模組 | 提供的區塊或指令 | 所屬層級 |
|---|---|---|
| `ngx_stream` | `stream { }` 及其全部子區塊 | 頂層 |
| `ngx_mail` | `mail { }` | 頂層 |
| `ngx_rtmp` | `rtmp { }` | 頂層 |
| `ngx_http_geoip2` | `geoip2 { }` | http |
| `ngx_stream_geoip2` | `geoip2 { }` | stream |
| `ngx_http_js` | `js_import` / `js_path` / `js_content` | http |
| `ngx_stream_js` | `js_access` / `js_preread` / `js_filter` | stream |
| `ngx_http_keyval` | `keyval_zone` / `keyval` | http |
| `ngx_stream_keyval` | `keyval_zone` / `keyval` | stream |
| `ngx_http_lua` | `lua_shared_dict` / `*_by_lua_block` | http |
| `ngx_http_dynamic_healthcheck` | `check` | upstream |
| `ngx_http_vhost_traffic_status` | `vhost_traffic_status_zone` | http |
| `ngx_http_naxsi` | `SecRulesEnabled` / `DeniedUrl` | http / server / location |
| `ngx_http_headers_more` | `more_set_headers` / `more_clear_headers` | http / server / location |
| `ngx_http_cache_purge` | `proxy_cache_purge` | location |
| `ngx_nchan` | `nchan_publisher` / `nchan_subscriber` | location |
| `ngx_http_acme` | `acme_issuer` / `acme_certificate` | http / server |

---

## 十、已知缺口

| 缺口 | 說明 |
|---|---|
| stream 流量監控 | VTS 僅支援 http。stream 的連線數與流量統計需另行編譯 `ngx_stream_server_traffic_status_module`(STS) |
| mail / rtmp 設定產出 | nginxWebUI 僅產出 http 與 stream 兩個區塊 |
| stream 速率限制 | 無 `limit_req` 模組。若需 L4 限流,僅能壓低 `limit_conn`,或於 OS 層以 iptables hashlimit 處理 |
