# page

> Source: https://nginx.org/en/docs/http/ngx_http_charset_module.html

---

## 目錄

- [Module ngx\_http\_charset\_module](#module-ngxhttpcharsetmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_charset\_module

`ngx_http_charset_module`模塊將指定的字符集添加到「Content-Type」響應頭欄位中。此外，模塊可以將數據從一個字符集轉換為另一個字符集，但有一些限制：

-   轉換是單向執行的從伺服器到客戶機，
-   只能轉換單字節字符集
-   或單字節字符集與UTF-8之間的轉換。

#### 配置示例

> include        conf/koi-win;
> 
> charset        windows-1251;
> source\_charset koi8-r;

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>charset</strong> <code><i>charset</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>charset off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

將指定的字符集添加到「Content-Type」響應標頭欄位。如果此字符集與[source\_charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#source_charset)指令中指定的字符集不同，則執行轉換。

參數`off`取消向「Content-Type」響應頭欄位添加字符集。

一個字符集可以用一個變量來定義：

> charset $charset;

在這種情況下，變量的所有可能值都需要以[charset\_map](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset_map)、[charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset)或[source\_charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#source_charset)指令的形式在配置中至少出現一次。對於`utf-8`、`windows-1251`和`koi8-r`字符集，將文件`conf/koi-win`、`conf/koi-utf`和`conf/win-utf`包含到配置中就足夠了。對於其他字符集，只需創建一個虛構的轉換表即可，例如：

> charset\_map iso-8859-5 \_ { }

此外，可以在「X-Accel-Charset」響應頭欄位中設置字符集。可以使用[proxy\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_ignore_headers)、[fastcgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_ignore_headers)、[uwsgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ignore_headers)、[scgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_ignore_headers)和[grpc\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ignore_headers)指令禁用此功能。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>charset_map</strong> <code><i>charset1</i></code> <code><i>charset2</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

描述從一個字符集到另一個字符集的轉換表。使用相同的數據構建反向轉換表。字符代碼以十六進位給出。80-FF範圍內缺少的字符將替換為「`?`"。從UTF-8轉換時，單字節字符集中缺少的字符將替換為「`&#XXXX;`"。

Example:

> charset\_map koi8-r windows-1251 {
>     C0 FE ; # small yu
>     C1 E0 ; # small a
>     C2 E1 ; # small b
>     C3 F6 ; # small ts
>     ...
> }

當描述一個UTF-8轉換表時，UTF-8字符集的代碼應該在第二列中給出，例如：

> charset\_map koi8-r utf-8 {
>     C0 D18E ; # small yu
>     C1 D0B0 ; # small a
>     C2 D0B1 ; # small b
>     C3 D186 ; # small ts
>     ...
> }

從`koi8-r`到`windows-1251`以及從`koi8-r`和`windows-1251`到`utf-8`的完整轉換表在分發文件`conf/koi-win`、`conf/koi-utf`和`conf/win-utf`中提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>charset_types</strong> <code><i>mime-type</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>charset_types text/html text/xml text/plain text/vnd.wap.wml
application/javascript application/rss+xml;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在0.7.9版本中。

在除「`text/html`"之外的指定MIME類型的響應中啟用模塊處理。特殊值「`*`」匹配任何MIME類型（0.8.29）。

> >在1.5.4版本之前，默認MIME類型是「`application/x-javascript`」而不是「`application/javascript`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>override_charset</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>override_charset off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

確定當從代理伺服器或FastCGI/uwsgi/SCGI/gRPC伺服器接收的應答在「Content-Type」響應標頭欄位中已攜帶字符集時，是否應對應答執行轉換。如果啟用轉換，則將在接收的響應中指定的字符集用作源字符集。

> >需要注意的是，如果在子請求中接收到響應，則無論`override_charset`指令設置如何，都會執行從響應字符集到主請求字符集的轉換。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>source_charset</strong> <code><i>charset</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

定義響應的源字符集。如果此字符集與[charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset)指令中指定的字符集不同，則執行轉換。