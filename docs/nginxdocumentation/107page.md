# page

> Source: https://nginx.org/en/docs/njs/changes.html

---

## 目錄

- [Changes](#changes)
    - [Changes with njs 1.0.0](#changes-with-njs-100)
    - [Changes with njs 0.9.9](#changes-with-njs-099)
    - [Changes with njs 0.9.8](#changes-with-njs-098)
    - [Changes with njs 0.9.7](#changes-with-njs-097)
    - [Changes with njs 0.9.6](#changes-with-njs-096)
    - [Changes with njs 0.9.5](#changes-with-njs-095)
    - [Changes with njs 0.9.4](#changes-with-njs-094)
    - [Changes with njs 0.9.3](#changes-with-njs-093)
    - [Changes with njs 0.9.2](#changes-with-njs-092)
    - [Changes with njs 0.9.1](#changes-with-njs-091)
    - [Changes with njs 0.9.0](#changes-with-njs-090)
    - [Changes with njs 0.8.10](#changes-with-njs-0810)
    - [Changes with njs 0.8.9](#changes-with-njs-089)
    - [Changes with njs 0.8.8](#changes-with-njs-088)
    - [Changes with njs 0.8.7](#changes-with-njs-087)
    - [Changes with njs 0.8.6](#changes-with-njs-086)
    - [Changes with njs 0.8.5](#changes-with-njs-085)
    - [Changes with njs 0.8.4](#changes-with-njs-084)
    - [Changes with njs 0.8.3](#changes-with-njs-083)
    - [Changes with njs 0.8.2](#changes-with-njs-082)
    - [Changes with njs 0.8.1](#changes-with-njs-081)
    - [Changes with njs 0.8.0](#changes-with-njs-080)
    - [Changes with njs 0.7.12](#changes-with-njs-0712)
    - [Changes with njs 0.7.11](#changes-with-njs-0711)
    - [Changes with njs 0.7.10](#changes-with-njs-0710)
    - [Changes with njs 0.7.9](#changes-with-njs-079)
    - [Changes with njs 0.7.8](#changes-with-njs-078)
    - [Changes with njs 0.7.7](#changes-with-njs-077)
    - [Changes with njs 0.7.6](#changes-with-njs-076)
    - [Changes with njs 0.7.5](#changes-with-njs-075)
    - [Changes with njs 0.7.4](#changes-with-njs-074)
    - [Changes with njs 0.7.3](#changes-with-njs-073)
    - [Changes with njs 0.7.2](#changes-with-njs-072)
    - [Changes with njs 0.7.1](#changes-with-njs-071)
    - [Changes with njs 0.7.0](#changes-with-njs-070)
    - [Changes with njs 0.6.2](#changes-with-njs-062)
    - [Changes with njs 0.6.1](#changes-with-njs-061)
    - [Changes with njs 0.6.0](#changes-with-njs-060)
    - [Changes with njs 0.5.3](#changes-with-njs-053)
    - [Changes with njs 0.5.2](#changes-with-njs-052)
    - [Changes with njs 0.5.1](#changes-with-njs-051)
    - [Changes with njs 0.5.0](#changes-with-njs-050)
    - [Changes with njs 0.4.4](#changes-with-njs-044)
    - [Changes with njs 0.4.3](#changes-with-njs-043)
    - [Changes with njs 0.4.2](#changes-with-njs-042)
    - [Changes with njs 0.4.1](#changes-with-njs-041)
    - [Changes with njs 0.4.0](#changes-with-njs-040)
    - [Changes with njs 0.3.9](#changes-with-njs-039)
    - [Changes with njs 0.3.8](#changes-with-njs-038)
    - [Changes with njs 0.3.7](#changes-with-njs-037)
    - [Changes with njs 0.3.6](#changes-with-njs-036)
    - [Changes with njs 0.3.5](#changes-with-njs-035)
    - [Changes with njs 0.3.4](#changes-with-njs-034)
    - [Changes with njs 0.3.3](#changes-with-njs-033)
    - [Changes with njs 0.3.2](#changes-with-njs-032)
    - [Changes with njs 0.3.1](#changes-with-njs-031)
    - [Changes with njs 0.3.0](#changes-with-njs-030)
    - [Changes with njs 0.2.8](#changes-with-njs-028)
    - [Changes with njs 0.2.7](#changes-with-njs-027)
    - [Changes with njs 0.2.6](#changes-with-njs-026)
    - [Changes with njs 0.2.5](#changes-with-njs-025)
    - [Changes with njs 0.2.4](#changes-with-njs-024)
    - [Changes with njs 0.2.3](#changes-with-njs-023)
    - [Changes with njs 0.2.2](#changes-with-njs-022)
    - [Changes with njs 0.2.1](#changes-with-njs-021)
    - [Changes with njs 0.2.0](#changes-with-njs-020)
    - [Changes with njs 0.1.15](#changes-with-njs-0115)
    - [Changes with njs 0.1.14](#changes-with-njs-0114)
    - [Changes with njs 0.1.13](#changes-with-njs-0113)
    - [Changes with njs 0.1.12](#changes-with-njs-0112)
    - [Changes with njs 0.1.11](#changes-with-njs-0111)
    - [Changes with njs 0.1.10](#changes-with-njs-0110)
    - [Changes with njs 0.1.9](#changes-with-njs-019)
    - [Changes with njs 0.1.8](#changes-with-njs-018)
    - [Changes with njs 0.1.7](#changes-with-njs-017)
    - [Changes with njs 0.1.6](#changes-with-njs-016)

---

## Changes

#### Changes with njs 1.0.0

Release Date: 23 June 2026

nginx modules:

-   Improvement: aligned HTTP, Stream, and [Fetch](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) exception classes between the njs and QuickJS engines. API misuse is now reported as `TypeError` and status bounds violations as `RangeError`.
    
-   Improvement: rejected unsafe request targets, methods, and header values in [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) before request serialization.
    
-   Bugfix: fixed a heap use-after-free in [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#r_subrequest) when the client closed the connection before the background subrequest completed. The issue was introduced in [75d6b61](https://github.com/nginx/njs/commit/75d6b61) ([0.9.5](https://nginx.org/en/docs/njs/changes.html#njs0.9.5)).
    
-   Bugfix: fixed a worker segfault while reading a request header that nginx registers without a dedicated slot, such as `Proxy-Connection`, via [r.headersIn](https://nginx.org/en/docs/njs/reference.html#r_headers_in).
    
-   Bugfix: excluded unverified-TLS and dynamic-proxy connections from the [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) [keepalive](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_keepalive) cache and validated cached connections before reuse.
    
-   Bugfix: fixed Content-Length truncation for very large request bodies and a missing CONNECT terminator for proxies configured without credentials in [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Bugfix: fixed leaks of promises, events, and init property values on [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) failure paths in the QuickJS engine.
    
-   Bugfix: fixed missing fetch event cleanup when the resolver failed to start.
    
-   Bugfix: fixed an out-of-bounds read of a short fetch proxy URL.
    
-   Bugfix: fixed request body truncation in [r.readRequestJSON()](https://nginx.org/en/docs/njs/reference.html#r_read_request_json) for bodies containing invalid UTF-8 in the QuickJS engine.
    
-   Bugfix: fixed an out-of-bounds read while loading a shared dictionary state file.
    
-   Bugfix: set a pending exception when `sendHeader()`, `send()`, and `finish()` fail in the njs HTTP handlers.
    
-   Bugfix: fixed the variable value state after a stream variable storage allocation failure.
    

Core:

-   Improvement: bounded string-producing chained-buffer growth, so that exceeding the maximum string length raises a catchable `RangeError("invalid string length")` instead of exhausting worker memory.
    
-   Improvement: aligned built-in exception classes (XML, console, TextEncoder, TextDecoder, Buffer, fs.Stats) between the njs and QuickJS engines.
    
-   Bugfix: fixed an infinite loop while inflating a zlib stream that requires a dictionary in the QuickJS engine.
    
-   Bugfix: fixed an infinite loop in `Buffer.prototype.fill()` with a zero-length typed array source.
    
-   Bugfix: fixed a use-after-free in `Array.prototype.sort()` when a getter invoked for a hole grows the array.
    
-   Bugfix: fixed type confusion in `Buffer.concat()` when a list element getter returns a typed array during validation but not during the copy.
    
-   Bugfix: fixed an out-of-bounds access in the variable-length Buffer `readInt`/`writeInt` methods with a zero byteLength.
    
-   Bugfix: fixed an out-of-bounds read in `Buffer.prototype.toString()` when start was greater than end.
    
-   Bugfix: fixed `Array.prototype.slice()` of large arrays returning wrong results in the non-fast keys path.
    
-   Bugfix: fixed the typed array constructor, `slice()`, `toReversed()`, and `toSorted()` ignoring the source view byte offset in the same-type fast path.
    
-   Bugfix: fixed Buffer allocation length checks for lengths greater than or equal to 2^32 on 32-bit platforms.
    
-   Bugfix: fixed the `Buffer.from()` typed-array source offset for multi-byte element types in the QuickJS engine.
    
-   Bugfix: fixed Buffer float access alignment.
    
-   Bugfix: fixed an out-of-bounds read in a parser string escape lookahead.
    

#### Changes with njs 0.9.9

Release Date: 19 May 2026

nginx modules:

-   Security: a heap buffer overflow might occur in a worker process when the [js\_fetch\_proxy](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_proxy) directive value contains nginx variables derived from the client request ( [`$http_*`](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_http_), [`$arg_*`](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_arg_), [`$cookie_*`](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_cookie_), etc.) and the location's JS handler invokes [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) ([CVE-2026-8711](https://www.cve.org/CVERecord?id=CVE-2026-8711)). The issue was introduced in [dea83189](https://github.com/nginx/njs/commit/dea83189) ([0.9.4](https://nginx.org/en/docs/njs/changes.html#njs0.9.4)).
    
-   Feature: added [js\_access](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_access) directive.
    
-   Feature: added [r.readRequestText()](https://nginx.org/en/docs/njs/reference.html#r_read_request_text), [r.readRequestArrayBuffer()](https://nginx.org/en/docs/njs/reference.html#r_read_request_array_buffer), and [r.readRequestJSON()](https://nginx.org/en/docs/njs/reference.html#r_read_request_json) async methods that read the request body, available in [js\_access](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_access) and [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content) directives.
    
-   Feature: added [r.readRequestForm()](https://nginx.org/en/docs/njs/reference.html#r_read_request_form) async method that parses the request body submitted from an HTML form (`application/x-www-form-urlencoded` and `multipart/form-data`) and returns a structured accessor object. The method is available in [js\_access](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_access) and [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content) directives.
    
-   Feature: added [jsVarNames()](https://nginx.org/en/docs/njs/reference.html#r_js_var_names) method. The method returns the names of variables declared with [js\_var](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_var).
    

Core:

-   Bugfix: fixed evaluation order of call arguments with side effects. Previously, an earlier argument could observe a later argument's mutation, e.g. `f(a, a = 2)` passed `2` as both arguments. The issue was introduced in [fd5e523f](https://github.com/nginx/njs/commit/fd5e523f) ([0.9.7](https://nginx.org/en/docs/njs/changes.html#njs0.9.7)).
    

#### Changes with njs 0.9.8

Release Date: 23 April 2026

nginx modules:

-   Bugfix: fixed loading of the built-in `crypto` module. The issue was introduced in [3185ce81](https://github.com/nginx/njs/commit/3185ce81) ([0.9.7](https://nginx.org/en/docs/njs/changes.html#njs0.9.7)).
    

#### Changes with njs 0.9.7

Release Date: 21 April 2026

nginx modules:

-   Feature: [improved](https://github.com/nginx/njs/commit/98342797) [shared dict](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone) eviction strategy.
    
-   Feature: added [ttl()](https://nginx.org/en/docs/njs/reference.html#dict_ttl) method to [shared dictionaries](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone).
    
-   Bugfix: removed spurious `js vm init` notice log emitted during configuration parsing.
    
-   Bugfix: removed [shared dict](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone) expiration from read-locked paths.
    
-   Bugfix: fixed double-free in [shared dict](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone) update with eviction.
    
-   Bugfix: fixed per-entry TTL reset on [incr()](https://nginx.org/en/docs/njs/reference.html#dict_incr) calls.
    

Core:

-   Feature: added Ed25519 and X25519 support for [WebCrypto](https://nginx.org/en/docs/njs/reference.html#builtin_crypto).
    
-   Feature: added [wrapKey()](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_wrap_key) and [unwrapKey()](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_unwrap_key) support for [WebCrypto](https://nginx.org/en/docs/njs/reference.html#builtin_crypto).
    
-   Feature: added [crypto.randomUUID()](https://nginx.org/en/docs/njs/reference.html#crypto_random_uuid).
    
-   Feature: allowed `await` expressions in tagged templates and as call arguments.
    
-   Improvement: switched to OpenSSL EVP for hashing in the built-in [crypto](https://nginx.org/en/docs/njs/reference.html#crypto) module.
    
-   Bugfix: fixed call argument evaluation.
    

#### Changes with njs 0.9.6

Release Date: 3 March 2026

nginx modules:

-   Bugfix: fixed expire field truncation in [shared dict](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone) state files. Millisecond timestamps were silently truncated to 10 digits, making restored entries appear expired on restart.
    
-   Bugfix: suppressed slab log\_nomem for evict [shared dict](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone) zones. When evict is enabled, memory allocation failures are expected and handled by evicting old entries.
    
-   Bugfix: fixed stack trace for [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) exceptions for [qjs](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine.
    

Core:

-   Feature: added optional chaining support.
    
-   Feature: added nullish coalescing assignment operator (`??=`).
    
-   Feature: added logical assignment operators (`||=` and `&&=`).
    
-   Improvement: aligned `SyntaxError` reporting with other JS engines. Previously, file name was a part of the error message. Now it is reported as "stack" property.
    
-   Improvement: improved `Error.stack` traces. Stack traces are now attached in error constructors. Performance of `Error.stack` is improved by ~100 times.
    
-   Bugfix: fixed string offset map corruption in scope values hash. The issue caused SEGV/SIGBUS crashes for multi-byte UTF-8 string constants with more than 32 characters when accessing a character at index >= 32 (e.g. via `.replace()` or bracket notation). The issue was introduced in [e7caa46d](https://github.com/nginx/njs/commit/e7caa46d) ([0.9.5](https://nginx.org/en/docs/njs/changes.html#njs0.9.5)).
    
-   Bugfix: fixed heap-buffer-overflow in atom hash caused by `Symbol()`.
    
-   Bugfix: fixed WebCrypto [importKey()](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_import_key) crash with mismatched JWK key type.
    
-   Bugfix: fixed interactive mode detection for piped stdin.
    
-   Bugfix: fixed build on MacOS.
    
-   Bugfix: fixed PTR macro compatibility with newer BFD library.
    
-   Bugfix: auto/cc: use portable `command -v` instead of `which`. Thanks to Zurab Kvachadze.
    

#### Changes with njs 0.9.5

Release Date: 13 January 2026

nginx modules:

-   Feature: added native module support for [qjs](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_load_http_native_module) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_load_stream_native_module).
    
-   Bugfix: fixed [js\_body\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter) with multiple chunks for [qjs](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine.
    
-   Bugfix: fixed `buffer_type` inheritance in if blocks for [js\_body\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter). Previously, when `js_body_filter` was used inside an if block, the data parameter received `Buffer` type instead of the expected `String` type.
    
-   Bugfix: fixed [js\_body\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter) when data is not in memory. Previously, when upstream data was delivered from nginx cache, `js_body_filter` was not able to process it correctly.
    
-   Bugfix: improved [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#subrequest) error handling. Fixed a problem of a lost write event when the njs handler making `r.subrequest()` is called from a lua handler as a subrequest.
    

Core:

-   Bugfix: fixed `XMLAttr` object. Pointer to `xmlAttr` could become invalid when the parent `XMLNode` was modified.
    
-   Bugfix: fixed `XMLNode` update.
    
-   Bugfix: fixed `ArrayBuffer` with detached buffers.
    
-   Bugfix: added missing detached array checks.
    
-   Bugfix: fixed [fs.mkdir()](https://nginx.org/en/docs/njs/reference.html#fs_mkdir) and friends.
    
-   Bugfix: fixed path restoration in [fs.mkdir()](https://nginx.org/en/docs/njs/reference.html#fs_mkdir) and friends on error.
    
-   Bugfix: fixed missed allocation check in promise code.
    

#### Changes with njs 0.9.4

Release Date: 28 October 2025

nginx modules:

-   Feature: added HTTP forward proxy support for [ngx.fetch()](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) API.
    

Core:

-   Bugfix: fixed [r.subrequest()](https://nginx.org/en/docs/njs/reference.html#subrequest) to a location with JS handler for QuickJS. The bug became visible after [bellard/quickjs@42eb2795](https://github.com/bellard/quickjs/commit/42eb2795).
    

#### Changes with njs 0.9.3

Release Date: 07 October 2025

nginx modules:

-   Bugfix: fixed heap-use-after-free while module loading.
    
-   Bugfix: fixed heap-use-after-free in `js_set` handler used in log phase. The issue was introduced in [04f6dfb](https://github.com/nginx/njs/commit/04f6dfb) ([0.9.2](https://nginx.org/en/docs/njs/changes.html#njs0.9.2)).
    

#### Changes with njs 0.9.2

Release Date: 23 September 2025

nginx modules:

-   Feature: added HTTP keepalive support for [`ngx.fetch()`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) API.
    
-   Improvement: added configure time check when the `js_import` directive is not specified for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import) or [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_import).
    
-   Bugfix: fixed merging of `js_path` directives for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_path) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_path).
    
-   Bugfix: fixed building when the [ngx\_http\_ssl\_module](https://nginx.org/en/docs/http/ngx_http_ssl_module.html) and [ngx\_stream\_ssl\_module](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html) modules are unavailable.
    

Core:

-   Change: increased the default stack size to 160k for njs VM.
    
-   Feature: added `njs.on('exit')` API for the [qjs](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine.
    
-   Improvement: optimized memory consumption while streaming in [qjs](https://nginx.org/en/docs/njs/engine.html#quickjs_engine).
    
-   Bugfix: fixed building [qjs](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine with clang 19.
    
-   Bugfix: fixed building with GCC 15 and O3 optimization level.
    

#### Changes with njs 0.9.1

Release Date: 10 Jul 2025

nginx modules:

-   Feature: added [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) for [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added state file for a [shared dictionary](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone).
    
-   Bugfix: fixed handling of Content-Length header when a body is provided in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Bugfix: fixed [qjs](https://nginx.org/en/docs/njs/engine.html) engine after [bellard/quickjs@458c34d2](https://github.com/bellard/quickjs/commit/458c34d).
    
-   Bugfix: fixed NULL pointer dereference when processing `If-Match` and `If-Unmodified-Since` headers.
    

Core:

-   Feature: added ECDH support for [WebCrypto](https://nginx.org/en/docs/njs/reference.html#builtin_crypto).
    
-   Improvement: reduced memory consumption by the object hash. The new hash uses 42% less memory per element.
    
-   Improvement: reduced memory consumption for concatenation of numbers and strings.
    
-   Improvement: reduced memory consumption of `String.prototype.concat()` with scalar values.
    
-   Bugfix: fixed segfault in `njs_property_query()`. The issue was introduced in [b28e50b1](https://github.com/nginx/njs/commit/b28e50b1) (0.9.0).
    
-   Bugfix: fixed Function constructor template injection.
    
-   Bugfix: fixed GCC compilation with O3 optimization level.
    
-   Bugfix: fixed `constant is too large` for 'long' warning on MIPS `-mabi=n32`.
    
-   Bugfix: fixed compilation with GCC 4.1.
    
-   Bugfix: fixed `%TypedArray%.from()` with the buffer is detached by the mapper.
    
-   Bugfix: fixed `%TypedArray%.prototype.slice()` with overlapping buffers.
    
-   Bugfix: fixed handling of detached buffers for typed arrays.
    
-   Bugfix: fixed frame saving for async functions with closures.
    
-   Bugfix: fixed RegExp compilation of patterns with escaped '\[' characters.
    

#### Changes with njs 0.9.0

Release Date: 06 May 2025

Core:

-   Feature: refactored handling of built-in strings, symbols, and small integers. Performance improvements (arewefastyet/benchmarks/v8-v7 benchmark):
    
    -   Richards: +57% (631 → 989)
        
    -   Crypto: +7% (1445 → 1551)
        
    -   RayTrace: +37% (562 → 772)
        
    -   NavierStokes: +20% (2062 → 2465)
        
    -   Overall score: +29% (1014 → 1307)
        
    
-   Bugfix: fixed handling of undefined values of a captured group in `RegExp.prototype[Symbol.split]()`.
    
-   Bugfix: fixed `GCC 15` build error with `-Wunterminated-string-initialization`.
    

#### Changes with njs 0.8.10

Release Date: 08 April 2025

nginx modules:

-   Feature: reading [`r.requestText`](https://nginx.org/en/docs/njs/reference.html#r_request_text) or [`r.requestBuffer`](https://nginx.org/en/docs/njs/reference.html#r_request_buffer) from a temporary file. Previously, an exception was thrown when accessing [`r.requestText`](https://nginx.org/en/docs/njs/reference.html#r_request_text) or [`r.requestBuffer`](https://nginx.org/en/docs/njs/reference.html#r_request_buffer) if the size of the client request body exceeded [`client_body_buffer_size`](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_buffer_size).
    
-   Improvement: improved reporting of unhandled promise rejections.
    
-   Bugfix: fixed name corruption in variables and headers processing.
    
-   Bugfix: fixed [`incr()`](https://nginx.org/en/docs/njs/reference.html#dict_incr) method of a shared dictionary with an empty init argument for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Bugfix: accepting response headers with underscore characters in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    

Core:

-   Change: fixed [`serializeToString()`](https://nginx.org/en/docs/njs/reference.html#xml_serialize_tostring). Previously, [`serializeToString()`](https://nginx.org/en/docs/njs/reference.html#xml_serialize_tostring) was [`exclusiveC14n()`](https://nginx.org/en/docs/njs/reference.html#xml_exclusivec14n) which returned a string instead of Buffer. According to the published documentation, it should be [`c14n()`](https://nginx.org/en/docs/njs/reference.html#xml_c14n).
    
-   Feature: added [`WebCrypto`](https://nginx.org/en/docs/njs/reference.html#builtin_crypto) API for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added [`TextEncoder`](https://nginx.org/en/docs/njs/reference.html#textencoder) and [`TextDecoder`](https://nginx.org/en/docs/njs/reference.html#textdecoder) for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added [`querystring`](https://nginx.org/en/docs/njs/reference.html#querystring) module for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added [`crypto`](https://nginx.org/en/docs/njs/reference.html#crypto) module for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added [`xml`](https://nginx.org/en/docs/njs/reference.html#xml) module for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added support for the `QuickJS-NG` library.
    
-   Bugfix: fixed [`Buffer.concat()`](https://nginx.org/en/docs/njs/reference.html#buffer_concat) with a single argument in [QuickJS](https://nginx.org/en/docs/njs/engine.html).
    
-   Bugfix: added missed syntax error for `await` in template literal.
    
-   Bugfix: fixed non-NULL terminated strings formatting in exceptions for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Bugfix: fixed compatibility with recent change in [QuickJS](https://nginx.org/en/docs/njs/engine.html) and `QuickJS-NG`.
    

#### Changes with njs 0.8.9

Release Date: 14 January 2025

nginx modules:

-   Bugfix: removed extra VM creation per server. Previously, when `js_import` was declared in [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import) or [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_import) blocks, an extra copy of the VM instance was created for each server block. This was not needed and consumed a lot of memory for configurations with many server blocks. This issue was introduced in [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6) and was partially fixed for location blocks only in [0.8.7](https://nginx.org/en/docs/njs/changes.html#njs0.8.7).
    

Core:

-   Feature: implemented [fs](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    

#### Changes with njs 0.8.8

Release Date: 10 December 2024

nginx modules:

-   Feature: implemented [shared dictionary](https://nginx.org/en/docs/njs/reference.html#ngx_shared) for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Improvement: [js\_preload\_object](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_preload_object) is refactored.
    
-   Bugfix: fixed limit rated output.
    
-   Bugfix: optimized use of SSL contexts for the [js\_fetch\_trusted\_certificate](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_trusted_certificate) directive.
    

Core:

-   Feature: implemented [process object](https://nginx.org/en/docs/njs/reference.html#process) for the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: implemented the [process.kill()](https://nginx.org/en/docs/njs/reference.html#process_kill) method.
    
-   Bugfix: fixed XML tests with [libxml2](https://gitlab.gnome.org/GNOME/libxml2) 2.13 and later.
    
-   Bugfix: fixed promise resolving when Promise is inherited.
    
-   Bugfix: fixed absolute scope in cloned VMs.
    

#### Changes with njs 0.8.7

Release Date: 22 October 2024

nginx modules:

-   Bugfix: eliminated unnecessary VM creation. Previously, njs consumed memory proportionally to the number of nginx locations. The issue was introduced in [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6).
    
-   Improvement: added strict syntax validation for [js\_body\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter).
    
-   Improvement: improved error messages for module loading failures.
    

Core:

-   Feature: implemented [`fs.readlinkSync()`](https://nginx.org/en/docs/njs/reference.html#fs_readlinksync).
    
-   Improvement: implemented lazy stack symbolization.
    
-   Bugfix: fixed heap-buffer-overflow in `Buffer.prototype.indexOf()`. The issue was introduced in [0.8.6](https://nginx.org/en/docs/njs/changes.html#njs0.8.6).
    
-   Bugfix: fixed `Buffer.prototype.lastIndexOf()` when \`from\` is provided.
    

#### Changes with njs 0.8.6

Release Date: 02 October 2024

nginx modules:

-   Feature: introduced the [QuickJS](https://nginx.org/en/docs/njs/engine.html) engine.
    
-   Feature: added optional `nocache` flag for the `js_set` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_set). Thanks to Thomas P.
    
-   Feature: exposed [capture group variables](https://nginx.org/en/docs/njs/reference.html#r_variables) in the HTTP module. Thanks to Thomas P.
    

Core:

-   Feature: added `Buffer` module for the QuickJS engine.
    
-   Bugfix: fixed handling of empty labelled statement in a function.
    
-   Bugfix: fixed `Function` constructor handling when called without arguments.
    
-   Bugfix: fixed `Buffer.prototype.writeInt8()` and friends.
    
-   Bugfix: fixed `Buffer.prototype.writeFloat()` and friends.
    
-   Bugfix: fixed `Buffer.prototype.lastIndexOf()`.
    
-   Bugfix: fixed `Buffer.prototype.write()`.
    
-   Bugfix: fixed maybe-uninitialized warnings in error creation.
    
-   Bugfix: fixed `ctx.codepoint` initialization in UTF-8 decoding.
    
-   Bugfix: fixed `length` initialization in `Array.prototype.pop()`.
    
-   Bugfix: fixed handling of `encode` arg in `fs.readdir()` and `fs.realpath()`.
    

#### Changes with njs 0.8.5

Release Date: 25 June 2024

nginx modules:

-   Change: bytes invalid in UTF-8 encoding are converted into the replacement character in:
    
    -   [`r.variables.var`](https://nginx.org/en/docs/njs/reference.html#r_variables), [`r.requestText`](https://nginx.org/en/docs/njs/reference.html#r_request_text), [`r.responseText`](https://nginx.org/en/docs/njs/reference.html#r_response_text), [`s.variables.var`](https://nginx.org/en/docs/njs/reference.html#s_variables),
        
    -   the `data` argument of the [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on) callback with `upload` or `download` event types,
        
    -   the `data` argument of the [`js_body_filter`](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter) directive.
        
    
    When working with binary data, use:
    
    -   [`r.rawVariables.var`](https://nginx.org/en/docs/njs/reference.html#r_raw_variables), [`r.requestBuffer`](https://nginx.org/en/docs/njs/reference.html#r_request_buffer), [`r.responseBuffer`](https://nginx.org/en/docs/njs/reference.html#r_response_buffer), [`s.rawVariables.var`](https://nginx.org/en/docs/njs/reference.html#s_raw_variables),
        
    -   the `upstream` or `downstream` event type for [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on),
        
    -   `buffer_type`\=`*buffer*` for [`js_body_filter`](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter).
        
    
-   Feature: added `timeout` argument for [`add()`](https://nginx.org/en/docs/njs/reference.html#dict_add), [`set()`](https://nginx.org/en/docs/njs/reference.html#dict_set), and [`incr()`](https://nginx.org/en/docs/njs/reference.html#dict_incr) methods of a shared dictionary.
    
-   Bugfix: fixed checking for duplicate [`js_set`](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) variables.
    
-   Bugfix: fixed request `Host` header when the port is non-standard.
    
-   Bugfix: fixed handling of a zero-length request body in [`ngx.fetch()`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) and [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest).
    
-   Bugfix: fixed heap-buffer-overflow in `Headers.get()`.
    
-   Bugfix: fixed [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest) error handling.
    

Core:

-   Feature: added `zlib` module for `QuickJS` engine.
    
-   Bugfix: fixed [`zlib.inflate()`](https://nginx.org/en/docs/njs/reference.html#zlib_inflatesync).
    
-   Bugfix: fixed `String.prototype.replaceAll()` with a zero-length argument.
    
-   Bugfix: fixed `retval` handling after an exception in `Array.prototype.toSpliced()`, `Array.prototype.toReversed()`, `Array.prototype.toSorted()`.
    
-   Bugfix: fixed `RegExp.prototype[@@replace]()` with replacements containing `$'`, `` $` `` and strings with Unicode characters.
    
-   Bugfix: fixed a one-byte overread in `decodeURI()` and `decodeURIComponent()`.
    
-   Bugfix: fixed tracking of argument scope.
    
-   Bugfix: fixed integer overflow in `Date.parse()`.
    

#### Changes with njs 0.8.4

Release Date: 16 April 2024

nginx modules:

-   Feature: the `Server` header for outgoing header can be set.
    
-   Improvement: validating URI and args arguments in [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest).
    
-   Improvement: checking for duplicate [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) variables.
    
-   Bugfix: fixed [`clear()`](https://nginx.org/en/docs/njs/reference.html#dict_clear) method of a shared dictionary without a timeout introduced in [0.8.3](https://nginx.org/en/docs/njs/changes.html#njs0.8.3).
    
-   Bugfix: fixed [`r.send()`](https://nginx.org/en/docs/njs/reference.html#r_send) method of a shared dictionary without a timeout with `Buffer` argument.
    

Core:

-   Feature: added `QuickJS` engine support in CLI.
    
-   Bugfix: fixed [`atob()`](https://nginx.org/en/docs/njs/reference.html#atob) with non-padded `base64` strings.
    

#### Changes with njs 0.8.3

Release Date: 07 February 2024

nginx modules:

-   Bugfix: fixed [`Headers.set()`](https://nginx.org/en/docs/njs/reference.html#headers_set).
    
-   Bugfix: fixed [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) with `Buffer` values.
    
-   Bugfix: fixed [`clear()`](https://nginx.org/en/docs/njs/reference.html#dict_clear) method of a shared dictionary when a timeout is not specified.
    
-   Bugfix: fixed [stub\_status](https://nginx.org/en/docs/http/ngx_http_stub_status_module.html) statistics when [js\_periodic](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_periodic) is enabled.
    

Core:

-   Bugfix: fixed building with [libxml2](https://gitlab.gnome.org/GNOME/libxml2) 2.12 and later.
    
-   Bugfix: fixed `Date` constructor for overflows and with `NaN` values.
    
-   Bugfix: fixed underflow in [`querystring.parse()`](https://nginx.org/en/docs/njs/reference.html#querystring_parse).
    
-   Bugfix: fixed potential buffer overread in `String.prototype.match()`.
    
-   Bugfix: fixed parsing of `for-in` loops.
    
-   Bugfix: fixed parsing of hexadecimal, octal, and binary literals with no digits.
    

#### Changes with njs 0.8.2

Release Date: 24 October 2023

nginx modules:

-   Feature: introduced [console](https://nginx.org/en/docs/njs/reference.html#console) object. The following methods were introduced: [`error()`](https://nginx.org/en/docs/njs/reference.html#console_error), [`info()`](https://nginx.org/en/docs/njs/reference.html#console_info), [`log()`](https://nginx.org/en/docs/njs/reference.html#console_log), [`time()`](https://nginx.org/en/docs/njs/reference.html#console_time), [`timeEnd()`](https://nginx.org/en/docs/njs/reference.html#console_time_end), [`warn()`](https://nginx.org/en/docs/njs/reference.html#console_warn).
    
-   Bugfix: fixed `HEAD` response handling with large Content-Length in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Bugfix: fixed [`items()`](https://nginx.org/en/docs/njs/reference.html#dict_items) method for a shared dictionary.
    
-   Bugfix: fixed [`delete()`](https://nginx.org/en/docs/njs/reference.html#dict_delete) method for a shared dictionary.
    

Core:

-   Feature: extended [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) module. Added [`fs.existsSync()`](https://nginx.org/en/docs/njs/reference.html#fs_existssync).
    
-   Bugfix: fixed [`xml`](https://nginx.org/en/docs/njs/reference.html#xml) module. Broken XML exception handling in [`xml.parse()`](https://nginx.org/en/docs/njs/reference.html#xml_parse) method was fixed.
    
-   Bugfix: fixed `Regexp.prototype.exec()` with global regexp and Unicode input.
    

#### Changes with njs 0.8.1

Release Date: 12 September 2023

nginx modules:

-   Feature: introduced the `js_periodic` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_periodic) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_periodic) that allows specifying a JS handler to run at regular intervals.
    
-   Feature: implemented [`items()`](https://nginx.org/en/docs/njs/reference.html#dict_items) method of a [shared dictionary](https://nginx.org/en/docs/njs/reference.html#ngx_shared). The method returns all the non-expired key-value pairs.
    
-   Bugfix: fixed [`size()`](https://nginx.org/en/docs/njs/reference.html#dict_size) and [`keys()`](https://nginx.org/en/docs/njs/reference.html#dict_keys) methods of a [shared dictionary](https://nginx.org/en/docs/njs/reference.html#ngx_shared).
    
-   Bugfix: fixed erroneous exception in [`r.internalRedirect()`](https://nginx.org/en/docs/njs/reference.html#r_internal_redirect) introduced in [0.8.0](https://nginx.org/en/docs/njs/changes.html#njs0.8.0).
    

Core:

-   Bugfix: fixed incorrect order of keys in `Object.getOwnPropertyNames()`.
    

#### Changes with njs 0.8.0

Release Date: 06 July 2023

nginx modules:

-   Change: removed special treatment of forbidden headers in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) introduced in [0.7.10](https://nginx.org/en/docs/njs/changes.html#njs0.7.10).
    
-   Change: removed deprecated since [0.5.0](https://nginx.org/en/docs/njs/changes.html#njs0.5.0) [`r.requestBody`](https://nginx.org/en/docs/njs/reference.html#r_request_body) and [`r.responseBody`](https://nginx.org/en/docs/njs/reference.html#r_response_body) in [`http`](https://nginx.org/en/docs/http/ngx_http_js_module.html) module.
    
-   Change: throwing an exception in [`r.internalRedirect()`](https://nginx.org/en/docs/njs/reference.html#r_internal_redirect) while filtering in [`http`](https://nginx.org/en/docs/http/ngx_http_js_module.html) module.
    
-   Feature: introduced more global [`nginx`](https://nginx.org/en/docs/njs/reference.html#ngx) properties: [`ngx.build`](https://nginx.org/en/docs/njs/reference.html#ngx_build), [`ngx.conf_file_path`](https://nginx.org/en/docs/njs/reference.html#ngx_conf_file_path), [`ngx.error_log_path`](https://nginx.org/en/docs/njs/reference.html#ngx_error_log_path), [`ngx.prefix`](https://nginx.org/en/docs/njs/reference.html#ngx_prefix), [`ngx.version`](https://nginx.org/en/docs/njs/reference.html#ngx_version), [`ngx.version_number`](https://nginx.org/en/docs/njs/reference.html#ngx_version_number), and [`ngx.worker_id`](https://nginx.org/en/docs/njs/reference.html#ngx_worker_id).
    
-   Feature: introduced the `js_shared_dict_zone` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_shared_dict_zone) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_shared_dict_zone) that allows declaring a dictionary shared between worker processes.
    
-   Feature: introduced global [`nginx.shared`](https://nginx.org/en/docs/njs/reference.html#ngx_shared) methods and properties for working with shared dictionaries.
    
-   Improvement: added compile-time options to disable njs modules. For example, to disable libxslt-related code:
    
    ```
    NJS_LIBXSLT=NO ./configure  .. --add-module=/path/to/njs/module
    ```
    
-   Bugfix: fixed [`r.status`](https://nginx.org/en/docs/njs/reference.html#r_status) setter when filtering in [`http`](https://nginx.org/en/docs/http/ngx_http_js_module.html) module.
    
-   Bugfix: fixed setting of Location header in [`http`](https://nginx.org/en/docs/http/ngx_http_js_module.html) module.
    

Core:

-   Change: native methods are provided with `retval` argument. This change breaks compatibility with C extension for njs requiring the modification of the code.
    
-   Change: non-compliant deprecated String methods were removed. The following methods were removed: [`String.bytesFrom()`](https://nginx.org/en/docs/njs/reference.html#string_bytesfrom), [`String.prototype.fromBytes()`](https://nginx.org/en/docs/njs/reference.html#string_frombytes), [`String.prototype.fromUTF8()`](https://nginx.org/en/docs/njs/reference.html#string_fromutf8), [`String.prototype.toBytes()`](https://nginx.org/en/docs/njs/reference.html#string_tobytes), [`String.prototype.toUTF8()`](https://nginx.org/en/docs/njs/reference.html#string_toutf8), [``String.prototype.toString(`*encoding*`)``](https://nginx.org/en/docs/njs/reference.html#string_toutf8).
    
-   Change: removed support for building with GNU readline.
    
-   Feature: added ES13-compliant `Array` methods: `Array.from()`, `Array.prototype.toSorted()`, `Array.prototype.toSpliced()`, `Array.prototype.toReversed()`.
    
-   Feature: added ES13-compliant `TypedArray` methods: `%TypedArray%.prototype.toSorted()`, `%TypedArray%.prototype.toSpliced()`, `%TypedArray%.prototype.toReversed()`.
    
-   Feature: added [`CryptoKey`](https://nginx.org/en/docs/njs/reference.html#cryptokey) properties in [WebCrypto API](https://nginx.org/en/docs/njs/reference.html#builtin_crypto). The following properties were added: [`algorithm`](https://nginx.org/en/docs/njs/reference.html#cryptokey_alg), [`extractable`](https://nginx.org/en/docs/njs/reference.html#cryptokey_extractable), [`type`](https://nginx.org/en/docs/njs/reference.html#cryptokey_type), [`usages`](https://nginx.org/en/docs/njs/reference.html#cryptokey_usages).
    
-   Bugfix: fixed `retval` of [`сrypto.getRandomValues()`](https://nginx.org/en/docs/njs/reference.html#crypto_get_random_values).
    
-   Bugfix: fixed evaluation of computed property names with function expressions.
    
-   Bugfix: fixed implicit name for a function expression declared in arrays.
    
-   Bugfix: fixed parsing of `for-in` loops.
    
-   Bugfix: fixed `Date.parse()` with ISO-8601 format and UTC time offset.
    

#### Changes with njs 0.7.12

Release Date: 10 April 2023

nginx modules:

-   Bugfix: fixed `Headers()` constructor in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    

Core:

-   Feature: added [`Hash.copy()`](https://nginx.org/en/docs/njs/reference.html#crypto_hash_copy) method in [crypto](https://nginx.org/en/docs/njs/reference.html#crypto) module.
    
-   Feature: added [zlib](https://nginx.org/en/docs/njs/reference.html#zlib) module.
    
-   Improvement: added support for `export {name as default}` statement.
    
-   Bugfix: fixed `Number` constructor according to the spec.
    

#### Changes with njs 0.7.11

Release Date: 09 March 2023

nginx modules:

-   Bugfix: added missed linking with [libxml2](https://gitlab.gnome.org/GNOME/libxml2) for the dynamic module. The bug was introduced in [0.7.10](https://nginx.org/en/docs/njs/changes.html#njs0.7.10).
    

Core:

-   Feature: added [XMLNode API](https://nginx.org/en/docs/njs/reference.html#xml_node) to modify XML documents.
    
-   Change: removed `XML_PARSE_DTDVALID` during parsing of an XML document due to security implications. The issue was introduced in [0.7.10](https://nginx.org/en/docs/njs/changes.html#njs0.7.10). When `XML_PARSE_DTDVALID` is enabled, [libxml2](https://gitlab.gnome.org/GNOME/libxml2) parses and executes external entities present inside an XML document.
    
-   Bugfix: fixed the detection of `await` in arguments.
    
-   Bugfix: fixed `Error()` instance dumping when “`name`” prop is not primitive.
    
-   Bugfix: fixed array instance with a `getter` property dumping.
    
-   Bugfix: fixed `njs_object_property()` with `NJS_WHITEOUT` properties.
    
-   Bugfix: fixed `func` instance dumping with “`name`” as getter.
    
-   Bugfix: fixed attaching of a stack to an error object.
    
-   Bugfix: fixed `String.prototype.replace()` with replacement containing “`$'`”, “`` $` ``”.
    

#### Changes with njs 0.7.10

Release Date: 07 February 2023

nginx modules:

-   Feature: added [`Request`](https://nginx.org/en/docs/njs/reference.html#request), [`Response`](https://nginx.org/en/docs/njs/reference.html#response), and [`Headers`](https://nginx.org/en/docs/njs/reference.html#headers) ctors in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Bugfix: fixed nginx logger callback for calls in the master process.
    

Core:

-   Feature: added signal support in CLI.
    
-   Feature: added [`xml`](https://nginx.org/en/docs/njs/reference.html#xml) module for working with XML documents.
    
-   Feature: extended support for symmetric and asymmetric keys in WebCrypto. Most notably `JWK` format for [`importKey()`](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_import_key) was added.
    
-   Feature: extended support for symmetric and asymmetric keys in [WebCrypto API](https://nginx.org/en/docs/njs/reference.html#builtin_crypto). Most notably `JWK` format for [`importKey()`](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_import_key) was added. [`generateKey()`](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_generate_key) and [`exportKey()`](https://nginx.org/en/docs/njs/reference.html#crypto_subtle_export_key) were also implemented.
    
-   Feature: added `String.prototype.replaceAll()`.
    
-   Bugfix: fixed `for(expr1;` conditional syntax error handling.
    
-   Bugfix: `Object.values()` and `Object.entries()` with external objects.
    
-   Bugfix: fixed `RegExp.prototype[@@replace]()`.
    

#### Changes with njs 0.7.9

Release Date: 17 November 2022

nginx modules:

-   Bugfix: fixed [`Fetch`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) Response prototype reinitialization. When at least one `js_import` directive was declared in both [HTTP](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import) and [Stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_import), [`ngx.fetch()`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) returned inapproriate response in Stream. The bug was introduced in [0.7.7](https://nginx.org/en/docs/njs/changes.html#njs0.7.7).
    

Core:

-   Bugfix: fixed `String.prototype.replace(re)` if `re.exec()` returns non-flat array.
    
-   Bugfix: fixed `Array.prototype.fill()` when `start` object changes `this`.
    
-   Bugfix: fixed description for [`fs.mkdir()`](https://nginx.org/en/docs/njs/reference.html#fs_mkdirsync) and [`fs.rmdir()`](https://nginx.org/en/docs/njs/reference.html#fs_rmdirsync) methods.
    
-   Bugfix: fixed `%TypedArray%.prototype.set(s)` when `s` element changes `this`.
    
-   Bugfix: fixed `Array.prototype.splice(s,d)` when `d` resizes `this` during eval.
    
-   Bugfix: fixed `for-in` loop with left and right hand side expressions.
    

#### Changes with njs 0.7.8

Release Date: 25 October 2022

nginx modules:

-   Feature: added [js\_preload\_object](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_preload_object) directive.
    
-   Feature: added [`ngx.conf_prefix`](https://nginx.org/en/docs/njs/reference.html#ngx_conf_prefix) property.
    
-   Feature: added [`s.sendUpstream()`](https://nginx.org/en/docs/njs/reference.html#s_send_upstream) and [`s.sendDownstream()`](https://nginx.org/en/docs/njs/reference.html#s_send_downstream) in [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html) module.
    
-   Feature: added support for `HEAD` method in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Improvement: improved `async` callback support for [`s.send()`](https://nginx.org/en/docs/njs/reference.html#s_send) in [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html) module.
    

Core:

-   Feature: added `name` instance property for a function object.
    
-   Feature: added [`njs.memoryStats`](https://nginx.org/en/docs/njs/reference.html#njs_memory_stats) object.
    
-   Bugfix: fixed `String.prototype.trimEnd()` with unicode string.
    
-   Bugfix: fixed `Object.freeze()` with fast arrays.
    
-   Bugfix: fixed `Object.defineProperty()` with fast arrays.
    
-   Bugfix: fixed `async` token as a property name of an object.
    
-   Bugfix: fixed property set instruction when key modifies base binding.
    
-   Bugfix: fixed complex assignments.
    
-   Bugfix: fixed handling of unhandled promise rejection.
    
-   Bugfix: fixed process.env when duplicate environ variables are present.
    
-   Bugfix: fixed double declaration detection in modules.
    
-   Bugfix: fixed bound function calls according to the spec.
    
-   Bugfix: fixed break label for `if` statement.
    
-   Bugfix: fixed labeled empty statements.
    

#### Changes with njs 0.7.7

Release Date: 30 August 2022

nginx modules:

-   Feature: the number of nginx configuration contexts where js directives can be specified is extended.
    
    -   HTTP: the [js\_import](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import), [js\_path](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_path), [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set), and [js\_var](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_var) directives are allowed in `server` and `location` contexts. The [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content), [js\_body\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter) and [js\_header\_filter](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_header_filter) are allowed in [if](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#if) context.
        
    -   Stream: the [js\_import](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import), [js\_path](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_path), [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set), and [js\_var](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_var) are allowed in `server` context.
        
    
-   Feature: added [`r.internal`](https://nginx.org/en/docs/njs/reference.html#r_internal) property.
    
-   Bugfix: fixed reading response body in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Bugfix: fixed [js\_fetch\_timeout](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_fetch_timeout) in [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html).
    
-   Bugfix: fixed socket leak with `0` fetch timeout.
    

Core:

-   Feature: extended [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) module. Added [`fs.openSync()`](https://nginx.org/en/docs/njs/reference.html#fs_opensync), [`fs.promises.open()`](https://nginx.org/en/docs/njs/reference.html#fs_promises_open), [`fs.fstatSync()`](https://nginx.org/en/docs/njs/reference.html#fs_fstatsync), [`fs.readSync()`](https://nginx.org/en/docs/njs/reference.html#fs_readsync), [`fs.writeSync()`](https://nginx.org/en/docs/njs/reference.html#fs_writesync_buf).
    
    The following properties of [`FileHandle`](https://nginx.org/en/docs/njs/reference.html#fs_filehandle) are implemented: `fd`, `read()`, `stat()`, `write()`, `close()`.
    
-   Bugfix: fixed `parseInt()`, `parseFloat()`, `Symbol.for()` with no arguments.
    

#### Changes with njs 0.7.6

Release Date: 19 July 2022

nginx modules:

-   Feature: improved [`r.args{}`](https://nginx.org/en/docs/njs/reference.html#r_args) object. Added support for multiple arguments with the same key. Added case sensitivity for keys. Keys and values are percent-decoded now.
    
-   Bugfix: fixed [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out) setter for special headers.
    

Core:

-   Feature: added `Symbol.for()` and `Symbol.keyfor()`.
    
-   Feature: added [`atob()`](https://nginx.org/en/docs/njs/reference.html#atob) and [`btoa()`](https://nginx.org/en/docs/njs/reference.html#btoa) from [WHATWG](https://html.spec.whatwg.org/) spec.
    
-   Bugfix: fixed large non-decimal literals.
    
-   Bugfix: fixed Unicode argument trimming in `parseInt()`.
    
-   Bugfix: fixed `break` instruction in `try-catch` block.
    
-   Bugfix: fixed `async` function declaration in CLI.
    

#### Changes with njs 0.7.5

Release Date: 21 June 2022

nginx modules:

-   Change: adapting to changes in nginx header structures.
    
-   Bugfix: fixed [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out) special getters when value is absent.
    
-   Change: returning undefined value instead of an empty string for `Content-Type` when the header is absent.
    

Core:

-   Bugfix: fixed catching of the exception thrown from an awaited function.
    
-   Bugfix: fixed function value initialization.
    
-   Bugfix: fixed interpreter when await fails.
    
-   Bugfix: fixed typed-array constructor when source array is changed while iterating.
    
-   Bugfix:fixed `String.prototype.replace()` with byte strings.
    
-   Bugfix: fixed template literal from producing byte-strings.
    
-   Bugfix: fixed array iterator with sparse arrays.
    
-   Bugfix: fixed memory free while converting a flat array to a slow array.
    
-   Bugfix: properly handling `NJS_DECLINE` in `promise` native functions.
    
-   Bugfix: fixed working with an array-like object in `Promise.all()` and friends.
    

#### Changes with njs 0.7.4

Release Date: 24 May 2022

nginx modules:

-   Feature: added extended directives for configuring [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch). The following directives were added for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html):
    
    -   [js\_fetch\_timeout](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_timeout),
        
    -   [js\_fetch\_verify](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_verify),
        
    -   [js\_fetch\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_buffer_size),
        
    -   [js\_fetch\_max\_response\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_max_response_buffer_size).
        
    
-   Change: [`r.internalRedirect()`](https://nginx.org/en/docs/njs/reference.html#r_internal_redirect) now accepts escaped URIs.
    
-   Bugfix: fixed [Response](https://nginx.org/en/docs/njs/reference.html#response) parsing with more than 8 headers in [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    

Core:

-   Feature: added [`njs.version_number`](https://nginx.org/en/docs/njs/reference.html#njs_version_number) property.
    
-   Feature: added compatibility with BoringSSL for [WebCrypto API](https://nginx.org/en/docs/njs/reference.html#builtin_crypto).
    
-   Bugfix: fixed `Array.prototype.sort()` when arr size is changed in a comparator.
    
-   Bugfix: fixed `Array.prototype.slice()` with slow `this` argument.
    
-   Bugfix: fixed aggregation methods of `Promise` ctor with array-like object.
    
-   Bugfix: fixed `String.prototype.lastIndexOf()` with Unicode string as `this`.
    
-   Bugfix: fixed `JSON.parse()` when `reviver` function is provided.
    
-   Bugfix: fixed `Object.defineProperty()` when a recursive descriptor is provided.
    
-   Bugfix: fixed `Array.prototype.fill()` for typed-arrays.
    
-   Bugfix: making function expression binding immutable according to the specs.
    
-   Bugfix: fixed redefinition of special props in `Object.defineProperty()`.
    

#### Changes with njs 0.7.3

Release Date: 12 April 2022

Core:

-   Feature: added support of module resolution callback. This feature allows the host environment to control how imported modules are loaded.
    
-   Bugfix: fixed backtraces while traversing imported user modules.
    
-   Bugfix: fixed `Array.prototype.concat()` when `this` is a slow array.
    
-   Bugfix: fixed frame allocation from an awaited frame.
    
-   Bugfix: fixed allocation of large array literals.
    
-   Bugfix: fixed interpreter when `toString` conversion fails.
    

#### Changes with njs 0.7.2

Release Date: 25 January 2022

Core:

-   Bugfix: fixed `Array.prototype.join()` when array is changed while iterating.
    
-   Bugfix: fixed `Array.prototype.slice()` when array is changed while iterating.
    
-   Bugfix: fixed `Array.prototype.concat()` when array is changed while iterating.
    
-   Bugfix: fixed `Array.prototype.reverse()` when array is changed while iterating.
    
-   Bugfix: fixed `Buffer.concat()` with subarrays. Thanks to Sylvain Etienne.
    
-   Bugfix: fixed type confusion bug while resolving promises.
    
-   Bugfix: fixed `Function.prototype.apply()` with large array arguments.
    
-   Bugfix: fixed recursive `async` function calls.
    
-   Bugfix: fixed function redeclaration. The bug was introduced in [0.7.0](https://nginx.org/en/docs/njs/changes.html#njs0.7.0).
    

#### Changes with njs 0.7.1

Release Date: 28 December 2021

nginx modules:

-   Change: the [js\_include](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_include) directive deprecated since [0.4.0](https://nginx.org/en/docs/njs/changes.html#njs0.4.0) was removed.
    
-   Change: PCRE/PCRE2-specific code was moved to the modules. This ensures that njs uses the same RegExp library as nginx.
    

Core:

-   Bugfix: fixed `decodeURI()` and `decodeURIComponent()` with invalid byte strings. The bug was introduced in [0.4.3](https://nginx.org/en/docs/njs/changes.html#njs0.4.3).
    
-   Bugfix: fixed heap-use-after-free in `await` frame. The bug was introduced in [0.7.0](https://nginx.org/en/docs/njs/changes.html#njs0.7.0).
    
-   Bugfix: fixed WebCrypto `sign()` and `verify()` methods with OpenSSL 3.0.
    
-   Bugfix: fixed exception throwing when RegExp match fails. The bug was introduced in [0.1.15](https://nginx.org/en/docs/njs/changes.html#njs0.1.15).
    
-   Bugfix: fixed catching of exception thrown in `try` block of `async` function. The bug was introduced in [0.7.0](https://nginx.org/en/docs/njs/changes.html#njs0.7.0).
    
-   Bugfix: fixed execution of `async` function in synchronous context. The bug was introduced in [0.7.0](https://nginx.org/en/docs/njs/changes.html#njs0.7.0).
    
-   Bugfix: fixed function redeclaration in CLI when interactive mode is on. The bug was introduced in [0.6.2](https://nginx.org/en/docs/njs/changes.html#njs0.6.2).
    
-   Bugfix: fixed `typeof` operator with `DataView` object.
    
-   Bugfix: eliminated information leak in `Buffer.from()`.
    

#### Changes with njs 0.7.0

Release Date: 19 October 2021

nginx modules:

-   Feature: Added [HTTPS](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_fetch_protocols) support for [Fetch API](https://nginx.org/en/docs/njs/reference.html#ngx_fetch).
    
-   Feature: Added `setReturnValue()` method for [http](https://nginx.org/en/docs/njs/reference.html#r_set_return_value) and [stream](https://nginx.org/en/docs/njs/reference.html#s_set_return_value).
    

Core:

-   Feature: introduced `Async/Await` implementation.
    
-   Feature: added [WebCrypto API](https://nginx.org/en/docs/njs/reference.html#builtin_crypto) implementation.
    
-   Bugfix: fixed copying of closures for declared functions. The bug was introduced in [0.6.0](https://nginx.org/en/docs/njs/changes.html#njs0.6.0).
    
-   Bugfix: fixed unhandled `promise` rejection in handle events.
    
-   Bugfix: fixed Response.headers getter in Fetch API.
    

#### Changes with njs 0.6.2

Release Date: 31 August 2021

nginx modules:

-   Bugfix: fixed CPU hog when `js_filter` is registered in both directions.
    

Core:

-   Feature: introduced `AggregateError` implementation.
    
-   Feature: added remaining `Promise` constructor methods. The following methods were added: `Promise.all()`, `Promise.allSettled()`, `Promise.any()`, `Promise.race()`.
    
-   Improvement: removed recursion from code generator.
    
-   Bugfix: fixed rest parameter parsing without binding identifier.
    
-   Bugfix: fixed resolve/reject callback for `Promise.prototype.finally()` .
    
-   Bugfix: fixed `%TypedArray%.prototype.join()` with detached buffer.
    
-   Bugfix: fixed memory leak in interactive shell.
    

#### Changes with njs 0.6.1

Release Date: 29 June 2021

-   Bugfix: fixed `RegExpBuiltinExec()` with UTF-8 only regexps. The bug was introduced in [0.4.2](https://nginx.org/en/docs/njs/changes.html#njs0.4.2).
    
-   Bugfix: fixed parsing of export default declaration with non-assignment expressions. Thanks to Artem S. Povalyukhin.
    

#### Changes with njs 0.6.0

Release Date: 15 June 2021

Core:

-   Feature: added `let` and `const` declaration support.
    
-   Feature: added `RegExp.prototype[Symbol.split]`.
    
-   Feature: added sticky flag support for RegExp.
    
-   Bugfix: fixed heap-buffer-overflow in `String.prototype.lastIndexOf()`.
    
-   Bugfix: fixed `RegExp.prototype.test()` according to the specification.
    
-   Bugfix: fixed `String.prototype.split()` according to the specification.
    
-   Bugfix: fixed use-of-uninitialized-value while tracking rejected promises.
    
-   Bugfix: fixed `njs.dump()` for objects with circular references.
    

#### Changes with njs 0.5.3

Release Date: 30 March 2021

nginx modules:

-   Feature: added the `js_var` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_var) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_var).
    

#### Changes with njs 0.5.2

Release Date: 09 March 2021

nginx modules:

-   Feature: added the [`js_body_filter`](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_body_filter) directive.
    
-   Feature: introduced the [`s.status`](https://nginx.org/en/docs/njs/reference.html#s_status) property for [Stream Session](https://nginx.org/en/docs/njs/reference.html#stream) object.
    

Core:

-   Feature: added [`njs.on`](https://nginx.org/en/docs/njs/reference.html#njs_on) (`exit`) callback support.
    
-   Bugfix: fixed property descriptor reuse for not extensible objects. Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed `Object.freeze()` and friends according to the specification. Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed `Function()` in CLI mode.
    
-   Bugfix: fixed `for-in` iteration of typed array values. Thanks to Artem S. Povalyukhin.
    

#### Changes with njs 0.5.1

Release Date: 16 February 2021

nginx modules:

-   Feature: introduced [`ngx.fetch()`](https://nginx.org/en/docs/njs/reference.html#ngx_fetch) method implementing Fetch API.
    
    The following properties and methods of [`Response`](https://nginx.org/en/docs/njs/reference.html#response) object are implemented: [`arrayBuffer()`](https://nginx.org/en/docs/njs/reference.html#response_arraybuffer), [`bodyUsed`](https://nginx.org/en/docs/njs/reference.html#response_bodyused), [`json()`](https://nginx.org/en/docs/njs/reference.html#response_json), [`headers`](https://nginx.org/en/docs/njs/reference.html#response_headers), [`ok`](https://nginx.org/en/docs/njs/reference.html#response_ok), [`redirect`](https://nginx.org/en/docs/njs/reference.html#response_redirect), [`status`](https://nginx.org/en/docs/njs/reference.html#response_status), [`statusText`](https://nginx.org/en/docs/njs/reference.html#response_statustext), [`text()`](https://nginx.org/en/docs/njs/reference.html#response_text), [`type`](https://nginx.org/en/docs/njs/reference.html#response_type), [`url`](https://nginx.org/en/docs/njs/reference.html#response_url).
    
    Notable limitations: only the `http://` scheme is supported, redirects are not handled.
    
    In collaboration with 洪志道 (Hong Zhi Dao).
    
-   Feature: added the [`js_header_filter`](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_header_filter) directive.
    
-   Bugfix: fixed processing buffered data in body filter in [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html) module.
    

Core:

-   Bugfix: fixed safe mode bypass in `Function` constructor.
    
-   Bugfix: fixed `Date.prototype.toISOString()` with invalid date values.
    

#### Changes with njs 0.5.0

Release Date: 01 December 2020

nginx modules:

-   Feature: introduced global [`ngx`](https://nginx.org/en/docs/njs/reference.html#ngx) object.
    
    The following methods were added:
    
    -   [`ngx.log(level, message)`](https://nginx.org/en/docs/njs/reference.html#ngx_log)
        
    
    The following properties were added:
    
    -   `ngx.INFO`, `ngx.WARN`, `ngx.ERR`.
        
    
-   Feature: added support for `Buffer` object where string is expected.
    
-   Feature: added Buffer version of existing properties.
    
    The following properties were added:
    
    -   [`r.requestBuffer`](https://nginx.org/en/docs/njs/reference.html#r_request_buffer) ([`r.requestBody`](https://nginx.org/en/docs/njs/reference.html#r_request_body)), [`r.responseBuffer`](https://nginx.org/en/docs/njs/reference.html#r_response_buffer) ([`r.responseBody`](https://nginx.org/en/docs/njs/reference.html#r_response_body)), [`r.rawVariables`](https://nginx.org/en/docs/njs/reference.html#r_raw_variables) ([`r.variables`](https://nginx.org/en/docs/njs/reference.html#r_variables)), [`s.rawVariables`](https://nginx.org/en/docs/njs/reference.html#s_raw_variables) ([`s.variables`](https://nginx.org/en/docs/njs/reference.html#s_variables)).
        
    
    The following events were added in the stream module:
    
    -   [`upstream`](https://nginx.org/en/docs/njs/reference.html#s_on) (upload), [`downstream`](https://nginx.org/en/docs/njs/reference.html#s_on) (download).
        
    
-   Improvement: added aliases to existing properties.
    
    The following properties were added:
    
    -   [`r.requestText`](https://nginx.org/en/docs/njs/reference.html#r_request_text) ([`r.requestBody`](https://nginx.org/en/docs/njs/reference.html#r_request_body)), [`r.responseText`](https://nginx.org/en/docs/njs/reference.html#r_response_text) ([`r.responseBody`](https://nginx.org/en/docs/njs/reference.html#r_response_body)).
        
    
-   Improvement: throwing an exception in [`r.internalRedirect()`](https://nginx.org/en/docs/njs/reference.html#r_internal_redirect) for a subrequest.
    
-   Bugfix: fixed promise [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest) with [`error_page`](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page) redirect.
    
-   Bugfix: fixed `promise` events handling.
    

Core:

-   Feature: added `TypeScript` definitions for built-in modules. Thanks to Jakub Jirutka.
    
-   Feature: tracking unhandled `promise` rejection.
    
-   Feature: added initial iterator support. Thanks to Artem S. Povalyukhin.
    
-   Improvement: `TypeScript` definitions are refactored. Thanks to Jakub Jirutka.
    
-   Improvement: added forgotten support for `Object.prototype.valueOf()` in `Buffer.from()`.
    
-   Bugfix: fixed heap-use-after-free in `JSON.parse()`.
    
-   Bugfix: fixed heap-use-after-free in `JSON.stringify()`.
    
-   Bugfix: fixed `JSON.stringify()` for arrays resizable via getters.
    
-   Bugfix: fixed heap-buffer-overflow for `RegExp.prototype[Symbol.replace]`.
    
-   Bugfix: fixed returned value for `Buffer.prototype.write*` functions.
    
-   Bugfix: fixed [`querystring.stringify()`](https://nginx.org/en/docs/njs/reference.html#querystring_stringify). Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed the catch handler for `Promise.prototype.finally()`.
    
-   Bugfix: fixed [`querystring.parse()`](https://nginx.org/en/docs/njs/reference.html#querystring_parse).
    

#### Changes with njs 0.4.4

Release Date: 29 September 2020

nginx modules:

-   Bugfix: fixed location merge.
    
-   Bugfix: fixed [`r.httpVersion`](https://nginx.org/en/docs/njs/reference.html#r_http_version) for HTTP/2.
    

Core:

-   Feature: added support for numeric separators (ES12).
    
-   Feature: added remaining methods for `%TypedArray%.prototype`. The following methods were added: `every()`, `filter()`, `find()`, `findIndex()`, `forEach()`, `includes()`, `indexOf()`, `lastIndexOf()`, `map()`, `reduce()`, `reduceRight()`, `reverse()`, `some()`.
    
-   Feature: added `%TypedArray%` remaining methods. The following methods were added: `from()`, `of()`.
    
-   Feature: added `DataView` object.
    
-   Feature: added `Buffer` object implementation.
    
-   Feature: added support for `ArrayBuffer` in [`TextDecoder.prototype.decode()`](https://nginx.org/en/docs/njs/reference.html#textdecoder_decode)
    
-   Feature: added support for `Buffer` object in [`crypto`](https://nginx.org/en/docs/njs/reference.html#crypto) methods.
    
-   Feature: added support for `Buffer` object in [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) methods.
    
-   Change: [`Hash.prototype.digest()`](https://nginx.org/en/docs/njs/reference.html#crypto_hash_digest) and [`Hmac.prototype.digest()`](https://nginx.org/en/docs/njs/reference.html#crypto_hmac_digest) now return a `Buffer` instance instead of a byte string when encoding is not provided.
    
-   Change: [`fs.readFile()`](https://nginx.org/en/docs/njs/reference.html#readfilesync) and friends now return a `Buffer` instance instead of a byte string when encoding is not provided.
    
-   Bugfix: fixed function `prototype` property handler while setting.
    
-   Bugfix: fixed function `constructor` property handler while setting.
    
-   Bugfix: fixed `String.prototype.indexOf()` for byte strings.
    
-   Bugfix: fixed `RegExpBuiltinExec()` with a global flag and byte strings.
    
-   Bugfix: fixed `RegExp.prototype[Symbol.replace]` the when replacement value is a function.
    
-   Bugfix: fixed [`TextDecoder.prototype.decode()`](https://nginx.org/en/docs/njs/reference.html#textdecoder_decode) with non-zero `TypedArray` offset.
    

#### Changes with njs 0.4.3

Release Date: 11 August 2020

Core:

-   Feature: added [`Query String`](https://nginx.org/en/docs/njs/reference.html#querystring) module.
    
-   Feature: improved [`fs.mkdir()`](https://nginx.org/en/docs/njs/reference.html#fs_mkdirsync) to support recursive directory creation. Thanks to Artem S. Povalyukhin.
    
-   Feature: improved [`fs.rmdir()`](https://nginx.org/en/docs/njs/reference.html#fs_rmdirsync) to support recursive directory removal. Thanks to Artem S. Povalyukhin.
    
-   Feature: introduced UTF-8 decoder according to [WHATWG](https://encoding.spec.whatwg.org/) encoding spec.
    
-   Feature: added [`TextDecoder()`](https://nginx.org/en/docs/njs/reference.html#textdecoder) and [`TextEncoder()`](https://nginx.org/en/docs/njs/reference.html#textencoder) implementation.
    
-   Bugfix: fixed parsing return statement without semicolon.
    
-   Bugfix: fixed `njs_number_to_int32()` for big-endian platforms.
    
-   Bugfix: fixed unit test on big-endian platforms.
    
-   Bugfix: fixed regexp-literals parsing with “`=`” characters.
    
-   Bugfix: fixed pre/post increment/decrement in assignment operations.
    

#### Changes with njs 0.4.2

Release Date: 07 July 2020

Core:

-   Feature: added `RegExp.prototype[Symbol.replace]`.
    
-   Feature: introduced line level backtrace.
    
-   Feature: added `%TypedArray%.prototype.sort()`.
    
-   Feature: extended [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) module. Added [`mkdir()`](https://nginx.org/en/docs/njs/reference.html#fs_mkdirsync), [`readdir()`](https://nginx.org/en/docs/njs/reference.html#fs_readdirsync), [`rmdir()`](https://nginx.org/en/docs/njs/reference.html#fs_rmdirsync), and friends.
    
-   Improvement: parser refactoring.
    
-   Bugfix: fixed TypedScript API description for HTTP headers.
    
-   Bugfix: fixed TypedScript API description for `NjsByteString` type.
    
-   Bugfix: fixed `String.prototype.repeat()` according to the specification.
    
-   Bugfix: fixed `String.prototype.replace()` according to the specification.
    
-   Bugfix: fixed parsing of flags for regexp literals.
    
-   Bugfix: fixed index generation for global objects in generator.
    
-   Bugfix: fixed `%TypedArray%.prototype.copyWithin()` with nonzero byte offset.
    
-   Bugfix: fixed `Array.prototype.splice()` for sparse arrays.
    
-   Bugfix: fixed `Array.prototype.reverse()` for sparse arrays.
    
-   Bugfix: fixed `Array.prototype.sort()` for sparse arrays.
    

#### Changes with njs 0.4.1

Release Date: 19 May 2020

nginx modules:

-   Feature: added support for multi-value headers in [`r.headersIn{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_in).
    
-   Feature: introduced `raw headers` API: [`r.rawHeadersIn[]`](https://nginx.org/en/docs/njs/reference.html#r_raw_headers_in) and [`r.rawHeadersOut[]`](https://nginx.org/en/docs/njs/reference.html#r_raw_headers_out).
    
-   Feature: added [TypeScript](https://nginx.org/en/docs/njs/typescript.html) API description.
    

Core:

-   Bugfix: fixed `Array.prototype.slice()` for sparse arrays.
    

#### Changes with njs 0.4.0

Release Date: 23 April 2020

nginx modules:

-   Feature: added support for multi-value headers in [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out).
    
-   Feature: added `js_import` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_import).
    
-   Improvement: improved iteration over [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out) with special headers.
    
-   Improvement: improved iteration over [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out) with duplicates.
    
-   Change: [`r.responseBody`](https://nginx.org/en/docs/njs/reference.html#r_response_body) property handler now returns `undefined` instead of throwing an exception if the response body is not available.
    

Core:

-   Feature: added script arguments support in CLI.
    
-   Feature: converting externals values to native js objects.
    
-   Bugfix: fixed NULL-pointer dereference in `__proto__` property handler.
    
-   Bugfix: fixed handling of no-newline at the end of the script.
    
-   Bugfix: fixed `RegExp()` constructor with empty pattern and non-empty flags.
    
-   Bugfix: fixed `String.prototype.replace()` when function returns non-string.
    
-   Bugfix: fixed reading of pseudofiles in [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs).
    

#### Changes with njs 0.3.9

Release Date: 03 March 2020

nginx modules:

-   Feature: added detached mode for [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest). Responses to detached subrequests are ignored. Unlike ordinary subrequests, a detached subrequest can be created inside a variable handler.
    

Core:

-   Feature: added `promises` API for [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) module. Thanks to Artem S. Povalyukhin.
    
-   Feature: extended [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) module. Added [`access()`](https://nginx.org/en/docs/njs/reference.html#fs_accesssync), [`symlink()`](https://nginx.org/en/docs/njs/reference.html#fs_symlinksync), [`unlink()`](https://nginx.org/en/docs/njs/reference.html#fs_unlinksync), [`realpath()`](https://nginx.org/en/docs/njs/reference.html#fs_realpathsync), and friends. Thanks to Artem S. Povalyukhin.
    
-   Improvement: introduced memory-efficient ordinary arrays.
    
-   Improvement: lexer refactoring.
    
-   Bugfix: fixed matching of native functions in backtraces.
    
-   Bugfix: fixed callback invocations in [`fs`](https://nginx.org/en/docs/njs/reference.html#njs_api_fs) module. Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed `Object.getOwnPropertySymbols()`.
    
-   Bugfix: fixed heap-buffer-overflow in `njs_json_append_string()`.
    
-   Bugfix: fixed `encodeURI()` and `decodeURI()` according to the specification.
    
-   Bugfix: fixed `Number.prototype.toPrecision()`.
    
-   Bugfix: fixed handling of space argument in `JSON.stringify()`.
    
-   Bugfix: fixed `JSON.stringify()` with `Number()` and `String()` objects.
    
-   Bugfix: fixed Unicode Escaping in `JSON.stringify()` according to specification.
    
-   Bugfix: fixed non-native module importing. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Bugfix: fixed `njs.dump()` with the `Date()` instance in a container.
    

#### Changes with njs 0.3.8

Release Date: 21 January 2020

nginx modules:

-   Feature: added `Promise` support for [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest). If a callback is not provided, [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest) returns an ordinary `Promise` object that resolves to a subrequest response object.
    
-   Change: [`r.parent`](https://nginx.org/en/docs/njs/reference.html#r_parent) property handler now returns `undefined` instead of throwing an exception if a parent object is not available.
    

Core:

-   Feature: added `Promise` support. Implemented according to the specification without: `Promise.all()`, `Promise.allSettled()`, `Promise.race()`.
    
-   Feature: added initial Typed-arrays support. Thanks to Tiago Natel de Moura.
    
-   Feature: added `ArrayBuffer` support. Thanks to Tiago Natel de Moura.
    
-   Feature: added initial `Symbol` support. Thanks to Artem S. Povalyukhin.
    
-   Feature: added externals support for `JSON.stringify()`.
    
-   Feature: added `Object.is()`. Thanks to Artem S. Povalyukhin.
    
-   Feature: added `Object.setPrototypeOf()`. Thanks to Artem S. Povalyukhin.
    
-   Feature: introduced nullish coalescing operator.
    
-   Bugfix: fixed `Object.getPrototypeOf()` according to the specification.
    
-   Bugfix: fixed `Object.prototype.valueOf()` according to the specification.
    
-   Bugfix: fixed `JSON.stringify()` with unprintable values and replacer function.
    
-   Bugfix: fixed operator `in` according to the specification.
    
-   Bugfix: fixed `Object.defineProperties()` according to the specification.
    
-   Bugfix: fixed `Object.create()` according to the specification. Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed `Number.prototype.toString(radix)` when fast-math is enabled.
    
-   Bugfix: fixed `RegExp()` instance properties.
    
-   Bugfix: fixed import segfault. Thanks to 洪志道 (Hong Zhi Dao).
    

#### Changes with njs 0.3.7

Release Date: 19 November 2019

nginx modules:

-   Improvement: refactored iteration over external objects.
    

Core:

-   Feature: added `Object.assign()`.
    
-   Feature: added `Array.prototype.copyWithin()`.
    
-   Feature: added support for labels in `console.time()`.
    
-   Change: removed `console.help()` from CLI.
    
-   Improvement: moved constructors and top-level objects to global object.
    
-   Improvement: arguments validation for configure script.
    
-   Improvement: refactored JSON methods.
    
-   Bugfix: fixed heap-buffer-overflow in `njs_array_reverse_iterator()` function. The following functions were affected: `Array.prototype.lastIndexOf()`, `Array.prototype.reduceRight()`.
    
-   Bugfix: fixed `[[Prototype]]` slot of `NativeErrors`.
    
-   Bugfix: fixed `NativeError.prototype.message` properties.
    
-   Bugfix: added conversion of `this` value to object in `Array.prototype functions`.
    
-   Bugfix: fixed iterator for `Array.prototype.find()` and `Array.prototype.findIndex()` functions.
    
-   Bugfix: fixed `Array.prototype.includes()` and `Array.prototype.join()` with `undefined` argument.
    
-   Bugfix: fixed `constructor` property of `Hash` and `Hmac` objects.
    
-   Bugfix: fixed `__proto__` property of getters and setters.
    
-   Bugfix: fixed `Date` object string formatting.
    
-   Bugfix: fixed handling of `NaN` and `-0` arguments in `Math.min()` and `Math.max()`.
    
-   Bugfix: fixed `Math.round()` according to the specification.
    
-   Bugfix: reimplemented `bound` functions according to the specification.
    

#### Changes with njs 0.3.6

Release Date: 22 October 2019

nginx modules:

-   Improvement: getting special headers from [`r.headersIn{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_in).
    

Core:

-   Feature: added new `Function()` support.
    
-   Feature: added `Number.prototype.toFixed()`.
    
-   Feature: added `Number.prototype.toPrecision()`.
    
-   Feature: added `Number.prototype.toExponential()`.
    
-   Improvement: making `prototype` property of function instances writable.
    
-   Improvement: limiting recursion depth while compiling.
    
-   Improvement: moving global functions to the global object.
    
-   Bugfix: fixed prototype mutation for object literals.
    
-   Bugfix: fixed heap-buffer-overflow while parsing regexp literals.
    
-   Bugfix: fixed integer-overflow while parsing exponent of number literals.
    
-   Bugfix: fixed `parseFloat()`.
    
-   Bugfix: fixed `Array.prototype` functions according to the specification. The following functions were fixed: `every`, `includes`, `indexOf`, `filter`, `find`, `findIndex`, `forEach`, `lastIndexOf`, `map`, `pop`, `push`, `reduce`, `reduceRight`, `shift`, `some`, `unshift`.
    
-   Bugfix: fixed handing of accessor descriptors in `Object.freeze()`.
    
-   Bugfix: fixed `String.prototype.replace()` when first argument is not a string.
    
-   Bugfix: fixed stack-use-after-scope in `Array.prototype.map()`.
    
-   Bugfix: `Date.prototype.toUTCString()` format was aligned to ES9.
    
-   Bugfix: fixed buffer overflow in `Number.prototype.toString(radix)`.
    
-   Bugfix: fixed `Regexp.prototype.test()` for regexps with backreferences.
    
-   Bugfix: fixed `Array.prototype.map()` for objects with nonexistent values.
    
-   Bugfix: fixed `Array.prototype.pop()` and `shift()` for sparse objects.
    
-   Bugfix: fixed `Date.UTC()` according to the specification.
    
-   Bugfix: fixed `Date()` constructor according to the specification.
    
-   Bugfix: fixed type of `Date.prototype`. Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed `Date.prototype.setTime()`. Thanks to Artem S. Povalyukhin.
    
-   Bugfix: fixed default number of arguments expected by built-in functions.
    
-   Bugfix: fixed `caller` and `arguments` properties of a function instance. Thanks to Artem S. Povalyukhin.
    

#### Changes with njs 0.3.5

Release Date: 15 August 2019

Core:

-   Bugfix: fixed module importing using `require()`. The bug was introduced in [0.3.4](https://nginx.org/en/docs/njs/changes.html#0.3.4).
    
-   Bugfix: fixed `[[SetPrototypeOf]]`.
    

#### Changes with njs 0.3.4

Release Date: 13 August 2019

Core:

-   Feature: added `Object` shorthand methods and computed property names. Thanks to 洪志道 (Hong Zhi Dao) and Artem S. Povalyukhin.
    
-   Feature: added getter/setter literal support. Thanks to 洪志道 (Hong Zhi Dao) and Artem S. Povalyukhin.
    
-   Feature: added [`fs.renameSync()`](https://nginx.org/en/docs/njs/reference.html#fs_renamesync).
    
-   Feature: added `String.prototype.trimEnd()` and `String.prototype.trimStart()`.
    
-   Improvement: added memory-sanitizer support.
    
-   Improvement: Unicode case tables updated to version 12.1.
    
-   Improvement: added UTF8 validation for string literals.
    
-   Bugfix: fixed reading files with zero size in [`fs.readFileSync()`](https://nginx.org/en/docs/njs/reference.html#readfilesync).
    
-   Bugfix: extended the list of space separators in `String.prototype.trim()`.
    
-   Bugfix: fixed using of uninitialized value in `String.prototype.padStart()`.
    
-   Bugfix: fixed `String.prototype.replace()` for `$0` and `$&` replacement string.
    
-   Bugfix: fixed `String.prototype.replace()` for byte strings with regex argument.
    
-   Bugfix: fixed global match in `String.prototype.replace()` with regexp argument.
    
-   Bugfix: fixed `Array.prototype.slice()` for primitive types.
    
-   Bugfix: fixed heap-buffer-overflow while importing module.
    
-   Bugfix: fixed UTF-8 character escaping.
    
-   Bugfix: fixed `Object.values()` and `Object.entries()` for shared objects.
    
-   Bugfix: fixed uninitialized memory access in `String.prototype.match()`.
    
-   Bugfix: fixed `String.prototype.match()` for byte strings with regex argument.
    
-   Bugfix: fixed `Array.prototype.lastIndexOf()` with undefined arguments.
    
-   Bugfix: fixed `String.prototype.substring()` with empty substring.
    
-   Bugfix: fixed invalid memory access in `String.prototype.substring()`.
    
-   Bugfix: fixed `String.fromCharCode()` for code points more than `65535` and `NaN`.
    
-   Bugfix: fixed `String.prototype.toLowerCase()` and `String.prototype.toUpperCase()`.
    
-   Bugfix: fixed `Error()` constructor with no arguments.
    
-   Bugfix: fixed `in` operator for values with accessor descriptors.
    
-   Bugfix: fixed `Object.defineProperty()` for non-boolean descriptor props.
    
-   Bugfix: fixed `Error.prototype.toString()` with UTF8 string properties.
    
-   Bugfix: fixed `Error.prototype.toString()` with non-string values for `name` and `message`.
    

#### Changes with njs 0.3.3

Release Date: 25 June 2019

nginx modules:

-   Improvement: getting of special response headers in [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out).
    
-   Improvement: working with unknown methods in [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest).
    
-   Improvement: added support for null as a second argument of [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest).
    
-   Bugfix: fixed processing empty output chain in stream body filter.
    

Core:

-   Feature: added runtime support for property getter/setter. Thanks to 洪志道 (Hong Zhi Dao) and Artem S. Povalyukhin.
    
-   Feature: added [`process`](https://nginx.org/en/docs/njs/reference.html#process) global object.
    
-   Feature: writable most of built-in properties and methods.
    
-   Feature: added generic implementation of `Array.prototype.fill()`.
    
-   Bugfix: fixed integer-overflow in `String.prototype.concat()`.
    
-   Bugfix: fixed setting of object properties.
    
-   Bugfix: fixed `Array.prototype.toString()`.
    
-   Bugfix: fixed `Date.prototype.toJSON()`.
    
-   Bugfix: fixed overwriting “constructor” property of built-in prototypes.
    
-   Bugfix: fixed processing of invalid surrogate pairs in strings.
    
-   Bugfix: fixed processing of invalid surrogate pairs in JSON strings.
    
-   Bugfix: fixed heap-buffer-overflow in `toUpperCase()` and `toLowerCase()`.
    
-   Bugfix: fixed escaping lone closing square brackets in `RegExp()` constructor.
    
-   Bugfix: fixed handling zero byte characters inside RegExp pattern strings.
    
-   Bugfix: fixed `String.prototype.toBytes()` for ASCII strings.
    
-   Bugfix: fixed truth value of JSON numbers in `JSON.parse()`.
    
-   Bugfix: fixed use-of-uninitialized-value in `njs_string_replace_join()`.
    
-   Bugfix: fixed `parseInt('-0')`. Thanks to Artem S. Povalyukhin.
    

#### Changes with njs 0.3.2

Release Date: 21 May 2019

Core:

-   Feature: added support for template literals. Thanks to 洪志道 (Hong Zhi Dao) and Artem S. Povalyukhin.
    
-   Feature: executing command from command line arguments.
    
-   Feature: added support for RegExp `groups` object (ES9).
    
-   Feature: added block scoped function definitions support.
    
-   Feature: added support for building with GNU Readline library.
    
-   Feature: made configurable `length`, `name`, and most of built-in methods.
    
-   Feature: made all constructor properties configurable.
    
-   Bugfix: fixed `Regexp.prototype.exec()` for Unicode-only regexps.
    
-   Bugfix: fixed `njs_vm_value_dump()` for empty string values.
    
-   Bugfix: fixed RegExp constructor for regexp value arguments.
    
-   Bugfix: fixed walking over prototypes chain during iteration over an object.
    
-   Bugfix: fixed overflow in `Array.prototype.concat()`.
    
-   Bugfix: fixed length calculation for UTF-8 string with escape characters.
    
-   Bugfix: fixed parsing surrogate pair presents as UTF-16 escape sequences.
    
-   Bugfix: fixed processing the “\*” quantifier for `String.prototype.match()`.
    
-   Bugfix: fixed `Date()` constructor with one argument.
    
-   Bugfix: fixed arrays expansion.
    
-   Bugfix: fixed heap-buffer-overflow in `String.prototype.replace()`.
    
-   Bugfix: fixed heap-buffer-overflow in `String.prototype.lastIndexOf()`.
    
-   Bugfix: fixed regexp literals parsing with escaped backslash and backslash in square brackets.
    
-   Bugfix: fixed regexp literals with lone closing brackets.
    
-   Bugfix: fixed uninitialized-memory-access in `Object.defineProperties()`.
    
-   Bugfix: fixed processing the “\*” quantifier for `String.prototype.replace()`.
    
-   Bugfix: fixed `Array.prototype.slice()` for UTF8-invalid byte strings.
    
-   Bugfix: fixed `String.prototype.split()` for UTF8-invalid byte strings.
    
-   Bugfix: fixed handling of empty block statements.
    

#### Changes with njs 0.3.1

Release Date: 16 April 2019

Core:

-   Feature: added arrow functions support. Thanks to 洪志道 (Hong Zhi Dao) and Artem S. Povalyukhin.
    
-   Feature: added `Object.getOwnPropertyNames()`. Thanks to Artem S. Povalyukhin.
    
-   Feature: added `Object.getOwnPropertyDescriptors()`. Thanks to Artem S. Povalyukhin.
    
-   Feature: making `__proto__` accessor descriptor of `Object` instances mutable.
    
-   Feature: added shebang support in CLI.
    
-   Feature: added support for module mode execution in CLI. In module mode global, this is unavailable.
    
-   Bugfix: fixed editline detection.
    
-   Bugfix: fixed `Function.prototype.bind()`. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Bugfix: fixed checking of duplication of parameters for functions. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Bugfix: fixed function declaration with the same name as a variable. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Improvement: code related to parsing of objects, variables and functions is refactored. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Improvement: large-value output improved in `console.log()`.
    
-   Improvement: string output improved in `console.log()` in a compliant way (without escaping and quotes).
    
-   Improvement: using ES6 version of `ToInt32()`, `ToUint32()`, `ToLength()`.
    

#### Changes with njs 0.3.0

Release Date: 26 March 2019

nginx modules:

-   Feature: added the `js_path` directive for [http](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_path) and [stream](https://nginx.org/en/docs/stream/ngx_stream_js_module.html#js_path).
    
-   Change: returning undefined value instead of empty strings for absent properties in the following objects: [`r.args{}`](https://nginx.org/en/docs/njs/reference.html#r_args), [`r.headersIn{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_in), [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out), [`r.variables{}`](https://nginx.org/en/docs/njs/reference.html#r_variables), [`s.variables{}`](https://nginx.org/en/docs/njs/reference.html#s_variables).
    
-   Change: returning undefined value instead of throwing an exception for [`r.requestBody`](https://nginx.org/en/docs/njs/reference.html#r_request_body) when request body is unavailable.
    
-   Bugfix: fixed crash while iterating over [`r.args{}`](https://nginx.org/en/docs/njs/reference.html#r_args) when a value is absent in a key-value pair.
    

Core:

-   Feature: added initial ES6 modules support. Default import and default export statements are supported. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Feature: added `Object.prototype.propertyIsEnumerable()`.
    
-   Feature: reporting file name and function name in disassembler output.
    
-   Bugfix: fixed function redeclarations in interactive shell. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Bugfix: fixed RegExp literals parsing.
    
-   Bugfix: fixed setting length of UTF8 string in [`fs.readFileSync()`](https://nginx.org/en/docs/njs/reference.html#readfilesync).
    
-   Bugfix: fixed `nxt_file_dirname()` for paths with no dir component.
    

#### Changes with njs 0.2.8

Release Date: 26 February 2019

nginx modules:

-   Change: properties of HTTP request deprecated in [0.2.2](https://nginx.org/en/docs/njs/changes.html#njs0.2.2) are removed.
    
-   Feature: added support for delete operation in [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out).
    
-   Feature: added support for setting nginx variables.
    
-   Bugfix: fixed [`r.subrequest()`](https://nginx.org/en/docs/njs/reference.html#r_subrequest) for empty body value.
    
-   Improvement: setting special response headers in [`r.headersOut{}`](https://nginx.org/en/docs/njs/reference.html#r_headers_out).
    

Core:

-   Feature: added labels support.
    
-   Feature: added `setImmediate()` method.
    
-   Feature: added support for shorthand property names for Object literals.
    
-   Bugfix: fixed `Function.prototype.bind()`.
    
-   Bugfix: fixed parsing of string literals containing newline characters.
    
-   Bugfix: fixed line number in reporting variable reference errors.
    
-   Bugfix: fixed creation of long UTF8 strings.
    
-   Bugfix: fixed setting special response headers in `String.prototype.split()` for Unicode strings.
    
-   Bugfix: fixed heap-buffer-overflow in `String.prototype.split()`.
    
-   Bugfix: fixed `Array.prototype.fill()`. Thanks to Artem S. Povalyukhin.
    
-   Improvement: code related to function invocation is refactored. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Improvement: code related to variables is refactored. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Improvement: parser is refactored. Thanks to 洪志道 (Hong Zhi Dao).
    
-   Improvement: reporting filenames in exceptions.
    

#### Changes with njs 0.2.7

Release Date: 25 December 2018

Core:

-   Feature: rest parameters syntax (destructuring is not supported). Thanks to Alexander Pyshchev.
    
-   Feature: added `Object.entries()` method.
    
-   Feature: added `Object.values()` method.
    
-   Improvement: code generator refactored and simplified.
    
-   Bugfix: fixed automatic semicolon insertion.
    
-   Bugfix: fixed assignment expression from compound assignment.
    
-   Bugfix: fixed comparison of Byte and UTF8 strings.
    
-   Bugfix: fixed type of iteration variable in `for-in` with array values.
    
-   Bugfix: fixed building on platforms without librt.
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.2.6

Release Date: 27 November 2018

Core:

-   Feature: making built-in prototypes mutable.
    
-   Feature: making global object mutable.
    
-   Feature: [`console.time()`](https://nginx.org/en/docs/njs/reference.html#console_time) and [`console.timeEnd()`](https://nginx.org/en/docs/njs/reference.html#console_time_end) methods.
    
-   Feature: allowing variables and functions to be redeclared.
    
-   Feature: extending `Object.defineProperty()` spec conformance.
    
-   Feature: introduced quiet mode for CLI to handle simple expressions from stdin.
    
-   Feature: introduced compact form of backtraces to handle stack overflows.
    
-   Improvement: improved wording for various exceptions.
    
-   Bugfix: fixed closure values handling.
    
-   Bugfix: fixed equality operator for various value types.
    
-   Bugfix: fixed handling of `this` keyword in various scopes.
    
-   Bugfix: fixed handling non-object values in `Object.keys()`.
    
-   Bugfix: fixed parsing of throw statement inside `if` statement.
    
-   Bugfix: fixed parsing of newline after throw statement.
    
-   Bugfix: fixed parsing of statements in if statement without newline.
    
-   Bugfix: fixed size `uint32_t` overflow in `njs_array_expand()`.
    
-   Bugfix: fixed `typeof` operator for `object_value` type.
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.2.5

Release Date: 30 October 2018

nginx modules:

-   Bugfix: fixed counting pending events in stream module.
    
-   Bugfix: fixed `s.off()` in stream module.
    
-   Bugfix: fixed processing of data chunks in `js_filter` in stream module.
    
-   Bugfix: fixed http `status` and `contentType` getter in http module.
    
-   Bugfix: fixed http response and parent getters in http module.
    

Core:

-   Feature: arguments object support.
    
-   Feature: non-integer fractions support.
    
-   Improvement: handling non-array values in `Array.prototype.slice()`.
    
-   Bugfix: fixed `Array.prototype.length` setter
    
-   Bugfix: fixed `njs_array_alloc()` for length > 2\*\*31.
    
-   Bugfix: handling int overflow in `njs_array_alloc()` on 32bit archs.
    
-   Bugfix: fixed code size mismatch error message.
    
-   Bugfix: fixed delete operator in a loop.
    
-   Bugfix: fixed `Object.getOwnPropertyDescriptor()` for complex object (inherited from `Array` and `string` values).
    
-   Bugfix: fixed `Object.prototype.hasOwnProperty()` for non-object properties
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.2.4

Release Date: 18 September 2018

nginx modules:

-   Change: stream module handlers refactored.
    
    New methods and properties: [`s.on()`](https://nginx.org/en/docs/njs/reference.html#s_on), [`s.off()`](https://nginx.org/en/docs/njs/reference.html#s_off), [`s.allow()`](https://nginx.org/en/docs/njs/reference.html#s_allow), [`s.done()`](https://nginx.org/en/docs/njs/reference.html#s_done), [`s.decline()`](https://nginx.org/en/docs/njs/reference.html#s_decline), [`s.deny()`](https://nginx.org/en/docs/njs/reference.html#s_deny).
    
    Removed properties of the [Stream](https://nginx.org/en/docs/njs/reference.html#stream) object: `s.OK`, `s.ABORT`, `s.AGAIN`, `s.DECLINED`, `s.ERROR` (replaced with [`s.allow()`](https://nginx.org/en/docs/njs/reference.html#s_allow), [`s.done()`](https://nginx.org/en/docs/njs/reference.html#s_done), [`s.deny()`](https://nginx.org/en/docs/njs/reference.html#s_deny)).
    
    `s.buffer` (for reading replaced with data argument of the corresponding callback, for writing use [`s.send()`](https://nginx.org/en/docs/njs/reference.html#s_send)).
    
    `s.fromUpstream` (replaced with a callback for a corresponding event).
    
    `s.eof` (replaced with [`flags.last`](https://nginx.org/en/docs/njs/reference.html#s_on_callback_last)).
    

Core:

-   Feature: added `Function.prototype.length`.
    
-   Feature: introduced sandboxing mode.
    
-   Improvement: added exception strings where appropriate.
    
-   Improvement: improved wording for primitive type conversion exception.
    
-   Bugfix: throwing `TypeError` for attempts to change frozen properties.
    
-   Bugfix: fixed `Object.defineProperty()` for existing properties.
    
-   Bugfix: respecting the enumerable attribute while iterating by for in.
    
-   Bugfix: respecting writable attribute for property handlers.
    
-   Bugfix: fixed exception handling in arguments of a function.
    
-   Bugfix: fixed `Object.prototype.toString` for different value types.
    
-   Bugfix: fixed `Object()` constructor for object types arguments.
    
-   Bugfix: fixed comparison of objects and strings.
    
-   Bugfix: fixed `String.slice()` for undefined arguments.
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.2.3

Release Date: 31 July 2018

nginx modules:

-   Bugfix: making a subrequest from a `Reply` object caused a segmentation fault.
    
-   Bugfix: getting the parent property of the main [HTTP Request](https://nginx.org/en/docs/njs/reference.html#http) object caused a segmentation fault.
    

Core:

-   Feature: added the pretty string representation for values.
    
-   Feature: correctly printing floating point numbers.
    
-   Feature: correctly parsing floating point numbers.
    
-   Feature: [`String.bytesFrom()`](https://nginx.org/en/docs/njs/reference.html#string_bytesfrom) method (decoding `hex`, `base64`, `base64url` into a byte string).
    
-   Feature: `String.padStart()` and `String.padEnd()` methods.
    
-   Feature: added support of binary literals.
    
-   Improvement: added information about illegal token in number parsing.
    
-   Improvement: allowed uppercased `O` in octal literal values.
    
-   Improvement: added support for multiple arguments in `console.log()`.
    
-   Bugfix: fixed applying `call()` to methods of external values.
    
-   Bugfix: fixed addition operator applied to an object.
    
-   Bugfix: fixed exception handling in `njs_vm_value_to_ext_string()`.
    
-   Bugfix: fixed `Number()` with boolean, null and undefined arguments.
    
-   Bugfix: fixed error handling of setting non-numeric `Array.length`.
    
-   Bugfix: fixed autocompletion for global objects.
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.2.2

Release Date: 19 June 2018

nginx modules:

-   Change: merged HTTP `Response` and `Reply` into [HTTP Request](https://nginx.org/en/docs/njs/reference.html#http). New members of `Request`:
    
    -   `req.status` (`res.status`)
        
    -   `req.parent` (`reply.parent`)
        
    -   `req.requestBody` (`req.body`)
        
    -   `req.responseBody` (`reply.body`)
        
    -   `req.headersIn` (`req.headers`)
        
    -   `req.headersOut` (`res.headers`)
        
    -   `req.sendHeader()` (`res.sendHeader()`)
        
    -   `req.send()` (`res.send()`)
        
    -   `req.finish()` (`res.finish()`)
        
    -   `req.return()` (`res.return()`)
        
    
    Deprecated members of `Request`:
    
    -   `req.body` (use `req.requestBody` or `req.responseBody`)
        
    -   `req.headers` (use `req.headersIn` or `req.headersOut`)
        
    -   `req.response`
        
    
    Deprecated members of `Response`:
    
    -   `res.contentLength` (use [`req.headersOut`](https://nginx.org/en/docs/njs/reference.html#r_headers_out)`['Content-Length']`)
        
    -   `res.contentType` (use [`req.headersOut`](https://nginx.org/en/docs/njs/reference.html#r_headers_out)`['Content-Type']`)
        
    
    The deprecated properties will be removed in [next](https://nginx.org/en/docs/njs/changes.html#njs0.2.8) releases.
    
-   Feature: HTTP [internalRedirect()](https://nginx.org/en/docs/njs/reference.html#r_internal_redirect) method.
    

Core:

-   Bugfix: fixed heap-buffer-overflow in `crypto.createHmac()`.
    

#### Changes with njs 0.2.1

Release Date: 31 May 2018

nginx modules:

-   Feature: HTTP request body getter.
    
-   Improvement: moved njs vm to the `main` configuration.
    
-   Improvement: improved logging for [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) and [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content) directives.
    
-   Improvement: setting status code to 500 by default in the [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content) handler
    
-   Improvement: added the debug for the returned status code in [js\_content](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_content) handler
    
-   Bugfix: fixed error logging in [js\_include](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_include).
    

Core:

-   Feature: added array length setter.
    
-   Improvement: public header `cleanup. njscript.h` is renamed to `njs.h`.
    
-   Bugfix: fixed crypto `update()` method after `digest()` is called.
    
-   Bugfix: fixed `crypto.createHmac()` for keys with size <= alg size and > 64.
    
-   Bugfix: fixed `JSON.stringify()` for arrays with empty cells.
    
-   Bugfix: fixed exception type for unsupported types in `JSON.stringify()`.
    
-   Bugfix: fixed handling of undefined arguments of functions.
    
-   Bugfix: fixed handling of missing `arg` of `Object.getOwnPropertyDescriptor()`.
    
-   Bugfix: fixed handling of properties in `Object.getOwnPropertyDescriptor()`.
    
-   Bugfix: fixed the writeable flag of `Array.length` property.
    
-   Bugfix: fixed return value type of `clearTimeout()`.
    
-   Bugfix: fixed `njs_vm_external_bind()`.
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.2.0

Release Date: 03 April 2018

-   Feature: reporting njs version by CLI.
    
-   Feature: textual description for type converting exceptions.
    
-   Feature: `setTimeout()` and `clearTimeout()` methods.
-   Feature: Byte string to `hex`, `base64`, `base64url` encodings.
    
-   Feature: [Node.js style](https://nodejs.org/api/crypto.html#crypto_class_hash) `Crypto` methods.
    
-   Feature: HTTP and stream `warn()` and `error()` methods.
    
-   Feature: HTTP `subrequest()` method.
    
-   Feature: HTTP `return()` method.
    
-   Bugfix: miscellaneous bugs have been fixed in the core and interactive shell.
    

#### Changes with njs 0.1.15

Release Date: 20 November 2017

-   Feature: `Error`, `EvalError`, `InternalError`, `RangeError`, `ReferenceError`, `SyntaxError`, `TypeError`, `URIError` objects.
    
-   Feature: octal literals support.
    
-   Feature: [Node.js style](https://nodejs.org/api/fs.html#fs_file_system) `File system` access methods: `fs.readFile()`, `fs.readFileSync()`, `fs.appendFile()`, `fs.appendFileSync()`, `fs.writeFile()`, `fs.writeFileSync()`.
    
-   Feature: nginx modules print backtrace on exception.
    
-   Bugfix: miscellaneous bugs have been fixed.
    

#### Changes with njs 0.1.14

Release Date: 09 October 2017

-   Feature: JSON object.
    
-   Feature: object level completions in interactive shell.
    
-   Feature: various configure improvements.
    
-   Bugfix: miscellaneous bugs have been fixed in the core and interactive shell.
    

#### Changes with njs 0.1.13

Release Date: 31 August 2017

-   Feature: `console.log()` and `console.help()` methods in interactive shell.
    
-   Feature: interactive shell prints backtrace on exception.
    
-   Feature: interactive shell by default if `libedit` is available.
    
-   Bugfix: processing of large files from `stdin` in command line mode.
    
-   Bugfix: improved `editline` detection.
    

#### Changes with njs 0.1.12

Release Date: 08 August 2017

-   Feature: Interactive shell.
    
-   Bugfix: in `Object.isSealed()`.
    

#### Changes with njs 0.1.11

Release Date: 27 June 2017

-   Feature: `Object.keys()`, `Object.prototype.hasOwnProperty()` methods.
    
-   Feature: `Object.defineProperty()`, `Object.defineProperties()`, `Object.getOwnPropertyDescriptor()` methods.
    
-   Feature: `Object.getPrototypeOf()`, `Object.prototype.isPrototypeOf()` methods.
    
-   Feature: `Object.preventExtensions()`, `Object.isExtensible()`, `Object.freeze()`, `Object.isFrozen()`, `Object.seal()`, `Object.isSealed()` methods.
    
-   Feature: scientific notation (`3.35e10`) literals support.
    
-   Feature: hexadecimal (`0x1123`) literals support.
    
-   Bugfix: processing of large array indexes.
    
-   Bugfix: in `parseInt()` and `Date.parse()`.
    

#### Changes with njs 0.1.10

Release Date: 04 April 2017

-   Feature: nested functions and function closures.
    
-   Feature: `Array.of()`, `Array.prototype.fill()`, `Array.prototype.find()`, `Array.prototype.findIndex()` methods.
    
-   Bugfix: miscellaneous bugs and segmentation faults have been fixed.
    

#### Changes with njs 0.1.9

Release Date: 01 February 2017

-   Bugfix: global variables were not initialized when njs was used in nginx.
    

#### Changes with njs 0.1.8

Release Date: 24 January 2017

-   Change: the `strict` mode is enforced, variables must be explicitly declared.
    
-   Feature: `for` and `for-in` loops support variable declaration.
    
-   Bugfix: global and function scopes have been fixed.
    
-   Bugfix: now `for-in` loop does not discard the last value of property variable.
    
-   Bugfix: miscellaneous bugs and segmentation faults have been fixed.
    

#### Changes with njs 0.1.7

Release Date: 27 December 2016

-   Change: the [js\_include](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_include) directive has been disabled at server and location levels.
    
-   Feature: exponentiation operators.
    
-   Bugfix: miscellaneous bugs and segmentation faults have been fixed.
    

#### Changes with njs 0.1.6

Release Date: 13 December 2016

-   Change: the [js\_set](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_set) directive has been disabled at server and location levels.
    
-   Feature: ES6 `Math` methods.
    
-   Bugfix: miscellaneous bugs and segmentation faults have been fixed.