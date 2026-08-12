# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_geoip_module.html

---

## 目錄

- [Module ngx\_stream\_geoip\_module](#module-ngxstreamgeoipmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_geoip\_module

`ngx_stream_geoip_module`模塊（1.11.3）使用預編譯的[MaxMind](http://www.maxmind.com/)資料庫創建變量，其值取決於客戶端IP位址。

使用支持IPv6的資料庫時，IPv4地址將作為IPv4映射的IPv6地址進行查找。

默認情況下不構建此模塊，應使用`--with-stream_geoip_module`配置參數啟用。

> >此模塊需要[MaxMind GeoIP](http://www.maxmind.com/app/c)庫。

#### 配置示例

> stream {
>     geoip\_country         GeoIP.dat;
>     geoip\_city            GeoLiteCity.dat;
> 
>     map $geoip\_city\_continent\_code $nearest\_server {
>         default        example.com;
>         EU          eu.example.com;
>         NA          na.example.com;
>         AS          as.example.com;
>     }
>    ...
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_country</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

指定用於根據客戶端IP位址確定國家/地區的資料庫。使用此資料庫時，以下變量可用：

`$geoip_country_code`

兩個字母的國家代碼，例如「`RU`"、「`US`"。

`$geoip_country_code3`

三個字母的國家代碼，例如「`RUS`"、「`USA`"。

`$geoip_country_name`

國家/地區名稱，例如「`Russian Federation`"、「`United States`"。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_city</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

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

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>geoip_org</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

指定一個資料庫，用於根據客戶端IP位址確定組織。使用此資料庫時，可以使用以下變量：

`$geoip_org`

組織名稱，例如，「墨爾本大學」。