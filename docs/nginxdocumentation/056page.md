# page

> Source: https://nginx.org/en/docs/http/ngx_http_log_module.html

---

## 目錄

- [Module ngx\_http\_log\_module](#module-ngxhttplogmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_log\_module

`ngx_http_log_module`模塊以指定格式寫入請求日誌。

請求記錄在處理結束的位置的上下文中。如果在請求處理期間發生[internal redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#internal)，則該位置可能與原始位置不同。

#### 配置示例

```nginx
log_format compression '$remote_addr - $remote_user [$time_local] '
                       '"$request」$status $bytes_sent '
                       '"$http_referer" "$http_user_agent" "$gzip_ratio"';

access_log /spool/logs/nginx-access.log compression buffer=32k;
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>access_log</strong> <code><i>path</i></code> [<code><i>format</i></code> [<code>buffer</code>=<code><i>size</i></code>] [<code>gzip[=<code><i>level</i></code>]</code>] [<code>flush</code>=<code><i>time</i></code>] [<code>if</code>=<code><i>condition</i></code>]];</code><br><code><strong>access_log</strong> <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>access_log logs/access.log combined;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code>, <code>limit_except</code><br></td></tr></tbody></table>

設置緩衝日誌寫入的路徑、格式和配置。可以在同一配置級別上指定多個日誌。可以通過在第一個參數中指定「`syslog:`」前綴來配置日誌記錄到[syslog](https://nginx.org/en/docs/syslog.html)。特殊值`off`取消當前級別上的所有`access_log`指令。如果未指定格式，則使用預定義的「`combined`」格式。

如果使用了`buffer`或`gzip`（1.3.10，1.2.7）參數，則對日誌的寫入將被緩衝。

> >緩衝區的大小不能超過對磁碟文件的原子寫入的大小。對於FreeBSD，這個大小是無限制的。

啟用緩衝後，數據將寫入文件：

-   如果下一個日誌行不適合緩衝器;
-   如果緩衝的數據比`flush`參數（1.3.10，1.2.7）指定的數據舊;
-   當工作進程是[re-opening](https://nginx.org/en/docs/control.html)日誌文件或正在關閉時。

如果使用`gzip`參數，則緩衝數據將在寫入文件之前進行壓縮。壓縮級別可以設置為1（最快，壓縮更少）和9（最慢，最佳壓縮）。默認情況下，緩衝區大小等於64 K字節，壓縮級別設置為1。由於數據是以原子塊壓縮的，日誌文件可以在任何時候被「`zcat`」解壓縮或讀取。

Example:

```nginx
access_log /path/to/log.gz combined gzip flush=5m;
```

> >要使用gzip壓縮，nginx必須使用zlib庫構建。

文件路徑可以包含變量（0.7.6+），但這樣的日誌有一些限制：

-   其憑據由輔助進程使用的[user](https://nginx.org/en/docs/ngx_core_module.html#user)應該具有在具有此類日誌的目錄中創建文件的權限;
-   緩衝寫入不起作用;
-   對於每次日誌寫入，都會打開和關閉該文件。但是，由於經常使用的文件的描述符可以存儲在[cache](https://nginx.org/en/docs/http/ngx_http_log_module.html#open_log_file_cache)中，因此在[open\_log\_file\_cache](https://nginx.org/en/docs/http/ngx_http_log_module.html#open_log_file_cache)指令的`valid`參數指定的時間內，可以繼續寫入舊文件
-   在每次日誌寫入期間，都會檢查請求的[root directory](https://nginx.org/en/docs/http/ngx_http_core_module.html#root)是否存在，如果不存在，則不會創建日誌。因此，在同一配置級別上指定[root](https://nginx.org/en/docs/http/ngx_http_core_module.html#root)和`access_log`是一個好主意：
    
    ```nginx
    server {
        root       /spool/vhost/data/$host;
        access_log /spool/vhost/logs/$host;
        ...
    ```
    

`if`參數（1.7.0）啟用條件日誌記錄。如果`*condition*`的計算結果為「0」或空字符串，則不會記錄請求。在以下示例中，響應代碼為2xx和3xx的請求將不會被記錄：

```nginx
map $status $loggable {
    ~^[23]  0;
    default 1;
}

access_log /path/to/access.log combined if=$loggable;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>log_format</strong> <code><i>name</i></code> [<code>escape</code>=<code>default</code>|<code>json</code>|<code>none</code>] <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>log_format combined "...";</pre></td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

指定日誌格式。

`escape`參數（1.11.8）允許在變量中設置`json`或`default`字符轉義，默認使用`default`轉義。`none`值（1.13.10）禁止轉義。

對於`default`轉義，字符「`"`"、「`\`"和其他值小於32（0.7.0）或大於126（1.1.6）的字符將被轉義為「`\xXX`"。如果未找到變量值，則將記錄連字符（「`-`」）。

對於`json`轉義，JSON[strings](https://datatracker.ietf.org/doc/html/rfc8259#section-7)中不允許的所有字符都將被轉義：字符「`"`」和「`\`」被轉義為「`\"`」和「`\\`"，值小於32的字符被轉義為「`\n`"、「`\r`"、「`\t`"、「`\b`"、「`\f`"或「`\u00XX`"。

日誌格式可以包含常見變量和僅在日誌寫入時存在的變量：

`$bytes_sent`

發送到客戶端的字節數

`$connection`

連接序號

`$connection_requests`

當前通過連接發出的請求數（1.1.18）

`$msec`

日誌寫入時的時間（以秒為單位，解析度為毫秒

`$pipe`

「`p`」（如果請求是流水線的），「`.`」（否則）

`$request_length`

請求長度（包括請求行、請求頭和請求體）

`$request_time`

請求處理時間（以秒為單位，解析度為毫秒）;從客戶端讀取第一個字節到將最後一個字節發送到客戶端後寫入日誌之間經過的時間

`$status`

響應狀態

`$time_iso8601`

ISO 8601標準格式的本地時間

`$time_local`

通用日誌格式中的本地時間

> >在現代nginx版本中變量[$status](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_status)（1.3.2，1.2.2），[$bytes\_sent](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_bytes_sent)（1.3.8，1.2.5），[$connection](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_connection)（1.3.8，1.2.5），[$connection\_requests](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_connection_requests)（1.3.8，1.2.5），[$msec](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_msec)（1.3.9，1.2.6）、[$request\_time](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_request_time)（1.3.9，1.2.6）、[$pipe](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_pipe)（1.3.12，1.2.7）、[$request\_length](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_request_length)（1.3.12，1.2.7）、[$time\_iso8601](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_time_iso8601)（1.3.12，1.2.7）和[$time\_local](https://nginx.org/en/docs/http/ngx_http_core_module.html#var_time_local)（1.3.12，1.2.7）也可用作常用變量。

發送到客戶端的標題行具有前綴「`sent_http_`"，例如`$sent_http_content_range`。

配置始終包含預定義的「`combined`」格式：

```nginx
log_format combined '$remote_addr - $remote_user [$time_local] '
                    '"$request」$status $body_bytes_sent '
                    '"$http_referer" "$http_user_agent"';
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>open_log_file_cache</strong> <code>max</code>=<code><i>N</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>min_uses</code>=<code><i>N</i></code>] [<code>valid</code>=<code><i>time</i></code>];</code><br><code><strong>open_log_file_cache</strong> <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>open_log_file_cache off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義一個緩存，用於存儲頻繁使用的日誌的文件描述符，這些日誌的名稱包含變量。該指令具有以下參數：

`max`

設置緩存中描述符的最大數量;如果該高速緩存已滿，則關閉最近最少使用（LRU）的描述符

`inactive`

設置關閉緩存描述符的時間（如果在此時間內沒有訪問）;默認情況下為10秒

`min_uses`

設置在由`inactive`參數定義的時間內文件使用的最小數量，以使描述符在緩存中保持打開狀態;默認情況下為1

`valid`

設置檢查文件是否仍以相同名稱存在的時間;默認情況下為60秒

`off`

禁用緩存

使用示例：

```nginx
open_log_file_cache max=1000 inactive=20s valid=1m min_uses=2;
```