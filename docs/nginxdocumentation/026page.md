# page

> Source: https://nginx.org/en/docs/http/ngx_http_addition_module.html

---

## 目錄

- [Module ngx\_http\_addition\_module](#module-ngxhttpadditionmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_addition\_module

`ngx_http_addition_module`模塊是一個過濾器，用於在響應之前和之後添加文本。默認情況下不會構建此模塊，應使用`--with-http_addition_module`配置參數啟用它。

#### 配置示例

```nginx
location / {
    add_before_body /before_action;
    add_after_body  /after_action;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>add_before_body</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

將處理給定子請求後返回的文本添加到響應正文之前。空字符串（`""`）作為參數將取消從上一配置級別繼承的添加。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>add_after_body</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

將處理給定子請求後返回的文本添加到響應正文之後。空字符串（`""`）作為參數將取消從上一個配置級別繼承的添加。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>addition_types</strong> <code><i>mime-type</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>addition_types text/html;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在0.7.9版本中。

除了「`text/html`"之外，還允許在具有指定MIME類型的響應中添加文本。特殊值「`*`」匹配任何MIME類型（0.8.29）。