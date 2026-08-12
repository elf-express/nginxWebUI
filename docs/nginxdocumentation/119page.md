# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_access_module.html

---

## 目錄

- [Module ngx\_stream\_access\_module](#module-ngxstreamaccessmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_access\_module

`ngx_stream_access_module`模塊（1.9.2）允許限制對某些客戶端地址的訪問。

#### 配置示例

```nginx
server {
    ...
    deny  192.168.1.1;
    allow 192.168.1.0/24;
    allow 10.1.1.0/16;
    allow 2001:0db8::/32;
    deny  all;
}
```

系統將按順序檢查規則，直到找到第一個匹配項。在本例中，僅允許訪問IPv4網絡`10.1.1.0/16`和`192.168.1.0/24`（不包括地址`192.168.1.1`）以及IPv6網絡`2001:0db8::/32`。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>allow</strong> <code><i>address</i></code> | <code><i>CIDR</i></code> | <code>unix:</code> | <code>all</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

允許訪問指定的網絡或地址。如果指定了特殊值`unix:`，則允許訪問所有UNIX域套接字。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>deny</strong> <code><i>address</i></code> | <code><i>CIDR</i></code> | <code>unix:</code> | <code>all</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

拒絕對指定網絡或地址的訪問。如果指定了特殊值`unix:`，則拒絕對所有UNIX域套接字的訪問。