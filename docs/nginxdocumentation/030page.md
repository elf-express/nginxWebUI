# 模組 ngx_http_auth_request_module

> Source: https://nginx.org/en/docs/http/ngx_http_auth_request_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_http_auth_request_module

（1.5.4+）依**子請求（subrequest）**結果做客戶端授權：

| 子請求回應 | 結果 |
|------------|------|
| 2xx | 允許存取 |
| 401 或 403 | 拒絕，回相同錯誤碼 |
| 其他 | 視為錯誤 |

401 時，客戶端也會收到子請求回應中的 `WWW-Authenticate` 標頭。

預設不編入；需 `--with-http_auth_request_module`（多數套件已含）。可與 access / auth_basic / auth_jwt 等透過 [`satisfy`](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy) 併用。

> 1.7.3 之前，授權子請求的回應不能快取。

### 範例

```nginx
location /private/ {
    auth_request /auth;
    ...
}

location = /auth {
    proxy_pass http://auth-backend;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
    proxy_set_header X-Original-URI $request_uri;
}
```

### Directives

#### `auth_request`

- **語法：** `auth_request uri | off;`
- **預設：** `off`
- **語境：** `http`, `server`, `location`
- 啟用並指定子請求 URI。

#### `auth_request_set`

- **語法：** `auth_request_set $variable value;`
- **語境：** `http`, `server`, `location`
- 授權子請求完成後，把變數設為指定值；值可含授權請求的變數（如 `$upstream_http_*`）。

### 本專案

參數模板「CrowdSec Auth Request」即 `auth_request` 模式：把驗證丟給 CrowdSec bouncer／內部 location。子請求 location 通常要：

- `proxy_pass_request_body off;`
- 清空或固定 `Content-Length`
- 視需要把原始 URI／IP 用 `proxy_set_header` 傳給驗證端
