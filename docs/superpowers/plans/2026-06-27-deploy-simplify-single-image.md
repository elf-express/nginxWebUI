# 部署簡化:回到單一自建 image Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把部署從「4 個自建 private image + compose 帶 build fallback + 7 容器全預設啟動」簡化為「1 個自建 image(nginxwebui)+ sidecar 全用官方 image 掛 config + 監控/IDS 可選 profile」,讓乾淨機器 `docker compose up -d` 一行就能起來。

**Architecture:** 三個 sidecar(grafana/promtail/crowdsec)的自建 image 本質只是「官方 image + COPY 幾個 config 檔」,改成「官方 image + bind mount 同一批 config(留在 repo 原地)」即可完全消除。compose 移除 `build:`(改用獨立 dev override),監控/IDS 服務加 `profiles:` 變成可選,CI matrix 只剩 nginxwebui。crowdsec 改「單檔 bind mount」順手解掉 named-volume seed-once 陷阱。

**Tech Stack:** Docker Compose v2(profiles)、官方 image(grafana/grafana:11.6.0、grafana/promtail:3.5.0、grafana/loki:3.5.0、crowdsecurity/crowdsec:v1.7.8、postgres:18-alpine、fbonalair/traefik-crowdsec-bouncer:0.5.0)、GitHub Actions(matrix build)。

## Global Constraints

- 只改部署層(docker/ + CI + 文件),**不動主程式業務邏輯**(Java/Solon)。
- compose 檔位置:`docker/docker-compose.yml`;指令一律在 `docker/` 目錄下執行(bind mount 相對路徑以此為基準)。
- container_name 維持:`nginxwebui` / `nginxwebui-<service>`。
- 三方 image 版本維持已 pin 的值,不升級:`postgres:18-alpine`、`grafana/loki:3.5.0`、`grafana/grafana:11.6.0`、`grafana/promtail:3.5.0`、`crowdsecurity/crowdsec:v1.7.8`、`fbonalair/traefik-crowdsec-bouncer:0.5.0`。
- 唯一保留的自建 image:`ghcr.io/elf-express/nginxwebui`(主程式)。
- 預設啟動集合 = `nginxwebui` + `postgres`;`monitoring` profile = loki/promtail/grafana;`security` profile = crowdsec/crowdsec-bouncer。
- 文件不使用 emoji。新增使用者可見字串才需動 i18n(本計畫不涉及)。

---

## File Structure

- `docker/docker-compose.yml` — 主部署檔。改:sidecar 換官方 image + bind mount config、加 profiles、nginxwebui 移除 build、volumes 移除 crowdsec_config。
- `docker/docker-compose.dev.yml` — 新增。本機從原始碼 build nginxwebui 的 override。
- `docker/grafana/Dockerfile`、`docker/promtail/Dockerfile`、`docker/crowdsec/Dockerfile` — 刪除(不再自建)。
- `docker/grafana/*.yml|*.json`、`docker/promtail/config.yml`、`docker/crowdsec/*.yml|*.yaml` — 保留原地,改由 bind mount 掛入。
- `.github/workflows/build.yml` — matrix 只留 nginxwebui。
- `docker/.env.example` — 更新版本變數說明、加 COMPOSE_PROFILES。
- `CLAUDE.md` — 更新部署/sidecar 描述與指令。

---

## Task 1: 三個 sidecar 改官方 image + bind mount config

消除 `-grafana` / `-promtail` / `-crowdsec` 三個自建 image。config 檔保留在 repo 原地,改用 bind mount 掛入官方 image;crowdsec 用「單檔」bind mount,避免蓋掉官方 image 內 `/etc/crowdsec` 的其他預設檔,也順手解掉 seed-once 陷阱。

**Files:**
- Modify: `docker/docker-compose.yml`(promtail / grafana / crowdsec 三段 + volumes 區段)
- Delete: `docker/grafana/Dockerfile`、`docker/promtail/Dockerfile`、`docker/crowdsec/Dockerfile`
- 保留(不動內容):`docker/grafana/datasources.yml`、`docker/grafana/dashboards.yml`、`docker/grafana/nginx-dashboard.json`、`docker/promtail/config.yml`、`docker/crowdsec/acquis.yml`、`docker/crowdsec/profiles.yaml`、`docker/crowdsec/abuseipdb.yaml`

