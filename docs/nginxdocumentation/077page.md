# page

> Source: https://nginx.org/en/docs/http/ngx_http_status_module.html

---

## 目錄

- [Module ngx\_http\_status\_module](#module-ngxhttpstatusmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Data](#data)
    - [Compatibility](#compatibility)

---

## Module ngx\_http\_status\_module

`ngx_http_status_module`模塊提供對各種狀態信息的訪問。

> >此模塊在1.13.10之前作為我們的[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。它在1.13.3中被[ngx\_http\_api\_module](https://nginx.org/en/docs/http/ngx_http_api_module.html)模塊取代。

#### 配置示例

```nginx
http {
    upstream **backend** {
        **zone** http_backend 64k;

        server backend1.example.com weight=5;
        server backend2.example.com;
    }

    proxy_cache_path /data/nginx/cache_backend keys_zone=**cache_backend**:10m;

    server {
        server_name backend.example.com;

        location / {
            proxy_pass  http://backend;
            proxy_cache cache_backend;

            health_check;
        }

        **status_zone server_backend;**
    }

    server {
        listen 127.0.0.1;

        location /upstream_conf {
            upstream_conf;
        }

        location /status {
            status;
        }

        location = /status.html {
        }
    }
}

stream {
    upstream **backend** {
        **zone** stream_backend 64k;

        server backend1.example.com:12345 weight=5;
        server backend2.example.com:12345;
    }

    server {
        listen      127.0.0.1:12345;
        proxy_pass  backend;
        **status_zone server_backend;**
        health_check;
    }
}
```

使用此配置的狀態請求示例：

```nginx
http://127.0.0.1/status
http://127.0.0.1/status/nginx_version
http://127.0.0.1/status/caches/cache_backend
http://127.0.0.1/status/upstreams
http://127.0.0.1/status/upstreams/backend
http://127.0.0.1/status/upstreams/backend/peers/1
http://127.0.0.1/status/upstreams/backend/peers/1/weight
http://127.0.0.1/status/stream
http://127.0.0.1/status/stream/upstreams
http://127.0.0.1/status/stream/upstreams/backend
http://127.0.0.1/status/stream/upstreams/backend/peers/1
http://127.0.0.1/status/stream/upstreams/backend/peers/1/weight
```

此發行版附帶了簡單監視頁面，在默認配置中可作為「`/status.html`」訪問。它需要如上所示配置位置「`/status`」和「`/status.html`」。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>status</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

可以從周圍位置訪問狀態信息。訪問此位置的權限應為[limited](https://nginx.org/en/docs/http/ngx_http_core_module.html#satisfy)。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>status_format</strong> <code>json</code>;</code><br><code><strong>status_format</strong> <code>jsonp</code> [<code><i>callback</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>status_format json;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

默認情況下，狀態信息以JSON格式輸出。

或者，數據也可以輸出為JSONP。`*callback*`參數指定回調函數的名稱。參數值可以包含變量。如果省略參數，或者計算值為空字符串，則使用「`ngx_status_jsonp_callback`」。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>status_zone</strong> <code><i>zone</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

啟用在指定的`*zone*`中收集虛擬[http](https://nginx.org/en/docs/http/ngx_http_core_module.html#server)或[stream](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#server)（1.7.11）伺服器狀態信息。多個伺服器可以共享同一區域。

#### Data

將提供以下狀態信息：

`version`

提供的數據集的版本。當前版本為8。

`nginx_version`

nginx版本

`nginx_build`

nginx build的名字

`address`

接受狀態請求的伺服器的地址。

`generation`

配置的總數[reloads](https://nginx.org/en/docs/control.html#reconfiguration)。

`load_timestamp`

上次重新加載配置的時間，自Epoch以來的毫秒數。

`timestamp`

自紀元以來的當前時間（毫秒）。

`pid`

處理狀態請求的工作進程的ID。

`ppid`

啟動[worker process](https://nginx.org/en/docs/http/ngx_http_status_module.html#pid)的主進程的ID。

`processes`

`respawned`

異常終止和重新生成的子進程的總數。

`connections`

`accepted`

接受的客戶端連接總數。

`dropped`

丟棄的客戶端連接總數。

`active`

當前活動客戶端連接數。

`idle`

當前空閒客戶端連接數。

`ssl`

`handshakes`

成功的SSL握手總數。

`handshakes_failed`

失敗的SSL握手總數。

`session_reuses`

SSL握手期間會話重用的總數。

`requests`

`total`

客戶端請求的總數。

`current`

當前客戶端請求數。

`server_zones`

對於每個[status\_zone](https://nginx.org/en/docs/http/ngx_http_status_module.html#status_zone)：

`processing`

當前正在處理的客戶端請求數。

`requests`

從客戶端接收的客戶端請求總數。

`responses`

`total`

發送給客戶端的響應總數。

`1xx`, `2xx`, `3xx`, `4xx`, `5xx`

狀態代碼為1xx、2xx、3xx、4xx和5xx的響應數。

`discarded`

已完成但未發送響應的請求總數。

`received`

從客戶端接收的字節總數。

`sent`

發送到客戶端的字節總數。

`slabs`

對於使用slab分配器的每個共享內存區域：

`pages`

`used`

當前使用的內存頁數。

`free`

當前可用內存頁的數量。

`slots`

對於每個內存插槽大小（8、16、32、64、128等），提供以下數據：

`used`

當前已用內存插槽數。

`free`

當前可用內存插槽數。

`reqs`

嘗試配置指定大小之內存的總次數。

`fails`

嘗試分配指定大小的內存失敗的次數。

`upstreams`

為每個[dynamically configurable](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)[group](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#upstream)提供以下數據：

`peers`

為每個[server](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)提供以下數據：

`id`

伺服器的ID。

`server`

伺服器的[address](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)。

`name`

在[server](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)指示詞中指定的伺服器名稱。

`service`

[server](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#server)指示詞的[service](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#service)參數值。

`backup`

布林值，指出伺服器是否為[backup](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#backup)伺服器。

`weight`

伺服器的[Weight](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#weight)。

`state`

當前狀態，可以是"`up`"、"`draining`"、"`down`"、"`unavail`"、"`checking`"或"`unhealthy`"中的一個。

`active`

當前活動連接的數目。

`max_conns`

伺服器的[max\_conns](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_conns)限制。

`requests`

轉發到此伺服器的客戶端請求的總數。

`responses`

`total`

從此伺服器獲得的響應總數。

`1xx`, `2xx`, `3xx`, `4xx`, `5xx`

狀態代碼為1xx、2xx、3xx、4xx和5xx的響應數。

`sent`

發送到此伺服器的總字節數。

`received`

從此伺服器接收的字節總數。

`fails`

嘗試與伺服器通信失敗的總數。

`unavail`

由於不成功嘗試的次數達到[max\_fails](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#max_fails)閾值，伺服器對客戶端請求（狀態「`unavail`」）變得不可用的次數。

`health_checks`

`checks`

已發出的[health check](https://nginx.org/en/docs/http/ngx_http_upstream_hc_module.html#health_check)個請求總數。

`fails`

失敗的運行狀況檢查數。

`unhealthy`

伺服器不正常的次數（狀態「`unhealthy`」）。

`last_passed`

指示上次運行狀況檢查請求是否成功並通過[tests](https://nginx.org/en/docs/http/ngx_http_upstream_hc_module.html#match)的布爾值。

`downtime`

伺服器處於「`unavail`"、「`checking`"和「`unhealthy`」狀態的總時間。

`downstart`

伺服器變為「`unavail`」、「`checking`」或「`unhealthy`」的時間（自Epoch以來的毫秒）。

`selected`

上次選擇伺服器處理請求的時間（以毫秒為單位）（1.7.5）。

`header_time`

從伺服器獲取[response header](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_header_time)的平均時間（1.7.10）。在1.11.6版之前，該欄位僅在使用[least\_time](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#least_time)負載平衡方法時可用。

`response_time`

從伺服器獲取[full response](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#var_upstream_response_time)的平均時間（1.7.10）。在1.11.6版之前，該欄位僅在使用[least\_time](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#least_time)負載平衡方法時可用。

`keepalive`

當前空閒的[keepalive](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#keepalive)連接數。

`zombies`

從組中刪除但仍在處理活動客戶端請求的伺服器的當前數量。

`zone`

保存組的配置和運行時狀態的共享內存[zone](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)的名稱。

`queue`

對於請求[queue](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#queue)，將提供以下數據：

`size`

隊列中的當前請求數。

`max_size`

隊列中可以同時存在的最大請求數。

`overflows`

由於隊列溢出而拒絕的請求總數。

`caches`

對於每個緩存（由[proxy\_cache\_path](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache_path)等配置）：

`size`

該高速緩存的當前大小。

`max_size`

配置中指定的該高速緩存的最大大小限制。

`cold`

一個布爾值，指示「緩存加載程式」進程是否仍在將數據從磁碟加載到該高速緩存中。

`hit`, `stale`, `updating`, `revalidated`

`responses`

從該高速緩存讀取的響應總數（命中或由於[proxy\_cache\_use\_stale](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache_use_stale)等原因導致的過時響應）。

`bytes`

從該高速緩存讀取的字節總數。

`miss`, `expired`, `bypass`

`responses`

未從該高速緩存中獲取的響應總數（未命中、過期或由於[proxy\_cache\_bypass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_cache_bypass)等原因而繞過）。

`bytes`

從代理伺服器讀取的字節總數。

`responses_written`

寫入該高速緩存的響應總數。

`bytes_written`

寫入該高速緩存的字節總數。

`stream`

`server_zones`

對於每個[status\_zone](https://nginx.org/en/docs/http/ngx_http_status_module.html#status_zone)：

`processing`

當前正在處理的客戶端連接數。

`connections`

從客戶端接受的連接總數。

`sessions`

`total`

已完成的客戶端會話總數。

`2xx`, `4xx`, `5xx`

使用[status codes](https://nginx.org/en/docs/stream/ngx_stream_core_module.html#var_status)2xx、4xx或5xx完成的會話數。

`discarded`

在未創建會話的情況下完成的連接總數。

`received`

從客戶端接收的字節總數。

`sent`

發送到客戶端的字節總數。

`upstreams`

為每個[dynamically configurable](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)[group](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#upstream)提供以下數據：

`peers`

對於每個[server](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)，提供以下數據：

`id`

伺服器的ID。

`server`

伺服器的[address](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)。

`name`

在[server](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)指示詞中指定的伺服器名稱。

`service`

[server](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#server)指示詞的[service](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#service)參數值。

`backup`

布林值，指出伺服器是否為[backup](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#backup)伺服器。

`weight`

伺服器的[Weight](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#weight)。

`state`

當前狀態，可以是「`up`"、「`down`"、「`unavail`"、「`checking`"或「`unhealthy`"中的一個。

`active`

當前連接數。

`max_conns`

伺服器的[max\_conns](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#max_conns)限制。

`connections`

轉發到此伺服器的客戶端連接總數。

`connect_time`

聯機至上游伺服器的平均時間。在1.11.6版之前，只有在使用[least\_time](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#least_time)負載平衡方法時，才可以使用此欄位。

`first_byte_time`

接收第一個字節數據的平均時間。在1.11.6版之前，只有在使用[least\_time](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#least_time)負載平衡方法時，才可以使用此欄位。

`response_time`

接收最後一個字節數據的平均時間。在1.11.6版之前，此欄位僅在使用[least\_time](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#least_time)負載平衡方法時可用。

`sent`

發送到此伺服器的總字節數。

`received`

從此伺服器接收的字節總數。

`fails`

嘗試與伺服器通信失敗的總次數。

`unavail`

由於嘗試失敗的次數達到[max\_fails](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#max_fails)閾值，伺服器對客戶端連接不可用（狀態「`unavail`」）的次數。

`health_checks`

`checks`

已發出的[health check](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#health_check)個請求總數。

`fails`

失敗的運行狀況檢查數。

`unhealthy`

伺服器不正常的次數（狀態「`unhealthy`」）。

`last_passed`

布林值，指出上一個健康檢查要求是否成功並通過[tests](https://nginx.org/en/docs/stream/ngx_stream_upstream_hc_module.html#match)。

`downtime`

伺服器處於「`unavail`"、「`checking`"和「`unhealthy`」狀態的總時間。

`downstart`

伺服器變為「`unavail`"、「`checking`"或「`unhealthy`"的時間（自紀元以來的毫秒）。

`selected`

上次選擇伺服器處理連接的時間（自紀元以來的毫秒）。

`zombies`

從組中刪除但仍在處理活動客戶端連接的伺服器的當前數量。

`zone`

保存組的配置和運行時狀態的共享內存[zone](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)的名稱。

#### Compatibility

-   上游[http](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)和[stream](https://nginx.org/en/docs/http/ngx_http_status_module.html#stream_upstreams)中的[zone](https://nginx.org/en/docs/http/ngx_http_status_module.html#zone)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)8中添加的。
-   [slabs](https://nginx.org/en/docs/http/ngx_http_status_module.html#slabs)狀態數據是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)8中添加的。
-   [checking](https://nginx.org/en/docs/http/ngx_http_status_module.html#state)狀態是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)8中添加的。
-   在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)8中添加了上游[http](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)和[stream](https://nginx.org/en/docs/http/ngx_http_status_module.html#stream_upstreams)中的[name](https://nginx.org/en/docs/http/ngx_http_status_module.html#name)和[service](https://nginx.org/en/docs/http/ngx_http_status_module.html#service)欄位。
-   [nginx\_build](https://nginx.org/en/docs/http/ngx_http_status_module.html#nginx_build)和[ppid](https://nginx.org/en/docs/http/ngx_http_status_module.html#ppid)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)8中添加的。
-   流[server\_zones](https://nginx.org/en/docs/http/ngx_http_status_module.html#stream_server_zones)中的[sessions](https://nginx.org/en/docs/http/ngx_http_status_module.html#sessions)狀態數據和[discarded](https://nginx.org/en/docs/http/ngx_http_status_module.html#stream_discarded)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)7中添加的。
-   [zombies](https://nginx.org/en/docs/http/ngx_http_status_module.html#zombies)欄位在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)6從nginx[debug](https://nginx.org/en/docs/debugging_log.html)版本中移走。
-   [ssl](https://nginx.org/en/docs/http/ngx_http_status_module.html#ssl)狀態數據是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)6中添加的。
-   [server\_zones](https://nginx.org/en/docs/http/ngx_http_status_module.html#server_zones)中的[discarded](https://nginx.org/en/docs/http/ngx_http_status_module.html#discarded)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)6中添加的。
-   [queue](https://nginx.org/en/docs/http/ngx_http_status_module.html#queue)狀態數據是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)6中添加的。
-   [pid](https://nginx.org/en/docs/http/ngx_http_status_module.html#pid)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)6中添加的。
-   已將[upstreams](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)中的伺服器列表移動到[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)6中的[peers](https://nginx.org/en/docs/http/ngx_http_status_module.html#peers)中。
-   上游伺服器的`keepalive`欄位已在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)5中刪除。
-   [stream](https://nginx.org/en/docs/http/ngx_http_status_module.html#stream)狀態數據是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)5中添加的。
-   [generation](https://nginx.org/en/docs/http/ngx_http_status_module.html#generation)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)5中添加的。
-   [processes](https://nginx.org/en/docs/http/ngx_http_status_module.html#processes)中的[respawned](https://nginx.org/en/docs/http/ngx_http_status_module.html#respawned)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)5中添加的。
-   [upstreams](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)中的[header\_time](https://nginx.org/en/docs/http/ngx_http_status_module.html#header_time)和[response\_time](https://nginx.org/en/docs/http/ngx_http_status_module.html#response_time)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)5中添加的。
-   [upstreams](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)中的[selected](https://nginx.org/en/docs/http/ngx_http_status_module.html#selected)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)4中添加的。
-   [upstreams](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)中的[draining](https://nginx.org/en/docs/http/ngx_http_status_module.html#state)狀態是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)4中添加的。
-   [upstreams](https://nginx.org/en/docs/http/ngx_http_status_module.html#upstreams)中的[id](https://nginx.org/en/docs/http/ngx_http_status_module.html#id)和[max\_conns](https://nginx.org/en/docs/http/ngx_http_status_module.html#max_conns)欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)3中添加的。
-   [caches](https://nginx.org/en/docs/http/ngx_http_status_module.html#caches)中的`revalidated`欄位是在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)3中添加的。
-   在[version](https://nginx.org/en/docs/http/ngx_http_status_module.html#version)2中添加了[server\_zones](https://nginx.org/en/docs/http/ngx_http_status_module.html#server_zones)、[caches](https://nginx.org/en/docs/http/ngx_http_status_module.html#caches)和[load\_timestamp](https://nginx.org/en/docs/http/ngx_http_status_module.html#load_timestamp)狀態數據。