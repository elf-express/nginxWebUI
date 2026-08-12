# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html

---

## 目錄

- [Module ngx\_stream\_ssl\_module](#module-ngxstreamsslmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_stream\_ssl\_module

The `ngx_stream_ssl_module` module (1.9.0) provides the necessary support for a stream proxy server to work with the SSL/TLS protocol.

This module is not built by default, it should be enabled with the `--with-stream_ssl_module` configuration parameter.

> This module requires the [OpenSSL](http://www.openssl.org/) library.

#### Example Configuration

To reduce the processor load, it is recommended to

-   set the number of [worker processes](https://nginx.org/en/docs/ngx_core_module.html#worker_processes) equal to the number of processors,
-   enable the [shared](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_session_cache_shared) session cache,
-   disable the [built-in](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_session_cache_builtin) session cache,
-   and possibly increase the session [lifetime](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_session_timeout) (by default, 5 minutes):

```nginx
**worker_processes auto;**

stream {

    ...

    server {
        listen              12345 ssl;

        ssl_protocols       TLSv1.2 TLSv1.3;
        ssl_ciphers         AES128-SHA:AES256-SHA:RC4-SHA:DES-CBC3-SHA:RC4-MD5;
        ssl_certificate     /usr/local/nginx/conf/cert.pem;
        ssl_certificate_key /usr/local/nginx/conf/cert.key;
        **ssl_session_cache   shared:SSL:10m;**
        **ssl_session_timeout 10m;**

        ...
    }
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_alpn</strong> <code><i>protocol</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.21.4.

Specifies the list of supported [ALPN](https://datatracker.ietf.org/doc/html/rfc7301) protocols. One of the protocols must be [negotiated](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#var_ssl_alpn_protocol) if the client uses ALPN:

```nginx
map $ssl_alpn_protocol $proxy {
    h2                127.0.0.1:8001;
    http/1.1          127.0.0.1:8002;
}

server {
    listen      12346;
    proxy_pass  $proxy;
    ssl_alpn    h2 http/1.1;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with the certificate in the PEM format for the given virtual server. If intermediate certificates should be specified in addition to a primary certificate, they should be specified in the same file in the following order: the primary certificate comes first, then the intermediate certificates. A secret key in the PEM format may be placed in the same file.

Since version 1.11.0, this directive can be specified multiple times to load certificates of different types, for example, RSA and ECDSA:

```nginx
server {
    listen              12345 ssl;

    ssl_certificate     example.com.rsa.crt;
    ssl_certificate_key example.com.rsa.key;

    ssl_certificate     example.com.ecdsa.crt;
    ssl_certificate_key example.com.ecdsa.key;

    ...
}
```

> Only OpenSSL 1.0.2 or higher supports separate [certificate chains](https://nginx.org/en/docs/http/configuring_https_servers.html#chains) for different certificates. With older versions, only one certificate chain can be used.

Since version 1.15.9, variables can be used in the `*file*` name when using OpenSSL 1.0.2 or higher:

```nginx
ssl_certificate     $ssl_server_name.crt;
ssl_certificate_key $ssl_server_name.key;
```

Note that using variables implies that a certificate will be loaded for each SSL handshake, and this may have a negative impact on performance.

The value `data`:`*$variable*` can be specified instead of the `*file*` (1.15.10), which loads a certificate from a variable without using intermediate files. Note that inappropriate use of this syntax may have its security implications, such as writing secret key data to [error log](https://nginx.org/en/docs/ngx_core_module.html#error_log).

It should be kept in mind that due to the SSL/TLS protocol limitations, for maximum interoperability with clients that do not use [SNI](http://en.wikipedia.org/wiki/Server_Name_Indication), virtual servers with different certificates should listen on [different IP addresses](https://nginx.org/en/docs/http/configuring_https_servers.html#name_based_https_servers).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate_cache</strong> <code>off</code>;</code><br><code><strong>ssl_certificate_cache</strong> <code>max</code>=<code><i>N</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>valid</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_certificate_cache off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.4.

Defines a cache that stores [SSL certificates](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_certificate) and [secret keys](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_certificate_key) specified with [variables](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_certificate_key_variables).

The directive has the following parameters:

`max`

sets the maximum number of elements in the cache; on cache overflow the least recently used (LRU) elements are removed;

`inactive`

defines a time after which an element is removed from the cache if it has not been accessed during this time; by default, it is 10 seconds;

`valid`

defines a time during which an element in the cache is considered valid and can be reused; by default, it is 60 seconds. Certificates that exceed this time will be reloaded or revalidated;

`off`

disables the cache.

Example:

```nginx
ssl_certificate       $ssl_server_name.crt;
ssl_certificate_key   $ssl_server_name.key;
ssl_certificate_cache max=1000 inactive=20s valid=1m;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate_compression</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_certificate_compression off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.1.

Enables TLS 1.3 [compression](https://datatracker.ietf.org/doc/html/rfc8879) of server certificates.

> The directive is supported when using OpenSSL 3.2 or higher; the list of supported compression algorithms is provided by the library.

> The directive is supported when using BoringSSL; the list of supported compression algorithms includes `zlib` (1.29.3).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_certificate_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with the secret key in the PEM format for the given virtual server.

The value `engine`:`*name*`:`*id*` can be specified instead of the `*file*`, which loads a secret key with a specified `*id*` from the OpenSSL engine `*name*`.

The value `store`:`*scheme*`:`*id*` can be specified instead of the `*file*` (1.29.0), which is used to load a secret key with a specified `*id*` and OpenSSL provider registered URI `*scheme*`, such as [`pkcs11`](https://datatracker.ietf.org/doc/html/rfc7512).

The value `data`:`*$variable*` can be specified instead of the `*file*` (1.15.10), which loads a secret key from a variable without using intermediate files. Note that inappropriate use of this syntax may have its security implications, such as writing secret key data to [error log](https://nginx.org/en/docs/ngx_core_module.html#error_log).

Since version 1.15.9, variables can be used in the `*file*` name when using OpenSSL 1.0.2 or higher.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_ciphers HIGH:!aNULL:!MD5;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies the enabled ciphers. The ciphers are specified in the format understood by the OpenSSL library, for example:

```nginx
ssl_ciphers ALL:!aNULL:!EXPORT56:RC4+RSA:+HIGH:+MEDIUM:+LOW:+SSLv2:+EXP;
```

The full list can be viewed using the “`openssl ciphers`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_client_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.8.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_verify_client) client certificates and OCSP responses if [ssl\_stapling](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_stapling) is enabled.

The list of certificates will be sent to clients. If this is not desired, the [ssl\_trusted\_certificate](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_trusted_certificate) directive can be used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_conf_command</strong> <code><i>name</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.19.4.

Sets arbitrary OpenSSL configuration [commands](https://www.openssl.org/docs/man1.1.1/man3/SSL_CONF_cmd.html).

> The directive is supported when using OpenSSL 1.0.2 or higher.

Several `ssl_conf_command` directives can be specified on the same level:

```nginx
ssl_conf_command Options PrioritizeChaCha;
ssl_conf_command Ciphersuites TLS_CHACHA20_POLY1305_SHA256;
```

These directives are inherited from the previous configuration level if and only if there are no `ssl_conf_command` directives defined on the current level.

> Note that configuring OpenSSL directly might result in unexpected behavior.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.8.

Specifies a `*file*` with revoked certificates (CRL) in the PEM format used to [verify](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_verify_client) client certificates. When using intermediate certificates, their CRLs should be specified in the same file.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_dhparam</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with DH parameters for DHE ciphers.

By default no parameters are set, and therefore DHE ciphers will not be used.

> Prior to version 1.11.0, builtin parameters were used by default.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ecdh_curve</strong> <code><i>curve</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_ecdh_curve auto;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*curve*` for ECDHE ciphers.

When using OpenSSL 1.0.2 or higher, it is possible to specify multiple curves (1.11.0), for example:

```nginx
ssl_ecdh_curve prime256v1:secp384r1;
```

The special value `auto` (1.11.0) instructs nginx to use a list built into the OpenSSL library when using OpenSSL 1.0.2 or higher, or `prime256v1` with older versions.

> Prior to version 1.11.0, the `prime256v1` curve was used by default.

> When using OpenSSL 1.0.2 or higher, this directive sets the list of curves supported by the server. Thus, in order for ECDSA certificates to work, it is important to include the curves used in the certificates.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ech_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.4.

Specifies a `*file*` with encrypted ClientHello configuration (`ECHConfig`) in the [PEM](https://datatracker.ietf.org/doc/draft-farrell-tls-pemesni/) format used to enable TLS 1.3 [ECH](https://datatracker.ietf.org/doc/html/draft-ietf-tls-esni) in shared mode.

> The directive is supported when using OpenSSL 4.0 or higher.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_handshake_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_handshake_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a timeout for the SSL handshake to complete.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_key_log</strong> path;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables logging of client connection SSL keys and specifies the path to the key log file. Keys are logged in the [SSLKEYLOGFILE](https://datatracker.ietf.org/doc/html/draft-ietf-tls-keylogfile) format compatible with Wireshark.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ocsp</strong> <code>on</code> | <code>off</code> | <code>leaf</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_ocsp off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables OCSP validation of the client certificate chain. The `leaf` parameter enables validation of the client certificate only.

For the OCSP validation to work, the [ssl\_verify\_client](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_verify_client) directive should be set to `on` or `optional`.

To resolve the OCSP responder hostname, the [resolver](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver) directive should also be specified.

Example:

```nginx
ssl_verify_client on;
ssl_ocsp          on;
resolver          192.0.2.1;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ocsp_cache</strong> <code>off</code> | [<code>shared</code>:<code><i>name</i></code>:<code><i>size</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_ocsp_cache off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Sets `name` and `size` of the cache that stores client certificates status for OCSP validation. The cache is shared between all worker processes. A cache with the same name can be used in several virtual servers.

The `off` parameter prohibits the use of the cache.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_ocsp_responder</strong> <code><i>url</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Overrides the URL of the OCSP responder specified in the “[Authority Information Access](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.2.1)” certificate extension for [validation](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_ocsp) of client certificates.

Only “`http://`” OCSP responders are supported:

```nginx
ssl_ocsp_responder http://ocsp.example.com/;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_password_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with passphrases for [secret keys](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_certificate_key) where each passphrase is specified on a separate line. Passphrases are tried in turn when loading the key.

Example:

```nginx
stream {
    ssl_password_file /etc/keys/global.pass;
    ...

    server {
        listen 127.0.0.1:12345;
        ssl_certificate_key /etc/keys/first.key;
    }

    server {
        listen 127.0.0.1:12346;

        # named pipe can also be used instead of a file
        ssl_password_file /etc/keys/fifo;
        ssl_certificate_key /etc/keys/second.key;
    }
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_prefer_server_ciphers</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_prefer_server_ciphers off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies that server ciphers should be preferred over client ciphers when the SSLv3 and TLS protocols are used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_protocols</strong> [<code>SSLv2</code>] [<code>SSLv3</code>] [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_protocols TLSv1.2 TLSv1.3;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Enables the specified protocols.

If the directive is specified on the [server](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server) level, the value from the default server can be used. Details are provided in the “[Virtual server selection](https://nginx.org/en/docs/http/server_names.html#virtual_server_selection)” section.

> The `TLSv1.1` and `TLSv1.2` parameters work only when OpenSSL 1.0.1 or higher is used.

> The `TLSv1.3` parameter (1.13.0) works only when OpenSSL 1.1.1 or higher is used.

> The `TLSv1.3` parameter is used by default since 1.23.4.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_reject_handshake</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_reject_handshake off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.25.5.

If enabled, SSL handshakes in the [server](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server) block will be rejected.

For example, in the following configuration, SSL handshakes with server names other than `example.com` are rejected:

```nginx
server {
    listen               443 ssl default_server;
    ssl_reject_handshake on;
}

server {
    listen              443 ssl;
    server_name         example.com;
    ssl_certificate     example.com.crt;
    ssl_certificate_key example.com.key;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_cache</strong> <code>off</code> | <code>none</code> | [<code>builtin</code>[:<code><i>size</i></code>]] [<code>shared</code>:<code><i>name</i></code>:<code><i>size</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_session_cache none;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets the types and sizes of caches that store session parameters. A cache can be of any of the following types:

`off`

the use of a session cache is strictly prohibited: nginx explicitly tells a client that sessions may not be reused.

`none`

the use of a session cache is gently disallowed: nginx tells a client that sessions may be reused, but does not actually store session parameters in the cache.

`builtin`

a cache built in OpenSSL; used by one worker process only. The cache size is specified in sessions. If size is not given, it is equal to 20480 sessions. Use of the built-in cache can cause memory fragmentation.

`shared`

a cache shared between all worker processes. The cache size is specified in bytes; one megabyte can store about 4000 sessions. Each shared cache should have an arbitrary name. A cache with the same name can be used in several virtual servers. It is also used to automatically generate, store, and periodically rotate TLS session ticket keys (1.23.2) unless configured explicitly using the [ssl\_session\_ticket\_key](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_session_ticket_key) directive.

Both cache types can be used simultaneously, for example:

```nginx
ssl_session_cache builtin:1000 shared:SSL:10m;
```

but using only shared cache without the built-in cache should be more efficient.

If the directive is specified on the [server](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server) level, the value from the default server can be used. Details are provided in the “[Virtual server selection](https://nginx.org/en/docs/http/server_names.html#virtual_server_selection)” section.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_ticket_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets a `*file*` with the secret key used to encrypt and decrypt TLS session tickets. The directive is necessary if the same key has to be shared between multiple servers. By default, a randomly generated key is used.

If several keys are specified, only the first key is used to encrypt TLS session tickets. This allows configuring key rotation, for example:

```nginx
ssl_session_ticket_key current.key;
ssl_session_ticket_key previous.key;
```

The `*file*` must contain 80 or 48 bytes of random data and can be created using the following command:

```
openssl rand 80 > ticket.key
```

Depending on the file size either AES256 (for 80-byte keys, 1.11.8) or AES128 (for 48-byte keys) is used for encryption.

If the directive is specified on the [server](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server) level, the value from the default server can be used. Details are provided in the “[Virtual server selection](https://nginx.org/en/docs/http/server_names.html#virtual_server_selection)” section.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_tickets</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_session_tickets on;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Enables or disables session resumption through [TLS session tickets](https://datatracker.ietf.org/doc/html/rfc5077).

If the directive is specified on the [server](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server) level, the value from the default server can be used. Details are provided in the “[Virtual server selection](https://nginx.org/en/docs/http/server_names.html#virtual_server_selection)” section.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_session_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_session_timeout 5m;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a time during which a client may reuse the session parameters.

If the directive is specified on the [server](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server) level, the value from the default server can be used. Details are provided in the “[Virtual server selection](https://nginx.org/en/docs/http/server_names.html#virtual_server_selection)” section.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_stapling</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_stapling off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables or disables [stapling of OCSP responses](https://datatracker.ietf.org/doc/html/rfc6066#section-8) by the server. Example:

```nginx
ssl_stapling on;
resolver 192.0.2.1;
```

For the OCSP stapling to work, the certificate of the server certificate issuer should be known. If the [ssl\_certificate](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_certificate) file does not contain intermediate certificates, the certificate of the server certificate issuer should be present in the [ssl\_trusted\_certificate](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_trusted_certificate) file.

For a resolution of the OCSP responder hostname, the [resolver](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver) directive should also be specified.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_stapling_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

When set, the stapled OCSP response will be taken from the specified `*file*` instead of querying the OCSP responder specified in the server certificate.

The file should be in the DER format as produced by the “`openssl ocsp`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_stapling_responder</strong> <code><i>url</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Overrides the URL of the OCSP responder specified in the “[Authority Information Access](https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.2.1)” certificate extension.

Only “`http://`” OCSP responders are supported:

```nginx
ssl_stapling_responder http://ocsp.example.com/;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_stapling_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_stapling_verify off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables or disables verification of OCSP responses by the server.

For verification to work, the certificate of the server certificate issuer, the root certificate, and all intermediate certificates should be configured as trusted using the [ssl\_trusted\_certificate](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_trusted_certificate) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.8.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_verify_client) client certificates and OCSP responses if [ssl\_stapling](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_stapling) is enabled.

In contrast to the certificate set by [ssl\_client\_certificate](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_client_certificate), the list of these certificates will not be sent to clients.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_verify_client</strong> <code>on</code> | <code>off</code> | <code>optional</code> | <code>optional_no_ca</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_verify_client off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.8.

Enables verification of client certificates. The verification result is stored in the [$ssl\_client\_verify](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#var_ssl_client_verify) variable. If an error has occurred during the client certificate verification or a client has not presented the required certificate, the connection is closed.

The `optional` parameter requests the client certificate and verifies it if the certificate is present.

The `optional_no_ca` parameter requests the client certificate but does not require it to be signed by a trusted CA certificate. This is intended for the use in cases when a service that is external to nginx performs the actual certificate verification. The contents of the certificate is accessible through the [$ssl\_client\_cert](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#var_ssl_client_cert) variable.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_verify_depth 1;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.8.

Sets the verification depth in the client certificates chain.

#### Embedded Variables

The `ngx_stream_ssl_module` module supports variables since 1.11.2.

`$ssl_alpn_protocol`

returns the protocol selected by ALPN during the SSL handshake, or an empty string otherwise (1.21.4);

`$ssl_cipher`

returns the name of the cipher used for an established SSL connection;

`$ssl_ciphers`

returns the list of ciphers supported by the client (1.11.7). Known ciphers are listed by names, unknown are shown in hexadecimal, for example:

```
AES128-SHA:AES256-SHA:0x00ff
```

> The variable is fully supported only when using OpenSSL version 1.0.2 or higher. With older versions, the variable is available only for new sessions and lists only known ciphers.

`$ssl_client_cert`

returns the client certificate in the PEM format for an established SSL connection, with each line except the first prepended with the tab character (1.11.8);

`$ssl_client_fingerprint`

returns the SHA1 fingerprint of the client certificate for an established SSL connection (1.11.8);

`$ssl_client_i_dn`

returns the “issuer DN” string of the client certificate for an established SSL connection according to [RFC 2253](https://datatracker.ietf.org/doc/html/rfc2253) (1.11.8);

`$ssl_client_raw_cert`

returns the client certificate in the PEM format for an established SSL connection (1.11.8);

`$ssl_client_s_dn`

returns the “subject DN” string of the client certificate for an established SSL connection according to [RFC 2253](https://datatracker.ietf.org/doc/html/rfc2253) (1.11.8);

`$ssl_client_serial`

returns the serial number of the client certificate for an established SSL connection (1.11.8);

`$ssl_client_sigalg`

returns the [signature algorithm](https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-16) for the client certificate for an established SSL connection (1.29.3).

> The variable is supported only when using OpenSSL version 3.5 or higher. With older versions, the variable value will be an empty string.

> The variable is available only for new sessions.

`$ssl_client_v_end`

returns the end date of the client certificate (1.11.8);

`$ssl_client_v_remain`

returns the number of days until the client certificate expires (1.11.8);

`$ssl_client_v_start`

returns the start date of the client certificate (1.11.8);

`$ssl_client_verify`

returns the result of client certificate verification (1.11.8): “`SUCCESS`”, “`FAILED:``*reason*`”, and “`NONE`” if a certificate was not present;

`$ssl_curve`

returns the negotiated curve used for SSL handshake key exchange process (1.21.5). Known curves are listed by names, unknown are shown in hexadecimal, for example:

```
prime256v1
```

> The variable is supported only when using OpenSSL version 3.0 or higher. With older versions, the variable value will be an empty string.

`$ssl_curves`

returns the list of curves supported by the client (1.11.7). Known curves are listed by names, unknown are shown in hexadecimal, for example:

```
0x001d:prime256v1:secp521r1:secp384r1
```

> The variable is supported only when using OpenSSL version 1.0.2 or higher. With older versions, the variable value will be an empty string.

> The variable is available only for new sessions.

`$ssl_ech_outer_server_name`

returns the public server name requested through [SNI](http://en.wikipedia.org/wiki/Server_Name_Indication) if TLS 1.3 [ECH](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_ech_file) was accepted, otherwise “” (1.29.4);

`$ssl_ech_status`

returns the result of TLS 1.3 [ECH](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html#ssl_ech_file) processing: “`FAILED`”, “`BACKEND`”, “`GREASE`”, “`SUCCESS`”, or “`NOT_TRIED`” (1.29.4);

> The variable is supported only when using OpenSSL version 4.0 or higher. With older versions, the variable value will be an empty string.

`$ssl_protocol`

returns the protocol of an established SSL connection;

`$ssl_server_name`

returns the server name requested through [SNI](http://en.wikipedia.org/wiki/Server_Name_Indication);

`$ssl_session_id`

returns the session identifier of an established SSL connection;

`$ssl_session_reused`

returns “`r`” if an SSL session was reused, or “`.`” otherwise.

`$ssl_sigalg`

returns the [signature algorithm](https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-16) for the server certificate for an established SSL connection (1.29.3).

> The variable is supported only when using OpenSSL version 3.5 or higher. With older versions, the variable value will be an empty string.

> The variable is available only for new sessions.

`$ssl_sigalgs`

returns the list of [signature algorithms](https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-16) supported by the client (1.31.2). Known values are listed by names, unknown are shown in hexadecimal, for example:

```
0xfe00:rsa_pkcs1_sha256:rsa_pss_rsae_sha256:ecdsa_secp256r1_sha256
```

> The variable is fully supported only when using OpenSSL version 4.0 or higher. For OpenSSL versions 1.0.2+, the values are always shown in hexadecimal. With older versions, the variable value will be an empty string.

> The variable is available only for new sessions.