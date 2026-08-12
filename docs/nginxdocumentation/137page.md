# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_split_clients_module.html

---

## 目錄

- [Module ngx\_stream\_split\_clients\_module](#module-ngxstreamsplitclientsmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_split\_clients\_module

`ngx_stream_split_clients_module`模塊（1.11.3）創建適用於A/B測試的變量，也稱為分割測試。

#### 配置示例

> stream {
>     ...
>     split\_clients "${remote\_addr}AAA" $upstream {
>                   0.5%                feature\_test1;
>                   2.0%                feature\_test2;
>                   \*                   production;
>     }
> 
>     server {
>         ...
>         proxy\_pass $upstream;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>split_clients</strong> <code><i>string</i></code> <code><i>$variable</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

為A/B測試創建變量，例如：

> split\_clients "${remote\_addr}AAA" $variant {
>                0.5%               .one;
>                2.0%               .two;
>                \*                  "";
> }

原始字符串的值使用MurmurHash 2進行散列。（0.5%）對應於`$variant`變量的值`".one"`，從21474836到107374180（2%）的哈希值對應於值`".two"`，並且從107374181到4294967295的散列值對應於值`""`（空字符串）。