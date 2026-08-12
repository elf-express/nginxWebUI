# page

> Source: https://nginx.org/en/docs/freebsd_tuning.html

---

## 目錄

- [Tuning FreeBSD for the highload](#tuning-freebsd-for-the-highload)
    - [Listen queues](#listen-queues)
    - [Socket buffers](#socket-buffers)
    - [mbufs, mbuf clusters, etc.](#mbufs-mbuf-clusters-etc)
    - [Outgoing connections](#outgoing-connections)
    - [Finalizing connection](#finalizing-connection)

---

## 調優FreeBSD以適應高負載

#### 監聽隊列

連接建立後，它被放置在偵聽套接字的偵聽隊列中。要查看當前偵聽隊列狀態，您可以運行命令"`netstat -Lan`"：

> >當前監聽隊列大小（qlen/incqlen/maxqlen）
> >試聽         本地地址
> tcp4  **10**/0/128       \*.80
> tcp4  0/0/128        \*.22

這是一個正常的情況：埠\*：80的偵聽隊列僅包含10個未接受的連接。如果Web伺服器無法處理負載，您可能會看到如下內容：

> >當前監聽隊列大小（qlen/incqlen/maxqlen）
> >試聽         本地地址
> tcp4  **192/**0/**128**      \*.80
> tcp4  0/0/128        \*.22

這裡有192個未接受的連接，很可能是新的連接正在被丟棄。雖然限制是128個連接，但FreeBSD允許接收超過限制1.5倍的連接，然後才開始丟棄新的連接。您可以使用

```
sysctl克恩. ipc. socacceptqueue = 4096
```

但是，請注意隊列只是抑制突發的阻尼器。如果總是溢出，這意味著您需要改進Web伺服器，但不是繼續增加限制。您也可以在nginx配置中更改偵聽隊列最大大小：

```nginx
listen  80  backlog=1024;
```

但是，您不能將其設置為超過當前`kern.ipc.soacceptqueue`值。默認情況下，nginx使用FreeBSD內核的最大值。

#### Socket buffers

當客戶端發送數據時，數據首先由內核接收，內核將數據放入套接字接收緩衝區。然後，應用程式（如Web伺服器）可以調用`recv()`或`read()`系統調用來從緩衝區獲取數據。當應用程式想要發送數據時，它調用`send()`或`write()`系統調用將數據放入套接字發送緩衝區。然後內核管理將數據從緩衝區發送到客戶端。在現代FreeBSD版本中，套接字的默認大小接收和發送緩衝區分別為64K和32K。您可以使用sysctls`net.inet.tcp.recvspace`和`net.inet.tcp.sendspace`動態更改它們。當然，更大的緩衝區大小可能會增加吞吐量，因為連接可能會使用更大的TCP滑動窗口大小。在Internet上，您可能會看到將緩衝區大小增加到一兆甚至幾兆字節的建議。然而，這樣大的緩衝區大小適合本地網絡或您控制的網絡。因為在Internet上，慢速網絡客戶端可能會請求一個大文件，然後它會在幾分鐘內下載該文件，如果不是幾小時的話。所有這些時間兆字節的緩衝區都將綁定到慢速客戶端，儘管我們可能只為它分配幾個字節。

對於使用阻塞I/O系統調用的Web伺服器（如Apache）來說，大的發送緩衝區還有一個好處。伺服器可能會將整個大的響應放在發送緩衝區中，然後可能會關閉連接，讓內核將響應發送到一個慢的客戶端，而伺服器已經準備好服務其他請求。您應該決定在您的情況下綁定到客戶端更好：一個幾十兆字節的Apache/mod\_perl進程或者幾百兆字節的socket發送緩衝區。注意nginx使用非阻塞I/O系統調用，只占用幾十兆字節的連接，因此它不需要很大的緩衝區大小。

#### mbufs、mbuf集群等

在內核內部，緩衝區以使用 * mbuf * 結構連結的內存塊鏈的形式存儲。mbuf大小為256位元組，可用於存儲少量數據，例如TCP/IP報頭。然而，mbuf主要指向存儲在 * mbuf集群 * 或 * 巨型集群 * 中的其他數據，mbuf集群大小為2K，巨型集群大小可以等於一個CPU頁面大小（amd64為4K）、9K或16K。9K和16K巨型集群主要用於乙太網幀大於通常1500位元組的本地網絡，而且它們超出了本文的範圍。頁面大小巨型集群通常只用於發送，而mbuf集群用於發送和接收。要查看mbuf和集群的當前使用情況及其限制，您可以運行命令"`netstat -m`"。這裡是FreeBSD 7.2/amd64的一個默認設置示例：

```
1477/** 3773/5250 mbufs ** 正在使用（當前/緩存/總數）
771/2203/** 2974/25600 mbuf群集 ** 在使用中（當前/緩存/總數/最大值）
771/1969 mbuf+使用中的數據包輔助區域外的群集
   （當前/緩存）
296/863/**1159/12800 4k（頁面大小）巨型群集 ** 正在使用中
   （current/cache/total/max）
0/0/0/6400 9 k巨型群集正在使用（當前/緩存/總數/最大值）
0/0/0/3200個16 k巨型群集正在使用（當前/緩存/總數/最大值）
3095 K/8801 K/11896 K字節分配給網絡（當前/緩存/總）
0/0/0個mbufs請求被拒絕（mbufs/clusters/mbuf+clusters）
0/0/0個巨型群集請求被拒絕（4k/9 k/16 k）
0/0/0 sfbufs正在使用（當前/峰值/最大值）
0個sfbufs請求被拒絕
0個sfbufs請求延遲
523590個由sendfile發起的I/O請求
0調用協議排出例程
```

有12800個頁面大小的巨型集群，因此它們只能存儲50 M的數據。如果您將`net.inet.tcp.sendspace`設置為1 M，那麼只有50個慢速客戶端將占用所有請求大文件的巨型集群。

您可以使用以下命令動態增加群集限制：

```
sysctl克恩.ipc.nmbclusters=200000
sysctl克恩.ipc.nmbjumbop=100000
```

前一個命令增加mbuf集群限制，後一個命令增加頁面大小巨型集群限制。請注意，所有分配的mbufs集群將占用大約440 M物理內存：（200000 ×（2048 + 256）），因為每個mbuf集群也需要mbuf。所有分配的頁面大小巨型集群將占用大約415 M物理內存：（100000 ×（4096 + 256）），它們加在一起可占845 M。

有一種方法可以在提供靜態文件時不使用巨型集群：*sendfile（）* 系統調用。sendfile允許將文件或其部分直接發送到套接字，而無需閱讀應用程式緩衝區中的部分。它創建mbufs鏈，其中mbufs指向FreeBSD緩存中已經存在的文件頁面，並將鏈傳遞給TCP/IP堆棧。因此，sendfile通過省略兩個內存複製操作來減少CPU使用，並通過使用緩存的文件頁面來減少內存使用。

#### 外發連接

```
net.inet.ip.portrange.randomized=0
net.inet.ip.portrange.first=1024
net.inet.ip.portrange.last=65535
```

#### 正在完成連接

```
net.inet.tcp.fast_finwait2_recycle=1
```