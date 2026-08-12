# page

> Source: https://nginx.org/en/docs/http/ngx_http_empty_gif_module.html

---

## 目錄

- [Module ngx\_http\_empty\_gif\_module](#module-ngxhttpemptygifmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_empty\_gif\_module

`ngx_http_empty_gif_module`模塊發出單像素透明GIF。

#### 配置示例

> location = /\_.gif {
>     empty\_gif;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>empty_gif</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

打開周圍位置的模塊處理。