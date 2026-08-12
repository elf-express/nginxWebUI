# 模組 ngx_http_access_module

> Source: https://nginx.org/en/docs/http/ngx_http_access_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_http_access_module

依**客戶端位址**限制存取。

亦可搭配：密碼（[`auth_basic`](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html)）、子請求結果（[`auth_request`](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html)）、JWT（[`auth_jwt`](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html)）。位址與密碼同時限制時，由 [`satisfy`](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy) 控制。

### 範例

```nginx
location / {
    deny  192.168.1.1;
    allow 192.168.1.0/24;
    allow 10.1.1.0/16;
    allow 2001:0db8::/32;
    deny  all;
}
```

規則**依序**檢查，**第一個命中**即生效。上例允許 IPv4 `10.1.1.0/16`、`192.168.1.0/24`（排除 `192.168.1.1`）以及 IPv6 `2001:0db8::/32`。規則很多時，較建議用 [`geo`](https://nginx.org/en/docs/http/ngx_http_geo_module.html) 變數。

### Directives

#### `allow`

- **語境：** `http`, `server`, `location`, `limit_except`
- **語法：** `allow address | CIDR | unix: | all;`
- 允許指定網路或位址。`unix:`（1.5.1）表示允許所有 UNIX domain socket。
- 同一層可多條；僅當本層沒有任何 `allow`/`deny` 時才繼承上層。

#### `deny`

- **語境：** 同上
- **語法：** `deny address | CIDR | unix: | all;`
- 拒絕指定網路或位址。繼承規則同 `allow`。

### 本專案

防火牆頁的黑／白名單會產生類似 `allow`/`deny`（以及 `include conf.d/deny_*.conf`）。**allow 在 deny 前、預設 allow** 等策略由 `ConfService.buildDenyAllow` 控制。
