# page

> Source: https://nginx.org/en/docs/http/ngx_http_gunzip_module.html

---

## 目錄

- [Module ngx\_http\_gunzip\_module](#module-ngxhttpgunzipmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_gunzip\_module

`ngx_http_gunzip_module`模塊是一個過濾器，用於為不支持「gzip」編碼方法的客戶端解壓縮帶有「`Content-Encoding: gzip`」的響應。當需要壓縮存儲數據以節省空間和降低I/O成本時，該模塊將非常有用。

默認情況下不構建此模塊，應使用`--with-http_gunzip_module`配置參數啟用。

#### 配置示例

```nginx
location /storage/ {
    gunzip on;
    ...
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gunzip</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gunzip off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為不支持gzip的客戶端啟用或禁用gzip壓縮響應的解壓縮。如果啟用，則在確定客戶端是否支持gzip時還將考慮以下指令：[gzip\_http\_version](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_http_version)、[gzip\_proxied](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_proxied)和[gzip\_disable](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_disable)。另請參閱[gzip\_vary](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_vary)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gunzip_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gunzip_buffers 32 4k|16 8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於緩存響應的緩衝區的`*number*`和`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小可以是4K或8 K。