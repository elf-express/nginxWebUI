# page

> Source: https://nginx.org/en/docs/http/ngx_http_flv_module.html

---

## 目錄

- [Module ngx\_http\_flv\_module](#module-ngxhttpflvmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_flv\_module

`ngx_http_flv_module`模塊為Flash視頻（FLV）文件提供偽流伺服器端支持。

它專門處理請求URI的查詢字符串中帶有`start`參數的請求，方法是從請求的字節偏移量開始並帶有前置FLV標頭的文件內容發回。

默認情況下不構建此模塊，應使用`--with-http_flv_module`配置參數啟用。

#### 配置示例

```nginx
location ~ \.flv$ {
    flv;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>flv</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

打開周圍位置的模塊處理。