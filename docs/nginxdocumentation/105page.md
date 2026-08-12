# page

> Source: https://nginx.org/en/docs/ngx_mgmt_module.html

---

## 目錄

- [Module ngx\_mgmt\_module](#module-ngxmgmtmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_mgmt\_module

`ngx_mgmt_module`模塊啟用NGINX Plus許可證驗證和使用情況報告。自1.27.2（[NGINX Plus R33](https://docs.nginx.com/nginx/releases/#r33)）起，每個NGINX Plus實例都必須執行此操作。

對於Linux，名為`license.jwt`的JWT許可證文件應位於`/etc/nginx/`，對於FreeBSD，名為`/usr/local/etc/nginx/`，或者位於[license\_token](https://nginx.org/en/docs/ngx_mgmt_module.html#license_token)指令指定的路徑。許可證文件可從[MyF5](https://my.f5.com/)獲得。

使用情況報告使用[secure](https://nginx.org/en/docs/ngx_mgmt_module.html#ssl_verify)連接直接或通過[proxy](https://nginx.org/en/docs/ngx_mgmt_module.html#proxy)發送到F5授權端點[every hour](https://nginx.org/en/docs/ngx_mgmt_module.html#usage_report)。（可選）在網絡受限的環境中，報告可以是[configured](https://nginx.org/en/docs/ngx_mgmt_module.html#usage_report)到[F5 NGINX Instance Manager](https://docs.nginx.com/nginx-management-suite/about/)，報告可以從這些端點發送到F5授權端點。

默認情況下，如果F5授權端點沒有收到[initial usage report](https://nginx.org/en/docs/ngx_mgmt_module.html#enforce_initial_report)，nginx將停止處理流量。

自1.29.0（[NGINX Plus R35](https://docs.nginx.com/nginx/releases/#r35)）起，對於直接向F5授權端點報告的實例，支持自動許可證續訂。續訂時，NGINX從F5授權端點下載更新的JWT，並在不配置[reload](https://nginx.org/en/docs/switches.html)的情況下應用它。更新的許可證存儲在[state\_path](https://nginx.org/en/docs/ngx_mgmt_module.html#state_path)目錄中。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

```nginx
mgmt {
    # 如果需要自定義路徑
    license_token custom/file/path/license.jwt;

    # 在向NGINX實例管理器報告時
    usage_report endpoint=NIM_FQDN;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mgmt</strong> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

提供配置文件上下文，在其中指定使用情況報告和許可證管理指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>enforce_initial_report</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>enforce_initial_report on;</pre></td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

此指令出現在1.27.2版中。

啟用或禁用發送初始使用情況報告的180天寬限期。

初始使用報告在nginx安裝後首次啟動時立即發送。默認情況下，如果F5授權端點沒有收到初始報告，nginx將停止處理流量，直到報告成功交付。將指令值設置為`off`將啟用180天的寬限期，在此期間，F5授權端點必須收到初始使用報告。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>license_token</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>license_token license.jwt;</pre></td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

此指令出現在1.27.2版中。

指定一個JWT許可證`*file*`。默認情況下，對於Linux，`*license.jwt*`文件應該位於`/etc/nginx/`，對於FreeBSD，則位於`/usr/local/etc/nginx/`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy</strong> <code><i>host</i></code>:<code><i>port</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

此指令出現在1.27.4版中。

設置用於發送使用情況報告的HTTP HTTP HTTP代理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_username</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

此指令出現在1.27.4版中。

設置用於在[proxy](https://nginx.org/en/docs/ngx_mgmt_module.html#proxy)上進行身份驗證的用戶名。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>proxy_password</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

此指令出現在1.27.4版中。

設置用於在[proxy](https://nginx.org/en/docs/ngx_mgmt_module.html#proxy)上進行身份驗證的密碼。

密碼默認不加密發送，如果代理支持TLS，可以使用[stream](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html)模塊保護到代理的連接：

```nginx
mgmt {
    proxy          127.0.0.1:8080;
    proxy_username <name>;
    proxy_password <password>;
}

stream {
    server {
        listen 127.0.0.1:8080;
        
        proxy_ssl                     on;
        proxy_ssl_verify              on;
        proxy_ssl_trusted_certificate <proxy_ca_file>;

        proxy_pass <proxy_host>:<proxy_port>;
    }
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>resolver</strong> <code><i>address</i></code> ... [<code>valid</code>=<code><i>time</i></code>] [<code>ipv4</code>=<code>on</code>|<code>off</code>] [<code>ipv6</code>=<code>on</code>|<code>off</code>] [<code>status_zone</code>=<code><i>zone</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

配置用於解析使用情況報告終結點名稱的名稱伺服器。默認情況下，使用系統解析程式。

有關詳細信息，請參閱[resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_crl</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

以PEM格式指定一個具有已吊銷證書（CRL）的`*file*`，用於[verify](https://nginx.org/en/docs/ngx_mgmt_module.html#ssl_verify)使用情況報告終結點的證書。使用中間證書時，應在同一文件中指定其CRL。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_trusted_certificate system CA bundle;</pre></td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

指定一個使用PEM格式的受信任CA證書的`*file*`，用於[verify](https://nginx.org/en/docs/ngx_mgmt_module.html#ssl_verify)使用情況報告終結點的證書。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_verify on;</pre></td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

啟用或禁用使用情況報告終結點證書的驗證。

> >在1.27.2之前，默認值為`off`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>state_path</strong> <code><i>path</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

此指令出現在1.27.2版中。

定義一個目錄，用於存儲由`ngx_mgmt_module`模塊創建的狀態文件（`nginx-mgmt-*`）。Linux的默認目錄是`/var/lib/nginx/state`，FreeBSD的默認目錄是`/var/db/nginx/state`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>usage_report</strong> [<code>endpoint</code>=<code><i>address</i></code>] [<code>interval</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>usage_report endpoint=product.connect.nginx.com interval=1h;</pre></td></tr><tr><th>Context:</th><td><code>mgmt</code><br></td></tr></tbody></table>

設置使用報告端點的`*address*`和`*port*`。`interval`參數設置兩個連續報告之間的間隔。

> >在1.27.2之前，默認值為`nginx-mgmt.local`和`30m`。