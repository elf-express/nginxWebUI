# page

> Source: https://nginx.org/en/docs/http/ngx_http_js_module.html

---

## 目錄

- [Module ngx\_http\_js\_module](#module-ngxhttpjsmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Request Argument](#request-argument)

---

## Module ngx\_http\_js\_module

The `ngx_http_js_module` module is used to implement location and variable handlers in [njs](https://nginx.org/en/docs/njs/index.html) — a subset of the JavaScript language.

Download and install instructions are available [here](https://nginx.org/en/docs/njs/install.html).

#### Example Configuration

The example works since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0).

```nginx
http {
    # since 0.9.1
    js_engine qjs;

    js_import http.js;

    js_set $foo     http.foo;
    js_set $summary http.summary;
    js_set $hash    http.hash;

    resolver 10.0.0.1;

    server {
        listen 8000;

        location / {
            add_header X-Foo $foo;
            js_content http.baz;
        }

        location = /summary {
            return 200 $summary;
        }

        location = /hello {
            js_content http.hello;
        }

        # since 0.7.0
        location = /fetch {
            js_content                   http.fetch;
            js_fetch_trusted_certificate /path/to/ISRG_Root_X1.pem;
        }

        # since 0.7.0
        location = /crypto {
            add_header Hash $hash;
            return     200;
        }
    }
}
```

The `http.js` file:

```javascript
function foo(r) {
    r.log("hello from foo() handler");
    return "foo";
}

function summary(r) {
    var a, s, h;

    s = "JS summary\n\n";

    s += "Method: " + r.method + "\n";
    s += "HTTP version: " + r.httpVersion + "\n";
    s += "Host: " + r.headersIn.host + "\n";
    s += "Remote Address: " + r.remoteAddress + "\n";
    s += "URI: " + r.uri + "\n";

    s += "Headers:\n";
    for (h in r.headersIn) {
        s += "  header '" + h + "' is '" + r.headersIn[h] + "'\n";
    }

    s += "Args:\n";
    for (a in r.args) {
        s += "  arg '" + a + "' is '" + r.args[a] + "'\n";
    }

    return s;
}

function baz(r) {
    r.status = 200;
    r.headersOut.foo = 1234;
    r.headersOut['Content-Type'] = "text/plain; charset=utf-8";
    r.headersOut['Content-Length'] = 15;
    r.sendHeader();
    r.send("nginx");
    r.send("java");
    r.send("script");

    r.finish();
}

function hello(r) {
    r.return(200, "Hello world!");
}

// since 0.7.0
async function fetch(r) {
    let results = await Promise.all([ngx.fetch('https://nginx.org/'),
                                     ngx.fetch('https://nginx.org/en/')]);

    r.return(200, JSON.stringify(results, undefined, 4));
}

// since 0.7.0
async function hash(r) {
    let hash = await crypto.subtle.digest('SHA-512', r.headersIn.host);
    r.setReturnValue(Buffer.from(hash).toString('hex'));
}

export default {foo, summary, baz, hello, fetch, hash};
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_body_filter</strong> <code><i>module.function</i></code> [<code><i>buffer_type</i></code>=<code><i>string</i></code> | <code><i>buffer</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code>, <code>limit_except</code><br></td></tr></tbody></table>

This directive appeared in version 0.5.2.

Sets an njs function as a response body filter. The filter function is called for each data chunk of a response body with the following arguments:

`r`

the [HTTP request](https://nginx.org/en/docs/njs/reference.html#http) object

`data`

the incoming data chunk, may be a string or Buffer depending on the `buffer_type` value, by default is a string. Since [0.8.5](https://nginx.org/en/docs/njs/changes.html#njs0.8.5), the `data` value is implicitly converted to a valid UTF-8 string by default. For binary data, the `buffer_type` value should be set to `buffer`.

`flags`

an object with the following properties:

`last`

a boolean value, true if data is a last buffer.

The filter function can pass its own modified version of the input data chunk to the next body filter by calling [`r.sendBuffer()`](https://nginx.org/en/docs/njs/reference.html#r_sendbuffer). For example, to transform all the lowercase letters in the response body:

```javascript
function filter(r, data, flags) {
    r.sendBuffer(data.toLowerCase(), flags);
}
```

If the filter function changes the length of the response body, the “Content-Length” response header (if present) should be cleared in [`js_header_filter`](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_header_filter) to enforce chunked transfer encoding:

```javascript
example.conf:
 location /foo {
     # proxy_pass http://localhost:8080;

    js_header_filter main.clear_content_length;
    js_body_filter   main.filter;
 }

example.js:
 function clear_content_length(r) {
     delete r.headersOut['Content-Length'];
 }
```

To stop filtering and pass the data chunks to the client without calling `js_body_filter`, [`r.done()`](https://nginx.org/en/docs/njs/reference.html#r_done) can be used. For example, to prepend some data to the response body:

```javascript
function prepend(r, data, flags) {
    r.sendBuffer("XXX");
    r.sendBuffer(data, flags);
    r.done();
}
```

> As the `js_body_filter` handler returns its result immediately, it supports only synchronous operations. Thus, asynchronous operations such as [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#r_subrequest) or [setTimeout()](https://nginx.org/en/docs/njs/reference.html#settimeout) are not supported.

> The directive can be specified inside the [if](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#if) block since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_access</strong> <code><i>module.function</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code>, <code>limit_except</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.9.

Sets an njs function as a handler in the [access phase](https://nginx.org/en/docs/dev/development_guide.html#http_phases). Asynchronous operations such as [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#r_subrequest), [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch), and [setTimeout()](https://nginx.org/en/docs/njs/reference.html#settimeout) are supported.

A handler that returns without calling [`r.return()`](https://nginx.org/en/docs/njs/reference.html#r_return) or [`r.decline()`](https://nginx.org/en/docs/njs/reference.html#r_decline) grants access. To deny access or send a custom response (for example, a redirect), the handler may call [`r.return()`](https://nginx.org/en/docs/njs/reference.html#r_return). To make the handler express no opinion, deferring the decision to other access checkers under [`satisfy any`](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy), the handler may call [`r.decline()`](https://nginx.org/en/docs/njs/reference.html#r_decline).

For example:

```javascript
example.conf:
 location /protected/ {
     js_access  main.auth;
     proxy_pass http://upstream;
 }

example.js:
 async function auth(r) {
     let reply = await ngx.fetch('http://authsvc/check', {
         headers: {Authorization: r.headersIn.Authorization}
     });

     if (reply.status != 200) {
         r.return(401);
         return;
     }
 }

 export default {auth};
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_content</strong> <code><i>module.function</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code>, <code>limit_except</code><br></td></tr></tbody></table>

Sets an njs function as a location content handler. Since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0), a module function can be referenced.

> The directive can be specified inside the [if](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#if) block since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_context_reuse</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_context_reuse 128;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.6.

Sets a maximum number of JS context to be reused for [QuickJS engine](https://nginx.org/en/docs/njs/engine.html). Each context is used for a single request. The finished context is put into a pool of reusable contexts. If the pool is full, the context is destroyed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_engine</strong> <code>njs</code> | <code>qjs</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_engine njs;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.6.

Sets a [JavaScript engine](https://nginx.org/en/docs/njs/engine.html) to be used for njs scripts. The `njs` parameter sets the njs engine, also used by default. The `qjs` parameter sets the QuickJS engine.

> The `njs` engine is deprecated since [1.0.0](https://nginx.org/en/docs/njs/changes.html#njs1.0.0); new configurations should use the `qjs` ([QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine)) engine.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_buffer_size 16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Sets the `*size*` of the buffer used for reading and writing with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_ciphers</strong> <code><i>ciphers</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_ciphers HIGH:!aNULL:!MD5;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Specifies the enabled ciphers for HTTPS requests with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The ciphers are specified in the format understood by the OpenSSL library.

The full list can be viewed using the “`openssl ciphers`” command.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_max_response_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_max_response_buffer_size 1m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Sets the maximum `*size*` of the response received with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_protocols</strong> [<code>TLSv1</code>] [<code>TLSv1.1</code>] [<code>TLSv1.2</code>] [<code>TLSv1.3</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_protocols TLSv1 TLSv1.1 TLSv1.2;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Enables the specified protocols for HTTPS requests with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Defines a timeout for reading and writing for [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The timeout is set only between two successive read/write operations, not for the whole response. If no data is transmitted within this time, the connection is closed.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Specifies a `*file*` with trusted CA certificates in the PEM format used to [verify](https://nginx.org/en/docs/njs/reference.html#fetch_verify) the HTTPS certificate with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_verify on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.4.

Enables or disables verification of the HTTPS server certificate with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_verify_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_verify_depth 100;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.0.

Sets the verification depth in the HTTPS server certificates chain with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_proxy</strong> <code><i>url</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.4.

Configures a forward proxy URL with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The `*url*` supports the HTTP scheme only and can contain optional user credentials in the format `http://[user:password@]host:port` for Basic authentication. Supports both HTTP and HTTPS connections to destination servers. If the `*url*` is empty, proxy routing is disabled. The parameter value can contain variables.

Example:

```nginx
location /fetch {
    js_fetch_proxy http://user:pass@proxy.example.com:3128;
    js_content main.fetch_handler;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive</strong> <code><i>connections</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Activates the cache for connections to destination servers. When the value is greater than `0`, enables keepalive connections for [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

The `*connections*` parameter sets the maximum number of idle keepalive connections to destination servers that are preserved in the cache of each worker process. When this number is exceeded, the least recently used connections are closed.

In HTTP, the cache is maintained separately for each effective location configuration. A value set at the `http` or `server` level is inherited by locations, but each location uses its own cache. Cached connections are reused for requests with the same protocol, host, and port.

When enabled, keepalive assumes that destination servers send valid HTTP responses.

Example:

```nginx
location /fetch {
    js_fetch_keepalive 32;
    js_fetch_trusted_certificate /path/to/ISRG_Root_X1.pem;
    js_content main.fetch_handler;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive_requests</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive_requests 1000;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Sets the maximum number of requests that can be served through one keepalive connection with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). After the maximum number of requests is made, the connection is closed.

Closing connections periodically is necessary to free per-connection memory allocations. Therefore, using too high maximum number of requests could result in excessive memory usage and not recommended.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive_time</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive_time 1h;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Limits the maximum time during which requests can be processed through one keepalive connection with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). After this time is reached, the connection is closed following the subsequent request processing.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_fetch_keepalive_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>js_fetch_keepalive_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.2.

Sets a timeout during which an idle keepalive connection to a destination server will stay open with [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_header_filter</strong> <code><i>module.function</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code>, <code>limit_except</code><br></td></tr></tbody></table>

This directive appeared in version 0.5.1.

Sets an njs function as a response header filter. The directive allows changing arbitrary header fields of a response header.

> As the `js_header_filter` handler returns its result immediately, it supports only synchronous operations. Thus, asynchronous operations such as [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#r_subrequest) or [setTimeout()](https://nginx.org/en/docs/njs/reference.html#settimeout) are not supported.

> The directive can be specified inside the [if](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#if) block since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_import</strong> <code><i>module.js</i></code> | <code><i>export_name from module.js</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.4.0.

Imports a module that implements location and variable handlers in njs. The `export_name` is used as a namespace to access module functions. If the `export_name` is not specified, the module name will be used as a namespace.

```nginx
js_import http.js;
```

Here, the module name `http` is used as a namespace while accessing exports. If the imported module exports `foo()`, `http.foo` is used to refer to it.

Several `js_import` directives can be specified.

When `js_import` is specified inside a [location](https://nginx.org/en/docs/http/ngx_http_core_module.html#location), the imported modules are visible only if no JavaScript code has been invoked earlier in the request. A single VM is created per request when a [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) variable is first accessed or when a [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content), [js\_header\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_header_filter), [js\_body\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter), or [js\_access](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_access) handler is first invoked. The VM is cloned from the configuration scope active at that moment, and all subsequent JavaScript invocations within the request reuse it. Thus, if any of the above is referenced before the request is mapped to its final location, for example from a server-level [set](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#set), the VM is bound to the import set of the outer scope, and modules imported in the matched `location` will not be visible. To avoid this, declare such imports in a common parent scope.

> The directive can be specified on the `server` and `location` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_include</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

Specifies a file that implements location and variable handlers in njs:

```javascript
nginx.conf:
js_include http.js;
location   /version {
    js_content version;
}

http.js:
function version(r) {
    r.return(200, njs.version);
}
```

The directive was made obsolete in version [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0) and was removed in version [0.7.1](https://nginx.org/en/docs/njs/changes.html#njs0.7.1). The [js\_import](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import) directive should be used instead.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_load_http_native_module</strong> <code><i>path</i></code> [<code>as</code> <code><i>name</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

This directive appeared in version 0.9.5.

Loads a [native module](https://nginx.org/en/docs/njs/native_modules.html) (shared library) for use in HTTP JavaScript code. The directive is [QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine)\-only and is not available when using the njs built-in JavaScript engine.

The `*path*` parameter specifies the absolute path to the shared library file. The optional `as` `*name*` parameter provides an alias name for importing the module in JavaScript code. If not specified, the module can be imported using its filename.

Example:

```nginx
js_load_http_native_module /path/to/mylib.so;
js_load_http_native_module /path/to/other.so as myalias;

http {
    js_import main.js;
    # ... rest of http configuration
}
```

In JavaScript code:

```javascript
// Import by filename
import * as mylib from 'mylib.so';

// Import by alias
import * as myalias from 'myalias';

// Use exported functions
let result = mylib.add(5, 10);
```

> For security reasons, this directive is only allowed in the `main` configuration context. Native modules run with full process privileges; use absolute paths and ensure proper code review.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_path</strong> <code><i>path</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.3.0.

Sets an additional path for njs modules.

> The directive can be specified on the `server` and `location` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_periodic</strong> <code><i>module.function</i></code> [<code>interval</code>=<code><i>time</i></code>] [<code>jitter</code>=<code><i>number</i></code>] [<code>worker_affinity</code>=<code><i>mask</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.1.

Specifies a content handler to run at regular interval. The handler receives a [session object](https://nginx.org/en/docs/njs/reference.html#periodic_session) as its first argument, it also has access to global objects such as [ngx](https://nginx.org/en/docs/njs/reference.html#ngx).

The optional `interval` parameter sets the interval between two consecutive runs, by default, 5 seconds.

The optional `jitter` parameter sets the time within which the location content handler will be randomly delayed, by default, there is no delay.

By default, the `js_handler` is executed on worker process 0. The optional `worker_affinity` parameter allows specifying particular worker processes where the location content handler should be executed. Each worker process set is represented by a bitmask of allowed worker processes. The `all` mask allows the handler to be executed in all worker processes.

Example:

```javascript
example.conf:

location @periodics {
    # to be run at 1 minute intervals in worker process 0
    js_periodic main.handler interval=60s;

    # to be run at 1 minute intervals in all worker processes
    js_periodic main.handler interval=60s worker_affinity=all;

    # to be run at 1 minute intervals in worker processes 1 and 3
    js_periodic main.handler interval=60s worker_affinity=0101;

    resolver 10.0.0.1;
    js_fetch_trusted_certificate /path/to/ISRG_Root_X1.pem;
}

example.js:

async function handler(s) {
    let reply = await ngx.fetch('https://nginx.org/en/docs/njs/');
    let body = await reply.text();

    ngx.log(ngx.INFO, body);
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_preload_object</strong> <code><i>name.json</i></code> | <code><i>name</i></code> from <code><i>file.json</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.7.8.

Preloads an [immutable object](https://nginx.org/en/docs/njs/preload_objects.html) at configure time. The `name` is used as a name of the global variable though which the object is available in njs code. If the `name` is not specified, the file name will be used instead.

```nginx
js_preload_object map.json;
```

Here, the `map` is used as a name while accessing the preloaded object.

Several `js_preload_object` directives can be specified.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_set</strong> <code><i>$variable</i></code> <code><i>module.function</i></code> [<code>nocache</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

Sets an njs `function` for the specified `variable`. Since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0), a module function can be referenced.

The function is called when the variable is referenced for the first time for a given request. The exact moment depends on a [phase](https://nginx.org/en/docs/dev/development_guide.html#http_phases) at which the variable is referenced. This can be used to perform some logic not related to variable evaluation. For example, if the variable is referenced only in the [log\_format](https://nginx.org/en/docs/http/ngx_http_log_module.html#log_format) directive, its handler will not be executed until the log phase. This handler can be used to do some cleanup right before the request is freed.

Since [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6), if an optional argument `nocache` is specified, the handler is called every time it is referenced. Due to current limitations of the [rewrite](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html) module, when a `nocache` variable is referenced by the [set](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#set) directive its handler should always return a fixed-length value.

> As the `js_set` handler returns its result immediately, it supports only synchronous operations. Thus, asynchronous operations such as [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#r_subrequest) or [setTimeout()](https://nginx.org/en/docs/njs/reference.html#settimeout) are not supported.

> The directive can be specified on the `server` and `location` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_shared_dict_zone</strong> <code>zone</code>=<code><i>name</i></code>:<code><i>size</i></code> [<code>timeout</code>=<code><i>time</i></code>] [<code>type</code>=<code>string</code>|<code>number</code>] [<code>evict</code>] [<code>state</code>=<code><i>file</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

This directive appeared in version 0.8.0.

Sets the `*name*` and `*size*` of the shared memory zone that keeps the key-value [dictionary](https://nginx.org/en/docs/njs/reference.html#dict) shared between worker processes.

By default the shared dictionary uses a string as a key and a value. The optional `type` parameter allows redefining the value type to number.

The optional `timeout` parameter sets the time in milliseconds after which all shared dictionary entries are removed from the zone. If some entries require a different removal time, it can be set with the `timeout` argument of the [add](https://nginx.org/en/docs/njs/reference.html#dict_add), [incr](https://nginx.org/en/docs/njs/reference.html#dict_incr), and [set](https://nginx.org/en/docs/njs/reference.html#dict_set) methods ([0.8.5](https://nginx.org/en/docs/njs/changes.html#njs0.8.5)).

The optional `evict` parameter removes the oldest key-value pair when the zone storage is exhausted.

The optional `state` parameter specifies a `*file*` that keeps the shared dictionary state in JSON format and makes it persistent across nginx restarts ([0.9.1](https://nginx.org/en/docs/njs/changes.html#njs0.9.1)).

Example:

```javascript
example.conf:
    # Creates a 1Mb dictionary with string values,
    # removes key-value pairs after 60 seconds of inactivity:
    js_shared_dict_zone zone=foo:1M timeout=60s;

    # Creates a 512Kb dictionary with string values,
    # forcibly removes oldest key-value pairs when the zone is exhausted:
    js_shared_dict_zone zone=bar:512K timeout=30s evict;

    # Creates a 32Kb permanent dictionary with number values:
    js_shared_dict_zone zone=num:32k type=number;

    # Creates a 1Mb dictionary with string values and persistent state:
    js_shared_dict_zone zone=persistent:1M state=/tmp/dict.json;

example.js:
    function get(r) {
        r.return(200, ngx.shared.foo.get(r.args.key));
    }

    function set(r) {
        r.return(200, ngx.shared.foo.set(r.args.key, r.args.value));
    }

    function del(r) {
        r.return(200, ngx.shared.bar.delete(r.args.key));
    }

    function increment(r) {
        r.return(200, ngx.shared.num.incr(r.args.key, 2));
    }
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>js_var</strong> <code><i>$variable</i></code> [<code><i>value</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

This directive appeared in version 0.5.3.

Declares a [writable](https://nginx.org/en/docs/njs/reference.html#r_variables) variable. The value can contain text, variables, and their combination. The variable is not overwritten after a redirect unlike variables created with the [set](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#set) directive.

> The directive can be specified on the `server` and `location` level since [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).

#### Request Argument

Each HTTP njs handler receives one argument, a request [object](https://nginx.org/en/docs/njs/reference.html#http).