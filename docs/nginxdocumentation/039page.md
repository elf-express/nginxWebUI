# page

> Source: https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html

---

## 目錄

- [Module ngx\_http\_fastcgi\_module](#module-ngxhttpfastcgimodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Parameters Passed to a FastCGI Server](#parameters-passed-to-a-fastcgi-server)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_fastcgi\_module

`ngx_http_fastcgi_module`模塊允許將請求傳遞到FastCGI伺服器。

#### 配置示例

```nginx
location / {
    fastcgi_pass  localhost:9000;
    fastcgi_index index.php;

    fastcgi_param SCRIPT_FILENAME /home/www/scripts/php$fastcgi_script_name;
    fastcgi_param QUERY_STRING    $query_string;
    fastcgi_param REQUEST_METHOD  $request_method;
    fastcgi_param CONTENT_TYPE    $content_type;
    fastcgi_param CONTENT_LENGTH  $content_length;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_allow_upstream</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

定義允許訪問FastCGI伺服器的條件或[denied](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#denied)。如果所有字符串參數都不為空且不等於「0」，則允許訪問。每次在建立到FastCGI伺服器的連接之前，都會評估這些條件。參數值可以包含變量：

```nginx
geo $upstream_last_addr $allow {
    volatile;
    10.10.0.0/24        1;
}

server {
    listen 127.0.0.1:8080;

    location / {
        fastcgi_pass           localhost:9000;
        fastcgi_allow_upstream $allow;
        ...
    }
}
```

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_bind</strong> <code><i>address</i></code> [<code>transparent</code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在0.8.22版本中。

使用可選埠（1.11.2）從指定的本地IP位址發起到FastCGI伺服器的傳出連接。參數值可以包含變量（1.3.12）。特殊值`off`（1.3.12）取消了從上一配置級別繼承的`fastcgi_bind`指令的效果，該指令允許系統自動分配本地IP位址和埠。

`transparent`參數（1.11.0）允許從非本地IP位址（例如，從客戶端的真實的IP位址）發出到FastCGI伺服器的傳出連接：

```nginx
fastcgi_bind $remote_addr transparent;
```

為了使此參數生效，通常需要以[superuser](https://nginx.org/en/docs/ngx_core_module.html#user)權限運行nginx工作進程。在Linux上，不需要（1.13.8），因為如果指定了`transparent`參數，工作進程將從主進程繼承`CAP_NET_RAW`功能。還需要配置內核路由表以攔截來自FastCGI伺服器的網絡流量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

啟用後，在每次連接嘗試時執行[bind](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_bind)操作。

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置緩衝區的`*size*`，緩衝區用於閱讀從FastCGI伺服器接收的響應的第一部分。這部分通常包含一個小的響應頭;如果它超過緩衝區大小，則響應被視為[invalid](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#invalid_header)。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小可以是4K或8 K。但是，它可以更小。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_buffering</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_buffering on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.6版中。

啟用或禁用FastCGI伺服器響應的緩衝。

當緩衝被啟用時，nginx會儘快從FastCGI伺服器接收響應，並將其保存到由[fastcgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffer_size)和[fastcgi\_buffers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffers)指令設置的緩衝區中。如果整個響應不適合內存，則可以將其一部分保存到磁碟上的[temporary file](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_temp_path)中。寫入臨時文件由[fastcgi\_max\_temp\_file\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_max_temp_file_size)和[fastcgi\_temp\_file\_write\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_temp_file_write_size)指令控制。

當緩衝被禁用時，響應會在收到時立即同步傳遞給客戶端。nginx不會嘗試從FastCGI伺服器讀取整個響應。nginx一次可以從伺服器接收的最大數據大小由[fastcgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffer_size)指令設置。

還可以通過在「X-Accel-Buffering」響應頭欄位中傳遞「`yes`」或「`no`」來啟用或禁用緩衝。可以使用[fastcgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_ignore_headers)指令禁用此功能。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_buffers 8 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為單個連接設置用於從FastCGI伺服器閱讀響應的緩衝區的`*number*`和`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小可以是4K或8 K。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_busy_buffers_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_busy_buffers_size 8k|16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

當啟用來自FastCGI伺服器的[buffering](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffering)個響應時，將限制在響應尚未完全讀取時可能忙碌向客戶端發送響應的緩衝區總數`*size*`。同時，其餘緩衝區可用於閱讀響應，並在需要時將部分響應緩衝到臨時文件中。默認情況下，`*size*`受[fastcgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffer_size)和[fastcgi\_buffers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffers)指令設置的兩個緩衝區的大小限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache</strong> <code><i>zone</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義用於緩存的共享內存區域。同一區域可以在多個地方使用。參數值可以包含變量（1.7.9）。`off`參數禁用從上一配置級別繼承的緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_background_update</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_background_update off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在版本1.11.10中。

允許啟動後台子請求來更新過期的緩存項，同時向客戶端返回過期的緩存響應。請注意，在更新過期的緩存響應時，需要[allow](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_use_stale_updating)其使用情況。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_bypass</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義不從緩存中獲取響應的條件。如果字符串參數中至少有一個值不為空且不等於「0」，則不從該高速緩存中獲取響應：

```nginx
fastcgi_cache_bypass $cookie_nocache $arg_nocache$arg_comment;
fastcgi_cache_bypass $http_pragma    $http_authorization;
```

可與[fastcgi\_no\_cache](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_no_cache)指令一起沿著使用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_key</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義用於緩存的鍵，例如

```nginx
fastcgi_cache_key localhost:9000$request_uri;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_lock</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_lock off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.12版本中。

啟用後，每次只允許一個請求通過將請求傳遞到FastCGI伺服器來填充根據[fastcgi\_cache\_key](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_key)指令標識的新緩存元素。同一緩存元素的其他請求將等待該高速緩存中出現響應或釋放該元素的該高速緩存鎖，直到[fastcgi\_cache\_lock\_timeout](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_lock_timeout)指令設置的時間。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_lock_age</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_lock_age 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.8版本中。

如果傳遞給FastCGI伺服器的用於填充新緩存元素的最後一個請求對於指定的`*time*`尚未完成，則可以再傳遞一個請求到FastCGI伺服器。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_lock_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_lock_timeout 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.12版本中。

設置[fastcgi\_cache\_lock](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_lock)的超時。當`*time*`超時時，請求將被傳遞到FastCGI伺服器，但是，響應將不會被緩存。

> >在1.7.8之前，響應可以被緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_max_range_offset</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.11.6版中。

設置字節範圍請求的偏移量（以字節為單位）。如果範圍超出偏移量，則範圍請求將被傳遞到FastCGI伺服器，並且響應將不被緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_methods</strong> <code>GET</code> | <code>HEAD</code> | <code>POST</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_methods GET HEAD;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在0.7.59版本中。

如果客戶端請求方法在此指令中列出，則響應將被緩存。「`GET`」和「`HEAD`」方法總是添加到列表中，但建議顯式指定它們。另請參閱[fastcgi\_no\_cache](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_no_cache)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_min_uses</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_min_uses 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置請求的`*number*`，在此之後將緩存響應。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_path</strong> <code><i>path</i></code> [<code>levels</code>=<code><i>levels</i></code>] [<code>use_temp_path</code>=<code>on</code>|<code>off</code>] <code>keys_zone</code>=<code><i>name</i></code>:<code><i>size</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>max_size</code>=<code><i>size</i></code>] [<code>min_free</code>=<code><i>size</i></code>] [<code>manager_files</code>=<code><i>number</i></code>] [<code>manager_sleep</code>=<code><i>time</i></code>] [<code>manager_threshold</code>=<code><i>time</i></code>] [<code>loader_files</code>=<code><i>number</i></code>] [<code>loader_sleep</code>=<code><i>time</i></code>] [<code>loader_threshold</code>=<code><i>time</i></code>] [<code>purger</code>=<code>on</code>|<code>off</code>] [<code>purger_files</code>=<code><i>number</i></code>] [<code>purger_sleep</code>=<code><i>time</i></code>] [<code>purger_threshold</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

設置緩存的路徑和其他參數。緩存數據存儲在文件中。緩存中的鍵和文件名都是對代理URL應用MD5函數的結果。`levels`參數定義緩存的層次結構級別：從1到3，每個級別接受值1或2。例如，在以下配置中

```nginx
fastcgi_cache_path /data/nginx/cache levels=1:2 keys_zone=one:10m;
```

緩存中的文件名如下所示：

```
/data/nginx/cache/**c**/**29**/b7f54b2df7773722d382f4809d650**29c**
```

一個緩存的響應先寫到一個臨時文件，然後文件重命名，從0. 8. 9版本開始，臨時文件和該高速緩存可以放在不同的文件系統上，但是，請注意，在這種情況下，文件是跨兩個文件系統複製的，而不是廉價的重命名操作。因此，建議對於任何給定的位置，都啟用緩存和保存臨時文件的目錄相同的文件系統。臨時文件的目錄是根據`use_temp_path`參數（1.7.10）設置的。如果該參數被省略或設置為值`on`，則將使用[fastcgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_temp_path)指令為給定位置設置的目錄。如果該值設置為`off`，則臨時文件將直接放在該高速緩存目錄中。

此外，所有活動密鑰和有關數據的信息都存儲在一個共享內存區中，其`*name*`和`*size*`由`keys_zone`參數配置。一兆字節區可存儲約八千個密鑰。

> >作為[commercial subscription](https://www.f5.com/products/nginx)的一部分，共享內存區域還存儲擴展緩存[information](https://nginx.org/en/docs/http/ngx_http_api_module.html#http_caches_)，因此，對於相同數量的密鑰，需要指定更大的區域大小。例如，一兆字節的區域可以存儲大約四千個密鑰。

在`inactive`參數指定的時間內未被訪問的緩存數據將從該高速緩存中刪除，而不管其新鮮度如何。默認情況下，`inactive`設置為10分鐘。

特殊的「緩存管理器」進程監視由`max_size`參數設置的最大緩存大小，以及由`min_free`（1.19.1）帶緩存的文件系統上的參數。當超過大小或沒有足夠的可用空間時，它將刪除最近使用的數據。數據將在由`manager_files`，`manager_threshold`，和`manager_sleep`參數（1.11.5）。在一次疊代中，不超過`manager_files`項被刪除（默認為100）。一次疊代的持續時間由`manager_threshold`參數限制（默認為200毫秒）。在疊代之間，進行由`manager_sleep`參數配置的暫停（默認為50毫秒）。

啟動一分鐘後，特殊的「緩存加載器」進程被激活。它將有關存儲在文件系統上的先前緩存數據的信息加載到緩存區域中。加載也是在疊代中完成的。在一次疊代中，加載不超過`loader_files`項（默認為100），此外，一次疊代的持續時間受`loader_threshold`參數限制（默認為200毫秒）。在疊代之間，進行由`loader_sleep`參數配置的暫停（默認為50毫秒）。

此外，以下參數可作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分：

`purger`\=`on`|`off`

指示該高速緩存清除器是否將從磁碟中刪除與[wildcard key](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_purge)匹配的緩存項（1.7.12）。將該參數設置為`on`（默認值為`off`）將激活「緩存清除器」進程，該進程將永久地遍歷所有緩存項並刪除與該關鍵字匹配的項。

`purger_files`\=`*number*`

設置在一次疊代中要掃描的項目數（1.7.12）。默認情況下，`purger_files`設置為10。

`purger_threshold`\=`*number*`

設置一次疊代的持續時間（1.7.12）。默認情況下，`purger_threshold`設置為50毫秒。

`purger_sleep`\=`*number*`

設置疊代之間的暫停（1.7.12）。默認情況下，`purger_sleep`設置為50毫秒。

> >在1.7.3、1.7.7和1.11.10版本中，緩存頭格式已更改。升級到較新的nginx版本後，以前緩存的響應將被視為無效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_purge</strong> string ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.7版本中。

定義將請求視為緩存清除請求的條件。如果字符串參數中至少有一個值不為空且不等於「0」，則刪除具有對應[cache key](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_key)的該高速緩存條目。操作成功的結果通過返回204（無內容）響應來指示。

如果清除請求的[cache key](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_key)以星號（「`*`」）結尾，則將從該高速緩存中刪除所有與清除鍵匹配的緩存條目。但是，這些條目將保留在磁碟上，直到它們被刪除以用於[inactivity](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_path)或由[cache purger](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#purger)（1.7.12）處理，或者客戶端嘗試訪問它們。

示例配置：

```nginx
fastcgi_cache_path /data/nginx/cache keys_zone=cache_zone:10m;

map $request_method $purge_method {
    PURGE   1;
    default 0;
}

server {
    ...
    location / {
        fastcgi_pass        backend;
        fastcgi_cache       cache_zone;
        fastcgi_cache_key   $uri;
        fastcgi_cache_purge $purge_method;
    }
}
```

> >此功能是我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_revalidate</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_revalidate off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.7版本中。

使用帶有「If-Modified-Since」和「If-None-Match」標頭欄位的條件請求啟用過期緩存項的重新驗證。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_use_stale</strong> <code>error</code> | <code>timeout</code> | <code>invalid_header</code> | <code>updating</code> | <code>http_500</code> | <code>http_503</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_cache_use_stale off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

確定在與FastCGI伺服器通信期間發生錯誤時，在哪些情況下可以使用過時的緩存響應。該指令的參數與[fastcgi\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream)指令的參數匹配。

如果無法選擇FastCGI伺服器來處理請求，`error`參數還允許使用陳舊的緩存響應。

此外，如果當前正在更新緩存響應，則`updating`參數允許使用陳舊的緩存響應。這允許在更新緩存數據時最小化對FastCGI伺服器的訪問次數。

也可以在響應過期後的指定秒數內直接在響應頭中啟用使用過期緩存響應（1.11.10）。這比使用指令參數的優先級低。

-   「Cache-Control」頭欄位的「[stale-while-revalidate](https://datatracker.ietf.org/doc/html/rfc5861#section-3)」擴展允許在當前正在更新的情況下使用陳舊的緩存響應。
-   「Cache-Control」頭欄位的「[stale-if-error](https://datatracker.ietf.org/doc/html/rfc5861#section-4)」擴展允許在出現錯誤時使用過時的緩存響應。

為了在填充新的緩存元素時最小化對FastCGI伺服器的訪問次數，可以使用[fastcgi\_cache\_lock](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_lock)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_cache_valid</strong> [<code><i>code</i></code> ...] <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為不同的響應代碼設置緩存時間。

```nginx
fastcgi_cache_valid 200 302 10m;
fastcgi_cache_valid 404      1m;
```

對代碼為200和302的響應設置10分鐘的緩存，對代碼為404的響應設置1分鐘的緩存。

如果僅指定緩存`*time*`

```nginx
fastcgi_cache_valid 5m;
```

則僅緩存200、301和302響應。

此外，可以指定`any`參數來緩存任何響應：

```nginx
fastcgi_cache_valid 200 302 10m;
fastcgi_cache_valid 301      1h;
fastcgi_cache_valid any      1m;
```

緩存參數也可以直接在響應標頭中設置。這比使用指令設置緩存時間的優先級更高。

-   The “X-Accel-Expires” header field sets caching time of a response in seconds. The zero value disables caching for a response. If the value starts with the `@` prefix, it sets an absolute time in seconds since Epoch, up to which the response may be cached.
-   如果報頭不包括「X-Accel-Expires」欄位，則可以在報頭欄位「Expires」或「Cache-Control」中設置緩存的參數。
-   如果頭包含「Set-Cookie」欄位，則不會緩存此類響應。
-   如果報頭包含具有特殊值「`*`"的「Vary」欄位，則不會緩存這樣的響應（1.7.7）。如果報頭包含具有另一個值的「Vary」欄位，則會考慮相應的請求報頭欄位來緩存這樣的響應（1.7.7）。

可以使用[fastcgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_ignore_headers)指令禁用一個或多個響應頭欄位的處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_catch_stderr</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置要在從FastCGI伺服器接收的響應的錯誤流中搜索的字符串。如果找到`*string*`，則認為FastCGI伺服器已返回[invalid response](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream)。這允許在nginx中處理應用程式錯誤，例如：

```nginx
location /php/ {
    fastcgi_pass backend:9000;
    ...
    fastcgi_catch_stderr "PHP Fatal error";
    fastcgi_next_upstream error timeout invalid_header;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義與FastCGI伺服器建立連接的超時時間。應該注意的是，此超時時間通常不能超過75秒。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_force_ranges</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_force_ranges off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.7版本中。

為來自FastCGI伺服器的緩存和未緩存響應啟用字節範圍支持，而不管這些響應中的「Accept-Ranges」欄位。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_hide_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

默認情況下，nginx不會將FastCGI伺服器響應中的頭欄位「Status」和「X-Accel-...」傳遞給客戶端。`fastcgi_hide_header`指令設置了不會傳遞的附加欄位。相反，如果需要允許傳遞欄位，則可以使用[fastcgi\_pass\_header](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_pass_header)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_ignore_client_abort</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_ignore_client_abort off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

確定當客戶端在沒有等待響應的情況下關閉連接時，是否應該關閉與FastCGI伺服器的連接。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_ignore_headers</strong> <code><i>field</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

禁止處理來自FastCGI伺服器的某些響應標頭欄位。可以忽略以下欄位：「X-Accel-Redirect」、「X-Accel-Expires」、「X-Accel-Limit-Rate」（1.1.6）、「X-Accel-Buffering」（1.1.6）、「X-Accel-Charset」（1.1.6）、「Expires」、「Cache-Control」、「Set-Cookie」（0.8.44）和「Vary」（1.7.7）。

如果未禁用，則處理這些標題欄位具有以下效果：

-   「X-Accel-Expires」、「Expires」、「Cache-Control」、「Set-Cookie」、「Vary」設置響應[caching](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_valid)的參數;
-   「X-Accel-Redirect」對指定的URI執行[internal redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#internal);
-   「X-Accel-Limit-Rate」設置向客戶端發送響應的[rate limit](https://nginx.org/en/docs/http/ngx_http_core_module.html#limit_rate);
-   「X-Accel-Buffering」啟用或禁用響應的[buffering](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffering);
-   「X-Accel-Charset」設置響應的期望[charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_index</strong> <code><i>name</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

在`$fastcgi_script_name`變量的值中設置一個文件名，該文件名將附加在以斜槓結尾的URI之後。例如，使用以下設置

```nginx
fastcgi_index index.php;
fastcgi_param SCRIPT_FILENAME /home/www/scripts/php$fastcgi_script_name;
```

對於「`/page.php`」請求，`SCRIPT_FILENAME`參數將等於「`/home/www/scripts/php/page.php`"，對於「`/`」請求，它將等於「`/home/www/scripts/php/index.php`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_intercept_errors</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_intercept_errors off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

確定代碼大於或等於300的FastCGI伺服器響應是否應該傳遞給客戶端，或者攔截並重定向到nginx以使用[error\_page](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page)指令進行處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_keep_conn</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_keep_conn off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.4版中。

默認情況下，FastCGI伺服器會在發送響應後立即關閉連接。然而，當此指令設置為值`on`時，nginx將指示FastCGI伺服器保持連接打開。這是必要的，特別是對於FastCGI伺服器的[keepalive](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#keepalive)連接功能。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_limit_rate</strong> <code><i>rate</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_limit_rate 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.7版本中。

限制從FastCGI伺服器閱讀響應的速度。`*rate*`以每秒字節數指定。零值禁用速率限制。該限制是根據請求設置的，因此如果nginx同時打開兩個到FastCFI伺服器的連接，總比率將是指定限制的兩倍。僅當[buffering](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffering)時，限制才有效從FastCGI伺服器的響應被啟用。參數值可以包含變量（1.27.0）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_max_temp_file_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_max_temp_file_size 1024m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

當來自FastCGI伺服器的[buffering](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffering)個響應被啟用，並且整個響應不適合由[fastcgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffer_size)和[fastcgi\_buffers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffers)指令設置的緩衝區時，可以將響應的一部分保存到臨時文件中。該指令設置臨時文件的最大值`*size*`。一次寫入臨時文件的數據大小由[fastcgi\_temp\_file\_write\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_temp_file_write_size)指令設置。

零值禁用對臨時文件的響應緩衝。

> >此限制不適用於磁碟上將為[cached](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache)或[stored](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_store)的響應。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_next_upstream</strong> <code>error</code> | <code>timeout</code> | <code>denied</code> | <code>invalid_header</code> | <code>http_500</code> | <code>http_503</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>non_idempotent</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_next_upstream error timeout;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定在哪些情況下應將請求傳遞到下一個伺服器：

`error`

與伺服器建立連接、向其傳遞請求或閱讀響應標頭時出錯;

`timeout`

在與伺服器建立連接、向其傳遞請求或閱讀響應標頭時發生超時;

`denied`

伺服器[denied](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_allow_upstream)連接（1.29.3）;

> >此參數作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分可用。

`invalid_header`

伺服器返回了一個空的或無效的響應;

`http_500`

伺服器返回一個帶有代碼500的響應;

`http_503`

伺服器返回代碼為503的響應;

`http_403`

伺服器返迴響應代碼403;

`http_404`

伺服器返回具有代碼404的響應;

`http_429`

伺服器返回代碼為429（1.11.13）的響應;

`non_idempotent`

通常，如果請求已發送到上游伺服器（1.9.13），則使用[non-idempotent](https://datatracker.ietf.org/doc/html/rfc7231#section-4.2.2)方法（`POST`，`LOCK`，`PATCH`）的請求不會傳遞到下一個伺服器;啟用此選項明確允許重試此類請求;

`off`

禁止將請求傳遞到下一個伺服器。

需要注意的是，只有在客戶端還沒有收到任何消息的情況下，才有可能將請求傳遞給下一個伺服器。也就是說，如果在傳輸響應的過程中發生錯誤或超時，則無法修復。

該指令還定義了與伺服器通信的[unsuccessful attempt](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails)。即使指令中沒有指定，`error`、`timeout`、`denied`和`invalid_header`的情況也總是被認為是不成功的嘗試。`http_500`、`http_503`、和`http_429`只有在指令中指定時才被視為不成功的嘗試。`http_403`和`http_404`的情況永遠不會被視為不成功的嘗試。

將請求傳遞到下一個伺服器可以受到[the number of tries](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream_tries)和[time](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream_timeout)的限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.5版中。

限制可以將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream)的時間。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.5版中。

限制將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream)的可能嘗試次數。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_no_cache</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義響應不會保存到緩存的條件。如果字符串參數中至少有一個值不為空且不等於「0」，則不會保存響應：

```nginx
fastcgi_no_cache $cookie_nocache $arg_nocache$arg_comment;
fastcgi_no_cache $http_pragma    $http_authorization;
```

可與[fastcgi\_cache\_bypass](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_bypass)指令一起沿著使用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_param</strong> <code><i>parameter</i></code> <code><i>value</i></code> [<code>if_not_empty</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_param HTTP_HOST $host$is_request_port$request_port;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置一個應傳遞給FastCGI伺服器的`*parameter*`。`*value*`可以包含文本、變量及其組合。若且唯若當前級別上沒有定義`fastcgi_param`指令時，這些指令才從上一配置級別繼承。

以下示例顯示PHP所需的最低設置：

```nginx
fastcgi_param SCRIPT_FILENAME /home/www/scripts/php$fastcgi_script_name;
fastcgi_param QUERY_STRING    $query_string;
```

在PHP中，`SCRIPT_FILENAME`參數用於確定腳本名稱，`QUERY_STRING`參數用於傳遞請求參數。

對於處理`POST`請求的腳本，還需要以下三個參數：

```nginx
fastcgi_param REQUEST_METHOD  $request_method;
fastcgi_param CONTENT_TYPE    $content_type;
fastcgi_param CONTENT_LENGTH  $content_length;
```

如果PHP是使用`--enable-force-cgi-redirect`配置參數構建的，則`REDIRECT_STATUS`參數也應該使用值「200」傳遞：

```nginx
fastcgi_param REDIRECT_STATUS 200;
```

如果指令是用`if_not_empty`（1.1.11）指定的，那麼只有當它的值不為空時，這樣的參數才會傳遞給伺服器：

```nginx
fastcgi_param HTTPS           $https if_not_empty;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_pass</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

設置FastCGI伺服器的地址。地址可以指定為域名或IP位址以及埠：

```nginx
fastcgi_pass localhost:9000;
```

或者作為UNIX域套接字路徑：

```nginx
fastcgi_pass unix:/tmp/fastcgi.socket;
```

如果一個域名解析為多個地址，所有的地址都將以循環方式使用。此外，地址可以指定為[server group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)。

參數值可以包含變量。在這種情況下，如果地址被指定為域名，則在所描述的[server groups](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)中搜索該名稱，如果沒有找到，則使用[resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver)確定。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_pass_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

允許將[otherwise disabled](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_hide_header)頭欄位從FastCGI伺服器傳遞到客戶端。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_pass_request_body</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_pass_request_body on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指示是否將原始請求正文傳遞到FastCGI伺服器。另請參閱[fastcgi\_pass\_request\_headers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_pass_request_headers)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_pass_request_headers</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_pass_request_headers on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指示是否將原始請求的頭欄位傳遞到FastCGI伺服器。另請參閱[fastcgi\_pass\_request\_body](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_pass_request_body)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_read_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_read_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義從FastCGI伺服器閱讀響應的超時。超時僅在兩個連續的讀取操作之間設置，而不是為整個響應的傳輸設置。如果FastCGI伺服器在此時間內未傳輸任何內容，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_request_buffering</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_request_buffering on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.11版本中。

啟用或禁用客戶端請求正文的緩衝。

當緩衝被啟用時，在將請求發送到FastCGI伺服器之前，整個請求體是來自客戶端的[read](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_buffer_size)。

當緩衝被關閉時，請求體會在收到後立即發送到FastCGI伺服器。在這種情況下，如果nginx已經開始發送請求體，則無法將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_next_upstream)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_request_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_request_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

啟用或禁用為每個FastCGI伺服器創建單獨的請求實例。默認情況下，所有FastCGI伺服器都使用單個請求。如果啟用，則創建單獨的請求實例，從而允許按伺服器自定義請求。

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_send_lowat</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_send_lowat 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果該指令被設置為非零值，nginx將嘗試通過使用[kqueue](https://nginx.org/en/docs/events.html#kqueue)方法的`NOTE_LOWAT`標誌或`SO_SNDLOWAT`socket選項，以及指定的`*size*`，最大限度地減少到FastCGI伺服器的傳出連接上的發送操作數量。

在Linux、Solaris和Windows上忽略此指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_send_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_send_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置向FastCGI伺服器發送請求的超時時間。超時時間只在兩個連續的寫操作之間設置，而不是整個請求的發送。如果FastCGI伺服器在此時間內沒有收到任何東西，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.15.6版中。

為到FastCGI伺服器的傳出連接配置「TCP keepalive」行為。默認情況下，作業系統的設置對套接字有效。如果該指令設置為值「`on`"，則為套接字啟用`SO_KEEPALIVE`socket選項。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_socket_rcvbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

為FastCGI伺服器的輸出連接設置接收緩衝區大小（`SO_RCVBUF`選項）。特殊值`0`取消了從以前的配置級別繼承的`fastcgi_socket_rcvbuf`指令的效果，這允許保持作業系統的設置對套接字有效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_socket_sndbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

為FastCGI伺服器的輸出連接設置發送緩衝區大小（`SO_SNDBUF`選項）。特殊值`0`取消了從以前的配置級別繼承的`fastcgi_socket_sndbuf`指令的效果，這允許保持作業系統的設置對套接字有效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_split_path_info</strong> <code><i>regex</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

定義捕獲`$fastcgi_path_info`變量值的正則表達式。正則表達式應具有兩個捕獲：第一個捕獲為`$fastcgi_script_name`變量的值，第二個捕獲為`$fastcgi_path_info`變量的值。例如，使用以下設置

```nginx
location ~ ^(.+\.php)(.*)$ {
    fastcgi_split_path_info       ^(.+\.php)(.*)$;
    fastcgi_param SCRIPT_FILENAME /path/to/php$fastcgi_script_name;
    fastcgi_param PATH_INFO       $fastcgi_path_info;
```

而「`/show.php/article/0001`」請求，則`SCRIPT_FILENAME`參數將等於「`/path/to/php/show.php`"，`PATH_INFO`參數將等於「`/article/0001`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_store</strong> <code>on</code> | <code>off</code> | <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_store off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

允許將文件保存到磁碟。`on`參數使用與指令[alias](https://nginx.org/en/docs/http/ngx_http_core_module.html#alias)或[root](https://nginx.org/en/docs/http/ngx_http_core_module.html#root)對應的路徑保存文件。`off`參數禁止保存文件。此外，可以使用帶變量的`*string*`顯式設置文件名：

```nginx
fastcgi_store /data/www$original_uri;
```

文件的修改時間根據接收到的「Last-Modified」響應頭欄位設置，響應先寫入臨時文件，然後重命名文件。從0.8.9版本開始，臨時文件和持久存儲可以放在不同的文件系統上。但是，請注意，在這種情況下，文件是跨兩個文件系統複製的，而不是廉價的重命名操作。因此，建議對於任何給定的位置，保存的文件和保存臨時文件的目錄，由[fastcgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_temp_path)指令設置的所有文件都放在同一個文件系統上。

此指令可用於創建靜態不可更改文件的本地複本，例如：

```nginx
location /images/ {
    root                 /data/www;
    error_page           404 = /fetch$uri;
}

location /fetch/ {
    internal;

    fastcgi_pass         backend:9000;
    ...

    fastcgi_store        on;
    fastcgi_store_access user:rw group:rw all:r;
    fastcgi_temp_path    /data/temp;

    alias                /data/www/;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_store_access</strong> <code><i>users</i></code>:<code><i>permissions</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_store_access user:rw;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為新創建的文件和目錄設置訪問權限，例如：

```nginx
fastcgi_store_access user:rw group:rw all:r;
```

如果指定了任何`group`或`all`訪問權限，則可以省略`user`權限：

```nginx
fastcgi_store_access group:rw all:r;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_temp_file_write_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_temp_file_write_size 8k|16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

當啟用從FastCGI伺服器到臨時文件的響應緩衝時，限制一次寫入臨時文件的數據的`*size*`。默認情況下，`*size*`受[fastcgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffer_size)和[fastcgi\_buffers](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffers)指令設置的兩個緩衝區的限制。臨時文件的最大大小由[fastcgi\_max\_temp\_file\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_max_temp_file_size)指令設置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>fastcgi_temp_path</strong> <code><i>path</i></code> [<code><i>level1</i></code> [<code><i>level2</i></code> [<code><i>level3</i></code>]]];</code><br></td></tr><tr><th>Default:</th><td><pre>fastcgi_temp_path fastcgi_temp;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義一個目錄，用於存儲包含從FastCGI伺服器接收到的數據的臨時文件。在指定目錄下最多可以使用三個級別的NTFS層次結構。例如，在以下配置中

```nginx
fastcgi_temp_path /spool/nginx/fastcgi_temp 1 2;
```

臨時文件可能看起來像這樣：

```
/spool/nginx/fastcgi_temp/**7**/**45**/00000123**457**
```

另請參見[fastcgi\_cache\_path](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_cache_path)指令的`use_temp_path`參數。

#### 傳遞給FastCGI伺服器的參數

HTTP請求標頭欄位作為參數傳遞給FastCGI伺服器。在作為FastCGI伺服器運行的應用程式和腳本中，這些參數通常作為環境變量提供。例如，「User-Agent」標頭欄位作為`HTTP_USER_AGENT`參數傳遞。除了HTTP請求標頭欄位之外，還可以使用[fastcgi\_param](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_param)指令傳遞任意參數。

#### 嵌入變量

`ngx_http_fastcgi_module`模塊支持嵌入式變量，這些變量可用於使用[fastcgi\_param](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_param)指令設置參數：

`$fastcgi_script_name`

request URI，或者如果URI以斜槓結尾，則請求URI的索引文件名由附加到它的[fastcgi\_index](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_index)指令配置。此變量可用於設置在PHP中確定腳本名稱的`SCRIPT_FILENAME`和`PATH_TRANSLATED`參數。例如，對於具有以下指令的「`/info/`」請求

```nginx
fastcgi_index index.php;
fastcgi_param SCRIPT_FILENAME /home/www/scripts/php$fastcgi_script_name;
```

`SCRIPT_FILENAME`參數將等於「`/home/www/scripts/php/info/index.php`"。

使用[fastcgi\_split\_path\_info](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_split_path_info)指令時，`$fastcgi_script_name`變量等於該指令設置的第一個捕獲的值。

`$fastcgi_path_info`

由[fastcgi\_split\_path\_info](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_split_path_info)指令設置的第二個捕獲的值。此變量可用於設置`PATH_INFO`參數。