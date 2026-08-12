# page

> Source: https://nginx.org/en/docs/http/ngx_http_auth_require_module.html

---

## 目錄

- [Module ngx\_http\_auth\_require\_module](#module-ngxhttpauthrequiremodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_auth\_require\_module

`ngx_http_auth_require_module`模塊（1.29.0）實現了基於變量的客戶端授權，也可以使用其他訪問模塊提供的變量，如[ngx\_http\_auth\_request\_module](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html)或[ngx\_http\_auth\_oidc\_module](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html)。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

> http {
>     oidc\_provider my\_idp {
>         ...
>     }
> 
>     map $oidc\_claim\_role $admin\_role {
>         "admin" 1;
>     }
> 
>     server {
>         auth\_oidc my\_idp;
> 
>         location /admin {
>             auth\_require $admin\_role;
>         }
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_require</strong> <code><i>$value</i></code> ... [<code>error</code>=<code>4xx</code> | <code>5xx</code>] ;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_require off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

根據指定的變量啟用授權。只有當所有變量都不為空且不等於「0」時才允許訪問。否則，模塊返回`403`code，該code可以由`error`參數覆蓋。可以使用多個`auth_require`指令返回不同的錯誤代碼。