**Interfaces:**
- Produces: 三個 service 不再有 `build:` 與 ghcr sidecar image 參照;config 路徑對應(來自既有 Dockerfile 的 COPY 目標):
  - promtail: `config.yml` → `/etc/promtail/config.yml`
  - grafana: `datasources.yml` → `/etc/grafana/provisioning/datasources/loki.yml`;`dashboards.yml` → `/etc/grafana/provisioning/dashboards/default.yml`;`nginx-dashboard.json` → `/var/lib/grafana/dashboards/nginx-monitor.json`
  - crowdsec: `acquis.yml` → `/etc/crowdsec/acquis.yaml`;`profiles.yaml` → `/etc/crowdsec/profiles.yaml`;`abuseipdb.yaml` → `/etc/crowdsec/notifications/abuseipdb.yaml`

- [ ] **Step 1: 改 promtail 段**

把 `docker/docker-compose.yml` 的 promtail 段(現為 image+build)整段換成:

```yaml
  promtail:
    image: grafana/promtail:3.5.0
    container_name: nginxwebui-promtail
    restart: unless-stopped
    volumes:
      - nginxwebui_log:/var/log/nginx:ro
      - ./promtail/config.yml:/etc/promtail/config.yml:ro
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki
```

- [ ] **Step 2: 改 grafana 段**

把 grafana 段換成(移除 image 的 ghcr 參照與 build,改官方 + 三個單檔 bind mount):

```yaml
  grafana:
    image: grafana/grafana:11.6.0
    container_name: nginxwebui-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/datasources.yml:/etc/grafana/provisioning/datasources/loki.yml:ro
      - ./grafana/dashboards.yml:/etc/grafana/provisioning/dashboards/default.yml:ro
      - ./grafana/nginx-dashboard.json:/var/lib/grafana/dashboards/nginx-monitor.json:ro
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    depends_on:
      loki:
        condition: service_healthy
```

- [ ] **Step 3: 改 crowdsec 段(單檔 bind mount,移除 crowdsec_config named volume)**

把 crowdsec 段換成:

```yaml
  crowdsec:
    image: crowdsecurity/crowdsec:v1.7.8
    container_name: nginxwebui-crowdsec
    restart: unless-stopped
    # config 改單檔 bind mount(只覆蓋自訂的 3 個檔,不碰官方 image 內 /etc/crowdsec 其餘預設),
    # 升版自動跟 repo config,不再需要 down -v 重新 seed。
    volumes:
      - nginxwebui_log:/var/log/nginx:ro
      - crowdsec_data:/var/lib/crowdsec/data
      - ./crowdsec/acquis.yml:/etc/crowdsec/acquis.yaml:ro
      - ./crowdsec/profiles.yaml:/etc/crowdsec/profiles.yaml:ro
      - ./crowdsec/abuseipdb.yaml:/etc/crowdsec/notifications/abuseipdb.yaml:ro
    environment:
      - COLLECTIONS=crowdsecurity/nginx crowdsecurity/http-cve crowdsecurity/base-http-scenarios
      - BOUNCER_KEY_nginx=${CROWDSEC_BOUNCER_KEY}
      - ABUSEIPDB_API_KEY=${ABUSEIPDB_API_KEY}
    healthcheck:
      test: ["CMD", "cscli", "version"]
      interval: 15s
      timeout: 5s
      start_period: 30s
      retries: 3
```

- [ ] **Step 4: 移除 volumes 區段裡的 crowdsec_config**

`docker/docker-compose.yml` 底部 `volumes:` 區段刪掉這兩行:

```yaml
  crowdsec_config:
    name: nginxwebui_crowdsec_config
```

其餘 volume(nginxwebui_data / nginxwebui_log / postgres_data / loki_data / grafana_data / crowdsec_data / nginxwebui_geoip)保留。

- [ ] **Step 5: 刪除三個 sidecar Dockerfile**

```bash
git rm docker/grafana/Dockerfile docker/promtail/Dockerfile docker/crowdsec/Dockerfile
```

- [ ] **Step 6: 驗證 compose 可解析**

Run(在 `docker/` 目錄):
```bash
docker compose config >/dev/null && echo OK
```
Expected: 印出 `OK`,無錯誤(尤其無 `crowdsec_config` undefined volume 報錯)。

- [ ] **Step 7: 驗證三個 sidecar 用官方 image 能起來**

