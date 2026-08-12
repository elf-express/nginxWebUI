# page

> Source: https://nginx.org/en/docs/njs/index.html

---

## 目錄

- [nginx JavaScript module](#nginx-javascript-module)
    - [Use cases](#use-cases)
    - [Basic HTTP Example](#basic-http-example)
    - [Tested OS and platforms](#tested-os-and-platforms)
    - [Presentation at nginx.conf 2018](#presentation-at-nginxconf-2018)

---

## nginx JavaScript module

njs is an nginx module that extends the server's functionality through JavaScript scripting, enabling the creation of custom server-side logic and [more](https://nginx.org/en/docs/njs/index.html#usecases).

> The built-in [njs](https://nginx.org/en/docs/njs/engine.html#njs_engine) JavaScript engine is deprecated since [1.0.0](https://nginx.org/en/docs/njs/changes.html#njs1.0.0); new configurations should use the [QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine.

-   [Download and install](https://nginx.org/en/docs/njs/install.html)
-   [Changes](https://nginx.org/en/docs/njs/changes.html)
-   [Reference](https://nginx.org/en/docs/njs/reference.html)
-   [JavaScript Engine](https://nginx.org/en/docs/njs/engine.html)
-   [Native modules](https://nginx.org/en/docs/njs/native_modules.html)
-   [Examples](https://github.com/nginx/njs-examples/)
-   [Security](https://nginx.org/en/docs/njs/security.html)
-   [Compatibility](https://nginx.org/en/docs/njs/compatibility.html)
-   [Command-line interface](https://nginx.org/en/docs/njs/cli.html)
-   [Understanding preloaded objects](https://nginx.org/en/docs/njs/preload_objects.html)
-   [Tested OS and platforms](https://nginx.org/en/docs/njs/index.html#tested_os_and_platforms)

-   [ngx\_http\_js\_module](https://nginx.org/en/docs/http/ngx_http_js_module.html)
-   [ngx\_stream\_js\_module](https://nginx.org/en/docs/stream/ngx_stream_js_module.html)

-   [Writing njs code using TypeScript definition files](https://nginx.org/en/docs/njs/typescript.html)
-   [Using node modules with njs](https://nginx.org/en/docs/njs/node_modules.html)

#### Use cases

-   Complex access control and security checks in njs before a request reaches an upstream server
-   Manipulating response headers
-   Writing flexible asynchronous content handlers and filters

See [examples](https://github.com/nginx/njs-examples/) for more njs use cases.

#### Basic HTTP Example

To use njs in nginx:

-   [install](https://nginx.org/en/docs/njs/install.html) njs scripting language
    
-   create an njs script file, for example, `http.js`. See [Reference](https://nginx.org/en/docs/njs/reference.html) for the list of njs properties and methods.
    
    > function hello(r) {
    >     r.return(200, "Hello world!");
    > }
    > 
    > export default {hello};
    
-   in the `nginx.conf` file, enable [ngx\_http\_js\_module](https://nginx.org/en/docs/http/ngx_http_js_module.html) module and specify the [js\_import](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import) directive with the `http.js` script file:
    
    > load\_module modules/ngx\_http\_js\_module.so;
    > 
    > events {}
    > 
    > http {
    >     # since 0.9.1
    >     js\_engine qjs;
    > 
    >     js\_import http.js;
    > 
    >     server {
    >         listen 8000;
    > 
    >         location / {
    >             js\_content http.hello;
    >         }
    >     }
    > }
    

There is also a standalone [command line](https://nginx.org/en/docs/njs/cli.html) utility that can be used independently of nginx for njs development and debugging.

#### Tested OS and platforms

-   FreeBSD / amd64;
-   Linux / x86, amd64, arm64, ppc64el;
-   Solaris 11 / amd64;
-   macOS / x86\_64;

#### Presentation at nginx.conf 2018