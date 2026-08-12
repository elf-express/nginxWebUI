# page

> Source: https://nginx.org/en/docs/faq/daemon_master_process_off.html

---

## 目錄

- [Can I run nginx with “daemon off” or “master\_process off” settings in a production environment?](#can-i-run-nginx-with-daemon-off-or-masterprocess-off-settings-in-a-production-environment)

---

## 在生產環境下，設置daemon off或master\_processoff可以運行nginx嗎？

**Q：** 在生產環境下，設置為daemon off或master\_processoff可以運行nginx嗎？

**A：** 首先，「daemon on| off」和「master\_processon| off」指令主要用於nginx代碼開發。

雖然很多人在生產環境中使用「daemon off」，但它並不是真正的意思。但是，從1.0.9版本開始，在生產環境中運行nginx時使用「daemon off」是非常安全的。請記住，「daemon off」並不是一個不間斷升級的選項。

在開發環境中，使用「master\_processoff」，nginx可以在沒有主進程的情況下在前台運行，並且可以簡單地使用^C（SIGINT）終止。這有點類似於使用「X」命令行選項運行Apache。但是，您永遠不應該使用「master\_processoff」在生產環境中運行nginx。