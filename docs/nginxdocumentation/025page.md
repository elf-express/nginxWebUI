# page

> Source: https://nginx.org/en/docs/http/ngx_http_acme_module.html

---

## 目錄

- [Module ngx\_http\_acme\_module](#module-ngxhttpacmemodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_acme\_module

`ngx_http_acme_module`模塊實現自動證書管理（[ACMEv2](https://datatracker.ietf.org/doc/html/rfc8555)）協議。

該模塊的原始碼可在[here](https://github.com/nginx/nginx-acme)獲得。下載和安裝說明可在[here](https://github.com/nginx/nginx-acme/blob/main/README.md)獲得。

自1.29.0起，該模塊也可以在預構建的`nginx-module-acme`[package](https://nginx.org/en/linux_packages.html#dynmodules)和`nginx-plus-module-acme`包中作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

#### 配置示例

```nginx
resolver 127.0.0.1:53;

acme_issuer example {
    uri         https://acme.example.com/directory;
    contact     admin@example.test;
    state_path  /var/cache/nginx/acme-example;
    accept_terms_of_service;
}

acme_shared_zone zone=ngx_acme_shared:1M;

server {
    listen 443 ssl;
    server_name  .example.test;

    acme_certificate example;

    ssl_certificate       $acme_certificate;
    ssl_certificate_key   $acme_certificate_key;

    # 不要在每次請求時解析證書
    ssl_certificate_cache max=2;
}

server {
    # 埠80上的偵聽器需要處理ACME HTTP-01質詢
    listen 80;

    location / {
        return 404;
    }
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>acme_issuer</strong> <code><i>name</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

定義ACME證書頒發者對象。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uri</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

ACME伺服器的[directory URL](https://datatracker.ietf.org/doc/html/rfc8555#section-7.1.1)。此指令是強制性的。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>account_key</strong> <code><i>alg</i></code>[:<code><i>size</i></code>] | <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

用於請求身份驗證的帳戶私鑰。

可接受的值：

-   `ecdsa`：`*256*`/`*384*`/`*521*`適用於ES256、ES384或ES512 JSON Web簽名算法
-   RS256的`rsa`：`*2048*`/`*3072*`/`*4096*`。
-   使用上面的算法之一，獲取現有鍵的文件路徑。

生成的帳戶密鑰將在重新加載時保留，但在重新啟動時將丟失，除非配置了[state\_path](https://nginx.org/en/docs/http/ngx_http_acme_module.html#state_path)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>challenge</strong> <code><i>type</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>challenge http-01;</pre></td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

此指令出現在0.2.0版本中。

指定要用於頒發者的ACME質詢類型。

可接受值：

-   `http-01` (`http`)
-   `tls-alpn-01` (`tls-alpn`)

> ACME質詢是版本化的。如果指定了非版本化名稱，則模塊會自動選擇最新實現的版本。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>common_name_in_csr</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>common_name_in_csr off;</pre></td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

此指令出現在0.4.0版本中。

如果啟用，則將證書請求中的使用者公用名稱設置為提供的第一個DNS名稱或第一個IP位址。

> >啟用此選項可能會導致拒絕證書請求。

> >在0.4.0版本之前，始終設置主題公用名稱。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>contact</strong> <code><i>URL</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

設置ACME伺服器可用於就帳戶問題聯繫客戶端的URL數組。除非明確指定，否則將使用`mailto:`方案。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>external_account_key</strong> <code><i>kid</i></code> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

此指令出現在0.2.0版本中。

為[external account authorization](https://datatracker.ietf.org/doc/html/rfc8555#section-7.3.4)指定密鑰標識符`*kid*`和帶有MAC密鑰的`*file*`。

可以指定值`data`：`*key*`而不是`*file*`，這將直接從配置加載密鑰，而不使用中間文件。

在這兩種情況下，密鑰都應該以[base64url](https://datatracker.ietf.org/doc/html/rfc4648#section-5)編碼。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>preferred_chain</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

此指令出現在0.3.0版本中。

指定首選證書鏈。

如果ACME伺服器提供多個證書鏈，請首選具有從使用者公用名稱`*name*`頒發的最頂層證書的鏈。如果沒有匹配項，則將使用默認鏈。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>profile</strong> <code><i>name</i></code> [<code>require</code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

此指令出現在0.3.0版本中。

從ACME伺服器請求[certificate profile](https://datatracker.ietf.org/doc/html/draft-ietf-acme-profiles)`*name*`。

如果伺服器不支持指定的配置文件，`require`參數將導致證書續訂失敗。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_trusted_certificate</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

指定一個具有PEM格式的受信任CA證書的`*file*`，用於[verify](https://nginx.org/en/docs/http/ngx_http_acme_module.html#ssl_verify)ACME伺服器的證書。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_verify</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_verify on;</pre></td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

啟用或禁用ACME伺服器證書的驗證。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>state_path</strong> <code><i>path</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>state_path acme_&lt;issuer&gt;;</pre></td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

定義一個目錄，用於存儲可以在重新啟動時保持的模塊數據。這可以通過在啟動時跳過某些請求來縮短加載時間，並避免達到ACME伺服器上的請求速率限制。

該目錄包含敏感內容，如帳戶密鑰、頒發的證書和私鑰。

`off`參數（0.2.0）禁止在磁碟上存儲帳戶信息和頒發的證書。

> >在0.2.0版本之前，默認情況下不創建狀態目錄。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>accept_terms_of_service</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>acme_issuer</code><br></td></tr></tbody></table>

同意使用ACME伺服器的服務條款。某些伺服器要求在註冊帳戶之前接受服務條款。這些條款通常可以在ACME伺服器的網站上找到，如果需要，URL將被列印到錯誤日誌中。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>acme_shared_zone</strong> <code>zone</code>=<code><i>name</i></code>:<code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>acme_shared_zone zone=ngx_acme_shared:256k;</pre></td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

允許增加模塊內存存儲的大小。共享內存區域將用於存儲所有已配置證書頒發者的已頒發證書、密鑰和質詢數據。

默認區域大小足以容納大約50個ECDSA prime 256 v1密鑰或35個RSA 2048密鑰。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>acme_certificate</strong> <code><i>issuer</i></code> [<code><i>identifier</i></code> ...] [<code>key</code>=<code><i>alg</i></code>[:<code><i>size</i></code>]];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

使用從頒發者`issuer`請求的`identifiers`列表定義證書。

標識符的顯式列表可以省略。在這種情況下，標識符將從同一[server](https://nginx.org/en/docs/http/ngx_http_core_module.html#server)塊中的[server\_name](https://nginx.org/en/docs/http/ngx_http_core_module.html#server_name)指令中獲取。並非`server_name`中接受的所有值都是有效的證書標識符：不支持正則表達式和通配符。

key參數設置生成私鑰的類型，支持的密鑰算法和大小：`ecdsa:256`（默認）、`ecdsa:384`、`ecdsa:521`、`rsa:2048`、`rsa:3072`、`rsa:4096`。

#### 嵌入變量

`ngx_http_acme_module`模塊支持嵌入式變量，這些變量在帶有[acme\_certificate](https://nginx.org/en/docs/http/ngx_http_acme_module.html#acme_certificate)指令的[server](https://nginx.org/en/docs/http/ngx_http_core_module.html#server)塊中有效：

`$acme_certificate`

可以傳遞給[ssl\_certificate](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate)的SSL證書

`$acme_certificate_key`

可傳遞給[ssl\_certificate\_key](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate_key)的SSL證書私鑰