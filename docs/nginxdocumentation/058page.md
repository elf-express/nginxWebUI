# page

> Source: https://nginx.org/en/docs/http/ngx_http_memcached_module.html

---

## 目錄

- [Module ngx\_http\_memcached\_module](#module-ngxhttpmemcachedmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_memcached\_module

`ngx_http_memcached_module`模塊用於從memcached伺服器獲取響應，key設置在`$memcached_key`變量中，響應需要通過nginx外部的方式提前放入memcached中。

#### 配置示例

```nginx
server {
    location / {
        set            $memcached_key "$uri?$args";
        memcached_pass host:11211;
        error_page     404 502 504 = @fallback;
    }

    location @fallback {
        proxy_pass     http://backend;
    }
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_allow_upstream</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

定義允許訪問memcached伺服器的條件或[denied](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#denied)。如果所有字符串參數不為空且不等於「0」，則允許訪問。每次在建立到memcached伺服器的連接之前都會評估這些條件。參數值可以包含變量：

```nginx
geo $upstream_last_addr $allow {
    volatile;
    10.10.0.0/24        1;
}

server {
    listen 127.0.0.1:8080;

    location / {
        memcached_pass           host:11211;
        memcached_allow_upstream $allow;
        ...
    }
}
```

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_bind</strong> <code><i>address</i></code> [<code>transparent </code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在0.8.22版本中。

使到memcached伺服器的傳出連接從指定的本地IP位址發起，並具有可選埠（1.11.2）。參數值可以包含變量（1.3.12）。特殊值`off`（1.3.12）取消了從上一配置級別繼承的`memcached_bind`指令的效果，該指令允許系統自動分配本地IP位址和埠。

`transparent`參數（1.11.0）允許從非本地IP位址（例如，客戶端的真實的IP位址）發出到memcached伺服器的傳出連接：

```nginx
memcached_bind $remote_addr transparent;
```

為了使這個參數生效，通常需要以[superuser](https://nginx.org/en/docs/ngx_core_module.html#user)特權運行nginx工作進程。在Linux上，不需要（1.13.8），因為如果指定了`transparent`參數，工作進程將從主進程繼承`CAP_NET_RAW`能力。還需要配置內核路由表以攔截來自memcached伺服器的網絡流量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

啟用後，在每次連接嘗試時執行[bind](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_bind)操作。

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置緩衝區的`*size*`，緩衝區用於閱讀從memcached伺服器接收到的響應。一旦接收到響應，響應就會同步傳遞給客戶端。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義與memcached伺服器建立連接的超時時間。應該注意的是，此超時時間通常不能超過75秒。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_gzip_flag</strong> <code><i>flag</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.3.6版中。

啟用memcached伺服器響應中`*flag*`存在的測試，並將「`Content-Encoding`」響應標頭欄位設置為「`gzip`」（如果設置了該標誌）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_next_upstream</strong> <code>error</code> | <code>timeout</code> | <code>denied</code> | <code>invalid_response</code> | <code>not_found</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_next_upstream error timeout;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定在哪些情況下應將請求傳遞到下一個伺服器：

`error`

與伺服器建立連接、向其傳遞請求或閱讀響應標頭時出錯;

`timeout`

在與伺服器建立連接、向其傳遞請求或閱讀響應標頭時發生超時;

`denied`

伺服器[denied](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_allow_upstream)連接（1.29.3）;

> >此參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`invalid_response`

伺服器返回了一個空的或無效的響應;

`not_found`

在伺服器上找不到響應;

`off`

禁止將請求傳遞到下一個伺服器。

需要注意的是，只有在客戶端還沒有收到任何消息的情況下，才有可能將請求傳遞給下一個伺服器。也就是說，如果在傳輸響應的過程中發生錯誤或超時，則無法修復。

該指令還定義了與伺服器通信的[unsuccessful attempt](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails)。即使指令中沒有指定，`error`、`timeout`、`denied`和`invalid_response`的情況也總是被認為是不成功的嘗試。`not_found`的情況永遠不會被認為是不成功的嘗試。

將請求傳遞到下一個伺服器可以受到[the number of tries](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_next_upstream_tries)和[time](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_next_upstream_timeout)的限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.5版中。

限制可以將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_next_upstream)的時間。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.5版中。

限制將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_next_upstream)的可能嘗試次數。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_pass</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

設置memcached伺服器地址。地址可以指定為域名或IP位址以及埠：

```nginx
memcached_pass localhost:11211;
```

或者作為UNIX域套接字路徑：

```nginx
memcached_pass unix:/tmp/memcached.socket;
```

如果一個域名解析為多個地址，所有的地址都將以循環方式使用。此外，地址可以指定為[server group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_read_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_read_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義從memcached伺服器閱讀響應的超時。超時僅在兩個連續的讀取操作之間設置，而不是整個響應的傳輸。如果memcached伺服器在此時間內沒有傳輸任何內容，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_send_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_send_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置向memcached伺服器發送請求的超時時間。超時時間只在兩個連續的寫操作之間設置，而不是整個請求的傳輸。如果memcached伺服器在此時間內沒有收到任何東西，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>memcached_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>memcached_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.15.6版中。

為到memcached伺服器的傳出連接配置「TCP keepalive」行為。默認情況下，作業系統的設置對套接字有效。如果該指令設置為值「`on`"，則為套接字啟用`SO_KEEPALIVE`socket選項。

#### 嵌入變量

`$memcached_key`

定義用於從memcached伺服器獲取響應的鍵。