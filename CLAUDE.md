# nginxWebUI 專案指南

## 專案簡介
nginxWebUI 是一款簡化 NGINX 配置的網頁管理工具。用戶無需手寫 nginx.conf，通過 UI 填寫表單即可完成反向代理、SSL、負載均衡等配置。

## 技術棧
- **後端：** Java 8 + Solon 3.3.3 框架（非 Spring Boot）
- **前端：** Layui + jQuery + Freemarker 模板
- **資料庫：** SQLite（預設）/ PostgreSQL / MySQL
- **構建：** Maven
- **測試：** Playwright（E2E 自動化測試）
- **容器：** Docker + Docker Compose

## 目錄結構
```
src/main/java/com/cym/
├── config/         # 初始化、過濾器、配置
├── controller/     # 控制器（adminPage/ 下按功能分）
├── model/          # 資料模型（Server, Location, Http, Template, Param...）
├── service/        # 業務邏輯
├── sqlhelper/      # 自製 ORM
└── utils/          # 工具類

src/main/resources/
├── WEB-INF/view/adminPage/   # Freemarker 模板（HTML）
├── static/js/adminPage/      # 前端 JS（按頁面分目錄）
├── static/lib/               # 第三方庫（layui, codemirror...）
├── messages.properties       # i18n 簡體中文
├── messages_en_US.properties # i18n 英文
├── messages_zh_TW.properties # i18n 繁體中文
└── app.yml                   # 應用配置

tests/e2e/          # Playwright 自動化測試
docs/               # 開發文件與計畫
```

## 開發規範

### 核心原則
1. **不影響現有業務邏輯** — 所有改動僅為 UI 優化或功能擴展
2. **多語言支援** — 新增文字必須同時更新三個 messages 檔案
3. **自動化測試** — 每次新增或修改功能必須附帶 Playwright 測試
4. **零風險優先** — 優先做純前端 / 純新增的改動

### 前端
- 使用 Layui 框架元件
- Layui 的 select / checkbox 需用 `form.render()` 刷新
- JS 按頁面放在對應目錄（如 `static/js/adminPage/server/index.js`）
- i18n 變數在 Freemarker 中用 `${serverStr.xxx}` 引用

### 後端
- 控制器放在 `controller/adminPage/` 下
- 資料庫操作使用 `SqlHelper`（自製 ORM，非 JPA）
- 初始化邏輯寫在 `InitConfig.java`
- 配置參數通過 `app.yml` 或啟動參數傳入

### 測試（詳見 docs/superpowers/plans/playwright-guide.md）
- 測試檔放在 `tests/e2e/`
- 按鈕文字用正則匹配簡繁體：`/批量輸入|批量输入/`
- Layui 元件用 `page.evaluate()` 操作
- 執行：`npm test`
- 報告：`npm run report`

### Docker（詳見 docs/superpowers/plans/docker-guide.md）
- container_name：`{專案名}-{版本}-{服務名}`
- volume name：`{專案名}_{用途}_data`
- 必須配 healthcheck 和啟動順序
- entrypoint.sh 必須 LF 換行

## 開發環境準備

### 先決條件

| 工具 | 版本要求 | 驗證指令 | 安裝建議 |
|---|---|---|---|
| **JDK** | Java 8 (1.8) | `java -version` | OpenJDK 8 / Zulu 8 / Temurin 8 |
| **Maven** | 3.6+ | `mvn -version` | 3.8+ 推薦 |
| **Node.js** | 18+ | `node -v` | LTS 版本 |
| **npm** | 隨 Node 8+ | `npm -v` | — |
| **Git** | 2.30+ | `git --version` | repo 已用 `.gitattributes` 強制 LF，全平台一致 |
| **Docker**（選用） | 20.10+ | `docker --version` | 含 Compose v2 |

> **跨平台換行符**：本 repo 透過 `.gitattributes` 強制全文字檔 LF。即使本機 `core.autocrlf=true`，clone 出來也會是 LF（Docker `entrypoint.sh` 不會壞）。

### 初次開發環境設置

```bash
# 1. Clone
git clone <repo-url> nginxWebUI
cd nginxWebUI

# 2. 驗證 JDK / Maven
java -version    # 必須是 1.8.x
mvn -version

# 3. 安裝 Node 端依賴（Playwright + Chromium）
npm install
npx playwright install --with-deps chromium

# 4. 編譯 Java 端
mvn clean package -DskipTests
# 產物：target/nginxWebUI-<version>.jar （<version> 見 pom.xml 第 19 行附近）
```

### 本地開發啟動

**最小啟動（SQLite，預設 8080 port）：**

```bash
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-5.0.3.jar \
     --server.port=8080 \
     --project.home=./dev-home/
```

啟動後造訪 `http://localhost:8080`，首次會看到「設定管理員密碼」表單。

**指定 PostgreSQL：**

```bash
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.3.jar \
     --server.port=8080 \
     --project.home=./dev-home/ \
     --spring.database.type=postgresql \
     --spring.datasource.url=jdbc:postgresql://localhost:5432/nginxwebui \
     --spring.datasource.username=postgres \
     --spring.datasource.password=postgres
```

