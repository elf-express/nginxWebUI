# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_log_module.html

---

## 目錄

- [Module ngx\_stream\_log\_module](#module-ngxstreamlogmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_log\_module

`ngx_stream_log_module`模塊（1.11.4）以指定格式寫入會話日誌。

#### 配置示例

> log\_format basic '$remote\_addr \[$time\_local\] '
>                  '$protocol $status $bytes\_sent$bytes\_received '
>                  '$session\_time';
> 
> access\_log /spool/logs/nginx-access.log basic buffer=32k;

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>access_log</strong> <code><i>path</i></code> <code><i>format</i></code> [<code>buffer</code>=<code><i>size</i></code>] [<code>gzip[=<code><i>level</i></code>]</code>] [<code>flush</code>=<code><i>time</i></code>] [<code>if</code>=<code><i>condition</i></code>];</code><br><code><strong>access_log</strong> <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>access_log off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

設置緩衝日誌寫入的路徑、[format](https://nginx.org/en/docs/stream/ngx_stream_log_module.html#log_format)和配置。可以在同一配置級別上指定多個日誌。可以通過在第一個參數中指定「`syslog:`」前綴來配置日誌記錄到[syslog](https://nginx.org/en/docs/syslog.html)。特殊值`off`取消當前級別上的所有`access_log`指令。

如果使用了`buffer`或`gzip`參數，則對日誌的寫入將被緩衝。

> >緩衝區的大小不能超過對磁碟文件的原子寫入的大小。對於FreeBSD，這個大小是無限制的。

啟用緩衝後，數據將寫入文件：

-   如果下一個日誌行不適合緩衝器;
-   如果緩衝的數據比`flush`參數指定的舊;
-   當工作進程是[re-opening](https://nginx.org/en/docs/control.html)日誌文件或正在關閉時。

如果使用`gzip`參數，則緩衝數據將在寫入文件之前進行壓縮。壓縮級別可以設置為1（最快，壓縮更少）和9（最慢，最佳壓縮）。默認情況下，緩衝區大小等於64 K字節，壓縮級別設置為1。由於數據是以原子塊壓縮的，日誌文件可以在任何時候被「`zcat`」解壓縮或讀取。

Example:

> access\_log /path/to/log.gz basic gzip flush=5m;

> >要使用gzip壓縮，nginx必須使用zlib庫構建。

文件路徑可以包含變量，但這樣的日誌有一些限制：

-   其憑據由輔助進程使用的[user](https://nginx.org/en/docs/ngx_core_module.html#user)應該具有在具有此類日誌的目錄中創建文件的權限;
-   緩衝寫入不起作用;
-   對於每次日誌寫入，都會打開和關閉該文件。但是，由於經常使用的文件的描述符可以存儲在[cache](https://nginx.org/en/docs/stream/ngx_stream_log_module.html#open_log_file_cache)中，因此在[open\_log\_file\_cache](https://nginx.org/en/docs/stream/ngx_stream_log_module.html#open_log_file_cache)指令的`valid`參數指定的時間內，可以繼續寫入舊文件

`if`參數啟用條件日誌記錄。如果`*condition*`的計算結果為「0」或空字符串，則不會記錄會話。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>log_format</strong> <code><i>name</i></code> [<code>escape</code>=<code>default</code>|<code>json</code>|<code>none</code>] <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

指定日誌格式，例如：

> log\_format proxy '$remote\_addr \[$time\_local\] '
>                  '$protocol $status $bytes\_sent$bytes\_received '
>                  '$session\_time "$upstream\_addr" '
>                  '"$upstream\_bytes\_sent" "$upstream\_bytes\_received" "$upstream\_connect\_time"';

`escape`參數（1.11.8）允許在變量中設置`json`或`default`字符轉義，默認使用`default`轉義。`none`參數（1.13.10）禁止轉義。

對於`default`轉義，字符「`"`"、「`\`"和其他值小於32或大於126的字符將被轉義為「`\xXX`"。如果未找到變量值，則將記錄連字符（「`-`」）。

對於`json`轉義，JSON[strings](https://datatracker.ietf.org/doc/html/rfc8259#section-7)中不允許的所有字符都將被轉義：字符「`"`」和「`\`」被轉義為「`\"`」和「`\\`"，值小於32的字符被轉義為「`\n`"、「`\r`"、「`\t`"、「`\b`"、「`\f`"或「`\u00XX`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>open_log_file_cache</strong> <code>max</code>=<code><i>N</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>min_uses</code>=<code><i>N</i></code>] [<code>valid</code>=<code><i>time</i></code>];</code><br><code><strong>open_log_file_cache</strong> <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>open_log_file_cache off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

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

> open\_log\_file\_cache max=1000 inactive=20s valid=1m min\_uses=2;