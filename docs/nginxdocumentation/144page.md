# 命令列參數

> Source: https://nginx.org/en/docs/switches.html  
> 翻譯：zh-TW（人工校對）· 參數與訊號字面值保留原文

---

## 命令列參數

nginx 支援以下命令列參數：

| 參數 | 說明 |
|------|------|
| `-?` / `-h` | 顯示命令列參數說明 |
| `-c file` | 使用指定設定檔，而非預設路徑 |
| `-e file` | 使用指定 error log 路徑（1.19.5）。特殊值 `stderr` 表示標準錯誤 |
| `-g directives` | 設定全域 directive，例如：`nginx -g "pid /var/run/nginx.pid; worker_processes \`sysctl -n hw.ncpu\`;"` |
| `-l port` | 在指定 port 或 UNIX socket 啟用 control REST API（1.29.8，**商業版**功能） |
| `-p prefix` | 設定 path prefix（伺服器檔案根目錄；預設常為 `/usr/local/nginx`） |
| `-q` | 設定測試時抑制非錯誤訊息 |
| `-s signal` | 向 master 送訊號（見下表） |
| `-t` | **測試**設定：檢查語法，並嘗試開啟設定中引用的檔案 |
| `-T` | 同 `-t`，並把設定內容傾印到標準輸出（1.9.2） |
| `-v` | 顯示 nginx 版本 |
| `-V` | 顯示版本、編譯器版本與 configure 參數 |

### `-s signal` 可取值

| signal | 意義 |
|--------|------|
| `stop` | 立即關閉 |
| `quit` | 優雅關閉 |
| `reload` | 重載設定：以新設定啟動新 worker，並優雅關閉舊 worker |
| `reopen` | 重新開啟日誌檔 |

### 本專案常用

```bash
# 驗證（UI「验证文件」等價概念）
nginx -t -c /home/nginxWebUI/temp/nginx.conf -p /home/nginxWebUI/temp/

# 正式路徑測試
nginx -t -c /home/nginxWebUI/nginx.conf

# 重載
nginx -s reload
```

> **注意：** 裸跑 `nginx -t`（未指定 `-c`）會測系統預設 `/etc/nginx/nginx.conf`，與 nginxWebUI 產生的 conf **不是同一份**。
