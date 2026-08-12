# page

> Source: https://nginx.org/en/docs/http/ngx_http_xslt_module.html

---

## 目錄

- [Module ngx\_http\_xslt\_module](#module-ngxhttpxsltmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_xslt\_module

`ngx_http_xslt_module`（0.7.8+）是一個過濾器，它使用一個或多個XML樣式錶轉換XML響應。

默認情況下不構建此模塊，應使用`--with-http_xslt_module`配置參數啟用。

> >此模塊需要[libxml2](http://xmlsoft.org/)和[libxslt](http://xmlsoft.org/XSLT/)庫。

#### 配置示例

> location / {
>     xml\_entities    /site/dtd/entities.dtd;
>     xslt\_stylesheet /site/xslt/one.xslt param=value;
>     xslt\_stylesheet /site/xslt/two.xslt;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xml_entities</strong> <code><i>path</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定聲明字符實體的DTD文件。此文件在配置階段編譯。由於技術原因，模塊無法使用在處理的XML中聲明的外部子集，因此將忽略它，並使用專門定義的文件。此文件不應描述XML結構。只需聲明所需的字符實體即可，例如：

> <!ENTITY nbsp "&#xa0;">

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xml_external_entities</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>xml_external_entities off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

啟用或禁用加載在已處理XML的內部DTD子集中聲明的外部實體。僅支持加載基於文件的外部實體。

> >為了防止閱讀工作進程可訪問的任意本地文件，建議僅在處理的XML受信任時啟用外部實體。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xslt_last_modified</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>xslt_last_modified off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.1版中。

允許在HTTP轉換期間保留原始響應的"Last-Modified"頭欄位，以便於響應緩存。

默認情況下，當響應的內容在轉換期間被修改時，頭欄位被移除，並且可能包含動態生成的元素或部分，這些元素或部分獨立於原始響應而更改。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xslt_param</strong> <code><i>parameter</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.18版本中。

定義樣式表的參數。`*value*`被視為樣式表表達式。`*value*`可以包含變量。要將字符串值傳遞給樣式表，可以使用[xslt\_string\_param](https://nginx.org/en/docs/http/ngx_http_xslt_module.html#xslt_string_param)指令。

可能有多個`xslt_param`指令。若且唯若當前級別上沒有定義`xslt_param`和[xslt\_string\_param](https://nginx.org/en/docs/http/ngx_http_xslt_module.html#xslt_string_param)指令時，這些指令才從上一配置級別繼承。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xslt_string_param</strong> <code><i>parameter</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.18版本中。

定義樣式表的字符串參數。不解釋`*value*`中的表達式。`*value*`可以包含變量。

可能有多個`xslt_string_param`指令。若且唯若當前級別上沒有定義[xslt\_param](https://nginx.org/en/docs/http/ngx_http_xslt_module.html#xslt_param)和`xslt_string_param`指令時，這些指令才從上一配置級別繼承。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xslt_stylesheet</strong> <code><i>stylesheet</i></code> [<code><i>parameter</i></code>=<code><i>value</i></code> ...];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

定義樣式表及其可選參數。樣式表在配置階段編譯。

參數可以單獨指定，也可以使用"`:`"引號組合在一行中。如果參數包含"`:`"字符，則應將其轉義為"`%3A`"。此外，`libxslt`要求將包含非字母數字字符的參數括在單引號或雙引號中，例如：

> param1 ='http%3A//www.example.com '：param2 = value2

參數描述可以包含變量，例如，整行參數可以取自單個變量：

> location / {
>     xslt\_stylesheet /site/xslt/one.xslt
>                     $arg\_xslt\_params
>                     param1 ='$value1'：param2=value2
>                     param3=value3;
> }

可以指定多個樣式表。它們將按照指定的順序依次應用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xslt_types</strong> <code><i>mime-type</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>xslt_types text/xml;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

在除「`text/xml`"之外的具有指定MIME類型的響應中啟用轉換。特殊值「`*`」匹配任何MIME類型（0.8.29）。如果轉換結果是HTML響應，則其MIME類型將更改為「`text/html`"。