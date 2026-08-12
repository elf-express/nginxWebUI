# page

> Source: https://nginx.org/en/docs/http/ngx_http_v2_module.html

---

## 目錄

- [Module ngx\_http\_v2\_module](#module-ngxhttpv2module)
    - [Known Issues](#known-issues)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_v2\_module

`ngx_http_v2_module`模塊（1.9.5）提供對[HTTP/2](https://datatracker.ietf.org/doc/html/rfc7540)的支持。

默認情況下不構建此模塊，應使用`--with-http_v2_module`配置參數啟用。

#### 已知問題

在版本1.9.14之前，無論指令值為[proxy\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_request_buffering)、[fastcgi\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_request_buffering)、[uwsgi\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_request_buffering)和[scgi\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_request_buffering)，都不能禁用客戶端請求正文的緩衝。

在1.19.1版本之前，[lingering\_close](https://nginx.org/en/docs/http/ngx_http_core_module.html#lingering_close)機制不用於控制關閉HTTP/2連接。

#### 配置示例

```nginx
server {
    listen 443 ssl;

    http2 on;

    ssl_certificate server.crt;
    ssl_certificate_key server.key;
}
```

請注意，通過TLS接受HTTP/2連接需要"應用層協議協商"（ALPN）TLS擴展支持，該支持從[OpenSSL](http://www.openssl.org/)版本1.0.2開始提供。

另外請注意，如果[ssl\_prefer\_server\_ciphers](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_prefer_server_ciphers)指令設置為值"`on`"，則應將[ciphers](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_ciphers)配置為符合[RFC 9113, Appendix A](https://datatracker.ietf.org/doc/html/rfc9113#appendix-A)黑名單並受客戶端支持。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2 off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.25.1版中。

啟用[HTTP/2](https://datatracker.ietf.org/doc/html/rfc9113)協議。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_body_preread_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_body_preread_size 64k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.11.0版本中。

設置每個請求的緩衝區的`*size*`，在開始處理請求之前，請求體可以保存在該緩衝區中。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_chunk_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_chunk_size 8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置響應正文切片的塊的最大大小。值太小會導致更高的開銷。值太大會由於[HOL blocking](http://en.wikipedia.org/wiki/Head-of-line_blocking)而影響優先級。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_idle_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_idle_timeout 3m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

> >此指令自版本1.19.7起已過時。應改用[keepalive\_timeout](https://nginx.org/en/docs/http/ngx_http_core_module.html#keepalive_timeout)指令。

設置連接關閉前的非活動超時。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_max_concurrent_pushes</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_max_concurrent_pushes 10;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.13.9版本中。

> >此指令自版本1.25.1起已過時。

限制連接中並發[push](https://nginx.org/en/docs/http/ngx_http_v2_module.html#http2_push)請求的最大數量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_max_concurrent_streams</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_max_concurrent_streams 128;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

設置連接中並發HTTP/2流的最大數量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_max_field_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_max_field_size 4k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

> >此指令自版本1.19.7起已過時。應改用[large\_client\_header\_buffers](https://nginx.org/en/docs/http/ngx_http_core_module.html#large_client_header_buffers)指令。

限制[HPACK](https://datatracker.ietf.org/doc/html/rfc7541)\壓縮請求頭欄位的最大大小。該限制同樣適用於名稱和值。請注意，如果應用霍夫曼編碼，則解壓縮的名稱和值字符串的實際大小可能會更大。對於大多數請求，默認限制應該足夠。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_max_header_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_max_header_size 16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

> >此指令自版本1.19.7起已過時。應改用[large\_client\_header\_buffers](https://nginx.org/en/docs/http/ngx_http_core_module.html#large_client_header_buffers)指令。

限制[HPACK](https://datatracker.ietf.org/doc/html/rfc7541)解壓縮後整個請求頭列表的最大大小。對於大多數請求，默認限制應該足夠了。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_max_requests</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_max_requests 1000;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.11.6版中。

> >此指令自版本1.19.7起已過時。應改用[keepalive\_requests](https://nginx.org/en/docs/http/ngx_http_core_module.html#keepalive_requests)指令。

設置通過一個HTTP/2連接可以服務的最大請求數（包括[push](https://nginx.org/en/docs/http/ngx_http_v2_module.html#http2_push)請求），超過此數，下一個客戶端請求將導致連接關閉並需要建立新的連接。

需要定期關閉連接以釋放每個連接的內存分配。因此，使用過高的最大請求數可能會導致過多的內存使用，不建議使用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_push</strong> <code><i>uri</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_push off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.13.9版本中。

> >此指令自版本1.25.1起已過時。可以使用[early\_hints](https://nginx.org/en/docs/http/ngx_http_core_module.html#early_hints)指令代替。

先向指定的`*uri*`發送（[pushes](https://datatracker.ietf.org/doc/html/rfc9113#section-8.4)）請求，同時沿著對原始請求的響應。只處理具有絕對路徑的相對URI，例如：

```nginx
http2_push /static/css/main.css;
```

`*uri*`值可以包含變量。

可以在同一配置級別上指定多個`http2_push`指令。`off`參數取消從上一配置級別繼承的`http2_push`指令的效果。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_push_preload</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_push_preload off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.13.9版本中。

> >此指令自版本1.25.1起已過時。

啟用將「Link」響應標頭欄位中指定的[preload links](https://www.w3.org/TR/preload/#server-push-http-2)自動轉換為[push](https://datatracker.ietf.org/doc/html/rfc9113#section-8.4)請求。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_recv_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_recv_buffer_size 256k;</pre></td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

設置每個[worker](https://nginx.org/en/docs/ngx_core_module.html#worker_processes)輸入緩衝區的大小。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>http2_recv_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>http2_recv_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code><br></td></tr></tbody></table>

> >此指令自版本1.19.7起已過時。應改用[client\_header\_timeout](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_header_timeout)指令。

設置等待來自客戶端的更多數據的超時時間，超過此時間連接將關閉。

#### 嵌入變量

`ngx_http_v2_module`模塊支持以下嵌入變量：

`$http2`

協商協議標識符：「`h2`」用於TLS上的HTTP/2，「`h2c`」用於明文TCP上的HTTP/2，否則為空字符串。