# page

> Source: https://nginx.org/en/docs/http/ngx_http_geo_module.html

---

## 目錄

- [Module ngx\_http\_geo\_module](#module-ngxhttpgeomodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_geo\_module

`ngx_http_geo_module`模塊創建變量，其值取決於客戶端IP位址。

#### 配置示例

> geo $geo {
>     default        0;
> 
>     127.0.0.1      2;
>     192.168.1.0/24 1;
>     10.1.0.0/16    1;
> 
>     ::1            2;
>     2001:0db8::/32 1;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geo</strong> [<code><i>$address</i></code>] <code><i>$variable</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

描述指定變量的值對客戶端IP位址的依賴關係。默認情況下，地址取自`$remote_addr`變量，但也可以取自其他變量（0.7.27），例如：

> geo $arg\_remote\_addr $geo {
>     ...;
> }

> 由於變量僅在使用時才被計算，因此即使存在大量聲明的「`geo`」變量也不會為請求處理帶來任何額外的成本。

如果變量的值不表示有效的IP位址，則使用「`255.255.255.255`」地址。

在CIDR表示法中，地址可以指定為前綴（包括單個地址），也可以指定為範圍（0.7.23）。

> >從1.3.10和1.2.7版本開始支持IPv6前綴。

還支持以下特殊參數：

`delete`

刪除指定的網絡（0.7.23）。

`default`

如果客戶端地址與任何指定的地址都不匹配，則設置為變量的值。如果以CIDR表示法指定地址，則可以使用「`0.0.0.0/0`」和「`::/0`」代替`default`。如果未指定`default`，則默認值將為空字符串。

`include`

包含一個帶有地址和值的文件。可以有多個包含項。

`proxy`

定義了可信地址（0.8.7，0.7.63）。當請求來自可信地址時，將使用「X-Forwarded-For」請求報頭欄位中的地址。與常規地址相反，可信地址將按順序檢查。

> >從1.3.0和1.2.1版本開始支持可信IPv6地址。

`proxy_recursive`

啟用遞歸地址搜索（1.3.0，1.2.1）.如果禁用遞歸搜索，則將使用「X-Forwarded-For」中發送的最後一個地址，而不是與其中一個可信地址匹配的原始客戶端地址。如果啟用遞歸搜索，則將使用與其中一個可信地址匹配的原始客戶端地址，將使用在「X-Forwarded-For」中發送的最後一個不可信地址。

`ranges`

表示地址被指定為範圍（0.7.23）。這個參數應該是第一個。為了加快加載一個geo基，地址應該按升序排列。

`volatile`

表示變量不可緩存（1.29.3）。

Example:

> geo $country {
>     default        ZZ;
>     include        conf/geo.conf;
>     delete         127.0.0.0/16;
>     proxy          192.168.100.0/24;
>     proxy          2001:0db8::/32;
> 
>     127.0.0.0/24   US;
>     127.0.0.1/32   RU;
>     10.1.0.0/16    RU;
>     192.168.1.0/24 UK;
> }

`conf/geo.conf`文件可以包含以下行：

> 10.2.0.0/16    RU;
> 192.168.2.0/24 RU;

使用最具體匹配的值。例如，對於127.0.0.1地址，將選擇值「`RU`」，而不是「`US`"。

範圍示例：

> geo $country {
>     ranges;
>     default                   ZZ;
>     127.0.0.0-127.0.0.0       US;
>     127.0.0.1-127.0.0.1       RU;
>     127.0.0.1-127.0.0.255     US;
>     10.1.0.0-10.1.255.255     RU;
>     192.168.1.0-192.168.1.255 UK;
> }