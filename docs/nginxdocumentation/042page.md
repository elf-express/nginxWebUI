# page

> Source: https://nginx.org/en/docs/http/ngx_http_geoip_module.html

---

## 目錄

- [Module ngx\_http\_geoip\_module](#module-ngxhttpgeoipmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_geoip\_module

`ngx_http_geoip_module`模塊（0.8.6+）使用預編譯的[MaxMind](http://www.maxmind.com/)資料庫創建變量，其值取決於客戶端IP位址。

使用支持IPv6的資料庫（1.3.12、1.2.7）時，IPv4地址將作為IPv4映射的IPv6地址進行查找。

默認情況下不構建此模塊，應使用`--with-http_geoip_module`配置參數啟用。

> >此模塊需要[MaxMind GeoIP](http://www.maxmind.com/app/c)庫。

#### 配置示例

> http {
>     geoip\_country         GeoIP.dat;
>     geoip\_city            GeoLiteCity.dat;
>     geoip\_proxy           192.168.100.0/24;
>     geoip\_proxy           2001:0db8::/32;
>     geoip\_proxy\_recursive on;
>     ...

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_country</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

指定用於根據客戶端IP位址確定國家/地區的資料庫。使用此資料庫時，以下變量可用：

`$geoip_country_code`

兩個字母的國家代碼，例如「`RU`"、「`US`"。

`$geoip_country_code3`

三個字母的國家代碼，例如「`RUS`"、「`USA`"。

`$geoip_country_name`

國家/地區名稱，例如「`Russian Federation`"、「`United States`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_city</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

指定用於根據客戶端IP位址確定國家/地區、地區和城市的資料庫。使用此資料庫時，以下變量可用：

`$geoip_area_code`

電話區號（僅限美國）。

> >此變量可能包含過時的信息，因為相應的資料庫欄位已被棄用。

`$geoip_city_continent_code`

兩個字母的洲代碼，例如「`EU`"、「`NA`"。

`$geoip_city_country_code`

兩個字母的國家代碼，例如「`RU`"、「`US`"。

`$geoip_city_country_code3`

三個字母的國家代碼，例如「`RUS`"、「`USA`"。

`$geoip_city_country_name`

國家/地區名稱，例如「`Russian Federation`"、「`United States`"。

`$geoip_dma_code`

美國的DMA區域代碼（也稱為「地鐵代碼」），根據Google AdWords API中的[geotargeting](https://developers.google.com/adwords/api/docs/appendix/cities-DMAregions)。

`$geoip_latitude`

latitude.

`$geoip_longitude`

longitude.

`$geoip_region`

兩個符號的國家地區代碼（地區、領土、州、省、聯邦土地等），例如「`48`"、「`DC`"。

`$geoip_region_name`

國家地區名稱（地區、領地、州、省、聯邦土地等），例如「`Moscow City`"、「`District of Columbia`"。

`$geoip_city`

城市名稱，例如「`Moscow`"、「`Washington`"。

`$geoip_postal_code`

郵政編碼。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_org</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

此指令出現在1.0.3版中。

指定一個資料庫，用於根據客戶端IP位址確定組織。使用此資料庫時，可以使用以下變量：

`$geoip_org`

組織名稱，例如，「墨爾本大學」。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_proxy</strong> <code><i>address</i></code> | <code><i>CIDR</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

此指令出現在1.3.0版和1.2.1版中。

定義受信任的地址。當請求來自受信任的地址時，將使用「X-Forwarded-For」請求標頭欄位中的地址。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_proxy_recursive</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>geoip_proxy_recursive off;</pre></td></tr><tr><th>Context:</th><td><code>http</code><br></td></tr></tbody></table>

此指令出現在1.3.0版和1.2.1版中。

如果禁用遞歸搜索，則將使用「X-Forwarded-For」中最後發送的地址，而不是與其中一個可信地址匹配的原始客戶端地址。如果啟用遞歸搜索，則將使用「X-Forwarded-For」中最後發送的不可信地址，而不是與其中一個可信地址匹配的原始客戶端地址。