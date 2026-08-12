# page

> Source: https://nginx.org/en/docs/http/ngx_http_slice_module.html

---

## 目錄

- [Module ngx\_http\_slice\_module](#module-ngxhttpslicemodule)
    - [Known Issues](#known-issues)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_slice\_module

`ngx_http_slice_module`模塊（1.9.8）是一個過濾器，它將一個請求拆分成子請求，每個子請求返回一定範圍的響應。過濾器提供了更有效的大響應緩存。

默認情況下不構建此模塊，應使用`--with-http_slice_module`配置參數啟用。

#### 已知問題

目前，模塊在子請求（如[background cache update](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache_background_update)）中無法正常工作。在這種情況下，請求的構造不支持字節範圍。

#### 配置示例

```nginx
location / {
    **slice**             1m;
    proxy_cache       cache;
    proxy_cache_key   $uri$is_args$args**$slice_range**;
    proxy_set_header  Range **$slice_range**;
    proxy_cache_valid 200 206 1h;
    proxy_pass        http://localhost:8000;
}
```

在本例中，響應被分割為1 MB的可緩存切片。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>slice</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>slice 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置切片的`*size*`。零值禁止將響應拆分為切片。注意，太小的值可能會導致過多的內存使用和打開大量文件。

為了使子請求返回所需的範圍，`$slice_range`變量應該是[passed](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header)到代理伺服器作為`Range`請求頭欄位。如果啟用了[caching](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache)，則應將`$slice_range`添加到[cache key](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache_key)，並且緩存具有206狀態碼的響應應是[enabled](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache_valid)。

#### 嵌入變量

`ngx_http_slice_module`模塊支持以下嵌入變量：

`$slice_range`

當前切片範圍，格式為[HTTP byte range](https://datatracker.ietf.org/doc/html/rfc7233#section-2.1)，例如`bytes=0-1048575`。