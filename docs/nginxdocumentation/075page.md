# page

> Source: https://nginx.org/en/docs/http/ngx_http_ssi_module.html

---

## 目錄

- [Module ngx\_http\_ssi\_module](#module-ngxhttpssimodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [SSI Commands](#ssi-commands)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_ssi\_module

`ngx_http_ssi_module`模塊是一個過濾器，用於處理通過它的響應中的SSI（伺服器端包含）命令。當前，受支持的SSI命令列表不完整。

#### 配置示例

> location / {
>     ssi on;
>     ...
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssi</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssi off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code>, <code>if in location</code><br></td></tr></tbody></table>

啟用或禁用響應中SSI命令的處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssi_last_modified</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssi_last_modified off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.1版中。

允許在SSI處理過程中保留原始響應的「Last-Modified」頭欄位，以便於響應緩存。

默認情況下，在處理過程中修改響應的內容時會刪除頭欄位，並且頭欄位可能包含動態生成的元素或部分，這些元素或部分的更改獨立於原始響應。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssi_min_file_chunk</strong> <code>size</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssi_min_file_chunk 1k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為存儲在磁碟上的響應部分設置最小值`*size*`，從該值開始，使用[sendfile](https://nginx.org/en/docs/http/ngx_http_core_module.html#sendfile)發送響應是有意義的。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssi_silent_errors</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssi_silent_errors off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果啟用此選項，則在SSI處理過程中發生錯誤時，將抑制「`[an error occurred while processing the directive]`」字符串的輸出。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssi_types</strong> <code><i>mime-type</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>ssi_types text/html;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

除了「`text/html`"之外，還允許行程具有指定MIME類型之回應中的SSI指令。特殊值「`*`」可符合任何MIME類型（0.8.29）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>ssi_value_length</strong> <code><i>length</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>ssi_value_length 256;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置SSI命令中參數值的最大長度。

#### SSI命令

SSI命令具有以下通用格式：

> <!--# command parameter1=value1 parameter2=value2 ... -->

支持以下命令：

`block`

定義可在`include`命令中用作存根的塊。該塊可包含其他SSI命令。該命令具有以下參數：

`name`

圖塊名稱。

Example:

> <!--# block name="one" -->
> stub
> <!--# endblock -->

`config`

設置SSI處理過程中使用的一些參數，即：

`errmsg`

返回在SSI處理過程中發生錯誤時輸出的字符串。默認情況下，輸出以下字符串：

> \[處理指令時出錯\]

`timefmt`

傳遞給`strftime()`函數的格式字符串，用於輸出日期和時間。默認情況下，使用以下格式：

> "%A, %d-%b-%Y %H:%M:%S %Z"

「`%s`」格式適合以秒為單位輸出時間。

`echo`

輸出變量的值。該命令具有以下參數：

`var`

變量名。

`encoding`

編碼方式。可能的值包括`none`、`url`和`entity`。默認情況下使用`entity`。

`default`

一個非標準參數，如果變量未定義，則設置輸出字符串。默認情況下，輸出「`(none)`」。命令

> <!--# echo var="name" default="**no**" -->

替換以下命令序列：

> <!--# if expr="$name" --><!--# echo var="name" --><!--#
>        else -->** 否 **<!--# endif -->

`if`

執行條件包含。支持以下命令：

> <!--# if expr="..." -->
> ...
> <!--# elif expr="..." -->
> ...
> <!--# else -->
> ...
> <!--# endif -->

當前僅支持一級嵌套。該命令具有以下參數：

`expr`

表達式。表達式可以是：

-   變量存在檢查：
    
    > <!--# if expr="$name" -->
    
-   變量與文本的比較：
    
    > <!--# if expr="$name = `*text*`" -->
    > <!--# if expr="$name != `*text*`" -->
    
-   一個正則表達式中的一個變量
    
    > <!--# if expr="$name = /`*text*`/" -->
    > <!--# if expr="$name != /`*text*`/" -->
    

如果`*text*`包含變量，則它們的值將被替換。正則表達式可以包含稍後可通過變量使用的位置捕獲和命名捕獲，例如：

> <!--# if expr="$name = /(.+)@(?P<domain>.+)/" -->
>     <!--# echo var="1" -->
>     <!--# echo var="domain" -->
> <!--# endif -->

`include`

將另一個請求的結果包含到響應中。該命令具有以下參數：

`file`

指定包含的文件，例如：

> <!--# include file="footer.html" -->

`virtual`

指定包含的請求，例如：

> <!--# include virtual="/remote/body.php?argument=value" -->

在一個頁面上指定並由代理或FastCGI/uwsgi/SCGI/gRPC伺服器處理的多個請求並行運行。如果需要順序處理，則應使用`wait`參數。

`stub`

一個非標準參數，用於命名塊，如果包含的請求導致空正文或在請求處理過程中發生錯誤，則將輸出其內容，例如：

> <!--# block name="one" -->&nbsp;<!--# endblock -->
> <!--# include virtual="/remote/body.php?argument=value" stub="one" -->

替換塊內容在包含的請求上下文中處理。

`wait`

指示在繼續SSI處理之前等待請求完全完成的非標準參數，例如：

> <!--# include virtual="/remote/body.php?argument=value" wait="yes" -->

`set`

一個非標準參數，指示將請求處理的成功結果寫入指定變量，例如：

> <!--# include virtual="/remote/body.php?argument=value" set="one" -->

響應的最大大小由[subrequest\_output\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_core_module.html#subrequest_output_buffer_size)指令（1.13.10）設置：

> location /remote/ {
>     subrequest\_output\_buffer\_size 64k;
>     ...
> }

在版本1.13.10之前，只有使用[ngx\_http\_proxy\_module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)、[ngx\_http\_memcached\_module](https://nginx.org/en/docs/http/ngx_http_memcached_module.html)、[ngx\_http\_fastcgi\_module](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html)（1.5.6）、[ngx\_http\_uwsgi\_module](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html)（1.5.6）和[ngx\_http\_scgi\_module](https://nginx.org/en/docs/http/ngx_http_scgi_module.html)（1.5.6）模塊獲得的響應結果才能寫入變量。響應的最大大小由[proxy\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffer_size)、[memcached\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_memcached_module.html#memcached_buffer_size)、[fastcgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_buffer_size)、[uwsgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_buffer_size)和[scgi\_buffer\_size](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_buffer_size)指令設置。

`set`

設置變量的值。該命令具有以下參數：

`var`

變量名。

`value`

變量值。如果賦值包含變量，則它們的值被替換。

#### 嵌入變量

`ngx_http_ssi_module`模塊支持兩個嵌入變量：

`$date_local`

本地時區的當前時間。格式由帶`timefmt`參數的`config`命令設置。

`$date_gmt`

GMT中的當前時間。格式由帶`timefmt`參數的`config`命令設置。