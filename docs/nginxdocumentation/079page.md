# page

> Source: https://nginx.org/en/docs/http/ngx_http_sub_module.html

---

## 目錄

- [Module ngx\_http\_sub\_module](#module-ngxhttpsubmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_sub\_module

`ngx_http_sub_module`模塊是一個過濾器，它通過將一個指定的字符串替換為另一個字符串來修改響應。

默認情況下未構建此模塊，應使用`--with-http_sub_module`配置參數啟用此模塊。

#### 配置示例

> location / {
>     sub\_filter '<a href="http://127.0.0.1:8080/'  '<a href="https://$host/';
>     sub\_filter '<img src="http://127.0.0.1:8080/' '<img src="https://$host/';
>     sub\_filter\_once on;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>sub_filter</strong> <code><i>string</i></code> <code><i>replacement</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置一個要替換的字符串和一個替換字符串。要替換的字符串將忽略大小寫進行匹配。要替換的字符串（1.9.4）和替換字符串可以包含變量。可以在同一配置級別（1.9.4）上指定多個`sub_filter`指令。若且唯若當前級別上沒有定義`sub_filter`指令時，這些指令才從上一配置級別繼承。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>sub_filter_last_modified</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>sub_filter_last_modified off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.1版中。

允許在替換期間保留原始響應的「Last-Modified」頭欄位，以便於響應緩存。

默認情況下，在處理期間修改響應內容時，將刪除頭欄位。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>sub_filter_once</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>sub_filter_once on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指示是否查找每個字符串以替換一次或重複替換。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>sub_filter_types</strong> <code><i>mime-type</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>sub_filter_types text/html;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

在除「`text/html`"之外的指定MIME類型的響應中啟用字符串替換。特殊值「`*`」匹配任何MIME類型（0.8.29）。