# nginxWebUI

### [README.md English version](README_EN.md)

#### 介紹
nginx網頁配置工具

QQ技術交流群1: 1106758598(已滿)

QQ技術交流群2: 560797506

郵箱: cym1102@qq.com


#### 功能說明

nginxWebUI是一款圖形化管理nginx配置得工具, 可以使用網頁來快速配置nginx的各項功能, 包括http協議轉發, tcp協議轉發, 反向代理, 負載均衡, 靜態html伺服器, ssl證書自動申請、續簽、配置等, 配置好後可一建生成nginx.conf檔案, 同時可控制nginx使用此檔案進行啟動與過載, 完成對nginx的圖形化控制閉環.

nginxWebUI也可管理多個nginx伺服器叢集, 隨時一鍵切換到對應伺服器上進行nginx配置, 也可以一鍵將某臺伺服器配置同步到其他伺服器, 方便叢集管理.

nginx本身功能複雜, nginxWebUI並不能涵蓋nginx所有功能, 但能覆蓋nginx日常90%的功能使用配置, 平臺沒有涵蓋到的nginx配置項, 可以使用自定義引數模板, 在conf檔案中生成配置獨特的引數。

部署此專案後, 配置nginx再也不用上網各種搜尋配置程式碼, 再也不用手動申請和配置ssl證書, 只需要在本專案中進行增刪改查就可方便的配置和啟動nginx。

#### 技術說明

本專案是基於solon的web系統, 資料庫使用sqlite, 因此伺服器上不需要安裝任何資料庫

本系統透過Let's encrypt申請證書, 使用acme.sh指令碼進行自動化申請和續簽, 開啟續簽的證書將在每天凌晨2點進行續簽, 只有超過60天的證書才會進行續簽. 只支援在linux下籤發證書.

新增tcp/ip轉發配置支援時, 一些低版本的nginx可能需要重新編譯，透過新增–with-stream引數指定安裝stream模組才能使用, 但在ubuntu 18.04下, 官方軟體庫中的nginx已經帶有stream模組, 不需要重新編譯. 本系統如果配置了tcp轉發項的話, 會自動引入ngx_stream_module.so的配置項, 如果沒有開啟則不引入, 最大限度最佳化ngnix配置檔案. 




#### jar安裝說明
以Ubuntu作業系統為例,

 **注意：本專案需要在root使用者下執行系統命令，極容易被駭客利用，請一定修改密碼為複雜密碼**

1.安裝java環境和nginx

Ubuntu:

```
apt update
apt install openjdk-11-jdk
apt install nginx
```

Centos:

```
yum install java-11-openjdk
yum install nginx
```

Windows:

```
下載JDK安裝包 https://www.oracle.com/java/technologies/downloads/
下載nginx http://nginx.org/en/download.html
配置JAVA環境變數 
JAVA_HOME : JDK安裝目錄
Path : JDK安裝目錄\bin
重啟電腦
```


2.下載最新版發行包jar

```
Linux: mkdir /home/nginxWebUI/ 
       wget -O /home/nginxWebUI/nginxWebUI.jar https://gitee.com/cym1102/nginxWebUI/releases/download/4.3.8/nginxWebUI-4.3.8.jar

Windows: 直接使用瀏覽器下載 https://gitee.com/cym1102/nginxWebUI/releases/download/4.3.8/nginxWebUI-4.3.8.jar 到 D:/home/nginxWebUI/nginxWebUI.jar
```

有新版本只需要修改路徑中的版本即可

3.啟動程式

```
Linux: nohup java -jar -Dfile.encoding=UTF-8 /home/nginxWebUI/nginxWebUI.jar --server.port=8080 --project.home=/home/nginxWebUI/ > /dev/null &

Windows: java -jar -Dfile.encoding=UTF-8 D:/home/nginxWebUI/nginxWebUI.jar --server.port=8080 --project.home=D:/home/nginxWebUI/
```

引數說明(都是非必填)

--server.port 佔用埠, 預設以8080埠啟動

--project.home 專案配置檔案目錄，存放資料庫檔案，證書檔案，日誌等, 預設為/home/nginxWebUI/

--spring.database.type=mysql 使用其他資料庫，不填為使用本地sqlite資料庫，可選mysql, postgresql

--spring.datasource.url=jdbc:mysql://ip:port/nginxwebui 資料庫url 

