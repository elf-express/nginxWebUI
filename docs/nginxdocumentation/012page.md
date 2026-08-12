# page

> Source: https://nginx.org/en/docs/faq/accept_failed.html

---

## 目錄

- [What does the following error mean in the log file: “accept() failed (53: Software caused connection abort) while accepting new connection on 0.0.0.0:80”?](#what-does-the-following-error-mean-in-the-log-file-accept-failed-53-software-caused-connection-abort-while-accepting-new-connection-on-000080)

---

## 在0.0.0.0：80上接受新連接時accept（）failed（53：Software caused connection abort）是什麼意思？

**Q：** 日誌文件中出現以下錯誤是什麼意思：「在0.0.0.0：80上接受新連接時accept（）失敗（53：軟體導致連接中止）」？

**A：** 此類錯誤源於客戶端在nginx能夠處理它們之前設法關閉的連接。例如，當用戶沒有等待大量圖像填充的頁面完全加載時，可能會發生這種情況，並點擊了不同的連結。在這種情況下，用戶的瀏覽器將關閉所有不再需要的先前連接。這是一個非嚴重錯誤。