# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_ssl_preread_module.html

---

## 目錄

- [Module ngx\_stream\_ssl\_preread\_module](#module-ngxstreamsslprereadmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_stream\_ssl\_preread\_module

`ngx_stream_ssl_preread_module`模塊（1.11.5）允許在不終止SSL/TLS的情況下從[ClientHello](https://datatracker.ietf.org/doc/html/rfc5246#section-7.4.1.2)消息中提取信息，例如，通過[SNI](https://datatracker.ietf.org/doc/html/rfc6066#section-3)請求的伺服器名稱或在[ALPN](https://datatracker.ietf.org/doc/html/rfc7301)中通告的協議。默認情況下不構建此模塊，應使用`--with-stream_ssl_preread_module`配置參數啟用它。

#### 配置示例

根據伺服器名稱選擇上游：

```nginx
map $ssl_preread_server_name $name {
    backend.example.com      backend;
    default                  backend2;
}

upstream backend {
    server 192.168.0.1:12345;
    server 192.168.0.2:12345;
}

upstream backend2 {
    server 192.168.0.3:12345;
    server 192.168.0.4:12345;
}

server {
    listen      12346;
    proxy_pass  $name;
    ssl_preread on;
}
```

根據協議選擇上游：

```nginx
map $ssl_preread_alpn_protocols $proxy {
    ~\bh2\b           127.0.0.1:8001;
    ~\bhttp/1.1\b     127.0.0.1:8002;
    ~\bxmpp-client\b  127.0.0.1:8003;
}

server {
    listen      9000;
    proxy_pass  $proxy;
    ssl_preread on;
}
```

根據SSL協議版本選擇上游：

```nginx
map $ssl_preread_protocol $upstream {
    ""        ssh.example.com:22;
    "TLSv1.2" new.example.com:443;
    default   tls.example.com:443;
}

# ssh和https在同一個埠上
server {
    listen      192.168.0.1:443;
    proxy_pass  $upstream;
    ssl_preread on;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_preread</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_preread off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

啟用在[preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase)階段從ClientHello消息中提取信息。

#### 嵌入變量

`$ssl_preread_protocol`

客戶端支持的最高SSL協議版本（1.15.2）

`$ssl_preread_server_name`

通過SNI請求的伺服器名稱

`$ssl_preread_alpn_protocols`

客戶端通過ALPN（1.13.10）通告的協議列表。值用逗號分隔。