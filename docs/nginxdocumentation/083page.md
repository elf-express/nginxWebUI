# page

> Source: https://nginx.org/en/docs/http/ngx_http_upstream_module.html

---

## 目錄

- [Module ngx\_http\_upstream\_module](#module-ngxhttpupstreammodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_upstream\_module

`ngx_http_upstream_module`模塊用於定義可由[proxy\_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)、[fastcgi\_pass](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_pass)、[uwsgi\_pass](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_pass)、[scgi\_pass](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_pass)、[memcached\_pass](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_pass)和[grpc\_pass](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_pass)指令引用的伺服器組。

#### 配置示例

```nginx
upstream **backend** {
    server backend1.example.com       weight=5;
    server backend2.example.com:8080;
    server unix:/tmp/backend3;

    server backup1.example.com:8080   backup;
    server backup2.example.com:8080   backup;
}

server {
    location / {
        proxy_pass http://**backend**;
    }
}
```

具有周期性[health checks](https://nginx.org/en/docs/http/ngx_http_upstream_hc_module.html)的動態可配置組可作為我們的[commercial subscription](https://www.f5.com/products/nginx)：

```nginx
resolver 10.0.0.1;

upstream **dynamic** {
    zone upstream_dynamic 64k;

    server backend1.example.com      weight=5;
    server backend2.example.com:8080 fail_timeout=5s slow_start=30s;
    server 192.0.2.1                 max_fails=3;
    server backend3.example.com      resolve;
    server backend4.example.com      service=http resolve;

    server backup1.example.com:8080  backup;
    server backup2.example.com:8080  backup;
}

server {
    location / {
        proxy_pass http://**dynamic**;
        health_check;
    }
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>upstream</strong> <code><i>name</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

定義一組伺服器。伺服器可以監聽不同的埠。此外，監聽TCP和UNIX域套接字的伺服器可以混合使用。

Example:

```nginx
upstream backend {
    server backend1.example.com weight=5;
    server 127.0.0.1:8080       max_fails=3 fail_timeout=30s;
    server unix:/tmp/backend3;

    server backup1.example.com  backup;
}
```

默認情況下，請求在伺服器之間採用加權輪詢均衡方式進行分配。在上例中，每7個請求將按如下方式進行分配：5個請求發送到`backend1.example.com`，第二個和第三個伺服器各發送一個請求。如果在與伺服器通信時發生錯誤，則請求將被傳遞到下一個伺服器，如此類推，直到所有運行中的伺服器都將被嘗試。如果不能從任何一個伺服器獲得成功的響應，則客戶端將接收與最後一個伺服器的通信結果。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server</strong> <code><i>address</i></code> [<code><i>parameters</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

定義伺服器的`*address*`和其他`*parameters*`。地址可以指定為域名或IP位址，帶有可選埠，也可以指定為在「`unix:`」前綴後指定的UNIX域套接字路徑。如果未指定埠，則使用埠80。解析為多個IP位址的域名一次定義多個伺服器。

可以定義以下參數：

`weight`\=`*number*`

設置伺服器的權重，默認為1。

`max_conns`\=`*number*`

限制到代理伺服器的同時活動連接的最大值`*number*`（1.11.5）。默認值為零，意味著沒有限制。如果伺服器組不在[shared memory](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)中，則每個工作進程都有限制。

> >如果啟用了[idle keepalive](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#keepalive)連接、多個[workers](https://nginx.org/en/docs/ngx_core_module.html#worker_processes)和[shared memory](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)，則到代理伺服器的活動和空閒連接總數可能會超過`max_conns`值。

> >自1.5.9版本和1.11.5版本之前，此參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`max_fails`\=`*number*`

設置在`fail_timeout`參數設置的持續時間內與伺服器通信的不成功嘗試次數，以將伺服器視為在`fail_timeout`參數設置的持續時間內不可用。默認情況下，不成功嘗試次數設置為1。零值禁用嘗試計數。被視為不成功嘗試的次數由[proxy\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_next_upstream)、[fastcgi\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream)、[uwsgi\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream)、[scgi\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream)、[memcached\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_next_upstream)和[grpc\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_next_upstream)指令。

`fail_timeout`\=`*time*`

sets

-   在指定次數的不成功嘗試與伺服器通信時，應考慮伺服器不可用的時間;
-   以及伺服器將被認為不可用的時間段。

默認情況下，該參數設置為10秒。

`backup`

將伺服器標記為備份伺服器。當主伺服器不可用時，它將傳遞請求。

> >該參數不能與[hash](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#hash)、[ip\_hash](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#ip_hash)和[random](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#random)負載平衡方法一起沿著使用。

`down`

將伺服器標記為永久不可用。

`resolve`

監控伺服器域名對應IP位址的變化，自動修改上游配置，無需重啟nginx（1.5.12），伺服器組必須位於[shared memory](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)。

為了使此參數起作用，必須在[http](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver)塊或相應的[upstream](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#resolver)塊中指定`resolver`指令。

> >在1.27.3版本之前，此參數僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`service`\=`*name*`

啟用DNS[SRV](https://datatracker.ietf.org/doc/html/rfc2782)記錄的解析並設置服務`*name*`（1.9.13）。為了使此參數起作用，必須為伺服器指定[resolve](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#resolve)參數並指定不帶埠號的主機名。

如果服務名稱不包含點（「`.`」），則構造符合[RFC](https://datatracker.ietf.org/doc/html/rfc2782)\-的名稱，並將TCP協議添加到服務前綴。例如，要查找`_http._tcp.backend.example.com`SRV記錄，需要指定以下指令：

```nginx
server backend.example.com service=http resolve;
```

如果服務名包含一個或多個點，則通過連接服務前綴和伺服器名來構造名稱。例如，要查找`_http._tcp.backend.example.com`和`server1.backend.example.com`SRV記錄，需要指定以下指令：

```nginx
server backend.example.com service=_http._tcp resolve;
server example.com service=server1.backend resolve;
```

最高優先級的SRV記錄（具有相同的最低編號優先級值的記錄）被解析為主伺服器，其餘的SRV記錄被解析為備份伺服器。如果為伺服器指定了[backup](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#backup)參數，則高優先級的SRV記錄被解析為備份伺服器，其餘的SRV記錄被忽略。

> >在1.27.3版本之前，此參數僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`route`\=`*string*`

設置伺服器路由名稱。

> >在版本1.29.6之前，此參數僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`drain`

將伺服器置於「排水」模式（1.13.6）。在此模式下，只有發送到伺服器的請求[bound](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#sticky)才會被代理到伺服器。

> >在1.13.6版本之前，只能通過[API](https://nginx.org/en/docs/http/ngx_http_api_module.html)模塊更改參數。

> >在版本1.29.6之前，該參數僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

此外，以下參數可作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分：

`slow_start`\=`*time*`

設置`*time*`，在此期間，當不健康的伺服器變為[healthy](https://nginx.org/en/docs/http/ngx_http_upstream_hc_module.html#health_check)時，或當伺服器在被認為是[unavailable](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#fail_timeout)的一段時間後變為可用時，伺服器將從零恢復其權重到標稱值。默認值為零，即禁用慢啟動。

> >該參數不能與[hash](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#hash)、[ip\_hash](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#ip_hash)和[random](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#random)負載平衡方法一起沿著使用。

> >如果組中只有一台伺服器，則忽略`max_fails`、`fail_timeout`和`slow_start`參數，這樣的伺服器永遠不會被視為不可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>zone</strong> <code><i>name</i></code> [<code><i>size</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.9.0版中。

定義共享內存區域的`*name*`和`*size*`，用於保存工作進程之間共享的組配置和運行時狀態。多個組可以共享同一區域。在這種情況下，只需指定一次`*size*`即可。

此外，作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分，此類組允許更改組成員身份或修改特定伺服器的設置，而無需重新啟動nginx。配置可通過[API](https://nginx.org/en/docs/http/ngx_http_api_module.html)模塊（1.13.3）訪問。

> >在1.13.3版本之前，只能通過[upstream\_conf](https://nginx.org/en/docs/http/ngx_http_upstream_conf_module.html#upstream_conf)處理的特殊位置訪問配置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>state</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.9.7版本中。

指定一個保持動態可配置組狀態的`*file*`。

Examples:

```
state /var/lib/nginx/state/servers.conf; # path for Linux
state /var/db/nginx/state/servers.conf;  # path for FreeBSD
```

當前狀態僅限於伺服器及其參數的列表。解析配置時讀取文件，每次上游配置為[changed](https://nginx.org/en/docs/http/ngx_http_api_module.html#http_upstreams_http_upstream_name_servers_)時更新文件。應避免直接更改文件內容。指令不能沿著與[server](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)指令一起使用。

> >在[configuration reload](https://nginx.org/en/docs/control.html#reconfiguration)或[binary upgrade](https://nginx.org/en/docs/control.html#upgrade)期間所做的更改可能會丟失。

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hash</strong> <code><i>key</i></code> [<code>consistent</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.7.2版中。

指定伺服器組的負載平衡方法，其中客戶端-伺服器映射基於哈希值`*key*`。`*key*`可以包含文本、變量及其組合。請注意，在組中添加或刪除伺服器可能會導致將大多數鍵重新映射到不同的伺服器。該方法與[Cache::Memcached](https://metacpan.org/pod/Cache::Memcached)Perl庫兼容。

如果指定了`consistent`參數，則會使用[ketama](https://www.metabrew.com/article/libketama-consistent-hashing-algo-memcached-clients)一致性哈希方法。該方法可以確保在組中添加或刪除伺服器時，只有少數鍵會重新映射到不同的伺服器。這有助於為緩存伺服器實現更高的緩存命中率。該方法與`*ketama_points*`參數設置為160的[Cache::Memcached::Fast](https://metacpan.org/pod/Cache::Memcached::Fast)Perl庫兼容。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ip_hash</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

指定組應使用負載平衡方法，其中請求基於客戶端IP位址在伺服器之間分發。客戶端IPv4地址的前三個八位字節或整個IPv6地址，作為哈希鍵。該方法確保來自同一客戶端的請求將始終傳遞到同一伺服器，除非該伺服器不可用時。在後一種情況下，客戶端請求將被傳遞到另一台伺服器。最有可能的是，它也將始終是同一台伺服器。

> >從1.3.2和1.2.2版本開始支持IPv6地址。

如果需要臨時刪除其中一個伺服器，則應使用`down`參數對其進行標記，以保留客戶端IP位址的當前哈希。

Example:

```nginx
upstream backend {
    ip_hash;

    server backend1.example.com;
    server backend2.example.com;
    server backend3.example.com **down**;
    server backend4.example.com;
}
```

> >在1.3.1和1.2.2版本之前，無法使用`ip_hash`負載平衡方法為伺服器指定權重。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>keepalive</strong> <code><i>connections</i></code> [<code>local</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>keepalive 32 local;</pre></td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.1.4版中。

啟用或禁用對與上游伺服器的保活連接的緩存。自1.29.7起，默認情況下，該高速緩存已激活，並且這些連接不是不同[locations](https://nginx.org/en/docs/http/ngx_http_core_module.html#location)之間的[shared](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#keepalive_local)。

即使上游伺服器地址匹配，`local`參數（1.29.7）也禁止跨不同位置共享緩存的keepalive連接。如果未指定`local`參數，則無論位置如何，都可以重用與同一上游伺服器匹配的任何緩存連接。

`*connections*`參數設置每個工作進程的該高速緩存中保留的到上游伺服器的空閒keepalive連接的最大數量。超過此數量時，將關閉最近最少使用的連接。零值將禁用到上游伺服器的keepalive連接。

> >自1.29.7起，默認情況下啟用keepalive連接，每個worker進程的默認連接限制為`*32*`。

> >需要特別注意的是，`keepalive`指令並不限制nginx worker進程可以打開的到上游伺服器的連接總數。`*connections*`參數應該設置為一個足夠小的數字，以便上游伺服器也能處理新的傳入連接。

> >在1.29.7之前，當使用非默認輪詢方法的負載均衡方法時，需要在`keepalive`指令之前激活它們。

使用keepalive連接的memcached上游配置示例：

```nginx
upstream memcached_backend {
    server 127.0.0.1:11211;
    server 10.0.0.2:11211;

    keepalive 32;
}

server {
    ...

    location /memcached/ {
        set $memcached_key $uri;
        memcached_pass memcached_backend;
    }

}
```

對於HTTP，[proxy\_http\_version](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_http_version)指令應該是"`1.1`"（從1.29.7開始默認）或設置為"`2`"，並且"Connection"頭欄位應該被清除。此示例適用於1.29.7之前的版本：

```nginx
upstream http_backend {
    server 127.0.0.1:8080;

    keepalive 16;
}

server {
    ...

    location /http/ {
        proxy_pass http://http_backend;
        # proxy_http_version 1.1; # before version 1.29.7
        # proxy_set_header Connection ""; # before version 1.29.7
        ...
    }
}
```

> 或者，HTTP/1.0持久連接可以通過將「Connection：Keep-Alive」頭欄位傳遞給上游伺服器來使用，儘管不推薦這種方法。

對於FastCGI伺服器，需要設置[fastcgi\_keep\_conn](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_keep_conn)以使keepalive連接工作：

```nginx
upstream fastcgi_backend {
    server 127.0.0.1:9000;

    keepalive 8;
}

server {
    ...

    location /fastcgi/ {
        fastcgi_pass fastcgi_backend;
        fastcgi_keep_conn on;
        ...
    }
}
```

> SCGI和uwsgi協議沒有keepalive連接的概念。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>keepalive_requests</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>keepalive_requests 1000;</pre></td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.15.3版中。

設置一個保持活動連接可以處理的最大請求數。達到最大請求數後，連接將關閉。

需要定期關閉連接以釋放每個連接的內存分配。因此，使用過高的最大請求數可能會導致過多的內存使用，不建議使用。

> 在1.19.10版本之前，默認值為100。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>keepalive_time</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>keepalive_time 1h;</pre></td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在版本1.19.10中。

限制通過一個保持活動連接處理請求的最長時間。達到此時間後，將在後續請求處理後關閉連接。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>keepalive_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>keepalive_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.15.3版中。

設置一個超時時間，在此時間內，與上游伺服器的空閒keepalive連接將保持打開狀態。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ntlm</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.9.2版中。

允許使用[NTLM Authentication](https://en.wikipedia.org/wiki/Integrated_Windows_Authentication)對請求進行代理。一旦客戶端發送了一個請求，其「Authorization」頭欄位值以「`Negotiate`」或「`NTLM`"開頭，則上游連接將綁定到客戶端連接。以後的客戶端請求將通過同一上游連接進行代理，並保持身份驗證上下文。

為了使NTLM身份驗證工作，必須啟用到上游伺服器的keepalive連接。[proxy\_http\_version](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_http_version)指令應該是「`1.1`」（自1.29.7起默認設置）或設置為「`2`」，並且應該清除「Connection」頭欄位。此示例適用於1.29.7之前的版本：

```nginx
upstream http_backend {
    server 127.0.0.1:8080;

    ntlm;
}

server {
    ...

    location /http/ {
        proxy_pass http://http_backend;
        # proxy_http_version 1.1; # before version 1.29.7
        # proxy_set_header Connection ""; # before version 1.29.7
        ...
    }
}
```

> >當使用非默認輪詢方法的負載均衡方法時，需要在`ntlm`指令之前激活它們。

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>least_conn</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

該指令出現在1.3.1和1.2.2版本中。

指定組應使用負載平衡方法，其中將請求傳遞到活動連接數最少的伺服器，同時考慮伺服器的權重。如果有多個這樣的伺服器，則使用加權循環平衡方法依次嘗試。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>least_time</strong> <code>header</code> | <code>last_byte</code> [<code>inflight</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

該指令出現在1.7.10版本中。

指定組應使用負載平衡方法，在此方法中，將請求傳遞到平均響應時間最短且活動連接數最少的伺服器，同時考慮伺服器的權重。如果有多個這樣的伺服器，則使用加權循環平衡方法依次嘗試。

如果指定了`header`參數，則使用接收[response header](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_header_time)的時間。如果指定了`last_byte`參數，則使用接收[full response](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_response_time)的時間。如果指定了`inflight`參數（1.11.6），則還考慮不完整的請求。

> 在1.11.6版本之前，默認情況下會考慮不完整的請求。

> >在1.31.0版本之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>queue</strong> <code><i>number</i></code> [<code>timeout</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.5.12版本中。

如果在處理請求時無法立即選擇上游伺服器，則將請求放入隊列中。指令指定了隊列中可以同時存在的最大請求數`*number*`。如果隊列已滿，或者在`timeout`參數指定的時間段內無法選擇要傳遞請求的伺服器，502（Bad Gateway）錯誤將返回給客戶端。

`timeout`參數的默認值為60秒。

> >當使用非默認輪詢方法的負載均衡方法時，需要在`queue`指令之前激活它們。

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>random</strong> [<code>two</code> [<code><i>method</i></code>]];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.15.1版中。

指定組應使用負載平衡方法，其中將請求傳遞到隨機選擇的伺服器，同時考慮伺服器的權重。

可選的`two`參數指示nginx隨機選擇[two](https://homes.cs.washington.edu/~karlin/papers/balls.pdf)個伺服器，然後使用指定的`method`選擇一個伺服器。默認方法是`least_conn`，它將請求傳遞到活動連接數最少的伺服器。

`least_time`方法將請求傳遞到平均響應時間最短且活動連接數最少的伺服器。如果指定了`least_time=header`，則使用接收[response header](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_header_time)的時間。如果指定了`least_time=last_byte`，則使用接收[full response](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_response_time)的時間。

> >`least_time`方法是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver</strong> <code><i>address</i></code> ... [<code>valid</code>=<code><i>time</i></code>] [<code>ipv4</code>=<code>on</code>|<code>off</code>] [<code>ipv6</code>=<code>on</code>|<code>off</code>] [<code>status_zone</code>=<code><i>zone</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.27.3版中。

配置用於將上游伺服器的名稱解析為地址的名稱伺服器，例如：

```nginx
resolver 127.0.0.1 [::1]:5353;
```

地址可以指定為域名或IP位址，並帶有可選埠。如果未指定埠，則使用埠53。名稱伺服器以循環方式查詢。

默認情況下，nginx在解析時會同時查找IPv4和IPv6地址。如果不需要查找IPv4或IPv6地址，可以指定`ipv4=off`（1.23.1）或`ipv6=off`參數。

默認情況下，nginx會使用響應的TTL值來緩存答案。可選的`valid`參數允許覆蓋它：

```nginx
resolver 127.0.0.1 [::1]:5353 valid=30s;
```

> >為了防止DNS欺騙，建議在適當安全的可信本地網絡中配置DNS伺服器。

可選的`status_zone`參數（1.17.5）在指定的`*zone*`中啟用[collection](https://nginx.org/en/docs/http/ngx_http_api_module.html#resolvers_)的請求和響應的DNS伺服器統計信息。該參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

> >自版本1.17.5和版本1.27.3之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>resolver_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.27.3版中。

設置名稱解析的超時，例如：

```nginx
resolver_timeout 5s;
```

> >自版本1.17.5和版本1.27.3之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>sticky</strong> <code>cookie</code> <code><i>name</i></code> [<code>expires=</code><code><i>time</i></code>] [<code>domain=</code><code><i>domain</i></code>] [<code>httponly</code>] [<code>samesite=</code><code>strict</code>|<code>lax</code>|<code>none</code>|<code><i>$variable</i></code>] [<code>secure</code>] [<code>path=</code><code><i>path</i></code>];</code><br><code><strong>sticky</strong> <code>route</code> <code><i>$variable</i></code> ...;</code><br><code><strong>sticky</strong> <code>learn</code> <code>create=</code><code><i>$variable</i></code> <code>lookup=</code><code><i>$variable</i></code> <code>zone=</code><code><i>name</i></code>:<code><i>size</i></code> [<code>timeout=</code><code><i>time</i></code>] [<code>header</code>] [<code>sync</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>upstream</code><br></td></tr></tbody></table>

此指令出現在1.5.7版本中。

啟用會話關聯性，這會導致來自同一客戶端的請求被傳遞到一組伺服器中的同一伺服器。有三種方法可用：

`cookie`

當使用`cookie`方法時，指定伺服器的信息會通過nginx生成的HTTP cookie傳遞：

```nginx
upstream backend {
    server backend1.example.com;
    server backend2.example.com;

    sticky cookie srv_id expires=1h domain=.example.com path=/;
}
```

來自尚未綁定到特定伺服器的客戶端的請求將被傳遞到配置的平衡方法所選擇的伺服器。帶有此Cookie的其他請求將被傳遞到指定的伺服器。如果指定的伺服器無法處理請求，則會選擇新的伺服器，就像客戶端尚未綁定一樣。

> 由於負載均衡方法總是試圖考慮已綁定的請求來均勻分配負載，因此具有較高數量的活動綁定請求的伺服器獲得新的未綁定請求的可能性較小。

第一個參數設置要設置或檢查的cookie的名稱。cookie值是IP位址和埠或UNIX域套接字路徑的MD5哈希的十六進位表示。但是，如果指定了[server](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)指令的「`route`」參數，則cookie值將是「`route`」參數的值：

```nginx
upstream backend {
    server backend1.example.com route=**a**;
    server backend2.example.com route=**b**;

    sticky cookie srv_id expires=1h domain=.example.com path=/;
}
```

在這種情況下，「`srv_id`」cookie的值將是`*a*`或`*b*`。

附加參數可以如下：

`expires=``*time*`

設置瀏覽器應保留cookie的`*time*`。特殊值`max`將導致cookie在「`31 Dec 2037 23:55:55 GMT`"過期。如果未指定該參數，則將導致cookie在瀏覽器會話結束時過期。

`domain=``*domain*`

定義設置cookie的`*domain*`。參數值可以包含變量（1.11.5）。

`httponly`

將`HttpOnly`屬性添加到cookie（1.7.11）。

`samesite=``strict` | `lax` | `none` | `*$variable*`

添加`SameSite`（1.19.4）使用以下值之一的cookie屬性：`Strict`、`Lax`、`None`或使用變量（1.23.3）.在後一種情況下，如果變量值為空，則不會將`SameSite`屬性添加到cookie中，如果值被解析為`Strict`，`Lax`或`None`，則會分配相應的值，否則將分配`Strict`值。

`secure`

將`Secure`屬性添加到cookie（1.7.11）。

`path=``*path*`

定義要為其設置Cookie的`*path*`。

如果省略任何參數，則不會設置相應的cookie欄位。

`route`

當使用`route`方法時，代理伺服器在收到第一個請求時為客戶端分配一個路由。來自此客戶端的所有後續請求都將在cookie或URI中攜帶路由信息。將此信息與[server](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)指令的「`route`」參數進行比較，以標識請求應代理到的伺服器。如果未指定「`route`」參數，路由名稱將是IP位址和埠的MD5散列或UNIX域套接字路徑的十六進位表示。如果指定的伺服器無法處理請求，則通過配置的平衡方法選擇新伺服器，就好像請求中沒有路由信息一樣。

`route`方法的參數指定可能包含路由信息的變量。第一個非空變量用於查找匹配的伺服器。

Example:

```nginx
map $cookie_jsessionid $route_cookie {
    ~.+\.(?P<route>\w+)$ $route;
}

map $request_uri $route_uri {
    ~jsessionid=.+\.(?P<route>\w+)$ $route;
}

upstream backend {
    server backend1.example.com route=a;
    server backend2.example.com route=b;

    sticky route $route_cookie $route_uri;
}
```

在這裡，如果請求中存在「`JSESSIONID`」cookie，則從該cookie獲取路由。否則，使用來自URI的路由。

`learn`

當使用`learn`方法（1.7.1）時，nginx分析上游伺服器響應並學習通常在HTTP cookie中傳遞的伺服器發起的會話。

```nginx
upstream backend {
   server backend1.example.com:8080;
   server backend2.example.com:8081;

   粘性學習
          create=$upstream_cookie_examplecookie
          lookup=$cookie_examplecookie
          zone=client_sessions:1m;
}
```

在本例中，上游伺服器通過在響應中設置cookie「`EXAMPLECOOKIE`」來創建會話。使用此cookie的後續請求將被傳遞到同一伺服器。如果伺服器無法處理該請求，則選擇新伺服器，就像客戶端尚未綁定一樣。

參數`create`和`lookup`指定變量，分別指示如何創建新會話和搜索現有會話。這兩個參數可以指定多次，在這種情況下，使用第一個非空變量。

會話存儲在共享內存區域中，其`*name*`和`*size*`由`zone`參數配置。在64位平台上，一個MB區域可以存儲大約4000個會話。在`timeout`參數指定的時間內未訪問的會話將從區域中刪除。默認情況下，`timeout`設置為10分鐘。

`header`參數（1.13.1）允許在從上游伺服器接收到響應頭後立即創建會話。

`sync`參數（1.13.8）啟用共享內存區域的[synchronization](https://nginx.org/en/docs/stream/ngx_stream_zone_sync_module.html#zone_sync)。該參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

> >在1.29.6版本之前，此指令僅作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

#### 嵌入變量

`ngx_http_upstream_module`模塊支持以下嵌入變量：

`$upstream_addr`

保留IP位址和埠，或上游伺服器的UNIX域套接字的路徑。如果在請求處理期間聯繫了多個伺服器，則它們的地址用逗號分隔，例如「`192.168.1.1:80, 192.168.1.2:80, unix:/tmp/sock`"。如果發生從一個伺服器組到另一個伺服器組的內部重定向，由「X-Accel-Redirect」或[error\_page](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page)發起，則來自不同組的伺服器地址用冒號分隔，例如「`192.168.1.1:80, 192.168.1.2:80, unix:/tmp/sock : 192.168.10.1:80, 192.168.10.2:80`"。如果無法選擇伺服器，則變量將保留伺服器組的名稱。

`$upstream_bytes_received`

從上游伺服器接收的字節數（1.11.4）。來自多個連接的值用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_bytes_sent`

發送到上游伺服器的字節數（1.15.8）。來自多個連接的值用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_cache_status`

保持訪問響應緩存的狀態（0.8.3）。狀態可以是「`MISS`"、「`BYPASS`"、「`EXPIRED`"、「`STALE`"、「`UPDATING`"、「`REVALIDATED`"或「`HIT`"。

`$upstream_connect_time`

記錄與上游伺服器建立連接所花費的時間（1.9.1）;時間以秒為單位，解析度為毫秒。在SSL的情況下，包括握手所花費的時間。幾個連接的時間用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_cookie_``*name*`

上游伺服器在「Set-Cookie」響應頭欄位（1.7.1）中發送的具有指定`*name*`的Cookie。僅保存來自最後一個伺服器的響應的Cookie。

`$upstream_header_time`

記錄從上游伺服器接收響應頭所花費的時間（1.7.10）;時間以秒為單位，解析度為毫秒。幾個響應的時間用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_http_``*name*`

保留伺服器響應頭欄位。例如，「Server」響應頭欄位可通過`$upstream_http_server`變量獲得。頭欄位名稱轉換為變量名稱的規則與以「[$http\_](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_http_)」前綴開頭的變量相同。僅保存最後一個伺服器響應的頭欄位。

`$upstream_last_addr`

保持IP位址或路逕到最後選擇的上游伺服器的UNIX域套接字（1.29.3）。

> >此變量作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`$upstream_last_server_name`

保留最後選擇的上游伺服器的名稱（1.25.3）;允許傳遞它[through SNI](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_ssl_server_name)：

```nginx
proxy_ssl_server_name on;
proxy_ssl_name        $upstream_last_server_name;
```

> >此變量作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`$upstream_queue_time`

記錄請求在上游花費的時間[queue](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#queue)（1.13.9）;時間以秒為單位，解析度為毫秒。幾個響應的時間用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_response_length`

保持從上游伺服器獲得的響應的長度（0.7.27）;長度以字節為單位。幾個響應的長度用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_response_time`

記錄從上游伺服器接收響應所花費的時間;時間以秒為單位，解析度為毫秒。幾個響應的時間用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。

`$upstream_status`

保留從上游伺服器獲取的響應的狀態碼，多個響應的狀態碼之間用逗號和冒號分隔，就像[$upstream\_addr](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_addr)變量中的地址一樣。如果無法選擇伺服器，則該變量保留502（Bad Gateway）狀態碼。

`$upstream_trailer_``*name*`

保留從上游伺服器獲得的響應的末尾開始的欄位（1.13.10）。