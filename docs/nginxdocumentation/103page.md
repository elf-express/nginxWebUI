# page

> Source: https://nginx.org/en/docs/ngx_core_module.html

---

## 目錄

- [Core functionality](#core-functionality)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## 核心功能

#### 配置示例

> user www www;
> worker\_processes 2;
> 
> error\_log /var/log/nginx-error.log info;
> 
> events {
>     use kqueue;
>     worker\_connections 2048;
> }
> 
> ...

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>accept_mutex</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>accept_mutex off;</pre></td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

如果啟用`accept_mutex`，工作進程將輪流接受新連接。否則，所有工作進程都會收到新連接的通知，如果新連接量較低，某些工作進程可能會浪費系統資源。

> >在支持[EPOLLEXCLUSIVE](https://nginx.org/en/docs/events.html#epoll)標誌（1.11.3）的系統上或使用[reuseport](https://nginx.org/en/docs/http/ngx_http_core_module.html#reuseport)時，無需啟用`accept_mutex`。

> >在1.11.3版本之前，默認值為`on`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>accept_mutex_delay</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>accept_mutex_delay 500ms;</pre></td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

如果啟用了[accept\_mutex](https://nginx.org/en/docs/ngx_core_module.html#accept_mutex)，則指定在另一個工作進程當前正在接受新連接的情況下，工作進程將嘗試重新啟動接受新連接的最長時間。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>daemon</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>daemon on;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

確定nginx是否應該成為守護進程。主要在開發過程中使用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>debug_connection</strong> <code><i>address</i></code> | <code><i>CIDR</i></code> | <code>unix:</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

為選定的客戶端連接啟用調試日誌。其他連接將使用由[error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log)指令設置的日誌記錄級別。已調試的連接由IPv4或IPv6（1.3.0、1.2.1）地址或網絡指定。也可以使用主機名指定連接。對於使用UNIX域套接字（1.3.0、1.2.1）的連接，調試日誌由「`unix:`」參數啟用。

> events {
>     debug\_connection 127.0.0.1;
>     debug\_connection localhost;
>     debug\_connection 192.0.2.0/24;
>     debug\_connection ::1;
>     debug\_connection 2001:0db8::/32;
>     debug\_connection unix:;
>     ...
> }

> >要使此指令生效，nginx需要使用`--with-debug`構建，請參閱「[A debugging log](https://nginx.org/en/docs/debugging_log.html)"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>debug_points</strong> <code>abort</code> | <code>stop</code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

此指令用於調試。

當檢測到內部錯誤時，例如在重新啟動工作進程時套接字泄漏，啟用`debug_points`會導致創建核心文件（`abort`）或停止進程（`stop`），以便使用系統調試器進行進一步分析。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>env</strong> <code><i>variable</i></code>[=<code><i>value</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>env TZ;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

默認情況下，nginx會移除從父進程繼承的所有環境變量，除了TZ變量。該指令允許保留一些繼承的變量，更改它們的值，或創建新的環境變量。然後這些變量是：

-   在可執行文件的[live upgrade](https://nginx.org/en/docs/control.html#upgrade)期間繼承;
-   由[ngx\_http\_perl\_module](https://nginx.org/en/docs/http/ngx_http_perl_module.html)模塊使用;
-   由工作進程使用。應該記住，以這種方式控制系統庫並不總是可行的，因為庫通常只在初始化期間檢查變量，在使用此指令設置變量之前。一個例外是上面提到的可執行文件的[live upgrade](https://nginx.org/en/docs/control.html#upgrade)。

TZ變量總是繼承的，並且對[ngx\_http\_perl\_module](https://nginx.org/en/docs/http/ngx_http_perl_module.html)模塊可用，除非顯式配置它。

使用示例：

> env MALLOC\_OPTIONS;
> env PERL5LIB=/data/site/modules;
> env OPENSSL\_ALLOW\_PROXY\_CERTS=1;

> NGINX環境變量由nginx內部使用，不應該由用戶直接設置。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>error_log</strong> <code><i>file</i></code> [<code><i>level</i></code>] [<code>json</code>];</code><br></td></tr><tr><th>Default:</th><td><pre>error_log logs/error.log error;</pre></td></tr><tr><th>Context:</th><td><code>main</code>, <code>http</code>, <code>mail</code>, <code>stream</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

配置日誌記錄。可以在同一配置級別（1.5.2）上指定多個日誌。如果在`main`配置級別上沒有明確定義將日誌寫入文件，則將使用默認文件。

第一個參數定義了一個將存儲日誌的`*file*`。特殊值`stderr`選擇標準錯誤文件。可以通過指定「`syslog:`」前綴來配置日誌記錄到[syslog](https://nginx.org/en/docs/syslog.html)。可以通過指定「`memory:`」前綴和緩衝區`*size*`來配置日誌記錄到[cyclic memory buffer](https://nginx.org/en/docs/debugging_log.html#memory)，通常用於調試（1.7.11）。

第二個參數決定了日誌記錄的`*level*`，可以是以下參數之一：`debug`、`info`、`notice`、`warn`、`error`、`crit`、`alert`或`emerg`。以上日誌級別按嚴重程度遞增的順序列出。設置某個日誌級別將導致記錄指定和更嚴重日誌級別的所有消息。例如，默認級別`error`將導致`error`、`crit`、`alert`和`emerg`消息被記錄。2如果省略此參數，則使用`error`。

> >為了`debug`logging工作，nginx需要用`--with-debug`構建，參見「[A debugging log](https://nginx.org/en/docs/debugging_log.html)"。

通過`json`參數（1.29.8）可以寫JSON格式的日誌，支持[context tags](https://nginx.org/en/docs/http/ngx_http_core_module.html#error_log_tag)：

> {
>   「level」：「error」，
>   "timestamp": "2026-05-13T10:30:15.042+00:00",
>   「pid」：12345，「tid」：12345，「cnum」：3，
>   「msg」：「connect（）failed」，
>   「client」：「192.168.1.10「，「server」：「example.com「，
>   「request」：「GET /API HTTP/1.1」，
>   "upstream": "http://127.0.0.1:8080/api",
>   "errno": 111,
>   「errtext」：「連接被拒絕」
> }

日誌條目不能超過2 KB，超過此限制的數據將被截斷為`“truncated”:1`。JSON不支持此日誌記錄。

> >此參數作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

> >從版本1.7.11開始可以在`stream`級別指定指令，從版本1.9.0開始可以在`mail`級別指定指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>events</strong> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

提供配置文件上下文，在該上下文中指定影響連接處理的指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>include</strong> <code><i>file</i></code> | <code><i>mask</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>any</code><br></td></tr></tbody></table>

將另一個`*file*`或與指定的`*mask*`匹配的文件包含到配置中。包含的文件應包含語法正確的指令和塊。

使用示例：

> include mime.types;
> include vhosts/\*.conf;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>load_module</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

此指令出現在1.9.11版本中。

加載動態模塊。

Example:

> load\_module modules/ngx\_mail\_module.so;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>lock_file</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>lock_file logs/nginx.lock;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

nginx使用鎖定機制來實現[accept\_mutex](https://nginx.org/en/docs/ngx_core_module.html#accept_mutex)並序列化對共享內存的訪問。在大多數系統上，鎖定是使用原子操作實現的，並且該指令被忽略。在其他系統上，使用「鎖定文件」機制。該指令為鎖定文件的名稱指定前綴。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>master_process</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>master_process on;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

確定是否啟動工作進程。此指令適用於nginx開發人員。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>multi_accept</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>multi_accept off;</pre></td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

如果`multi_accept`被禁用，則工作進程將一次接受一個新連接。否則，工作進程將一次接受所有新連接。

> >如果使用[kqueue](https://nginx.org/en/docs/events.html#kqueue)連接處理方法，則忽略該指令，因為它報告了等待接受的新連接數。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>pcre_jit</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>pcre_jit off;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

此指令出現在1.1.12版本中。

對配置解析時已知的正則表達式啟用或禁用「即時編譯」（PCRE JIT）。

PCRE JIT可以顯著加快正則表達式的處理速度。

> >從8.20版開始，使用`--enable-jit`配置參數構建的PCRE庫中提供JIT。當PCRE庫使用nginx（`--with-pcre=`）構建時，通過`--with-pcre-jit`配置參數啟用JIT支持。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>pid</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>pid logs/nginx.pid;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

定義一個將存儲主進程的進程ID的`*file*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_engine</strong> <code><i>device</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

定義硬體SSL加速器的名稱。

> >模塊可以在配置測試期間由OpenSSL動態加載。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssl_object_cache_inheritable</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssl_object_cache_inheritable on;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

此指令出現在1.27.4版中。

如果啟用，SSL對象（SSL證書、密鑰、受信任的CA證書、CRL列表）將在配置重新加載過程中繼承。

如果自上次配置加載以來修改時間和文件索引未更改，則從文件加載的SSL對象將被繼承。指定為`engine:name:id`的密鑰永遠不會被繼承。指定為`data:value`的密鑰始終被繼承。

> 從變量加載的SSL對象不能被繼承。

Example:

> ssl\_object\_cache\_inheritable on;
> 
> http {
>     ...
>     server {
>         ...
>         ssl\_certificate     example.com.crt;
>         ssl\_certificate\_key example.com.key;
>     }
> }

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>stall_threshold</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>stall_threshold 1000ms;</pre></td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

此指令出現在版本1.29.0中。

允許在報告暫停之前覆蓋事件循環疊代的默認時間閾值。默認情況下，當事件循環疊代超過`*1000ms*`時報告暫停。如果啟用了[timer\_resolution](https://nginx.org/en/docs/ngx_core_module.html#timer_resolution)指令，則將忽略時間閾值。

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>thread_pool</strong> <code><i>name</i></code> <code>threads</code>=<code><i>number</i></code> [<code>max_queue</code>=<code><i>number</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>thread_pool default threads=32 max_queue=65536;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

此指令出現在1.7.11版本中。

定義用於多線程閱讀和發送文件[without blocking](https://nginx.org/en/docs/http/ngx_http_core_module.html#aio)worker進程的線程池的`*name*`和參數。

`threads`參數定義池中的線程數。

當池中的所有線程都處於忙碌狀態時，會有新的任務在隊列中等待。`max_queue`參數限制了隊列中允許等待的任務數量。默認情況下，隊列中最多可以等待65536個任務。當隊列溢出時，任務完成並出錯。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>timer_resolution</strong> <code><i>interval</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

降低工作進程中的計時器解析度，從而減少進行的`gettimeofday()`系統調用的次數。默認情況下，每次接收內核事件時都會調用`gettimeofday()`。降低解析度後，每指定`*interval*`，僅調用`gettimeofday()`一次。

Example:

> timer\_resolution 100ms;

間隔的內部實現取決於所使用的方法：

-   如果使用`kqueue`，則為`EVFILT_TIMER`過濾器;
-   如果使用`eventport`，則為`timer_create()`;
-   `setitimer()` otherwise.

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>use</strong> <code><i>method</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

指定要使用的[connection processing](https://nginx.org/en/docs/events.html)`*method*`。通常不需要顯式指定，因為nginx默認會使用最有效的方法。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>user</strong> <code><i>user</i></code> [<code><i>group</i></code>];</code><br></td></tr><tr><th>Default:</th><td><pre>user nobody nobody;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

定義輔助進程使用的`*user*`和`*group*`憑據。如果省略`*group*`，則使用名稱等於`*user*`的組。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_aio_requests</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>worker_aio_requests 32;</pre></td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

該指令出現在1.1.4和1.0.7版本中。

將[aio](https://nginx.org/en/docs/http/ngx_http_core_module.html#aio)與[epoll](https://nginx.org/en/docs/events.html#epoll)連接處理方法一起使用時，為單個工作進程設置未完成的異步I/O操作的最大值`*number*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_connections</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>worker_connections 512;</pre></td></tr><tr><th>Context:</th><td><code>events</code><br></td></tr></tbody></table>

設置輔助進程可以同時打開的最大連接數。

需要注意的是，這個數量包括所有的連接（例如，與代理伺服器的連接等），而不僅僅是與客戶端的連接。另一個需要注意的是，同時連接的實際數量不能超過當前打開文件的最大數量限制，該限制可以通過[worker\_rlimit\_nofile](https://nginx.org/en/docs/ngx_core_module.html#worker_rlimit_nofile)進行更改。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_cpu_affinity</strong> <code><i>cpumask</i></code> ...;</code><br><code><strong>worker_cpu_affinity</strong> <code>auto</code> [<code><i>cpumask</i></code>];</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

將工作進程綁定到CPU集。每個CPU集由允許的CPU的位掩碼表示。應為每個工作進程定義一個單獨的集。默認情況下，工作進程不綁定到任何特定的CPU。

比如說，

> worker\_processes    4;
> worker\_cpu\_affinity 0001 0010 0100 1000;

將每個工作進程綁定到單獨的CPU，

> worker\_processes    2;
> worker\_cpu\_affinity 0101 1010;

將第一個工作進程綁定到CPU0/CPU2，將第二個工作進程綁定到CPU1/CPU3。第二個示例適用於超線程。

特殊值`auto`（1.9.10）允許將工作進程自動綁定到可用CPU：

> worker\_processes auto;
> worker\_cpu\_affinity auto;

可選的mask參數可用於限制可用於自動綁定的CPU：

> worker\_cpu\_affinity auto 01010101;

> >該指令僅在FreeBSD和Linux上可用。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_priority</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>worker_priority 0;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

定義工作進程的調度優先級，就像用`nice`命令一樣：負的`*number*`表示更高的優先級。允許的範圍通常在-20到20之間。

Example:

> worker\_priority -10;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_processes</strong> <code><i>number</i></code> | <code>auto</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>worker_processes 1;</pre></td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

定義工作進程的數量。

最佳值取決於許多因素，包括（但不限於）CPU核心的數量，存儲數據的硬碟驅動器的數量和負載模式。當一個人有疑問時，將其設置為可用CPU核心的數量將是一個很好的開始（值"`auto`"將嘗試自動檢測它）。

> >從1.3.8和1.2.5版本開始支持`auto`參數。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_rlimit_core</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

更改輔助進程的核心文件（`RLIMIT_CORE`）的最大大小限制。用於在不重新啟動主進程的情況下增加限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_rlimit_nofile</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

更改輔助進程的最大打開文件數（`RLIMIT_NOFILE`）限制。用於在不重新啟動主進程的情況下增加限制。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>worker_shutdown_timeout</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

此指令出現在1.11.11版本中。

為工作進程的正常關閉配置超時。當`*time*`超時時，nginx將嘗試關閉當前打開的所有連接以方便關閉。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>working_directory</strong> <code><i>directory</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

定義工作進程的當前工作目錄。它主要在寫核心文件時使用，在這種情況下，工作進程應該對指定的目錄有寫權限。