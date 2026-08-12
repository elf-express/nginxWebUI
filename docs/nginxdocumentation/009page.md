# 連線處理方法

> Source: https://nginx.org/en/docs/events.html  
> 翻譯：zh-TW（人工校對）

---

## 連線處理方法

nginx 支援多種連線處理（I/O 多路複用）方法；可用方法依平台而異。若平台支援多種，nginx 通常會**自動選最有效率**的一種。必要時可用 [`use`](https://nginx.org/en/docs/ngx_core_module.html#use) 明確指定。

| 方法 | 說明 |
|------|------|
| `select` | 標準方法。在沒有更高效方法的平台會自動編入。可用 `--with-select_module` / `--without-select_module` 強制開關 |
| `poll` | 標準方法。同上，可用 `--with-poll_module` / `--without-poll_module` |
| `kqueue` | 高效；FreeBSD 4.1+、OpenBSD 2.9+、NetBSD 2.0、macOS |
| `epoll` | 高效；**Linux 2.6+**（本專案 Alpine/Docker 通常走此路徑）。1.11.3 起支援 `EPOLLRDHUP`、`EPOLLEXCLUSIVE` |
| `/dev/poll` | 高效；Solaris 7 11/99+、HP/UX 11.22+（eventport）、IRIX 6.5.15+、Tru64 UNIX 5.1A+ |
| `eventport` | Solaris 10+ 的 event ports（已知問題較多，**建議改用 `/dev/poll`**） |

### 本專案備註

- Linux 容器一般**不必**手寫 `use epoll;`，預設已是高效路徑。
- `worker_connections` 等在 `events { }` 內設定，見 core 模組。
