# page

> Source: https://nginx.org/en/docs/configure.html

---

## 目錄

- [從原始碼建構 nginx](#從原始碼建構-nginx)

---

## 從原始碼建構 nginx

使用 `configure` 指令配置建置。它定義了系統的各個方面，包括允許 nginx 用於連接處理的方法。最後它創建了一個`Makefile`。

`configure`指令支援以下參數：

`--help`

列印幫助訊息。

`` --prefix=`*路徑*` ``

定義一個保存伺服器檔案的目錄。該目錄也將用於 `configure` 設定的所有相對路徑（庫來源的路徑除外）和 `nginx.conf` 設定檔中。預設為`/usr/local/nginx`目錄。

`` --sbin-path=`*路徑*` ``

設定 nginx 可執行檔的名稱。此名稱僅在安裝期間使用。預設情況下，檔案名稱為「` `*prefix*`/sbin/nginx `」。

`` --modules-path=`*路徑*` ``

定義將安裝 nginx 動態模組的目錄。預設使用 `` `*prefix*`/modules `` 目錄。

`` --conf-path=`*路徑*` ``

設定`nginx.conf`設定檔的名稱。如果需要，nginx 始終可以透過在命令列參數 `` -c `*file*` ``. By default the file is named `` `*prefix*`/conf/nginx.conf `` 中指定不同的設定檔來啟動。

`` --error-log-path=`*路徑*` ``

設定主要錯誤、警告和診斷文件的名稱。安裝後，始終可以使用 [error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log) 指令在 `nginx.conf` 設定檔中變更檔案名稱。預設情況下，檔案名稱為「` `*prefix*`/logs/error.log `」。

`` --pid-path=`*路徑*` ``

設定將儲存主進程的進程 ID 的 `nginx.pid` 檔案的名稱。安裝後，始終可以使用 [pid](https://nginx.org/en/docs/ngx_core_module.html#pid) 指令在 `nginx.conf` 設定檔中變更檔案名稱。預設情況下，檔案名稱為「` `*prefix*`/logs/nginx.pid `」。

`` --lock-path=`*路徑*` ``

設定鎖定檔案名稱的前綴。安裝後，始終可以使用 [lock\_file](https://nginx.org/en/docs/ngx_core_module.html#lock_file) 指令在 `nginx.conf` 設定檔中變更該值。預設值為`` `*前綴*`/logs/nginx.lock ``。

`` --user=`*名字*` ``

設定非特權使用者的名稱，其憑證將由工作進程使用。安裝後，始終可以使用 [user](https://nginx.org/en/docs/ngx_core_module.html#user) 指令在 `nginx.conf` 設定檔中變更名稱。預設使用者名稱是nobody。

`` --group=`*名字*` ``

設定工作進程將使用其憑證的群組的名稱。安裝後，始終可以使用 [user](https://nginx.org/en/docs/ngx_core_module.html#user) 指令在 `nginx.conf` 設定檔中變更名稱。預設情況下，群組名稱設定為非特權使用者的名稱。

`` --build=`*名字*` ``

設定可選的 nginx 建置名稱。

`` --builddir=`*路徑*` ``

設定建置目錄。

`--with-select_module`  
`--without-select_module`

啟用或停用建置允許伺服器使用`select()`方法的模組。如果平台似乎不支援更合適的方法（例如 kqueue、epoll 或 /dev/poll），則會自動建立此模組。

`--with-poll_module`  
`--without-poll_module`

啟用或停用建置允許伺服器使用`poll()`方法的模組。如果平台似乎不支援更合適的方法（例如 kqueue、epoll 或 /dev/poll），則會自動建立此模組。

`--with-threads`

允許使用[線程池](https://nginx.org/en/docs/ngx_core_module.html#thread_pool)。

`--with-file-aio`

允許在 FreeBSD 和 Linux 上使用 [非同步檔案 I/O](https://nginx.org/en/docs/http/ngx_http_core_module.html#aio) (AIO)。

`--with-http_ssl_module`

允許建構一個將[HTTPS協定支援](https://nginx.org/en/docs/http/ngx_http_ssl_module.html)加入HTTP伺服器的模組。預設情況下不建置此模組。建置和運行此模組需要 OpenSSL 庫。

`--with-http_v2_module`

允許建置支援 [HTTP/2](https://nginx.org/en/docs/http/ngx_http_v2_module.html) 的模組。預設情況下不建置此模組。

`--with-http_v3_module`

允許建置支援 [HTTP/3](https://nginx.org/en/docs/http/ngx_http_v3_module.html) 的模組。預設情況下不建置此模組。建置和執行此模組需要提供 HTTP/3 支援的 OpenSSL 庫。

`--with-http_realip_module`

允許建置 [ngx\_http\_realip\_module](https://nginx.org/en/docs/http/ngx_http_realip_module.html) 模組，將客戶端位址變更為在指定標頭欄位中傳送的位址。預設情況下不建置此模組。

`--with-http_addition_module`

允許建構 [ngx\_http\_addition\_module](https://nginx.org/en/docs/http/ngx_http_addition_module.html) 模組，在回應之前和之後加入文字。預設情況下不建置此模組。

`--with-http_xslt_module`  
`--with-http_xslt_module=dynamic`

允許建構 [ngx\_http\_xslt\_module](https://nginx.org/en/docs/http/ngx_http_xslt_module.html) 模組，該模組使用一個或多個 XSLT 樣式表轉換 XML 回應。預設情況下不建置此模組。建置和執行此模組需要 [libxml2](http://xmlsoft.org/) 和 [libxslt](http://xmlsoft.org/XSLT/) 函式庫。

`--with-http_image_filter_module`  
`--with-http_image_filter_module=dynamic`

允許建置 [ngx\_http\_image\_filter\_module](https://nginx.org/en/docs/http/ngx_http_image_filter_module.html) 模組，用於轉換 JPEG、GIF、PNG 和 WebP 格式的映像。預設情況下不建置此模組。

`--with-http_geoip_module`  
`--with-http_geoip_module=dynamic`

允许构建 [ngx\_http\_geoip\_module](https://nginx.org/en/docs/http/ngx_http_geoip_module.html) 模块，该模块根据客户端 IP 地址和预编译的 [MaxMind](http://www.maxmind.com/) 数据库创建变量。預設情況下不建置此模組。

`--with-http_sub_module`

允許建構 [ngx\_http\_sub\_module](https://nginx.org/en/docs/http/ngx_http_sub_module.html) 模組，該模組透過將一個指定字串替換為另一個指定字串來修改回應。預設情況下不建置此模組。

`--with-http_dav_module`

允許建置 [ngx\_http\_dav\_module](https://nginx.org/en/docs/http/ngx_http_dav_module.html) 模組，該模組透過 WebDAV 協定提供檔案管理自動化。預設情況下不建置此模組。

`--with-http_flv_module`

允許建置 [ngx\_http\_flv\_module](https://nginx.org/en/docs/http/ngx_http_flv_module.html) 模組，為 Flash 視訊 (FLV) 檔案提供偽流伺服器端支援。預設情況下不建置此模組。

`--with-http_mp4_module`

允許建置 [ngx\_http\_mp4\_module](https://nginx.org/en/docs/http/ngx_http_mp4_module.html) 模組，為 MP4 檔案提供偽流伺服器端支援。預設情況下不建置此模組。

`--with-http_gunzip_module`

允許建置 [ngx\_http\_gunzip\_module](https://nginx.org/en/docs/http/ngx_http_gunzip_module.html) 模組，為不支援「gzip」編碼方法的客戶端使用「`Content-Encoding: gzip`」解壓縮回應。預設情況下不建置此模組。

`--with-http_gzip_static_module`

允許建置 [ngx\_http\_gzip\_static\_module](https://nginx.org/en/docs/http/ngx_http_gzip_static_module.html) 模組，該模組允許發送帶有「`.gz`」檔案副檔名的預壓縮檔案而不是常規檔案。預設情況下不建置此模組。

`--with-http_auth_request_module`

允許建置 [ngx\_http\_auth\_request\_module](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html) 模組，該模組根據子請求的結果實現客戶端授權。預設情況下不建置此模組。

`--with-http_random_index_module`

允許建置 [ngx\_http\_random\_index\_module](https://nginx.org/en/docs/http/ngx_http_random_index_module.html) 模組，該模組處理以斜槓字元（‘`/`’）結尾的請求，並在目錄中選擇一個隨機檔案作為索引檔案。預設情況下不建置此模組。

`--with-http_secure_link_module`

允許建置 [ngx\_http\_secure\_link\_module](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html) 模組。預設情況下不建置此模組。

`--with-http_degradation_module`

能夠建造`ngx_http_degradation_module`模組。預設情況下不建置此模組。

`--with-http_slice_module`

允許建置 [ngx\_http\_slice\_module](https://nginx.org/en/docs/http/ngx_http_slice_module.html) 模組，該模組將請求拆分為子請求，每個子請求傳回一定範圍的回應。此模組提供了更有效的大響應快取。預設情況下不建置此模組。

`--with-http_stub_status_module`

允許建置 [ngx\_http\_stub\_status\_module](https://nginx.org/en/docs/http/ngx_http_stub_status_module.html) 模組，該模組提供對基本狀態資訊的存取。預設情況下不建置此模組。

`--without-http_charset_module`

停用建置 [ngx\_http\_charset\_module](https://nginx.org/en/docs/http/ngx_http_charset_module.html) 模組，該模組將指定的字元集新增至「Content-Type」回應頭字段，並且還可以將資料從一種字元集轉換為另一種字元集。

`--without-http_gzip_module`

停用建構一個[壓縮 HTTP 伺服器回應](https://nginx.org/en/docs/http/ngx_http_gzip_module.html) 的模組。建置和運行該模組需要 zlib 庫。

`--without-http_ssi_module`

停用建置 [ngx\_http\_ssi\_module](https://nginx.org/en/docs/http/ngx_http_ssi_module.html) 模組，該模組處理通過它的回應中的 SSI（伺服器端包含）命令。

`--without-http_userid_module`

停用建置 [ngx\_http\_userid\_module](https://nginx.org/en/docs/http/ngx_http_userid_module.html) 模組來設定適合用戶端辨識的 cookie。

`--without-http_access_module`

禁用建置允許限制對某些客戶端位址的存取的 [ngx\_http\_access\_module](https://nginx.org/en/docs/http/ngx_http_access_module.html) 模組。

`--without-http_auth_basic_module`

停用建置 [ngx\_http\_auth\_basic\_module](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html) 模組，該模組允許透過使用「HTTP 基本驗證」協定驗證使用者名稱和密碼來限制對資源的存取。

`--without-http_mirror_module`

停用建置 [ngx\_http\_mirror\_module](https://nginx.org/en/docs/http/ngx_http_mirror_module.html) 模組，該模組透過建立後台鏡像子請求來實現原始請求的鏡像。

`--without-http_autoindex_module`

停用建置 [ngx\_http\_autoindex\_module](https://nginx.org/en/docs/http/ngx_http_autoindex_module.html) 模組，該模組處理以斜線字元（‘`/`’）結束的請求，並產生目錄列表，以防 [ngx\_http\_index\_module](https://nginx.org/en/docs/http/ngx_http_index_module.html) 模組找不到索引檔。

`--without-http_geo_module`

停用建置 [ngx\_http\_geo\_module](https://nginx.org/en/docs/http/ngx_http_geo_module.html) 模組，該模組根據客戶端 IP 位址建立其值的變數。

`--without-http_map_module`

停用建置 [ngx\_http\_map\_module](https://nginx.org/en/docs/http/ngx_http_map_module.html) 模組，該模組建立其值取決於其他變數值的變數。

`--without-http_split_clients_module`

停用建置為 A/B 測試建立變數的 [ngx\_http\_split\_clients\_module](https://nginx.org/en/docs/http/ngx_http_split_clients_module.html) 模組。

`--without-http_referer_module`

停用建置 [ngx\_http\_referer\_module](https://nginx.org/en/docs/http/ngx_http_referer_module.html) 模組，該模組可以阻止「Referer」標頭欄位中具有無效值的請求存取網站。

`--without-http_rewrite_module`

禁止建置允許 HTTP 伺服器[重定向請求並更改請求的 URI](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html) 的模組。建置和運行該模組需要 PCRE 庫。

`--without-http_proxy_module`

停用建置 HTTP 伺服器 [代理模組](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)。

`--without-http_fastcgi_module`

停用建置將請求傳遞至 FastCGI 伺服器的 [ngx\_http\_fastcgi\_module](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html) 模組。

`--without-http_uwsgi_module`

停用建置將請求傳遞到 uwsgi 伺服器的 [ngx\_http\_uwsgi\_module](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html) 模組。

`--without-http_scgi_module`

停用建置將請求傳遞到 SCGI 伺服器的 [ngx\_http\_scgi\_module](https://nginx.org/en/docs/http/ngx_http_scgi_module.html) 模組。

`--without-http_grpc_module`

停用建置將請求傳遞到 gRPC 伺服器的 [ngx\_http\_grpc\_module](https://nginx.org/en/docs/http/ngx_http_grpc_module.html) 模組。

`--without-http_tunnel_module`

停用建置處理 HTTP/1.1 CONNECT 請求並建立端對端虛擬連線的 [ngx\_http\_tunnel\_module](https://nginx.org/en/docs/http/ngx_http_tunnel_module.html) 模組。

`--without-http_memcached_module`

停用建置從 memcached 伺服器取得回應的 [ngx\_http\_memcached\_module](https://nginx.org/en/docs/http/ngx_http_memcached_module.html) 模組。

`--without-http_limit_conn_module`

停用建置 [ngx\_http\_limit\_conn\_module](https://nginx.org/en/docs/http/ngx_http_limit_conn_module.html) 模組，該模組限制每個金鑰的連線數，例如來自單一 IP 位址的連線數。

`--without-http_limit_req_module`

停用建置 [ngx\_http\_limit\_req\_module](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html) 模組，該模組限制每個按鍵的請求處理速率，例如來自單一 IP 位址的請求的處理速率。

`--without-http_empty_gif_module`

停用建置[發出單像素透明 GIF](https://nginx.org/en/docs/http/ngx_http_empty_gif_module.html) 的模組。

`--without-http_browser_module`

停用建置 [ngx\_http\_browser\_module](https://nginx.org/en/docs/http/ngx_http_browser_module.html) 模組，該模組建立其值取決於「User-Agent」請求標頭欄位的值的變數。

`--without-http_upstream_hash_module`

停用建置實作 [hash](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#hash) 負載平衡方法的模組。

`--without-http_upstream_ip_hash_module`

停用建置實作 [ip\_hash](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#ip_hash) 負載平衡方法的模組。

`--without-http_upstream_least_conn_module`

停用建置實作 [least\_conn](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#least_conn) 負載平衡方法的模組。

`--without-http_upstream_least_time_module`

停用建置實作 [least\_time](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#least_time) 負載平衡方法的模組。

`--without-http_upstream_random_module`

停用建置實作 [random](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#random) 負載平衡方法的模組。

`--without-http_upstream_keepalive_module`

停用建置向上游伺服器提供[連線快取](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#keepalive)的模組。

`--without-http_upstream_zone_module`

停用建置一個模組，該模組可以將上游群組的運行時狀態儲存在共享記憶體[區域](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#zone)中。

`--without-http_upstream_sticky_module`

停用建置向上游伺服器提供[會話關聯](https://nginx.org/en/docs/http/ngx_http_upstream_module.html#sticky)的模組。

`--with-http_perl_module`  
`--with-http_perl_module=dynamic`

允許建構[嵌入式 Perl 模組](https://nginx.org/en/docs/http/ngx_http_perl_module.html)。預設情況下不建置此模組。

`` --with-perl_modules_path=`*路徑*` ``

定義一個儲存 Perl 模組的目錄。

`` --with-perl=`*路徑*` ``

設定 Perl 二進位檔案的名稱。

`` --http-log-path=`*路徑*` ``

設定 HTTP 伺服器的主請求日誌檔案的名稱。安裝後，始終可以使用 [access\_log](https://nginx.org/en/docs/http/ngx_http_log_module.html#access_log) 指令在 `nginx.conf` 設定檔中變更檔案名稱。預設情況下，檔案名稱為「` `*prefix*`/logs/access.log `」。

`` --http-client-body-temp-path=`*路徑*` ``

定義一個目錄，用於儲存保存客戶端請求主體的暫存檔案。安裝後，始終可以使用 [client\_body\_temp\_path](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_temp_path) 指令在 `nginx.conf` 設定檔中變更目錄。預設情況下，該目錄名為「` `*prefix*`/client_body_temp `」。

`` --http-proxy-temp-path=`*路徑*` ``

定義一個目錄，用於儲存帶有從代理伺服器接收到的資料的暫存檔案。安裝後，始終可以使用 [proxy\_temp\_path](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_temp_path) 指令在 `nginx.conf` 設定檔中變更目錄。預設情況下，該目錄名為「` `*prefix*`/proxy_temp `」。

`` --http-fastcgi-temp-path=`*路徑*` ``

定義目錄，用於儲存從 FastCGI 伺服器接收的資料的暫存檔案。安裝後，始終可以使用 [fastcgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_fastcgi_module.html#fastcgi_temp_path) 指令在 `nginx.conf` 設定檔中變更目錄。預設情況下，該目錄名為「` `*prefix*`/fastcgi_temp `」。

`` --http-uwsgi-temp-path=`*路徑*` ``

定義一個目錄，用於儲存從 uwsgi 伺服器接收的資料的暫存檔案。安裝後，始終可以使用 [uwsgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_uwsgi_module.html#uwsgi_temp_path) 指令在 `nginx.conf` 設定檔中變更目錄。預設情況下，該目錄名為「` `*prefix*`/uwsgi_temp `」。

`` --http-scgi-temp-path=`*路徑*` ``

定義目錄，用於儲存包含從 SCGI 伺服器接收的資料的暫存檔案。安裝後，始終可以使用 [scgi\_temp\_path](https://nginx.org/en/docs/http/ngx_http_scgi_module.html#scgi_temp_path) 指令在 `nginx.conf` 設定檔中變更目錄。預設情況下，該目錄名為「` `*prefix*`/scgi_temp `」。

`--without-http`

停用 [HTTP](https://nginx.org/en/docs/http/ngx_http_core_module.html) 伺服器。

`--without-http-cache`

禁用 HTTP 快取。

`--with-mail`  
`--with-mail=dynamic`

啟用 POP3/IMAP4/SMTP [郵件代理](https://nginx.org/en/docs/mail/ngx_mail_core_module.html) 伺服器。

`--with-mail_ssl_module`

允許建立一個模組，將[SSL/TLS協定支援](https://nginx.org/en/docs/mail/ngx_mail_ssl_module.html)加入到郵件代理伺服器。預設情況下不建置此模組。建置和運行此模組需要 OpenSSL 庫。

`--without-mail_pop3_module`

禁用郵件代理伺服器中的 [POP3](https://nginx.org/en/docs/mail/ngx_mail_pop3_module.html) 協定。

`--without-mail_imap_module`

停用郵件代理伺服器中的 [IMAP](https://nginx.org/en/docs/mail/ngx_mail_imap_module.html) 協定。

`--without-mail_smtp_module`

禁用郵件代理伺服器中的 [SMTP](https://nginx.org/en/docs/mail/ngx_mail_smtp_module.html) 協定。

`--with-stream`  
`--with-stream=dynamic`

允許建構用於通用 TCP/UDP 代理和負載平衡的 [流模組](https://nginx.org/en/docs/stream/ngx_stream_core_module.html)。預設情況下不建置此模組。

`--with-stream_ssl_module`

允許建構一個將 [SSL/TLS 協定支援](https://nginx.org/en/docs/stream/ngx_stream_ssl_module.html) 新增到流模組的模組。預設情況下不建置此模組。建置和運行此模組需要 OpenSSL 庫。

`--with-stream_realip_module`

允許建置 [ngx\_stream\_realip\_module](https://nginx.org/en/docs/stream/ngx_stream_realip_module.html) 模組，將客戶端位址變更為 PROXY 協定標頭中傳送的位址。預設情況下不建置此模組。

`--with-stream_geoip_module`  
`--with-stream_geoip_module=dynamic`

允許建置 [ngx\_stream\_geoip\_module](https://nginx.org/en/docs/stream/ngx_stream_geoip_module.html) 模組，該模組根據客戶端 IP 位址和預先編譯的 [MaxMind](http://www.maxmind.com/) 資料庫建立變數。預設情況下不建置此模組。

`--with-stream_ssl_preread_module`

允許建置 [ngx\_stream\_ssl\_preread\_module](https://nginx.org/en/docs/stream/ngx_stream_ssl_preread_module.html) 模組，該模組允許從 [ClientHello](https://datatracker.ietf.org/doc/html/rfc5246#section-7.4.1.2) 訊息中提取訊息，而無需終止 SSL/TLS。預設情況下不建置此模組。

`--without-stream_limit_conn_module`

停用建置 [ngx\_stream\_limit\_conn\_module](https://nginx.org/en/docs/stream/ngx_stream_limit_conn_module.html) 模組，該模組限制每個金鑰的連接數，例如來自單一 IP 位址的連接數。

`--without-stream_access_module`

禁用建置允許限制對某些客戶端位址的存取的 [ngx\_stream\_access\_module](https://nginx.org/en/docs/stream/ngx_stream_access_module.html) 模組。

`--without-stream_geo_module`

停用建置 [ngx\_stream\_geo\_module](https://nginx.org/en/docs/stream/ngx_stream_geo_module.html) 模組，該模組根據客戶端 IP 位址建立其值的變數。

`--without-stream_map_module`

停用建置 [ngx\_stream\_map\_module](https://nginx.org/en/docs/stream/ngx_stream_map_module.html) 模組，該模組建立其值取決於其他變數值的變數。

`--without-stream_split_clients_module`

停用建置為 A/B 測試建立變數的 [ngx\_stream\_split\_clients\_module](https://nginx.org/en/docs/stream/ngx_stream_split_clients_module.html) 模組。

`--without-stream_return_module`

停用建置 [ngx\_stream\_return\_module](https://nginx.org/en/docs/stream/ngx_stream_return_module.html) 模組，該模組將某些指定值傳送至客戶端，然後關閉連線。

`--without-stream_pass_module`

停用建置 [ngx\_stream\_pass\_module](https://nginx.org/en/docs/stream/ngx_stream_pass_module.html) 模組，該模組將接受的連接傳遞到其他偵聽套接字。

`--without-stream_set_module`

停用建構為變數設定值的 [ngx\_stream\_set\_module](https://nginx.org/en/docs/stream/ngx_stream_set_module.html) 模組。

`--without-stream_upstream_hash_module`

停用建置實作 [hash](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#hash) 負載平衡方法的模組。

`--without-stream_upstream_least_conn_module`

停用建置實作 [least\_conn](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#least_conn) 負載平衡方法的模組。

`--without-stream_upstream_least_time_module`

停用建置實作 [least\_time](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#least_time) 負載平衡方法的模組。

`--without-stream_upstream_random_module`

停用建置實作 [random](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#random) 負載平衡方法的模組。

`--without-stream_upstream_zone_module`

停用建置一個模組，該模組可以將上游群組的運行時狀態儲存在共享記憶體[區域](https://nginx.org/en/docs/stream/ngx_stream_upstream_module.html#zone)中。

`--with-google_perftools_module`

允許建置 [ngx\_google\_perftools\_module](https://nginx.org/en/docs/ngx_google_perftools_module.html) 模組，該模組可以使用 [Google Performance Tools](https://github.com/gperftools/gperftools) 對 nginx 工作流程進行分析。此模組適用於 nginx 開發人員，預設不建置。

`--with-cpp_test_module`

能夠建造`ngx_cpp_test_module`模組。

`` --add-module=`*路徑*` ``

啟用外部模組。

`` --add-dynamic-module=`*路徑*` ``

啟用外部動態模組。

`--with-compat`

啟用動態模組相容性。

`` --with-cc=`*路徑*` ``

設定 C 編譯器的名稱。

`` --with-cpp=`*路徑*` ``

設定 C 預處理器的名稱。

`` --with-cc-opt=`*參數*` ``

設定將會新增到 CFLAGS 變數的附加參數。在FreeBSD下使用系統PCRE函式庫時，需要指定`--with-cc-opt="-I /usr/local/include"`。如果需要增加`select()`支援的檔案數量，也可以在這裡指定，例如：`--with-cc-opt="-D FD_SETSIZE=2048"`。

`` --with-ld-opt=`*參數*` ``

設定連結期間將使用的附加參數。在FreeBSD下使用系統PCRE函式庫時，需要指定`--with-ld-opt="-L /usr/local/lib"`。

`` --with-cpu-opt=`*CPU*` ``

允許依指定 CPU 進行建構：`pentium`、`pentiumpro`、`pentium3`、`pentium4`、`athlon`、`opteron`、`sparc32`⟦、⟦121211⟧⧦

`--without-pcre`

禁用 PCRE 庫的使用。

`--with-pcre`

強制使用 PCRE 函式庫。

`` --with-pcre=`*路徑*` ``

設定 PCRE 庫源的路徑。庫發行版需從 [PCRE](http://www.pcre.org/) 網站下載並解壓縮。剩下的由nginx的`./configure`和`make`完成。 [location](https://nginx.org/en/docs/http/ngx_http_core_module.html#location) 指令和 [ngx\_http\_rewrite\_module](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html) 模組中的正規表示式支援需要該函式庫。

`` --with-pcre-opt=`*參數*` ``

為 PCRE 設定附加建置選項。

`--with-pcre-jit`

建立具有「即時編譯」支援的 PCRE 函式庫（1.1.12，[pcre\_jit](https://nginx.org/en/docs/ngx_core_module.html#pcre_jit) 指令）。

`--without-pcre2`

禁用 PCRE2 庫而不是原始 PCRE 庫 (1.21.5)。

`` --with-zlib=`*路徑*` ``

設定 zlib 庫來源的路徑。庫發行版需要從 [zlib](http://zlib.net/) 網站下載並解壓縮。剩下的由nginx的`./configure`和`make`完成。 [ngx\_http\_gzip\_module](https://nginx.org/en/docs/http/ngx_http_gzip_module.html) 模組需要該函式庫。

`` --with-zlib-opt=`*參數*` ``

為 zlib 設定附加建置選項。

`` --with-zlib-asm=`*CPU*` ``

允許使用針對指定 CPU 之一最佳化的 zlib 組譯器來源：`pentium`、`pentiumpro`。

`--with-libatomic`

強制使用 libatomic\_ops 庫。

`` --with-libatomic=`*路徑*` ``

設定 libatomic\_ops 庫來源的路徑。

`` --with-openssl=`*路徑*` ``

設定 OpenSSL 庫來源的路徑。

`` --with-openssl-opt=`*參數*` ``

為 OpenSSL 設定附加建置選項。

`--with-debug`

啟用[調試日誌](https://nginx.org/en/docs/debugging_log.html)。

參數使用範例（所有這些都需要在一行中輸入）：

> ./配置
> --sbin-path=/usr/local/nginx/nginx
> --conf-path=/usr/local/nginx/nginx.conf
> --pid-path=/usr/local/nginx/nginx.pid
> --with-http\_ssl\_module
> --with-pcre=../pcre2-10.39
> --with-zlib=../zlib-1.3

配置完成後，使用`make`編譯並安裝nginx。