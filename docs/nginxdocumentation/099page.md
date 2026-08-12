# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_realip_module.html

---

## 目錄

- [Module ngx\_mail\_realip\_module](#module-ngxmailrealipmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_mail\_realip\_module

`ngx_mail_realip_module`模塊用於將客戶端地址和埠更改為在PROXY協議頭（1.19.8）中發送的地址和埠。PROXY協議必須預先通過在`listen`指令中設置[proxy\_protocol](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#proxy_protocol)參數來啟用。

#### 配置示例

```nginx
listen 110 proxy_protocol;

set_real_ip_from  192.168.1.0/24;
set_real_ip_from  192.168.2.1;
set_real_ip_from  2001:0db8::/32;
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>set_real_ip_from</strong> <code><i>address</i></code> | <code><i>CIDR</i></code> | <code>unix:</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

定義已知可發送正確替換地址的受信任地址。如果指定了特殊值`unix:`，則所有UNIX域套接字都將受信任。