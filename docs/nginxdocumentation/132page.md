# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html

---

## 目錄

- [Module ngx\_stream\_proxy\_module](#module-ngxstreamproxymodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_proxy\_module

The `ngx_stream_proxy_module` module (1.9.0) allows proxying data streams over TCP, UDP (1.9.13), and UNIX-domain sockets.

#### Example Configuration

> server {
>     listen 127.0.0.1:12345;
>     proxy\_pass 127.0.0.1:8080;
> }
> 
> server {
>     listen 12345;
>     proxy\_connect\_timeout 1s;
>     proxy\_timeout 1m;
>     proxy\_pass example.com:12345;
> }
> 
> server {
>     listen 53 udp reuseport;
>     proxy\_timeout 20s;
>     proxy\_pass dns.example.com:53;
> }
> 
> server {
>     listen \[::1\]:12345;
>     proxy\_pass unix:/tmp/stream.socket;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_bind</strong> <code><i>address</i></code> [<code>transparent</code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.2.

Makes outgoing connections to a proxied server originate from the specified local IP `*address*`. Parameter value can contain variables (1.11.2). The special value `off` cancels the effect of the `proxy_bind` directive inherited from the previous configuration level, which allows the system to auto-assign the local IP address.

The `transparent` parameter (1.11.0) allows outgoing connections to a proxied server originate from a non-local IP address, for example, from a real IP address of a client:

> proxy\_bind $remote\_addr transparent;

In order for this parameter to work, it is usually necessary to run nginx worker processes with the [superuser](https://nginx.org/en/docs/ngx_core_module.html#user) privileges. On Linux it is not required (1.13.8) as if the `transparent` parameter is specified, worker processes inherit the `CAP_NET_RAW` capability from the master process. It is also necessary to configure kernel routing table to intercept network traffic from the proxied server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

When enabled, makes the [bind](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_bind) operation at each connection attempt.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_buffer_size 16k;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.4.

Sets the `*size*` of the buffer used for reading data from the proxied server. Also sets the `*size*` of the buffer used for reading data from the client.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Defines a timeout for establishing a connection with a proxied server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_download_rate</strong> <code><i>rate</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_download_rate 0;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.3.

Limits the speed of reading the data from the proxied server. The `*rate*` is specified in bytes per second. The zero value disables rate limiting. The limit is set per a connection, so if nginx simultaneously opens two connections to the proxied server, the overall rate will be twice as much as the specified limit.

Parameter value can contain variables (1.17.0). It may be useful in cases where rate should be limited depending on a certain condition:

> map $slow $rate {
>     1     4k;
>     2     8k;
> }
> 
> proxy\_download\_rate $rate;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_half_close</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_half_close off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.21.4.

Enables or disables closing each direction of a TCP connection independently (“TCP half-close”). If enabled, proxying over TCP will be kept until both sides close the connection.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_next_upstream</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_next_upstream on;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

When a connection to the proxied server cannot be established, determines whether a client connection will be passed to the next server.

Passing a connection to the next server can be limited by [the number of tries](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_next_upstream_tries) and by [time](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_next_upstream_timeout).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Limits the time allowed to pass a connection to the [next server](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_next_upstream). The `0` value turns off this limitation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Limits the number of possible tries for passing a connection to the [next server](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_next_upstream). The `0` value turns off this limitation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_pass</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

Sets the address of a proxied server. The address can be specified as a domain name or IP address, and a port:

> proxy\_pass localhost:12345;

or as a UNIX-domain socket path:

> proxy\_pass unix:/tmp/stream.socket;

If a domain name resolves to several addresses, all of them will be used in a round-robin fashion. In addition, an address can be specified as a [server group](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html).

The address can also be specified using variables (1.11.3):

> proxy\_pass $upstream;

In this case, the server name is searched among the described [server groups](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html), and, if not found, is determined using a [resolver](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_protocol</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_protocol off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.2.

Enables the [PROXY protocol](http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt) for connections to a proxied server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_requests</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_requests 0;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.15.7.

Sets the number of client datagrams at which binding between a client and existing UDP stream session is dropped. After receiving the specified number of datagrams, next datagram from the same client starts a new session. The session terminates when all client datagrams are transmitted to a proxied server and the expected number of [responses](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_responses) is received, or when it reaches a [timeout](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_timeout).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_responses</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.13.

Sets the number of datagrams expected from the proxied server in response to a client datagram if the [UDP](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#udp) protocol is used. The number serves as a hint for session termination. By default, the number of datagrams is not limited.

If zero value is specified, no response is expected. However, if a response is received and the session is still not finished, the response will be handled.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_session_drop</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_session_drop off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.15.8.

Enables terminating all sessions to a proxied server after it was removed from the group or marked as permanently unavailable. This can occur because of [re-resolve](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#resolver) or with the API [`DELETE`](https://nginx.org/en/docs/http/ngx_http_api_module.html#deleteStreamUpstreamServer) command. A server can be marked as permanently unavailable if it is considered [unhealthy](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#health_check) or with the API [`PATCH`](https://nginx.org/en/docs/http/ngx_http_api_module.html#patchStreamUpstreamServer) command. Each session is terminated when the next read or write event is processed for the client or proxied server.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.15.6.

Configures the “TCP keepalive” behavior for outgoing connections to a proxied server. By default, the operating system’s settings are in effect for the socket. If the directive is set to the value “`on`”, the `SO_KEEPALIVE` socket option is turned on for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_socket_rcvbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.3.

Sets the receive buffer size (the `SO_RCVBUF` option) for outgoing connections to a proxied server. The special value `0` cancels the effect of the `proxy_socket_rcvbuf` directive inherited from the previous configuration level, which allows keeping the operating system’s settings in effect for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_socket_sndbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.3.

Sets the send buffer size (the `SO_SNDBUF` option) for outgoing connections to a proxied server. The special value `0` cancels the effect of the `proxy_socket_sndbuf` directive inherited from the previous configuration level, which allows keeping the operating system’s settings in effect for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Enables the SSL/TLS protocol for connections to a proxied server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_alpn</strong> <code><i>protocol</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.0.

Specifies the list of protocols to advertise via the [ALPN](https://datatracker.ietf.org/doc/html/rfc7301) extension when establishing a connection with the proxied server. For example:

> proxy\_ssl\_alpn h2 http/1.1;

Parameter value can contain variables:

> proxy\_ssl\_alpn $ssl\_alpn\_protocol;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with the certificate in the PEM format used for authentication to a proxied server.

Since version 1.21.0, variables can be used in the `*file*` name.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_certificate_cache</strong> <code>off</code>;</code><br><code><strong>proxy_ssl_certificate_cache</strong> <code>max</code>=<code><i>N</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>valid</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_certificate_cache off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.4.

Defines a cache that stores [SSL certificates](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_certificate) and [secret keys](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_certificate_key) specified with [variables](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_certificate_key_variables).

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

> proxy\_ssl\_certificate       $proxy\_ssl\_server\_name.crt;
> proxy\_ssl\_certificate\_key   $proxy\_ssl\_server\_name.key;
> proxy\_ssl\_certificate\_cache max=1000 inactive=20s valid=1m;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_certificate_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

The value `store`:`*scheme*`:`*id*` can be specified instead of the `*file*` (1.29.0), which is used to load a secret key with a specified `*id*` and OpenSSL provider registered URI `*scheme*`, such as [`pkcs11`](https://datatracker.ietf.org/doc/html/rfc7512).

Specifies a `*file*` with the secret key in the PEM format used for authentication to a proxied server.

Since version 1.21.0, variables can be used in the `*file*` name.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_ciphers DEFAULT;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies the enabled ciphers for connections to a proxied server. The ciphers are specified in the format understood by the OpenSSL library.

The full list can be viewed using the “`openssl ciphers`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_conf_command</strong> <code><i>name</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.19.4.

Sets arbitrary OpenSSL configuration [commands](https://www.openssl.org/docs/man1.1.1/man3/SSL_CONF_cmd.html) when establishing a connection with the proxied server.

> The directive is supported when using OpenSSL 1.0.2 or higher.

Several `proxy_ssl_conf_command` directives can be specified on the same level. These directives are inherited from the previous configuration level if and only if there are no `proxy_ssl_conf_command` directives defined on the current level.

> Note that configuring OpenSSL directly might result in unexpected behavior.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with revoked certificates (CRL) in the PEM format used to [verify](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_verify) the certificate of the proxied server. When using intermediate certificates, their CRLs should be specified in the same file.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_key_log</strong> path;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables logging of proxied server connection SSL keys and specifies the path to the key log file. Keys are logged in the [SSLKEYLOGFILE](https://datatracker.ietf.org/doc/html/draft-ietf-tls-keylogfile) format compatible with Wireshark.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_name host from proxy_pass;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Allows overriding the server name used to [verify](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_verify) the certificate of the proxied server and to be [passed through SNI](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_server_name) when establishing a connection with the proxied server. The server name can also be specified using variables (1.11.3).

By default, the host part of the [proxy\_pass](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_pass) address is used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_password_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with passphrases for [secret keys](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_certificate_key) where each passphrase is specified on a separate line. Passphrases are tried in turn when loading the key.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_protocols</strong> [<code>SSLv2</code>] [<code>SSLv3</code>] [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_protocols TLSv1.2 TLSv1.3;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Enables the specified protocols for connections to a proxied server.

> The `TLSv1.3` parameter is used by default since 1.23.4.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_server_name</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_server_name off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Enables or disables passing of the server name through [TLS Server Name Indication extension](http://en.wikipedia.org/wiki/Server_Name_Indication) (SNI, RFC 6066) when establishing a connection with the proxied server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_session_reuse</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_session_reuse on;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Determines whether SSL sessions can be reused when working with the proxied server. If the errors “`digest check failed`” appear in the logs, try disabling session reuse.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_ssl_verify) the certificate of the proxied server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_verify off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Enables or disables verification of the proxied server certificate.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_ssl_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_ssl_verify_depth 1;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets the verification depth in the proxied server certificates chain.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_timeout 10m;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets the `*timeout*` between two successive read or write operations on client or proxied server connections. If no data is transmitted within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_upload_rate</strong> <code><i>rate</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_upload_rate 0;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.3.

Limits the speed of reading the data from the client. The `*rate*` is specified in bytes per second. The zero value disables rate limiting. The limit is set per a connection, so if the client simultaneously opens two connections, the overall rate will be twice as much as the specified limit.

Parameter value can contain variables (1.17.0). It may be useful in cases where rate should be limited depending on a certain condition:

> map $slow $rate {
>     1     4k;
>     2     8k;
> }
> 
> proxy\_upload\_rate $rate;