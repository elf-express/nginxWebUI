# page

> Source: https://nginx.org/en/docs/http/ngx_http_hls_module.html

---

## 目錄

- [Module ngx\_http\_hls\_module](#module-ngxhttphlsmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_hls\_module

`ngx_http_hls_module`模塊為MP4和MOV媒體文件提供HTTP實時流（HLS）伺服器端支持。此類文件通常具有`.mp4`、`.m4v`、`.m4a`、`.mov`或`.qt`文件擴展名。該模塊支持H.264視頻編解碼器、AAC和MP3音頻編解碼器。

對於每個媒體文件，支持兩個URI：

-   文件擴展名為「`.m3u8`」的播放列表URI。URI可以接受可選參數：
    -   「`start`」和「`end`」以秒為單位定義播放列表邊界（1.9.0）。
    -   「`offset`」將初始播放位置移動到以秒為單位的時間偏移（1.9.0）。正值設置從播放列表開始的時間偏移。負值設置從播放列表中最後一個片段結束的時間偏移。
    -   「`len`」以秒為單位定義碎片長度。
-   文件擴展名為「`.ts`」的片段URI。URI可以接受可選參數：
    -   「`start`」和「`end`」以秒為單位定義片段邊界。

> >此模塊是我們[commercial subscription](https://www.f5.com/products/nginx)的一部分。

#### 配置示例

```nginx
location / {
    hls;
    hls_fragment            5s;
    hls_buffers             10 10m;
    hls_mp4_buffer_size     1m;
    hls_mp4_max_buffer_size 5m;
    root /var/video/;
}
```

通過此配置，「`/var/video/test.mp4`」文件支持以下URI：

```nginx
http://hls.example.com/test.mp4.m3u8?offset=1.000&start=1.000&end=2.200
http://hls.example.com/test.mp4.m3u8?len=8.000
http://hls.example.com/test.mp4.ts?start=1.000&end=2.200
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hls</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

打開周圍位置的HLS流。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hls_buffers</strong> <code><i>number</i></code> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>hls_buffers 8 2m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於閱讀和寫入數據幀的最大緩衝區`*number*`和`*size*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hls_forward_args</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>hls_forward_args off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.5.12版本中。

將播放列表請求中的參數添加到片段的URI中。這對於在請求片段時執行客戶端授權或使用[ngx\_http\_secure\_link\_module](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html)模塊保護HLS流時可能很有用。

例如，如果客戶端請求播放列表`http://example.com/hls/test.mp4.m3u8?a=1&b=2`，則參數`a=1`和`b=2`將被添加到參數`start`和`end`之後的片段的URI中：

> #EXTM3U
> #EXT-X-VERSION：3
> #EXT-X-目標持續時間：15
> #EXT-X-PLAYLIST-TYPE：視頻點播
> 
> #EXTINF:9.333,
> test.mp4.ts？start=0.000&end=9.333&a=1&b=2
> #EXTINF:7.167,
> >測試.mp4.ts？開始= 9.333 &結束=16.500&a=1&b=2
> #EXTINF:5.416,
> >測試.mp4.ts？開始= 16.500 &結束=21.916&a=1&b=2
> #EXTINF:5.500,
> >測試.mp4.ts？開始= 21.916 &結束=27.416&a=1&b=2
> #EXTINF:15.167,
> >測試.mp4.ts？開始= 27.416 &結束=42.583&a=1&b=2
> #EXTINF:9.626,
> >測試.mp4.ts？開始= 42.583 &結束=52.209&a=1&b=2
> 
> #擴展X-終結者

如果HLS流受[ngx\_http\_secure\_link\_module](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html)模塊保護，則不應在[secure\_link\_md5](https://nginx.org/en/docs/http/ngx_http_secure_link_module.html#secure_link_md5)表達式中使用`$uri`，因為這會在請求片段時導致錯誤。應使用[Base URI](https://nginx.org/en/docs/http/ngx_http_map_module.html#map)而不是`$uri`（示例中為`$hls_uri`）：

```nginx
http {
    ...

    map $uri $hls_uri {
        ~^(?<base_uri>.*).m3u8$ $base_uri;
        ~^(?<base_uri>.*).ts$   $base_uri;
        default                 $uri;
    }

    server {
        ...

        location /hls/ {
            hls;
            hls_forward_args on;

            alias /var/videos/;

            secure_link $arg_md5,$arg_expires;
            secure_link_md5 "$secure_link_expires$hls_uri$remote_addr secret";

            if ($secure_link = "") {
                return 403;
            }

            if ($secure_link = "0") {
                return 410;
            }
        }
    }
}
```

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hls_fragment</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>hls_fragment 5s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義在不使用「`len`」參數的情況下請求的播放列表URI的默認片段長度。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hls_mp4_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>hls_mp4_buffer_size 512k;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於處理MP4和MOV文件的緩衝區的初始`*size*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>hls_mp4_max_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>hls_mp4_max_buffer_size 10m;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

在元數據處理過程中，可能需要更大的緩衝區。緩衝區的大小不能超過指定的`*size*`，否則nginx將返回伺服器錯誤500（內部伺服器錯誤），並記錄以下消息：

> >「/some/movie/file.mp4」mp4 moov原子太大：
> 12583268，您可能需要增加hls\_mp4\_max\_buffer\_size