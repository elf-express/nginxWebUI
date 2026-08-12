# page

> Source: https://nginx.org/en/docs/http/ngx_http_session_log_module.html

---

## 目錄

- [Module ngx\_http\_session\_log\_module](#module-ngxhttpsessionlogmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_session\_log\_module

`ngx_http_session_log_module`模塊啟用記錄會話（即，多個HTTP請求的聚合），而不是單個HTTP請求。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

以下配置設置會話日誌，並根據請求客戶端地址和「User-Agent」請求頭欄位將請求映射到會話：

```nginx
    session_log_zone /path/to/log format=組合
                     區域= 1：1 m超時= 30 s
                     md5=$binary_remote_addr$http_user_agent;

    location /media/ {
        session_log one;
    }
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>session_log</strong> <code><i>name</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>session_log off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

允許使用指定的會話日誌。特殊值`off`取消了從前一個配置級別繼承的`session_log`指令的效果。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>session_log_format</strong> <code><i>name</i></code> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>session_log_format combined "...";</pre></td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

指定日誌的輸出格式。`$body_bytes_sent`變量的值在會話中的所有請求之間聚合。可用於記錄的所有其他變量的值對應於會話中的第一個請求。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>session_log_zone</strong> <code><i>path</i></code> <code>zone</code>=<code><i>name</i></code>:<code><i>size</i></code> [<code>format</code>=<code><i>format</i></code>] [<code>timeout</code>=<code><i>time</i></code>] [<code>id</code>=<code><i>id</i></code>] [<code>md5</code>=<code><i>md5</i></code>] ;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

設置日誌文件的路徑並配置用於存儲當前活動會話的共享內存區域。

只要會話中的最後一個請求後經過的時間不超過指定的`timeout`（默認情況下為30秒），會話就被認為是活動的。一旦會話不再活動，則將其寫入日誌。

`id`參數標識請求映射到的會話。`id`參數設置為MD5哈希的十六進位表示（例如，使用變量從cookie中獲取）。如果未指定此參數或此參數不表示有效的MD5哈希，nginx根據`md5`參數的值計算MD5哈希值，並使用此哈希值創建一個新會話。`id`和`md5`參數都可以包含變量。

`format`參數設置由[session\_log\_format](https://nginx.org/en/docs/http/ngx_http_session_log_module.html#session_log_format)指令配置的自定義會話日誌格式。如果未指定`format`，則使用預定義的「`combined`」格式。

#### 嵌入變量

`ngx_http_session_log_module`模塊支持兩個嵌入變量：

`$session_log_id`

當前會話ID;

`$session_log_binary_id`

二進位形式的當前會話ID（16位元組）。