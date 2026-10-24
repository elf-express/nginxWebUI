# Dev environment, run & deploy / 開發環境、執行與部署

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。

## Dev Environment

| Tool | Version | Check |
|---|---|---|
| JDK | Java 17 (LTS) | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Node.js | 24（`frontend-maven-plugin` 自帶 v24.18.0 到 `frontend/node/`，`mvn clean` 不會刪） | `node -v` |
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

> 注意：`mvn package` 在 generate-resources 階段會跑 `frontend-maven-plugin`（1.15.1）：安裝 Node、`npm ci --prefer-offline`、`npm run build`（vue-tsc + vite build）。
> 第一次建置會下載 Node 與 npm 依賴，之後 `mvn -o` 可離線；`-DskipTests` 建置時間約 27s → 54s。
> 前端開發：`cd frontend && npm test`（Vitest）· `npm run build` · `npm run typecheck`。

**Orca worktree：** 「工作樹位置」填 `.ocrca\worktrees`（已列入 `.gitignore`）。新 worktree 的初始化腳本在根目錄
[`orca.yaml`](../../orca.yaml)（`scripts.setup`，團隊共用）：`npm ci`、裝 Chromium、建 jar（E2E 依賴它），
主倉庫有 `docker/.env` 時一併複製。主倉庫勿跑 `git clean -x`，會連 worktree 一起刪掉。

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
- 不殺同名 jar 進程：`--project.skipKillSelf=true`（預設啟動時會 kill 其他 `nginxWebUI*.jar` 進程當作舊版本；E2E 另起第二個實例時帶這個）
- 關閉 GeoIP 自動下載：`--project.skipGeoipFetch=true`（預設開啟；排程不再下載 mmdb，E2E 與離線環境用，手動下載不受影響）
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
**profile 可選** —— `security`：crowdsec（自建 `nginxwebui-crowdsec`：官方 v1.8.1 base + 烤 config 與 hub 內容）。
沒有獨立 bouncer 容器：封鎖由 nginxwebui image 內的官方 Lua bouncer（`lua-cs-bouncer` v1.2.3）在 nginx access 階段執行，
順序是白名單 → CrowdSec 連不到超過寬限（預設 120 秒）回 503 → ban 決策回 444 → 保留路徑 `/.well-known/nwu-challenge/`
與 captcha 決策交給自架驗證頁 → AppSec 擋下回 403。驗證頁的判定規則、兩種模式、金鑰與部署注意事項見
[docs/crowdsec-challenge.md](../crowdsec-challenge.md)（E-387）。
LAPI 8080、AppSec 7422 只在 compose 網路內，不 publish。

