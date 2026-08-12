# 模組 ngx_stream_limit_conn_module

> Source: https://nginx.org/en/docs/stream/ngx_stream_limit_conn_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_stream_limit_conn_module

（自 1.9.3）在 **stream** 上下文依 key 限制連線數（常見：單一 IP）。超過時**關閉連線**（與 HTTP 回狀態碼不同）。

### 範例

```nginx
stream {
    limit_conn_zone $binary_remote_addr zone=addr:10m;

    server {
        # listen ...;
        # proxy_pass ...;
        limit_conn           addr 1;
        limit_conn_log_level error;
    }
}
```

同一 IP 同時只允許 1 條連線。

### Directives

#### `limit_conn_zone`（僅 `stream`）

```nginx
limit_conn_zone key zone=name:size;
```

- 共享記憶體保存各 key 連線數。  
- key 可含文字與變數（1.11.2+）；空 key 不計。  
- 建議 `$binary_remote_addr`。zone 滿則關閉連線。

#### `limit_conn`（`stream` / `server`）

```nginx
limit_conn zone number;
```

- 可多條，**任一**限制觸發即生效。  
- 僅當本層沒有 `limit_conn` 時繼承上層。

#### `limit_conn_dry_run`（1.17.6）

- 預設 `off`；`on` 不真正限制但仍統計。

#### `limit_conn_log_level`

- 觸發限制時日誌等級，預設 `error`。

### 內嵌變數

| 變數 | 意義 |
|------|------|
| `$limit_conn_status` | `PASSED` / `REJECTED` / `REJECTED_DRY_RUN`（1.17.6） |

### 與 HTTP 版差異（重要）

| | HTTP `limit_conn` | stream `limit_conn` |
|--|-------------------|---------------------|
| 宣告上下文 | `http` | `stream` |
| 超限行為 | HTTP 狀態碼（預設 503） | 關閉連線 |
| 可否用 `if` | HTTP 有（慎用） | **stream 無 `if`** |

### 本專案

模板「Connection Limit (stream / stream server)」對應此模組。`limit_conn_zone` 必須在 `stream { }` 層宣告，`limit_conn` 引用同一 zone 名；**不要**把 HTTP 的 `limit_conn_zone` 或 `if` 貼進 stream。
