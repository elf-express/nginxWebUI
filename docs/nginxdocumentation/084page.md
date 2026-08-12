# page

> Source: https://nginx.org/en/docs/http/ngx_http_userid_module.html

---

## 目錄

- [Module ngx\_http\_userid\_module](#module-ngxhttpuseridmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_userid\_module

`ngx_http_userid_module`模塊設置適合客戶端識別的cookie。可以使用嵌入式變量[$uid\_got](https://nginx.org/en/docs/http/ngx_http_userid_module.html#var_uid_got)和[$uid\_set](https://nginx.org/en/docs/http/ngx_http_userid_module.html#var_uid_set)記錄接收和設置的cookie。此模塊與Apache的[mod\_uid](http://www.lexa.ru/programs/mod-uid-eng.html)模塊兼容。

#### 配置示例

> userid         on;
> userid\_name    uid;
> userid\_domain  example.com;
> userid\_path    /;
> userid\_expires 365d;
> userid\_p3p     'policyref="/w3c/p3p.xml", CP="CUR ADM OUR NOR STA NID"';

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid</strong> <code>on</code> | <code>v1</code> | <code>log</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

啟用或禁用設置Cookie和記錄接收到的Cookie：

`on`

啟用版本2 cookie的設置和接收cookie的記錄;

`v1`

啟用版本1 cookie的設置和接收cookie的記錄;

`log`

禁用Cookie設置，但啟用接收到的Cookie的記錄;

`off`

禁用cookie的設置和接收cookie的記錄。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_domain</strong> <code><i>name</i></code> | <code>none</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_domain none;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義設置Cookie的域。`none`參數禁用Cookie的域設置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_expires</strong> <code><i>time</i></code> | <code>max</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_expires off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置瀏覽器保留cookie的時間。參數`max`將使cookie在「`31 Dec 2037 23:55:55 GMT`"過期。參數`off`將使cookie在瀏覽器會話結束時過期。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_flags</strong> <code>off</code> | <code><i>flag</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_flags off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.19.3版中。

如果參數不是`off`，則為Cookie定義一個或多個附加標誌：`secure`、`httponly`、`samesite=strict`、`samesite=lax`、`samesite=none`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_mark</strong> <code><i>letter</i></code> | <code><i>digit</i></code> | <code>=</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_mark off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果參數不是`off`，則啟用cookie標記機制並設置用作標記的字符。此機制用於添加或更改[userid\_p3p](https://nginx.org/en/docs/http/ngx_http_userid_module.html#userid_p3p)和/或cookie過期時間，同時保留客戶端標識符。標記可以是英文字母中的任何字母（區分大小寫）、數字或「`=`」字符。

如果設置了標記，則將其與傳入cookie的客戶端標識符的base64表示中的第一個填充符號進行比較。如果不匹配，則重新發送帶有指定標記、過期時間和「P3 P」報頭的cookie。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_name uid;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置Cookie名稱。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_p3p</strong> <code><i>string</i></code> | <code>none</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_p3p none;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為「P3 P」標頭欄位設置一個值，該值將與Cookie一起沿著發送。如果將該指令設置為特殊值`none`，則「P3 P」標頭將不會在響應中發送。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_path</strong> <code><i>path</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_path /;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義設置Cookie的路徑。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>userid_service</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>userid_service IP address of the server;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果標識符是由多個伺服器（服務）發出的，則每個服務都應分配自己的`*number*`，以確保客戶端標識符是唯一的。對於版本1的Cookie，默認值為零。對於版本2的Cookie，默認值為伺服器IP位址的最後四個八位字節組成的數字。

#### 嵌入變量

`ngx_http_userid_module`模塊支持以下嵌入變量：

`$uid_got`

Cookie名稱和接收到的客戶端標識符。

`$uid_reset`

如果變量被設置為非空字符串（不為「`0`"），則客戶端標識符將被重置。特殊值「`log`」還將導致有關重置標識符的消息輸出到[error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log)。

`$uid_set`

Cookie名稱和發送的客戶端標識符。