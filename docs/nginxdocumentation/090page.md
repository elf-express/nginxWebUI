# Server 名稱（server_name）

> Source: https://nginx.org/en/docs/http/server_names.html  
> 翻譯：zh-TW（人工校對）· 名稱語法保留原文

---

## Server 名稱

由 [`server_name`](https://nginx.org/en/docs/http/ngx_http_core_module.html#server_name) 定義，決定請求由哪個 `server` 區塊處理。亦可見 [請求如何處理](089page.md)。

可用：精確名稱、萬用字元、正規表示式。

```nginx
server {
    listen       80;
    server_name  example.org  www.example.org;
}

server {
    listen       80;
    server_name  *.example.org;
}

server {
    listen       80;
    server_name  mail.*;
}

server {
    listen       80;
    server_name  ~^(?<user>.+)\.example\.net$;
}
```

### 比對優先順序（命中即停）

1. 精確名稱  
2. 最長、以 `*` 開頭的萬用名稱（如 `*.example.org`）  
3. 最長、以 `*` 結尾的萬用名稱（如 `mail.*`）  
4. 第一個命中的正規表示式（依設定檔出現順序）

### 萬用名稱

- `*` 只能在名稱**開頭或結尾**，且在**點邊界**上。`www.*.example.org`、`w*.example.org` **無效**（需用正則）。
- `*.example.org` 可匹配多層，例如 `www.example.org` 與 `www.sub.example.org`。
- 特殊形式 `.example.org` = 精確 `example.org` **加上** `*.example.org`。

### 正規表示式名稱

須以 `~` 開頭，否則會被當精確名或（含 `*` 時）無效萬用名。建議加 `^` `$`，網域中的 `.` 要跳脫。含 `{` `}` 時整個名稱需加引號，否則會報 `server_name` 未以 `;` 結束。

命名擷取可當變數使用：

```nginx
server {
    server_name   ~^(www\.)?(?<domain>.+)$;
    location / {
        root   /sites/$domain;
    }
}
```

### 特殊名稱

| 寫法 | 意義 |
|------|------|
| `""` | 匹配無 `Host` 的請求；未寫 `server_name` 時預設也是空名稱（0.8.48+） |
| `$hostname` | 機器 hostname（0.9.4+） |
| IP 字面 | 客戶端用 IP 造訪時 `Host` 為 IP，可寫入 `server_name` |
| `_` | 常見 catch-all **假名**（無效網域），無特殊語意；default server 仍由 `listen ... default_server` 決定 |

### 國際化網域（IDN）

`server_name` 須寫 **Punycode** ASCII 形式，例如 `xn--e1afmkfd.xn--80akhbyknj4f`。

### 虛擬主機何時選定

連線先落在 default server 上下文，之後可能在下列階段更新選定的 server：

1. SSL handshake 的 **SNI**  
2. 處理請求列之後  
3. 處理 `Host` 標頭之後  
4. 仍無法決定時使用空名稱  

因此部分 SSL／標頭相關 directive 應寫在 **default server**（細節見原文與 [HTTPS 設定](021page.md)）。

### 效能

精確名與兩種萬用名存在三張 hash（綁定 listen 埠）。正則**依序**測試，最慢。高頻名稱建議**明確列出**精確名，而不是只寫 `.example.org`。名稱很多或很長時調：

- `server_names_hash_max_size`（先調這個）  
- `server_names_hash_bucket_size`（次之，通常調成 2 的冪）  

見 [雜湊表設定](019page.md)。若某 listen 埠只有一個 server，通常不建 hash、不比名稱（正則含擷取除外）。
