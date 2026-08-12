# 偵錯日誌

> Source: https://nginx.org/en/docs/debugging_log.html  
> 翻譯：zh-TW（人工校對）· 指令／directive／路徑／腳本保留原文

---

## 目錄

- [啟用偵錯日誌](#啟用偵錯日誌)
- [選定客戶端的偵錯日誌](#選定客戶端的偵錯日誌)
- [記錄到循環記憶體緩衝區](#記錄到循環記憶體緩衝區)

---

## 啟用偵錯日誌

要使用偵錯日誌，nginx 必須在編譯時就開啟 debug 支援：

```bash
./configure --with-debug ...
```

接著用 [`error_log`](https://nginx.org/en/docs/ngx_core_module.html#error_log) 指令設定 `debug` 等級：

```nginx
error_log /path/to/log debug;
```

要確認 nginx 是否已編入偵錯支援，執行 `nginx -V`，輸出中應包含：

```text
configure arguments: --with-debug ...
```

[Linux 預先建置套件](https://nginx.org/en/linux_packages.html)自 1.9.8 起透過 `nginx-debug` 二進位檔提供開箱即用的偵錯日誌，可直接切換：

```bash
service nginx stop
service nginx-debug start
```

之後再設定 `debug` 等級即可。Windows 版的 nginx 二進位檔一律內建偵錯日誌支援，只要設定 `debug` 等級就夠了。

注意：重新定義日誌卻沒有指定 `debug` 等級，會讓偵錯日誌失效。下例在 [`server`](https://nginx.org/en/docs/http/ngx_http_core_module.html#server) 層重新定義日誌，該 server 的偵錯日誌就被關掉了：

```nginx
error_log /path/to/log debug;

http {
    server {
        error_log /path/to/log;
        ...
```

要避免這種情況，請把重新定義日誌的那一行註解掉，或補上 `debug` 等級：

```nginx
error_log /path/to/log debug;

http {
    server {
        error_log /path/to/log debug;
        ...
```

## 選定客戶端的偵錯日誌

也可以只對[選定的客戶端位址](https://nginx.org/en/docs/ngx_core_module.html#debug_connection)啟用偵錯日誌：

```nginx
error_log /path/to/log;

events {
    debug_connection 192.168.1.1;
    debug_connection 192.168.10.0/24;
}
```

## 記錄到循環記憶體緩衝區

偵錯日誌可以寫進循環記憶體緩衝區：

```nginx
error_log memory:32m debug;
```

即使在高負載下，寫入記憶體緩衝區的 `debug` 等級日誌也不會明顯影響效能。這種情況下，可以用 `gdb` 腳本把日誌取出來：

```gdb
set $log = ngx_cycle->log

while $log->writer != ngx_log_memory_writer
    set $log = $log->next
end

set $buf = (ngx_log_memory_buf_t *) $log->wdata
dump binary memory debug_log.txt $buf->start $buf->end
```

或使用 `lldb` 腳本：

```lldb
expr ngx_log_t *$log = ngx_cycle->log
expr while ($log->writer != ngx_log_memory_writer) { $log = $log->next; }
expr ngx_log_memory_buf_t *$buf = (ngx_log_memory_buf_t *) $log->wdata
memory read --force --outfile debug_log.txt --binary $buf->start $buf->end
```
