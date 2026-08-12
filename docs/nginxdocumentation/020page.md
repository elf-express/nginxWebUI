# page

> Source: https://nginx.org/en/docs/howto_build_on_win32.html

---

## 目錄

- [Building nginx on the Win32 platform with Visual C](#building-nginx-on-the-win32-platform-with-visual-c)
    - [Prerequisites](#prerequisites)
    - [Build steps](#build-steps)
    - [See also](#see-also)

---

## 使用Visual C在Win32平台上構建nginx

#### Prerequisites

要在Microsoft Win32®平台上構建nginx，您需要：

-   Microsoft Visual C編譯器。已知Microsoft Visual Studio® 8、10、17可以工作。
-   [MSYS](https://sourceforge.net/projects/mingw/files/MSYS/) or [MSYS2](https://www.msys2.org/).
-   Perl，如果你想構建支持SSL的OpenSSL®和nginx。例如[ActivePerl](http://www.activestate.com/activeperl)或[Strawberry Perl](http://strawberryperl.com/)。
-   [Git](https://cli.github.com/) client.
-   [PCRE](http://www.pcre.org/)、[zlib](http://zlib.net/)和[OpenSSL](http://www.openssl.org/)庫源。

#### 構建步驟

在開始構建之前，請確保將Perl、Git和Mubbin目錄的路徑添加到PATH環境變量中。若要設置Visual C環境，請從Visual C目錄運行vcvarsall.bat腳本。

構建nginx：

-   開始狂歡吧。
-   從GitHub倉庫查看nginx原始碼：
    
    ```
    git clone https://github.com/nginx/nginx.git
    ```
    
-   創建構建和lib目錄，並將zlib、PCRE和OpenSSL庫源解壓到lib目錄中：
    
    ```
    mkdir對象
    mkdir objs/lib
    cd objs/lib
    tar -xzf ../../pcre2-10.39.tar.gz
    tar -xzf ../../zlib-1.3.1.tar.gz
    tar -xzf ../../openssl-3.0.14.tar.gz
    ```
    
-   運行配置腳本：
    
    ```
    auto/configure \
        --with-cc =cl \
        --with-debug \
        --prefix= \
        --conf-path=conf/nginx.conf \
        --pid-path=logs/nginx.pid\
        --http-log-path=logs/access.log \
        --錯誤日誌路徑=logs/error.log \
        --sbin-path=nginx.exe \
        --http-client-body-temp-path=temp/client_body_temp \
        --http-proxy-temp-path=temp/proxy_temp \
        --http-fastcgi-temp-path=temp/fastcgi_temp \
        --http-scgi-temp-path=temp/scgi_temp \
        --http-uwsgi-temp-path=temp/uwsgi_temp \
        --with-cc-opt=-DFD_SETSIZE=1024 \
        --with-pcre=objs/lib/pcre2-10.39 \
        --with-zlib=objs/lib/zlib-1.3.1 \
        --with-openssl=objs/lib/openssl-3.0.14 \
        --with-openssl-opt=no-asm \
        --with-http_ssl_module
    ```
    
-   運行製造商：
    
    ```
    nmake
    ```
    

#### 參見

-   [nginx for Windows](https://nginx.org/en/docs/windows.html)