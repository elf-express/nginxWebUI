# page

> Source: https://nginx.org/en/docs/http/ngx_http_num_map_module.html

---

## 目錄

- [Module ngx\_http\_num\_map\_module](#module-ngxhttpnummapmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_num\_map\_module

`ngx_http_num_map_module`模塊（1.29.3）創建其值依賴於數值或數值範圍的變量。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

```nginx
num_map $remote_port $port_allow {
    default    0;

    80         1;
    443        1;
    <=1023     0;
    8080-8090  1;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>num_map</strong> [<code><i>$number</i></code>] <code><i>$variable</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

描述指定變量的值如何取決於數值或數值範圍。

> 由於變量僅在使用時才被計算，因此即使存在大量聲明的「`num_map`」變量也不會為請求處理帶來任何額外的成本。

`num_map`塊內的參數指定源值和結果值之間的映射。

源值指定為數字或數字範圍。

還支持以下特殊參數：

`default`

如果源值不匹配任何指定變量，則設置結果值。如果未指定`default`，則默認結果值將為空字符串。

`include`

包含一個帶值的文件。可以有多個包含項。

`volatile`

表示該變量不可緩存。