# page

> Source: https://nginx.org/en/docs/http/ngx_http_perl_module.html

---

## 目錄

- [Module ngx\_http\_perl\_module](#module-ngxhttpperlmodule)
    - [Known Issues](#known-issues)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Calling Perl from SSI](#calling-perl-from-ssi)
    - [The $r Request Object Methods](#the-r-request-object-methods)

---

## Module ngx\_http\_perl\_module

`ngx_http_perl_module`模塊用於在Perl中實現位置和變量處理程式，並將Perl調用插入SSI。

默認情況下不構建此模塊，應使用`--with-http_perl_module`配置參數啟用。

> >此模塊需要[Perl](https://www.perl.org/get.html)版本5.6.1或更高版本。C編譯器應與用於構建Perl的編譯器兼容。

#### 已知問題

該模塊是實驗性的，買者自負適用。

為了使Perl在重新配置期間重新編譯修改後的模塊，應該使用`-Dusemultiplicity=yes`或`-Dusethreads=yes`參數來構建它。此外，為了使Perl在運行時泄漏更少的內存，應該使用`-Dusemymalloc=no`參數來構建它。要在已經構建的Perl中檢查這些參數的值（在示例中指定了首選值），請運行：

> $ perl -V：usemultiplicity -V：usemymalloc
> usemultiplicity='define';
> usemymalloc='n';

請注意，在使用新的`-Dusemultiplicity=yes`或`-Dusethreads=yes`參數重建Perl之後，所有的二進位Perl模塊也必須重建-它們將停止使用新的Perl。

每次重新配置後，主進程和工作進程的大小都有可能增加。如果主進程的大小增加到不可接受的大小，則可以應用[live upgrade](https://nginx.org/en/docs/control.html#upgrade)過程，而無需更改可執行文件。

當Perl模塊執行長時間運行的操作時，例如解析域名、連接到另一個伺服器或查詢資料庫，分配給當前工作進程的其他請求將不會被處理。因此建議只執行可預測且執行時間短的操作，例如訪問本地文件系統。

#### 配置示例

> http {
> 
>     perl\_modules perl/lib;
>     perl\_require hello.pm;
> 
>     perl\_set $msie6 '
> 
>         sub {
>             my $r = shift;
>             my $ua = $r->header\_in("User-Agent");
> 
>             return "" if $ua =~ /Opera/;
>             return "1" if $ua =~ / MSIE \[6-9\]\\.\\d+/;
>             return "";
>         }
> 
>     ';
> 
>     server {
>         location / {
>             perl hello::handler;
>         }
>     }

`perl/lib/hello.pm`模塊：

> package hello;
> 
> use nginx;
> 
> sub handler {
>     my $r = shift;
> 
>     $r->send\_http\_header("text/html");
>     return OK if $r->header\_only;
> 
>     $r->print("hello!\\n<br/>");
> 
>     if (-f $r->filename or -d \_) {
>         $r->print($r->uri, " exists!\\n");
>     }
> 
>     return OK;
> }
> 
> 1;
> \_\_END\_\_

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>perl</strong> <code><i>module</i></code>::<code><i>function</i></code>|'sub { ... }';</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code>, <code>limit_except</code><br></td></tr></tbody></table>

為給定位置設置Perl處理程式。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>perl_modules</strong> <code><i>path</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

設置Perl模塊的附加路徑。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>perl_require</strong> <code><i>module</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

定義在每次重新配置期間將加載的模塊的名稱。可以存在多個`perl_require`指令。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>perl_set</strong> <code><i>$variable</i></code> <code><i>module</i></code>::<code><i>function</i></code>|'sub { ... }';</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

為指定的變量安裝Perl處理程式。

#### 從SSI調用Perl

調用Perl的SSI命令具有以下格式：

> <！--# perl sub="`*module*`：：`*function*`」arg="`*parameter1*`」arg="`*parameter2*`」.
> -->

#### $r請求對象方法

`$r->args`

返回請求參數。

`$r->filename`

返回與請求URI對應的文件名。

``$r->has_request_body(`*handler*`)``

如果請求中沒有body，則返回0。如果有body，則為請求設置指定的handler並返回1。閱讀請求body後，nginx將調用指定的handler。注意handler函數應該通過引用傳遞。示例：

> package hello;
> 
> use nginx;
> 
> sub handler {
>     my $r = shift;
> 
>     if ($r->request\_method ne "POST") {
>         return DECLINED;
>     }
> 
>     if ($r->has\_request\_body(**\\&post**)) {
>         return OK;
>     }
> 
>     return HTTP\_BAD\_REQUEST;
> }
> 
> sub **post** {
>     my $r = shift;
> 
>     $r->send\_http\_header;
> 
>     $r->print("request\_body: \\"", $r->request\_body, "\\"<br/>");
>     $r->print("request\_body\_file: \\"", $r->request\_body\_file, "\\"<br/>\\n");
> 
>     return OK;
> }
> 
> 1;
> 
> \_\_END\_\_

`$r->allow_ranges`

允許在發送響應時使用字節範圍。

`$r->discard_request_body`

命令nginx丟棄請求體。

``$r->header_in(`*field*`)``

返回指定的客戶端請求標頭欄位的值。

`$r->header_only`

決定是否整個響應或只有它的頭應該被發送到客戶端。

``$r->header_out(`*field*`, `*value*`)``

為指定的響應標頭欄位設置值。

``$r->internal_redirect(`*uri*`)``

對指定的`*uri*`進行內部重定向。實際的重定向發生在Perl處理程式執行完成之後。

> >自1.17.2版起，該方法接受轉義URI並支持重定向到命名位置。

``$r->log_error(`*errno*`, `*message*`)``

將指定的`*message*`寫入[error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log)。如果`*errno*`非零，則錯誤代碼及其說明將附加到消息中。

``$r->print(`*text*`, ...)``

將數據傳遞給客戶端。

`$r->request_body`

如果客戶端請求體還沒有寫入臨時文件，則返回客戶端請求體。為了確保客戶端請求體在內存中，其大小應限制為[client\_max\_body\_size](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size)，並且應使用[client\_body\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_buffer_size)設置足夠的緩衝區大小。

`$r->request_body_file`

返回帶有客戶端請求體的文件名。處理後，該文件應被刪除。要始終將請求體寫入文件，應啟用[client\_body\_in\_file\_only](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_in_file_only)。

`$r->request_method`

返回客戶端請求HTTP方法。

`$r->remote_addr`

返回客戶端IP位址。

`$r->flush`

立即將數據發送到客戶端。

``$r->sendfile(`*name*`[, `*offset*`[, `*length*`]])``

將指定的文件內容發送到客戶端。可選參數指定要傳輸的數據的初始偏移量和長度。實際的數據傳輸發生在Perl處理程式完成之後。

``$r->send_http_header([`*type*`])``

向客戶端發送響應頭，可選參數`*type*`設置響應頭的Content-Type欄位的值，如果該值為空字符串，則不發送Content-Type頭欄位。

``$r->status(`*code*`)``

設置響應代碼。

``$r->sleep(`*milliseconds*`, `*handler*`)``

設置指定的handler，並在指定的時間內停止處理請求。在此期間，nginx繼續處理其他請求。在指定的時間過去後，nginx將調用安裝的handler。注意handler函數應該通過引用傳遞。為了在handler之間傳遞數據，應該使用`$r->variable()`。示例：

> package hello;
> 
> use nginx;
> 
> sub handler {
>     my $r = shift;
> 
>     $r->discard\_request\_body;
>     $r->variable("var", "OK");
>     $r->sleep(1000, **\\&next**);
> 
>     return OK;
> }
> 
> sub **next** {
>     my $r = shift;
> 
>     $r->send\_http\_header;
>     $r->print($r->variable("var"));
> 
>     return OK;
> }
> 
> 1;
> 
> \_\_END\_\_

``$r->unescape(`*text*`)``

解碼以「%XX」形式編碼的文本。

`$r->uri`

返回請求URI。

``$r->variable(`*name*`[, `*value*`])``

返回或設置指定變量的值。變量是每個請求的本地變量。