# page

> Source: https://nginx.org/en/docs/http/ngx_http_referer_module.html

---

## 目錄

- [Module ngx\_http\_referer\_module](#module-ngxhttpreferermodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_referer\_module

`ngx_http_referer_module`模塊用於阻止在"Referer"頭欄位中具有無效值的請求訪問站點。應該記住，用適當的"Referer"欄位值編造請求非常容易，因此，該模塊的預期目的不是徹底阻止此類請求，而是阻止常規瀏覽器發送的大量請求流。即使對於有效的請求，普通瀏覽器也可能不會發送「Referer」欄位。

#### 配置示例

> >valid\_referers沒有阻止server\_names
>                \*. example.com example.\* www.example.com
>                ~\\.google\\.;
> 
> if ($invalid\_referer) {
>     return 403;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>referer_hash_bucket_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>referer_hash_bucket_size 64;</pre></td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.0.5版中。

設置有效引用者哈希表的存儲桶大小。設置哈希表的詳細信息在單獨的[document](https://nginx.org/en/docs/hash.html)中提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>referer_hash_max_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>referer_hash_max_size 2048;</pre></td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.0.5版中。

設置有效引用者哈希表的最大值`*size*`。設置哈希表的詳細信息在單獨的[document](https://nginx.org/en/docs/hash.html)中提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>valid_referers</strong> <code>none</code> | <code>blocked</code> | <code>server_names</code> | <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定"Referer"請求標頭欄位值，這些值將導致嵌入的`$invalid_referer`變量設置為空字符串。否則，變量將設置為"`1`"。搜索匹配項不區分大小寫。

參數可以如下：

`none`

請求報頭中缺少"Referer"欄位;

`blocked`

"Referer"欄位存在於請求報頭中，但其值已被防火牆或代理伺服器刪除;此類值是不以"`http://`"或"`https://`"開頭的字符串;

`server_names`

"Referer"請求報頭欄位包含伺服器名稱之一;

任意字符串

定義伺服器名稱和可選的URI前綴。伺服器名稱可以在開頭或結尾處具有"`*`"。在檢查期間，"Referer"欄位中的伺服器埠將被忽略;

正則表達式

第一個符號應該是"`~`"。應該注意的是，表達式將與"`http://`"或"`https://`"之後的文本匹配。

Example:

> >valid\_referers沒有阻止server\_names
>                \*. example.com example.\* www.example.com
>                ~\\.google\\.;

#### 嵌入變量

`$invalid_referer`

空字符串，如果「Referer」請求頭欄位值被視為[valid](https://nginx.org/en/docs/http/ngx_http_referer_module.html#valid_referers)，否則為「`1`"。