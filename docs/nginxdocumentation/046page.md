# page

> Source: https://nginx.org/en/docs/http/ngx_http_gzip_static_module.html

---

## 目錄

- [Module ngx\_http\_gzip\_static\_module](#module-ngxhttpgzipstaticmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_gzip\_static\_module

`ngx_http_gzip_static_module`模塊允許發送帶有「`.gz`」文件擴展名的預壓縮文件，而不是普通文件。

默認情況下不構建此模塊，應使用`--with-http_gzip_static_module`配置參數啟用。

#### 配置示例

```nginx
gzip_static  on;
gzip_proxied expired no-cache no-store private auth;
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>gzip_static</strong> <code>on</code> | <code>off</code> | <code>always</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>gzip_static off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

啟用（「`on`」）或禁用（「`off`」）檢查是否存在預壓縮文件。還將考慮以下指令：[gzip\_http\_version](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_http_version)、[gzip\_proxied](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_proxied)、[gzip\_disable](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_disable)和[gzip\_vary](https://nginx.org/en/docs/http/ngx_http_gzip_module.html#gzip_vary)。

使用「`always`」值（1.3.6），在所有情況下都使用gzip壓縮文件，而不檢查客戶端是否支持它。如果磁碟上沒有未壓縮的文件或使用[ngx\_http\_gunzip\_module](https://nginx.org/en/docs/http/ngx_http_gunzip_module.html)，這很有用。

可以使用`gzip`命令或其他兼容命令壓縮文件。建議原始文件和壓縮文件的修改日期和時間相同。