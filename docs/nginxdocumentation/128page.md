# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_mqtt_filter_module.html

---

## 目錄

- [Module ngx\_stream\_mqtt\_filter\_module](#module-ngxstreammqttfiltermodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_mqtt\_filter\_module

`ngx_stream_mqtt_filter_module`模塊（1.23.4）提供對消息收發遙測傳輸協議（MQTT）版本[3.1.1](https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/mqtt-v3.1.1.html)和[5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)的支持。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

```nginx
listen            127.0.0.1:18883;
proxy_pass        backend;
proxy_buffer_size 16k;

mqtt             on;
mqtt_set_connect clientid "$client";
mqtt_set_connect username "$name";
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mqtt</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mqtt off;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

為給定的虛擬伺服器啟用MQTT協議。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mqtt_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mqtt_buffers 100 1k;</pre></td></tr><tr><th>Context:</th><td><code>stream</code>, <code>server</code><br></td></tr></tbody></table>

此指令出現在1.25.1版中。

為單個連接設置用於處理MQTT消息的緩衝區的`*number*`和`*size*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mqtt_rewrite_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mqtt_rewrite_buffer_size 4k|8k;</pre></td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

> >此指令自版本1.25.1起已過時。應改用[mqtt\_buffers](https://nginx.org/en/docs/stream/ngx_stream_mqtt_filter_module.html#mqtt_buffers)指令。

設置用於寫入修改消息的緩衝區的`*size*`。默認情況下，緩衝區大小等於一個內存頁。根據平台的不同，緩衝區大小可以是4K或8 K。但是，緩衝區大小可以更小。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mqtt_set_connect</strong> <code>field</code> <code><i>value</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

將消息`field`設置為給定的`value`，以用於驗證ECT消息。支持以下欄位：`clientid`、`username`和`password`。該值可以包含文本、變量及其組合。

可以在同一級別上指定多個`mqtt_set_connect`指令：

```nginx
mqtt_set_connect clientid "$client";
mqtt_set_connect username "$name";
```