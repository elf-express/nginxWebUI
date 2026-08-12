# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_imap_module.html

---

## 目錄

- [Module ngx\_mail\_imap\_module](#module-ngxmailimapmodule)
    - [Directives](#directives)

---

## Module ngx\_mail\_imap\_module

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>imap_auth</strong> <code><i>method</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>imap_auth plain;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

為IMAP客戶端設置允許的身份驗證方法。支持的方法包括：

`plain`

[LOGIN](https://datatracker.ietf.org/doc/html/rfc3501), [AUTH=PLAIN](https://datatracker.ietf.org/doc/html/rfc4616)

`login`

[AUTH=LOGIN](https://datatracker.ietf.org/doc/html/draft-murchison-sasl-login-00)

`cram-md5`

[AUTH=CRAM-MD5](https://datatracker.ietf.org/doc/html/rfc2195)。要使此方法生效，密碼必須以未加密的方式存儲。

`external`

[AUTH=EXTERNAL](https://datatracker.ietf.org/doc/html/rfc4422) (1.11.6).

始終啟用純文本身份驗證方法（`LOGIN`命令、`AUTH=PLAIN`和`AUTH=LOGIN`），但如果未指定`plain`和`login`方法，`AUTH=PLAIN`和`AUTH=LOGIN`將不會自動包含在[imap\_capabilities](https://nginx.org/en/docs/mail/ngx_mail_imap_module.html#imap_capabilities)中。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>imap_capabilities</strong> <code><i>extension</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>imap_capabilities IMAP4 IMAP4rev1 UIDPLUS;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置傳遞給客戶端以響應`CAPABILITY`命令的[IMAP protocol](https://datatracker.ietf.org/doc/html/rfc3501)擴展名列表。在[imap\_auth](https://nginx.org/en/docs/mail/ngx_mail_imap_module.html#imap_auth)指令和[STARTTLS](https://datatracker.ietf.org/doc/html/rfc2595)中指定的身份驗證方法將根據[starttls](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#starttls)指令值自動添加到此列表。

指定客戶端代理到的IMAP後端支持的擴展是有意義的（如果這些擴展與身份驗證後使用的命令相關，當nginx透明地代理客戶端連接到後端時）。

當前的標準化擴展列表發布於[www.iana.org](http://www.iana.org/assignments/imap4-capabilities)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>imap_client_buffer</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>imap_client_buffer 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置用於閱讀IMAP命令的緩衝區的`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小可以是4K或8 K。