Run:
```bash
docker compose up -d loki promtail grafana crowdsec
docker compose ps
```
Expected: 四個都 `Up`;`grafana`/`loki`/`crowdsec` 顯示 `(healthy)`。再確認 crowdsec config 有吃到:
```bash
docker exec nginxwebui-crowdsec cscli collections list
```
Expected: 列出 `crowdsecurity/nginx` 等 collection,crowdsec 正常運行(代表單檔 bind mount 沒破壞官方預設 config)。

- [ ] **Step 8: 收尾並 commit**

```bash
docker compose down
git add docker/docker-compose.yml
git commit -m "refactor(docker): sidecars use official images + bind-mount config (drop 3 self-built images)"
```

---

## Task 2: nginxwebui 移除 build fallback,改用獨立 dev override

部署用的 compose 不該帶 `build:`——它會在 pull 失敗時偷偷 fallback build,在沒有原始碼的部署機報出與真因無關的 `path not found`。本機 build 的能力移到獨立 override 檔。

**Files:**
- Modify: `docker/docker-compose.yml`(nginxwebui 段移除 `build:`)
- Create: `docker/docker-compose.dev.yml`

**Interfaces:**
- Consumes: Task 1 後的 compose。
- Produces: 部署檔純 pull;本機 build 走 `-f docker-compose.yml -f docker-compose.dev.yml`。

- [ ] **Step 1: 移除 nginxwebui 段的 build 區塊**

刪掉 nginxwebui 段這三行:

```yaml
    build:
      context: ..
      dockerfile: Dockerfile
```

並把上方那兩行「想從原始碼本機 build」的註解改為:

```yaml
    # 預設拉 ghcr.io release image。要釘版本:在 docker/.env 設 NGINX_WEBUI_VERSION=x.y.z
    # 本機從原始碼 build:見 docker-compose.dev.yml(docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build)
```

- [ ] **Step 2: 建立 dev override**

Create `docker/docker-compose.dev.yml`:

```yaml
# 本機從原始碼 build nginxwebui(其餘 sidecar 一律用官方 image,不在這裡 build)。
# 用法(在 docker/ 目錄):
#   docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
# 前置:先在 repo 根跑 mvn clean package -DskipTests(Dockerfile 會 COPY target/*.jar)
services:
  nginxwebui:
    build:
      context: ..
      dockerfile: Dockerfile
```

- [ ] **Step 3: 驗證部署檔不再 fallback build**

Run(在 `docker/`):
```bash
docker compose config | grep -A3 "nginxwebui:" | grep build || echo "NO BUILD (correct)"
```
Expected: 印出 `NO BUILD (correct)`(部署檔 nginxwebui 已無 build 區塊)。

- [ ] **Step 4: 驗證 dev override 仍能 build**

Run(在 `docker/`):
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml config | grep -A3 "nginxwebui:" | grep -q "context" && echo "DEV BUILD OK"
```
Expected: 印出 `DEV BUILD OK`。

- [ ] **Step 5: Commit**

```bash
git add docker/docker-compose.yml docker/docker-compose.dev.yml
git commit -m "refactor(docker): move nginxwebui build to dev override (deploy compose is pull-only)"
```

---

## Task 3: 監控/IDS 改可選 profile

只想要 nginx UI 的人,`docker compose up -d` 只該起 nginxwebui + postgres。其餘做成 opt-in。

**Files:**
- Modify: `docker/docker-compose.yml`(loki/promtail/grafana/crowdsec/crowdsec-bouncer 加 `profiles:`)

**Interfaces:**
- Produces: profile 對應 — `monitoring` = loki/promtail/grafana;`security` = crowdsec/crowdsec-bouncer。無 profile 的 service(nginxwebui/postgres)永遠啟動。

- [ ] **Step 1: 給 monitoring 三個 service 加 profile**

在 `loki`、`promtail`、`grafana` 三段各加一行(放在 `container_name` 下方即可):

```yaml
    profiles: ["monitoring"]
```

- [ ] **Step 2: 給 security 兩個 service 加 profile**

在 `crowdsec`、`crowdsec-bouncer` 兩段各加:

```yaml
    profiles: ["security"]
