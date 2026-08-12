# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_js_module.html

---

## 目錄

- [Module ngx\_stream\_js\_module](#module-ngxstreamjsmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Session Object Properties](#session-object-properties)

---

## Module ngx\_stream\_js\_module

The `ngx_stream_js_module` module is used to implement handlers in [njs](https://nginx.org/en/docs/njs/index.html) — a subset of the JavaScript language.

Download and install instructions are available [here](https://nginx.org/en/docs/njs/install.html).

#### Example Configuration

The example works since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0).

> stream {
>     # since 0.9.1
>     js\_engine qjs;
> 
>     js\_import stream.js;
> 
>     js\_set $bar stream.bar;
>     js\_set $req\_line stream.req\_line;
> 
>     server {
>         listen 12345;
> 
>         js\_preread stream.preread;
>         return     $req\_line;
>     }
> 
>     server {
>         listen 12346;
> 
>         js\_access  stream.access;
>         proxy\_pass 127.0.0.1:8000;
>         js\_filter  stream.header\_inject;
>     }
> }
> 
> http {
>     server {
>         listen 8000;
>         location / {
>             return 200 $http\_foo\\n;
>         }
>     }
> }

The `stream.js` file:

> var line = '';
> 
> function bar(s) {
>     var v = s.variables;
>     s.log("hello from bar() handler!");
>     return "bar-var" + v.remote\_port + "; pid=" + v.pid;
> }
> 
> function preread(s) {
>     s.on('upload', function (data, flags) {
>         var n = data.indexOf('\\n');
>         if (n != -1) {
>             line = data.substr(0, n);
>             s.done();
>         }
>     });
> }
> 
> function req\_line(s) {
>     return line;
> }
> 
> // Read HTTP request line.
> // Collect bytes in 'req' until
> // request line is read.
> // Injects HTTP header into a client's request
> 
> var my\_header =  'Foo: foo';
> function header\_inject(s) {
>     var req = '';
>     s.on('upload', function(data, flags) {
>         req += data;
>         var n = req.search('\\n');
>         if (n != -1) {
>             var rest = req.substr(n + 1);
>             req = req.substr(0, n + 1);
>             s.send(req + my\_header + '\\r\\n' + rest, flags);
>             s.off('upload');
>         }
>     });
> }
> 
> function access(s) {
>     if (s.remoteAddress.match('^192.\*')) {
>         s.deny();
>         return;
>     }
> 
>     s.allow();
> }
> 
> export default {bar, preread, req\_line, header\_inject, access};

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_access</strong> <code><i>module.function</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets an njs function which will be called at the [access](https://nginx.org/en/docs/stream/stream_processing.html#access_phase) phase. Since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0), a module function can be referenced.

The function is called once at the moment when the stream session reaches the [access](https://nginx.org/en/docs/stream/stream_processing.html#access_phase) phase for the first time. The function is called with the following arguments:

`s`

the [Stream Session](https://nginx.org/en/docs/njs/reference.html#stream) object

At this phase, it is possible to perform initialization or register a callback with the [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) method for each incoming data chunk until one of the following methods are called: [`s.allow()`](https://nginx.org/en/docs/njs/reference.html#s_allow), [`s.decline()`](https://nginx.org/en/docs/njs/reference.html#s_decline), [`s.done()`](https://nginx.org/en/docs/njs/reference.html#s_done). As soon as one of these methods is called, the stream session processing switches to the [next phase](https://nginx.org/en/docs/stream/stream_processing.html) and all current [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) callbacks are dropped.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_context_reuse</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_context_reuse 128;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.6.

Sets a maximum number of JS context to be reused for [QuickJS engine](https://nginx.org/en/docs/njs/engine.html). Each context is used for a single stream session. The finished context is put into a pool of reusable contexts. If the pool is full, the context is destroyed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_engine</strong> <code>njs</code> | <code>qjs</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_engine njs;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.6.

Sets a [JavaScript engine](https://nginx.org/en/docs/njs/engine.html) to be used for njs scripts. The `njs` parameter sets the njs engine, also used by default. The `qjs` parameter sets the QuickJS engine.

> The `njs` engine is deprecated since [1.0.0](https://nginx.org/en/docs/njs/changes.html#njs1.0.0); new configurations should use the `qjs` ([QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine)) engine.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_buffer_size 16k;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Sets the `*size*` of the buffer used for reading and writing with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_ciphers HIGH:!aNULL:!MD5;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Specifies the enabled ciphers for HTTPS connections with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The ciphers are specified in the format understood by the OpenSSL library.

The full list can be viewed using the “`openssl ciphers`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_max_response_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_max_response_buffer_size 1m;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Sets the maximum `*size*` of the response received with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_protocols</strong> [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_protocols TLSv1 TLSv1.1 TLSv1.2;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Enables the specified protocols for HTTPS connections with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Defines a timeout for reading and writing for [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The timeout is set only between two successive read/write operations, not for the whole response. If no data is transmitted within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/njs/reference.html#fetch_verify) the HTTPS certificate with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_verify on;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Enables or disables verification of the HTTPS server certificate with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_verify_depth 100;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Sets the verification depth in the HTTPS server certificates chain with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_proxy</strong> <code><i>url</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.4.

Configures a forward proxy URL with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The `*url*` supports the HTTP scheme only and can contain optional user credentials in the format `http://[user:password@]host:port` for Basic authentication. Supports both HTTP and HTTPS connections to destination servers. If the `*url*` is empty, proxy routing is disabled. The parameter value can contain variables.

Example:

> server {
>     listen 12345;
>     js\_fetch\_proxy http://user:pass@proxy.example.com:3128;
>     js\_preread main.fetch\_handler;
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive</strong> <code><i>connections</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive 0;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Activates the cache for connections to destination servers. When the value is greater than `0`, enables keepalive connections for [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

The `*connections*` parameter sets the maximum number of idle keepalive connections to destination servers that are preserved in the cache of each worker process. When this number is exceeded, the least recently used connections are closed.

In Stream, the cache is maintained separately for each server configuration. A value set at the `stream` level is inherited by servers, but each server uses its own cache. Cached connections are reused for requests with the same protocol, host, and port.

When enabled, keepalive assumes that destination servers send valid HTTP responses.

Example:

> server {
>     listen 12345;
>     js\_fetch\_keepalive 32;
>     js\_fetch\_trusted\_certificate /path/to/ISRG\_Root\_X1.pem;
>     js\_preread main.fetch\_handler;
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive_requests</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive_requests 1000;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Sets the maximum number of requests that can be served through one keepalive connection with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). After the maximum number of requests is made, the connection is closed.

Closing connections periodically is necessary to free per-connection memory allocations. Therefore, using too high maximum number of requests could result in excessive memory usage and not recommended.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive_time</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive_time 1h;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Limits the maximum time during which requests can be processed through one keepalive connection with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). After this time is reached, the connection is closed following the subsequent request processing.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Sets a timeout during which an idle keepalive connection to a destination server will stay open with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_filter</strong> <code><i>module.function</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets a data filter. Since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0), a module function can be referenced. The filter function is called once at the moment when the stream session reaches the [content](https://nginx.org/en/docs/stream/stream_processing.html#content_phase) phase.

The filter function is called with the following arguments:

`s`

the [Stream Session](https://nginx.org/en/docs/njs/reference.html#stream) object

At this phase, it is possible to perform initialization or register a callback with the [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) method for each incoming data chunk. The [`s.off()`](https://nginx.org/en/docs/njs/reference.html#s_off) method may be used to unregister a callback and stop filtering.

> As the `js_filter` handler returns its result immediately, it supports only synchronous operations. Thus, asynchronous operations such as [`ngx.fetch()`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) or [`setTimeout()`](https://nginx.org/en/docs/njs/reference.html#settimeout) are not supported.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_import</strong> <code><i>module.js</i></code> | <code><i>export_name from module.js</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.4.0.

Imports a module that implements location and variable handlers in njs. The `export_name` is used as a namespace to access module functions. If the `export_name` is not specified, the module name will be used as a namespace.

> js\_import stream.js;

Here, the module name `stream` is used as a namespace while accessing exports. If the imported module exports `foo()`, `stream.foo` is used to refer to it.

Several `js_import` directives can be specified.

> The directive can be specified on the `server` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_include</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

Specifies a file that implements server and variable handlers in njs:

> nginx.conf:
> js\_include stream.js;
> js\_set     $js\_addr address;
> server {
>     listen 127.0.0.1:12345;
>     return $js\_addr;
> }
> 
> stream.js:
> function address(s) {
>     return s.remoteAddress;
> }

The directive was made obsolete in version [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0) and was removed in version [0.7.1](https://nginx.org/en/docs/njs/changes.html#njs0.7.1). The [js\_import](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_import) directive should be used instead.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_load_stream_native_module</strong> <code><i>path</i></code> [<code>as</code> <code><i>name</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.5.

Loads a [native module](https://nginx.org/en/docs/njs/native_modules.html) (shared library) for use in Stream JavaScript code. The directive is [QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine)\-only and is not available when using the njs built-in JavaScript engine.

The `*path*` parameter specifies the absolute path to the shared library file. The optional `as` `*name*` parameter provides an alias name for importing the module in JavaScript code. If not specified, the module can be imported using its filename.

Example:

> js\_load\_stream\_native\_module /path/to/mylib.so;
> js\_load\_stream\_native\_module /path/to/other.so as myalias;
> 
> stream {
>     js\_import main.js;
>     # ... rest of stream configuration
> }

In JavaScript code:

> // Import by filename
> import \* as mylib from 'mylib.so';
> 
> // Import by alias
> import \* as myalias from 'myalias';
> 
> // Use exported functions
> let result = mylib.add(5, 10);

> For security reasons, this directive is only allowed in the `main` configuration context. Native modules run with full process privileges; use absolute paths and ensure proper code review.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_path</strong> <code><i>path</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.3.0.

Sets an additional path for njs modules.

> The directive can be specified on the `server` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_periodic</strong> <code><i>module.function</i></code> [<code>interval</code>=<code><i>time</i></code>] [<code>jitter</code>=<code><i>number</i></code>] [<code>worker_affinity</code>=<code><i>mask</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.1.

Specifies a content handler to run at regular interval. The handler receives a [session object](https://nginx.org/en/docs/njs/reference.html#periodic_session) as its first argument, it also has access to global objects such as [ngx](https://nginx.org/en/docs/njs/reference.html#ngx).

The optional `interval` parameter sets the interval between two consecutive runs, by default, 5 seconds.

The optional `jitter` parameter sets the time within which the location content handler will be randomly delayed, by default, there is no delay.

By default, the `js_handler` is executed on worker process 0. The optional `worker_affinity` parameter allows specifying particular worker processes where the location content handler should be executed. Each worker process set is represented by a bitmask of allowed worker processes. The `all` mask allows the handler to be executed in all worker processes.

Example:

> example.conf:
> 
> location @periodics {
>     # to be run at 1 minute intervals in worker process 0
>     js\_periodic main.handler interval=60s;
> 
>     # to be run at 1 minute intervals in all worker processes
>     js\_periodic main.handler interval=60s worker\_affinity=all;
> 
>     # to be run at 1 minute intervals in worker processes 1 and 3
>     js\_periodic main.handler interval=60s worker\_affinity=0101;
> 
>     resolver 10.0.0.1;
>     js\_fetch\_trusted\_certificate /path/to/ISRG\_Root\_X1.pem;
> }
> 
> example.js:
> 
> async function handler(s) {
>     let reply = await ngx.fetch('https://nginx.org/en/docs/njs/');
>     let body = await reply.text();
> 
>     ngx.log(ngx.INFO, body);
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_preload_object</strong> <code><i>name.json</i></code> | <code><i>name</i></code> from <code><i>file.json</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.8.

Preloads an [immutable object](https://nginx.org/en/docs/njs/preload_objects.html) at configure time. The `name` is used as a name of the global variable though which the object is available in njs code. If the `name` is not specified, the file name will be used instead.

> js\_preload\_object map.json;

Here, the `map` is used as a name while accessing the preloaded object.

Several `js_preload_object` directives can be specified.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_preread</strong> <code><i>module.function</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets an njs function which will be called at the [preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase) phase. Since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0), a module function can be referenced.

The function is called once at the moment when the stream session reaches the [preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase) phase for the first time. The function is called with the following arguments:

`s`

the [Stream Session](https://nginx.org/en/docs/njs/reference.html#stream) object

At this phase, it is possible to perform initialization or register a callback with the [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) method for each incoming data chunk until one of the following methods are called: [`s.allow()`](https://nginx.org/en/docs/njs/reference.html#s_allow), [`s.decline()`](https://nginx.org/en/docs/njs/reference.html#s_decline), [`s.done()`](https://nginx.org/en/docs/njs/reference.html#s_done). When one of these methods is called, the stream session switches to the [next phase](https://nginx.org/en/docs/stream/stream_processing.html) and all current [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) callbacks are dropped.

> As the `js_preread` handler returns its result immediately, it supports only synchronous callbacks. Thus, asynchronous callbacks such as [`ngx.fetch()`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) or [`setTimeout()`](https://nginx.org/en/docs/njs/reference.html#settimeout) are not supported. Nevertheless, asynchronous operations are supported in [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) callbacks in the [preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase) phase. See [this example](https://github.com/nginx/njs-examples#authorizing-connections-using-ngx-fetch-as-auth-request-stream-auth-request) for more information.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_set</strong> <code><i>$variable</i></code> <code><i>module.function</i></code> [<code>nocache</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

Sets an njs `function` for the specified `variable`. Since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0), a module function can be referenced.

The function is called when the variable is referenced for the first time for a given request. The exact moment depends on a [phase](https://nginx.org/en/docs/stream/stream_processing.html) at which the variable is referenced. This can be used to perform some logic not related to variable evaluation. For example, if the variable is referenced only in the [log\_format](https://nginx.org/en/docs/stream/ngx_stream_log_module.html#log_format) directive, its handler will not be executed until the log phase. This handler can be used to do some cleanup right before the request is freed.

Since [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6), when optional argument `nocache` is provided the handler is called every time it is referenced. Due to current limitations of the [rewrite](https://nginx.org/en/docs/stream/ngx_stream_rewrite_module.html) module, when a `nocache` variable is referenced by the [set](https://nginx.org/en/docs/stream/ngx_stream_set_module.html#set) directive its handler should always return a fixed-length value.

> As the `js_set` handler returns its result immediately, it supports only synchronous callbacks. Thus, asynchronous callbacks such as [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) or [setTimeout()](https://nginx.org/en/docs/njs/reference.html#settimeout) are not supported.

> The directive can be specified on the `server` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_shared_dict_zone</strong> <code>zone</code>=<code><i>name</i></code>:<code><i>size</i></code> [<code>timeout</code>=<code><i>time</i></code>] [<code>type</code>=<code>string</code>|<code>number</code>] [<code>evict</code>] [<code>state</code>=<code><i>file</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.0.

Sets the `*name*` and `*size*` of the shared memory zone that keeps the key-value [dictionary](https://nginx.org/en/docs/njs/reference.html#dict) shared between worker processes.

By default the shared dictionary uses a string as a key and a value. The optional `type` parameter allows redefining the value type to number.

The optional `timeout` parameter sets the time in milliseconds after which all shared dictionary entries are removed from the zone. If some entries require a different removal time, it can be set with the `timeout` argument of the [add](https://nginx.org/en/docs/njs/reference.html#dict_add), [incr](https://nginx.org/en/docs/njs/reference.html#dict_incr), and [set](https://nginx.org/en/docs/njs/reference.html#dict_set) methods ([0.8.5](https://nginx.org/en/docs/njs/changes.html#njs0.8.5)).

The optional `evict` parameter removes the oldest key-value pair when the zone storage is exhausted.

The optional `state` parameter specifies a `*file*` that keeps the shared dictionary state in JSON format and makes it persistent across nginx restarts ([0.9.1](https://nginx.org/en/docs/njs/changes.html#njs0.9.1)).

Example:

> example.conf:
>     # Creates a 1Mb dictionary with string values,
>     # removes key-value pairs after 60 seconds of inactivity:
>     js\_shared\_dict\_zone zone=foo:1M timeout=60s;
> 
>     # Creates a 512Kb dictionary with string values,
>     # forcibly removes oldest key-value pairs when the zone is exhausted:
>     js\_shared\_dict\_zone zone=bar:512K timeout=30s evict;
> 
>     # Creates a 32Kb permanent dictionary with number values:
>     js\_shared\_dict\_zone zone=num:32k type=number;
> 
>     # Creates a 1Mb dictionary with string values and persistent state:
>     js\_shared\_dict\_zone zone=persistent:1M state=/tmp/dict.json;
> 
> example.js:
>     function get(r) {
>         r.return(200, ngx.shared.foo.get(r.args.key));
>     }
> 
>     function set(r) {
>         r.return(200, ngx.shared.foo.set(r.args.key, r.args.value));
>     }
> 
>     function del(r) {
>         r.return(200, ngx.shared.bar.delete(r.args.key));
>     }
> 
>     function increment(r) {
>         r.return(200, ngx.shared.num.incr(r.args.key, 2));
>     }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_var</strong> <code><i>$variable</i></code> [<code><i>value</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

This directive appeared in version 0.5.3.

Declares a [writable](https://nginx.org/en/docs/njs/reference.html#r_variables) variable. The value can contain text, variables, and their combination.

> The directive can be specified on the `server` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

#### Session Object Properties

Each stream njs handler receives one argument, a stream session [object](https://nginx.org/en/docs/njs/reference.html#stream).