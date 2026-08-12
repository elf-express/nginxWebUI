# page

> Source: https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html

---

## 目錄

- [Module ngx\_http\_uwsgi\_module](#module-ngxhttpuwsgimodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_uwsgi\_module

The `ngx_http_uwsgi_module` module allows passing requests to a uwsgi server.

#### Example Configuration

> location / {
>     include    uwsgi\_params;
>     uwsgi\_pass localhost:9000;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_allow_upstream</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

Defines conditions under which access to a uwsgi server is allowed or [denied](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#denied). If all string parameters are not empty and not equal to “0” then the access is allowed. The conditions are evaluated each time before a connection to a uwsgi server is established. Parameter values can contain variables:

> geo $upstream\_last\_addr $allow {
>     volatile;
>     10.10.0.0/24        1;
> }
> 
> server {
>     listen 127.0.0.1:8080;
> 
>     location / {
>         uwsgi\_pass           localhost:9000;
>         uwsgi\_allow\_upstream $allow;
>         ...
>     }
> }

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_bind</strong> <code><i>address</i></code> [<code>transparent</code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Makes outgoing connections to a uwsgi server originate from the specified local IP address with an optional port (1.11.2). Parameter value can contain variables (1.3.12). The special value `off` (1.3.12) cancels the effect of the `uwsgi_bind` directive inherited from the previous configuration level, which allows the system to auto-assign the local IP address and port.

The `transparent` parameter (1.11.0) allows outgoing connections to a uwsgi server originate from a non-local IP address, for example, from a real IP address of a client:

> uwsgi\_bind $remote\_addr transparent;

In order for this parameter to work, it is usually necessary to run nginx worker processes with the [superuser](https://nginx.org/en/docs/ngx_core_module.html#user) privileges. On Linux it is not required (1.13.8) as if the `transparent` parameter is specified, worker processes inherit the `CAP_NET_RAW` capability from the master process. It is also necessary to configure kernel routing table to intercept network traffic from the uwsgi server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

When enabled, makes the [bind](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_bind) operation at each connection attempt.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the `*size*` of the buffer used for reading the first part of the response received from the uwsgi server. This part usually contains a small response header; if it exceeds the buffer size, the response is considered [invalid](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#invalid_header). By default, the buffer size is equal to one memory page. This is either 4K or 8K, depending on a platform. It can be made smaller, however.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_buffering</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_buffering on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Enables or disables buffering of responses from the uwsgi server.

When buffering is enabled, nginx receives a response from the uwsgi server as soon as possible, saving it into the buffers set by the [uwsgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffer_size) and [uwsgi\_buffers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffers) directives. If the whole response does not fit into memory, a part of it can be saved to a [temporary file](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_temp_path) on the disk. Writing to temporary files is controlled by the [uwsgi\_max\_temp\_file\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_max_temp_file_size) and [uwsgi\_temp\_file\_write\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_temp_file_write_size) directives.

When buffering is disabled, the response is passed to a client synchronously, immediately as it is received. nginx will not try to read the whole response from the uwsgi server. The maximum size of the data that nginx can receive from the server at a time is set by the [uwsgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffer_size) directive.

Buffering can also be enabled or disabled by passing “`yes`” or “`no`” in the “X-Accel-Buffering” response header field. This capability can be disabled using the [uwsgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ignore_headers) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_buffers 8 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the `*number*` and `*size*` of the buffers used for reading a response from the uwsgi server, for a single connection. By default, the buffer size is equal to one memory page. This is either 4K or 8K, depending on a platform.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_busy_buffers_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_busy_buffers_size 8k|16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

When [buffering](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffering) of responses from the uwsgi server is enabled, limits the total `*size*` of buffers that can be busy sending a response to the client while the response is not yet fully read. In the meantime, the rest of the buffers can be used for reading the response and, if needed, buffering part of the response to a temporary file. By default, `*size*` is limited by the size of two buffers set by the [uwsgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffer_size) and [uwsgi\_buffers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffers) directives.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache</strong> <code><i>zone</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a shared memory zone used for caching. The same zone can be used in several places. Parameter value can contain variables (1.7.9). The `off` parameter disables caching inherited from the previous configuration level.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_background_update</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_background_update off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.10.

Allows starting a background subrequest to update an expired cache item, while a stale cached response is returned to the client. Note that it is necessary to [allow](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_use_stale_updating) the usage of a stale cached response when it is being updated.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_bypass</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines conditions under which the response will not be taken from a cache. If at least one value of the string parameters is not empty and is not equal to “0” then the response will not be taken from the cache:

> uwsgi\_cache\_bypass $cookie\_nocache $arg\_nocache$arg\_comment;
> uwsgi\_cache\_bypass $http\_pragma    $http\_authorization;

Can be used along with the [uwsgi\_no\_cache](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_no_cache) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_key</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a key for caching, for example

> uwsgi\_cache\_key localhost:9000$request\_uri;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_lock</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_lock off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.1.12.

When enabled, only one request at a time will be allowed to populate a new cache element identified according to the [uwsgi\_cache\_key](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_key) directive by passing a request to a uwsgi server. Other requests of the same cache element will either wait for a response to appear in the cache or the cache lock for this element to be released, up to the time set by the [uwsgi\_cache\_lock\_timeout](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_lock_timeout) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_lock_age</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_lock_age 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.8.

If the last request passed to the uwsgi server for populating a new cache element has not completed for the specified `*time*`, one more request may be passed to the uwsgi server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_lock_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_lock_timeout 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.1.12.

Sets a timeout for [uwsgi\_cache\_lock](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_lock). When the `*time*` expires, the request will be passed to the uwsgi server, however, the response will not be cached.

> Before 1.7.8, the response could be cached.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_max_range_offset</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.11.6.

Sets an offset in bytes for byte-range requests. If the range is beyond the offset, the range request will be passed to the uwsgi server and the response will not be cached.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_methods</strong> <code>GET</code> | <code>HEAD</code> | <code>POST</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_methods GET HEAD;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

If the client request method is listed in this directive then the response will be cached. “`GET`” and “`HEAD`” methods are always added to the list, though it is recommended to specify them explicitly. See also the [uwsgi\_no\_cache](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_no_cache) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_min_uses</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_min_uses 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the `*number*` of requests after which the response will be cached.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_path</strong> <code><i>path</i></code> [<code>levels</code>=<code><i>levels</i></code>] [<code>use_temp_path</code>=<code>on</code>|<code>off</code>] <code>keys_zone</code>=<code><i>name</i></code>:<code><i>size</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>max_size</code>=<code><i>size</i></code>] [<code>min_free</code>=<code><i>size</i></code>] [<code>manager_files</code>=<code><i>number</i></code>] [<code>manager_sleep</code>=<code><i>time</i></code>] [<code>manager_threshold</code>=<code><i>time</i></code>] [<code>loader_files</code>=<code><i>number</i></code>] [<code>loader_sleep</code>=<code><i>time</i></code>] [<code>loader_threshold</code>=<code><i>time</i></code>] [<code>purger</code>=<code>on</code>|<code>off</code>] [<code>purger_files</code>=<code><i>number</i></code>] [<code>purger_sleep</code>=<code><i>time</i></code>] [<code>purger_threshold</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

Sets the path and other parameters of a cache. Cache data are stored in files. The file name in a cache is a result of applying the MD5 function to the [cache key](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_key). The `levels` parameter defines hierarchy levels of a cache: from 1 to 3, each level accepts values 1 or 2. For example, in the following configuration

> uwsgi\_cache\_path /data/nginx/cache levels=1:2 keys\_zone=one:10m;

file names in a cache will look like this:

> /data/nginx/cache/**c**/**29**/b7f54b2df7773722d382f4809d650**29c**

A cached response is first written to a temporary file, and then the file is renamed. Starting from version 0.8.9, temporary files and the cache can be put on different file systems. However, be aware that in this case a file is copied across two file systems instead of the cheap renaming operation. It is thus recommended that for any given location both cache and a directory holding temporary files are put on the same file system. A directory for temporary files is set based on the `use_temp_path` parameter (1.7.10). If this parameter is omitted or set to the value `on`, the directory set by the [uwsgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_temp_path) directive for the given location will be used. If the value is set to `off`, temporary files will be put directly in the cache directory.

In addition, all active keys and information about data are stored in a shared memory zone, whose `*name*` and `*size*` are configured by the `keys_zone` parameter. One megabyte zone can store about 8 thousand keys.

> As part of [commercial subscription](https://www.f5.com/products/nginx), the shared memory zone also stores extended cache [information](https://nginx.org/en/docs/http/ngx_http_api_module.html#http_caches_), thus, it is required to specify a larger zone size for the same number of keys. For example, one megabyte zone can store about 4 thousand keys.

Cached data that are not accessed during the time specified by the `inactive` parameter get removed from the cache regardless of their freshness. By default, `inactive` is set to 10 minutes.

The special “cache manager” process monitors the maximum cache size set by the `max_size` parameter, and the minimum amount of free space set by the `min_free` (1.19.1) parameter on the file system with cache. When the size is exceeded or there is not enough free space, it removes the least recently used data. The data is removed in iterations configured by `manager_files`, `manager_threshold`, and `manager_sleep` parameters (1.11.5). During one iteration no more than `manager_files` items are deleted (by default, 100). The duration of one iteration is limited by the `manager_threshold` parameter (by default, 200 milliseconds). Between iterations, a pause configured by the `manager_sleep` parameter (by default, 50 milliseconds) is made.

A minute after the start the special “cache loader” process is activated. It loads information about previously cached data stored on file system into a cache zone. The loading is also done in iterations. During one iteration no more than `loader_files` items are loaded (by default, 100). Besides, the duration of one iteration is limited by the `loader_threshold` parameter (by default, 200 milliseconds). Between iterations, a pause configured by the `loader_sleep` parameter (by default, 50 milliseconds) is made.

Additionally, the following parameters are available as part of our [commercial subscription](https://www.f5.com/products/nginx):

`purger`\=`on`|`off`

Instructs whether cache entries that match a [wildcard key](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_purge) will be removed from the disk by the cache purger (1.7.12). Setting the parameter to `on` (default is `off`) will activate the “cache purger” process that permanently iterates through all cache entries and deletes the entries that match the wildcard key.

`purger_files`\=`*number*`

Sets the number of items that will be scanned during one iteration (1.7.12). By default, `purger_files` is set to 10.

`purger_threshold`\=`*number*`

Sets the duration of one iteration (1.7.12). By default, `purger_threshold` is set to 50 milliseconds.

`purger_sleep`\=`*number*`

Sets a pause between iterations (1.7.12). By default, `purger_sleep` is set to 50 milliseconds.

> In versions 1.7.3, 1.7.7, and 1.11.10 cache header format has been changed. Previously cached responses will be considered invalid after upgrading to a newer nginx version.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_purge</strong> string ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.7.

Defines conditions under which the request will be considered a cache purge request. If at least one value of the string parameters is not empty and is not equal to “0” then the cache entry with a corresponding [cache key](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_key) is removed. The result of successful operation is indicated by returning the 204 (No Content) response.

If the [cache key](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_key) of a purge request ends with an asterisk (“`*`”), all cache entries matching the wildcard key will be removed from the cache. However, these entries will remain on the disk until they are deleted for either [inactivity](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_path), or processed by the [cache purger](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#purger) (1.7.12), or a client attempts to access them.

Example configuration:

> uwsgi\_cache\_path /data/nginx/cache keys\_zone=cache\_zone:10m;
> 
> map $request\_method $purge\_method {
>     PURGE   1;
>     default 0;
> }
> 
> server {
>     ...
>     location / {
>         uwsgi\_pass        backend;
>         uwsgi\_cache       cache\_zone;
>         uwsgi\_cache\_key   $uri;
>         uwsgi\_cache\_purge $purge\_method;
>     }
> }

> This functionality is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_revalidate</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_revalidate off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.7.

Enables revalidation of expired cache items using conditional requests with the “If-Modified-Since” and “If-None-Match” header fields.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_use_stale</strong> <code>error</code> | <code>timeout</code> | <code>invalid_header</code> | <code>updating</code> | <code>http_500</code> | <code>http_503</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_cache_use_stale off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Determines in which cases a stale cached response can be used when an error occurs during communication with the uwsgi server. The directive’s parameters match the parameters of the [uwsgi\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream) directive.

The `error` parameter also permits using a stale cached response if a uwsgi server to process a request cannot be selected.

Additionally, the `updating` parameter permits using a stale cached response if it is currently being updated. This allows minimizing the number of accesses to uwsgi servers when updating cached data.

Using a stale cached response can also be enabled directly in the response header for a specified number of seconds after the response became stale (1.11.10). This has lower priority than using the directive parameters.

-   The “[stale-while-revalidate](https://datatracker.ietf.org/doc/html/rfc5861#section-3)” extension of the “Cache-Control” header field permits using a stale cached response if it is currently being updated.
-   The “[stale-if-error](https://datatracker.ietf.org/doc/html/rfc5861#section-4)” extension of the “Cache-Control” header field permits using a stale cached response in case of an error.

To minimize the number of accesses to uwsgi servers when populating a new cache element, the [uwsgi\_cache\_lock](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_lock) directive can be used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_cache_valid</strong> [<code><i>code</i></code> ...] <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets caching time for different response codes. For example, the following directives

> uwsgi\_cache\_valid 200 302 10m;
> uwsgi\_cache\_valid 404      1m;

set 10 minutes of caching for responses with codes 200 and 302 and 1 minute for responses with code 404.

If only caching `*time*` is specified

> uwsgi\_cache\_valid 5m;

then only 200, 301, and 302 responses are cached.

In addition, the `any` parameter can be specified to cache any responses:

> uwsgi\_cache\_valid 200 302 10m;
> uwsgi\_cache\_valid 301      1h;
> uwsgi\_cache\_valid any      1m;

Parameters of caching can also be set directly in the response header. This has higher priority than setting of caching time using the directive.

-   The “X-Accel-Expires” header field sets caching time of a response in seconds. The zero value disables caching for a response. If the value starts with the `@` prefix, it sets an absolute time in seconds since Epoch, up to which the response may be cached.
-   If the header does not include the “X-Accel-Expires” field, parameters of caching may be set in the header fields “Expires” or “Cache-Control”.
-   If the header includes the “Set-Cookie” field, such a response will not be cached.
-   If the header includes the “Vary” field with the special value “`*`”, such a response will not be cached (1.7.7). If the header includes the “Vary” field with another value, such a response will be cached taking into account the corresponding request header fields (1.7.7).

Processing of one or more of these response header fields can be disabled using the [uwsgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ignore_headers) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a timeout for establishing a connection with a uwsgi server. It should be noted that this timeout cannot usually exceed 75 seconds.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_force_ranges</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_force_ranges off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.7.

Enables byte-range support for both cached and uncached responses from the uwsgi server regardless of the “Accept-Ranges” field in these responses.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_hide_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

By default, nginx does not pass the header fields “Status” and “X-Accel-...” from the response of a uwsgi server to a client. The `uwsgi_hide_header` directive sets additional fields that will not be passed. If, on the contrary, the passing of fields needs to be permitted, the [uwsgi\_pass\_header](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_pass_header) directive can be used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ignore_client_abort</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ignore_client_abort off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Determines whether the connection with a uwsgi server should be closed when a client closes the connection without waiting for a response.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ignore_headers</strong> <code><i>field</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Disables processing of certain response header fields from the uwsgi server. The following fields can be ignored: “X-Accel-Redirect”, “X-Accel-Expires”, “X-Accel-Limit-Rate” (1.1.6), “X-Accel-Buffering” (1.1.6), “X-Accel-Charset” (1.1.6), “Expires”, “Cache-Control”, “Set-Cookie” (0.8.44), and “Vary” (1.7.7).

If not disabled, processing of these header fields has the following effect:

-   “X-Accel-Expires”, “Expires”, “Cache-Control”, “Set-Cookie”, and “Vary” set the parameters of response [caching](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_valid);
-   “X-Accel-Redirect” performs an [internal redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#internal) to the specified URI;
-   “X-Accel-Limit-Rate” sets the [rate limit](https://nginx.org/en/docs/http/ngx_http_core_module.html#limit_rate) for transmission of a response to a client;
-   “X-Accel-Buffering” enables or disables [buffering](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffering) of a response;
-   “X-Accel-Charset” sets the desired [charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset) of a response.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_intercept_errors</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_intercept_errors off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Determines whether a uwsgi server responses with codes greater than or equal to 300 should be passed to a client or be intercepted and redirected to nginx for processing with the [error\_page](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_limit_rate</strong> <code><i>rate</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_limit_rate 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.7.

Limits the speed of reading the response from the uwsgi server. The `*rate*` is specified in bytes per second. The zero value disables rate limiting. The limit is set per a request, and so if nginx simultaneously opens two connections to the uwsgi server, the overall rate will be twice as much as the specified limit. The limitation works only if [buffering](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffering) of responses from the uwsgi server is enabled. Parameter value can contain variables (1.27.0).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_max_temp_file_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_max_temp_file_size 1024m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

When [buffering](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffering) of responses from the uwsgi server is enabled, and the whole response does not fit into the buffers set by the [uwsgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffer_size) and [uwsgi\_buffers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffers) directives, a part of the response can be saved to a temporary file. This directive sets the maximum `*size*` of the temporary file. The size of data written to the temporary file at a time is set by the [uwsgi\_temp\_file\_write\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_temp_file_write_size) directive.

The zero value disables buffering of responses to temporary files.

> This restriction does not apply to responses that will be [cached](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache) or [stored](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_store) on disk.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_modifier1</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_modifier1 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the value of the `modifier1` field in the [uwsgi packet header](http://uwsgi-docs.readthedocs.org/en/latest/Protocol.html#uwsgi-packet-header).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_modifier2</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_modifier2 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets the value of the `modifier2` field in the [uwsgi packet header](http://uwsgi-docs.readthedocs.org/en/latest/Protocol.html#uwsgi-packet-header).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_next_upstream</strong> <code>error</code> | <code>timeout</code> | <code>denied</code> | <code>invalid_header</code> | <code>http_500</code> | <code>http_503</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>non_idempotent</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_next_upstream error timeout;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Specifies in which cases a request should be passed to the next server:

`error`

an error occurred while establishing a connection with the server, passing a request to it, or reading the response header;

`timeout`

a timeout has occurred while establishing a connection with the server, passing a request to it, or reading the response header;

`denied`

the server [denied](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_allow_upstream) the connection (1.29.3);

> This parameter is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

`invalid_header`

a server returned an empty or invalid response;

`http_500`

a server returned a response with the code 500;

`http_503`

a server returned a response with the code 503;

`http_403`

a server returned a response with the code 403;

`http_404`

a server returned a response with the code 404;

`http_429`

a server returned a response with the code 429 (1.11.13);

`non_idempotent`

normally, requests with a [non-idempotent](https://datatracker.ietf.org/doc/html/rfc7231#section-4.2.2) method (`POST`, `LOCK`, `PATCH`) are not passed to the next server if a request has been sent to an upstream server (1.9.13); enabling this option explicitly allows retrying such requests;

`off`

disables passing a request to the next server.

One should bear in mind that passing a request to the next server is only possible if nothing has been sent to a client yet. That is, if an error or timeout occurs in the middle of the transferring of a response, fixing this is impossible.

The directive also defines what is considered an [unsuccessful attempt](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails) of communication with a server. The cases of `error`, `timeout`, `denied` and `invalid_header` are always considered unsuccessful attempts, even if they are not specified in the directive. The cases of `http_500`, `http_503`, and `http_429` are considered unsuccessful attempts only if they are specified in the directive. The cases of `http_403` and `http_404` are never considered unsuccessful attempts.

Passing a request to the next server can be limited by [the number of tries](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream_tries) and by [time](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream_timeout).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.5.

Limits the time during which a request can be passed to the [next server](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream). The `0` value turns off this limitation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.5.

Limits the number of possible tries for passing a request to the [next server](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream). The `0` value turns off this limitation.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_no_cache</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines conditions under which the response will not be saved to a cache. If at least one value of the string parameters is not empty and is not equal to “0” then the response will not be saved:

> uwsgi\_no\_cache $cookie\_nocache $arg\_nocache$arg\_comment;
> uwsgi\_no\_cache $http\_pragma    $http\_authorization;

Can be used along with the [uwsgi\_cache\_bypass](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_bypass) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_param</strong> <code><i>parameter</i></code> <code><i>value</i></code> [<code>if_not_empty</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_param HTTP_HOST $host$is_request_port$request_port;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets a `*parameter*` that should be passed to the uwsgi server. The `*value*` can contain text, variables, and their combination. These directives are inherited from the previous configuration level if and only if there are no `uwsgi_param` directives defined on the current level.

Standard [CGI environment variables](https://datatracker.ietf.org/doc/html/rfc3875#section-4.1) should be provided as uwsgi headers, see the `uwsgi_params` file provided in the distribution:

> location / {
>     include uwsgi\_params;
>     ...
> }

If the directive is specified with `if_not_empty` (1.1.11) then such a parameter will be passed to the server only if its value is not empty:

> uwsgi\_param HTTPS $https if\_not\_empty;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_pass</strong> [<code><i>protocol</i></code>://]<code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

Sets the protocol and address of a uwsgi server. As a `*protocol*`, “`uwsgi`” or “`suwsgi`” (secured uwsgi, uwsgi over SSL) can be specified. The address can be specified as a domain name or IP address, and a port:

> uwsgi\_pass localhost:9000;
> uwsgi\_pass uwsgi://localhost:9000;
> uwsgi\_pass suwsgi://\[2001:db8::1\]:9090;

or as a UNIX-domain socket path:

> uwsgi\_pass unix:/tmp/uwsgi.socket;

If a domain name resolves to several addresses, all of them will be used in a round-robin fashion. In addition, an address can be specified as a [server group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html).

Parameter value can contain variables. In this case, if an address is specified as a domain name, the name is searched among the described [server groups](https://nginx.org/en/docs/http/ngx_http_upstream_module.html), and, if not found, is determined using a [resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver).

> Secured uwsgi protocol is supported since version 1.5.8.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_pass_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Permits passing [otherwise disabled](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_hide_header) header fields from a uwsgi server to a client.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_pass_request_body</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_pass_request_body on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Indicates whether the original request body is passed to the uwsgi server. See also the [uwsgi\_pass\_request\_headers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_pass_request_headers) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_pass_request_headers</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_pass_request_headers on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Indicates whether the header fields of the original request are passed to the uwsgi server. See also the [uwsgi\_pass\_request\_body](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_pass_request_body) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_read_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_read_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a timeout for reading a response from the uwsgi server. The timeout is set only between two successive read operations, not for the transmission of the whole response. If the uwsgi server does not transmit anything within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_request_buffering</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_request_buffering on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.11.

Enables or disables buffering of a client request body.

When buffering is enabled, the entire request body is [read](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_buffer_size) from the client before sending the request to a uwsgi server.

When buffering is disabled, the request body is sent to the uwsgi server immediately as it is received. In this case, the request cannot be passed to the [next server](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_next_upstream) if nginx already started sending the request body.

When HTTP/1.1 chunked transfer encoding is used to send the original request body, the request body will be buffered regardless of the directive value.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_request_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_request_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.29.3.

Enables or disables creation of a separate request instance for each uwsgi server. By default, a single request is used for all uwsgi servers. If enabled, a separate request instance is created, allowing per-server request customization.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_send_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_send_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets a timeout for transmitting a request to the uwsgi server. The timeout is set only between two successive write operations, not for the transmission of the whole request. If the uwsgi server does not receive anything within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.15.6.

Configures the “TCP keepalive” behavior for outgoing connections to a uwsgi server. By default, the operating system’s settings are in effect for the socket. If the directive is set to the value “`on`”, the `SO_KEEPALIVE` socket option is turned on for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_socket_rcvbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.3.

Sets the receive buffer size (the `SO_RCVBUF` option) for outgoing connections to a uwsgi server. The special value `0` cancels the effect of the `uwsgi_socket_rcvbuf` directive inherited from the previous configuration level, which allows keeping the operating system’s settings in effect for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_socket_sndbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.31.3.

Sets the send buffer size (the `SO_SNDBUF` option) for outgoing connections to a uwsgi server. The special value `0` cancels the effect of the `uwsgi_socket_sndbuf` directive inherited from the previous configuration level, which allows keeping the operating system’s settings in effect for the socket.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.8.

Specifies a `*file*` with the certificate in the PEM format used for authentication to a secured uwsgi server.

Since version 1.21.0, variables can be used in the `*file*` name.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_certificate_cache</strong> <code>off</code>;</code><br><code><strong>uwsgi_ssl_certificate_cache</strong> <code>max</code>=<code><i>N</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>valid</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_certificate_cache off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.4.

Defines a cache that stores [SSL certificates](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_certificate) and [secret keys](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_certificate_key) specified with [variables](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_certificate_key_variables).

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

> uwsgi\_ssl\_certificate       $uwsgi\_ssl\_server\_name.crt;
> uwsgi\_ssl\_certificate\_key   $uwsgi\_ssl\_server\_name.key;
> uwsgi\_ssl\_certificate\_cache max=1000 inactive=20s valid=1m;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_certificate_key</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.8.

Specifies a `*file*` with the secret key in the PEM format used for authentication to a secured uwsgi server.

The value `engine`:`*name*`:`*id*` can be specified instead of the `*file*` (1.7.9), which loads a secret key with a specified `*id*` from the OpenSSL engine `*name*`.

The value `store`:`*scheme*`:`*id*` can be specified instead of the `*file*` (1.29.0), which is used to load a secret key with a specified `*id*` and OpenSSL provider registered URI `*scheme*`, such as [`pkcs11`](https://datatracker.ietf.org/doc/html/rfc7512).

Since version 1.21.0, variables can be used in the `*file*` name.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_ciphers DEFAULT;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.8.

Specifies the enabled ciphers for requests to a secured uwsgi server. The ciphers are specified in the format understood by the OpenSSL library.

The full list can be viewed using the “`openssl ciphers`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_conf_command</strong> <code><i>name</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.19.4.

Sets arbitrary OpenSSL configuration [commands](https://www.openssl.org/docs/man1.1.1/man3/SSL_CONF_cmd.html) when establishing a connection with the secured uwsgi server.

> The directive is supported when using OpenSSL 1.0.2 or higher.

Several `uwsgi_ssl_conf_command` directives can be specified on the same level. These directives are inherited from the previous configuration level if and only if there are no `uwsgi_ssl_conf_command` directives defined on the current level.

> Note that configuring OpenSSL directly might result in unexpected behavior.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.0.

Specifies a `*file*` with revoked certificates (CRL) in the PEM format used to [verify](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_verify) the certificate of the secured uwsgi server. When using intermediate certificates, their CRLs should be specified in the same file.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_key_log</strong> path;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.27.2.

Enables logging of secured uwsgi server connection SSL keys and specifies the path to the key log file. Keys are logged in the [SSLKEYLOGFILE](https://datatracker.ietf.org/doc/html/draft-ietf-tls-keylogfile) format compatible with Wireshark.

> This directive is available as part of our [commercial subscription](https://www.f5.com/products/nginx).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_name</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_name host from uwsgi_pass;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.0.

Allows overriding the server name used to [verify](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_verify) the certificate of the secured uwsgi server and to be [passed through SNI](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_server_name) when establishing a connection with the secured uwsgi server.

By default, the host part from [uwsgi\_pass](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_pass) is used.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_password_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.8.

Specifies a `*file*` with passphrases for [secret keys](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_certificate_key) where each passphrase is specified on a separate line. Passphrases are tried in turn when loading the key.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_protocols</strong> [<code>SSLv2</code>] [<code>SSLv3</code>] [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_protocols TLSv1.2 TLSv1.3;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.8.

Enables the specified protocols for requests to a secured uwsgi server.

> The `TLSv1.3` parameter is used by default since 1.23.4.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_server_name</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_server_name off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.0.

Enables or disables passing of the server name through [TLS Server Name Indication extension](http://en.wikipedia.org/wiki/Server_Name_Indication) (SNI, RFC 6066) when establishing a connection with the secured uwsgi server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_session_reuse</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_session_reuse on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.5.8.

Determines whether SSL sessions can be reused when working with a secured uwsgi server. If the errors “`digest check failed`” appear in the logs, try disabling session reuse.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.0.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_ssl_verify) the certificate of the secured uwsgi server.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_verify off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.0.

Enables or disables verification of the secured uwsgi server certificate.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_ssl_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_ssl_verify_depth 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 1.7.0.

Sets the verification depth in the secured uwsgi server certificates chain.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_store</strong> <code>on</code> | <code>off</code> | <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_store off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Enables saving of files to a disk. The `on` parameter saves files with paths corresponding to the directives [alias](https://nginx.org/en/docs/http/ngx_http_core_module.html#alias) or [root](https://nginx.org/en/docs/http/ngx_http_core_module.html#root). The `off` parameter disables saving of files. In addition, the file name can be set explicitly using the `*string*` with variables:

> uwsgi\_store /data/www$original\_uri;

The modification time of files is set according to the received “Last-Modified” response header field. The response is first written to a temporary file, and then the file is renamed. Starting from version 0.8.9, temporary files and the persistent store can be put on different file systems. However, be aware that in this case a file is copied across two file systems instead of the cheap renaming operation. It is thus recommended that for any given location both saved files and a directory holding temporary files, set by the [uwsgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_temp_path) directive, are put on the same file system.

This directive can be used to create local copies of static unchangeable files, e.g.:

> location /images/ {
>     root               /data/www;
>     error\_page         404 = /fetch$uri;
> }
> 
> location /fetch/ {
>     internal;
> 
>     uwsgi\_pass         backend:9000;
>     ...
> 
>     uwsgi\_store        on;
>     uwsgi\_store\_access user:rw group:rw all:r;
>     uwsgi\_temp\_path    /data/temp;
> 
>     alias              /data/www/;
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_store_access</strong> <code><i>users</i></code>:<code><i>permissions</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_store_access user:rw;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets access permissions for newly created files and directories, e.g.:

> uwsgi\_store\_access user:rw group:rw all:r;

If any `group` or `all` access permissions are specified then `user` permissions may be omitted:

> uwsgi\_store\_access group:rw all:r;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_temp_file_write_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_temp_file_write_size 8k|16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Limits the `*size*` of data written to a temporary file at a time, when buffering of responses from the uwsgi server to temporary files is enabled. By default, `*size*` is limited by two buffers set by the [uwsgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffer_size) and [uwsgi\_buffers](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffers) directives. The maximum size of a temporary file is set by the [uwsgi\_max\_temp\_file\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_max_temp_file_size) directive.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uwsgi_temp_path</strong> <code><i>path</i></code> [<code><i>level1</i></code> [<code><i>level2</i></code> [<code><i>level3</i></code>]]];</code><br></td></tr><tr><th>Default:</th><td><pre>uwsgi_temp_path uwsgi_temp;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Defines a directory for storing temporary files with data received from uwsgi servers. Up to three-level subdirectory hierarchy can be used underneath the specified directory. For example, in the following configuration

> uwsgi\_temp\_path /spool/nginx/uwsgi\_temp 1 2;

a temporary file might look like this:

> /spool/nginx/uwsgi\_temp/**7**/**45**/00000123**457**

See also the `use_temp_path` parameter of the [uwsgi\_cache\_path](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_cache_path) directive.