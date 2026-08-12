# page

> Source: https://nginx.org/en/docs/http/ngx_http_browser_module.html

---

## 目錄

- [Module ngx\_http\_browser\_module](#module-ngxhttpbrowsermodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_browser\_module

`ngx_http_browser_module`模塊創建變量，其值取決於「User-Agent」請求頭欄位的值：

`$modern_browser`

如果瀏覽器被標識為modern，則等於由[modern\_browser\_value](https://nginx.org/en/docs/http/ngx_http_browser_module.html#modern_browser_value)指令設置的值;

`$ancient_browser`

如果瀏覽器被標識為舊的，則等於由[ancient\_browser\_value](https://nginx.org/en/docs/http/ngx_http_browser_module.html#ancient_browser_value)指令設置的值;

`$msie`

如果瀏覽器被標識為任何版本的MSIE，則等於「1」。

#### 配置示例

選擇索引文件：

> modern\_browser\_value "modern.";
> 
> modern\_browser msie      5.5;
> modern\_browser gecko     1.0.0;
> modern\_browser opera     9.0;
> modern\_browser safari    413;
> modern\_browser konqueror 3.0;
> 
> index index.${modern\_browser}html index.html;

舊瀏覽器的重定向：

> modern\_browser msie      5.0;
> modern\_browser gecko     0.9.1;
> modern\_browser opera     8.0;
> modern\_browser safari    413;
> modern\_browser konqueror 3.0;
> 
> modern\_browser unlisted;
> 
> ancient\_browser Links Lynx netscape4;
> 
> if ($ancient\_browser) {
>     rewrite ^ /ancient.html;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ancient_browser</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果在「User-Agent」請求頭欄位中找到任何指定的子字符串，則瀏覽器將被視為過時。特殊字符串「`netscape4`」對應於正則表達式「`^Mozilla/[1-4]`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ancient_browser_value</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ancient_browser_value 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置`$ancient_browser`變量的值。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>modern_browser</strong> <code><i>browser</i></code> <code><i>version</i></code>;</code><br><code><strong>modern_browser</strong> <code>unlisted</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定一個版本，從該版本開始，瀏覽器被視為現代瀏覽器。瀏覽器可以是以下任意一種：`msie`、`gecko`（基於Mozilla的瀏覽器）、`opera`、`safari`或`konqueror`。

可以使用以下格式指定版本：X、X.X、X.X.X或X. X. X. X。每種格式的最大值分別為4000、4000.99、4000.99.99和4000.99.99.99。

特殊值`unlisted`指定如果瀏覽器未被`modern_browser`和[ancient\_browser](https://nginx.org/en/docs/http/ngx_http_browser_module.html#ancient_browser)指令列出，則將其視為現代瀏覽器。否則，此類瀏覽器將被視為舊瀏覽器。如果請求的頭中未提供「User-Agent」欄位，則瀏覽器將被視為未列出。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>modern_browser_value</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>modern_browser_value 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置`$modern_browser`變量的值。