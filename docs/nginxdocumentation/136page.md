# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_set_module.html

---

## 目錄

- [Module ngx\_stream\_set\_module](#module-ngxstreamsetmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_set\_module

`ngx_stream_set_module`模塊（1.19.3）允許設置變量的值。

#### 配置示例

```nginx
server {
    listen 12345;
    set    $true 1;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>set</strong> <code><i>$variable</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

為指定的`*variable*`設置一個`*value*`。`*value*`可以包含文本、變量及其組合。