```

- [ ] **Step 3: 驗證預設只起兩個**

Run(在 `docker/`):
```bash
docker compose config --services | sort | tr '\n' ' '
```
Expected: 包含全部 7 個 service 名(config 會列出全部,profile 只影響啟動)。再驗實際啟動集合:
```bash
docker compose up -d
docker compose ps --services | sort | tr '\n' ' '
```
Expected: 只有 `nginxwebui postgres`。

- [ ] **Step 4: 驗證 profile 能拉起其餘**

Run:
```bash
docker compose --profile monitoring --profile security up -d
docker compose ps --services | wc -l
```
Expected: `7`(全部起來)。

- [ ] **Step 5: 收尾並 commit**

```bash
docker compose --profile monitoring --profile security down
git add docker/docker-compose.yml
git commit -m "feat(docker): monitoring/security as optional compose profiles (default = app + db only)"
```

---

## Task 4: CI matrix 只 build nginxwebui

不再有 sidecar 自建 image,CI 不必 build 它們。

**Files:**
- Modify: `.github/workflows/build.yml`(docker job 的 `strategy.matrix.image`)

**Interfaces:**
- Consumes: 現有 matrix 有 4 個 entry。
- Produces: matrix 只剩 nginxwebui 一個 entry,其餘步驟邏輯不變(`needs_mvn` 判斷仍適用)。

- [ ] **Step 1: 把 matrix 縮成單一 image**

將 `.github/workflows/build.yml` 的 `matrix:` 區塊(現有 4 個 image entry)整段換成:

```yaml
      matrix:
        image:
          - name: nginxwebui
            context: .
            dockerfile: Dockerfile
            needs_mvn: true
```

(移除 `nginxwebui-grafana`、`nginxwebui-promtail`、`nginxwebui-crowdsec` 三個 entry;其餘 steps 不動。)

- [ ] **Step 2: 驗證 workflow YAML 合法**

Run:
```bash
docker run --rm -v "$PWD:/w" -w /w pipelinecomponents/yamllint yamllint .github/workflows/build.yml || echo "檢查 yamllint 輸出,無語法錯即可"
```
Expected: 無 YAML 語法錯誤(可能有 line-length 等 style 警告,可忽略)。若本機無法跑 yamllint,改人工確認 matrix 只剩一個 entry、縮排正確。

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "ci: only build nginxwebui image (sidecars use official images now)"
```

---

## Task 5: 更新 .env.example 與 CLAUDE.md

讓文件與新架構一致:版本變數只影響 nginxwebui、新增 profile 用法、sidecar 描述從「baked image」改為「官方 image + bind mount config」。

**Files:**
- Modify: `docker/.env.example`
- Modify: `CLAUDE.md`(Docker 段落)

**Interfaces:**
- Consumes: Task 1-4 的最終架構。

- [ ] **Step 1: .env.example 加 COMPOSE_PROFILES 說明**

在 `docker/.env.example` 的 `NGINX_WEBUI_VERSION=latest` 那段之後,插入:

```bash
# 要啟用的可選服務(預設只起 nginxwebui + postgres)
# → 監控(Loki/Promtail/Grafana):COMPOSE_PROFILES=monitoring
# → 安全(CrowdSec + bouncer):COMPOSE_PROFILES=monitoring,security
# 留空 = 只起核心。也可改用 `docker compose --profile monitoring up -d`。
COMPOSE_PROFILES=
```

- [ ] **Step 2: 修正 NGINX_WEBUI_VERSION 註解**

把 `docker/.env.example` 第 13 行「從原始碼本機 build」那行註解,改為指向 dev override:

