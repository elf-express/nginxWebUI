# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_mqtt_preread_module.html

---

## 目錄

- [Module ngx\_stream\_mqtt\_preread\_module](#module-ngxstreammqttprereadmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)
    - [Embedded Variables](#embedded-variables)

---

## Module ngx\_stream\_mqtt\_preread\_module

`ngx_stream_mqtt_preread_module`模塊（1.23.4）允許從消息傳輸遠程通信傳輸協議（MQTT）版本[3.1.1](https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/mqtt-v3.1.1.html)和[5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)的HTTP消息中提取信息，例如用戶名或客戶端ID。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

```nginx
mqtt_preread on;
return       $mqtt_preread_clientid;
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mqtt_preread</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mqtt_preread off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

啟用在[preread](https://nginx.org/en/docs/stream/stream_processing.html#preread_phase)階段從MQTT驗證ECT消息中提取信息。

#### 嵌入變量

`$mqtt_preread_clientid`

來自EQUECT消息的`clientid`值

`$mqtt_preread_username`

來自EQUECT消息的`username`值