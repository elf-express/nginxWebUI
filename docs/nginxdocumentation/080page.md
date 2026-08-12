# page

> Source: https://nginx.org/en/docs/http/ngx_http_tunnel_module.html

---

## 目錄

- [Module ngx\_http\_tunnel\_module](#module-ngxhttptunnelmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_tunnel\_module

`ngx_http_tunnel_module`（1.31.0）處理HTTP/1.1[CONNECT](https://datatracker.ietf.org/doc/html/rfc9110#section-9.3.6)請求並建立端到端虛擬連接。

#### 配置示例

> http {
> 
>     map $request\_port $allow\_port {
>         443            1;
>     }
> 
>     map $host $allow\_host {
>         hostnames;
> 
>         example.org    1;
>         \*.example.org  1;
>     }
> 
>     server {
>         listen 8000;
> 
>         resolver dns.example.com;
> 
>         if ($allow\_port != 1) {
>             return 502;
>         }
> 
>         if ($allow\_host != 1) {
>             return 502;
>         }
> 
>         tunnel\_pass;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_allow_upstream</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義允許訪問後端伺服器的條件或[denied](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#denied)。如果所有字符串參數都不為空且不等於「0」，則允許訪問。每次在建立到後端伺服器的連接之前，都會評估這些條件。參數值可以包含變量：

> geo $upstream\_last\_addr $allow {
>     volatile;
>     10.10.0.0/24        1;
> }
> 
> server {
>     listen 127.0.0.1:8080;
> 
>     tunnel\_pass;
>     tunnel\_allow\_upstream $allow;
> }

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_bind</strong> <code><i>address</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

使到後端伺服器的傳出連接從指定的本地IP位址發起，並具有可選埠。參數值可以包含變量。特殊值`off`取消了從上一配置級別繼承的`tunnel_bind`指令的效果，該指令允許系統自動分配本地IP位址和埠。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

啟用後，在每次連接嘗試時執行[tunnel\_bind](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#tunnel_bind)操作：

> geo $upstream\_last\_addr $bind\_addr {
>     volatile;
>     10.0.0.0/24    10.0.0.1;
>     192.168.0.0/24  192.168.0.1;
> }
> 
> tunnel\_bind         $bind\_addr;
> tunnel\_bind\_dynamic on;

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_buffer_size 16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於從後端伺服器閱讀數據的緩衝區的`*size*`。還設置用於從客戶端閱讀數據的緩衝區的`*size*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義與後端伺服器建立連接的超時時間。需要注意的是，此超時時間通常不能超過75秒。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_next_upstream</strong> <code>error</code> | <code>timeout</code> | <code>denied</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_next_upstream error timeout;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定在哪些情況下應將請求傳遞到下一個伺服器：

`error`

與伺服器建立連接或從伺服器閱讀數據時出錯;

`timeout`

與伺服器建立連接、向其傳遞請求或從伺服器閱讀數據時發生超時;

`denied`

伺服器[denied](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#tunnel_allow_upstream)連接;

> >此參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`off`

禁止將請求傳遞到下一個伺服器。

需要注意的是，只有在客戶端還沒有收到任何消息的情況下，才有可能將請求傳遞給下一個伺服器。也就是說，如果在傳輸響應的過程中發生錯誤或超時，則無法修復。

該指令還定義了與伺服器通信的[unsuccessful attempt](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails)情況。`error`、`timeout`和`denied`情況始終被視為不成功的嘗試，即使指令中沒有指定。

將請求傳遞到下一個伺服器可以受到[the number of tries](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#tunnel_next_upstream_tries)和[time](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#tunnel_next_upstream_timeout)的限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

限制可以將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#tunnel_next_upstream)的時間。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

限制將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html#tunnel_next_upstream)的可能嘗試次數。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_pass</strong> [<code><i>address</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

允許處理BPECT請求並設置後端伺服器的地址。默認情況下，`address`為`*$host:$request_port*`，取自客戶端請求。在大多數情況下，`tunnel_pass`不需要配置任何參數。

地址可以指定為域名或IP位址以及埠：

> tunnel\_pass localhost:9000;

或者作為UNIX域套接字路徑：

> tunnel\_pass unix:/tmp/backend.socket;

如果一個域名解析為多個地址，所有的地址都將以循環方式使用。此外，地址可以指定為[server group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)。

參數值可以包含變量。在這種情況下，如果地址被指定為域名，則在所描述的[server groups](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)中搜索該名稱，如果沒有找到，則使用[resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver)確定。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_read_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_read_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置客戶端或後端伺服器連接上的兩個連續讀或寫操作之間的超時。如果在此時間內沒有傳輸數據，則關閉連接。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_send_lowat</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_send_lowat 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果該指令被設置為非零值，nginx將嘗試通過使用[kqueue](https://nginx.org/en/docs/events.html#kqueue)方法的`NOTE_LOWAT`標誌或`SO_SNDLOWAT`socket選項，以及指定的`*size*`，最大限度地減少到後端伺服器的傳出連接上的發送操作數量。

在Linux、Solaris和Windows上忽略此指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_send_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_send_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置向後端伺服器發送請求的超時。超時僅在兩個連續的寫操作之間設置，而不是整個請求的傳輸。如果後端伺服器在此時間內沒有收到任何內容，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tunnel_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為到後端伺服器的傳出連接配置「TCP keepalive」行為。默認情況下，作業系統的設置對套接字有效。如果該指令設置為值「`on`"，則為套接字啟用`SO_KEEPALIVE`socket選項。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_socket_rcvbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

設置到後端伺服器的傳出連接的接收緩衝區大小（`SO_RCVBUF`選項）。特殊值`0`取消了從以前的配置級別繼承的`tunnel_socket_rcvbuf`指令的效果，這允許保持作業系統的設置對套接字有效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tunnel_socket_sndbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

設置發送緩衝區大小（`SO_SNDBUF`選項）用於到後端伺服器的傳出連接。特殊值`0`取消了從以前的配置級別繼承的`tunnel_socket_sndbuf`指令的效果，這允許保持作業系統的設置對套接字有效。