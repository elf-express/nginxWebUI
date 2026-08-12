# page

> Source: https://nginx.org/en/docs/quic.html

---

## 目錄

- [Support for QUIC and HTTP/3](#support-for-quic-and-http3)
    - [Building from sources](#building-from-sources)
    - [Configuration tips](#configuration-tips)
    - [Troubleshooting](#troubleshooting)

---

## 支持QUIC和HTTP/3

Support for [QUIC](https://datatracker.ietf.org/doc/html/rfc9000) and [HTTP/3](https://datatracker.ietf.org/doc/html/rfc9114) protocols is available since 1.25.0, it is included in Linux [binary packages](https://nginx.org/en/linux_packages.html). Please refer to the [ngx\_http\_v3\_module](https://nginx.org/en/docs/http/ngx_http_v3_module.html) documentation.

#### 從原始碼構建

使用`configure`命令配置構建。有關詳細信息，請參閱[Building nginx from Sources](https://nginx.org/en/docs/configure.html)。

建議使用[OpenSSL](https://openssl.org/)library版本3.5.1或更高版本來構建支持QUIC的nginx。否則，將使用不支持[early data](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_early_data)的[OpenSSL](https://openssl.org/)兼容層。或者，可以使用[BoringSSL](https://boringssl.googlesource.com/boringssl)，[LibreSSL](https://www.libressl.org/)或[QuicTLS](https://github.com/quictls/openssl)預構建的library。

使用以下命令配置nginx與[BoringSSL](https://boringssl.googlesource.com/boringssl)：

```bash
./configure
    --with-debug
    --with-http_v3_module
    --with-cc-opt="-I../boringssl/include」
    --with-ld-opt="-L../boringssl/build -lstdc++」
```

或者，nginx可以配置為[QuicTLS](https://github.com/quictls/openssl)：

```bash
./configure
    --with-debug
    --with-http_v3_module
    --with-cc-opt="-I../quictls/build/include」
    --with-ld-opt="-L../quictls/build/lib」
```

或者，nginx可以配置為[LibreSSL](https://www.libressl.org/)：

```bash
./configure
    --with-debug
    --with-http_v3_module
    --with-cc-opt="-I../libressl/build/include」
    --with-ld-opt="-L../libressl/build/lib」
```

配置完成後，使用`make`編譯並安裝nginx。

#### 配置提示

[ngx\_http\_core\_module](https://nginx.org/en/docs/http/ngx_http_core_module.html)模塊中的[listen](https://nginx.org/en/docs/http/ngx_http_core_module.html#listen)指令獲得了一個新參數[quic](https://nginx.org/en/docs/http/ngx_http_core_module.html#quic)，該參數在指定埠上通過QUIC啟用HTTP/3。

沿著`quic`參數，也可以指定[reuseport](https://nginx.org/en/docs/http/ngx_http_core_module.html#reuseport)參數，使其與多個工作進程一起正常工作。

到[enable](https://nginx.org/en/docs/http/ngx_http_v3_module.html#quic_retry)地址驗證：

```nginx
quic_retry on;
```

至[enable](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_early_data)0-RTT：

```nginx
ssl_early_data on;
```

至[enable](https://nginx.org/en/docs/http/ngx_http_v3_module.html#quic_gso)GSO（通用分段卸載）：

```nginx
quic_gso on;
```

到各種令牌的[set](https://nginx.org/en/docs/http/ngx_http_v3_module.html#quic_host_key)host密鑰：

```nginx
quic_host_key <filename>;
```

QUIC需要TLSv1.3協議版本，該版本在[ssl\_protocols](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_protocols)指令中默認啟用。

默認情況下，[GSO Linux-specific optimization](http://vger.kernel.org/lpc_net2018_talks/willemdebruijn-lpc2018-udpgso-paper-DRAFT-1.pdf)是禁用的。如果相應的網絡接口配置為支持GSO，則啟用它。

#### Troubleshooting

可能有助於識別問題的提示：

-   確保nginx使用正確的SSL庫構建。
-   確保nginx在運行時使用正確的SSL庫（`nginx -V`顯示它當前使用的內容）。
-   確保客戶端實際上是通過QUIC發送請求。建議從一個簡單的控制台客戶端（如[ngtcp2](https://nghttp2.org/ngtcp2)）開始，以確保在嘗試使用真實的瀏覽器（可能對證書非常挑剔）之前正確配置伺服器。
-   使用[debug support](https://nginx.org/en/docs/debugging_log.html)構建nginx，並查看調試日誌。它應該包含有關連接的所有詳細信息以及失敗原因。所有相關消息都包含「`quic`」前綴，可以輕鬆過濾掉。
-   為了進行更深入的調查，可以使用以下宏啟用其他調試：`NGX_QUIC_DEBUG_PACKETS`、`NGX_QUIC_DEBUG_FRAMES`、`NGX_QUIC_DEBUG_ALLOC`、`NGX_QUIC_DEBUG_CRYPTO`。
    
    ```bash
    ./configure
        --with-http_v3_module
        --with-debug
        --with-cc-opt="-DNGX_QUIC_DEBUG_PACKETS -DNGX_QUIC_DEBUG_PACKPTO」
    ```