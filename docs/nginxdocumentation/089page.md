# nginx 如何處理一個請求

> Source: https://nginx.org/en/docs/http/request_processing.html  
> 翻譯：zh-TW（人工校對）· conf 保留原文

---

## nginx 如何處理一個請求

### 依名稱的虛擬主機（name-based）

nginx 先決定由哪個 **server** 處理請求。三個 server 都聽 `*:80` 時：

```nginx
server {
    listen      80;
    server_name example.org www.example.org;
    ...
}

server {
    listen      80;
    server_name example.net www.example.net;
    ...
}

server {
    listen      80;
    server_name example.com www.example.com;
    ...
}
```

此時主要依請求標頭 **`Host`** 選擇 server。若 `Host` 不符任何 `server_name`，或請求沒有 `Host`，則交給該埠的 **default server**。上例中 default 是**第一個** server（nginx 預設行為）。也可在 [`listen`](https://nginx.org/en/docs/http/ngx_http_core_module.html#listen) 明確指定：

```nginx
server {
    listen      80 default_server;
    server_name example.net www.example.net;
    ...
}
```

> `default_server` 自 0.8.21 起可用；更早版本用 `default`。  
> default server 是 **listen 埠** 的屬性，不是 server name 的屬性。

### 拒絕沒有 Host 的請求

```nginx
server {
    listen      80;
    server_name "";
    return      444;
}
```

空字串 `server_name` 可匹配沒有 `Host` 的請求；`444` 是 nginx 非標準狀態，會直接關閉連線。  
自 0.8.48 起，預設 server name 即為空字串，可省略 `server_name ""`。

### 名稱 + IP 混合

```nginx
server {
    listen      192.168.1.1:80;
    server_name example.org www.example.org;
    ...
}

server {
    listen      192.168.1.1:80;
    server_name example.net www.example.net;
    ...
}

server {
    listen      192.168.1.2:80;
    server_name example.com www.example.com;
    ...
}
```

nginx 先比對請求的 **IP:port** 與各 server 的 `listen`，再在命中的集合裡用 `Host` 比對 `server_name`。找不到名稱則用該 IP:port 的 default server。  
例如打到 `192.168.1.1:80` 但 Host 是 `www.example.com`（此埠未定義）→ 由 192.168.1.1:80 的 default server 處理。

不同埠可有不同 default：

```nginx
listen 192.168.1.1:80 default_server;
listen 192.168.1.2:80 default_server;
```

### 簡單 PHP 站的 location 選擇

```nginx
server {
    listen      80;
    server_name example.org www.example.org;
    root        /data/www;

    location / {
        index   index.html index.php;
    }

    location ~* \.(gif|jpg|png)$ {
        expires 30d;
    }

    location ~ \.php$ {
        fastcgi_pass  localhost:9000;
        fastcgi_param SCRIPT_FILENAME
                      $document_root$fastcgi_script_name;
        include       fastcgi_params;
    }
}
```

選擇規則：

1. 先找**最長字面前綴** location（與列出順序無關）。上例前綴只有 `/`，當最後手段。  
2. 再依設定檔**列出順序**檢查**正規表示式** location；**第一個**命中即停止。  
3. 若沒有正則命中，使用先前記住的最長前綴 location。

location 只比對請求列的 **URI 部分（不含 query string 參數）**。

處理範例：

| 請求 | 結果 |
|------|------|
| `/logo.gif` | 前綴 `/` → 正則 `\.(gif\|jpg\|png)$` → 送 `/data/www/logo.gif` |
| `/index.php` | 前綴 `/` → 正則 `\.php$` → FastCGI `SCRIPT_FILENAME=/data/www/index.php` |
| `/about.html` | 僅前綴 `/` → 送 `/data/www/about.html` |
| `/` | 前綴 `/`；`index` 若發現 `index.php` 存在會**內部重導**到 `/index.php`，再重新跑 location 選擇 → 最終 FastCGI |

---

原文作者 Igor Sysoev；Brian Mercer 編輯。
