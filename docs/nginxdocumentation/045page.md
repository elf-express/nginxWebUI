# page

> Source: https://nginx.org/en/docs/http/ngx_http_gzip_module.html

---

## 目錄

- [Module ngx\_http\_gzip\_module](#module-ngxhttpgzipmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_gzip\_module

`ngx_http_gzip_module`模塊是一個使用「gzip」方法壓縮響應的過濾器。這通常有助於將傳輸數據的大小減少一半甚至更多。

> >使用SSL/TLS協議時，壓縮響應可能會受到[BREACH](https://en.wikipedia.org/wiki/BREACH)攻擊。

#### 配置示例

> gzip            on;
> gzip\_min\_length 1000;
> gzip\_proxied    expired no-cache no-store private auth;
> gzip\_types      text/plain application/xml;

`$gzip_ratio`變量可用於記錄所達到的壓縮率。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

啟用或禁用響應的gzip壓縮。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_buffers 32 4k|16 8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於壓縮響應的緩衝區的`*number*`和`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，該值可以是4K或8 K。

> >在版本0.7.28之前，默認情況下使用四個4K或8 K緩衝區。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_comp_level</strong> <code><i>level</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_comp_level 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設定回應的gzip壓縮`*level*`。可接受的值介於1到9之間。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_disable</strong> <code><i>regex</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在0.6.23版中。

對於「User-Agent」標頭欄位與任何指定的正則表達式匹配的請求，禁用gzip響應。

特殊遮罩「`msie6`」（0.7.12）映射至正則表達式「`MSIE [4-6]\.`"，但運作速度較快。從版本0.8.11開始，此遮罩不包含「`MSIE 6.0; ... SV1`」。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_http_version</strong> <code>1.0</code> | <code>1.1</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_http_version 1.1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置壓縮響應所需的請求的最低HTTP版本。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_min_length</strong> <code><i>length</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_min_length 20;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置將被gzip壓縮的響應的最小長度。該長度僅由「Content-Length」響應頭欄位確定。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_proxied</strong> <code>off</code> | <code>expired</code> | <code>no-cache</code> | <code>no-store</code> | <code>private</code> | <code>no_last_modified</code> | <code>no_etag</code> | <code>auth</code> | <code>any</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_proxied off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

根據請求和響應，啟用或禁用對代理請求的響應進行gzip壓縮。請求是否被代理取決於是否存在「Via」請求標頭欄位。該指令接受多個參數：

`off`

禁用所有代理請求的壓縮，忽略其他參數;

`expired`

如果響應頭包括具有禁用緩存的值的「Expires」欄位，則啟用壓縮;

`no-cache`

如果響應標頭包含帶「`no-cache`」參數的「Cache-Control」欄位，則啟用壓縮;

`no-store`

如果響應標頭包含帶「`no-store`」參數的「Cache-Control」欄位，則啟用壓縮;

`private`

如果響應標頭包含帶「`private`」參數的「Cache-Control」欄位，則啟用壓縮;

`no_last_modified`

如果響應報頭不包括「Last-Modified」欄位，則啟用壓縮;

`no_etag`

如果響應報頭不包括「ETag」欄位，則啟用壓縮;

`auth`

如果請求報頭包括「授權」欄位，則啟用壓縮;

`any`

為所有代理請求啟用壓縮。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_types</strong> <code><i>mime-type</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_types text/html;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

除了「`text/html`"之外，還允許對指定MIME類型的響應進行gzipping。特殊值「`*`」匹配任何MIME類型（0.8.29）。具有「`text/html`」類型的響應始終被壓縮。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_vary</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_vary off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果指令[gzip](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip)、[gzip\_static](https://nginx.org/en/docs/http/ngx_http_gzip_static_module.html#gzip_static)或[gunzip](https://nginx.org/en/docs/http/ngx_http_gunzip_module.html#gunzip)處於活動狀態，則啟用或禁用插入「Vary：Accept-Encoding」響應標頭欄位。

#### 嵌入變量

`$gzip_ratio`

實現的壓縮比，計算為原始響應大小和壓縮響應大小之間的比率。