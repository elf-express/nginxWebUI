# page

> Source: https://nginx.org/en/docs/syslog.html

---

## 目錄

- [Logging to syslog](#logging-to-syslog)

---

## 登錄到系統日誌

[error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log)和[access\_log](https://nginx.org/en/docs/http/ngx_http_log_module.html#access_log)指令支持記錄到系統日誌。以下參數配置記錄到系統日誌：

`server=``*address*`

定義系統日誌伺服器的地址。該地址可以指定為域名或IP位址（帶有可選埠），也可以指定為在「`unix:`」前綴後指定的UNIX域套接字路徑。如果未指定埠，則使用UDP埠514。如果域名解析為多個IP位址，則使用第一個解析的地址。

`facility=``*string*`

設置系統日誌消息的設施，如[RFC 3164](https://datatracker.ietf.org/doc/html/rfc3164#section-4.1.1)中定義的。設施可以是「`kern`」、「`user`」、「`mail`」、「`daemon`」、「`auth`」、「`intern`」、「`lpr`」、「`news`」、「`uucp`」、「`clock`」、「`authpriv`」、「`ftp`」、「`ntp`」、「`audit`」、「`alert`」、「`cron`」之一「`local0`".."`local7`"。默認值為「`local7`"。

`severity=``*string*`

設置[access\_log](https://nginx.org/en/docs/http/ngx_http_log_module.html#access_log)的系統日誌消息的嚴重性，如[RFC 3164](https://datatracker.ietf.org/doc/html/rfc3164#section-4.1.1)中所定義。可能的值與[error\_log](https://nginx.org/en/docs/ngx_core_module.html#error_log)指令的第二個參數（級別）相同。默認值為「`info`"。

> >錯誤消息的嚴重性由nginx決定，因此在`error_log`指令中忽略該參數。

`tag=``*string*`

設置系統日誌消息的標籤。默認為「`nginx`"。

`nohostname`

禁止將「hostname」欄位添加到系統日誌消息頭中（1.9.7）。

系統日誌配置示例：

```nginx
error_log syslog:server=192.168.1.1 debug;

access_log syslog:server=unix:/var/log/nginx.sock,nohostname;
access_log syslog:server=[2001:db8::1]:12345,facility=local7,tag=nginx,severity=info combined;
```

> >自1.7.1版起可登錄到系統日誌。作為我們[commercial subscription](https://www.f5.com/products/nginx)的一部分，自1.5.3版起可登錄到系統日誌。