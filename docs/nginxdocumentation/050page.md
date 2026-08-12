# page

> Source: https://nginx.org/en/docs/http/ngx_http_index_module.html

---

## 目錄

- [Module ngx\_http\_index\_module](#module-ngxhttpindexmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_index\_module

`ngx_http_index_module`模塊處理以斜槓字符（「`/`」）結尾的請求。此類請求也可以由[ngx\_http\_autoindex\_module](https://nginx.org/en/docs/http/ngx_http_autoindex_module.html)和[ngx\_http\_random\_index\_module](https://nginx.org/en/docs/http/ngx_http_random_index_module.html)模塊處理。

#### 配置示例

```nginx
location / {
    index index.$geo.html index.html;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>index</strong> <code><i>file</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>index index.html;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義將用作索引的文件。`*file*`名稱可以包含變量。按指定順序檢查文件。列表的最後一個元素可以是具有絕對路徑的文件。示例：

```nginx
index index.$geo.html index.0.html /index.html;
```

需要注意的是，使用索引文件會導致內部重定向，請求可以在不同的位置處理。例如，使用以下配置：

```nginx
location = / {
    index index.html;
}

location / {
    ...
}
```

「`/`」請求將在第二個位置實際上作為「`/index.html`"處理。