# 控制 nginx

> Source: https://nginx.org/en/docs/control.html  
> 翻譯：zh-TW（人工校對）· 指令／訊號名稱保留原文

---

## 目錄

- [變更設定](#變更設定)
- [輪替日誌](#輪替日誌)
- [線上升級可執行檔](#線上升級可執行檔)

---

## 控制 nginx

nginx 以**訊號**控制。master 的行程 ID 預設寫入 `/usr/local/nginx/logs/nginx.pid`（編譯時可改；或在 `nginx.conf` 用 [`pid`](https://nginx.org/en/docs/ngx_core_module.html#pid) 指定）。

### Master 支援的訊號

| 訊號 | 行為 |
|------|------|
| `TERM`, `INT` | 立即關閉（fast shutdown） |
| `QUIT` | 優雅關閉（graceful shutdown） |
| `HUP` | 重新讀取設定；在 FreeBSD/Linux 也會跟上時區變更；以新設定啟動新 worker，並優雅關閉舊 worker |
| `USR1` | 重新開啟日誌檔 |
| `USR2` | 升級可執行檔（binary upgrade） |
| `WINCH` | 優雅關閉 worker（master 可繼續） |

### Worker 也可收訊號（通常不必手動）

| 訊號 | 行為 |
|------|------|
| `TERM`, `INT` | 立即關閉 |
| `QUIT` | 優雅關閉 |
| `USR1` | 重新開啟日誌檔 |
| `WINCH` | 異常終止（除錯用，需啟用 [`debug_points`](https://nginx.org/en/docs/ngx_core_module.html#debug_points)） |

常用 CLI 等價：

```bash
nginx -s stop      # ≈ TERM 立即關閉
nginx -s quit      # ≈ QUIT 優雅關閉
nginx -s reload    # ≈ HUP  重載設定
nginx -s reopen    # ≈ USR1 重開日誌
```

---

### 變更設定

要讓 nginx 重讀設定檔，對 master 送 **`HUP`**（或 `nginx -s reload`）。

master 會：

1. 檢查新設定語法  
2. 嘗試套用（開日誌、聽新 socket 等）  
3. **失敗** → 回滾，繼續舊設定  
4. **成功** → 啟動新 worker，請舊 worker 優雅退出  

舊 worker 會關閉 listen socket，但仍服務既有客戶端；全部完成後結束。

**範例（FreeBSD 風格 ps）：**

```bash
ps axw -o pid,ppid,user,%cpu,vsz,wchan,command | egrep '(nginx|PID)'
```

可能輸出：

```text
  PID  PPID USER    %CPU   VSZ WCHAN  COMMAND
33126     1 root     0.0  1148 pause  nginx: master process /usr/local/nginx/sbin/nginx
33127 33126 nobody   0.0  1380 kqread nginx: worker process (nginx)
33128 33126 nobody   0.0  1364 kqread nginx: worker process (nginx)
33129 33126 nobody   0.0  1364 kqread nginx: worker process (nginx)
```

對 master 送 `HUP` 後，舊 worker 會進入 shutting down，新 worker 已啟動。過一段時間只剩新 worker。

---

### 輪替日誌

1. 先**重新命名**（mv）目前日誌檔  
2. 對 master 送 **`USR1`**（或 `nginx -s reopen`）

master 會重新開啟所有目前使用中的日誌，並以 worker 執行身分的使用者作為新檔擁有者。成功後關閉舊 fd，並通知 worker 也重開。舊檔可立刻壓縮或後處理。

---

### 線上升級可執行檔

1. 用**新 binary 覆蓋**舊執行檔路徑  
2. 對 master 送 **`USR2`**

master 會把 `nginx.pid` 改名為帶 `.oldbin` 後綴（例如 `nginx.pid.oldbin`），再啟動新 binary（含新 master 與新 worker）。此時**舊、新 worker 都還在接受請求**。

對**舊** master 送 **`WINCH`**，可請舊 worker 優雅退出，請求逐漸只由新 worker 處理。舊 master **不會**關閉其 listen socket，必要時仍可再拉起舊 worker。

若新版本有問題，可：

- 對**舊** master 送 **`HUP`**：舊 master 在**不重讀設定**的情況下再起 worker；再對**新** master 送 **`QUIT`** 優雅關掉新程序。  
- 或對**新** master 送 **`TERM`**：請新 worker 立刻退出；若卡住可再 **`KILL`**。新 master 退出後，舊 master 會自動再起 worker。

升級成功後，對**舊** master 送 **`QUIT`**，只留下新程序。新 master 退出時會拿掉 pid 檔的 `.oldbin` 後綴。
