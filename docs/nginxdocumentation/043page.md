# page

> Source: https://nginx.org/en/docs/http/ngx_http_grpc_module.html

---

## 目錄

- [Module ngx\_http\_grpc\_module](#module-ngxhttpgrpcmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_grpc\_module

The `ngx_http_grpc_module` module allows passing requests to a gRPC server (1.13.10). The module requires the [ngx\_http\_v2\_module](https://nginx.org/en/docs/http/ngx_http_v2_module.html) module.

#### Example Configuration

```nginx
server {
    listen 9000;

    http2 on;

    location / {
        grpc_pass 127.0.0.1:9000;
    }
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_allow_upstream</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

Defines conditions under which access to a gRPC server is allowed or [denied](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#denied). If all string parameters are not empty and not equal to “0” then the access is allowed. The conditions are evaluated each time before a connection to a gRPC server is established. Parameter values can contain variables:

```nginx
geo $upstream_last_addr $allow {
    volatile;
    10.10.0.0/24        1;
}

server {
    listen 127.0.0.1:8080;
    http2 on;

    location / {
        grpc_pass           localhost:9000;
        grpc_allow_upstream $allow;
        ...
    }
}
```

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_bind</strong> <code><i>address</i></code> [<code>transparent </code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Makes outgoing connections to a gRPC server originate from the specified local IP address with an optional port. Parameter value can contain variables. The special value `off` cancels the effect of the `grpc_bind` directive inherited from the previous configuration level, which allows the system to auto-assign the local IP address and port.

The `transparent` parameter allows outgoing connections to a gRPC server originate from a non-local IP address, for example, from a real IP address of a client:

```nginx
grpc_bind $remote_addr transparent;
```

In order for this parameter to work, it is usually necessary to run nginx worker processes with the [superuser](https://nginx.org/en/docs/ngx_core_module.html#user) privileges. On Linux it is not required as if the `transparent` parameter is specified, worker processes inherit the `CAP_NET_RAW` capability from the master process. It is also necessary to configure kernel routing table to intercept network traffic from the gRPC server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

When enabled, makes the [bind](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_bind) operation at each connection attempt.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the `*size*` of the buffer used for reading the response received from the gRPC server. The first part of the response usually contains a small header; if it exceeds the buffer size, the response is considered [invalid](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#invalid_header). The response is passed to the client synchronously, as soon as it is received. By default, the buffer size is equal to one memory page. This is either 4K or 8K, depending on a platform. It can be made smaller, however.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a timeout for establishing a connection with a gRPC server. It should be noted that this timeout cannot usually exceed 75 seconds.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_hide_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

By default, nginx does not pass the header fields “Date”, “Server”, and “X-Accel-...” from the response of a gRPC server to a client. The `grpc_hide_header` directive sets additional fields that will not be passed. If, on the contrary, the passing of fields needs to be permitted, the [grpc\_pass\_header](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_pass_header) directive can be used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ignore_headers</strong> <code><i>field</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Disables processing of certain response header fields from the gRPC server. The following fields can be ignored: “X-Accel-Redirect” and “X-Accel-Charset”.

If not disabled, processing of these header fields has the following effect:

-   “X-Accel-Redirect” performs an [internal redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#internal) to the specified URI;
-   “X-Accel-Charset” sets the desired [charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset) of a response.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_intercept_errors</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_intercept_errors off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Determines whether gRPC server responses with codes greater than or equal to 300 should be passed to a client or be intercepted and redirected to nginx for processing with the [error\_page](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_next_upstream</strong> <code>error</code> | <code>timeout</code> | <code>denied</code> | <code>invalid_header</code> | <code>http_500</code> | <code>http_502</code> | <code>http_503</code> | <code>http_504</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>non_idempotent</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_next_upstream error timeout;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies in which cases a request should be passed to the next server:

`error`

an error occurred while establishing a connection with the server, passing a request to it, or reading the response header;

`timeout`

a timeout has occurred while establishing a connection with the server, passing a request to it, or reading the response header;

`denied`

the server [denied](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_allow_upstream) the connection (1.29.3);

> This parameter is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

`invalid_header`

a server returned an empty or invalid response;

`http_500`

a server returned a response with the code 500;

`http_502`

a server returned a response with the code 502;

`http_503`

a server returned a response with the code 503;

`http_504`

a server returned a response with the code 504;

`http_403`

a server returned a response with the code 403;

`http_404`

a server returned a response with the code 404;

`http_429`

a server returned a response with the code 429;

`non_idempotent`

normally, requests with a [non-idempotent](https://datatracker.ietf.org/doc/html/rfc7231#section-4.2.2) method (`POST`, `LOCK`, `PATCH`) are not passed to the next server if a request has been sent to an upstream server; enabling this option explicitly allows retrying such requests;

`off`

disables passing a request to the next server.

One should bear in mind that passing a request to the next server is only possible if nothing has been sent to a client yet. That is, if an error or timeout occurs in the middle of the transferring of a response, fixing this is impossible.

The directive also defines what is considered an [unsuccessful attempt](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails) of communication with a server. The cases of `error`, `timeout`, `denied` and `invalid_header` are always considered unsuccessful attempts, even if they are not specified in the directive. The cases of `http_500`, `http_502`, `http_503`, `http_504`, and `http_429` are considered unsuccessful attempts only if they are specified in the directive. The cases of `http_403` and `http_404` are never considered unsuccessful attempts.

Passing a request to the next server can be limited by [the number of tries](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_next_upstream_tries) and by [time](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_next_upstream_timeout).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Limits the time during which a request can be passed to the [next server](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_next_upstream). The `0` value turns off this limitation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Limits the number of possible tries for passing a request to the [next server](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_next_upstream). The `0` value turns off this limitation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_pass</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

Sets the gRPC server address. The address can be specified as a domain name or IP address, and a port:

```nginx
grpc_pass localhost:9000;
```

or as a UNIX-domain socket path:

```nginx
grpc_pass unix:/tmp/grpc.socket;
```

Alternatively, the “`grpc://`” scheme can be used:

```nginx
grpc_pass grpc://127.0.0.1:9000;
```

To use gRPC over SSL, the “`grpcs://`” scheme should be used:

```nginx
grpc_pass grpcs://127.0.0.1:443;
```

If a domain name resolves to several addresses, all of them will be used in a round-robin fashion. In addition, an address can be specified as a [server group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html).

Parameter value can contain variables (1.17.8). In this case, if an address is specified as a domain name, the name is searched among the described [server groups](https://nginx.org/en/docs/http/ngx_http_upstream_module.html), and, if not found, is determined using a [resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver).

> Trailer fields received from an upstream server are passed to a client as is, without interpretation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_pass_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Permits passing [otherwise disabled](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_hide_header) header fields from a gRPC server to a client.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_read_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_read_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a timeout for reading a response from the gRPC server. The timeout is set only between two successive read operations, not for the transmission of the whole response. If the gRPC server does not transmit anything within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_request_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_request_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

Enables or disables creation of a separate request instance for each gRPC server. By default, a single request is used for all gRPC servers. If enabled, a separate request instance is created, allowing per-server request customization. For example, the server-specific “Host” request header field can be set:

```nginx
grpc_request_dynamic on;
grpc_set_header      Host $upstream_last_server_name;
```

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_send_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_send_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets a timeout for transmitting a request to the gRPC server. The timeout is set only between two successive write operations, not for the transmission of the whole request. If the gRPC server does not receive anything within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_set_header</strong> <code><i>field</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_set_header Content-Length $content_length;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Allows redefining or appending fields to the request header [passed](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_pass_request_headers) to the gRPC server. The `*value*` can contain text, variables, and their combinations. These directives are inherited from the previous configuration level if and only if there are no `grpc_set_header` directives defined on the current level.

If the value of a header field is an empty string then this field will not be passed to a gRPC server:

```nginx
grpc_set_header Accept-Encoding "";
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.15.6.

Configures the “TCP keepalive” behavior for outgoing connections to a gRPC server. By default, the operating system’s settings are in effect for the socket. If the directive is set to the value “`on`”, the `SO_KEEPALIVE` socket option is turned on for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_socket_rcvbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.3.

Sets the receive buffer size (the `SO_RCVBUF` option) for outgoing connections to a gRPC server. The special value `0` cancels the effect of the `grpc_socket_rcvbuf` directive inherited from the previous configuration level, which allows keeping the operating system’s settings in effect for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_socket_sndbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.3.

Sets the send buffer size (the `SO_SNDBUF` option) for outgoing connections to a gRPC server. The special value `0` cancels the effect of the `grpc_socket_sndbuf` directive inherited from the previous configuration level, which allows keeping the operating system’s settings in effect for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies a `*file*` with the certificate in the PEM format used for authentication to a gRPC SSL server.

Since version 1.21.0, variables can be used in the `*file*` name.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_certificate_cache</strong> <code>off</code>;</code><br><code><strong>grpc_ssl_certificate_cache</strong> <code>max</code>=<code><i>N</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>valid</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_certificate_cache off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.4.

Defines a cache that stores [SSL certificates](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_certificate) and [secret keys](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_certificate_key) specified with [variables](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_certificate_key_variables).

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
grpc_ssl_certificate       $grpc_ssl_server_name.crt;
grpc_ssl_certificate_key   $grpc_ssl_server_name.key;
grpc_ssl_certificate_cache max=1000 inactive=20s valid=1m;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_certificate_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies a `*file*` with the secret key in the PEM format used for authentication to a gRPC SSL server.

The value `engine`:`*name*`:`*id*` can be specified instead of the `*file*`, which loads a secret key with a specified `*id*` from the OpenSSL engine `*name*`.

The value `store`:`*scheme*`:`*id*` can be specified instead of the `*file*` (1.29.0), which is used to load a secret key with a specified `*id*` and OpenSSL provider registered URI `*scheme*`, such as [`pkcs11`](https://datatracker.ietf.org/doc/html/rfc7512).

Since version 1.21.0, variables can be used in the `*file*` name.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_ciphers DEFAULT;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies the enabled ciphers for requests to a gRPC SSL server. The ciphers are specified in the format understood by the OpenSSL library.

The full list can be viewed using the “`openssl ciphers`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_conf_command</strong> <code><i>name</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.19.4.

Sets arbitrary OpenSSL configuration [commands](https://www.openssl.org/docs/man1.1.1/man3/SSL_CONF_cmd.html) when establishing a connection with the gRPC SSL server.

> The directive is supported when using OpenSSL 1.0.2 or higher.

Several `grpc_ssl_conf_command` directives can be specified on the same level. These directives are inherited from the previous configuration level if and only if there are no `grpc_ssl_conf_command` directives defined on the current level.

> Note that configuring OpenSSL directly might result in unexpected behavior.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies a `*file*` with revoked certificates (CRL) in the PEM format used to [verify](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_verify) the certificate of the gRPC SSL server. When using intermediate certificates, their CRLs should be specified in the same file.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_key_log</strong> path;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables logging of gRPC SSL server connection SSL keys and specifies the path to the key log file. Keys are logged in the [SSLKEYLOGFILE](https://datatracker.ietf.org/doc/html/draft-ietf-tls-keylogfile) format compatible with Wireshark.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_name host from grpc_pass;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Allows overriding the server name used to [verify](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_verify) the certificate of the gRPC SSL server and to be [passed through SNI](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_server_name) when establishing a connection with the gRPC SSL server.

By default, the host part from [grpc\_pass](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_pass) is used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_password_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies a `*file*` with passphrases for [secret keys](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_certificate_key) where each passphrase is specified on a separate line. Passphrases are tried in turn when loading the key.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_protocols</strong> [<code>SSLv2</code>] [<code>SSLv3</code>] [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_protocols TLSv1.2 TLSv1.3;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Enables the specified protocols for requests to a gRPC SSL server.

> The `TLSv1.3` parameter is used by default since 1.23.4.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_server_name</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_server_name off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Enables or disables passing of the server name through [TLS Server Name Indication extension](http://en.wikipedia.org/wiki/Server_Name_Indication) (SNI, RFC 6066) when establishing a connection with the gRPC SSL server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_session_reuse</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_session_reuse on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Determines whether SSL sessions can be reused when working with the gRPC server. If the errors “`digest check failed`” appear in the logs, try disabling session reuse.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/http/ngx_http_grpc_module.html#grpc_ssl_verify) the certificate of the gRPC SSL server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_verify off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Enables or disables verification of the gRPC SSL server certificate.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>grpc_ssl_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>grpc_ssl_verify_depth 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the verification depth in the gRPC SSL server certificates chain.