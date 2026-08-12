# page

> Source: https://nginx.org/en/docs/http/ngx_http_rewrite_module.html

---

## 目錄

- [Module ngx\_http\_rewrite\_module](#module-ngxhttprewritemodule)
    - [Directives](#directives)
    - [Internal Implementation](#internal-implementation)

---

## Module ngx\_http\_rewrite\_module

`ngx_http_rewrite_module`模塊用於使用PCRE正則表達式更改請求URI、返回重定向和有條件地選擇配置。

按以下順序處理[break](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#break)、[if](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#if)、[return](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#return)、[rewrite](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#rewrite)和[set](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#set)指令：

-   在[server](https://nginx.org/en/docs/http/ngx_http_core_module.html#server)層指定的該模塊的指令按順序執行;
-   repeatedly:
    -   基於請求URI搜索[location](https://nginx.org/en/docs/http/ngx_http_core_module.html#location);
    -   在找到的位置內指定的該模塊的指令被順序執行;
    -   如果請求URI為[rewritten](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#rewrite)但不超過[10 times](https://nginx.org/en/docs/http/ngx_http_core_module.html#internal)，則重複該循環。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>break</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code>, <code>if</code><br></td></tr></tbody></table>

停止處理當前的`ngx_http_rewrite_module`指令集。

如果在[location](https://nginx.org/en/docs/http/ngx_http_core_module.html#location)中指定了指令，則在此位置繼續對請求進行進一步處理。

Example:

```nginx
if ($slow) {
    limit_rate 10k;
    break;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>if</strong> (<code><i>condition</i></code>) { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code><br></td></tr></tbody></table>

計算指定的`*condition*`。如果為true，則執行大括號內指定的此模塊指令，並將`if`指令內的配置分配給請求。`if`指令內的配置繼承自上一個配置級別。

條件可以是以下任何一種：

-   變量名;如果變量的值為空字符串或"`0`"，則為false;
    
    > >在1.0.1版本之前，任何以"`0`"開頭的字符串都被認為是假值。
    
-   使用"`=`"和"`!=`"運算符比較變量和字符串;
-   使用"`~`"將變量與正則表達式進行匹配（用於區分大小寫匹配）和"`~*`"（用於不區分大小寫的匹配）運算符。正則表達式可以包含捕獲，這些捕獲可用於以後在`$1`..`$9`變量中重用。還可以使用否定運算符"`!~`"和"`!~*`"。如果正則表達式包含"`}`"或"`;`"字符，整個表達式應該用單引號或雙引號括起來。
-   使用"`-f`"和"`!-f`"操作符檢查文件是否存在;
-   使用"`-d`"和"`!-d`"操作符檢查目錄是否存在;
-   使用"`-e`"和"`!-e`"操作符檢查文件、目錄或符號連結是否存在;
-   使用"`-x`"和"`!-x`"運算符檢查可執行文件。

Examples:

```nginx
if ($http_user_agent ~ MSIE) {
    rewrite ^(.*)$ /msie/$1 break;
}

if ($http_cookie ~* "id=([^;]+)(?:;|$)") {
    set $id $1;
}

if ($request_method = POST) {
    return 405;
}

if ($slow) {
    limit_rate 10k;
}

if ($invalid_referer) {
    return 403;
}
```

> >`$invalid_referer`嵌入變量的值由[valid\_referers](https://nginx.org/en/docs/http/ngx_http_referer_module.html#valid_referers)指令設置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>return</strong> <code><i>code</i></code> [<code><i>text</i></code>];</code><br><code><strong>return</strong> <code><i>code</i></code> <code><i>URL</i></code>;</code><br><code><strong>return</strong> <code><i>URL</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code>, <code>if</code><br></td></tr></tbody></table>

停止處理並將指定的`*code*`返回給客戶端。非標準代碼444關閉連接而不發送響應頭。

從版本0.8.42開始，可以指定重定向URL（對於代碼301、302、303、307和308）或響應體`*text*`（用於其他代碼）。響應正文文本和重定向URL可以包含變量。作為特殊情況，重定向URL可以指定為此伺服器本地的URI，在這種情況下，根據請求方案（`$scheme`）以及[server\_name\_in\_redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#server_name_in_redirect)和[port\_in\_redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#port_in_redirect)指令形成完整的重定向URL。

另外，可以指定一個用於臨時重定向的`*URL*`作為唯一參數，代碼為302。這樣的參數應該以「`http://`"、「`https://`"或「`$scheme`」字符串開頭。`*URL*`可以包含變量。

> >在0.7.51版本之前，只能返回以下代碼：204、400、402 - 406、408、410、411、413、416和500 - 504。

> 代碼307在版本1.1.16和1.0.13之前沒有被視為重定向。

> 代碼308在版本1.13.0之前不被視為重定向。

另請參見[error\_page](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>rewrite</strong> <code><i>regex</i></code> <code><i>replacement</i></code> [<code><i>flag</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code>, <code>if</code><br></td></tr></tbody></table>

如果指定的正則表達式與請求URI匹配，則URI將按照`*replacement*`字符串中的指定進行更改。`rewrite`指令將按照它們在配置文件中出現的順序依次執行。可以使用標誌終止對指令的進一步處理。如果替換字符串以「`http://`"、「`https://`"或「`$scheme`"開頭，則處理停止並且重定向被返回到客戶端。

可選的`*flag*`參數可以是以下參數之一：

`last`

停止處理當前的`ngx_http_rewrite_module`指令集，並開始搜索與更改的URI匹配的新位置;

`break`

停止處理當前的`ngx_http_rewrite_module`指令集，就像處理[break](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#break)指令一樣;

`redirect`

返回帶有302代碼的臨時重定向;如果替換字符串不以「`http://`"、「`https://`"或「`$scheme`"開頭，則使用該代碼;

`permanent`

返回帶有301代碼的永久重定向。

完整的重定向URL是根據請求方案（`$scheme`）以及[server\_name\_in\_redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#server_name_in_redirect)和[port\_in\_redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#port_in_redirect)指令形成的。

Example:

```nginx
server {
    ...
    rewrite ^(/download/.*)/media/(.*)\\..*$ $1/mp3/$2.mp3 last;
    rewrite ^(/download/.*)/audio/(.*)\\..*$ $1/mp3/$2.ra  last;
    return  403;
    ...
}
```

但是如果這些指令放在「`/download/`」位置，`last`標誌應該替換為`break`，否則nginx將進行10個周期並返回500錯誤：

```nginx
location /download/ {
    rewrite ^(/download/.*)/media/(.*)\\..*$ $1/mp3/$2.mp3 break;
    rewrite ^(/download/.*)/audio/(.*)\\..*$ $1/mp3/$2.ra  break;
    return  403;
}
```

如果`*replacement*`字符串包含新的請求參數，則會將先前的請求參數追加在它們之後。如果不希望這樣，可以在替換字符串的末尾加上問號，以避免追加它們，例如：

```nginx
rewrite ^/users/(.*)$ /show?user=$1? last;
```

如果正則表達式中包含「`}`」或「`;`」字符，則整個表達式都應該用單引號或雙引號引起來。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>rewrite_log</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>rewrite_log off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if</code><br></td></tr></tbody></table>

啟用或禁用將`ngx_http_rewrite_module`模塊指令處理結果記錄到`notice`級別的[error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log)中。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>set</strong> <code><i>$variable</i></code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code>, <code>if</code><br></td></tr></tbody></table>

為指定的`*variable*`設置一個`*value*`。`*value*`可以包含文本、變量及其組合。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>uninitialized_variable_warn</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>uninitialized_variable_warn on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if</code><br></td></tr></tbody></table>

控制是否記錄有關未初始化變量的警告。

#### 內部實現

`ngx_http_rewrite_module`模塊指令在配置階段被編譯成內部指令，在請求處理期間被解釋。解釋器是一個簡單的虛擬堆棧機。

例如，指令

```nginx
location /download/ {
    if ($forbidden) {
        return 403;
    }

    if ($slow) {
        limit_rate 10k;
    }

    rewrite ^/(download/.*)/media/(.*)\\..*$ /$1/mp3/$2.mp3 break;
}
```

將被翻譯成這些指令：

```nginx
>變量$禁止
>檢查零
    return 403
    代碼結束
>變量$slow
>檢查零
>正則表達式匹配
copy "/"
copy $1
copy「/mp3/」
copy $2
>複製「.mp3」
>正則表達式的結尾
>代碼結束
```

請注意，上面的[limit\_rate](https://nginx.org/en/docs/http/ngx_http_core_module.html#limit_rate)指令沒有說明，因為它與`ngx_http_rewrite_module`模塊無關。為[if](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html#if)塊創建單獨的配置。如果條件為真，則將此配置分配給請求，其中`limit_rate`等於10 k。

該指令

```nginx
rewrite ^/(download/.*)/media/(.*)\\..*$ /$1/mp3/$2.mp3 break;
```

如果正則表達式中的第一個斜槓放在括號內，則可以通過一條指令使其變小：

```nginx
rewrite ^(**/**download/.*)/media/(.*)\\..*$ $1/mp3/$2.mp3 break;
```

相應的指令將如下所示：

```
>正則表達式匹配
copy $1
copy「/mp3/」
copy $2
>複製「.mp3」
>正則表達式的結尾
>代碼結束
```