**忘記密碼（重置）：**

```bash
java -jar target/nginxWebUI-5.0.3.jar --project.findPass=true
# 啟動會把現有密碼印出後立即退出
```

**測試用驗證碼（限本機）：**

```bash
java -jar target/nginxWebUI-5.0.3.jar --project.testCaptcha=1234
# 之後 CAPTCHA 一律接受 1234，方便 E2E 測試
```

### IDE 設置（無預設 .run / .vscode 配置檔）

- **Main class**：`com.cym.NginxWebUI`
- **Program args**：`--server.port=8080 --project.home=./dev-home/`
- **JVM args**：`-Dfile.encoding=UTF-8`
- **Working directory**：repo 根目錄

## 部署方式

### 方式 A：純 jar 部署（最小化）

```bash
# 1. 構建
mvn clean package -DskipTests

# 2. 上傳到目標機器
scp target/nginxWebUI-5.0.3.jar user@host:/home/nginxWebUI/

# 3. 啟動
ssh user@host
cd /home/nginxWebUI
nohup java -jar -Dfile.encoding=UTF-8 nginxWebUI-5.0.3.jar \
      --server.port=8080 \
      --project.home=/home/nginxWebUI/ \
      > app.log 2>&1 &
```

### 方式 B：Docker Compose Stack（推薦）

詳見 [docs/superpowers/plans/docker-guide.md](docs/superpowers/plans/docker-guide.md)。

```bash
# 構建並啟動全 stack
docker compose up -d --build

# 確認狀態（應看到所有 service healthy）
docker compose ps

# 查看 logs
docker compose logs -f nginxwebui
```

**Stack 組成（從根目錄 `docker-compose.yml`）：**

| Service | Port | 用途 |
|---|---|---|
| nginxwebui | 8080 / 80 / 443 | 主應用 |
| postgres | 5432 | 資料庫 |
| loki | 3100 | 日誌聚合 |
| grafana | 3000 | 監控 / 日誌 dashboard |
| promtail | — | 把 nginx + app log 推到 Loki |
| crowdsec | — | 入侵偵測 |
| crowdsec-bouncer | — | nginx 流量過濾 |

### 方式 C：多平台映像建構（CI/CD）

```bash
# 構建 linux/amd64 + linux/arm64 並 push 到 registry
bash buildx.sh

# 僅本地單平台（linux/amd64）
bash local_build.sh
```

### 部署後驗證

```bash
# 健康檢查（與 Dockerfile HEALTHCHECK 一致）
curl http://localhost:8080/

# Nginx 模組列表（驗證 Dockerfile 編入的 18 個 module）
curl http://localhost:8080/adminPage/monitor/nginxInfo
```

## 測試流程

### Playwright E2E（詳見 [docs/superpowers/plans/playwright-guide.md](docs/superpowers/plans/playwright-guide.md)）

```bash
# 確認版本
npx playwright --version

# 跑全部測試（headed，方便觀看）
npm test

# 無頭模式（CI）
npm run test:fast

# 看測試報告（開 http://localhost:9400）
npm run report
```

測試會自動啟動獨立 server（port 18080）+ 獨立 SQLite。**不會碰你 dev 用的 ./dev-home/**。

### 單元測試（Maven）

```bash
mvn test
# 目前 repo 內 Java 端單元測試覆蓋有限，主要依賴 E2E
```

## 快速指令參考

```bash
# 編譯
mvn clean package -DskipTests

# 本地啟動
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.3.jar --server.port=8080

# E2E 測試
npm test

# 測試報告
npm run report

# Docker 構建啟動
docker compose up -d --build

# Docker 狀態
docker compose ps
```

## app.yml 重要參數
```yaml
project:
  home: /home/nginxWebUI/    # 資料目錄（資料庫、日誌、證書）
  findPass: false            # true 時印出密碼並退出

spring:
  database:
    type: sqlite             # sqlite / postgresql / mysql
  datasource:
    url:                     # PG/MySQL 的 JDBC URL
    username:
    password:

init:
  admin:                     # 初始管理員帳號（空=網頁設定）
  pass:                      # 初始管理員密碼（空=網頁設定）
```

## 已完成的改進
1. 批量輸入參數（http / server / location 三處）
2. TLS 版本預設值修正 + 棄用標註
3. 啟用配置頁面 conf 縮進美化
4. Conf 語法高亮（CodeMirror + monokai 主題）
5. 登入頁面密碼可見切換 + 背景美化
6. 預設 http 參數（gzip、安全 Headers）
7. 預設參數模板（WebSocket、Proxy Headers、大文件上傳、靜態緩存）
8. 測試用驗證碼支援（--project.testCaptcha）
9. Docker Compose Stack（PG 18 + Loki + Grafana）

## 詳細文件
- [改進計畫與完成報告](docs/superpowers/plans/completion-report.md)
- [Playwright 測試規範](docs/superpowers/plans/playwright-guide.md)
- [Docker 構建方案](docs/superpowers/plans/docker-guide.md)
- [Docker 容器規範](docs/superpowers/plans/docker-standard.md)
- [Playwright 快速指令](playwright.md)
