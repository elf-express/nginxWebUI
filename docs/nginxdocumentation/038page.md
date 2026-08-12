# page

> Source: https://nginx.org/en/docs/http/ngx_http_f4f_module.html

---

## 目錄

- [Module ngx\_http\_f4f\_module](#module-ngxhttpf4fmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_f4f\_module

`ngx_http_f4f_module`模塊為Adobe HTTP動態流（HDS）提供伺服器端支持。

此模塊實現了以「`/videoSeg1-Frag1`」形式處理HTTP動態流請求-使用`videoSeg1.f4x`索引文件從`videoSeg1.f4f`文件中提取所需的片段。此模塊是Adobe用於Apache的f4 f模塊（HTTP Origin Module）的替代方案。

需要使用Adobe的f4 fpackager進行預處理，請參閱相關文檔了解詳細信息。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

> location /video/ {
>     f4f;
>     ...
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>f4f</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

打開周圍位置的模塊處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>f4f_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>f4f_buffer_size 512k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於閱讀`.f4x`索引文件的緩衝區的`*size*`。