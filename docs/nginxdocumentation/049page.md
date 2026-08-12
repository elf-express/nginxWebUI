# page

> Source: https://nginx.org/en/docs/http/ngx_http_image_filter_module.html

---

## 目錄

- [Module ngx\_http\_image\_filter\_module](#module-ngxhttpimagefiltermodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_image\_filter\_module

`ngx_http_image_filter_module`模塊（0.7.54+）是一個過濾器，可以轉換JPEG、GIF、PNG和WebP格式的圖像。

默認情況下不構建此模塊，應使用`--with-http_image_filter_module`配置參數啟用。

> >本模塊使用[libgd](http://libgd.org/)庫，建議使用最新版本的庫。

> 1.11.6版本中出現了WebP格式支持。要轉換此格式的圖像，必須使用WebP支持編譯`libgd`庫。

#### 配置示例

> location /img/ {
>     proxy\_pass   http://backend;
>     image\_filter resize 150 100;
>     image\_filter rotate 90;
>     error\_page   415 = /empty;
> }
> 
> location = /empty {
>     empty\_gif;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter</strong> <code>off</code>;</code><br><code><strong>image_filter</strong> <code>test</code>;</code><br><code><strong>image_filter</strong> <code>size</code>;</code><br><code><strong>image_filter</strong> <code>rotate</code> <code>90</code> | <code>180</code> | <code>270</code>;</code><br><code><strong>image_filter</strong> <code>resize</code> <code><i>width</i></code> <code><i>height</i></code>;</code><br><code><strong>image_filter</strong> <code>crop</code> <code><i>width</i></code> <code><i>height</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter off;</pre></td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

設置要對圖像執行的變換類型：

`off`

關閉周圍位置的模塊處理。

`test`

確保響應是JPEG、GIF、PNG或WebP格式的圖像。否則，將返回415（不支持的媒體類型）錯誤。

`size`

以JSON格式輸出有關圖像的信息，例如：

> { "img" : { "width": 100, "height": 100, "type": "gif" } }

如果出現錯誤，輸出如下：

> {}

`rotate` `90`|`180`|`270`

按指定度數逆時針旋轉圖像。參數值可以包含變量。此模式可以單獨使用，也可以沿著與`resize`和`crop`變換一起使用。

`resize` `*width*` `*height*`

按比例縮小圖像到指定的大小。如果只縮小一個尺寸，可以指定另一個尺寸為「`-`"。如果出現錯誤，伺服器將返回代碼415（不支持的媒體類型）。參數值可以包含變量。當沿著使用`rotate`參數時，旋轉在 ** 縮小後 ** 發生。

`crop` `*width*` `*height*`

按比例將圖像縮小到較大的邊，並在另一邊裁剪多餘的邊。要僅縮小一個尺寸，可以將另一個尺寸指定為「`-`"。如果出現錯誤，伺服器將返回代碼415（不支持的媒體類型）。參數值可以包含變量。當沿著`rotate`參數一起使用時，旋轉在 ** 縮小之前發生。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter_buffer</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter_buffer 1M;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於閱讀圖像的緩衝區的最大大小。當超過該大小時，伺服器返回錯誤415（不支持的媒體類型）。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter_interlace</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter_interlace off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.3.15版本中。

如果啟用，最終圖像將隔行掃描。對於JPEG，最終圖像將採用「漸進JPEG」格式。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter_jpeg_quality</strong> <code><i>quality</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter_jpeg_quality 75;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置所需的JPEG轉換圖像的`*quality*`。可接受的值範圍為1到100。較小的值通常意味著較低的圖像質量和較少的數據傳輸。最大推薦值為95。參數值可以包含變量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter_sharpen</strong> <code><i>percent</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter_sharpen 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

增加最終圖像的清晰度。清晰度百分比可以超過100。零值禁用銳化。參數值可以包含變量。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter_transparency</strong> <code>on</code>|<code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter_transparency on;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

定義在轉換GIF圖像或PNG圖像時是否應保留透明度。透明度的丟失會導致圖像質量更好。PNG中的Alpha通道透明度始終保留。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>image_filter_webp_quality</strong> <code><i>quality</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>image_filter_webp_quality 80;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.11.6版中。

設置所需的轉換WebP圖像的`*quality*`。可接受的值範圍為1到100。較小的值通常意味著較低的圖像質量和較少的數據傳輸。參數值可以包含變量。