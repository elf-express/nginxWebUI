# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_core_module.html

---

## 目錄

- [Module ngx\_mail\_core\_module](#module-ngxmailcoremodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_mail\_core\_module

默認情況下未構建此模塊，應使用`--with-mail`配置參數啟用此模塊。

#### 配置示例

> worker\_processes auto;
> 
> error\_log /var/log/nginx/error.log info;
> 
> events {
>     worker\_connections  1024;
> }
> 
> mail {
>     server\_name       mail.example.com;
>     auth\_http         localhost:9000/cgi-bin/nginxauth.cgi;
> 
>     imap\_capabilities IMAP4rev1 UIDPLUS IDLE LITERAL+ QUOTA;
> 
>     pop3\_auth         plain apop cram-md5;
>     pop3\_capabilities LAST TOP USER PIPELINING UIDL;
> 
>     smtp\_auth         login plain cram-md5;
>     smtp\_capabilities "SIZE 10485760" ENHANCEDSTATUSCODES 8BITMIME DSN;
>     xclient           off;
> 
>     server {
>         listen   25;
>         protocol smtp;
>     }
>     server {
>         listen   110;
>         protocol pop3;
>         proxy\_pass\_error\_message on;
>     }
>     server {
>         listen   143;
>         protocol imap;
>     }
>     server {
>         listen   587;
>         protocol smtp;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>listen</strong> <code><i>address</i></code>:<code><i>port</i></code> [<code>ssl</code>] [<code>proxy_protocol</code>] [<code>backlog</code>=<code><i>number</i></code>] [<code>rcvbuf</code>=<code><i>size</i></code>] [<code>sndbuf</code>=<code><i>size</i></code>] [<code>bind</code>] [<code>ipv6only</code>=<code>on</code>|<code>off</code>] [<code>multipath</code>] [<code>so_keepalive</code>=<code>on</code>|<code>off</code>|[<code><i>keepidle</i></code>]:[<code><i>keepintvl</i></code>]:[<code><i>keepcnt</i></code>]];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

為伺服器將接受請求的套接字設置`*address*`和`*port*`。可以只指定埠。地址也可以是主機名，例如：

> listen 127.0.0.1:110;
> listen \*:110;
> listen 110;     # same as \*:110
> listen localhost:110;

IPv6地址（0.7.58）在方括號中指定：

> listen \[::1\]:110;
> listen \[::\]:110;

UNIX域套接字（1.3.5）使用「`unix:`」前綴指定：

> listen unix:/var/run/nginx.sock;

不同的伺服器必須偵聽不同的`*address*`：`*port*`對。

`ssl`參數允許指定此埠上接受的所有連接都應在SSL模式下工作。

`proxy_protocol`參數（1.19.8）允許指定此埠上接受的所有連接都應使用[PROXY protocol](http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)。獲得的信息將傳遞給[authentication server](https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html#proxy_protocol)，並可用於[change the client address](https://nginx.org/en/docs/mail/ngx_mail_realip_module.html)。

`listen`指令可以有幾個特定於套接字相關系統調用的附加參數。

`backlog`\=`*number*`

在`listen()`調用中設置`backlog`參數，限制掛起連接隊列的最大長度（1.9.2）。默認情況下，`backlog`在FreeBSD、DragonFly BSD和macOS上設置為-1，在其他平台上設置為511。

`rcvbuf`\=`*size*`

設置監聽套接字的接收緩衝區大小（`SO_RCVBUF`選項）（1.11.13）。

`sndbuf`\=`*size*`

為監聽套接字設置發送緩衝區大小（`SO_SNDBUF`選項）（1.11.13）。

`bind`

這個參數指示對給定的address：port對進行單獨的`bind()`調用。事實是，如果有幾個`listen`指令具有相同的埠但不同的地址，並且其中一個`listen`指令監聽給定埠的所有地址（`*:``*port*`），nginx將僅從`bind()`到`*:``*port*`。應該注意的是，在這種情況下，將進行`getsockname()`系統調用以確定接受連接的地址。如果使用`backlog`，`rcvbuf`，`sndbuf`，`ipv6only`，`multipath`或`so_keepalive`參數，則對於給定的`*address*`：`*port*`對，將始終進行單獨的`bind()`調用。

`ipv6only`\=`on`|`off`

此參數決定（通過`IPV6_V6ONLY`socket選項）IPv6套接字偵聽IP位址`[::]`是否只接受IPv6連接或同時接受IPv6和IPv4連接。2此參數默認打開。3它只能在啟動時設置一次。

`multipath`

這個參數（1.29.7）為監聽套接字配置[Multipath TCP](https://datatracker.ietf.org/doc/html/rfc8684)協議（`IPPROTO_MPTCP`）。2這目前只在Linux 5.6+上工作。

> >添加或刪除此參數還將啟用`SO_REUSEPORT`socket選項，該選項的安全性可能為[implications](http://man7.org/linux/man-pages/man7/socket.7.html)。

`so_keepalive`\=`on`|`off`|\[`*keepidle*`\]:\[`*keepintvl*`\]:\[`*keepcnt*`\]

此參數為偵聽套接字配置「TCP keepalive」行為。如果省略此參數，則作業系統的設置將對套接字生效。如果將其設置為值「`on`"，則為套接字打開`SO_KEEPALIVE`選項。如果將其設置為值「`off`"，套接字的`SO_KEEPALIVE`選項已關閉。某些作業系統支持使用`TCP_KEEPIDLE`、`TCP_KEEPINTVL`和`TCP_KEEPCNT`套接字選項在每個套接字的基礎上設置TCP keepalive參數。在此類系統上（目前為Linux、NetBSD、Dragonfly、FreeBSD、macOS），可以使用`*keepidle*`、`*keepintvl*`、`*keepcnt*`參數進行配置。可以省略一個或兩個參數，此時對應套接字選項的系統默認設置將生效。例如，

> so\_keepalive=30m::10

將設置空閒超時（`TCP_KEEPIDLE`）為30分鐘，保留探測間隔（`TCP_KEEPINTVL`）為系統默認值，並將探測計數（`TCP_KEEPCNT`）設置為10個探測。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mail</strong> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

提供在其中指定郵件伺服器指令的配置文件上下文。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>max_errors</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>max_errors 5;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.21.0版中。

設置協議錯誤的數量，在此數量之後關閉連接。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>protocol</strong> <code>imap</code> | <code>pop3</code> | <code>smtp</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

設置代理伺服器的協議。支持的協議有[IMAP](https://nginx.org/en/docs/mail/ngx_mail_imap_module.html)、[POP3](https://nginx.org/en/docs/mail/ngx_mail_pop3_module.html)和[SMTP](https://nginx.org/en/docs/mail/ngx_mail_smtp_module.html)。

如果未設置該指令，則可以根據[listen](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#listen)指令中指定的已知埠自動檢測協議：

-   `imap`: 143, 993
-   `pop3`: 110, 995
-   `smtp`: 25, 587, 465

可以使用[configuration](https://nginx.org/en/docs/configure.html)參數`--without-mail_imap_module`、`--without-mail_pop3_module`和`--without-mail_smtp_module`禁用不必要的協議。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver</strong> <code><i>address</i></code> ... [<code>valid</code>=<code><i>time</i></code>] [<code>ipv4</code>=<code>on</code>|<code>off</code>] [<code>ipv6</code>=<code>on</code>|<code>off</code>] [<code>status_zone</code>=<code><i>zone</i></code>];</code><br><code><strong>resolver</strong> <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>resolver off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

配置用於查找客戶端主機名的名稱伺服器，以便在禁用SMTP時將其傳遞給[authentication server](https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html)和[XCLIENT](https://nginx.org/en/docs/mail/ngx_mail_proxy_module.html#xclient)命令。例如：

> resolver 127.0.0.1 \[::1\]:5353;

地址可以指定為域名或IP位址，並帶有可選埠（1.3.1，1.2.2）。如果未指定埠，則使用埠53。名稱伺服器以循環方式查詢。

> >在1.1.7版本之前，只能配置單個名稱伺服器。從1.3.1和1.2.2版本開始，支持使用IPv6地址的域名伺服器。

默認情況下，nginx在解析時會同時查找IPv4和IPv6地址。如果不需要查找IPv4或IPv6地址，可以指定`ipv4=off`（1.23.1）或`ipv6=off`參數。

> >從版本1.5.8開始支持將名稱解析為IPv6地址。

默認情況下，nginx會使用響應的TTL值來緩存答案。可選的`valid`參數允許覆蓋它：

> resolver 127.0.0.1 \[::1\]:5353 valid=30s;

> >在1.1.9版本之前，無法調整緩存時間，nginx總是將答案緩存5分鐘。

> >為了防止DNS欺騙，建議在適當安全的可信本地網絡中配置DNS伺服器。

可選的`status_zone`參數（1.17.1）在指定的`*zone*`中啟用[collection](https://nginx.org/en/docs/http/ngx_http_api_module.html#resolvers_)的請求和響應的DNS伺服器統計信息。該參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

特殊值`off`禁用解析。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>resolver_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置DNS操作的超時，例如：

> resolver\_timeout 5s;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server</strong> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code><br></td></tr></tbody></table>

設置伺服器的配置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>server_name hostname;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置使用的伺服器名稱：

-   在初始POP3/SMTP伺服器問候中;
-   在SASL CRAM-MD5身份驗證期間的鹽中;
-   如果啟用了[XCLIENT](https://nginx.org/en/docs/mail/ngx_mail_proxy_module.html#xclient)命令的傳遞，則在連接到SMTP後端時在`EHLO`命令中。

如果未指定該指令，則使用計算機的主機名。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置在開始備份到後端之前使用的超時。