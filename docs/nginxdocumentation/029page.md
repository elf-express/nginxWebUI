# page

> Source: https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html

---

## 目錄

- [Module ngx\_http\_auth\_jwt\_module](#module-ngxhttpauthjwtmodule)
    - [Supported Algorithms](#supported-algorithms)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_auth\_jwt\_module

`ngx_http_auth_jwt_module`模塊（1.11.3）通過使用指定的密鑰驗證提供的[JSON Web Token](https://datatracker.ietf.org/doc/html/rfc7519)（JWT）來實現客戶端授權。該模塊支持[JSON Web Signature](https://datatracker.ietf.org/doc/html/rfc7515)（JWS）、[JSON Web Encryption](https://datatracker.ietf.org/doc/html/rfc7516)（JWE）（1.19.7）和嵌套JWT（1.21.0）。該模塊可用於[OpenID Connect](http://openid.net/specs/openid-connect-core-1_0.html)身份驗證。

該模塊可以通過[satisfy](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy)指令與其他訪問模塊（如[ngx\_http\_access\_module](https://nginx.org/en/docs/http/ngx_http_access_module.html)、[ngx\_http\_auth\_basic\_module](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html)和[ngx\_http\_auth\_request\_module](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html)）組合。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 支持算法

該模塊支持以下JSON Web[Algorithms](https://www.iana.org/assignments/jose/jose.xhtml#web-signature-encryption-algorithms)。

JWS算法：

-   HS256、HS384、HS512
-   RS256、RS384、RS512
-   ES256、ES384、ES512
-   EdDSA（Ed 25519和Ed 448簽名）（1.15.7）
-   PS256、PS384、PS512（1.29.0）

> >在1.13.7版本之前，僅支持HS 256、RS 256、ES 256算法。

JWE內容加密算法（1.19.7）：

-   A128CBC-HS256，A192CBC-HS384，A256CBC-HS512
-   A128GCM，A192GCM，A256GCM

JWE密鑰管理算法（1.19.9）：

-   A128KW，A192KW，A256KW
-   A128GCMKW、A192GCMKW、A256GCMKW
-   dir -直接使用共享對稱密鑰作為內容加密密鑰
-   RSA-OAEP、RSA-OAEP-256、RSA-OAEP-384、RSA-OAEP-512（1.21.0）

#### 配置示例

> location / {
>     auth\_jwt          "closed site";
>     auth\_jwt\_key\_file conf/keys.json;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt</strong> <code><i>string</i></code> [<code>token=</code><code><i>$variable</i></code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_jwt off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

啟用JSON Web令牌的驗證。指定的`*string*`用作`realm`。參數值可以包含變量。

可選的`token`參數指定一個包含JSON Web Token的變量。默認情況下，JWT在「Authorization」頭中作為[Bearer Token](https://datatracker.ietf.org/doc/html/rfc6750)傳遞。JWT也可以作為cookie或查詢字符串的一部分傳遞：

> auth\_jwt "closed site" token=$cookie\_auth\_token;

特殊值`off`取消了從上一配置級別繼承的`auth_jwt`指令的效果。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_claim_set</strong> <code><i>$variable</i></code> <code><i>name</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

此指令出現在版本1.11.10中。

將`*variable*`設置為由鍵名標識的JWT聲明參數。名稱匹配從JSON樹的頂層開始。對於數組，該變量保留由逗號分隔的數組元素列表。

> auth\_jwt\_claim\_set $email info e-mail;
> auth\_jwt\_claim\_set $job info "job title";

> >在1.13.7版本之前，只能指定一個鍵名，並且數組的結果是未定義的。

> >使用JWE加密的令牌的變量值僅在[Access](https://nginx.org/en/docs/dev/development_guide.html#http_phases)階段進行解密後可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_header_set</strong> <code><i>$variable</i></code> <code><i>name</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

此指令出現在版本1.11.10中。

將`*variable*`設置為由鍵名標識的JOSE頭參數。名稱匹配從JSON樹的頂層開始。對於數組，該變量保留由逗號分隔的數組元素列表。

> >在1.13.7版本之前，只能指定一個鍵名，並且數組的結果是未定義的。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_key_cache</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_jwt_key_cache 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.21.4版中。

啟用或禁用對從[file](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html#auth_jwt_key_file)或[subrequest](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html#auth_jwt_key_request)獲取的鍵的緩存，並設置緩存時間。不支持緩存從變量獲取的鍵。默認情況下，禁用對鍵的緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_key_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

指定[JSON Web Key Set](https://datatracker.ietf.org/doc/html/rfc7517#section-5)格式的`*file*`以驗證JWT簽名。參數值可以包含變量。

可以在同一級別上指定多個`auth_jwt_key_file`指令（1.21.1）：

> auth\_jwt\_key\_file conf/keys.json;
> auth\_jwt\_key\_file conf/key.jwk;

如果至少有一個指定的鍵無法加載或處理，nginx將返回500（內部伺服器錯誤）錯誤。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_key_request</strong> <code><i>uri</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

此指令出現在1.15.6版中。

允許從子請求中檢索[JSON Web Key Set](https://datatracker.ietf.org/doc/html/rfc7517#section-5)文件，用於驗證JWT簽名，並設置子請求將發送到的URI。參數值可以包含變量。為了避免驗證開銷，建議緩存密鑰文件：

> proxy\_cache\_path /data/nginx/cache levels=1 keys\_zone=foo:10m;
> 
> server {
>     ...
> 
>     location / {
>         auth\_jwt             "closed site";
>         auth\_jwt\_key\_request /jwks\_uri;
>     }
> 
>     location = /jwks\_uri {
>         internal;
>         proxy\_cache foo;
>         proxy\_pass  http://idp.example.com/keys;
>     }
> }

可以在同一級別上指定多個`auth_jwt_key_request`指令（1.21.1）：

> auth\_jwt\_key\_request /jwks\_uri;
> auth\_jwt\_key\_request /jwks2\_uri;

如果至少有一個指定的鍵無法加載或處理，nginx將返回500（內部伺服器錯誤）錯誤。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_leeway</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_jwt_leeway 0s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在版本1.13.10中。

設置在驗證[exp](https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.4)和[nbf](https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.5)JWT聲明時補償時鐘偏差的最大允許裕度。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_type</strong> <code>signed</code> | <code>encrypted</code> | <code>nested</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>auth_jwt_type signed;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

此指令出現在1.19.7版本中。

指定需要哪種類型的JSON Web令牌：JWS（`signed`）、JWE（`encrypted`）或簽名然後加密的嵌套JWT（`nested`）（1.21.0）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>auth_jwt_require</strong> <code><i>$value</i></code> ... [<code>error</code>=<code>401</code> | <code>403</code>] ;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

此指令出現在1.21.2版中。

指定JWT驗證的附加檢查。值可以包含文本、變量及其組合，並且必須以變量（1.21.7）開頭。僅當所有值不為空且不等於「0」時，身份驗證才會成功。

> map $jwt\_claim\_iss $valid\_jwt\_iss {
>     "good" 1;
> }
> ...
> 
> auth\_jwt\_require $valid\_jwt\_iss;

如果任何檢查失敗，則返回`401`錯誤代碼。可選的`error`參數（1.21.7）允許將錯誤代碼重新定義為`403`。

#### 嵌入變量

`ngx_http_auth_jwt_module`模塊支持嵌入變量：

`$jwt_header_``*name*`

返回指定[JOSE header](https://datatracker.ietf.org/doc/html/rfc7515#section-4)的值

`$jwt_claim_``*name*`

返回指定[JWT claim](https://datatracker.ietf.org/doc/html/rfc7519#section-4)的值

對於嵌套聲明和包含點（「.」）的聲明，不能計算變量的值;應使用[auth\_jwt\_claim\_set](https://nginx.org/en/docs/http/ngx_http_auth_jwt_module.html#auth_jwt_claim_set)指令。

使用JWE加密的令牌的變量值僅在[Access](https://nginx.org/en/docs/dev/development_guide.html#http_phases)階段進行解密後可用。

`$jwt_payload`

返回`nested`或`encrypted`令牌的解密頂級負載（1.21.2）。對於嵌套令牌，返回封裝的JWS令牌。對於加密令牌，返回帶有聲明的JSON。