# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_proxy_module.html

---

## 目錄

- [Module ngx\_mail\_proxy\_module](#module-ngxmailproxymodule)
    - [Directives](#directives)

---

## Module ngx\_mail\_proxy\_module

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_buffer</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_buffer 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置用於緩存的緩衝區大小。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小可以是4K或8 K。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_pass_error_message</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_pass_error_message off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

表示是否將後台身份驗證過程中獲取的錯誤消息傳遞給客戶端。

通常情況下，如果nginx中的身份驗證成功，後端不會返回錯誤。如果它仍然返回錯誤，則意味著發生了一些內部錯誤。在這種情況下，後端消息可能包含不應該顯示給客戶端的信息。但是，對於某些POP3伺服器來說，正確的密碼錯誤是正常的行為。例如，CommuniGatePro通過定期輸出[authentication error](http://www.stalker.com/CommuniGatePro/POP.html#Alerts)來通知用戶有關[mailbox overflow](http://www.stalker.com/CommuniGatePro/Alerts.html#Quota)或其他事件。在這種情況下應啟用該指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_protocol</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_protocol off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

該指令出現在1.19.8版本中。

為到後端的連接啟用[PROXY protocol](http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_smtp_auth</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_smtp_auth off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.19.4版中。

使用`AUTH`命令啟用或禁用SMTP後端上的用戶身份驗證。

如果還啟用了[XCLIENT](https://nginx.org/en/docs/mail/ngx_mail_proxy_module.html#xclient)，則`XCLIENT`命令將不會發送`LOGIN`參數。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_timeout 24h;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

在客戶端或代理伺服器連接上設置兩個連續讀或寫操作之間的`*timeout*`。如果在此時間內沒有數據傳輸，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>xclient</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>xclient on;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

在連接到SMTP後端時啟用或禁用帶有客戶端參數的[XCLIENT](http://www.postfix.org/XCLIENT_README.html)命令的傳遞。

使用`XCLIENT`，MTA能夠將客戶端信息寫入日誌，並基於此數據應用各種限制。

如果開啟了`XCLIENT`，nginx在連接後台時會傳遞以下命令：

-   `EHLO`與[server name](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#server_name)
-   `XCLIENT`
-   `EHLO`或`HELO`，由客戶端傳遞

如果客戶端IP位址的名稱[found](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#resolver)指向相同的地址，則在`XCLIENT`命令的`NAME`參數中傳遞。如果找不到名稱，指向不同的地址，或未指定[resolver](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#resolver)，則在`NAME`參數中傳遞`[UNAVAILABLE]`。如果在解析過程中發生錯誤，則使用`[TEMPUNAVAIL]`值。

如果`XCLIENT`被禁用，那麼nginx在連接到後端時，如果客戶端傳遞了`EHLO`，則會傳遞`EHLO`命令和[server name](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#server_name)，否則會傳遞`HELO`和伺服器名稱。