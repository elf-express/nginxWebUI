# page

> Source: https://nginx.org/en/docs/http/ngx_http_upstream_conf_module.html

---

## 目錄

- [Module ngx\_http\_upstream\_conf\_module](#module-ngxhttpupstreamconfmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_upstream\_conf\_module

`ngx_http_upstream_conf_module`模塊允許通過簡單的HTTP接口動態配置上游伺服器組，而無需重新啟動nginx。[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)伺服器組必須駐留在共享內存中。

> >此模塊在1.13.10之前作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。它在1.13.3中被[ngx\_http\_api\_module](https://nginx.org/en/docs/http/ngx_http_api_module.html)模塊取代。

#### 配置示例

> upstream backend {
>     zone upstream\_backend 64k;
> 
>     ...
> }
> 
> server {
>     location /upstream\_conf {
>         **upstream\_conf**;
>         allow 127.0.0.1;
>         deny all;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>upstream_conf</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

打開周圍位置上游配置的HTTP接口。訪問此位置的權限應為[limited](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy)。

配置命令可用於：

-   查看組配置;
-   查看、修改或刪除伺服器;
-   添加新伺服器。

> >由於組中的地址不需要唯一，因此組中的特定伺服器通過其ID引用。ID會自動分配，並在添加新伺服器或查看組配置時顯示。

配置命令由作為請求參數傳遞的參數組成，例如：

> http://127.0.0.1/upstream\_conf?upstream=backend

支持以下參數：

`stream=`

選擇[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html)上游伺服器組。如果沒有此參數，則選擇[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)上游伺服器組。

`upstream=``*name*`

指定要使用的組。此參數是必需的。

`id=``*number*`

禁用伺服器以進行查看、修改或刪除。

`remove=`

從組中刪除伺服器。

`add=`

向組中添加新伺服器。

`backup=`

需要添加備份伺服器。

> >在1.7.2版本之前，還需要`backup=`來查看、修改或刪除現有的備份伺服器。

`server=``*address*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)上游伺服器的「`address`」參數相同。

添加伺服器時，可以將其指定為域名。在這種情況下，對應於域名的IP位址的更改將被監控並自動應用到上游配置，而無需重新啟動nginx（1.7.2）。這需要在[http](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver)或[stream](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver)塊中使用「`resolver`」指令。另請參閱[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#resolve)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#resolve)上游伺服器的「`resolve`」參數。

`service=``*name*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#service)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#service)上游伺服器的「`service`」參數相同（1.9.13）。

`weight=``*number*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#weight)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#weight)上游伺服器的「`weight`」參數相同。

`max_conns=``*number*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_conns)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#max_conns)上游伺服器的「`max_conns`」參數相同。

`max_fails=``*number*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#max_fails)上游伺服器的「`max_fails`」參數相同。

`fail_timeout=``*time*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#fail_timeout)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#fail_timeout)上游伺服器的「`fail_timeout`」參數相同。

`slow_start=``*time*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#slow_start)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#slow_start)上游伺服器的「`slow_start`」參數相同。

`down=`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#down)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#down)上游伺服器的「`down`」參數相同。

`drain=`

將[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)上游伺服器置於「排出」模式（1.7.5）。在此模式下，只有發送到伺服器的請求[bound](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#sticky)才會被代理到該伺服器。

`up=`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#down)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#down)上游伺服器的「`down`」參數相反。

`route=``*string*`

與[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#route)上游伺服器的「`route`」參數相同。

前三個參數選擇一個對象。這可以是整個http或stream上游伺服器組，也可以是特定的伺服器。如果沒有其他參數，則顯示所選組或伺服器的配置。

例如，要查看整個組的配置，請發送：

> http://127.0.0.1/upstream\_conf?upstream=backend

要查看特定伺服器的配置，請同時指定其ID：

> http://127.0.0.1/upstream\_conf?upstream=backend&id=42

要添加新伺服器，請在「`server=`」參數中指定其地址。如果未指定其他參數，則將添加其他參數設置為默認值的伺服器（請參閱[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)或[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)「`server`」指令）。

例如，要添加新的主伺服器，請發送：

> http://127.0.0.1/upstream\_conf?add=&upstream=backend&server=127.0.0.1:8080

要添加新的備份伺服器，請發送：

> http://127.0.0.1/upstream\_conf?add=&upstream=backend&backup=&server=127.0.0.1:8080

要添加新的主伺服器，請將其參數設置為非默認值並將其標記為「`down`"，發送：

> http://127.0.0.1/upstream\_conf?add=&upstream=backend&server=127.0.0.1:8080&weight=2&down=

要刪除伺服器，請指定其ID：

> http://127.0.0.1/upstream\_conf?remove=&upstream=backend&id=42

要將現有伺服器標記為「`down`"，請發送：

> http://127.0.0.1/upstream\_conf?upstream=backend&id=42&down=

要修改現有伺服器的地址，請發送：

> http://127.0.0.1/upstream\_conf?upstream=backend&id=42&server=192.0.2.3:8123

要修改現有伺服器的其他參數，請發送：

> http://127.0.0.1/upstream\_conf?upstream=backend&id=42&max\_fails=3&weight=4

以上示例適用於[http](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)上游伺服器組。類似的示例適用於[stream](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html)上游伺服器組，需要「`stream=`」參數。