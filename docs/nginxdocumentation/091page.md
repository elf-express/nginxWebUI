# WebSocket 代理

> Source: https://nginx.org/en/docs/http/websocket.html  
> 翻譯：zh-TW（人工校對）· conf 片段保留原文

---

## WebSocket 代理

要把客戶端與伺服器之間的 HTTP/1.1 連線升級成 WebSocket，使用 HTTP/1.1 的 [protocol switch](https://datatracker.ietf.org/doc/html/rfc2616#section-14.42) 機制。

注意：`Upgrade` 是 [hop-by-hop](https://datatracker.ietf.org/doc/html/rfc2616#section-13.5.1) 標頭，**預設不會**從客戶端轉發給被代理的上游。正向代理可用 `CONNECT` 繞過；**反向代理**則必須由 proxy 明確轉傳相關標頭。

自 1.3.13 起，若客戶端請求帶 `Upgrade`，且上游回 **101 Switching Protocols**，nginx 會在客戶端與上游之間建立隧道。

因 hop-by-hop 的 `Upgrade`、`Connection` 不會自動轉發，需**明確設定**：

```nginx
location /chat/ {
    proxy_pass http://backend;
    # proxy_http_version 1.1; # 1.29.7 以前版本需要
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

較完整的寫法：依客戶端是否帶 `Upgrade` 決定 `Connection`：

```nginx
http {
    map $http_upgrade $connection_upgrade {
        default upgrade;
        ''      close;
    }

    server {
        # ...

        location /chat/ {
            proxy_pass http://backend;
            # proxy_http_version 1.1; # 1.29.7 以前版本需要
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection $connection_upgrade;
        }
    }
}
```

### 逾時

預設若上游 **60 秒**內無資料，連線會關閉。可增大 [`proxy_read_timeout`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout)。也可讓上游定期送 WebSocket ping，重置逾時並偵測連線是否仍存活。

### 本專案

參數模板「WebSocket Proxy」即對應上述 `Upgrade` / `Connection` 標頭設定。
