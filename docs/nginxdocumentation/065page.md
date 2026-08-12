# page

> Source: https://nginx.org/en/docs/http/ngx_http_proxy_protocol_vendor_module.html

---

## 目錄

- [Module ngx\_http\_proxy\_protocol\_vendor\_module](#module-ngxhttpproxyprotocolvendormodule)
    - [Example Configuration](#example-configuration)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_proxy\_protocol\_vendor\_module

`ngx_http_proxy_protocol_vendor_module`模塊（1.23.3）允許從[PROXY protocol](http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)標頭的應用程式特定TLV獲取有關雲平台中連接的附加信息。

支持的雲平台：

-   亞馬遜網絡服務
-   Google Cloud Platform
-   Microsoft Azure

必須通過在[listen](https://nginx.org/en/docs/http/ngx_http_core_module.html#listen)指令中設置`proxy_protocol`參數來預先啟用PROXY協議。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

> proxy\_set\_header X-Conn-ID $proxy\_protocol\_tlv\_gcp\_conn\_id;
> 
> server {
>     listen 80   proxy\_protocol;
>     listen 443  ssl proxy\_protocol;
>     ...
> }

#### 嵌入變量

`$proxy_protocol_tlv_aws_vpce_id`

代表[ID of AWS VPC endpoint](https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-target-groups.html#proxy-protocol)的PROXY協議報頭中的TLV值

`$proxy_protocol_tlv_azure_pel_id`

代表[LinkID of Azure private endpoint](https://learn.microsoft.com/en-us/azure/private-link/private-link-service-overview#getting-connection-information-using-tcp-proxy-v2)的PROXY協議報頭中的TLV值

`$proxy_protocol_tlv_gcp_conn_id`

代表[Google Cloud PSC connection ID](https://cloud.google.com/vpc/docs/configure-private-service-connect-producer#proxy-protocol)的PROXY協議報頭中的TLV值