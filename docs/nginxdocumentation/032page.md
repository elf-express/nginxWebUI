# page

> Source: https://nginx.org/en/docs/http/ngx_http_autoindex_module.html

---

## 目錄

- [Module ngx\_http\_autoindex\_module](#module-ngxhttpautoindexmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_autoindex\_module

`ngx_http_autoindex_module`模塊處理以斜槓字符（「`/`」）結尾的請求並生成目錄列表。通常，當[ngx\_http\_index\_module](https://nginx.org/en/docs/http/ngx_http_index_module.html)模塊找不到索引文件時，會將請求傳遞給`ngx_http_autoindex_module`模塊。

#### 配置示例

```nginx
location / {
    autoindex on;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>autoindex</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>autoindex off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

啟用或禁用目錄列表輸出。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>autoindex_exact_size</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>autoindex_exact_size on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

對於HTML[format](https://nginx.org/en/docs/http/ngx_http_autoindex_module.html#autoindex_format)，指定是否應在目錄列表中輸出確切的文件大小，或者四捨五入為千字節、兆字節和千兆字節。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>autoindex_format</strong> <code>html</code> | <code>xml</code> | <code>json</code> | <code>jsonp</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>autoindex_format html;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.9版本中。

設置目錄列表的格式。

當使用JSONP格式時，回調函數的名稱使用`callback`request參數設置。如果參數缺失或具有空值，則使用JSON格式。

可以使用[ngx\_http\_xslt\_module](https://nginx.org/en/docs/http/ngx_http_xslt_module.html)模塊轉換XML輸出。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>autoindex_localtime</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>autoindex_localtime off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

對於HTML[format](https://nginx.org/en/docs/http/ngx_http_autoindex_module.html#autoindex_format)，指定目錄列表中的時間應按本地時區還是UTC輸出。