# 模組 ngx_http_map_module

> Source: https://nginx.org/en/docs/http/ngx_http_map_module.html  
> 翻譯：zh-TW（人工校對）

---

## 模組 ngx_http_map_module

依**其他變數的值**建立新變數。

> 變數只在**被使用時**才計算；只宣告大量 `map` **不會**增加每次請求的成本。

### 範例

```nginx
map $http_host $name {
    hostnames;

    default       0;

    example.com   1;
    *.example.com 1;
    example.org   2;
    *.example.org 2;
    .example.net  3;
    wap.*         4;
}

map $http_user_agent $mobile {
    default       0;
    "~Opera Mini" 1;
}
```

### `map`（僅 `http`）

```nginx
map $source $target { ... }
```

區塊內定義來源值 → 結果值的對應。來源可為字串（**不分大小寫**）或正則（0.9.6+）：

- `~` 區分大小寫  
- `~*` 不分大小寫（1.0.4+）  
- 正則可含命名／位置擷取，供後續 directive 使用  

若來源值剛好等於特殊參數名，前面加 `\`。結果可含文字與變數。

#### 特殊參數

| 參數 | 意義 |
|------|------|
| `default value` | 皆不符時的結果；未寫則為空字串 |
| `hostnames` | 來源可為帶前後綴遮罩的主機名；須寫在值列表**之前**。`.example.com` = `example.com` + `*.example.com` |
| `include file` | 從檔案載入對應（可多個） |
| `volatile` | 變數不快取（1.11.7） |

#### 搜尋順序（命中即停）

1. 無遮罩的字串  
2. 最長前綴遮罩（`*.example.com`）  
3. 最長後綴遮罩（`mail.*`）  
4. 第一個命中的正則（設定出現順序）  
5. `default`

### 雜湊參數

| Directive | 預設 | 說明 |
|-----------|------|------|
| `map_hash_bucket_size` | 依 cache line | map 雜湊 bucket 大小 |
| `map_hash_max_size` | 2048 | map 雜湊最大 size |

細節見 [雜湊表](019page.md)。

### 本專案

- WebSocket 模板使用 `map $http_upgrade $connection_upgrade`  
- Geo／國家封鎖常用 `map $geoip2_data_country_code $geo_block_...`  
- **`map` 只能在 `http`（或 stream 的 stream-map 模組）**，不要寫進 `server` 當一般指令誤用
