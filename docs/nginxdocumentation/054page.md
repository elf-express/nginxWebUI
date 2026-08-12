# 模組 ngx_http_limit_conn_module

> Source: https://nginx.org/en/docs/http/ngx_http_limit_conn_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_http_limit_conn_module

依定義的 **key** 限制**連線數**（常見：單一 IP 的同時連線數）。

並非所有連線都計入：只有伺服器**正在處理請求**、且**已讀完完整請求標頭**的連線才計數。

> HTTP/2 與 HTTP/3 下，每個並行請求視為一條連線。

### 範例

```nginx
http {
    limit_conn_zone $binary_remote_addr zone=addr:10m;

    server {
        location /download/ {
            limit_conn addr 1;
        }
    }
}
```

同一 IP 同時只允許 1 條計數中的連線。

### Directives

#### `limit_conn_zone`（僅 `http`）

```nginx
limit_conn_zone key zone=name:size;
```

- 共享記憶體 zone，保存各 key 的目前連線數。
- 空 key 不計數。
- 建議 key 用 `$binary_remote_addr`。
- zone 耗盡時，後續請求會收到錯誤（見 `limit_conn_status`）。

舊指令 `limit_zone` 已廢止（1.1.8 起廢棄，1.7.6 移除），請改用 `limit_conn_zone`。

#### `limit_conn`（`http`/`server`/`location`）

```nginx
limit_conn zone number;
```

- 超過該 key 允許的連線數時回錯誤。
- 可多條（per-IP + per-server）：

```nginx
limit_conn_zone $binary_remote_addr zone=perip:10m;
limit_conn_zone $server_name zone=perserver:10m;

server {
    limit_conn perip 10;
    limit_conn perserver 100;
}
```

- 僅當本層沒有 `limit_conn` 時繼承上層。

#### `limit_conn_dry_run`（1.17.6）

- 預設 `off`。`on` 時不真正限制，但仍統計。

#### `limit_conn_log_level`（0.8.18）

- 觸發限制時的日誌等級，預設 `error`。

#### `limit_conn_status`（1.3.15）

- 拒絕時狀態碼，預設 `503`。

### 內嵌變數

| 變數 | 意義 |
|------|------|
| `$limit_conn_status` | `PASSED` / `REJECTED` / `REJECTED_DRY_RUN`（1.17.6） |

### 本專案

- HTTP 模板：Connection Limit (http/server)
- **stream** 請用 `ngx_stream_limit_conn_module`（`limit_conn_zone` 在 `stream` 上下文；**不可**把 HTTP 的 `if` 貼進 stream）
- zone 必須**先宣告**再 `limit_conn` 引用；宣告層級錯誤會導致 `nginx -t` 失敗
