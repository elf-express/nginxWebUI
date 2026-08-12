# page

> Source: https://nginx.org/en/docs/http/ngx_http_headers_module.html

---

## 目錄

- [Module ngx\_http\_headers\_module](#module-ngxhttpheadersmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_headers\_module

`ngx_http_headers_module`模塊允許將「Expires」和「Cache-Control」頭欄位以及任意欄位添加到響應頭。

#### 配置示例

> expires    24h;
> expires    modified +24h;
> expires    @24h;
> expires    0;
> expires    -1;
> expires    epoch;
> expires    $expires;
> add\_header Cache-Control private;

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>add_header</strong> <code><i>name</i></code> <code><i>value</i></code> [<code>always</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

如果響應代碼等於200、201（1.3.10）、204、206、301、302、303、304、307（1.1.16、1.0.13）或308（1.13.0），則將指定的欄位添加到響應標頭。參數值可以包含變量。

可以有多個`add_header`指令。若且唯若當前級別上沒有定義`add_header`指令時，這些指令才從上一配置級別繼承。繼承規則可以用[add\_header\_inherit](https://nginx.org/en/docs/http/ngx_http_headers_module.html#add_header_inherit)指令（1.29.3）重新定義。

如果指定了`always`參數（1.7.5），則無論響應代碼如何，都會添加頭欄位。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>add_header_inherit</strong> <code>on</code> | <code>off</code> | <code>merge</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>add_header_inherit on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

允許更改[add\_header](https://nginx.org/en/docs/http/ngx_http_headers_module.html#add_header)指令中指定值的繼承規則。默認情況下，使用[standard](https://nginx.org/en/docs/http/ngx_http_headers_module.html#add_header_default_inherit)繼承模型。

`merge`參數允許將上一級別的值附加到當前級別定義的值。

`off`參數取消從上一配置級別繼承值。

繼承規則本身是以標準的方式繼承的。例如，在頂層指定的`add_header_inherit merge;`將在所有嵌套級別中遞歸繼承，除非稍後重新定義。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>add_trailer</strong> <code><i>name</i></code> <code><i>value</i></code> [<code>always</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

此指令出現在1.13.2版中。

如果響應代碼等於200、201、206、301、302、303、307或308，則將指定的欄位添加到響應的末尾。參數值可以包含變量。

可以有多個`add_trailer`指令。若且唯若當前級別上沒有定義`add_trailer`指令時，這些指令才從上一配置級別繼承。繼承規則可以用[add\_trailer\_inherit](https://nginx.org/en/docs/http/ngx_http_headers_module.html#add_trailer_inherit)指令（1.29.3）重新定義。

如果指定了`always`參數，則無論響應代碼如何，都會添加指定的欄位。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>add_trailer_inherit</strong> <code>on</code> | <code>off</code> | <code>merge</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>add_trailer_inherit on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

允許更改[add\_trailer](https://nginx.org/en/docs/http/ngx_http_headers_module.html#add_trailer)指令中指定值的繼承規則。默認情況下，使用[standard](https://nginx.org/en/docs/http/ngx_http_headers_module.html#add_trailer_default_inherit)繼承模型。

`merge`參數允許將上一級別的值附加到當前級別定義的值。

`off`參數取消從上一配置級別繼承值。

繼承規則本身是以標準的方式繼承的。例如，在頂層指定的`add_trailer_inherit merge;`將在所有嵌套級別中遞歸繼承，除非稍後重新定義。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>expires</strong> [<code>modified</code>] <code><i>time</i></code>;</code><br><code><strong>expires</strong> <code>epoch</code> | <code>max</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>expires off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

啟用或禁用添加或修改「Expires」和「Cache-Control」響應標頭欄位，前提是響應代碼等於200、201（1.3.10）、204、206、301、302、303、304、307（1.1.16、1.0.13）或308（1.13.0）。參數可以是正的或負的[time](https://nginx.org/en/docs/syntax.html)。

「Expires」欄位中的時間計算為當前時間和指令中指定的`*time*`的總和。如果使用了`modified`參數（0.7.0，0.6.32），則時間計算為文件的修改時間和指令中指定的`*time*`的總和。

In addition, it is possible to specify a time of day using the “`@`” prefix (0.7.9, 0.6.34):

> expires @15h30m;

「Cache-Control」欄位的內容取決於指定時間的符號：

-   時間為負-「Cache-Control：no-cache」。
-   time為正數或零-「Cache-Control：max-age=`*t*`"，其中`*t*`是指令中指定的時間，單位為秒。

`epoch`參數將「Expires」設置為值「`Thu, 01 Jan 1970 00:00:01 GMT`"，將「Cache-Control」設置為「`no-cache`"。

`max`參數將「Expires」設置為值「`Thu, 31 Dec 2037 23:55:55 GMT`"，將「Cache-Control」設置為10年。

`off`參數禁止添加或修改「Expires」和「Cache-Control」響應頭欄位。

最後一個參數值可以包含變量（1.7.9）：

> map $sent\_http\_content\_type $expires {
>     default         off;
>     application/pdf 42d;
>     ~image/         max;
> }
> 
> expires $expires;