# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html

---

## 目錄

- [Module ngx\_stream\_upstream\_module](#module-ngxstreamupstreammodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_stream\_upstream\_module

`ngx_stream_upstream_module`模塊（1.9.0）用於定義可由[proxy\_pass](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_pass)指令引用的伺服器組。

#### 配置示例

> upstream **backend** {
>     hash $remote\_addr consistent;
> 
>     server backend1.example.com:12345  weight=5;
>     server backend2.example.com:12345;
>     server unix:/tmp/backend3;
> 
>     server backup1.example.com:12345   backup;
>     server backup2.example.com:12345   backup;
> }
> 
> server {
>     listen 12346;
>     proxy\_pass **backend**;
> }

具有周期性[health checks](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html)的動態可配置組可作為我們的[commercial subscription](https://www.f5.com/products/nginx)：

> resolver 10.0.0.1;
> 
> upstream **dynamic** {
>     zone upstream\_dynamic 64k;
> 
>     server backend1.example.com:12345 weight=5;
>     server backend2.example.com:12345 fail\_timeout=5s slow\_start=30s;
>     server 192.0.2.1:12345            max\_fails=3;
>     server backend3.example.com:12345 resolve;
>     server backend4.example.com       service=http resolve;
> 
>     server backup1.example.com:12345  backup;
>     server backup2.example.com:12345  backup;
> }
> 
> server {
>     listen 12346;
>     proxy\_pass **dynamic**;
>     health\_check;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>upstream</strong> <code><i>name</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

定義一組伺服器。伺服器可以監聽不同的埠。此外，監聽TCP和UNIX域套接字的伺服器可以混合使用。

Example:

> upstream backend {
>     server backend1.example.com:12345 weight=5;
>     server 127.0.0.1:12345            max\_fails=3 fail\_timeout=30s;
>     server unix:/tmp/backend2;
>     server backend3.example.com:12345 resolve;
> 
>     server backup1.example.com:12345  backup;
> }

默認情況下，伺服器之間的連接分配採用加權輪詢均衡方式。在上例中，每7個連接分配如下：5個連接到`backend1.example.com:12345`，第二個和第三個伺服器各有一個連接。如果在與伺服器通信時發生錯誤，則連接將傳遞到下一個伺服器，以此類推，直到嘗試所有運行中的伺服器。如果與所有伺服器的通信失敗，連接將被關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server</strong> <code><i>address</i></code> [<code><i>parameters</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

定義伺服器的`*address*`和其他`*parameters*`。地址可以指定為域名或帶有強制埠的IP位址，也可以指定為在「`unix:`」前綴後指定的UNIX域套接字路徑。解析為多個IP位址的域名一次定義多個伺服器。

可以定義以下參數：

`weight`\=`*number*`

設置伺服器的權重，默認為1。

`max_conns`\=`*number*`

限制到代理伺服器的最大同時連接數`*number*`（1.11.5）。默認值為零，表示沒有限制。如果伺服器組不位於[shared memory](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)中，則限制對每個工作進程有效。

> >在版本1.11.5之前，此參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`max_fails`\=`*number*`

設置在由`fail_timeout`參數設置的持續時間內與伺服器通信的不成功嘗試次數，以將伺服器視為在由`fail_timeout`參數設置的持續時間內不可用。默認情況下，不成功嘗試次數設置為1。零值禁用嘗試計數。此處，不成功的嘗試是在與伺服器建立連接時的錯誤或超時。

`fail_timeout`\=`*time*`

sets

-   在指定次數的不成功嘗試與伺服器通信時，應考慮伺服器不可用的時間;
-   以及伺服器將被認為不可用的時間段。

默認情況下，該參數設置為10秒。

`backup`

將伺服器標記為備份伺服器。當主伺服器不可用時，將傳遞到備份伺服器的連接。

> >該參數不能沿著與[hash](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#hash)和[random](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#random)負載平衡方法一起使用。

`down`

將伺服器標記為永久不可用。

`resolve`

監控伺服器域名對應IP位址的變化，自動修改上游配置，無需重啟nginx，伺服器組必須位於[shared memory](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)。

為了使此參數起作用，必須在[stream](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver)塊或相應的[upstream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#resolver)塊中指定`resolver`指令。

> >在1.27.3版本之前，此參數僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`service`\=`*name*`

啟用DNS[SRV](https://datatracker.ietf.org/doc/html/rfc2782)記錄的解析並設置服務`*name*`（1.9.13）。為了使此參數起作用，必須為伺服器指定[resolve](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#resolve)參數並指定不帶埠號的主機名。

如果服務名稱不包含點（「`.`」），則構造符合[RFC](https://datatracker.ietf.org/doc/html/rfc2782)\-的名稱，並將TCP協議添加到服務前綴。例如，要查找`_http._tcp.backend.example.com`SRV記錄，需要指定以下指令：

> server backend.example.com service=http resolve;

如果服務名包含一個或多個點，則通過連接服務前綴和伺服器名來構造名稱。例如，要查找`_http._tcp.backend.example.com`和`server1.backend.example.com`SRV記錄，需要指定以下指令：

> server backend.example.com service=\_http.\_tcp resolve;
> server example.com service=server1.backend resolve;

最高優先級的SRV記錄（具有相同的最低編號優先級值的記錄）被解析為主伺服器，其餘的SRV記錄被解析為備份伺服器。如果為伺服器指定了[backup](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#backup)參數，則高優先級的SRV記錄被解析為備份伺服器，其餘的SRV記錄被忽略。

> >在1.27.3版本之前，此參數僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

此外，以下參數可作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分：

`slow_start`\=`*time*`

設置`*time*`，在此期間，當不健康的伺服器變為[healthy](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#health_check)時，或當伺服器在被認為是[unavailable](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#fail_timeout)的一段時間後變為可用時，伺服器將從零恢復其權重到標稱值。默認值為零，即禁用慢啟動。

> >該參數不能沿著與[hash](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#hash)和[random](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#random)負載平衡方法一起使用。

> >如果組中只有一台伺服器，則忽略`max_fails`、`fail_timeout`和`slow_start`參數，這樣的伺服器永遠不會被視為不可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone</strong> <code><i>name</i></code> [<code><i>size</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

定義共享內存區域的`*name*`和`*size*`，用於保存工作進程之間共享的組配置和運行時狀態。多個組可以共享同一區域。在這種情況下，只需指定一次`*size*`即可。

此外，作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分，此類組允許更改組成員身份或修改特定伺服器的設置，而無需重新啟動nginx。配置可通過[API](https://nginx.org/en/docs/http/ngx_http_api_module.html)模塊（1.13.3）訪問。

> >在版本1.13.3之前，配置只能通過由[upstream\_conf](https://nginx.org/en/docs/http/ngx_http_upstream_conf_module.html#upstream_conf)處理的特殊位置訪問。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>state</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.9.7版本中。

指定一個保持動態可配置組狀態的`*file*`。

Examples:

> state /var/lib/nginx/state/servers.conf; # path for Linux
> state /var/db/nginx/state/servers.conf;  # path for FreeBSD

當前狀態僅限於伺服器及其參數的列表。解析配置時讀取文件，每次上游配置為[changed](https://nginx.org/en/docs/http/ngx_http_api_module.html#stream_upstreams_stream_upstream_name_servers_)時更新文件。應避免直接更改文件內容。指令不能沿著與[server](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)指令一起使用。

> >在[configuration reload](https://nginx.org/en/docs/control.html#reconfiguration)或[binary upgrade](https://nginx.org/en/docs/control.html#upgrade)期間所做的更改可能會丟失。

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hash</strong> <code><i>key</i></code> [<code>consistent</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

指定伺服器組的負載平衡方法，其中客戶端-伺服器映射基於哈希值`*key*`。`*key*`可以包含文本、變量及其組合（1.11.2）。用法示例：

> hash $remote\_addr;

請注意，在組中添加或刪除伺服器可能會導致將大多數鍵重新映射到不同的伺服器。該方法與[Cache::Memcached](https://metacpan.org/pod/Cache::Memcached)Perl庫兼容。

如果指定了`consistent`參數，則會使用[ketama](https://www.metabrew.com/article/libketama-consistent-hashing-algo-memcached-clients)一致性哈希方法。該方法可以確保在組中添加或刪除伺服器時，只有少數鍵會重新映射到不同的伺服器。這有助於為緩存伺服器實現更高的緩存命中率。該方法與`*ketama_points*`參數設置為160的[Cache::Memcached::Fast](https://metacpan.org/pod/Cache::Memcached::Fast)Perl庫兼容。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>least_conn</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

指定組應使用負載平衡方法，其中將連接傳遞到活動連接數最少的伺服器，同時考慮伺服器的權重。如果有多個這樣的伺服器，則使用加權循環平衡方法依次嘗試。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>least_time</strong> <code>connect</code> | <code>first_byte</code> | <code>last_byte</code> [<code>inflight</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.7.11版本中。

指定組應使用負載平衡方法，其中將連接傳遞到平均時間最短且活動連接數最少的伺服器，同時考慮伺服器的權重。如果有多個這樣的伺服器，則使用加權循環平衡方法依次嘗試。

如果指定了`connect`參數，則使用到上游伺服器的[connect](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_connect_time)的時間。如果指定了`first_byte`參數，則使用到接收數據的[first byte](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_first_byte_time)的時間。如果指定了`last_byte`，則使用到接收數據的[last byte](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_session_time)的時間。如果指定了`inflight`參數（1.11.6），則還考慮不完整的連接。

> >在1.11.6版本之前，默認情況下會考慮不完整的連接。

> >在1.31.0版本之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>random</strong> [<code>two</code> [<code><i>method</i></code>]];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.15.1版中。

指定組應使用負載平衡方法，其中將連接傳遞到隨機選擇的伺服器，同時考慮伺服器的權重。

可選的`two`參數指示nginx隨機選擇[two](https://homes.cs.washington.edu/~karlin/papers/balls.pdf)個伺服器，然後使用指定的`method`選擇一個伺服器。默認方法是`least_conn`，它將連接傳遞到活動連接數最少的伺服器。

`least_time`方法將連接傳遞到具有最短平均時間和最少活動連接數的伺服器。如果指定了`least_time=connect`參數，則使用到上游伺服器的[connect](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_connect_time)時間。如果指定了`least_time=first_byte`參數，則使用到接收[first byte](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_first_byte_time)數據的時間。如果指定了`least_time=last_byte`，則使用到接收[last byte](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_session_time)數據的時間。

> >`least_time`方法是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver</strong> <code><i>address</i></code> ... [<code>valid</code>=<code><i>time</i></code>] [<code>ipv4</code>=<code>on</code>|<code>off</code>] [<code>ipv6</code>=<code>on</code>|<code>off</code>] [<code>status_zone</code>=<code><i>zone</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.27.3版中。

配置用於將上游伺服器的名稱解析為地址的名稱伺服器，例如：

> resolver 127.0.0.1 \[::1\]:5353;

地址可以指定為域名或IP位址，並帶有可選埠。如果未指定埠，則使用埠53。名稱伺服器以循環方式查詢。

默認情況下，nginx在解析時會同時查找IPv4和IPv6地址。如果不需要查找IPv4或IPv6地址，可以指定`ipv4=off`（1.23.1）或`ipv6=off`參數。

默認情況下，nginx會使用響應的TTL值來緩存答案。可選的`valid`參數允許覆蓋它：

> resolver 127.0.0.1 \[::1\]:5353 valid=30s;

> >為了防止DNS欺騙，建議在適當安全的可信本地網絡中配置DNS伺服器。

可選的`status_zone`參數（1.17.5）在指定的`*zone*`中啟用[collection](https://nginx.org/en/docs/http/ngx_http_api_module.html#resolvers_)的請求和響應的DNS伺服器統計信息。該參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

> >自版本1.17.5和版本1.27.3之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>resolver_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.27.3版中。

設置名稱解析的超時，例如：

> resolver\_timeout 5s;

> >自版本1.17.5和版本1.27.3之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

#### 嵌入變量

`ngx_stream_upstream_module`模塊支持以下嵌入變量：

`$upstream_addr`

保留IP位址和埠，或到上游伺服器的UNIX域套接字的路徑（1.11.4）。如果在重定向期間聯繫了多個伺服器，則它們的地址用逗號分隔，例如"`192.168.1.1:12345, 192.168.1.2:12345, unix:/tmp/sock`"。如果無法選擇伺服器，則該變量保留伺服器組的名稱。

`$upstream_bytes_received`

從上游伺服器接收的字節數（1.11.4）。來自幾個連接的值用逗號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_bytes_sent`

發送到上游伺服器的字節數（1.11.4）。來自多個連接的值用逗號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_connect_time`

連接到上游伺服器的時間（1.11.4）;時間以秒為單位，毫秒解析度。幾個連接的時間用逗號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_first_byte_time`

接收數據的第一個字節的時間（1.11.4）;時間以秒為單位，解析度為毫秒。幾個連接的時間用逗號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_last_addr`

保持IP位址或路逕到最後選擇的上游伺服器的UNIX域套接字（1.29.3）。

> >此變量作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`$upstream_session_time`

會話持續時間以秒為單位，解析度為毫秒（1.11.4）。幾個連接的時間用逗號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#var_upstream_addr)變量中的地址一樣。