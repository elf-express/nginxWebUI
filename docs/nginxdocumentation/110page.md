# page

> Source: https://nginx.org/en/docs/njs/engine.html

---

## 目錄

- [JavaScript Engine](#javascript-engine)
    - [njs engine](#njs-engine)
    - [QuickJS engine](#quickjs-engine)

---

## JavaScript Engine

Starting from version [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6), multiple JavaScript engines are supported. To specify a particular engine, use the `js_engine` directive available for both the [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_engine) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_engine). By default, the njs engine is used.

#### njs engine

> The njs engine is deprecated since [1.0.0](https://nginx.org/en/docs/njs/changes.html#njs1.0.0); new code should use the [QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine instead.

njs is an embeddable JavaScript engine developed as a part of the njs module. See the [Сompatibility](https://nginx.org/en/docs/njs/compatibility.html) section for details.

#### QuickJS engine

[QuickJS](https://bellard.org/quickjs/) is a lightweight, embeddable JavaScript engine that supports the [ES2023](https://tc39.es/ecma262/2023/) specification, including features as modules, asynchronous generators, proxies and BigInt.

Since version [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6), a drop-in replacement for [njs/nginx objects](https://nginx.org/en/docs/njs/reference.html) has been introduced to ensure compatibility with the njs engine, with the following exceptions:

-   njs-specific API: [`njs.dump()`](https://nginx.org/en/docs/njs/reference.html#njs_dump), `console.dump()`.
-   deprecated API: `require()`, use the `import` statement instead.
-   `js_preload_object` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_preload_object) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_preload_object).

njs built-in modules status:

-   [`buffer`](https://nginx.org/en/docs/njs/reference.html#buffer): since [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6).
-   [`crypto`](https://nginx.org/en/docs/njs/reference.html#crypto): since [0.8.10](https://nginx.org/en/docs/njs/changes.html#njs0.8.10).
-   [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs): since [0.8.9](https://nginx.org/en/docs/njs/changes.html#njs0.8.9).
-   [`querystring`](https://nginx.org/en/docs/njs/reference.html#querystring): since [0.8.10](https://nginx.org/en/docs/njs/changes.html#njs0.8.10).
-   [`WebCrypto`](https://nginx.org/en/docs/njs/reference.html#builtin_crypto): since [0.8.10](https://nginx.org/en/docs/njs/changes.html#njs0.8.10).
-   [`xml`](https://nginx.org/en/docs/njs/reference.html#xml): since [0.8.10](https://nginx.org/en/docs/njs/changes.html#njs0.8.10).
-   [`zlib`](https://nginx.org/en/docs/njs/reference.html#zlib): since [0.8.5](https://nginx.org/en/docs/njs/changes.html#njs0.8.5).

njs built-in objects status:

-   [`process`](https://nginx.org/en/docs/njs/reference.html#process): since [0.8.8](https://nginx.org/en/docs/njs/changes.html#njs0.8.8).
-   [`TextDecoder`](https://nginx.org/en/docs/njs/reference.html#textdecoder): since [0.8.10](https://nginx.org/en/docs/njs/changes.html#njs0.8.10).
-   [`TextEncoder`](https://nginx.org/en/docs/njs/reference.html#textencoder): since [0.8.10](https://nginx.org/en/docs/njs/changes.html#njs0.8.10).

nginx built-in objects status:

-   [`ngx.fetch`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch): since [0.9.1](https://nginx.org/en/docs/njs/changes.html#njs0.9.1).
-   [`shared dictionary`](https://nginx.org/en/docs/njs/reference.html#ngx_shared): since [0.8.8](https://nginx.org/en/docs/njs/changes.html#njs0.8.8).