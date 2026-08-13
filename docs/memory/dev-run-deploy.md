# Dev environment, run & deploy / 開發環境、執行與部署

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。

## Dev Environment

| Tool | Version | Check |
|---|---|---|
| JDK | Java 17 (LTS) | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| Git | 2.30+ | `.gitattributes` 強制跨平台 LF |
| Docker (optional) | 20.10+ | 含 Compose v2 |

> 注意：Java 17 是建置目標（2026-07-05 從 Java 8 升級，配合 maxmind-db 4.1.0 需 Java 16+）；
> CI 以 JDK 17 為準（build.yml）。跑 E2E 時 `spawn('java')` 走 PATH，需確保 PATH 的 java 是 17
>（否則 Java 8 跑 Java 17 jar 會 UnsupportedClassVersionError）。

```bash
git clone <repo-url> nginxWebUI && cd nginxWebUI
npm install && npx playwright install --with-deps chromium   # Node deps
mvn clean package -DskipTests                                # → target/nginxWebUI-<version>.jar
```

IDE：Main class `com.cym.NginxWebUI` · Program args `--server.port=8080 --project.home=./dev-home/` · JVM args `-Dfile.encoding=UTF-8`。

## Run

**最小啟動（SQLite，port 8080）：**

```bash
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-<version>.jar --server.port=8080 --project.home=./dev-home/
```

首次造訪會引導設定管理員密碼。

**常用啟動參數：**

- PostgreSQL：`--spring.database.type=postgresql --spring.datasource.url=... --spring.datasource.username=... --spring.datasource.password=...`
- 重設密碼：`--project.findPass=true`（印出密碼後結束）
- 測試用驗證碼：`--project.testCaptcha=1234`（CAPTCHA 永遠接受 1234 —— 給 E2E 用）
- 跳過引導：`--init.admin=admin --init.pass=admin123 --init.api=true`
  > 注意：`--init.*` 只在 DB 還沒有任何管理員時生效。自 5.1.0 起 compose 的 `BOOT_OPTIONS` 不再內建 `--init.admin/pass`（首次走 UI 引導）。
- nginx 文件 MCP：`--mcp.token=<token>`（不設就完全不啟用，見 [mcp.md](mcp.md)）

## Docker Compose（建議）

從 `docker/` 執行。部署只需要 `docker-compose.yml` + `.env`（crowdsec 設定已烤進自建 image，不需要 bind-mount）。
預設 `up -d` = nginxwebui + postgres；加 `--profile security` 才起 CrowdSec IDS：

```bash
cd docker
docker compose pull && docker compose up -d     # pull release images (:latest = newest tag)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build   # 從原始碼建 nginxwebui
docker compose ps                                # 全部 healthy
```

**Stack**（見 [docker/docker-compose.yml](../../docker/docker-compose.yml)）：
**常駐** —— nginxwebui（host **12300**→8080、80、443）· postgres:18-alpine。
**profile 可選** —— `security`：crowdsec · crowdsec-bouncer。CrowdSec = 自建 `nginxwebui-crowdsec`（官方 base + 烤 config）。

> 注意：crowdsec config（acquis/profiles/abuseipdb）已烤進自建 image（`docker/crowdsec/Dockerfile`），
> 升版跟著 image 走；runtime secret 仍走 `.env`。
> 注意：Loki / Promtail / Grafana monitoring profile 已於 2026-06-30 從本專案移除。
> 若 server 上還有 `nginxwebui_loki_data` / `nginxwebui_grafana_data` volume 是歷史遺留，可手動 `docker volume rm` 清理。

## app.yml 關鍵參數

```yaml
project: { home: /home/nginxWebUI/, findPass: false }   # home: 資料目錄 (db/log/cert)
spring:
  database: { type: sqlite }                              # sqlite / postgresql / mysql
  datasource: { url: , username: , password: }            # PG/MySQL JDBC
init: { admin: , pass: , api: }                           # 留空 → 走 UI wizard
mcp: { token: }                                           # 留空 → /mcp 不註冊、回 404
```
