# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_realip_module.html

---

## 目錄

- [Module ngx\_stream\_realip\_module](#module-ngxstreamrealipmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_stream\_realip\_module

`ngx_stream_realip_module`模塊用於將客戶端地址和埠更改為在PROXY協議頭（1.11.4）中發送的地址和埠。PROXY協議必須預先通過在`listen`指令中設置[proxy\_protocol](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#proxy_protocol)參數來啟用。

默認情況下未構建此模塊，應使用`--with-stream_realip_module`配置參數啟用此模塊。

#### 配置示例

```nginx
listen 12345 proxy_protocol;

set_real_ip_from  192.168.1.0/24;
set_real_ip_from  192.168.2.1;
set_real_ip_from  2001:0db8::/32;
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>set_real_ip_from</strong> <code><i>address</i></code> | <code><i>CIDR</i></code> | <code>unix:</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

定義已知可發送正確替換地址的受信任地址。如果指定了特殊值`unix:`，則所有UNIX域套接字都將受信任。

#### 嵌入變量

`$realip_remote_addr`

保留原始客戶端地址

`$realip_remote_port`

保留原始客戶端埠