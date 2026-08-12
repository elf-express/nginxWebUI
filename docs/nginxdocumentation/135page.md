# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_return_module.html

---

## 目錄

- [Module ngx\_stream\_return\_module](#module-ngxstreamreturnmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_return\_module

`ngx_stream_return_module`模塊（1.11.2）允許向客戶端發送指定的值，然後關閉連接。

#### 配置示例

```nginx
server {
    listen 12345;
    return $time_iso8601;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>return</strong> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

指定要發送到客戶端的`*value*`。該值可以包含文本、變量及其組合。