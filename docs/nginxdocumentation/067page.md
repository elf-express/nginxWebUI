# 模組 ngx_http_realip_module

> Source: https://nginx.org/en/docs/http/ngx_http_realip_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_http_realip_module

把客戶端位址（與可選埠）改成指定請求標頭中送來的值。  
預設**不編入**；需 `--with-http_realip_module`（許多發行版套件已含）。

### 範例

```nginx
set_real_ip_from  192.168.1.0/24;
set_real_ip_from  192.168.2.1;
set_real_ip_from  2001:0db8::/32;
real_ip_header    X-Forwarded-For;
real_ip_recursive on;
```

### Directives

#### `set_real_ip_from`

- **語境：** `http`, `server`, `location`
- **語法：** `set_real_ip_from address | CIDR | unix:;`
- 定義**可信**來源（已知會送正確替代位址的代理／LB）。`unix:` 表示信任所有 UNIX domain socket。1.13.1 起可用 hostname。IPv6 自 1.3.0 / 1.2.1 支援。

#### `real_ip_header`

- **預設：** `real_ip_header X-Real-IP;`
- **語境：** `http`, `server`, `location`
- 指定用來替換客戶端位址的標頭。可含可選埠（1.11.0），格式依 [RFC 3986](https://datatracker.ietf.org/doc/html/rfc3986)。
- `proxy_protocol`（1.5.12）：改用 PROXY protocol 標頭中的位址；需在 `listen` 啟用 `proxy_protocol`。

#### `real_ip_recursive`（1.3.0 / 1.2.1）

- **預設：** `off`
- **off：** 若原始客戶端位址屬於可信位址，則以標頭中的**最後一個**位址取代。  
- **on：** 以標頭中**最後一個不可信**位址取代（適合多層 `X-Forwarded-For`）。

### 內嵌變數

| 變數 | 意義 |
|------|------|
| `$realip_remote_addr` | 替換前的原始客戶端位址（1.9.7） |
| `$realip_remote_port` | 替換前的原始埠（1.11.0） |

### 本專案

- Cloudflare / 反向代理真實 IP：`include /etc/nginx/geoip/realip.conf`（由 GeoIP/Cloudflare 下載腳本維護 `set_real_ip_from` 清單）
- 務必只信任**真正的邊緣代理**網段；盲目信任 `X-Forwarded-For` 會被偽造 IP 繞過黑白名單與限速
