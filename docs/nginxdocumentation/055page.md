# 模組 ngx_http_limit_req_module

> Source: https://nginx.org/en/docs/http/ngx_http_limit_req_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_http_limit_req_module

（自 0.7.21）依定義的 **key** 限制請求處理速率（常見：單一 IP）。使用 **leaky bucket（漏桶）** 演算法。

### 範例

```nginx
http {
    limit_req_zone $binary_remote_addr zone=one:10m rate=1r/s;

    server {
        location /search/ {
            limit_req zone=one burst=5;
        }
    }
}
```

### Directives

#### `limit_req_zone`（僅 `http`）

```nginx
limit_req_zone key zone=name:size rate=rate [sync];
```

- 建立共享記憶體 zone，保存各 key 的狀態（含目前過量請求數）。
- `key` 可含文字與變數；**空 key 不計數**。
- 建議用 `$binary_remote_addr`（IPv4 固定 4 bytes / IPv6 16 bytes），勿用長度不固定的 `$remote_addr` 當 key 以節省空間。
- `rate`：`r/s`（每秒）或 `r/m`（每分鐘）。例如半次/秒 → `30r/m`。
- zone 滿時會淘汰最近最少使用狀態；仍無法建立則以錯誤回應（見 `limit_req_status`）。
- `sync`（1.15.3）為商業版 zone 同步。

範例：10MB zone `one`，平均不超過 1 請求/秒。

#### `limit_req`（`http`/`server`/`location`）

```nginx
limit_req zone=name [burst=number] [nodelay | delay=number];
```

- 超過 zone 設定的 rate 時會**延遲**處理，使實際速率貼近 rate。
- 過量請求數超過 `burst` 則拒絕（狀態碼見 `limit_req_status`）。預設 burst=0。
- `nodelay`：過量請求不延遲，立刻處理或拒絕。
- `delay`（1.15.7）：超過多少才開始延遲；預設 0 表示所有過量都延遲。
- 可寫多條（例如 per-IP + per-server）：

```nginx
limit_req_zone $binary_remote_addr zone=perip:10m rate=1r/s;
limit_req_zone $server_name zone=perserver:10m rate=10r/s;

server {
    limit_req zone=perip burst=5 nodelay;
    limit_req zone=perserver burst=10;
}
```

- 僅當本層沒有 `limit_req` 時繼承上層。

#### `limit_req_dry_run`（1.17.1）

- 預設 `off`。`on` 時不真正限速，但仍在 zone 內統計過量。

#### `limit_req_log_level`（0.8.18）

- 預設 `error`。拒絕與延遲的日誌等級；延遲比拒絕低一級（例如設 `notice` 時，延遲記 `info`）。

#### `limit_req_status`（1.3.15）

- 拒絕時的 HTTP 狀態碼，預設 `503`。

### 內嵌變數

| 變數 | 意義 |
|------|------|
| `$limit_req_status` | `PASSED` / `DELAYED` / `REJECTED` / `DELAYED_DRY_RUN` / `REJECTED_DRY_RUN`（1.17.6） |

### 本專案

參數模板「Rate Limit (http/server)」對應 `limit_req_zone` + `limit_req` 骨架。zone 名稱與 key 請依站點修改。