```bash
# → 從原始碼本機 build:docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

- [ ] **Step 3: 更新 CLAUDE.md 的 Docker 描述**

在 `CLAUDE.md` 找到描述 sidecar baked image 的句子(「Sidecars (grafana / promtail / crowdsec) use baked images ...」),改為:

```markdown
- Sidecars (grafana / promtail / crowdsec) run **official images** with config bind-mounted from `docker/<service>/`. Only `nginxwebui` is self-built. Monitoring/security are optional compose **profiles** (`monitoring` / `security`); default `up -d` starts only nginxwebui + postgres.
```

並把 Stack 那行的 sidecar「baked」字眼一併更新為「official image + bind-mount config」。

- [ ] **Step 4: 驗證 compose 與文件描述一致**

Run(在 `docker/`):
```bash
docker compose --profile monitoring --profile security config --services | sort | tr '\n' ' '
```
Expected: `crowdsec crowdsec-bouncer grafana loki nginxwebui postgres promtail`(7 個),與文件描述的 profile 分組一致。

- [ ] **Step 5: Commit**

```bash
git add docker/.env.example CLAUDE.md
git commit -m "docs: deploy now single self-built image + official sidecars + optional profiles"
```

---

## Task 6(可選): nginxwebui image 設 public + Dockerfile 改 multi-stage

簡化後唯一的自建 image 是 nginxwebui。若希望「任何乾淨機器免 login 直接 pull」,把它設 public;若希望「CI/使用者不必先本機 mvn package」,Dockerfile 改 multi-stage。兩者皆 opt-in,與前 5 個 task 獨立。

**Files:**
- Modify: `Dockerfile`(改 multi-stage)
- 外部動作: ghcr package visibility(GitHub 網頁,無 API)

**Interfaces:**
- Consumes: 現 Dockerfile 第 48 行 `COPY target/nginxWebUI-*.jar /home/nginxWebUI.jar`(假設 jar 已存在)。

- [ ] **Step 1: Dockerfile 改 multi-stage(build stage 跑 mvn)**

在現有 `FROM alpine:3.22` 之前插入 build stage,並把第 48 行的 COPY 來源改為從 build stage 取得:

```dockerfile
# ---- build stage:在 image 內 mvn package,免本機先 build ----
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- runtime stage(原本的 alpine) ----
FROM alpine:3.22
# ...（原本內容不變,直到 COPY jar 那行）
```

並把:
```dockerfile
COPY target/nginxWebUI-*.jar /home/nginxWebUI.jar
```
改為:
```dockerfile
COPY --from=build /src/target/nginxWebUI-*.jar /home/nginxWebUI.jar
```

- [ ] **Step 2: 驗證 multi-stage build 成功(不需先 mvn)**

Run(在 repo 根,先確保 target/ 不存在以證明自包含):
```bash
rm -rf target && docker build -t nginxwebui:multistage-test . && echo "MULTISTAGE OK"
```
Expected: build 成功印出 `MULTISTAGE OK`(全程在 image 內 build,不依賴本機 mvn)。

- [ ] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "build(docker): multi-stage Dockerfile (self-contained, no host mvn needed)"
```

- [ ] **Step 4(手動,無法用指令): 設 nginxwebui image 為 public**

GitHub 無改 package visibility 的 API,須在網頁操作:
```
https://github.com/orgs/elf-express/packages/container/nginxwebui/settings
```
→ Danger Zone → Change package visibility → Public。
設完後乾淨機器 `docker compose pull` 免 login 直接過。
(promtail/grafana/crowdsec 已改官方 image,本就不需認證;`-promtail`/`-grafana`/`-crowdsec` 三個舊 private package 可在同頁面刪除。)

---

## Self-Review

**1. Spec coverage(對照對話中的訴求):**
- 「該只有一個 image」→ Task 1(消除 3 個 sidecar 自建 image)、Task 4(CI 只 build 1 個)。✓
- 「換環境就裝不起來」→ Task 6(public,免 login)+ Task 1(sidecar 官方 image 本就免認證)。✓
- 「神秘的 path not found / build fallback」→ Task 2(部署檔移除 build)。✓
- 「crowdsec down -v 清光資料的 seed 陷阱」→ Task 1 Step 3(crowdsec 改單檔 bind mount,不再用 crowdsec_config named volume)。✓
- 「7 容器太重」→ Task 3(profile,預設只起 2 個)。✓

**2. Placeholder scan:** 無 TBD/TODO;每個改 compose/Dockerfile 的 step 都有完整 YAML/Dockerfile 內容;驗證步驟都有實際指令與預期輸出。✓

**3. Type/路徑一致性:** bind mount 的容器內路徑全部對齊原 Dockerfile 的 COPY 目標(promtail `/etc/promtail/config.yml`、grafana 三個 provisioning 路徑、crowdsec 三個 `/etc/crowdsec/...` 路徑);volume 名(crowdsec_data 保留、crowdsec_config 移除)前後一致;profile 名(monitoring/security)在 Task 3 與 Task 5 文件描述一致。✓

**已知需在執行時驗證的風險:**
- crowdsec 單檔 bind mount 唯讀(`:ro`)是否與 v1.7.8 entrypoint 相容 → Task 1 Step 7 用 `cscli collections list` 確認。
- grafana 單檔 bind mount 與 `grafana_data` named volume 在 `/var/lib/grafana` 巢狀掛載 → Task 1 Step 7 ps healthy 確認 dashboard 載入正常。