--spring.datasource.username=root  資料庫使用者

--spring.datasource.password=pass  資料庫密碼

--init.admin=admin 初始使用者名稱

--init.pass=admin 初始使用者密碼

--init.api=true 初始使用者開啟api許可權

注意Linux命令最後加一個&號, 表示專案後臺執行

#### docker安裝說明

本專案製作了docker映象, 支援 x86_64/arm64/arm v7 平臺，同時包含nginx和nginxWebUI在內, 一體化管理與執行nginx. 

1.安裝docker容器環境

Ubuntu:

```
apt install docker.io
```

Centos:

```
yum install docker
```

2.拉取映象: 

```
docker pull cym1102/nginxwebui:latest

或者

docker pull registry.cn-hangzhou.aliyuncs.com/cym19871102/nginxwebui:latest
```

3.啟動容器: 

```
docker run -itd \
  -v /home/nginxWebUI:/home/nginxWebUI \
  -e BOOT_OPTIONS="--server.port=8080" \
  --net=host \
  --restart=always \
  cym1102/nginxwebui:latest
  
或者

docker run -itd \
  -v /home/nginxWebUI:/home/nginxWebUI \
  -e BOOT_OPTIONS="--server.port=8080" \
  --net=host \
  --restart=always \
  registry.cn-hangzhou.aliyuncs.com/cym19871102/nginxwebui:latest
```

注意: 

1. 啟動容器時請使用--net=host引數, 直接對映本機埠, 因為內部nginx可能使用任意一個埠, 所以必須對映本機所有埠. 

2. 容器需要對映路徑/home/nginxWebUI:/home/nginxWebUI, 此路徑下存放專案所有資料檔案, 包括資料庫, nginx配置檔案, 日誌, 證書等, 升級映象時, 此目錄可保證專案資料不丟失. 請注意備份.

3. -e BOOT_OPTIONS 引數可填充java啟動引數, 可以靠此項引數修改埠號

--server.port 佔用埠, 不填預設以8080埠啟動

4. 日誌預設存放在/home/nginxWebUI/log/nginxWebUI.log

另: 使用docker-compose時配置檔案如下

> **本 fork (elf-express) 提供完整 Stack：** PostgreSQL + Loki + Grafana + CrowdSec + Promtail，配置在 [deploy/docker-compose.yml](deploy/docker-compose.yml)。
> 必須先 `cd deploy/` 再執行 `docker compose up -d`（配置檔以相對路徑引用）。
> 完整 dev / release 流程見 [docs/superpowers/plans/2026-05-21-dev-release-workflow.md](docs/superpowers/plans/2026-05-21-dev-release-workflow.md)。
>
> 下面是上游 cym1102 提供的最小化 docker-compose（單一 service）：

```
version: "3.2"
services:
  nginxWebUi-server:
    image: cym1102/nginxwebui:latest
    volumes:
      - type: bind
        source: "/home/nginxWebUI"
        target: "/home/nginxWebUI"
    environment:
      BOOT_OPTIONS: "--server.port=8080"
    network_mode: "host"
    restart: always

或者

version: "3.2"
services:
  nginxWebUi-server:
    image: registry.cn-hangzhou.aliyuncs.com/cym19871102/nginxwebui:latest
    volumes:
      - type: bind
        source: "/home/nginxWebUI"
        target: "/home/nginxWebUI"
    environment:
      BOOT_OPTIONS: "--server.port=8080"
    network_mode: "host"
    restart: always
```


#### 編譯說明

使用maven編譯打包

```
mvn clean package
```

使用docker構建映象

```
docker build -t nginxwebui:latest .
```

#### 新增開機啟動


1. 編輯service配置

```
vim /etc/systemd/system/nginxwebui.service
```

```
[Unit]
Description=NginxWebUI
After=syslog.target
After=network.target
 
[Service]
Type=simple
User=root
Group=root
WorkingDirectory=/home/nginxWebUI
ExecStart=/usr/bin/java -jar -Dfile.encoding=UTF-8 /home/nginxWebUI/nginxWebUI.jar
Restart=always
 
[Install]
WantedBy=multi-user.target
```

2. 之後執行

```
systemctl daemon-reload
systemctl enable nginxwebui.service
systemctl start nginxwebui.service
```

