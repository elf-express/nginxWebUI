# page

> Source: https://nginx.org/en/docs/stream/ngx_stream_map_module.html

---

## 目錄

- [Module ngx\_stream\_map\_module](#module-ngxstreammapmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_stream\_map\_module

`ngx_stream_map_module`模塊（1.11.2）創建的變量的值依賴於其他變量的值。

#### 配置示例

```nginx
map $remote_addr $limit {
    127.0.0.1    "";
    default      $binary_remote_addr;
}

limit_conn_zone $limit zone=addr:10m;
limit_conn addr 1;
```

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>map</strong> <code><i>string</i></code> <code><i>$variable</i></code> { ... }</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

創建一個新變量，其值取決於第一個參數中指定的一個或多個源變量的值。

> 由於變量僅在使用時才被計算，因此即使聲明大量「`map`」變量也不會給連接處理增加任何額外的成本。

`map`塊內的參數指定源值和結果值之間的映射。

源值被指定為字符串或正則表達式。

字符串匹配時忽略大小寫。

正則表達式應該從「`~`」符號開始進行區分大小寫的匹配，或者從「`~*`」符號開始進行不區分大小寫的匹配。正則表達式可以包含命名和位置捕獲，這些捕獲稍後可以與結果變量沿著用於其他指令。

如果源值與下面描述的特殊參數的名稱之一匹配，則應使用「`\`」符號作為前綴。

結果值可以包含文本、變量及其組合。

還支持以下特殊參數：

`default` `*value*`

如果源值不匹配任何指定變量，則設置結果值。如果未指定`default`，則默認結果值將為空字符串。

`hostnames`

指示源值可以是帶有前綴或後綴掩碼的主機名：

```nginx
*.example.com 1;
example.*     1;
```

以下兩個記錄

```nginx
example.com   1;
*.example.com 1;
```

可以結合：

```nginx
.example.com  1;
```

此參數應在值列表之前指定。

`include` `*file*`

包含一個帶值的文件。可以有多個包含項。

`volatile`

表示變量不可緩存（1.11.7）。

搜索按以下優先級順序執行，並在第一個匹配變體上終止：

1.  1.不帶掩碼的字符串值
2.  2.帶前綴掩碼的最長字符串值，例如「`*.example.com`」
3.  3.帶後綴掩碼的最長字符串值，例如「`mail.*`」
4.  4.第一個匹配的正則表達式（按在配置文件中出現的順序）
5.  5.默認值

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>map_hash_bucket_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>map_hash_bucket_size 32|64|128;</pre></td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

設置[map](https://nginx.org/en/docs/stream/ngx_stream_map_module.html#map)變量哈希表的存儲桶大小。默認值取決於處理器的緩存行大小。設置哈希表的詳細信息在單獨的[document](https://nginx.org/en/docs/hash.html)中提供。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>map_hash_max_size</strong> <code><i>size</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>map_hash_max_size 2048;</pre></td></tr><tr><th>Context:</th><td><code>stream</code><br></td></tr></tbody></table>

設置[map](https://nginx.org/en/docs/stream/ngx_stream_map_module.html#map)變量哈希表的最大值`*size*`。設置哈希表的詳細信息在單獨的[document](https://nginx.org/en/docs/hash.html)中提供。