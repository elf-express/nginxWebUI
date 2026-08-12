# 設定 HTTPS 伺服器

> Source: https://nginx.org/en/docs/http/configuring_https_servers.html  
> 翻譯：zh-TW（人工校對）· conf 保留原文

---

## 設定 HTTPS 伺服器

在 `server` 的 [`listen`](https://nginx.org/en/docs/http/ngx_http_core_module.html#listen) 加上 `ssl`，並指定憑證與私鑰：

```nginx
server {
    listen              443 ssl;
    server_name         www.example.com;
    ssl_certificate     www.example.com.crt;
    ssl_certificate_key www.example.com.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ...
}
```

- **憑證**是公開的，會送給每個客戶端。  
- **私鑰**需嚴格權限，但 master 必須可讀。  
- 憑證與金鑰可放同一檔；僅憑證會送給客戶端。

`ssl_protocols` / `ssl_ciphers` 可限制協定與加密套件。現代預設多為 `TLSv1.2 TLSv1.3` 與 `HIGH:!aNULL:!MD5`，通常不必再寫（預設曾多次變更，見相容性）。

### 效能優化

SSL 耗 CPU，尤以 handshake 為重。建議：

1. `worker_processes` ≥ CPU 核心數（常用 `auto`）  
2. 開 **keepalive**，減少重複連線  
3. 共用 **SSL session cache**，避免並行／後續連線重做 handshake  

```nginx
worker_processes auto;

http {
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;

    server {
        listen              443 ssl;
        server_name         www.example.com;
        keepalive_timeout   70;
        ssl_certificate     www.example.com.crt;
        ssl_certificate_key www.example.com.key;
        ...
    }
}
```

約 1MB cache ≈ 4000 sessions；預設 timeout 5 分鐘，可用 `ssl_session_timeout` 調整。

### 憑證鏈（certificate chain）

若 CA 用中繼憑證簽名，需把 **server 憑證在前**、中繼 bundle 在後串成一檔：

```bash
cat www.example.com.crt bundle.crt > www.example.com.chained.crt
```

```nginx
ssl_certificate     www.example.com.chained.crt;
ssl_certificate_key www.example.com.key;
```

順序反了可能啟動失敗（`key values mismatch`）。可用 `openssl s_client -connect host:443` 檢查 Certificate chain 是否完整。

### 同一 server 兼 HTTP + HTTPS

```nginx
server {
    listen              80;
    listen              443 ssl;
    server_name         www.example.com;
    ssl_certificate     www.example.com.crt;
    ssl_certificate_key www.example.com.key;
}
```

現代版本應在 `listen` 使用 `ssl` 參數；舊的整 server `ssl on;` 已不建議（1.25.1 移除）。

### 同 IP 多個 HTTPS 站

SSL 握手發生在 HTTP 請求之前；**沒有 SNI** 時只能送 **default server** 的憑證。解法：

1. **每站獨立 IP**（最穩）  
2. **多名稱／萬用憑證**（SAN 或 `*.example.org`；萬用通常只蓋一層子網域）  
3. **SNI**（TLS Server Name Indication）：握手時帶主機名，nginx 選對應憑證  

確認 SNI：

```bash
nginx -V
# 應見：TLS SNI support enabled
```

### 相容性摘要

- `listen ... ssl`：0.7.14+  
- SNI：0.5.23+；`nginx -V` 顯示 SNI 狀態：0.8.21 / 0.7.62+  
- 近期預設協定偏向僅 **TLSv1.2 + TLSv1.3**（視 OpenSSL）

### 本專案

憑證由 UI／acme 模組管理；產生 conf 時注意 `ssl_certificate` 路徑可讀、鏈完整，並在 `nginx -t` 通過後再替換。
