# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html

---

## 目錄

- [Module ngx\_mail\_auth\_http\_module](#module-ngxmailauthhttpmodule)
    - [Directives](#directives)
    - [Protocol](#protocol)

---

## Module ngx\_mail\_auth\_http\_module

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_http</strong> <code><i>URL</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設定HTTP認證伺服器的URL。通信協定的描述為[below](https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html#protocol)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_http_header</strong> <code><i>header</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

將指定的標頭附加到發送到驗證伺服器的請求。此標頭可用作共享機密，以驗證請求是否來自nginx。例如：

> auth\_http\_header X-Auth-Key "secret\_string";

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_http_pass_client_cert</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_http_pass_client_cert off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.7.11版中。

將PEM格式（URL編碼）的帶有[client](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_verify_client)證書的「Auth-SSL-Cert」標頭附加到發送到身份驗證伺服器的請求中。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_http_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_http_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

設置與身份驗證伺服器通信的超時。

#### Protocol

HTTP協議用於與身份驗證伺服器通信。響應正文中的數據將被忽略，信息僅在標頭中傳遞。

請求和響應示例：

Request:

> GET /身份驗證HTTP/1.0
> >主機：本地主機
> Auth-Method：普通#普通/apop/cram-md5/外部
> >授權用戶：用戶
> >驗證通過：密碼
> >身份驗證協議：imap # imap/pop3/smtp
> >驗證登錄嘗試：1
> >客戶端IP位址：192.0.2.42
> >客戶端-主機：client.example.org

良好的響應：

> HTTP/1.0 200正常
> >驗證狀態：正常
> >認證伺服器：198.51.100.1
> Auth-Port：143

不良反應：

> HTTP/1.0 200 OK
> Auth-Status：登錄名或密碼無效
> Auth-Wait：3

如果沒有「Auth-Wait」標頭，將返回錯誤並關閉連接。當前實現為每次身份驗證嘗試分配內存。內存僅在會話結束時釋放。因此，必須限制單個會話中無效身份驗證嘗試的次數-伺服器必須在10-20次嘗試後不使用「Auth-Wait」報頭進行響應（嘗試次數在「Auth-Login-Attempt」頭中傳遞）。

當使用APOP或CRAM-MD5時，請求-響應將如下所示：

> GET /auth HTTP/1.0
> >主機：本地主機
> Auth-Method：apop
> >授權用戶：用戶
> Auth-Salt：<mail.example.com>
> Auth-Pass：auth\_response
> Auth-Protocol：imap
> Auth-Login-Attempt：1
> >客戶端IP位址：192.0.2.42
> >客戶端主機：client.example.org

良好的反應：

> HTTP/1.0 200 OK
> >驗證狀態：正常
> >認證伺服器：198.51.100.1
> Auth-Port：143
> Auth-Pass：純文本傳遞

如果響應中存在「Auth-User」標頭，則它將覆蓋用於向後端進行身份驗證的用戶名。

對於SMTP，響應還將考慮「Auth-Error-Code」頭-如果存在，則將其用作錯誤情況下的響應代碼。否則，535 5.7.0代碼將添加到「Auth-Status」頭。

例如，如果從認證伺服器接收到以下響應：

> HTTP/1.0 200 OK
> Auth-Status：伺服器暫時出現問題，請稍後再試
> Auth-Error-Code：451 4.3.0
> >作者等待：3

則SMTP客戶端將收到錯誤

> 451 4.3.0伺服器暫時出現問題，請稍後再試

如果驗證SMTP不需要身份驗證，則請求將如下所示：

> GET /auth HTTP/1.0
> >主機：本地主機
> Auth-Method：none
> Auth-User：
> Auth-Pass：
> Auth-Protocol：smtp
> Auth-Login-Attempt：1
> >客戶端IP位址：192.0.2.42
> >客戶端-主機：client.example.org
> Auth-SMTP-Helo：client.example.org
> Auth-SMTP-From：MAIL FROM：<>
> Auth-SMTP-To: RCPT TO: <postmaster@mail.example.com>

對於SSL/TLS客戶端連接（1.7.11），添加了「Auth-SSL」頭，並且「Auth-SSL-Verify」將包含客戶端證書驗證的結果，如果是[enabled](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_verify_client)：「`SUCCESS`"、「`FAILED:``*reason*`」，如果證書不存在，則為「`NONE`」。

> >在1.11.7版本之前，「`FAILED`」結果不包含`*reason*`字符串。

當客戶端證書存在時，其詳細信息將在以下請求標頭中傳遞：「Auth-SSL-Subject」、「Auth-SSL-Issuer」、「Auth-SSL-Serial」和「Auth-SSL-Fingerprint」。如果啟用[auth\_http\_pass\_client\_cert](https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html#auth_http_pass_client_cert)，證書本身在「Auth-SSL-Cert」頭中傳遞。已建立連接的協議和密碼在「Auth-SSL-Protocol」和「Auth-SSL-Cipher」頭中傳遞（1.21.2）.請求內容如下：

> GET /auth HTTP/1.0
> >主機：localhost
> Auth-Method：plain
> >授權用戶：用戶
> >驗證通過：密碼
> Auth-Protocol：imap
> >驗證登錄嘗試：1
> >客戶端IP：192.0.2.42
> Auth-SSL：on
> Auth-SSL-Protocol：TLSv1.3
> Auth-SSL-Cipher：TLS\_AES\_256\_GCM\_SHA384
> Auth-SSL-Verify：成功
> Auth-SSL-Subject：/CN=example.com
> Auth-SSL-Issuer：/CN=example.com
> Auth-SSL-Serial：C07AD56B846B5BFF
> Auth-SSL-指紋：29 d 6a 80 a123 d13355 ed 16 b4 b 04605 e29 cb 55 a5 ad

當使用[PROXY protocol](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#proxy_protocol)時，其詳細信息將在以下請求頭中傳遞：「Proxy-Protocol-Addr」、「Proxy-Protocol-Port」、「Proxy-Protocol-Server-Addr」和「Proxy-Protocol-Server-Port」（1.19.8）。