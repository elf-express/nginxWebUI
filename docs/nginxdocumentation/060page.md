# page

> Source: https://nginx.org/en/docs/http/ngx_http_mp4_module.html

---

## 目錄

- [Module ngx\_http\_mp4\_module](#module-ngxhttpmp4module)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_mp4\_module

`ngx_http_mp4_module`模塊為MP4文件提供了偽流伺服器端支持。這類文件通常具有`.mp4`、`.m4v`或`.m4a`文件擴展名。

偽流與兼容的媒體播放器協同工作。播放器向伺服器發送HTTP請求，並在查詢字符串參數（僅命名為`start`，以秒為單位）中指定開始時間，伺服器以流響應，使其開始位置與請求的時間相對應，例如：

> http://example.com/elephants\_dream.mp4?start=238.88

這允許在任何時間執行隨機搜索，或在時間軸的中間開始回放。

為了支持搜索，基於H.264的格式將元數據存儲在所謂的「moov原子」中。它是文件的一部分，保存整個文件的索引信息。

要開始播放，播放器首先需要讀取元數據。這是通過發送帶有`start=0`參數的特殊請求來完成的。許多編碼軟體會將元數據插入到文件的末尾。這對於偽流來說不是最佳的，因為播放器必須在開始播放之前下載整個文件。如果元數據位於文件的開頭，nginx只需開始發回文件內容就足夠了，如果元數據位於文件的末尾，nginx必須讀取整個文件並準備一個新的流，以便元數據出現在媒體數據之前，這涉及到一些CPU、內存和磁碟I/O開銷，因此最好提前[prepare an original file for pseudo-streaming](https://github.com/flowplayer/flowplayer/wiki/7.1.1-video-file-correction)，而不是讓nginx對每個這樣的請求都這麼做。

該模塊還支持HTTP請求（1.5.13）的`end`參數，該參數設置播放的結束點。`end`參數可以與`start`參數一起指定，也可以單獨指定：

> http://example.com/elephants\_dream.mp4?start=238.88&end=555.55

對於帶有非零`start`或`end`參數的匹配請求，nginx將從文件中讀取元數據，準備帶有所請求時間範圍的流，並將其發送到客戶端。這與上述開銷相同。

如果`start`參數指向非關鍵視頻幀，則此類視頻的開頭將被打斷。若要解決此問題，請在視頻[can](https://nginx.org/en/docs/http/ngx_http_mp4_module.html#mp4_start_key_frame)的`start`點之前加上關鍵幀，並在關鍵幀之間加上所有中間幀。這些幀將使用編輯列表（1.21.4）隱藏，無法播放。

如果匹配的請求不包含`start`和`end`參數，則不會產生任何開銷，文件將僅作為靜態資源發送。某些播放器還支持字節範圍請求，因此不需要此模塊。

默認情況下未構建此模塊，應使用`--with-http_mp4_module`配置參數啟用此模塊。

> >如果之前使用了第三方mp4模塊，則應將其禁用。

[ngx\_http\_flv\_module](https://nginx.org/en/docs/http/ngx_http_flv_module.html)模塊為FLV文件提供了類似的偽流支持。

#### 配置示例

> location /video/ {
>     mp4;
>     mp4\_buffer\_size       1m;
>     mp4\_max\_buffer\_size   5m;
>     mp4\_limit\_rate        on;
>     mp4\_limit\_rate\_after  30s;
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mp4</strong>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>location</code><br></td></tr></tbody></table>

打開周圍位置中的模塊處理。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mp4_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mp4_buffer_size 512K;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置用於處理MP4文件的緩衝區的初始`*size*`。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mp4_max_buffer_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mp4_max_buffer_size 10M;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

在元數據處理過程中，可能需要更大的緩衝區。其大小不能超過指定的`*size*`，否則nginx將返回500（內部伺服器錯誤）伺服器錯誤，並記錄以下消息：

> >「/some/movie/file.mp4」mp4 moov原子太大：
> 12583268，您可能需要增加mp4\_max\_buffer\_size

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mp4_limit_rate</strong> <code>on</code> | <code>off</code> | <code><i>factor</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mp4_limit_rate off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

限制對客戶端傳輸回應的速率。速率是根據所提供MP4檔案的平均比特率來限制。若要計算速率，比特率會乘以指定的`*factor*`。特殊值「`on`」映射到係數1.1。特殊值「`off`」會停用速率限制。限制是針對每個要求來設定。因此，如果客戶端同時打開兩個連接，則總速率將是指定限制的兩倍。

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mp4_limit_rate_after</strong> <code><i>time</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mp4_limit_rate_after 60s;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

設置媒體數據的初始量（以播放時間衡量），在此之後，向客戶端進一步傳輸響應將受到速率限制。

> >此指令可作為[commercial subscription](https://www.f5.com/products/nginx)的一部分提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>mp4_start_key_frame</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>mp4_start_key_frame off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

此指令出現在1.21.4版中。

強制輸出視頻始終從關鍵視頻幀開始。如果`start`參數未指向關鍵幀，則使用mp4編輯列表隱藏初始幀。編輯列表受主要播放器和瀏覽器（如Chrome、Safari、QuickTime和ffmpeg）支持，Firefox部分支持。