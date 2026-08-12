# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_core_module.html

---

## 目錄

- [Module ngx\_stream\_core\_module](#module-ngxstreamcoremodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_stream\_core\_module

The `ngx_stream_core_module` module is available since version 1.9.0. This module is not built by default, it should be enabled with the `--with-stream` configuration parameter.

#### Example Configuration

> worker\_processes auto;
> 
> error\_log /var/log/nginx/error.log info;
> 
> events {
>     worker\_connections  1024;
> }
> 
> stream {
>     upstream backend {
>         hash $remote\_addr consistent;
> 
>         server backend1.example.com:12345 weight=5;
>         server 127.0.0.1:12345            max\_fails=3 fail\_timeout=30s;
>         server unix:/tmp/backend3;
>     }
> 
>     upstream dns {
>        server 192.168.0.1:53535;
>        server dns.example.com:53;
>     }
> 
>     server {
>         listen 12345;
>         proxy\_connect\_timeout 1s;
>         proxy\_timeout 3s;
>         proxy\_pass backend;
>     }
> 
>     server {
>         listen 127.0.0.1:53 udp reuseport;
>         proxy\_timeout 20s;
>         proxy\_pass dns;
>     }
> 
>     server {
>         listen \[::1\]:12345;
>         proxy\_pass unix:/tmp/stream.socket;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>listen</strong> <code><i>address</i></code>:<code><i>port</i></code> [<code>default_server</code>] [<code>ssl</code>] [<code>udp</code>] [<code>proxy_protocol</code>] [<code>setfib</code>=<code><i>number</i></code>] [<code>fastopen</code>=<code><i>number</i></code>] [<code>backlog</code>=<code><i>number</i></code>] [<code>rcvbuf</code>=<code><i>size</i></code>] [<code>sndbuf</code>=<code><i>size</i></code>] [<code>accept_filter</code>=<code><i>filter</i></code>] [<code>deferred</code>] [<code>bind</code>] [<code>ipv6only</code>=<code>on</code>|<code>off</code>] [<code>reuseport</code>] [<code>multipath</code>] [<code>so_keepalive</code>=<code>on</code>|<code>off</code>|[<code><i>keepidle</i></code>]:[<code><i>keepintvl</i></code>]:[<code><i>keepcnt</i></code>]];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

Sets the `*address*` and `*port*` for the socket on which the server will accept connections. It is possible to specify just the port. The address can also be a hostname, for example:

> listen 127.0.0.1:12345;
> listen \*:12345;
> listen 12345;     # same as \*:12345
> listen localhost:12345;

IPv6 addresses are specified in square brackets:

> listen \[::1\]:12345;
> listen \[::\]:12345;

UNIX-domain sockets are specified with the “`unix:`” prefix:

> listen unix:/var/run/nginx.sock;

Port ranges (1.15.10) are specified with the first and last port separated by a hyphen:

> listen 127.0.0.1:12345-12399;
> listen 12345-12399;

The `default_server` parameter, if present, will cause the server to become the default server for the specified `*address*`:`*port*` pair (1.25.5). If none of the directives have the `default_server` parameter then the first server with the `*address*`:`*port*` pair will be the default server for this pair.

The `ssl` parameter allows specifying that all connections accepted on this port should work in SSL mode.

The `udp` parameter configures a listening socket for working with datagrams (1.9.13). In order to handle packets from the same address and port in the same session, the [`reuseport`](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#reuseport) parameter should also be specified.

The `proxy_protocol` parameter (1.11.4) allows specifying that all connections accepted on this port should use the [PROXY protocol](http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt).

> The PROXY protocol version 2 is supported since version 1.13.11.

The `listen` directive can have several additional parameters specific to socket-related system calls. These parameters can be specified in any `listen` directive, but only once for a given `*address*`:`*port*` pair.

`setfib`\=`*number*`

this parameter (1.25.5) sets the associated routing table, FIB (the `SO_SETFIB` option) for the listening socket. This currently works only on FreeBSD.

`fastopen`\=`*number*`

enables “[TCP Fast Open](http://en.wikipedia.org/wiki/TCP_Fast_Open)” for the listening socket (1.21.0) and [limits](https://datatracker.ietf.org/doc/html/rfc7413#section-5.1) the maximum length for the queue of connections that have not yet completed the three-way handshake.

> Do not enable this feature unless the server can handle receiving the [same SYN packet with data](https://datatracker.ietf.org/doc/html/rfc7413#section-6.1) more than once.

`backlog`\=`*number*`

sets the `backlog` parameter in the `listen()` call that limits the maximum length for the queue of pending connections (1.9.2). By default, `backlog` is set to -1 on FreeBSD, DragonFly BSD, and macOS, and to 511 on other platforms.

`rcvbuf`\=`*size*`

sets the receive buffer size (the `SO_RCVBUF` option) for the listening socket (1.11.13).

`sndbuf`\=`*size*`

sets the send buffer size (the `SO_SNDBUF` option) for the listening socket (1.11.13).

`accept_filter`\=`*filter*`

sets the name of accept filter (the `SO_ACCEPTFILTER` option) for the listening socket that filters incoming connections before passing them to `accept()` (1.25.5). This works only on FreeBSD and NetBSD 5.0+. Possible values are [dataready](http://man.freebsd.org/accf_data) and [httpready](http://man.freebsd.org/accf_http).

`deferred`

instructs to use a deferred `accept()` (the `TCP_DEFER_ACCEPT` socket option) on Linux (1.25.5).

`bind`

this parameter instructs to make a separate `bind()` call for a given address:port pair. The fact is that if there are several `listen` directives with the same port but different addresses, and one of the `listen` directives listens on all addresses for the given port (`*:``*port*`), nginx will `bind()` only to `*:``*port*`. It should be noted that the `getsockname()` system call will be made in this case to determine the address that accepted the connection. If the `setfib`, `fastopen`, `backlog`, `rcvbuf`, `sndbuf`, `accept_filter`, `deferred`, `ipv6only`, `reuseport`, `multipath`, or `so_keepalive` parameters are used then for a given `*address*`:`*port*` pair a separate `bind()` call will always be made.

`ipv6only`\=`on`|`off`

this parameter determines (via the `IPV6_V6ONLY` socket option) whether an IPv6 socket listening on a wildcard address `[::]` will accept only IPv6 connections or both IPv6 and IPv4 connections. This parameter is turned on by default. It can only be set once on start.

`reuseport`

this parameter (1.9.1) instructs to create an individual listening socket for each worker process (using the `SO_REUSEPORT` socket option on Linux 3.9+ and DragonFly BSD, or `SO_REUSEPORT_LB` on FreeBSD 12+), allowing a kernel to distribute incoming connections between worker processes. This currently works only on Linux 3.9+, DragonFly BSD, and FreeBSD 12+ (1.15.1).

> Inappropriate use of this option may have its security [implications](http://man7.org/linux/man-pages/man7/socket.7.html).

`multipath`

this parameter (1.29.7) configures the [Multipath TCP](https://datatracker.ietf.org/doc/html/rfc8684) protocol (`IPPROTO_MPTCP`) for the listening socket. This currently works only on Linux 5.6+.

> Adding or removing this parameter will also enable the `SO_REUSEPORT` socket option, which may have its security [implications](http://man7.org/linux/man-pages/man7/socket.7.html).

`so_keepalive`\=`on`|`off`|\[`*keepidle*`\]:\[`*keepintvl*`\]:\[`*keepcnt*`\]

this parameter configures the “TCP keepalive” behavior for the listening socket. If this parameter is omitted then the operating system’s settings will be in effect for the socket. If it is set to the value “`on`”, the `SO_KEEPALIVE` option is turned on for the socket. If it is set to the value “`off`”, the `SO_KEEPALIVE` option is turned off for the socket. Some operating systems support setting of TCP keepalive parameters on a per-socket basis using the `TCP_KEEPIDLE`, `TCP_KEEPINTVL`, and `TCP_KEEPCNT` socket options. On such systems (currently, Linux, NetBSD, Dragonfly, FreeBSD, and macOS), they can be configured using the `*keepidle*`, `*keepintvl*`, and `*keepcnt*` parameters. One or two parameters may be omitted, in which case the system default setting for the corresponding socket option will be in effect. For example,

> so\_keepalive=30m::10

will set the idle timeout (`TCP_KEEPIDLE`) to 30 minutes, leave the probe interval (`TCP_KEEPINTVL`) at its system default, and set the probes count (`TCP_KEEPCNT`) to 10 probes.

> Before version 1.25.5, different servers must listen on different `*address*`:`*port*` pairs.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>preread_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>preread_buffer_size 16k;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.5.

Specifies a `*size*` of the [preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase) buffer.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>preread_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>preread_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.5.

Specifies a `*timeout*` of the [preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase) phase.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_protocol_timeout</strong> <code><i>timeout</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>proxy_protocol_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.4.

Specifies a `*timeout*` for reading the PROXY protocol header to complete. If no entire header is transmitted within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver</strong> <code><i>address</i></code> ... [<code>valid</code>=<code><i>time</i></code>] [<code>ipv4</code>=<code>on</code>|<code>off</code>] [<code>ipv6</code>=<code>on</code>|<code>off</code>] [<code>status_zone</code>=<code><i>zone</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.3.

Configures name servers used to resolve names of upstream servers into addresses, for example:

> resolver 127.0.0.1 \[::1\]:5353;

The address can be specified as a domain name or IP address, with an optional port. If port is not specified, the port 53 is used. Name servers are queried in a round-robin fashion.

By default, nginx will look up both IPv4 and IPv6 addresses while resolving. If looking up of IPv4 or IPv6 addresses is not desired, the `ipv4=off` (1.23.1) or the `ipv6=off` parameter can be specified.

By default, nginx caches answers using the TTL value of a response. The optional `valid` parameter allows overriding it:

> resolver 127.0.0.1 \[::1\]:5353 valid=30s;

> To prevent DNS spoofing, it is recommended configuring DNS servers in a properly secured trusted local network.

The optional `status_zone` parameter (1.17.1) enables [collection](https://nginx.org/en/docs/http/ngx_http_api_module.html#resolvers_) of DNS server statistics of requests and responses in the specified `*zone*`. The parameter is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

> Before version 1.11.3, this directive was available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>resolver_timeout 30s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.3.

Sets a timeout for name resolution, for example:

> resolver\_timeout 5s;

> Before version 1.11.3, this directive was available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server</strong> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

Sets the configuration for a virtual server. There is no clear separation between IP-based (based on the IP address) and name-based (based on the [TLS Server Name Indication extension](http://en.wikipedia.org/wiki/Server_Name_Indication) (SNI, RFC 6066)) (1.25.5) virtual servers. Instead, the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directives describe all addresses and ports that should accept connections for the server, and the [server\_name](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server_name) directive lists all server names.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server_name</strong> <code><i>name</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>server_name "";</pre></td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.25.5.

Sets names of a virtual server, for example:

> server {
>     server\_name example.com www.example.com;
> }

The first name becomes the primary server name.

Server names can include an asterisk (“`*`”) replacing the first or last part of a name:

> server {
>     server\_name example.com \*.example.com www.example.\*;
> }

Such names are called wildcard names.

The first two of the names mentioned above can be combined in one:

> server {
>     server\_name .example.com;
> }

It is also possible to use regular expressions in server names, preceding the name with a tilde (“`~`”):

> server {
>     server\_name www.example.com ~^www\\d+\\.example\\.com$;
> }

Regular expressions can contain captures that can later be used in other directives:

> server {
>     server\_name ~^(www\\.)?(.+)$;
> 
>     proxy\_pass www.$2:12345;
> }

Named captures in regular expressions create variables that can later be used in other directives:

> server {
>     server\_name ~^(www\\.)?(?<domain>.+)$;
> 
>     proxy\_pass www.$domain:12345;
> }

If the directive’s parameter is set to “`$hostname`”, the machine’s hostname is inserted.

The search is performed in the following order of priority and terminates on the first matching variant:

1.  the exact name
2.  the longest wildcard name starting with an asterisk, e.g. “`*.example.com`”
3.  the longest wildcard name ending with an asterisk, e.g. “`mail.*`”
4.  the first matching regular expression (in order of appearance in the configuration file)

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server_names_hash_bucket_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>server_names_hash_bucket_size 32|64|128;</pre></td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

This directive appeared in version 1.25.5.

Sets the bucket size for the server names hash tables. The default value depends on the size of the processor’s cache line. The details of setting up hash tables are provided in a separate [document](https://nginx.org/en/docs/hash.html).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>server_names_hash_max_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>server_names_hash_max_size 512;</pre></td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

This directive appeared in version 1.25.5.

Sets the maximum `*size*` of the server names hash tables. The details of setting up hash tables are provided in a separate [document](https://nginx.org/en/docs/hash.html).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>stream</strong> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

Provides the configuration file context in which the stream server directives are specified.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>tcp_nodelay</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>tcp_nodelay on;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 1.9.4.

Enables or disables the use of the `TCP_NODELAY` option. The option is enabled for both client and proxied server connections.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>variables_hash_bucket_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>variables_hash_bucket_size 64;</pre></td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.2.

Sets the bucket size for the variables hash table. The details of setting up hash tables are provided in a separate [document](https://nginx.org/en/docs/hash.html).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>variables_hash_max_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>variables_hash_max_size 1024;</pre></td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.2.

Sets the maximum `*size*` of the variables hash table. The details of setting up hash tables are provided in a separate [document](https://nginx.org/en/docs/hash.html).

#### Embedded Variables

The `ngx_stream_core_module` module supports variables since 1.11.2.

`$binary_remote_addr`

client address in a binary form, value’s length is always 4 bytes for IPv4 addresses or 16 bytes for IPv6 addresses

`$bytes_received`

number of bytes received from a client (1.11.4)

`$bytes_sent`

number of bytes sent to a client

`$connection`

connection serial number

`$hostname`

host name

`$msec`

current time in seconds with the milliseconds resolution

`$nginx_version`

nginx version

`$pid`

PID of the worker process

`$protocol`

protocol used to communicate with the client: `TCP` or `UDP` (1.11.4)

`$proxy_protocol_addr`

client address from the PROXY protocol header (1.11.4)

The PROXY protocol must be previously enabled by setting the `proxy_protocol` parameter in the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directive.

`$proxy_protocol_port`

client port from the PROXY protocol header (1.11.4)

The PROXY protocol must be previously enabled by setting the `proxy_protocol` parameter in the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directive.

`$proxy_protocol_server_addr`

server address from the PROXY protocol header (1.17.6)

The PROXY protocol must be previously enabled by setting the `proxy_protocol` parameter in the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directive.

`$proxy_protocol_server_port`

server port from the PROXY protocol header (1.17.6)

The PROXY protocol must be previously enabled by setting the `proxy_protocol` parameter in the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directive.

`$proxy_protocol_tlv_``*name*`

TLV from the PROXY Protocol header (1.23.2). The `name` can be a TLV type name or its numeric value. In the latter case, the value is hexadecimal and should be prefixed with `0x`:

> $proxy\_protocol\_tlv\_alpn
> $proxy\_protocol\_tlv\_0x01

SSL TLVs can also be accessed by TLV type name or its numeric value, both prefixed by `ssl_`:

> $proxy\_protocol\_tlv\_ssl\_version
> $proxy\_protocol\_tlv\_ssl\_0x21

The following TLV type names are supported:

-   `alpn` (`0x01`) - upper layer protocol used over the connection
-   `authority` (`0x02`) - host name value passed by the client
-   `unique_id` (`0x05`) - unique connection id
-   `netns` (`0x30`) - name of the namespace
-   `ssl` (`0x20`) - binary SSL TLV structure

The following SSL TLV type names are supported:

-   `ssl_version` (`0x21`) - SSL version used in client connection
-   `ssl_cn` (`0x22`) - SSL certificate Common Name
-   `ssl_cipher` (`0x23`) - name of the used cipher
-   `ssl_sig_alg` (`0x24`) - algorithm used to sign the certificate
-   `ssl_key_alg` (`0x25`) - public-key algorithm

Also, the following special SSL TLV type name is supported:

-   `ssl_verify` - client SSL certificate verification result, zero if the client presented a certificate and it was successfully verified, and non-zero otherwise

The PROXY protocol must be previously enabled by setting the `proxy_protocol` parameter in the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directive.

`$remote_addr`

client address

`$remote_port`

client port

`$server_addr`

an address of the server which accepted a connection

Computing a value of this variable usually requires one system call. To avoid a system call, the [listen](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#listen) directives must specify addresses and use the `bind` parameter.

`$server_port`

port of the server which accepted a connection

`$session_time`

session duration in seconds with a milliseconds resolution (1.11.4);

`$status`

session status (1.11.4), can be one of the following:

`200`

session completed successfully

`400`

client data could not be parsed, for example, the [PROXY protocol](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#proxy_protocol) header

`403`

access forbidden, for example, when access is limited for [certain client addresses](https://nginx.org/en/docs/stream/ngx_stream_access_module.html)

`500`

internal server error

`502`

bad gateway, for example, if an upstream server could not be selected or reached.

`503`

service unavailable, for example, when access is limited by the [number of connections](https://nginx.org/en/docs/stream/ngx_stream_limit_conn_module.html)

`$time_iso8601`

local time in the ISO 8601 standard format

`$time_iso8601_ms`

local time in the ISO 8601 standard format with a millisecond resolution (1.29.8)

> This variable is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

`$time_local`

local time in the Common Log Format