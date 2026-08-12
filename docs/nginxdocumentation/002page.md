# 新手指南

> Source: https://nginx.org/en/docs/beginners_guide.html  
> 翻譯：zh-TW（人工校對）· 指令／directive／路徑保留原文

---

## 目錄

- [啟動、停止與重新載入設定](#啟動停止與重新載入設定)
- [設定檔結構](#設定檔結構)
- [提供靜態內容](#提供靜態內容)
- [設定簡易反向代理](#設定簡易反向代理)
- [設定 FastCGI 代理](#設定-fastcgi-代理)

---

## 新手指南

本指南簡介 nginx，並說明幾個常見基本操作。假設機器上**已經安裝** nginx；若尚未安裝，請見 [安裝 nginx](https://nginx.org/en/docs/install.html)。內容包含：如何啟動／停止 nginx、重新載入設定、設定檔結構，以及如何提供靜態內容、當反向代理、串接 FastCGI 應用。

nginx 有一個 **master** 行程與多個 **worker** 行程。master 主要負責讀取與評估設定，並維護 worker；實際處理請求的是 worker。nginx 使用事件驅動模型，並依作業系統機制在 worker 之間有效分配請求。worker 數量在設定檔中指定，可為固定值，也可依可用 CPU 核心數自動調整（見 [`worker_processes`](https://nginx.org/en/docs/ngx_core_module.html#worker_processes)）。

nginx 與各模組的行為由設定檔決定。預設檔名為 `nginx.conf`，常見路徑為 `/usr/local/nginx/conf`、`/etc/nginx` 或 `/usr/local/etc/nginx`。  
（本專案 Docker 預設使用 `/home/nginxWebUI/nginx.conf`，由 UI 產生。）

### 啟動、停止與重新載入設定

啟動 nginx：執行 nginx 可執行檔。啟動後可用 `-s` 參數控制：

```text
nginx -s signal
```

其中 `signal` 可為：

| 訊號參數 | 意義 |
|----------|------|
| `stop` | 立即關閉（fast shutdown） |
| `quit` | 優雅關閉（graceful shutdown） |
| `reload` | 重新載入設定檔 |
| `reopen` | 重新開啟日誌檔 |

例如：等 worker 處理完目前請求再關閉：

```bash
nginx -s quit
```

> 此命令應以**啟動 nginx 的同一使用者**執行。

修改設定檔後，必須送出重新載入或重啟才會生效：

```bash
nginx -s reload
```

master 收到 reload 後會先檢查新設定語法，再嘗試套用。成功則啟動新 worker，並請舊 worker 優雅退出；失敗則回滾，繼續使用舊設定。舊 worker 停止接受新連線，服務完現有請求後結束。

也可用 Unix 工具（如 `kill`）直接對行程送訊號。master 的 PID 預設寫在 `/usr/local/nginx/logs/nginx.pid` 或 `/var/run/nginx.pid`。例如 master PID 為 1628、要優雅關閉：

```bash
kill -s QUIT 1628
```

列出行程：

```bash
ps -ax | grep nginx
```

更多訊號說明見 [控制 nginx](005page.md)。

### 設定檔結構

nginx 由模組組成，行為由設定檔中的 **directive（指令）** 控制。指令分兩類：

- **簡單指令**：名稱與參數以空白分隔，以分號 `;` 結尾。
- **區塊指令**：結構類似，但以大括號 `{ ... }` 包住一組子指令。若區塊內還可放其他指令，稱為 **context（上下文）**，例如 `events`、`http`、`server`、`location`。

不在任何區塊內的指令屬於 **main** 上下文。`events` 與 `http` 在 main；`server` 在 `http`；`location` 在 `server`。

`#` 之後到行尾為註解。

### 提供靜態內容

常見任務是提供檔案（圖片、靜態 HTML）。以下範例依請求從不同本機目錄提供：`/data/www`（HTML）與 `/data/images`（圖片）。需在 `http` 內設定 `server`，並含兩個 `location`。

先建立目錄與內容：

```bash
mkdir -p /data/www /data/images
# 在 /data/www 放 index.html，在 /data/images 放圖片
```

設定骨架：

```nginx
http {
    server {
    }
}
```

一個設定檔可有多個 `server`，通常以監聽埠與 [server name](090page.md) 區分。nginx 選定 `server` 後，會用請求 URI 與該 `server` 內的 `location` 參數比對。

加入：

```nginx
location / {
    root /data/www;
}
```

此前綴 `/` 會與請求 URI 比對；符合時，URI 會接在 `root`（此處 `/data/www`）後面形成本機路徑。多個 `location` 符合時，取**最長前綴**。`location /` 前綴最短，僅在其他 location 都不符合時使用。

再加入：

```nginx
location /images/ {
    root /data;
}
```

以 `/images/` 開頭的請求會符合此區塊（`location /` 也符合，但前綴較短）。

完整 `server`：

```nginx
server {
    location / {
        root /data/www;
    }

    location /images/ {
        root /data;
    }
}
```

此 server 預設聽 80，本機可透過 `http://localhost/` 存取。URI 以 `/images/` 開頭時對應 `/data/images/...`；其餘對應 `/data/www/...`。檔案不存在則回 404。

套用設定：

```bash
nginx -s reload
```

異常時可查 `access.log` / `error.log`（常見於 `/usr/local/nginx/logs` 或 `/var/log/nginx`）。

### 設定簡易反向代理

反向代理：接收客戶端請求 → 轉給上游 → 取回應 → 回客戶端。

先定義上游（同實例另一個 `server`）：

```nginx
server {
    listen 8080;
    root /data/up1;

    location / {
    }
}
```

此 server 聽 8080，請求對應到 `/data/up1`。請建立目錄並放入 `index.html`。`root` 寫在 `server` 層時，若所選 `location` 沒有自己的 `root`，就使用此值。

代理 server（圖片走本機，其餘轉上游）：

```nginx
server {
    location / {
        proxy_pass http://localhost:8080;
    }

    location /images/ {
        root /data;
    }
}
```

可把第二個 `location` 改成依副檔名匹配圖片：

```nginx
location ~ \.(gif|jpg|png)$ {
    root /data/images;
}
```

`~` 表示正規表示式。選 location 時 nginx 先比對前綴（記住最長前綴），再比對正規表示式；正則命中則用該 location，否則用先前記住的前綴 location。

代理 server 範例：

```nginx
server {
    location / {
        proxy_pass http://localhost:8080/;
    }

    location ~ \.(gif|jpg|png)$ {
        root /data/images;
    }
}
```

套用：`nginx -s reload`。更多代理指令見 [ngx_http_proxy_module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)。

### 設定 FastCGI 代理

nginx 可把請求轉給 FastCGI 伺服器（例如 PHP）。基本做法是用 `fastcgi_pass` 取代 `proxy_pass`，並用 `fastcgi_param` 設定傳給 FastCGI 的參數。假設 FastCGI 在 `localhost:9000`：

```nginx
server {
    location / {
        fastcgi_pass  localhost:9000;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        fastcgi_param QUERY_STRING    $query_string;
    }

    location ~ \.(gif|jpg|png)$ {
        root /data/images;
    }
}
```

除靜態圖片外，其餘請求會以 FastCGI 協定轉到 `localhost:9000`。
