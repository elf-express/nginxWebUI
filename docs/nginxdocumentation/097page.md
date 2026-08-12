# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_pop3_module.html

---

## 目錄

- [Module ngx\_mail\_pop3\_module](#module-ngxmailpop3module)
    - [Directives](#directives)

---

## Module ngx\_mail\_pop3\_module

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>pop3_auth</strong> <code><i>method</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>pop3_auth plain;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

為POP3客戶端設置允許的身份驗證方法。支持的方法包括：

`plain`

[USER/PASS](https://datatracker.ietf.org/doc/html/rfc1939), [AUTH PLAIN](https://datatracker.ietf.org/doc/html/rfc4616), [AUTH LOGIN](https://datatracker.ietf.org/doc/html/draft-murchison-sasl-login-00)

`apop`

[APOP](https://datatracker.ietf.org/doc/html/rfc1939)。要使此方法生效，密碼必須以未加密的方式存儲。

`cram-md5`

[AUTH CRAM-MD5](https://datatracker.ietf.org/doc/html/rfc2195)。要使此方法生效，密碼必須以未加密的方式存儲。

`external`

[AUTH EXTERNAL](https://datatracker.ietf.org/doc/html/rfc4422) (1.11.6).

始終啟用純文本身份驗證方法（`USER/PASS`、`AUTH PLAIN`和`AUTH LOGIN`），但如果未指定`plain`方法，`AUTH PLAIN`和`AUTH LOGIN`將不會自動包含在[pop3\_capabilities](https://nginx.org/en/docs/mail/ngx_mail_pop3_module.html#pop3_capabilities)中。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>pop3_capabilities</strong> <code><i>extension</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>pop3_capabilities TOP USER UIDL;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置傳遞給客戶端以響應`CAPA`命令的[POP3 protocol](https://datatracker.ietf.org/doc/html/rfc2449)擴展名列表。在[pop3\_auth](https://nginx.org/en/docs/mail/ngx_mail_pop3_module.html#pop3_auth)指令（[SASL](https://datatracker.ietf.org/doc/html/rfc2449)extension）和[STLS](https://datatracker.ietf.org/doc/html/rfc2595)中指定的身份驗證方法將根據[starttls](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#starttls)指令值自動添加到此列表。

指定客戶端代理到的POP3後端支持的擴展是有意義的（如果這些擴展與身份驗證後使用的命令相關，當nginx透明地代理客戶端連接到後端時）。

當前的標準化擴展列表發布於[www.iana.org](http://www.iana.org/assignments/pop3-extension-mechanism)。