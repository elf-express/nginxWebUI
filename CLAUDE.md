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

### 測試（詳見 docs/playwright-guide.md）
- 測試檔放在 `tests/e2e/`
- 按鈕文字用正則匹配簡繁體：`/批量輸入|批量输入/`
- Layui 元件用 `page.evaluate()` 操作
- 執行：`npm test`
- 報告：`npm run report`

### Docker（詳見 docs/docker-guide.md）
- container_name：`{專案名}-{版本}-{服務名}`
- volume name：`{專案名}_{用途}_data`
- 必須配 healthcheck 和啟動順序
- entrypoint.sh 必須 LF 換行

## 常用指令

```bash
# 編譯
mvn clean package -DskipTests

# 本地啟動
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.0.jar --server.port=8080

# 自動化測試
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
- [改進計畫與完成報告](docs/completion-report.md)
- [Playwright 測試規範](docs/playwright-guide.md)
- [Docker 構建方案](docs/docker-guide.md)
- [Playwright 快速指令](playwright.md)
