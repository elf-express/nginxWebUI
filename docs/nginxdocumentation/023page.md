# 以 nginx 作為 HTTP 負載平衡器

> Source: https://nginx.org/en/docs/http/load_balancing.html  
> 翻譯：zh-TW（人工校對）· conf 與 directive 保留原文

---

## 以 nginx 作為 HTTP 負載平衡器

在多個應用實例間分配流量，可優化資源、提高吞吐量、降低延遲，並提升容錯。nginx 可高效地作為 HTTP 負載平衡器。

### 負載平衡方法

| 方法 | 說明 |
|------|------|
| round-robin | 預設；依序輪詢上游 |
| least-connected | 選**目前活躍連線最少**的上游（`least_conn`） |
| ip-hash | 依客戶端 IP 雜湊固定上游（`ip_hash`，session sticky） |

（另有商業版 `least_time` 等，開源版以文件當時支援為準。）

### 預設設定（round-robin）

```nginx
http {
    upstream myapp1 {
        server srv1.example.com;
        server srv2.example.com;
        server srv3.example.com;
    }

    server {
        listen 80;

        location / {
            proxy_pass http://myapp1;
        }
    }
}
```

未指定方法時即 round-robin。反向代理實作亦涵蓋 HTTPS、FastCGI、uwsgi、SCGI、memcached、gRPC（分別用 `https://`、`fastcgi_pass`、`uwsgi_pass`、`scgi_pass`、`memcached_pass`、`grpc_pass`）。

### 最少連線（least_conn）

部分請求較長時，可避免把新請求塞給已經很忙的機器：

```nginx
upstream myapp1 {
    least_conn;
    server srv1.example.com;
    server srv2.example.com;
    server srv3.example.com;
}
```

### 工作階段黏著（ip_hash）

round-robin / least_conn **不保證**同一客戶端總是打到同一上游。需要 sticky 時：

```nginx
upstream myapp1 {
    ip_hash;
    server srv1.example.com;
    server srv2.example.com;
    server srv3.example.com;
}
```

同一客戶端 IP 會固定到同一 server（該 server 不可用時除外）。

### 權重（weight）

```nginx
upstream myapp1 {
    server srv1.example.com weight=3;
    server srv2.example.com;
    server srv3.example.com;
}
```

在 round-robin 下，大約每 5 個新請求中：3 個到 srv1，各 1 個到 srv2、srv3。較新版本亦可與 least_conn、ip_hash 併用權重。

### 健康檢查（被動 / in-band）

開源版內建**被動**健康檢查：某上游連續失敗時標記為 failed，一段時間內避開。

- [`max_fails`](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)：在 `fail_timeout` 視窗內連續失敗次數（預設 1；`0` = 關閉此 server 的健康檢查）
- [`fail_timeout`](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)：標記 failed 的時長；之後會用真實客戶端請求優雅探測，成功則恢復

### 延伸

另見：`proxy_next_upstream`、`backup`、`down`、`keepalive` 等（[upstream 模組](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)）。主動健康檢查、動態改組等進階功能屬 **NGINX Plus**。

### 本專案注意

映像**已移除** `upstream_fair`（久未維護、有 segfault 風險）。負載演算法請用開源內建的 `round-robin` / `least_conn` / `ip_hash`，勿再依賴 fair 模組。