#### 使用說明

開啟 http://xxx.xxx.xxx.xxx:8080 進入主頁

![輸入圖片說明](README/login.jpeg "login.jpg")

登入頁面, 第一次開啟會要求初始化管理員賬號

![輸入圖片說明](README/admin.jpeg "admin.jpg")

進入系統後, 可在管理員管理裡面新增修改管理員賬號

![輸入圖片說明](README/http.jpeg "http.jpg")

在http引數配置中可以配置nginx的http專案,進行http轉發, 預設會給出幾個常用配置, 其他需要的配置可自由增刪改查. 可以勾選開啟日誌跟蹤, 生成日誌檔案。

![輸入圖片說明](README/tcp.jpeg "tcp.jpg")

在TCP引數配置中可以配置nginx的stream專案引數, 大多數情況下可不配.

![輸入圖片說明](README/server.jpeg "server.jpg")

在反向代理中可配置nginx的反向代理即server項功能, 可開啟ssl功能, 可以直接從網頁上上傳pem檔案和key檔案, 或者使用系統內申請的證書, 可以直接開啟http轉跳https功能，也可開啟http2協議

![輸入圖片說明](README/upstream.jpeg "upstream.jpg")

在負載均衡中可配置nginx的負載均衡即upstream項功能, 在反向代理管理中可選擇代理目標為配置好的負載均衡

![輸入圖片說明](README/html.jpeg "html.jpg")

在html靜態檔案上傳中可直接上傳html壓縮包到指定路徑,上傳後可直接在反向代理中使用,省去在Linux中上傳html檔案的步驟

![輸入圖片說明](README/cert.jpeg "cert.jpg")

在證書管理中可新增證書, 並進行簽發和續簽, 開啟定時續簽後, 系統會自動續簽即將過期的證書, 注意:證書的簽發是用的acme.sh的dns模式, 需要配合阿里雲的aliKey和aliSecret來使用. 請先申請好aliKey和aliSecret

![輸入圖片說明](README/bak.jpeg "bak.jpg")

備份檔案管理, 這裡可以看到nginx.cnf的備份歷史版本, nginx出現錯誤時可以選擇回滾到某一個歷史版本

![輸入圖片說明](README/conf.jpeg "conf.jpg")

最終生成conf檔案,可在此進行進一步手動修改,確認修改無誤後,可覆蓋本機conf檔案,並進行效驗和重啟, 可以選擇生成單一nginx.conf檔案還是按域名將各個配置檔案分開放在conf.d下
 
![輸入圖片說明](README/remote.jpeg "remote.jpg")

遠端伺服器管理, 如果有多臺nginx伺服器, 可以都部署上nginxWebUI, 然後登入其中一臺, 在遠端管理中新增其他伺服器的ip和使用者名稱密碼, 就可以在一臺機器上管理所有的nginx伺服器了.

提供一鍵同步功能, 可以將某一臺伺服器的資料配置和證書檔案同步到其他伺服器中

#### 介面開發

本系統提供http介面呼叫, 開啟 http://xxx.xxx.xxx.xxx:8080/doc.html 即可檢視smart-doc介面頁面.

介面呼叫需要在http請求header中新增token, 其中token的獲取需要先在管理員管理中, 開啟使用者的介面呼叫許可權, 然後透過使用者名稱密碼呼叫獲取token介面, 才能得到token 

![輸入圖片說明](README/smart-doc.png "smart-doc.png")

#### 找回密碼

如果忘記了登入密碼或沒有儲存兩步驗證二維碼，可按如下教程重置密碼和關閉兩步驗證.

1.jar安裝方式, 執行命令

```
java -jar /home/nginxWebUI/nginxWebUI.jar --project.home=/home/nginxWebUI/ --project.findPass=true
```

--project.home 為專案檔案所在目錄, 使用docker容器時為對映目錄

--project.findPass 為是否列印使用者名稱密碼

執行成功後即可重置並列印出全部使用者名稱密碼並關閉兩步驗證

2.docker安裝方式, 首先執行進入docker容器的命令, 其中{ID}為容器的id

```
docker exec -it {ID} /bin/sh
```

再執行命令

```
java -jar /home/nginxWebUI.jar --project.findPass=true
```

執行成功後即可重置並列印出全部使用者名稱密碼並關閉兩步驗證