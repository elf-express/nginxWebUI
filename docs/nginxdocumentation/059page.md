# page

> Source: https://nginx.org/en/docs/http/ngx_http_mirror_module.html

---

## 目錄

- [Module ngx\_http\_mirror\_module](#module-ngxhttpmirrormodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_mirror\_module

`ngx_http_mirror_module`模塊（1.13.4）通過創建後台鏡像子請求來實現對原始請求的鏡像，對鏡像子請求的響應將被忽略。

#### 配置示例

```nginx
location / {
    mirror /mirror;
    proxy_pass http://backend;
}

location = /mirror {
    internal;
    proxy_pass http://test_backend$request_uri;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mirror</strong> <code><i>uri</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mirror off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置原始請求將被鏡像到的URI。可以在同一配置級別上指定多個鏡像。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mirror_request_body</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mirror_request_body on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指示是否鏡像客戶端請求正文。如果啟用，將在創建鏡像子請求之前讀取客戶端請求正文。在這種情況下，將禁用由[proxy\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_request_buffering)、[fastcgi\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_request_buffering)、[scgi\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_request_buffering)和[uwsgi\_request\_buffering](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_request_buffering)指令設置的未緩衝客戶端請求正文的鏡像。

```nginx
location / {
    mirror /mirror;
    mirror_request_body off;
    proxy_pass http://backend;
}

location = /mirror {
    internal;
    proxy_pass http://log_backend;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
    proxy_set_header X-Original-URI $request_uri;
}
```