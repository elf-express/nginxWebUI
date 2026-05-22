# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案簡介
nginxWebUI 是一款簡化 NGINX 配置的網頁管理工具。用戶無需手寫 nginx.conf，通過 UI 填寫表單即可完成反向代理、SSL、負載均衡等配置。

主入口：[com.cym.NginxWebUI](src/main/java/com/cym/NginxWebUI.java)（`@SolonMain` + `@EnableScheduling`，啟動時會先殺掉同名舊 jar process 再 `Solon.start()`）。

## 技術棧
- **後端：** Java 8 + [Solon 3.3.3](https://solon.noear.org/) — **不是 Spring Boot**
  - DI 註解：Service 用 `@Component`、Controller 用 `@Controller`，注入用 `@Inject`（不是 `@Service` / `@Autowired`）
  - 路由：`@Mapping("/path")` 同時用於 class 與 method 級別
  - 排程：`@Scheduled` 來自 `solon-scheduling-simple`
- **前端：** Layui + jQuery + Freemarker 模板（伺服端渲染，**不是 SPA**）
- **資料庫：** SQLite（預設）/ PostgreSQL / MySQL — 透過 `--spring.database.type` 切換
- **構建：** Maven（產物 `target/nginxWebUI-<version>.jar`，含 `jar-with-dependencies`）
- **測試：** Playwright（E2E 自動化測試，**不依賴 JUnit 做端對端**）
- **容器：** Docker + Docker Compose（含 PostgreSQL + Loki + Grafana + CrowdSec stack）

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
- **i18n key 慣例：** Properties 檔內 key 以「頁面 + Str」命名（如 `serverStr.add`、`httpGroup.base`），對應 Controller 注入 `MessageUtils m` 後傳給 Freemarker；模板裡用 `${serverStr.xxx}` 引用
- **新增任何使用者可見的字串時必須同時改三份 properties**：`messages.properties`（簡中）、`messages_zh_TW.properties`（繁中）、`messages_en_US.properties`（英文）

### 後端
- 控制器放在 `controller/adminPage/` 下（目前共 28 個，含 CrowdSec / Geo / ASN / ProtectionCert / SiteResource 等近期新增的安全防護功能）
- Service 用 `@Component` 註冊、`@Inject SqlHelper sqlHelper;` 注入
- 資料庫操作使用 `SqlHelper`（自製 ORM，非 JPA，詳見下文「SqlHelper 速查」）
- 主鍵一律用 `SnowFlakeUtils.getId()`（雪花 ID，String 存進去但 Long 產生）
- 初始化邏輯寫在 `InitConfig.java`
- 配置參數通過 `app.yml` 或啟動參數傳入

### 測試（詳見 docs/superpowers/plans/playwright-guide.md）
- 測試檔放在 `tests/e2e/`（目前共 24 份，編號 `01-login` 到 `22-asn-block` + `flag-svg-integrity` + `22-lang-switch`）
- 按鈕文字用正則匹配簡繁體：`/批量輸入|批量输入/`
- Layui 元件用 `page.evaluate()` 操作
- **全部測試（headed）：** `npm test`
- **全部測試（headless / CI）：** `npm run test:fast`
- **單一檔案：** `npx playwright test tests/e2e/08-crowdsec.spec.js --headed`
- **單一 test name：** `npx playwright test -g "批量輸入"`
- **debug 單一檔案：** `npx playwright test tests/e2e/08-crowdsec.spec.js --debug`
- 報告：`npm run report`（開 http://localhost:9400）

### Docker（詳見 docs/superpowers/plans/docker-guide.md）
- container_name：`{專案名}-{版本}-{服務名}`
- volume name：`{專案名}_{用途}_data`
- 必須配 healthcheck 和啟動順序
- entrypoint.sh 必須 LF 換行

## 核心架構流程

請求一條典型的「使用者編輯 HTTP 參數」資料流，依序穿越這幾層：

```
┌─ Freemarker view (src/main/resources/WEB-INF/view/adminPage/*.html)
│     ↓  Layui 表單 submit / jQuery ajax
│
│  Controller — com.cym.controller.adminPage.HttpController
│     @Controller @Mapping("/adminPage/http")  → 路由由 Solon 解析
│     @Inject HttpService httpService;         → DI 注入 Service
│     方法回傳 ModelAndView 或 JsonResult
│     ↓
│
│  Service — com.cym.service.HttpService（純 POJO + 一個 @Inject）
│     @Component
│     @Inject SqlHelper sqlHelper;
│     ↓
│
│  SqlHelper（自製 ORM） — com.cym.sqlhelper.utils.SqlHelper
│     ConditionAndWrapper / Page / Sort
│     ↓ JDBC
│
└─ SQLite ／ PostgreSQL ／ MySQL
```

**新增一個 CRUD 頁面的最短路徑：**

1. 建 `model/Xxx.java`，加 `@Table`，欄位用 boxed type（`Long` / `Boolean`），主鍵繼承 `BaseModel`
2. 建 `service/XxxService.java`，掛 `@Component`，注入 `SqlHelper`
3. 建 `controller/adminPage/XxxController.java`，掛 `@Controller @Mapping("/adminPage/xxx")`
4. 建 view `WEB-INF/view/adminPage/xxx/index.html`（Freemarker）+ JS `static/js/adminPage/xxx/index.js`
5. **三份 `messages*.properties` 同步加 i18n key**
6. 在 `tests/e2e/` 加一份 `NN-xxx.spec.js`（編號接續最大值）

## SqlHelper 速查

`SqlHelper` 不是 JPA、不是 MyBatis，是 [src/main/java/com/cym/sqlhelper/utils/SqlHelper.java](src/main/java/com/cym/sqlhelper/utils/SqlHelper.java)。慣用法（節錄自實際 service）：

```java
@Component
public class HttpService {
    @Inject SqlHelper sqlHelper;

    // 全查（按欄位排序）
    public List<Http> findAll() {
        return sqlHelper.findAll(new Sort("seq", Direction.ASC), Http.class);
    }

    // 主鍵查
    Http http = sqlHelper.findById(httpId, Http.class);

    // 條件單筆查（lambda 形 or 字串形都支援）
    Http one = sqlHelper.findOneByQuery(
        new ConditionAndWrapper().eq("name", "log_format"), Http.class);

    // 條件列表查（lambda 形，編譯期安全）
    List<Param> params = sqlHelper.findListByQuery(
        new ConditionAndWrapper().eq(Param::getTemplateId, templateId), Param.class);

    // 寫入
    sqlHelper.insert(entity);              // 新增（外部需先給 ID）
    sqlHelper.updateById(entity);          // 全欄位更新
    sqlHelper.insertOrUpdate(entity);      // ID 為空 → insert；否則 update
    sqlHelper.deleteById(id, Http.class);
}
```

**踩雷點：**
- 主鍵型別是 `String`，但值來自 `SnowFlakeUtils.getId()` 傳回的 `Long`（會自動 toString）。新增 entity 前可以不指定 ID，讓 `insertOrUpdate` 處理。
- `ConditionAndWrapper` 鏈式呼叫不可變；組複合條件用 `.and(new ConditionAndWrapper()...)` / `.or(...)`。
- 沒有 `@Repository`，沒有 Mapper interface — 不要 grep 找 DAO 介面，直接讀對應的 `XxxService.java`。
- 分頁回傳 `Page<T>`，欄位 `records` / `total`，搭配 `new Page<>(pageNum, pageSize)`。

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

**跳過初始化精靈（直接帶入管理員）：**

```bash
java -jar target/nginxWebUI-5.0.3.jar \
     --init.admin=admin \
     --init.pass=admin123 \
     --init.api=true     # 同時開啟 API 呼叫權限
```

> `--init.*` 只在資料庫**還沒有任何管理員**時生效；之後請改用網頁的「管理員管理」修改。

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

**所有 compose 指令必須在 `deploy/` 目錄下執行** — 配置檔 (`promtail-config.yml` / `grafana-*.yml` / `crowdsec/*`) 用相對路徑被引用，且只在 `deploy/` 目錄存在。

```bash
cd deploy

# 拉 release image 並啟動全 stack
docker compose pull
docker compose up -d

# 開發期間從原始碼 build（compose 內已含 build: ../Dockerfile）
docker compose up -d --build

# 確認狀態（應看到所有 service healthy）
docker compose ps

# 查看 logs
docker compose logs -f nginxwebui
```

**Stack 組成（從 `deploy/docker-compose.yml`）：**

| Service | Port (host:container) | 用途 |
|---|---|---|
| nginxwebui | **12300:8080** / 80:80 / 443:443 | 主應用（host port 12300 對應 container 內 8080）|
| postgres | 5432 | 資料庫 |
| loki | 3100 | 日誌聚合 |
| grafana | 3000 | 監控 / 日誌 dashboard |
| promtail | — | 把 nginx + app log 推到 Loki |
| crowdsec | — | 入侵偵測 |
| crowdsec-bouncer | — | nginx 流量過濾 |

### 方式 C：多平台映像建構（CI/CD）

不必本機跑 buildx。**`scripts/release.sh 5.x.y` + git push tag → GitHub Actions 自動跑 multi-platform build + push ghcr.io**（見 [.github/workflows/build.yml](.github/workflows/build.yml)）。

要本機快速 build 單平台測試用 `docker compose up -d --build`（5.1.0 後 compose 內已含 `build:` 段）。

### 部署後驗證

```bash
# 健康檢查（compose 把 host 12300 對應到 container 內 8080）
curl http://localhost:12300/

# Nginx 模組列表（驗證 Dockerfile 編入的 18 個 module）
curl http://localhost:12300/adminPage/monitor/nginxInfo
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

# Docker 構建啟動 (從 deploy/ 執行；compose 已內含 build: ../Dockerfile)
cd deploy && docker compose up -d --build

# Docker 狀態
cd deploy && docker compose ps
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
  api:                       # true 時為初始管理員開 API 呼叫權限
```

## Release 流程

完整設計與決策過程見 [docs/superpowers/plans/2026-05-21-dev-release-workflow.md](docs/superpowers/plans/2026-05-21-dev-release-workflow.md)。簡述：

**分支模型：**
- `dev` — 日常開發 + release 動作；HEAD 永遠領先或等於 master
- `master` — 「最後一次 release 的快照指針」；不在 master 上直接 commit、不在 master 上打 tag
- `tag v*` — 由 `scripts/release.sh` 在 dev 上打的；CI 看到 tag 才 build + push image

**ghcr.io image：**
- `ghcr.io/elf-express/nginxwebui:x.y.z` — 對應 tag vx.y.z 的不可變 image
- `ghcr.io/elf-express/nginxwebui:latest` — 永遠等於最新 tag 的 build 結果

### 日常開發（dev 分支）

```bash
git checkout dev
# 開發、commit
git push origin dev
# CI 跑：mvn build + e2e test；不 push image
```

### Release 新版本（dev → tag → master）

```bash
# 1. 保持在 dev
git checkout dev
git pull origin dev

# 2. 跑 release script（自動改 pom + commit + 打 tag）
scripts/release.sh 5.0.4

# 3. 推 dev 含 tag
git push origin dev --tags
# CI 看到 tag v5.0.4 → buildx → push ghcr.io/.../nginxwebui:5.0.4 + :latest

# 4. 等 CI 完成、確認 image 已 push
docker manifest inspect ghcr.io/elf-express/nginxwebui:5.0.4

# 5. 把 dev fast-forward 到 master
git push origin dev:master

# 6. 在 GitHub Releases 頁開 v5.0.4 條目（手動，貼 CHANGELOG）
```

### Hotfix（緊急修 production，不能把 dev 整批帶上來時）

```bash
# 從 master 開 hotfix（master = 已 release 的乾淨快照）
git checkout master
git checkout -b hotfix/5.0.5
# 修 bug、commit ...
scripts/release.sh 5.0.5    # script 允許在 hotfix/* 分支上跑
git push origin hotfix/5.0.5 --tags
# 等 CI 完成
git push origin hotfix/5.0.5:master

# 同步回 dev（避免下版本忘了帶這個修）
git checkout dev
git merge hotfix/5.0.5
git push origin dev

# 清理
git branch -d hotfix/5.0.5
git push origin :hotfix/5.0.5
```

## 已完成的改進

**UI / UX：**
1. 批量輸入參數（http / server / location 三處）
2. TLS 版本預設值修正 + 棄用標註
3. 啟用配置頁面 conf 縮進美化
4. Conf 語法高亮（CodeMirror + monokai 主題）
5. 登入頁面密碼可見切換 + 背景美化
6. 預設 http 參數（gzip、安全 Headers）
7. 預設參數模板（WebSocket、Proxy Headers、大文件上傳、靜態緩存）
8. HTTP 參數分組顯示（`HttpController.GROUP_DEFS`：base/realip/geoip/gzip/brotli/headers/proxy/logging）
9. 模板分組（Template grouping）
10. IP 標籤化（IP tag polish）+ Deny/Allow tag 化
11. 編輯模式（Edit mode）優化
12. Conf 錯誤診斷頁（Error diagnosis）
13. 語系切換 UI（國旗 icon + flag SVG）

**安全防護模組：**
14. CrowdSec 整合（入侵偵測 + nginx bouncer）
15. Geo blocking（GeoIP2 國家封鎖）
16. ASN block（自治系統封鎖）
17. Protection Cert（防爬蟲憑證）
18. Real-IP 模組設定頁

**監控與運維：**
19. Nginx 模組自動偵測（`/adminPage/monitor/nginxInfo`）
20. Site Resource 資源頁
21. 連線測試（Test connectivity）

**部署 / 測試：**
22. 測試用驗證碼支援（`--project.testCaptcha`）
23. Docker Compose Stack（PG 18 + Loki + Grafana + CrowdSec）
24. `.gitattributes` 強制全文字檔 LF（Docker `entrypoint.sh` 跨平台不壞）
25. Playwright E2E 套件涵蓋 22+ 場景

## 詳細文件
- [改進計畫與完成報告](docs/superpowers/plans/completion-report.md)
- [Playwright 測試規範](docs/superpowers/plans/playwright-guide.md)
- [Docker 構建方案](docs/superpowers/plans/docker-guide.md)
- [Docker 容器規範](docs/superpowers/plans/docker-standard.md)
- [Playwright 快速指令](playwright.md)
