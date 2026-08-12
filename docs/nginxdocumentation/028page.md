# page

> Source: https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html

---

## 目錄

- [Module ngx\_http\_auth\_basic\_module](#module-ngxhttpauthbasicmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_auth\_basic\_module

`ngx_http_auth_basic_module`模塊允許通過使用「HTTP基本身份驗證」協議驗證用戶名和密碼來限制對資源的訪問。

也可以通過[address](https://nginx.org/en/docs/http/ngx_http_access_module.html)、[result of subrequest](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html)或[JWT](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html)限制訪問。通過地址和密碼同時限制訪問由[satisfy](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy)指令控制。

#### 配置示例

> location / {
>     auth\_basic           "closed site";
>     auth\_basic\_user\_file conf/htpasswd;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_basic</strong> <code><i>string</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_basic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

使用「HTTP基本身份驗證」協議啟用用戶名和密碼驗證。指定的參數用作`*realm*`。參數值可以包含變量（1.3.10，1.2.7）。特殊值`off`取消了從上一配置級別繼承的`auth_basic`指令的效果。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_basic_user_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

指定保存用戶名和密碼的文件，格式如下：

> \# comment
> >名稱1：密碼1
> >名稱2：密碼2：評論
> >名稱3：密碼3

`*file*`名稱可以包含變量。

支持以下密碼類型：

-   使用`crypt()`函數加密;可以使用Apache HTTP Server分發版中的「`htpasswd`」實用程式或「`openssl passwd`」命令生成;
-   使用基於MD5的密碼算法（apr 1）的Apache變體進行散列;可以使用相同的工具生成;
-   由「`{``*scheme*``}``*data*`」語法（1.0.3+）指定，如[RFC 2307](https://datatracker.ietf.org/doc/html/rfc2307#section-5.3)中所述;當前實現的方案包括`PLAIN`（一個示例，不應使用）、`SHA`（1.3.13）（普通SHA-1散列，不應使用）和`SSHA`（加鹽SHA-1散列，由一些軟體包使用，特別是OpenLDAP和Dovecot）。
    
    > >添加對`SHA`方案的支持只是為了幫助從其他Web伺服器遷移。它不應該用於新密碼，因為它使用的無鹽SHA-1哈希容易受到[rainbow table](http://en.wikipedia.org/wiki/Rainbow_attack)攻擊。