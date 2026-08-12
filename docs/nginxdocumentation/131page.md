# page

> Source: [https://nginx.org/en/docs/stream/ngx_stream_pass_module.html](https://nginx.org/en/docs/stream/ngx_stream_pass_module.html)

---

## 目錄

- [Module ngxstreampassmodule](#module-ngxstreampassmodule)
  - [Example Configuration](#example-configuration)
  - [Directives](#directives)

---

## Module ngxstreampassmodule

`ngx_stream_pass_module`模塊（1.25.5）允許將接受的連接直接傳遞到`http`、`stream`、`mail`和其他類似模塊中的任何配置的偵聽套接字。

#### 配置示例

http {
    server {
        listen 8000;

````nginx
```
    location / {
        root html;
    }
}
```

}

stream {
    server {
        listen 12345 ssl;

```
    ssl_certificate     domain.crt;
    ssl_certificate_key domain.key;

    pass 127.0.0.1:8000;
}
```

}
````

在示例中，在`stream`模塊中終止SSL/TLS後，連接將傳遞到`http`模塊。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>pass</strong> <code><i>address</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>server</code><br></td></tr></tbody></table>

設置要將客戶端連接傳遞到的伺服器地址。該地址可以指定為IP位址和埠：

```nginx
pass 127.0.0.1:12345;
```

或者作為UNIX域套接字路徑：

```nginx
pass unix:/tmp/stream.socket;
```

地址也可以使用變量指定：

```nginx
pass $upstream;
```

