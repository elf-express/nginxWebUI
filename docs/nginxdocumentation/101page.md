# page

> Source: https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html

---

## 目錄

- [Module ngx\_mail\_ssl\_module](#module-ngxmailsslmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_mail\_ssl\_module

The `ngx_mail_ssl_module` module provides the necessary support for a mail proxy server to work with the SSL/TLS protocol.

This module is not built by default, it should be enabled with the `--with-mail_ssl_module` configuration parameter.

> This module requires the [OpenSSL](http://www.openssl.org/) library.

#### Example Configuration

To reduce the processor load, it is recommended to

-   set the number of [worker processes](https://nginx.org/en/docs/ngx_core_module.html#worker_processes) equal to the number of processors,
-   enable the [shared](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_session_cache_shared) session cache,
-   disable the [built-in](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_session_cache_builtin) session cache,
-   and possibly increase the session [lifetime](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_session_timeout) (by default, 5 minutes):

> **worker\_processes auto;**
> 
> mail {
> 
>     ...
> 
>     server {
>         listen              993 ssl;
> 
>         ssl\_protocols       TLSv1.2 TLSv1.3;
>         ssl\_ciphers         AES128-SHA:AES256-SHA:RC4-SHA:DES-CBC3-SHA:RC4-MD5;
>         ssl\_certificate     /usr/local/nginx/conf/cert.pem;
>         ssl\_certificate\_key /usr/local/nginx/conf/cert.key;
>         **ssl\_session\_cache   shared:SSL:10m;**
>         **ssl\_session\_timeout 10m;**
> 
>         ...
>     }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive was made obsolete in version 1.15.0 and was removed in version 1.25.1. The `ssl` parameter of the [listen](https://nginx.org/en/docs/mail/ngx_mail_core_module.html#listen) directive should be used instead.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with the certificate in the PEM format for the given server. If intermediate certificates should be specified in addition to a primary certificate, they should be specified in the same file in the following order: the primary certificate comes first, then the intermediate certificates. A secret key in the PEM format may be placed in the same file.

Since version 1.11.0, this directive can be specified multiple times to load certificates of different types, for example, RSA and ECDSA:

> server {
>     listen              993 ssl;
> 
>     ssl\_certificate     example.com.rsa.crt;
>     ssl\_certificate\_key example.com.rsa.key;
> 
>     ssl\_certificate     example.com.ecdsa.crt;
>     ssl\_certificate\_key example.com.ecdsa.key;
> 
>     ...
> }

> Only OpenSSL 1.0.2 or higher supports separate [certificate chains](https://nginx.org/en/docs/http/configuring_https_servers.html#chains) for different certificates. With older versions, only one certificate chain can be used.

The value `data`:`*certificate*` can be specified instead of the `*file*` (1.15.10), which loads a certificate without using intermediate files. Note that inappropriate use of this syntax may have its security implications, such as writing secret key data to [error log](https://nginx.org/en/docs/ngx_core_module.html#error_log).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate_compression</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_certificate_compression off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.1.

Enables TLS 1.3 [compression](https://datatracker.ietf.org/doc/html/rfc8879) of server certificates.

> The directive is supported when using OpenSSL 3.2 or higher; the list of supported compression algorithms is provided by the library.

> The directive is supported when using BoringSSL; the list of supported compression algorithms includes `zlib` (1.29.3).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with the secret key in the PEM format for the given server.

The value `engine`:`*name*`:`*id*` can be specified instead of the `*file*` (1.7.9), which loads a secret key with a specified `*id*` from the OpenSSL engine `*name*`.

The value `store`:`*scheme*`:`*id*` can be specified instead of the `*file*` (1.29.0), which is used to load a secret key with a specified `*id*` and OpenSSL provider registered URI `*scheme*`, such as [`pkcs11`](https://datatracker.ietf.org/doc/html/rfc7512).

The value `data`:`*key*` can be specified instead of the `*file*` (1.15.10), which loads a secret key without using intermediate files. Note that inappropriate use of this syntax may have its security implications, such as writing secret key data to [error log](https://nginx.org/en/docs/ngx_core_module.html#error_log).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_ciphers HIGH:!aNULL:!MD5;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Specifies the enabled ciphers. The ciphers are specified in the format understood by the OpenSSL library, for example:

> ssl\_ciphers ALL:!aNULL:!EXPORT56:RC4+RSA:+HIGH:+MEDIUM:+LOW:+SSLv2:+EXP;

The full list can be viewed using the “`openssl ciphers`” command.

> The previous versions of nginx used [different](https://nginx.org/en/docs/http/configuring_https_servers.html#compatibility) ciphers by default.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_client_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.11.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_verify_client) client certificates.

The list of certificates will be sent to clients. If this is not desired, the [ssl\_trusted\_certificate](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_trusted_certificate) directive can be used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_conf_command</strong> <code><i>name</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.19.4.

Sets arbitrary OpenSSL configuration [commands](https://www.openssl.org/docs/man1.1.1/man3/SSL_CONF_cmd.html).

> The directive is supported when using OpenSSL 1.0.2 or higher.

Several `ssl_conf_command` directives can be specified on the same level:

> ssl\_conf\_command Options PrioritizeChaCha;
> ssl\_conf\_command Ciphersuites TLS\_CHACHA20\_POLY1305\_SHA256;

These directives are inherited from the previous configuration level if and only if there are no `ssl_conf_command` directives defined on the current level.

> Note that configuring OpenSSL directly might result in unexpected behavior.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.11.

Specifies a `*file*` with revoked certificates (CRL) in the PEM format used to [verify](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_verify_client) client certificates. When using intermediate certificates, their CRLs should be specified in the same file.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_dhparam</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.2.

Specifies a `*file*` with DH parameters for DHE ciphers.

By default no parameters are set, and therefore DHE ciphers will not be used.

> Prior to version 1.11.0, builtin parameters were used by default.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ecdh_curve</strong> <code><i>curve</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_ecdh_curve auto;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in versions 1.1.0 and 1.0.6.

Specifies a `*curve*` for ECDHE ciphers.

When using OpenSSL 1.0.2 or higher, it is possible to specify multiple curves (1.11.0), for example:

> ssl\_ecdh\_curve prime256v1:secp384r1;

The special value `auto` (1.11.0) instructs nginx to use a list built into the OpenSSL library when using OpenSSL 1.0.2 or higher, or `prime256v1` with older versions.

> Prior to version 1.11.0, the `prime256v1` curve was used by default.

> When using OpenSSL 1.0.2 or higher, this directive sets the list of curves supported by the server. Thus, in order for ECDSA certificates to work, it is important to include the curves used in the certificates.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_password_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.3.

Specifies a `*file*` with passphrases for [secret keys](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_certificate_key) where each passphrase is specified on a separate line. Passphrases are tried in turn when loading the key.

Example:

> mail {
>     ssl\_password\_file /etc/keys/global.pass;
>     ...
> 
>     server {
>         server\_name mail1.example.com;
>         ssl\_certificate\_key /etc/keys/first.key;
>     }
> 
>     server {
>         server\_name mail2.example.com;
> 
>         # named pipe can also be used instead of a file
>         ssl\_password\_file /etc/keys/fifo;
>         ssl\_certificate\_key /etc/keys/second.key;
>     }
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_prefer_server_ciphers</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_prefer_server_ciphers off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Specifies that server ciphers should be preferred over client ciphers when the SSLv3 and TLS protocols are used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_protocols</strong> [<code>SSLv2</code>] [<code>SSLv3</code>] [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_protocols TLSv1.2 TLSv1.3;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Enables the specified protocols.

> The `TLSv1.1` and `TLSv1.2` parameters (1.1.13, 1.0.12) work only when OpenSSL 1.0.1 or higher is used.

> The `TLSv1.3` parameter (1.13.0) works only when OpenSSL 1.1.1 or higher is used.

> The `TLSv1.3` parameter is used by default since 1.23.4.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_cache</strong> <code>off</code> | <code>none</code> | [<code>builtin</code>[:<code><i>size</i></code>]] [<code>shared</code>:<code><i>name</i></code>:<code><i>size</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_session_cache none;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Sets the types and sizes of caches that store session parameters. A cache can be of any of the following types:

`off`

the use of a session cache is strictly prohibited: nginx explicitly tells a client that sessions may not be reused.

`none`

the use of a session cache is gently disallowed: nginx tells a client that sessions may be reused, but does not actually store session parameters in the cache.

`builtin`

a cache built in OpenSSL; used by one worker process only. The cache size is specified in sessions. If size is not given, it is equal to 20480 sessions. Use of the built-in cache can cause memory fragmentation.

`shared`

a cache shared between all worker processes. The cache size is specified in bytes; one megabyte can store about 4000 sessions. Each shared cache should have an arbitrary name. A cache with the same name can be used in several servers. It is also used to automatically generate, store, and periodically rotate TLS session ticket keys (1.23.2) unless configured explicitly using the [ssl\_session\_ticket\_key](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_session_ticket_key) directive.

Both cache types can be used simultaneously, for example:

> ssl\_session\_cache builtin:1000 shared:SSL:10m;

but using only shared cache without the built-in cache should be more efficient.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_ticket_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.7.

Sets a `*file*` with the secret key used to encrypt and decrypt TLS session tickets. The directive is necessary if the same key has to be shared between multiple servers. By default, a randomly generated key is used.

If several keys are specified, only the first key is used to encrypt TLS session tickets. This allows configuring key rotation, for example:

> ssl\_session\_ticket\_key current.key;
> ssl\_session\_ticket\_key previous.key;

The `*file*` must contain 80 or 48 bytes of random data and can be created using the following command:

> openssl rand 80 > ticket.key

Depending on the file size either AES256 (for 80-byte keys, 1.11.8) or AES128 (for 48-byte keys) is used for encryption.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_tickets</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_session_tickets on;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.9.

Enables or disables session resumption through [TLS session tickets](https://datatracker.ietf.org/doc/html/rfc5077).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_session_timeout 5m;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a time during which a client may reuse the session parameters.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.11.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_verify_client) client certificates.

In contrast to the certificate set by [ssl\_client\_certificate](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html#ssl_client_certificate), the list of these certificates will not be sent to clients.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_verify_client</strong> <code>on</code> | <code>off</code> | <code>optional</code> | <code>optional_no_ca</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_verify_client off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.11.

Enables verification of client certificates. The verification result is passed in the “Auth-SSL-Verify” header of the [authentication](https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html#auth_http) request.

The `optional` parameter requests the client certificate and verifies it if the certificate is present.

The `optional_no_ca` parameter requests the client certificate but does not require it to be signed by a trusted CA certificate. This is intended for the use in cases when a service that is external to nginx performs the actual certificate verification. The contents of the certificate is accessible through requests [sent](https://nginx.org/en/docs/mail/ngx_mail_auth_http_module.html#auth_http_pass_client_cert) to the authentication server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_verify_depth 1;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.11.

Sets the verification depth in the client certificates chain.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>starttls</strong> <code>on</code> | <code>off</code> | <code>only</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>starttls off;</pre></td></tr><tr><th>Context:</th><td><code>mail</code>, <code>server</code><br></td></tr></tbody></table>

`on`

allow usage of the `STLS` command for the POP3 and the `STARTTLS` command for the IMAP and SMTP;

`off`

deny usage of the `STLS` and `STARTTLS` commands;

`only`

require preliminary TLS transition.