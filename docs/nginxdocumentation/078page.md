# page

> Source: https://nginx.org/en/docs/http/ngx_http_stub_status_module.html

---

## 目錄

- [Module ngx\_http\_stub\_status\_module](#module-ngxhttpstubstatusmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Data](#data)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_http\_stub\_status\_module

`ngx_http_stub_status_module`模塊提供對基本狀態信息的訪問。

默認情況下未構建此模塊，應使用`--with-http_stub_status_module`配置參數啟用此模塊。

#### 配置示例

> location = /basic\_status {
>     stub\_status;
> }

此配置創建一個簡單的網頁，其中包含基本狀態數據，可能如下所示：

> >活躍連接數：291
> >伺服器接受已處理的請求
>  16630948 16630948 31070465
> >閱讀：6寫作：179等待：106

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>stub_status</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code>, <code>location</code><br></td></tr></tbody></table>

基本狀態信息可從周圍位置訪問。

> >在1.7.5之前的版本中，指令語法需要一個任意參數，例如"`stub_status on`"。

#### Data

將提供以下狀態信息：

`Active connections`

當前活動客戶端連接數，包括`Waiting`連接。

`accepts`

接受的客戶端連接總數。

`handled`

已處理的連接總數。通常，參數值與`accepts`相同，除非達到某些資源限制（例如[worker\_connections](https://nginx.org/en/docs/ngx_core_module.html#worker_connections)限制）。

`requests`

客戶端請求的總數。

`Reading`

nginx正在閱讀請求頭的當前連接數。

`Writing`

nginx將響應寫回客戶端的當前連接數。

`Waiting`

當前等待請求的空閒客戶端連接數。

#### 嵌入變量

`ngx_http_stub_status_module`模塊支持以下嵌入變量（1.3.14）：

`$connections_active`

與`Active connections`值相同;

`$connections_reading`

與`Reading`值相同;

`$connections_writing`

與`Writing`值相同;

`$connections_waiting`

與`Waiting`值相同。