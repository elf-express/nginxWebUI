# 設定雜湊表（hashes）

> Source: https://nginx.org/en/docs/hash.html  
> 翻譯：zh-TW（人工校對）

---

## 設定雜湊表

為了快速處理靜態資料集合（例如 server name、[`map`](https://nginx.org/en/docs/http/ngx_http_map_module.html#map) 的值、MIME 類型、請求標頭名稱等），nginx 使用**雜湊表（hash tables）**。

啟動與每次重載設定時，nginx 會盡量選擇**夠小**的表大小，使同一個 bucket 內相同 hash 值的鍵總長不超過設定的 **hash bucket size**。表的大小以 bucket 數量表示，調整會持續到超過 **hash max size** 為止。

多數雜湊都有對應 directive 可調這兩個參數。以 server name 為例：

- [`server_names_hash_max_size`](https://nginx.org/en/docs/http/ngx_http_core_module.html#server_names_hash_max_size)
- [`server_names_hash_bucket_size`](https://nginx.org/en/docs/http/ngx_http_core_module.html#server_names_hash_bucket_size)

**hash bucket size** 會對齊到處理器 **cache line** 大小的整數倍，以減少記憶體存取次數、加速查表。若 bucket size 等於一條 cache line，最差情況鍵搜尋約兩次記憶體存取（算 bucket 位址一次、bucket 內搜尋一次）。

### 看到警告時怎麼調

若 error log / `nginx -t` 出現類似：

```text
could not build the server_names_hash…
you should increase either server_names_hash_max_size: …
or server_names_hash_bucket_size: …
```

或：

```text
could not build optimal variables_hash…
```

**建議先增大 `*_hash_max_size`**，仍不足再增大 `*_hash_bucket_size`。

### 本專案常見相關指令

| 場景 | 可調參數（範例） |
|------|------------------|
| 大量 `server_name` | `server_names_hash_max_size` / `server_names_hash_bucket_size` |
| 大量變數（geoip2、map 等） | `variables_hash_max_size` / `variables_hash_bucket_size` |
| 代理標頭 | `proxy_headers_hash_max_size` / `proxy_headers_hash_bucket_size` |
| MIME `types` | `types_hash_max_size` / `types_hash_bucket_size` |
