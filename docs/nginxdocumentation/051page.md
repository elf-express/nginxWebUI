# page

> Source: https://nginx.org/en/docs/http/ngx_http_internal_redirect_module.html

---

## 目錄

- [Module ngx\_http\_internal\_redirect\_module](#module-ngxhttpinternalredirectmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_internal\_redirect\_module

`ngx_http_internal_redirect_module`模塊（1.23.4）允許進行內部重定向。與[rewriting URIs](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html)相反，重定向是在檢查[request](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)和[connection](https://nginx.org/en/docs/http/ngx_http_limit_conn_module.html)處理限制以及[access](https://nginx.org/en/docs/http/ngx_http_access_module.html)限制之後進行的。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

> limit\_req\_zone $jwt\_claim\_sub zone=jwt\_sub:10m rate=1r/s;
> 
> server {
>     location / {
>         auth\_jwt          "realm";
>         auth\_jwt\_key\_file key.jwk;
> 
>         internal\_redirect @rate\_limited;
>     }
> 
>     location @rate\_limited {
>         internal;
> 
>         limit\_req  zone=jwt\_sub burst=10;
>         proxy\_pass http://backend;
>     }
> }

該示例實現了[per-user](https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2)[rate limiting](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)。沒有[internal\_redirect](https://nginx.org/en/docs/http/ngx_http_internal_redirect_module.html#internal_redirect)的實現容易受到未簽名JWT的DoS攻擊，因為通常會在[before](https://nginx.org/en/docs/dev/development_guide.html#http_phases)[auth\_jwt](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html#auth_jwt)檢查中執行[limit\_req](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html#limit_req)檢查。使用[internal\_redirect](https://nginx.org/en/docs/http/ngx_http_internal_redirect_module.html#internal_redirect)允許重新排序這些檢查。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>internal_redirect</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code><br></td></tr></tbody></table>

為請求的內部重定向設置URI。也可以使用[named location](https://nginx.org/en/docs/http/ngx_http_core_module.html#location_named)代替URI。`*uri*`值可以包含變量。如果`*uri*`值為空，則不會進行重定向。