> 注意：crowdsec config（acquis/profiles/abuseipdb）與 hub 內容（nginx 日誌解析、AppSec virtual patching + CRS）
> 已烤進自建 image（`docker/crowdsec/Dockerfile`），啟動不連 hub，升版跟著 image 走；runtime secret 仍走 `.env`。
> 自己 bind-mount `/etc/crowdsec` 會蓋掉烤好的 hub 內容。
> 注意：**金鑰與 profile 要一起開**。nginxwebui 看不到 profile，只看金鑰（`/run/secrets/crowdsec_bouncer_key`
> 優先，其次 `CROWDSEC_BOUNCER_KEY`）：有金鑰才產生 `conf.d/crowdsec_http.conf` 與
> `<home>crowdsec/crowdsec-nginx-bouncer.conf`（600）。只填金鑰沒起 crowdsec → 寬限過後非白名單全站 503。
> 金鑰用 `openssl rand -hex 32` 產生；crowdsec 首次啟動以 `BOUNCER_KEY_nginx` 自動註冊 bouncer `nginx`，
> 已存在就不重註冊 —— 換金鑰或從舊 traefik bouncer 升級時，沿用舊金鑰或先
> `docker exec nginxwebui-crowdsec cscli bouncers delete nginx` 再 `docker compose up -d`；
> 舊的 `nginxwebui-bouncer` 容器用 `--remove-orphans` 清掉。金鑰不想進環境變數：疊 `docker-compose.secrets.yml`（見檔頭）。
> 注意：後台管理封鎖（「CrowdSec 防護」頁、新增／刪除封鎖、ASN 推送）用另一組 machine 帳號：密碼 `CROWDSEC_MACHINE_PASSWORD`
>（secrets 檔 `crowdsec_machine_password` 優先），帳號 `CROWDSEC_MACHINE_ID`（預設 `nginxwebui`）。crowdsec 容器每次啟動都以官方
> `AGENT_USERNAME`／`AGENT_PASSWORD` 機制（`cscli machines add --force`，包裝腳本 `/nwu-start.sh` 先讀 secrets 檔）註冊或更新它。
> Java 以 `POST /v1/watchers/login` 取 JWT（快取到 expire 前 60 秒，401 重登一次），新增封鎖走 `POST /v1/alerts`、刪除走
> `DELETE /v1/decisions`，讀名單仍用 bouncer 金鑰。所有請求帶 `User-Agent: nginxWebUI/CrowdSec`（真實 LAPI 會拒絕不是「名稱/版本」形式的 UA）。
> 後台加的封鎖 reason 一律 `nginxwebui:` 開頭（自訂 reason 會變成 `nginxwebui:manual:<reason>`），`profiles.yaml` 已設定排除它們、不送
> AbuseIPDB（以設定檔檢查驗證，未對 AbuseIPDB 實際送測）。
> 寬限秒數在「CrowdSec 防護」頁的連線設定分頁修改（setting `crowdsec.graceSeconds`），要再套用設定才寫進 nginx。
> 注意：舊版 `docker/.env.example` 附過未註解的 `CROWDSEC_BOUNCER_KEY=your-bouncer-key-here`，`CrowdSecCredentials` 把這個
> 範例值當成未設定（只 WARN 一次、不含金鑰）。只跑核心服務的部署請清空 `.env` 的 `CROWDSEC_BOUNCER_KEY`；`security` 部署若真的拿它當
> 金鑰，要 `openssl rand -hex 32` 換新金鑰，再 `cscli bouncers delete nginx` → `docker compose up -d` → 套用設定。
> 注意：在 http 參數／模板自訂 `resolver` 會取代內建 `127.0.0.11`，必須能解析服務名 `crowdsec`，否則把 `CROWDSEC_LAPI_URL`／`CROWDSEC_APPSEC_URL` 改 IP。
> 注意：nginx.conf 只在「套用設定」時重新產生，容器重啟會沿用舊檔。新增／更換金鑰後要再套用一次才會載入 bouncer；拿掉金鑰後重啟是安全的（啟動時 InitConfig 會刪掉過期的 bouncer 設定檔，Lua 包裝發現檔案不存在就不啟用、流量放行），不必先套用。
> 測試：`sh docker/test/lua-smoke.sh <image>`（Lua 載入、live 模式）、`sh docker/test/crowdsec-offline.sh <crowdsec image>`
>（無網路啟動）、`sh docker/test/crowdsec-e2e.sh <nginxwebui image> <crowdsec image>`（整個 stack，stream 模式，
> 約 10 分鐘；用獨立 project `nwu-e376-e2e` 與自己的 volume／網段，不碰既有部署）。
> 驗證頁：`sh docker/test/lua-unit.sh <image>`（Lua 純函式與交接票測試資料）、`sh docker/test/challenge-smoke.sh <image>`
>（image 內 nginx 出題／驗答／交接票）、`sh docker/test/crowdsec-challenge-e2e.sh <nginxwebui image> <crowdsec image>`
>（整個 stack 跑在 internal 網路上，約 3 分鐘，獨立 project `nwu-e387-e2e`）。
> machine 帳號：`sh docker/test/crowdsec-machine.sh <crowdsec image>`（secrets 檔／環境變數註冊、重啟、可登入）、
> `sh docker/test/crowdsec-admin-e2e.sh <nginxwebui image> <crowdsec image>`（後台新增／刪除封鎖、ASN 撤銷、錯誤密碼，約 3 分鐘，
> 獨立 project `nwu-e390-e2e`）。
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
