# page

> Source: https://nginx.org/en/docs/http/ngx_http_random_index_module.html

---

## 目錄

- [Module ngx\_http\_random\_index\_module](#module-ngxhttprandomindexmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_random\_index\_module

`ngx_http_random_index_module`模塊處理以斜槓字符（「`/`」）結尾的請求，並在目錄中隨機選擇一個文件作為索引文件。該模塊在[ngx\_http\_index\_module](https://nginx.org/en/docs/http/ngx_http_index_module.html)模塊之前處理。

默認情況下未構建此模塊，應使用`--with-http_random_index_module`配置參數啟用此模塊。

#### 配置示例

```nginx
location / {
    random_index on;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>random_index</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>random_index off;</pre></td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

在周圍位置啟用或禁用模塊處理。