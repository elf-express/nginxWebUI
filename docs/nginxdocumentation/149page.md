# page

> Source: https://nginx.org/en/docs/welcome_nginx_facebook.html

---

## 目錄

- [I am trying to open Facebook, Yahoo!, Yandex, Tumblr, Google etc., and instead I am getting “Welcome to nginx!” page](#i-am-trying-to-open-facebook-yahoo-yandex-tumblr-google-etc-and-instead-i-am-getting-welcome-to-nginx-page)

---

## 我試圖打開Facebook，雅虎！，Yandex，Tumblr，谷歌等，而不是我得到「歡迎來到nginx！」頁面

**Q：** 我試圖打開Facebook，Yahoo！，Yandex，Google或其他一些知名網站，而不是我得到一個空白的網頁，其中有一條消息指的是nginx：「歡迎使用nginx！」或「404 Not Found / nginx」。

我懷疑出了什麼問題，可能有惡意企圖引導我到一個流氓網頁（闖入我的電腦，做網絡釣魚等）。為什麼會這樣，nginx與我試圖連接到Facebook（雅虎！，谷歌等）有什麼關係？

**A：** 首先，您看到的「歡迎來到nginx！」頁面並不是我們的網站。在nginx，我們編寫並分發了一個 ** 免費的 **[open source](http://en.wikipedia.org/wiki/Open-source_software)web伺服器軟體。一個網頁上寫著「歡迎來到nginx！」「只是一個診斷響應，可以由任何網站產生，運行nginx web伺服器。目前，nginx是世界上第二大最流行的開源web伺服器，它被超過1.26億人使用（或14%的網際網路）網站。這些網站中的大多數是合法的，但有些不是。我們的軟體是為了在網際網路上實現性能和可擴展性而創建的，它是在[popular open source license](http://nginx.org/LICENSE)下授權的，nginx本身與任何威脅或惡意活動無關- nginx不是惡意軟體，也不在您的計算機上。但某人的惡意軟體確實可能篡改了您的計算機或路由器，將您重定向到欺詐性的Internet伺服器。

我們建議在您的計算機上運行防病毒檢查，並建議在ISP或其他支持人員的幫助下檢查和驗證整個系統設置：

（免責聲明：您明確理解和同意，中國機械製造行業網不對因下述任一情況而發生的任何損害賠償承擔責任，包括但不限於利潤、商譽、使用、數據等方面的損失或其他無形損失的損害賠償（無論中國機械製造行業網是否已被告知該等損害賠償的可能性）：或利潤;或業務中斷）。

-   檢查您的TCP/IP設置，並查看DNS伺服器配置是否與有效配置匹配（由您的Internet服務提供商或IT支持人員建議）。
-   使用[Google Public DNS](http://developers.google.com/speed/public-dns/)，看看它是否能解決問題。從Google對其公共DNS的描述-「Google公共DNS是一種免費的全球域名系統（DNS）解析服務，您可以將其用作當前DNS提供商的替代服務。\[..\]通過使用Google公共DNS，您可以：加快瀏覽體驗。** 提高安全性 **。」
-   清除您的DNS解析器緩存。在Microsoft Windows XP上，轉到開始>運行，然後鍵入以下命令：「ipconfig /flushdns」。在Microsoft Vista、Windows 7和Windows 8上，單擊開始徽標，按照所有程式>附件，右鍵單擊命令提示符，選擇「以管理員身份運行」，鍵入「ipconfig /flushdns」，然後按Enter。
-   點擊瀏覽器中的「頁面重新加載」按鈕。清除瀏覽器數據（緩存、Cookie等）。例如，使用Chrome查找並單擊「清除瀏覽數據」（設置>後台）。使用Internet Explorer查找工具> Internet選項>常規。** 注意：** 您可能會在此處刪除保存的密碼信息，因此請小心操作並檢查您正在執行的確切操作。
-   檢查「hosts」文件是否不包含「127.0.0.1 localhost」以外的條目，如果是，則檢查這些條目是否用於您嘗試訪問的網站。「hosts」文件位於C：\\WINDOWS\\system32\\drivers\\etc目錄中。通常，其中應該只有一個條目，即「127.0.0.1 localhost」。「hosts」文件可以使用標準記事本應用程式進行查看和編輯。
-   檢查瀏覽器安裝的插件和擴展。重新安裝瀏覽器或嘗試替代瀏覽器（如果可能）。

你的 ** 作業系統 ** 設置，** 家庭路由器 ** 設置或 ** 瀏覽器 ** 配置一定有問題，如果你試圖訪問一個知名的網站，而你得到的卻是「歡迎來到nginx！"。如果你的計算機和網絡是乾淨和安全的，這不應該發生。

如果將DNS伺服器更改為Google Public DNS、刷新DNS解析器緩存、修復瀏覽器配置或清理「主機」文件（如果適用）有所幫助，則可能是您的PC上或周圍的某個地方存在惡意軟體。使用您首選的防病毒和反惡意軟體工具找到並清理它。

其他可能有幫助的文章：

DCWG.org:

[How can you detect if your computer has been violated and infected with DNS Changer?](http://www.dcwg.org/detect/)

[How to clean up or fix malicious software (“malware”) associated with DNS Changer](http://www.dcwg.org/fix/)

Microsoft:

[Malicious Software Removal Tool](http://www.microsoft.com/security/pc-security/malware-removal.aspx)

[How can I reset the Hosts file back to the default?](http://support.microsoft.com/kb/972034)

[How to reset Internet Protocol (TCP/IP)](http://support.microsoft.com/kb/299357)

Firefox幫助：

[Disable or remove Add-ons](http://support.mozilla.org/en-US/kb/disable-or-remove-add-ons)

技術配方：

[DNS Cache Flush, Clear, or Reset in Vista, Windows 7, and Windows 8](http://www.tech-recipes.com/rx/1600/vista_dns_cache_flush/)