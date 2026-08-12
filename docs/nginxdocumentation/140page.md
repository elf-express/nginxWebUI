# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html

---

## 目錄

- [Module ngx\_stream\_upstream\_hc\_module](#module-ngxstreamupstreamhcmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_upstream\_hc\_module

`ngx_stream_upstream_hc_module`模塊（1.9.0）允許對[group](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#upstream)中的伺服器啟用定期健康檢查。伺服器組必須位於[shared memory](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)中。

如果運行狀況檢查失敗，則伺服器將被視為不健康。如果為同一組伺服器定義了多個運行狀況檢查，則任何一個檢查失敗都會使相應的伺服器被視為不健康。客戶端連接不會傳遞到不健康的伺服器和處於「檢查」狀態的伺服器。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

```nginx
upstream tcp {
    zone upstream_tcp 64k;

    server backend1.example.com:12345 weight=5;
    server backend2.example.com:12345 fail_timeout=5s slow_start=30s;
    server 192.0.2.1:12345            max_fails=3;

    server backup1.example.com:12345  backup;
    server backup2.example.com:12345  backup;
}

server {
    listen     12346;
    proxy_pass tcp;
    health_check;
}
```

在此配置下，nginx會每隔5秒檢查一次與`tcp`組中每台伺服器建立TCP連接的能力。當無法與伺服器建立連接時，健康檢查將失敗，伺服器將被視為不健康。

可以為UDP協議配置運行狀況檢查：

```nginx
upstream dns_upstream {

    zone   dns_zone 64k;

    server dns1.example.com:53;
    server dns2.example.com:53;
    server dns3.example.com:53;
}

server {
    listen       53 udp;
    proxy_pass   dns_upstream;
    health_check udp;
}
```

在這種情況下，在對發送的字符串「`nginx health check`"的回覆中，預期不存在「`Destination Unreachable`」消息。

運行狀況檢查也可以配置為測試從伺服器獲取的數據。測試使用[match](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#match)指令單獨配置，並在[health\_check](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#health_check)指令的`match`參數中引用。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>health_check</strong> [<code><i>parameters</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

啟用[group](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#upstream)中伺服器的定期運行狀況檢查。

支持以下可選參數：

`interval`\=`*time*`

設置兩次連續運行狀況檢查之間的間隔，默認為5秒。

`jitter`\=`*time*`

設置每次運行狀況檢查隨機延遲的時間，默認無延遲。

`fails`\=`*number*`

設置特定伺服器連續失敗的運行狀況檢查次數，超過此次數，此伺服器將被視為不正常，默認情況下為1。

`passes`\=`*number*`

設置連續通過特定伺服器的健康檢查的次數，在此次數之後，伺服器將被視為健康，默認情況下為1。

`mandatory` \[`persistent`\]

設置伺服器的初始「檢查」狀態，直到第一次健康檢查完成（1.11.7）。客戶端連接不會傳遞到處於「檢查」狀態的伺服器。如果未指定該參數，則伺服器最初將被認為是健康的。

如果伺服器在重新加載之前被認為是健康的，則`persistent`參數（1.21.1）在重新加載之後設置伺服器的初始「up」狀態。

`match`\=`*name*`

指定配置測試的`match`塊，成功的連接應通過這些測試，以便通過運行狀況檢查。默認情況下，對於TCP，僅檢查與伺服器建立TCP連接的能力。對於[UDP](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#health_check_udp)，在對發送的字符串「`nginx health check`"的答覆中，預期不存在「`Destination Unreachable`」消息。

> >在1.11.7版本之前，默認情況下，UDP健康檢查需要一個帶有[send](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#match_send)和[expect](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#match_expect)參數的[match](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#hc_match)塊。

`port`\=`*number*`

定義連接到伺服器以執行健康檢查時使用的埠（1.9.7）。默認情況下，等於[server](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)埠。

`udp`

指定運行狀況檢查應使用`UDP`協議，而不是默認的`TCP`協議（1.9.13）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>health_check_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>health_check_timeout 5s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

覆蓋運行狀況檢查的[proxy\_timeout](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_timeout)值。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>match</strong> <code><i>name</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

定義用於驗證伺服器對運行狀況檢查的響應的命名測試集。

可以配置以下參數：

`send` `*string*`;

向伺服器發送一個`*string*`;

`expect` `*string*` | `~` `*regex*`;

從伺服器獲取的數據應該匹配的文字字符串（1.9.12）或正則表達式。正則表達式是用前面的「`~*`」修飾符（用於不區分大小寫的匹配）或「`~`」修飾符（用於區分大小寫的匹配）指定的。

`send`和`expect`參數都可以包含前綴為「`\x`」後跟兩個十六進位數字的十六進位文字，例如「`\x80`」（1.9.12）。

如果滿足以下條件，則通過運行狀況檢查：

-   TCP連接已成功建立;
-   來自`send`參數的`*string*`（如果指定）已發送;
-   從伺服器獲取的數據與`expect`參數中的字符串或正則表達式匹配（如果指定）;
-   經過的時間不超過[health\_check\_timeout](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#health_check_timeout)指令中指定的值。

Example:

```nginx
upstream backend {
    zone     upstream_backend 10m;
    server   127.0.0.1:12345;
}

match http {
    send     "GET / HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n";
    expect ~ "200 OK";
}

server {
    listen       12346;
    proxy_pass   backend;
    health_check match=http;
}
```

> >只檢查從伺服器獲取的前[proxy\_buffer\_size](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_buffer_size)字節數據。