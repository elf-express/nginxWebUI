# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html

---

## 目錄

- [Module ngx\_stream\_zone\_sync\_module](#module-ngxstreamzonesyncmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [API endpoints](#api-endpoints)
    - [Starting, stopping, removing a cluster node](#starting-stopping-removing-a-cluster-node)

---

## Module ngx\_stream\_zone\_sync\_module

`ngx_stream_zone_sync_module`模塊（1.13.8）提供了集群內節點間同步[shared memory zones](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)內容的必要支持。要啟用特定區域的同步，相應的模塊必須支持此功能。目前可以同步[http](https://nginx.org/en/docs/http/ngx_http_keyval_module.html)和[stream](https://nginx.org/en/docs/stream/ngx_stream_keyval_module.html)中的HTTP[sticky](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#sticky)會話、[excessive HTTP requests](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)信息和鍵值對。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

最小配置：

> http {
>     ...
> 
>     upstream backend {
>        server backend1.example.com:8080;
>        server backend2.example.com:8081;
> 
>        粘性學習
>               create=$upstream\_cookie\_examplecookie
>               lookup=$cookie\_examplecookie
>               zone=client\_sessions:1m **sync**;
>     }
> 
>     ...
> }
> 
> stream {
>     ...
> 
> 
>     server {
>         zone\_sync;
> 
>         listen 127.0.0.1:12345;
> 
>         # 2個節點的集群
>         zone\_sync\_server a.example.com:12345;
>         zone\_sync\_server b.example.com:12345;
> 
>     }

一個更複雜的配置，啟用了SSL，並由DNS定義了集群成員：

> ...
> 
> stream {
>     ...
> 
>     resolver 127.0.0.1 valid=10s;
> 
>     server {
>         zone\_sync;
> 
>         # 名稱解析為對應於群集節點的多個地址
>         zone\_sync\_server cluster.example.com:12345 resolve;
> 
>         listen 127.0.0.1:4433 ssl;
> 
>         ssl\_certificate     localhost.crt;
>         ssl\_certificate\_key localhost.key;
> 
>         zone\_sync\_ssl on;
> 
>         zone\_sync\_ssl\_certificate     localhost.crt;
>         zone\_sync\_ssl\_certificate\_key localhost.key;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

啟用群集節點之間共享內存區域的同步。群集節點使用[zone\_sync\_server](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_server)指令定義。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_buffers 8 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

設置用於推送區域內容的每個區域緩衝區的`*number*`和`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台，緩衝區大小可以是4K或8K。

> >單個緩衝區必須足夠大，以容納正在同步的每個區域的任何條目。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_connect_retry_interval</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_connect_retry_interval 1s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

定義連接到另一群集節點的嘗試之間的間隔。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_connect_timeout 5s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

定義與另一群集節點建立連接的超時。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_interval</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_interval 1s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

定義輪詢共享內存區域中的更新的時間間隔。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_recv_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_recv_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

設置用於分析傳入同步消息流的每個連接接收緩衝區的`*size*`。緩衝區大小必須等於或大於[zone\_sync\_buffers](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_buffers)之一。默認情況下，緩衝區大小等於[zone\_sync\_buffers](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_buffers)`*size*`乘以`*number*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_server</strong> <code><i>address</i></code> [<code>resolve</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

定義群集節點的`*address*`。地址可以指定為域名或帶有強制埠的IP位址，也可以指定為在"`unix:`"前綴後指定的UNIX域套接字路徑。解析為多個IP位址的域名一次定義多個節點。

`resolve`參數指示nginx監控節點域名對應IP位址的變化，自動修改配置，無需重啟nginx。

群集節點可以動態地指定為帶有`resolve`參數的單個`zone_sync_server`指令，也可以靜態地指定為不帶參數的一系列指令。

> >每個集群節點應該只指定一次。

> >所有群集節點都應使用相同的配置。

為了使`resolve`參數起作用，必須在[stream](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#stream)塊中指定[resolver](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver)指令。示例：

> stream {
>     resolver 10.0.0.1;
> 
>     server {
>         zone\_sync;
>         zone\_sync\_server cluster.example.com:12345 resolve;
>         ...
>     }
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

啟用SSL/TLS協議以連接到另一個群集伺服器。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

指定一個`*file*`，其證書採用PEM格式，用於對另一個群集伺服器進行身份驗證。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_certificate_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

指定一個`*file*`，其密鑰採用PEM格式，用於對另一個群集伺服器進行身份驗證。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl_ciphers DEFAULT;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

指定連接到另一個群集伺服器時啟用的密碼。密碼以OpenSSL庫可以理解的格式指定。

可以使用「`openssl ciphers`」命令查看完整列表。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_conf_command</strong> <code><i>name</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.19.4版中。

在與另一個群集伺服器建立連接時設置任意OpenSSL配置[commands](https://www.openssl.org/docs/man1.1.1/man3/SSL_CONF_cmd.html)。

> >使用OpenSSL 1.0.2或更高版本時支持該指令。

可以在同一級別上指定多個`zone_sync_ssl_conf_command`指令。若且唯若當前級別上沒有定義`zone_sync_ssl_conf_command`指令時，這些指令才從上一配置級別繼承。

> >請注意，直接配置OpenSSL可能會導致意外行為。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

以PEM格式指定一個具有已吊銷證書（CRL）的`*file*`，用於[verify](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_ssl_verify)另一個群集伺服器的證書。使用中間證書時，應在同一文件中指定它們的CRL。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl_name host from zone_sync_server;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.15.7版本中。

Allows overriding the server name used to [verify](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_ssl_verify) the certificate of a cluster server and to be [passed through SNI](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_ssl_server_name) when establishing a connection with the cluster server.

默認情況下，使用[zone\_sync\_server](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_server)地址的主機部分，如果指定了[resolve](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#resolve)參數，則使用解析的IP位址。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_password_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

為[secret keys](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_ssl_certificate_key)指定帶密碼的`*file*`，其中每個密碼在單獨的行上指定。加載密鑰時依次嘗試密碼。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_protocols</strong> [<code>SSLv2</code>] [<code>SSLv3</code>] [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl_protocols TLSv1.2 TLSv1.3;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

為到另一個群集伺服器的連接啟用指定的協議。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_server_name</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl_server_name off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.15.7版本中。

啟用或禁用在與另一個群集伺服器建立連接時通過[TLS Server Name Indication extension](http://en.wikipedia.org/wiki/Server_Name_Indication)（SNI，RFC 6066）傳遞伺服器名稱。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

指定一個具有PEM格式的受信任CA證書的`*file*`，用於[verify](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync_ssl_verify)另一個群集伺服器的證書。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl_verify off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

啟用或禁用另一個群集伺服器證書的驗證。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_ssl_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_ssl_verify_depth 1;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

設置另一個群集伺服器證書鏈中的驗證深度。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone_sync_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>zone_sync_timeout 5s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

設置連接到另一個集群節點時兩次連續讀或寫操作之間的`*timeout*`。如果在此時間內沒有數據傳輸，則關閉連接。

#### API端點

節點的同步狀態可通過返回[following](https://nginx.org/en/docs/http/ngx_http_api_module.html#def_nginx_stream_zone_sync)指標的API的[/stream/zone\_sync/](https://nginx.org/en/docs/http/ngx_http_api_module.html#stream_zone_sync_)端點獲得。

#### 啟動、停止、刪除集群節點

要啟動新節點，請使用新節點的IP位址更新集群主機名的DNS記錄並啟動實例。新節點將通過DNS或靜態配置發現其他節點，並開始向它們發送更新。其他節點最終將使用DNS發現新節點並開始向其推送更新。如果是靜態配置，其它節點需要被重新加載以便向新節點發送更新。

若要停止節點，請向實例發送`QUIT`信號。節點將完成區域同步並正常關閉打開的連接。

要刪除節點，請更新群集主機名的DNS記錄並刪除該節點的IP位址。所有其他節點最終都會發現該節點已被刪除，關閉與該節點的連接，並且不再嘗試連接該節點。在刪除該節點後，可以如上所述停止該節點。在靜態配置的情況下，其它節點需要被重新加載以便停止向被移除的節點發送更新。