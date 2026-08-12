# page

> Source: https://nginx.org/en/docs/http/ngx_http_scgi_module.html

---

## 目錄

- [Module ngx\_http\_scgi\_module](#module-ngxhttpscgimodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_scgi\_module

`ngx_http_scgi_module`模塊允許將請求傳遞到SCGI伺服器。

#### 配置示例

```nginx
location / {
    include   scgi_params;
    scgi_pass localhost:9000;
}
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_allow_upstream</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

定義允許訪問SCGI伺服器的條件或[denied](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#denied)。如果所有字符串參數不為空且不等於「0」，則允許訪問。每次建立到SCGI伺服器的連接之前，都會評估這些條件。參數值可以包含變量：

```nginx
geo $upstream_last_addr $allow {
    volatile;
    10.10.0.0/24        1;
}

server {
    listen 127.0.0.1:8080;

    location / {
        scgi_pass           localhost:9000;
        scgi_allow_upstream $allow;
        ...
    }
}
```

> >此指令作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_bind</strong> <code><i>address</i></code> [<code>transparent</code>] | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

使用可選埠（1.11.2）從指定的本地IP位址發起到SCGI伺服器的傳出連接。參數值可以包含變量（1.3.12）。特殊值`off`（1.3.12）取消了從上一配置級別繼承的`scgi_bind`指令的效果，該指令允許系統自動分配本地IP位址和埠。

`transparent`參數（1.11.0）允許從非本地IP位址（例如，從客戶端的真實的IP位址）發起到SCGI伺服器的傳出連接：

```nginx
scgi_bind $remote_addr transparent;
```

為了使此參數生效，通常需要使用[superuser](https://nginx.org/en/docs/ngx_core_module.html#user)特權運行nginx工作進程。在Linux上，不需要（1.13.8），因為如果指定了`transparent`參數，工作進程將從主進程繼承`CAP_NET_RAW`功能。還需要配置內核路由表以攔截來自SCGI伺服器的網絡流量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_bind_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_bind_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

啟用後，在每次連接嘗試時執行[bind](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_bind)操作。

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於閱讀從SCGI伺服器接收的響應的第一部分的緩衝區的`*size*`。此部分通常包含一個小的響應標頭;如果它超過緩衝區大小，則將響應視為[invalid](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#invalid_header)。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小為4K或8 K。但是，可以將其設置得更小。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_buffering</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_buffering on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

啟用或禁用來自SCGI伺服器的響應緩衝。

開啟緩衝後，nginx會儘快從SCGI伺服器接收響應，並將其保存到[scgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffer_size)和[scgi\_buffers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffers)指令設置的緩衝區中。如果整個響應無法放入內存，可以將其一部分保存到磁碟上的[temporary file](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_temp_path)中。寫入臨時文件由[scgi\_max\_temp\_file\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_max_temp_file_size)和[scgi\_temp\_file\_write\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_temp_file_write_size)指令控制。

當緩衝被禁用時，響應會在收到時立即同步傳遞給客戶端。nginx不會嘗試從SCGI伺服器讀取整個響應。nginx一次可以從伺服器接收的最大數據大小由[scgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffer_size)指令設置。

還可以通過在「X-Accel-Buffering」響應頭欄位中傳遞「`yes`」或「`no`」來啟用或禁用緩衝。可以使用[scgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_ignore_headers)指令禁用此功能。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_buffers 8 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為單個連接設置用於從SCGI伺服器閱讀響應的緩衝區的`*number*`和`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台，緩衝區大小為4K或8 K。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_busy_buffers_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_busy_buffers_size 8k|16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

當啟用來自SCGI伺服器的[buffering](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffering)個響應時，將限制在響應尚未完全讀取時可能忙碌向客戶端發送響應的緩衝區總數`*size*`。同時，其餘緩衝區可用於閱讀響應，並在需要時將部分響應緩衝到臨時文件中。默認情況下，`*size*`受[scgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffer_size)和[scgi\_buffers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffers)指令設置的兩個緩衝區的大小限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache</strong> <code><i>zone</i></code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義用於緩存的共享內存區域。同一區域可以在多個地方使用。參數值可以包含變量（1.7.9）。`off`參數禁用從上一配置級別繼承的緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_background_update</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_background_update off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在版本1.11.10中。

允許啟動後台子請求來更新過期的緩存項，同時向客戶端返回過期的緩存響應。請注意，在更新過期的緩存響應時，需要[allow](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_use_stale_updating)其使用情況。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_bypass</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義不從緩存中獲取響應的條件。如果字符串參數中至少有一個值不為空且不等於「0」，則不從該高速緩存中獲取響應：

```nginx
scgi_cache_bypass $cookie_nocache $arg_nocache$arg_comment;
scgi_cache_bypass $http_pragma    $http_authorization;
```

可與[scgi\_no\_cache](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_no_cache)指令一起沿著使用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_key</strong> <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義用於緩存的鍵，例如

```nginx
scgi_cache_key localhost:9000$request_uri;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_lock</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_lock off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.12版本中。

啟用後，一次只允許一個請求通過向SCGI伺服器傳遞請求來填充根據[scgi\_cache\_key](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_key)指令標識的新緩存元素。同一緩存元素的其他請求將等待該高速緩存中出現響應，或等待釋放該元素的該高速緩存鎖，直到[scgi\_cache\_lock\_timeout](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_lock_timeout)指令設置的時間為止。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_lock_age</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_lock_age 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.8版本中。

如果對於指定的`*time*`，傳遞到SCGI伺服器的用於填充新的高速緩存元素的最後一個請求尚未完成，則可以將另一個請求傳遞到SCGI伺服器。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_lock_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_lock_timeout 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.1.12版本中。

設置[scgi\_cache\_lock](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_lock)的超時時間。當`*time*`超時時，請求將被傳遞到SCGI伺服器，但不會緩存響應。

> >在1.7.8之前，響應可以被緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_max_range_offset</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.11.6版中。

設置字節範圍請求的偏移量（以字節為單位）。如果範圍超出偏移量，則範圍請求將被傳遞到SCGI伺服器，並且響應將不被緩存。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_methods</strong> <code>GET</code> | <code>HEAD</code> | <code>POST</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_methods GET HEAD;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果客戶端請求方法在此指令中列出，則響應將被緩存。「`GET`」和「`HEAD`」方法總是添加到列表中，但建議顯式指定它們。另請參閱[scgi\_no\_cache](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_no_cache)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_min_uses</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_min_uses 1;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置請求的`*number*`，在此之後將緩存響應。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_path</strong> <code><i>path</i></code> [<code>levels</code>=<code><i>levels</i></code>] [<code>use_temp_path</code>=<code>on</code>|<code>off</code>] <code>keys_zone</code>=<code><i>name</i></code>:<code><i>size</i></code> [<code>inactive</code>=<code><i>time</i></code>] [<code>max_size</code>=<code><i>size</i></code>] [<code>min_free</code>=<code><i>size</i></code>] [<code>manager_files</code>=<code><i>number</i></code>] [<code>manager_sleep</code>=<code><i>time</i></code>] [<code>manager_threshold</code>=<code><i>time</i></code>] [<code>loader_files</code>=<code><i>number</i></code>] [<code>loader_sleep</code>=<code><i>time</i></code>] [<code>loader_threshold</code>=<code><i>time</i></code>] [<code>purger</code>=<code>on</code>|<code>off</code>] [<code>purger_files</code>=<code><i>number</i></code>] [<code>purger_sleep</code>=<code><i>time</i></code>] [<code>purger_threshold</code>=<code><i>time</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

設置緩存的路徑和其他參數。緩存數據存儲在文件中。緩存中的文件名是對[cache key](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_key)應用MD5函數的結果。`levels`參數定義緩存的層次結構級別：從1到3，每個級別接受值1或2。例如，在以下配置中

```nginx
scgi_cache_path /data/nginx/cache levels=1:2 keys_zone=one:10m;
```

緩存中的文件名如下所示：

```
/data/nginx/cache/**c**/**29**/b7f54b2df7773722d382f4809d650**29c**
```

一個緩存的響應先寫到一個臨時文件，然後文件重命名，從0. 8. 9版本開始，臨時文件和該高速緩存可以放在不同的文件系統上，但是，請注意，在這種情況下，文件是跨兩個文件系統複製的，而不是廉價的重命名操作。因此，建議對於任何給定的位置，都啟用緩存和保存臨時文件的目錄相同的文件系統。臨時文件的目錄是根據`use_temp_path`參數（1.7.10）設置的。如果該參數被省略或設置為值`on`，則將使用[scgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_temp_path)指令為給定位置設置的目錄。如果該值設置為`off`，則臨時文件將直接放在該高速緩存目錄中。

此外，所有活動密鑰和有關數據的信息都存儲在一個共享內存區中，其`*name*`和`*size*`由`keys_zone`參數配置。一兆字節區可存儲約八千個密鑰。

> >作為[commercial subscription](https://www.f5.com/products/nginx)的一部分，共享內存區域還存儲擴展緩存[information](https://nginx.org/en/docs/http/ngx_http_api_module.html#http_caches_)，因此，對於相同數量的密鑰，需要指定更大的區域大小。例如，一兆字節的區域可以存儲大約四千個密鑰。

在`inactive`參數指定的時間內未被訪問的緩存數據將從該高速緩存中刪除，而不管其新鮮度如何。默認情況下，`inactive`設置為10分鐘。

特殊的「緩存管理器」進程監視由`max_size`參數設置的最大緩存大小，以及由`min_free`（1.19.1）帶緩存的文件系統上的參數。當超過大小或沒有足夠的可用空間時，它將刪除最近使用的數據。數據將在由`manager_files`，`manager_threshold`，和`manager_sleep`參數（1.11.5）。在一次疊代中，不超過`manager_files`項被刪除（默認為100）。一次疊代的持續時間由`manager_threshold`參數限制（默認為200毫秒）。在疊代之間，進行由`manager_sleep`參數配置的暫停（默認為50毫秒）。

啟動一分鐘後，特殊的「緩存加載器」進程被激活。它將有關存儲在文件系統上的先前緩存數據的信息加載到緩存區域中。加載也是在疊代中完成的。在一次疊代中，加載不超過`loader_files`項（默認為100），此外，一次疊代的持續時間受`loader_threshold`參數限制（默認為200毫秒）。在疊代之間，進行由`loader_sleep`參數配置的暫停（默認為50毫秒）。

此外，以下參數可作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分：

`purger`\=`on`|`off`

指示該高速緩存清除器是否將從磁碟中刪除與[wildcard key](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_purge)匹配的緩存項（1.7.12）。將該參數設置為`on`（默認值為`off`）將激活「緩存清除器」進程，該進程將永久地遍歷所有緩存項並刪除與該關鍵字匹配的項。

`purger_files`\=`*number*`

設置在一次疊代中要掃描的項目數（1.7.12）。默認情況下，`purger_files`設置為10。

`purger_threshold`\=`*number*`

設置一次疊代的持續時間（1.7.12）。默認情況下，`purger_threshold`設置為50毫秒。

`purger_sleep`\=`*number*`

設置疊代之間的暫停（1.7.12）。默認情況下，`purger_sleep`設置為50毫秒。

> >在1.7.3、1.7.7和1.11.10版本中，緩存頭格式已更改。升級到較新的nginx版本後，以前緩存的響應將被視為無效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_purge</strong> string ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.7版本中。

定義將請求視為緩存清除請求的條件。如果字符串參數中至少有一個值不為空且不等於「0」，則刪除具有對應[cache key](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_key)的該高速緩存條目。操作成功的結果通過返回204（無內容）響應來指示。

如果清除請求的[cache key](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_key)以星號（「`*`」）結尾，則將從該高速緩存中刪除所有與清除鍵匹配的緩存條目。但是，這些條目將保留在磁碟上，直到它們被刪除以用於[inactivity](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_path)或由[cache purger](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#purger)（1.7.12）處理，或者客戶端嘗試訪問它們。

示例配置：

```nginx
scgi_cache_path /data/nginx/cache keys_zone=cache_zone:10m;

map $request_method $purge_method {
    PURGE   1;
    default 0;
}

server {
    ...
    location / {
        scgi_pass        backend;
        scgi_cache       cache_zone;
        scgi_cache_key   $uri;
        scgi_cache_purge $purge_method;
    }
}
```

> >此功能是我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_revalidate</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_revalidate off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.7版本中。

使用帶有「If-Modified-Since」和「If-None-Match」標頭欄位的條件請求啟用過期緩存項的重新驗證。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_use_stale</strong> <code>error</code> | <code>timeout</code> | <code>invalid_header</code> | <code>updating</code> | <code>http_500</code> | <code>http_503</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_cache_use_stale off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

確定在與SCGI伺服器通信期間發生錯誤時，在哪些情況下可以使用過時的緩存響應。指令的參數與[scgi\_next\_upstream](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream)指令的參數匹配。

如果無法選擇處理請求的SCGI伺服器，`error`參數還允許使用陳舊的緩存響應。

此外，如果當前正在更新緩存響應，則`updating`參數允許使用陳舊的緩存響應。這允許在更新緩存數據時最小化對SCGI伺服器的訪問次數。

也可以在響應過期後的指定秒數內直接在響應頭中啟用使用過期緩存響應（1.11.10）。這比使用指令參數的優先級低。

-   「Cache-Control」頭欄位的「[stale-while-revalidate](https://datatracker.ietf.org/doc/html/rfc5861#section-3)」擴展允許在當前正在更新的情況下使用陳舊的緩存響應。
-   「Cache-Control」頭欄位的「[stale-if-error](https://datatracker.ietf.org/doc/html/rfc5861#section-4)」擴展允許在出現錯誤時使用過時的緩存響應。

為了在填充新的緩存元素時最小化對SCGI伺服器的訪問次數，可以使用[scgi\_cache\_lock](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_lock)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_cache_valid</strong> [<code><i>code</i></code> ...] <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為不同的響應代碼設置緩存時間。

```nginx
scgi_cache_valid 200 302 10m;
scgi_cache_valid 404      1m;
```

對代碼為200和302的響應設置10分鐘的緩存，對代碼為404的響應設置1分鐘的緩存。

如果僅指定緩存`*time*`

```nginx
scgi_cache_valid 5m;
```

則僅緩存200、301和302響應。

此外，可以指定`any`參數來緩存任何響應：

```nginx
scgi_cache_valid 200 302 10m;
scgi_cache_valid 301      1h;
scgi_cache_valid any      1m;
```

緩存的參數也可以直接在響應頭中設置。這比使用指令設置緩存時間具有更高的優先級。

-   The “X-Accel-Expires” header field sets caching time of a response in seconds. The zero value disables caching for a response. If the value starts with the `@` prefix, it sets an absolute time in seconds since Epoch, up to which the response may be cached.
-   如果報頭不包括「X-Accel-Expires」欄位，則可以在報頭欄位「Expires」或「Cache-Control」中設置緩存的參數。
-   如果頭包含「Set-Cookie」欄位，則不會緩存此類響應。
-   如果報頭包含具有特殊值「`*`"的「Vary」欄位，則不會緩存這樣的響應（1.7.7）。如果報頭包含具有另一個值的「Vary」欄位，則會考慮相應的請求報頭欄位來緩存這樣的響應（1.7.7）。

可以使用[scgi\_ignore\_headers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_ignore_headers)指令禁用一個或多個響應頭欄位的處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_connect_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_connect_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義與SCGI伺服器建立連接的超時。應該注意的是，此超時通常不能超過75秒。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_force_ranges</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_force_ranges off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.7版本中。

為來自SCGI伺服器的緩存和未緩存響應啟用字節範圍支持，而不管這些響應中的「Accept-Ranges」欄位。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_hide_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

默認情況下，nginx不會將SCGI伺服器響應中的頭欄位「Status」和「X-Accel-...」傳遞給客戶端。`scgi_hide_header`指令設置了不會傳遞的附加欄位。相反，如果需要允許傳遞欄位，則可以使用[scgi\_pass\_header](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_pass_header)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_ignore_client_abort</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_ignore_client_abort off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

確定當客戶端在不等待響應的情況下關閉連接時，是否應關閉與SCGI伺服器的連接。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_ignore_headers</strong> <code><i>field</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

禁止處理來自SCGI伺服器的某些響應標頭欄位。可以忽略以下欄位：「X-Accel-Redirect」、「X-Accel-Expires」、「X-Accel-Limit-Rate」（1.1.6）、「X-Accel-Buffering」（1.1.6）、「X-Accel-Charset」（1.1.6）、「Expires」、「Cache-Control」、「Set-Cookie」（0.8.44）和「Vary」（1.7.7）。

如果未禁用，則處理這些標題欄位具有以下效果：

-   「X-Accel-Expires」、「Expires」、「Cache-Control」、「Set-Cookie」、「Vary」設置響應[caching](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_valid)的參數;
-   「X-Accel-Redirect」對指定的URI執行[internal redirect](https://nginx.org/en/docs/http/ngx_http_core_module.html#internal);
-   「X-Accel-Limit-Rate」設置向客戶端發送響應的[rate limit](https://nginx.org/en/docs/http/ngx_http_core_module.html#limit_rate);
-   「X-Accel-Buffering」啟用或禁用響應的[buffering](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffering);
-   「X-Accel-Charset」設置響應的期望[charset](https://nginx.org/en/docs/http/ngx_http_charset_module.html#charset)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_intercept_errors</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_intercept_errors off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

確定代碼大於或等於300的SCGI伺服器響應是否應該傳遞給客戶端，或者攔截並重定向到nginx以使用[error\_page](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_page)指令進行處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_limit_rate</strong> <code><i>rate</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_limit_rate 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.7版本中。

限制從SCGI伺服器閱讀響應的速度。`*rate*`以字節每秒為單位指定。零值禁用速率限制。該限制是根據請求設置的，因此如果nginx同時打開兩個到SCGI伺服器的連接，總比率將是指定限制的兩倍。僅當[buffering](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffering)時，限制才有效已啟用SCGI伺服器的響應。參數值可以包含變量（1.27.0）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_max_temp_file_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_max_temp_file_size 1024m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

當來自SCGI伺服器的[buffering](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffering)個響應被啟用，並且整個響應不適合由[scgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffer_size)和[scgi\_buffers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffers)指令設置的緩衝區時，可以將部分響應保存到臨時文件。該指令設置臨時文件的最大值`*size*`。一次寫入臨時文件的數據大小由[scgi\_temp\_file\_write\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_temp_file_write_size)指令設置。

零值禁用對臨時文件的響應緩衝。

> >此限制不適用於磁碟上將為[cached](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache)或[stored](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_store)的響應。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_next_upstream</strong> <code>error</code> | <code>timeout</code> | <code>denied</code> | <code>invalid_header</code> | <code>http_500</code> | <code>http_503</code> | <code>http_403</code> | <code>http_404</code> | <code>http_429</code> | <code>non_idempotent</code> | <code>off</code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_next_upstream error timeout;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指定在哪些情況下應將請求傳遞到下一個伺服器：

`error`

與伺服器建立連接、向其傳遞請求或閱讀響應標頭時出錯;

`timeout`

在與伺服器建立連接、向其傳遞請求或閱讀響應標頭時發生超時;

`denied`

伺服器[denied](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_allow_upstream)連接（1.29.3）;

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

將請求傳遞到下一個伺服器可以受到[the number of tries](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream_tries)和[time](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream_timeout)的限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_next_upstream_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_next_upstream_timeout 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.5版中。

限制可以將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream)的時間。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_next_upstream_tries</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_next_upstream_tries 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.5版中。

限制將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream)的可能嘗試次數。`0`值關閉此限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_no_cache</strong> <code><i>string</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義響應不會保存到緩存的條件。如果字符串參數中至少有一個值不為空且不等於「0」，則不會保存響應：

```nginx
scgi_no_cache $cookie_nocache $arg_nocache$arg_comment;
scgi_no_cache $http_pragma    $http_authorization;
```

可與[scgi\_cache\_bypass](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_bypass)指令一起沿著使用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_param</strong> <code><i>parameter</i></code> <code><i>value</i></code> [<code>if_not_empty</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_param HTTP_HOST $host$is_request_port$request_port;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置應傳遞給SCGI伺服器的`*parameter*`。`*value*`可以包含文本、變量及其組合。若且唯若當前級別上沒有定義`scgi_param`指令時，這些指令才從上一配置級別繼承。

標準[CGI environment variables](https://datatracker.ietf.org/doc/html/rfc3875#section-4.1)應作為SCGI標頭提供，請參閱分發版中提供的`scgi_params`文件：

```nginx
location / {
    include scgi_params;
    ...
}
```

如果指令是用`if_not_empty`（1.1.11）指定的，那麼只有當它的值不為空時，這樣的參數才會傳遞給伺服器：

```nginx
scgi_param HTTPS $https if_not_empty;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_pass</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

設置SCGI伺服器的地址。該地址可以指定為域名或IP位址以及埠：

```nginx
scgi_pass localhost:9000;
```

或者作為UNIX域套接字路徑：

```nginx
scgi_pass unix:/tmp/scgi.socket;
```

如果一個域名解析為多個地址，所有的地址都將以循環方式使用。此外，地址可以指定為[server group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)。

參數值可以包含變量。在這種情況下，如果地址被指定為域名，則在所描述的[server groups](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)中搜索該名稱，如果沒有找到，則使用[resolver](https://nginx.org/en/docs/http/ngx_http_core_module.html#resolver)確定。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_pass_header</strong> <code><i>field</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

允許將[otherwise disabled](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_hide_header)頭欄位從SCGI伺服器傳遞到客戶端。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_pass_request_body</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_pass_request_body on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指示是否將原始請求正文傳遞到SCGI伺服器。另請參閱[scgi\_pass\_request\_headers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_pass_request_headers)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_pass_request_headers</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_pass_request_headers on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

指示是否將原始請求的頭欄位傳遞到SCGI伺服器。另請參閱[scgi\_pass\_request\_body](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_pass_request_body)指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_read_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_read_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義從SCGI伺服器閱讀響應的超時。超時僅在兩個連續的讀取操作之間設置，而不是為整個響應的傳輸設置。如果SCGI伺服器在此時間內未傳輸任何內容，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_request_buffering</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_request_buffering on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.7.11版本中。

啟用或禁用客戶端請求正文的緩衝。

當啟用緩衝時，在將請求發送到SCGI伺服器之前，整個請求主體來自客戶端[read](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_buffer_size)。

當緩衝關閉時，請求體會在收到後立即發送到SCGI伺服器。在這種情況下，如果nginx已經開始發送請求體，則無法將請求傳遞到[next server](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_next_upstream)。

當HTTP/1.1分塊傳輸編碼用於發送原始請求主體時，無論指令值如何，請求主體都將被緩衝。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_request_dynamic</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_request_dynamic off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.29.3版中。

啟用或禁用為每個SCGI伺服器創建單獨的請求實例。默認情況下，單個請求用於所有SCGI伺服器。如果啟用，則創建單獨的請求實例，允許按伺服器自定義請求。

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_send_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_send_timeout 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置向SCGI伺服器發送請求的超時。超時僅在兩個連續的寫操作之間設置，而不是為整個請求的傳輸設置。如果SCGI伺服器在此時間內未收到任何內容，則連接關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_socket_keepalive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_socket_keepalive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.15.6版中。

為到SCGI伺服器的傳出連接配置「TCP keepalive」行為。默認情況下，作業系統的設置對套接字有效。如果該指令設置為值「`on`"，則為套接字啟用`SO_KEEPALIVE`socket選項。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_socket_rcvbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

設置到SCGI伺服器的傳出連接的接收緩衝區大小（`SO_RCVBUF`選項）。特殊值`0`取消了從以前的配置級別繼承的`scgi_socket_rcvbuf`指令的效果，這允許保持作業系統的設置對套接字有效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_socket_sndbuf</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.31.3版中。

設置到SCGI伺服器的傳出連接的發送緩衝區大小（`SO_SNDBUF`選項）。特殊值`0`取消了從以前的配置級別繼承的`scgi_socket_sndbuf`指令的效果，這允許保持作業系統的設置對套接字有效。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_store</strong> <code>on</code> | <code>off</code> | <code><i>string</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_store off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

允許將文件保存到磁碟。`on`參數使用與指令[alias](https://nginx.org/en/docs/http/ngx_http_core_module.html#alias)或[root](https://nginx.org/en/docs/http/ngx_http_core_module.html#root)對應的路徑保存文件。`off`參數禁止保存文件。此外，可以使用帶變量的`*string*`顯式設置文件名：

```nginx
scgi_store /data/www$original_uri;
```

文件的修改時間根據接收到的"Last-Modified"響應頭欄位設置，響應先寫入臨時文件，然後重命名文件。從0.8.9版本開始，臨時文件和持久存儲可以放在不同的文件系統上。但是，請注意，在這種情況下，文件是跨兩個文件系統複製的，而不是廉價的重命名操作。因此，建議對於任何給定的位置，保存的文件和保存臨時文件的目錄，由[scgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_temp_path)指令設置的所有文件都放在同一個文件系統上。

此指令可用於創建靜態不可更改文件的本地複本，例如：

```nginx
location /images/ {
    root              /data/www;
    error_page        404 = /fetch$uri;
}

location /fetch/ {
    internal;

    scgi_pass         backend:9000;
    ...

    scgi_store        on;
    scgi_store_access user:rw group:rw all:r;
    scgi_temp_path    /data/temp;

    alias             /data/www/;
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_store_access</strong> <code><i>users</i></code>:<code><i>permissions</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_store_access user:rw;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為新創建的文件和目錄設置訪問權限，例如：

```nginx
scgi_store_access user:rw group:rw all:r;
```

如果指定了任何`group`或`all`訪問權限，則可以省略`user`權限：

```nginx
scgi_store_access group:rw all:r;
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_temp_file_write_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_temp_file_write_size 8k|16k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

當啟用將SCGI伺服器的響應緩衝到臨時文件時，限制一次寫入臨時文件的數據的`*size*`。默認情況下，`*size*`受[scgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffer_size)和[scgi\_buffers](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffers)指令設置的兩個緩衝區的限制。臨時文件的最大大小由[scgi\_max\_temp\_file\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_max_temp_file_size)指令設置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>scgi_temp_path</strong> <code><i>path</i></code> [<code><i>level1</i></code> [<code><i>level2</i></code> [<code><i>level3</i></code>]]];</code><br></td></tr><tr><th>Default:</th><td><pre>scgi_temp_path scgi_temp;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義一個目錄，用於存儲包含從SCGI伺服器接收到的數據的臨時文件。在指定目錄下最多可以使用三個級別的XML層次結構。例如，在以下配置中

```nginx
scgi_temp_path /spool/nginx/scgi_temp 1 2;
```

臨時文件可能看起來像這樣：

> >/spool/nginx/scgi\_temp/** 7 **/** 45 **/00000123 ** 457 **

另請參見[scgi\_cache\_path](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_cache_path)指令的`use_temp_path`參數。