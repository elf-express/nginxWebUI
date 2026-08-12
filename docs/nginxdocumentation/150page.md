# page

> Source: https://nginx.org/en/docs/windows.html

---

## 目錄

- [nginx for Windows](#nginx-for-windows)
    - [Known issues](#known-issues)
    - [Possible future enhancements](#possible-future-enhancements)

---

## nginx for Windows

Windows版nginx使用原生Win32 API（不是Cygwin仿真層）。只有`select()`和`poll()`（1.15.9）連接處理方法目前使用，因此不應期望高性能和可擴展性。由於這個和其他一些已知的問題，用於Windows的nginx版本被認為是 *beta* 版本。此時，它提供了與UNIX版本的nginx幾乎相同的功能，除了過濾器，圖像過濾器，GeoIP模塊和嵌入式Perl語言。

要安裝nginx/Windows，[download](https://nginx.org/en/download.html)最新的主線版本發行版（1.31.3），因為nginx的主線分支包含所有已知的修復。然後解壓縮發行版，轉到nginx-1.31.3目錄，並運行`nginx`。下面是驅動器C：根目錄的示例：

```
cd c:\
unzip nginx-1.31.3.zip
cd nginx-1.31.3
>啟動nginx
```

運行`tasklist`命令行工具查看nginx進程：

```
C：\nginx-1.31.3>tasklist /fi「imagine eq nginx.exe」

>圖片名稱           PID會話名稱     會話編號    內存使用
=============== ======== ============== ========== ============
nginx.exe            652 Console                 0      2 780 K
nginx.exe           1332 Console                 0      3 112 K
```

其中一個進程是主進程，另一個是工作進程。如果nginx沒有啟動，請在錯誤日誌文件`logs\error.log`中查找原因。如果日誌文件尚未創建，則應在Windows事件日誌中報告此原因。如果顯示錯誤頁面而不是預期頁面，請在`logs\error.log`文件中查找原因。

nginx/Windows在配置中使用它運行的目錄作為相對路徑的前綴。在上面的示例中，前綴是`C:\nginx-1.31.3\`。配置文件中的路徑必須使用UNIX風格的正斜槓指定：

```nginx
access_log   logs/site.log;
root         C:/web/html;
```

nginx/Windows作為標準控制台應用程式（而不是服務）運行，可以使用以下命令進行管理：

```
<table width="100%"><tbody><tr><td width="20%">nginx -s stop</td><td>fast shutdown</td></tr><tr><td>nginx -s quit</td><td>graceful shutdown</td></tr><tr><td>nginx -s reload</td><td>changing configuration, starting new worker processes with a new configuration, graceful shutdown of old worker processes</td></tr><tr><td>nginx -s reopen</td><td>re-opening log files</td></tr></tbody></table>
```

#### 已知問題

-   雖然可以啟動幾個工作器，但實際上只有一個工作器在工作。
-   不支持UDP（以及QUIC）功能。

#### 未來可能的改進

-   作為服務運行。
-   使用I/O完成埠作為連接處理方法。
-   在單個工作進程中使用多個工作線程。