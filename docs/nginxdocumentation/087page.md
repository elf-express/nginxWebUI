# page

> Source: https://nginx.org/en/docs/http/ngx_http_v3_module.html

---

## 目錄

- [Module ngx\_http\_v3\_module](#module-ngxhttpv3module)
    - [Known Issues](#known-issues)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_v3\_module

`ngx_http_v3_module`模塊（1.25.0）為[HTTP/3](https://datatracker.ietf.org/doc/html/rfc9114)提供實驗支持。

默認情況下不構建此模塊，應使用`--with-http_v3_module`配置參數啟用。

> >此模塊需要[OpenSSL](http://www.openssl.org/)庫版本1.1.1或更高版本。

> 0-RTT支持需要[OpenSSL](http://www.openssl.org/)庫版本3.5.1或更高版本。或者，[BoringSSL](https://boringssl.googlesource.com/boringssl)、[LibreSSL](https://www.libressl.org/)或[QuicTLS](https://github.com/quictls/openssl)庫可用於構建和運行此模塊。

#### 已知問題

該模塊是實驗性的，買者自負適用。

在版本1.29.1之前，無論[ssl\_early\_data](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_early_data)指令值如何，都無法使用OpenSSL啟用0-RTT支持。

該模塊不能在Win32平台上構建。

#### 配置示例

```nginx
http {
    log_format quic '$remote_addr - $remote_user [$time_local] '
                    '"$request」$status $body_bytes_sent '
                    '"$http_referer" "$http_user_agent" "$http3"';

    access_log logs/access.log quic;

    server {
        # 為了更好的兼容性，建議
        # 使用相同的埠為http/3和https
        listen 8443 quic reuseport;
        listen 8443 ssl;

        ssl_certificate     certs/example.com.crt;
        ssl_certificate_key certs/example.com.key;

        location / {
            # 用於通告HTTP/3的可用性
            add_header Alt-Svc 'h3=":8443"; ma=86400';
        }
    }
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http3</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http3 on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

啟用[HTTP/3](https://datatracker.ietf.org/doc/html/rfc9114)協議協商。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http3_hq</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http3_hq off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

啟用在[QUIC interoperability tests](https://github.com/marten-seemann/quic-interop-runner)中使用的HTTP/0.9協議協商。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http3_max_concurrent_streams</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http3_max_concurrent_streams 128;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

設置連接中並發HTTP/3請求流的最大數量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http3_stream_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http3_stream_buffer_size 64k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

設置用於閱讀和寫入QUIC流的緩衝區的大小。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>quic_active_connection_id_limit</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>quic_active_connection_id_limit 2;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

設置QUIC`active_connection_id_limit`傳輸參數值。這是伺服器上可以存儲的客戶端連接ID的最大數量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>quic_bpf</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>quic_bpf off;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

使用[eBPF](https://ebpf.io/)啟用QUIC數據包的路由。啟用後，將支持QUIC連接遷移。

> >該指令僅在Linux 5.7+上受支持。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>quic_gso</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>quic_gso off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

啟用使用分段卸載的優化批處理模式發送。

> >只有支持`UDP_SEGMENT`的Linux才支持優化發送。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>quic_host_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

使用用於加密無狀態重置和地址驗證令牌的密鑰設置`*file*`。默認情況下，每次重新加載時都會生成隨機密鑰。不接受使用舊密鑰生成的令牌。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>quic_retry</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>quic_retry off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

啟用[QUIC Address Validation](https://datatracker.ietf.org/doc/html/rfc9000#name-address-validation)功能。這包括在`Retry`數據包或`NEW_TOKEN`幀中發送新令牌，並驗證在`Initial`數據包中接收到的令牌。

#### 嵌入變量

`ngx_http_v3_module`模塊支持以下嵌入變量：

`$http3`

協商的協議標識符：「`h3`」用於HTTP/3連接，「`hq`」用於hq連接，否則為空字符串。