# page

> Source: https://nginx.org/en/docs/http/ngx_http_secure_link_module.html

---

## 目錄

- [Module ngx\_http\_secure\_link\_module](#module-ngxhttpsecurelinkmodule)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_secure\_link\_module

`ngx_http_secure_link_module`模塊（0.7.18）用於檢查請求的連結的真實性，保護資源免受未經授權的訪問，並限制連結的生命周期。

通過將請求中傳遞的校驗和值與為請求計算的值進行比較，可以驗證請求連結的真實性。如果連結的生存期有限，並且時間已過期，則認為該連結已過期。這些檢查的狀態在`$secure_link`變量中可用。

該模塊提供了兩種可選的操作模式。第一種模式由[secure\_link\_secret](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_secret)指令啟用，用於檢查所請求連結的真實性以及保護資源免受未經授權的訪問。第二種模式（0.8.50）由[secure\_link](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link)和[secure\_link\_md5](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_md5)指令啟用，也用於限制連結的生命周期。

默認情況下未構建此模塊，應使用`--with-http_secure_link_module`配置參數啟用此模塊。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>secure_link</strong> <code><i>expression</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義一個包含變量的字符串，將從該字符串中提取連結的校驗和值和生存期。

在`*expression*`中使用的變量通常與請求相關聯;參見下面的[example](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_md5)。

將從字符串中提取的校驗和值與[secure\_link\_md5](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_md5)指令定義的表達式的MD5哈希值進行比較。如果校驗和不同，則將`$secure_link`變量設置為空字符串。如果校驗和相同，則檢查連結生存期。如果連結的生存期有限且時間已過期，則將`$secure_link`變量設置為「`0`"。否則，設置為「`1`"。請求中傳遞的MD5哈希值以[base64url](https://datatracker.ietf.org/doc/html/rfc4648#section-5)編碼。

如果連結具有有限的生存期，則過期時間設置為從Epoch（Thu，01 Jan 1970 00：00：00 GMT）開始以秒為單位。該值在MD5哈希後的表達式中指定，並用逗號分隔。請求中傳遞的過期時間可通過`$secure_link_expires`變量在[secure\_link\_md5](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_md5)指令中使用。如果未指定過期時間，則連結具有無限的生存期。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>secure_link_md5</strong> <code><i>expression</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義一個表達式，將計算該表達式的MD5哈希值並將其與請求中傳遞的值進行比較。

表達式應包含連結（資源）的安全部分和機密成分。如果連結的生存期有限，表達式還應包含`$secure_link_expires`。

為了防止未經授權的訪問，表達式可能包含有關客戶端的一些信息，例如其地址和瀏覽器版本。

Example:

```nginx
location /s/ {
    secure_link $arg_md5,$arg_expires;
    secure_link_md5 "$secure_link_expires$uri$remote_addr secret";

    if ($secure_link = "") {
        return 403;
    }

    if ($secure_link = "0") {
        return 410;
    }

    ...
}
```

「`/s/link?md5=_e4Nc3iduzkWRm01TBBNYw&expires=2147483647`」連結限制IP位址為127.0.0.1的客戶端訪問「`/s/link`」。該連結的生存期也有限，直到2038年1月19日（GMT）。

在UNIX上，`*md5*`request參數值可以通過以下方式獲得：

```
echo -n '2147483647/s/link127.0.0.1 secret'| \
    openssl md5 -binary| openssl base64| tr +/ -_|tr -d =
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>secure_link_secret</strong> <code><i>word</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

定義用於檢查所請求連結的真實性的密碼`*word*`。

請求的連結的完整URI如下所示：

```
/`*prefix*`/`*hash*`/`*link*`
```

其中，`*hash*`是為連結和秘密字的連接計算的MD5哈希的十六進位表示，`*prefix*`是不帶斜槓的任意字符串。

如果請求的連結通過了真實性檢查，則將`$secure_link`變量設置為從請求URI中提取的連結。否則，將`$secure_link`變量設置為空字符串。

Example:

```nginx
location /p/ {
    secure_link_secret secret;

    if ($secure_link = "") {
        return 403;
    }

    rewrite ^ /secure/$secure_link;
}

location /secure/ {
    internal;
}
```

「`/p/5e814704a28d9bc1914ff19fa0c4a00a/link`」的請求將在內部重定向到「`/secure/link`"。

在UNIX上，此示例的散列值可以通過以下方式獲得：

```
echo -n 'linksecret'| openssl md5 -hex
```

#### 嵌入變量

`$secure_link`

鏈路檢查的狀態。具體值取決於所選的操作模式。

`$secure_link_expires`

在請求中傳遞的連結的生存期;僅用於[secure\_link\_md5](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_md5)指令。