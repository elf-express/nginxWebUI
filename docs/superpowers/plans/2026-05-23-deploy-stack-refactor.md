# nginxWebUI Deploy Stack Refactor Implementation Plan

> **SUPERSEDED 2026-06-30:** Plan 規劃 bake 4 sidecar image(nginxwebui + grafana + promtail + crowdsec),實際走 [2026-06-27-deploy-simplify-single-image.md](2026-06-27-deploy-simplify-single-image.md) 簡化路線(只 build nginxwebui,其餘用官方 image + bind-mount),之後 monitoring stack (Loki/Promtail/Grafana) 又於 2026-06-30 整套移除。本 plan 保留作歷史記錄。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓 `docker/docker-compose.yml` 成為完全 self-contained 的部署單元 — 任何 Docker 工具（DockHand / Portainer / Komodo / 純 CLI）拿到 `compose.yml` + `.env` 兩個檔案就能在 server 跑起 7 個 service 全 healthy，無需 bind mount 本機任何檔案。同時清理倉庫重複檔、移除 `--init.*` 預設密碼、目錄改名為國際慣例 `docker/`。

**Architecture:**
1. **Sidecar Image Bake** — 為 grafana / promtail / crowdsec 各做 fork-only image（薄薄 FROM 上游 image + COPY 設定檔），push 到 `ghcr.io/elf-express/nginxwebui-{grafana,promtail,crowdsec}`。Compose 移除所有 `./xxx.yml` bind mount。
2. **目錄重整** — `deploy/` 改名 `docker/`（符合國際慣例：docker stack assets）。根目錄清掉重複設定檔、`.env` 孤兒、JVM crash dump。
3. **--init.* 移除** — 讓首次啟動走 nginxWebUI 原作者 UI 設定精靈（程式碼證據：[InitConfig.java:660-674](../../../src/main/java/com/cym/config/InitConfig.java) `addAdmin()` 第一行 `if (adminCount > 0) return;`）。
4. **CI matrix build** — `.github/workflows/build.yml` 改 matrix，tag 觸發時並行 build + push 4 image。`scripts/release.sh` 維持單 tag 觸發。
5. **deploy.sh 簡化** — image 自帶設定後，移除 cp 設定檔邏輯，只剩 cp compose.yml + cp .env.example + docker compose pull + up。

**Tech Stack:** Docker Compose v2, Dockerfile (multi-FROM), GitHub Actions matrix build, ghcr.io OCI registry, Bash, Java 8 / Solon 3.3.3（主應用不改）, PostgreSQL 18, Grafana 11.6, Loki 3.5, Promtail 3.5, CrowdSec 1.7.8

---

## Context

**問題現況**：使用者在新 server 用 DockHand 部署 `elf-express/nginxWebUI` stack，主應用 + postgres + loki healthy，但 grafana / crowdsec / promtail / crowdsec-bouncer 全部 crash loop 或 `created` 不啟動。

**根因**：
- 架構層：[docker-compose.yml:59-109](../../../deploy/docker-compose.yml) 用了 7 處 `./xxx.yml` bind mount。DockHand 部署時只 import compose.yml + .env，這些檔案不在 server stack 目錄 → 服務 crash。
- 倉庫層：歷史 commit `6cffd23` 把 sidecar 設定檔同時加到根目錄與 `deploy/`，造成兩份。`promtail-config.yml` 根目錄版本缺 country/city/asn 捕獲，`grafana-nginx-dashboard.json` blob hash 已分歧。
- 預設密碼：[docker-compose.yml:21](../../../deploy/docker-compose.yml) 寫死 `--init.pass=${INIT_PASS:-Admin123}`，違反原作者「無預設密碼 + UI 引導」哲學。
- 目錄命名：`deploy/` 應為「部署動作腳本」，但實際裝 Docker assets，違反國際慣例。

**前次互動的錯誤**（不再犯）：
- 上一個 AI 誤判 `deploy.sh` / `entrypoint.sh` 沒用 → **錯**，兩個都是關鍵腳本（[Dockerfile:49](../../../Dockerfile) COPY entrypoint，deploy.sh 是一鍵 install）
- 我推 DockHand Git deploy → **錯**，會綁死工具 + 拉整個 source repo 到 server
- 我推 `${INIT_PASS:?error}` 強制 .env → **錯**，違反原作者哲學

**用戶確認**：
- 「沒有上游了」— fork 已徹底獨立，無回 PR 約束
- 「一次動到位：根目錄只留 metadata」— 激進清理
- 「deploy.sh 留根目錄不改名」— 不動 deploy.sh 位置
- 「密碼我不改」— 不動現有 prod server 的 admin/Admin123（反正 Phase 10 會砍 stack 重建）

**驗證證據**：
- DockHand 確認為 [Finsys/dockhand](https://github.com/Finsys/dockhand) 開源 Docker UI
- `InitConfig.java:114-116` 與 `addAdmin():660-674` 已驗證「移除 --init.* 後首次啟動走 UI 精靈」屬實
- `deploy/` 改名 `docker/` 符合大型 Java/Node 專案慣例（Spring Boot examples、Vercel projects 等）

**預期結果**：
- 7 個 container 全部 healthy
- compose.yml + .env 兩檔可移植到任何 Docker 工具
- 根目錄只剩 metadata 檔
- 首次啟動跳 UI 設定精靈
- CI 自動 build + push 4 image

---

## File Structure

### 新建檔案
- `docker/grafana/Dockerfile` — Grafana sidecar image
- `docker/promtail/Dockerfile` — Promtail sidecar image
- `docker/crowdsec/Dockerfile` — CrowdSec sidecar image

### 重命名檔案（保留 git history）
- `deploy/` → `docker/`（整個目錄）
- `deploy/grafana-datasources.yml` → `docker/grafana/datasources.yml`
- `deploy/grafana-dashboards.yml` → `docker/grafana/dashboards.yml`
- `deploy/grafana-nginx-dashboard.json` → `docker/grafana/nginx-dashboard.json`
- `deploy/promtail-config.yml` → `docker/promtail/config.yml`
- `deploy/crowdsec/*` 已在 `docker/crowdsec/` 內（rename 自動到位）

### 修改檔案
- `docker/docker-compose.yml`（line 21、5-7 處 volumes、image: 三處）
- `docker/.env.example`（移除 INIT_* 段）
- `.github/workflows/build.yml`（matrix 改造 + paths 改 docker/）
- `scripts/release.sh`（確認 4-image push 邏輯）
- `deploy.sh`（簡化路徑、移除 cp 設定檔邏輯）
- `CLAUDE.md`（部署章節、release 章節、init.* 段）
- `.gitignore`（加 `hs_err_pid*` 規則）

### 刪除檔案
- 根目錄 `grafana-dashboards.yml`
- 根目錄 `grafana-datasources.yml`
- 根目錄 `grafana-nginx-dashboard.json`
- 根目錄 `promtail-config.yml`
- 根目錄 `crowdsec/`（整個目錄）
- 根目錄 `.env`（untracked，含 ABUSEIPDB_API_KEY 應 rotate）
- 根目錄 `hs_err_pid31320.mdmp`（untracked）

### 不動檔案
- `Dockerfile`、`entrypoint.sh`、`scripts/update-geoip-cf.sh`、`pom.xml`、`src/`、`tests/`

---

## Phase 0: Pre-flight Check

### Task 0.1: 確認 worktree 狀態與工具齊備

**Files:** 無修改，純檢查

- [ ] **Step 1: 確認在 worktree 內**

Run:
```bash
pwd
git rev-parse --git-common-dir
git branch --show-current
```

Expected:
```
/e/nginxWebUI/.claude/worktrees/zany-tickling-dragon
/e/nginxWebUI/.git
worktree-zany-tickling-dragon
```

如不符 → 停止，呼叫 user 解決。

- [ ] **Step 2: 確認 Docker / Maven / Node 可用**

Run:
```bash
docker --version && docker compose version
mvn --version | head -1
node --version
```

Expected:
- Docker 20+ / Compose v2
- Maven 3.8+
- Node 18+

- [ ] **Step 3: 確認 worktree 與 dev branch 同步**

Run:
```bash
git fetch origin dev
git log --oneline HEAD..origin/dev | head -10
```

Expected: 列出 dev 領先的 commits（如 `a9301ba`, `f3d793e`, `713557c`, `3e4b10a`）

如有領先 commits → 跑 `git merge origin/dev`，解 conflict（特別注意 commit `3e4b10a` 刪了 `buildx.sh` / `local_build.sh`，worktree 內這兩檔自動消失）

- [ ] **Step 4: 確認 working tree clean**

Run:
```bash
git status --porcelain
```

Expected: 空輸出 或 只有 `??` untracked（如 `.env` / `hs_err_pid*.mdmp`）

如有 modified tracked 檔 → 停止，呼叫 user 處理。

---

## Phase 1: 倉庫清理

### Task 1.1: 刪除根目錄重複的 sidecar 設定檔

**Files:**
- Delete: `grafana-dashboards.yml`
- Delete: `grafana-datasources.yml`
- Delete: `grafana-nginx-dashboard.json`
- Delete: `promtail-config.yml`
- Delete: `crowdsec/`（整個目錄）

- [ ] **Step 1: 確認 deploy/ 內對應檔案存在（避免誤刪唯一副本）**

Run:
```bash
ls -la deploy/grafana-dashboards.yml deploy/grafana-datasources.yml deploy/grafana-nginx-dashboard.json deploy/promtail-config.yml deploy/crowdsec/
```

Expected: 6 個檔案 + 1 個目錄都存在

如有缺漏 → 停止，呼叫 user 處理。

- [ ] **Step 2: git rm 5 個檔案 + crowdsec/ 目錄**

Run:
```bash
git rm grafana-dashboards.yml grafana-datasources.yml grafana-nginx-dashboard.json promtail-config.yml
git rm -r crowdsec/
```

Expected:
```
rm 'grafana-dashboards.yml'
rm 'grafana-datasources.yml'
rm 'grafana-nginx-dashboard.json'
rm 'promtail-config.yml'
rm 'crowdsec/acquis.yml'
rm 'crowdsec/abuseipdb.yaml'
rm 'crowdsec/profiles.yaml'
```

- [ ] **Step 3: 確認根目錄沒有殘留**

Run:
```bash
ls grafana-*.yml grafana-*.json promtail-config.yml crowdsec/ 2>&1
```

Expected: 全部 `No such file or directory`

- [ ] **Step 4: Commit**

Run:
```bash
git add -u
git commit -m "$(cat <<'EOF'
chore(repo): remove duplicated sidecar config files from root

These files were accidentally duplicated to root by commit 6cffd23
when deploy/ directory was first introduced. deploy/ versions are
the source of truth (and promtail-config.yml + grafana-nginx-dashboard.json
root versions had already diverged from deploy/ versions).

Removed:
- grafana-dashboards.yml      (duplicate of deploy/)
- grafana-datasources.yml     (duplicate of deploy/)
- grafana-nginx-dashboard.json (root version is stale)
- promtail-config.yml         (root version missing country/city/asn captures)
- crowdsec/                   (entire directory, duplicate of deploy/crowdsec/)
EOF
)"
```

Expected: 1 commit, 7 files deleted

---

### Task 1.2: 刪除根目錄孤兒檔（.env + JVM crash dump）

**Files:**
- Delete: `.env`（untracked，含 CROWDSEC_BOUNCER_KEY + ABUSEIPDB_API_KEY）
- Delete: `hs_err_pid31320.mdmp`（untracked）

- [ ] **Step 1: 備份 .env 內容到 secure location 之前確認 user 已 rotate ABUSEIPDB key**

⚠️ **STOP** — 在執行此 Task 之前必須先確認：
1. ABUSEIPDB_API_KEY 已在 https://www.abuseipdb.com/account/api 重新生成
2. CROWDSEC_BOUNCER_KEY 是 server-side 生成（不需要 rotate，但會在 Phase 10 重設新 stack 時重新生成）

User 在 prompt 中確認 rotate 完成才繼續。

- [ ] **Step 2: 確認檔案是 untracked**

Run:
```bash
git ls-files .env hs_err_pid31320.mdmp 2>&1
```

Expected: 空輸出（兩個檔案都 untracked）

如有任一在輸出 → 停止，改用 `git rm` 處理。

- [ ] **Step 3: rm 兩個檔案**

Run:
```bash
rm -f .env hs_err_pid31320.mdmp
```

- [ ] **Step 4: 確認消失**

Run:
```bash
ls .env hs_err_pid31320.mdmp 2>&1
```

Expected: 兩個 `No such file or directory`

- [ ] **Step 5: 無 commit**（untracked file 刪除不需 commit）

---

### Task 1.3: 強化 .gitignore 規則

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: 讀取現有 .gitignore 找 hs_err_pid 規則**

Run:
```bash
grep -n hs_err_pid .gitignore
```

Expected: `56:hs_err_pid*.log`（只擋 .log，擋不住 .mdmp / .hprof）

- [ ] **Step 2: 修改 line 56**

Use Edit tool:
- old_string: `hs_err_pid*.log`
- new_string: `hs_err_pid*`

- [ ] **Step 3: 驗證規則生效**

Run:
```bash
touch hs_err_pid99999.mdmp
git status --porcelain hs_err_pid99999.mdmp
rm hs_err_pid99999.mdmp
```

Expected: `git status` 輸出空（被 .gitignore 忽略）

- [ ] **Step 4: Commit**

Run:
```bash
git add .gitignore
git commit -m "$(cat <<'EOF'
chore(gitignore): broaden JVM crash dump rule from *.log to *

Old rule only matched hs_err_pid*.log but JVM also emits
.mdmp (Windows minidump) and .hprof (heap dump) files when
crashing. Catch all variants.
EOF
)"
```

---

### Task 1.4: 確認 README/title.ico 狀態

**Files:**
- 由 user 決定：restore or delete `README/title.ico`

- [ ] **Step 1: 看當前狀態**

Run:
```bash
git status README/title.ico
git log --oneline -5 -- README/title.ico
```

預期一：tracked-but-deleted（worktree HEAD 含此 file 但 working tree 沒有）
預期二：已被 dev 的某 commit 刪掉

- [ ] **Step 2: 詢問 user 處理方式**

如 user 要恢復：
```bash
git checkout HEAD -- README/title.ico
```

如 user 要刪除：
```bash
git rm README/title.ico
git commit -m "chore(repo): remove unused README/title.ico icon"
```

預設動作：**詢問 user 後執行**，不可自行決定。

---

## Phase 2: 目錄改名 deploy/ → docker/

### Task 2.1: git mv 整個目錄

**Files:**
- Rename: `deploy/` → `docker/`

- [ ] **Step 1: 確認 deploy/ 內容**

Run:
```bash
ls deploy/
```

Expected:
```
.env.example
crowdsec/
docker-compose.yml
grafana-dashboards.yml
grafana-datasources.yml
grafana-nginx-dashboard.json
promtail-config.yml
```

- [ ] **Step 2: git mv**

Run:
```bash
git mv deploy docker
```

- [ ] **Step 3: 確認 docker/ 內容完整**

Run:
```bash
ls docker/
git status --short | head
```

Expected:
- `docker/` 內含上述 7 個檔案
- `git status` 列出 `R  deploy/xxx -> docker/xxx`（R 表 rename）

- [ ] **Step 4: 確認 docker-compose.yml 內 build context 仍 valid**

Run:
```bash
grep -n 'context:' docker/docker-compose.yml
```

Expected: `context: ..`（指向父目錄即 repo root，**不需修改** — 路徑相對於 docker-compose.yml 所在目錄）

- [ ] **Step 5: 跑 docker compose config 驗證**

Run:
```bash
cd docker && docker compose config > /dev/null && echo "OK" || echo "FAIL"
cd ..
```

Expected: `OK`

如失敗 → 看錯誤訊息，修 build context 或 image 路徑問題。

- [ ] **Step 6: Commit rename**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
chore(repo): rename deploy/ to docker/

International convention: deploy/ is for deployment scripts,
docker/ is for Docker-related assets (compose, configs, etc).

This rename matches industry patterns (Spring Boot, Vercel, etc).
All file paths inside docker/ unchanged; build context in
docker-compose.yml still uses '..' (relative path).

References to deploy/ in CI/CD, deploy.sh, CLAUDE.md will
be updated in subsequent commits.
EOF
)"
```

---

### Task 2.2: 更新 .gitattributes（如有 deploy/ 引用）

**Files:**
- Modify: `.gitattributes`（若有引用）

- [ ] **Step 1: 檢查 .gitattributes 是否有 deploy/ 引用**

Run:
```bash
grep -n 'deploy' .gitattributes 2>&1
```

如無輸出 → 跳過 Task 2.2

如有 → 用 Edit tool 把 `deploy` 改 `docker`

- [ ] **Step 2: 若有改，commit**

Run:
```bash
git add .gitattributes
git commit -m "chore(gitattributes): update deploy/ to docker/ references"
```

---

## Phase 3: Sidecar Image 化

### Task 3.1: 建立 docker/grafana/ 子目錄 + Dockerfile

**Files:**
- Create dir: `docker/grafana/`
- Create: `docker/grafana/Dockerfile`
- Rename: `docker/grafana-datasources.yml` → `docker/grafana/datasources.yml`
- Rename: `docker/grafana-dashboards.yml` → `docker/grafana/dashboards.yml`
- Rename: `docker/grafana-nginx-dashboard.json` → `docker/grafana/nginx-dashboard.json`

- [ ] **Step 1: 建立目錄**

Run:
```bash
mkdir -p docker/grafana
```

- [ ] **Step 2: git mv 3 個 grafana 設定檔**

Run:
```bash
git mv docker/grafana-datasources.yml docker/grafana/datasources.yml
git mv docker/grafana-dashboards.yml docker/grafana/dashboards.yml
git mv docker/grafana-nginx-dashboard.json docker/grafana/nginx-dashboard.json
```

- [ ] **Step 3: 建立 Dockerfile**

Write file `docker/grafana/Dockerfile`:
```dockerfile
# Custom Grafana image for nginxWebUI stack
# Bakes datasources + dashboards into image so deploy needs only compose.yml + .env

FROM grafana/grafana:11.6.0

# Provisioning files
COPY datasources.yml      /etc/grafana/provisioning/datasources/loki.yml
COPY dashboards.yml       /etc/grafana/provisioning/dashboards/default.yml
COPY nginx-dashboard.json /var/lib/grafana/dashboards/nginx-monitor.json

# Standard Grafana ports/env baked in upstream image; no override here
```

- [ ] **Step 4: 本機 build 驗證**

Run:
```bash
cd docker/grafana && docker build -t test-nginxwebui-grafana:latest . && cd ../..
```

Expected: build 成功，無 ERROR。最後行 `Successfully tagged test-nginxwebui-grafana:latest`

- [ ] **Step 5: 啟動測試 container 確認設定就位**

Run:
```bash
docker run --rm -d --name test-grafana -p 3001:3000 test-nginxwebui-grafana:latest
sleep 10
docker exec test-grafana ls /etc/grafana/provisioning/datasources/ /etc/grafana/provisioning/dashboards/ /var/lib/grafana/dashboards/
docker stop test-grafana
docker rmi test-nginxwebui-grafana:latest
```

Expected: 看到 `loki.yml`, `default.yml`, `nginx-monitor.json` 三個檔案

- [ ] **Step 6: Commit**

Run:
```bash
git add docker/grafana/
git commit -m "$(cat <<'EOF'
feat(docker): bake Grafana sidecar image with provisioning baked in

New image: ghcr.io/elf-express/nginxwebui-grafana:<version>

Source: docker/grafana/Dockerfile
Base:   grafana/grafana:11.6.0
Bakes:  datasources.yml, dashboards.yml, nginx-dashboard.json

Allows docker-compose.yml to deploy from any Docker UI tool
(DockHand, Portainer, Komodo, raw CLI) without bind-mounting
local files.
EOF
)"
```

---

### Task 3.2: 建立 docker/promtail/ 子目錄 + Dockerfile

**Files:**
- Create dir: `docker/promtail/`
- Create: `docker/promtail/Dockerfile`
- Rename: `docker/promtail-config.yml` → `docker/promtail/config.yml`

- [ ] **Step 1: 建立目錄**

Run:
```bash
mkdir -p docker/promtail
```

- [ ] **Step 2: git mv 設定檔**

Run:
```bash
git mv docker/promtail-config.yml docker/promtail/config.yml
```

- [ ] **Step 3: 建立 Dockerfile**

Write file `docker/promtail/Dockerfile`:
```dockerfile
# Custom Promtail image for nginxWebUI stack
# Bakes scrape config into image

FROM grafana/promtail:3.5.0

COPY config.yml /etc/promtail/config.yml

# Upstream entrypoint expects -config.file, but our compose
# explicitly passes it; both work
```

- [ ] **Step 4: 本機 build 驗證**

Run:
```bash
cd docker/promtail && docker build -t test-nginxwebui-promtail:latest . && cd ../..
```

Expected: build 成功

- [ ] **Step 5: 啟動測試 container 確認設定就位**

Run:
```bash
docker run --rm -d --name test-promtail test-nginxwebui-promtail:latest -config.file=/etc/promtail/config.yml
sleep 3
docker exec test-promtail cat /etc/promtail/config.yml | head -5
docker stop test-promtail
docker rmi test-nginxwebui-promtail:latest
```

Expected: 看到 `server:` 開頭的 promtail config 內容

- [ ] **Step 6: Commit**

Run:
```bash
git add docker/promtail/
git commit -m "$(cat <<'EOF'
feat(docker): bake Promtail sidecar image with scrape config baked in

New image: ghcr.io/elf-express/nginxwebui-promtail:<version>

Source: docker/promtail/Dockerfile
Base:   grafana/promtail:3.5.0
Bakes:  config.yml (with country/city/asn captures)
EOF
)"
```

---

### Task 3.3: 建立 docker/crowdsec/Dockerfile（設定檔已在子目錄）

**Files:**
- Create: `docker/crowdsec/Dockerfile`
- Existing: `docker/crowdsec/acquis.yml`, `abuseipdb.yaml`, `profiles.yaml`（git mv 已在 Task 2.1 處理）

- [ ] **Step 1: 確認設定檔已在 docker/crowdsec/**

Run:
```bash
ls docker/crowdsec/
```

Expected:
```
abuseipdb.yaml
acquis.yml
profiles.yaml
```

- [ ] **Step 2: 建立 Dockerfile**

Write file `docker/crowdsec/Dockerfile`:
```dockerfile
# Custom CrowdSec image for nginxWebUI stack
# Bakes acquisition + profiles + notification config into image

FROM crowdsecurity/crowdsec:v1.7.8

# CrowdSec config locations (validated against upstream image layout)
COPY acquis.yml      /etc/crowdsec/acquis.yaml
COPY profiles.yaml   /etc/crowdsec/profiles.yaml
COPY abuseipdb.yaml  /etc/crowdsec/notifications/abuseipdb.yaml
```

- [ ] **Step 3: 本機 build 驗證**

Run:
```bash
cd docker/crowdsec && docker build -t test-nginxwebui-crowdsec:latest . && cd ../..
```

Expected: build 成功

- [ ] **Step 4: 啟動測試 container 確認設定就位（無 panic）**

Run:
```bash
docker run --rm -d --name test-crowdsec \
  -e COLLECTIONS="crowdsecurity/nginx" \
  -e BOUNCER_KEY_nginx="dummy-test-key-not-real-32-chars-padding" \
  test-nginxwebui-crowdsec:latest
sleep 15
docker logs test-crowdsec 2>&1 | tail -20
docker stop test-crowdsec
docker rmi test-nginxwebui-crowdsec:latest
```

Expected:
- 看到 `Loading acquisition` / `Loading profiles` 訊息
- **無** `panic` / `fatal` 字串

如有 panic → 看 log 找原因（通常是 yaml syntax 或 env var 缺漏）

- [ ] **Step 5: Commit**

Run:
```bash
git add docker/crowdsec/Dockerfile
git commit -m "$(cat <<'EOF'
feat(docker): bake CrowdSec sidecar image with acquis/profiles/notif baked in

New image: ghcr.io/elf-express/nginxwebui-crowdsec:<version>

Source: docker/crowdsec/Dockerfile
Base:   crowdsecurity/crowdsec:v1.7.8
Bakes:  acquis.yml, profiles.yaml, abuseipdb.yaml

Note: CROWDSEC_BOUNCER_KEY and ABUSEIPDB_API_KEY still come
from .env (correct — secrets shouldn't bake into image).
EOF
)"
```

---

## Phase 4: docker-compose.yml 重構

### Task 4.1: 改 grafana service — 用新 image + 移除 volumes

**Files:**
- Modify: `docker/docker-compose.yml`（grafana service block，約 line 78-95）

- [ ] **Step 1: 讀當前 grafana service block**

Run:
```bash
sed -n '78,95p' docker/docker-compose.yml
```

確認當前 image 為 `grafana/grafana:11.6.0`，volumes 含 3 條 `./grafana-*` mounts

- [ ] **Step 2: 用 Edit tool 改 grafana service**

old_string:
```yaml
  grafana:
    image: grafana/grafana:11.6.0
    container_name: nginxwebui-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana-datasources.yml:/etc/grafana/provisioning/datasources/loki.yml:ro
      - ./grafana-dashboards.yml:/etc/grafana/provisioning/dashboards/default.yml:ro
      - ./grafana-nginx-dashboard.json:/var/lib/grafana/dashboards/nginx-monitor.json:ro
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    depends_on:
      loki:
        condition: service_healthy
```

new_string:
```yaml
  grafana:
    image: ghcr.io/elf-express/nginxwebui-grafana:${NGINX_WEBUI_VERSION:-latest}
    build:
      context: ./grafana
      dockerfile: Dockerfile
    container_name: nginxwebui-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    depends_on:
      loki:
        condition: service_healthy
```

- [ ] **Step 3: 驗證 yaml 仍 valid**

Run:
```bash
cd docker && docker compose config > /dev/null && echo "OK" || echo "FAIL"
cd ..
```

Expected: `OK`

---

### Task 4.2: 改 promtail service — 用新 image + 移除 volumes

**Files:**
- Modify: `docker/docker-compose.yml`（promtail service block，約 line 53-62）

- [ ] **Step 1: 用 Edit tool 改 promtail service**

old_string:
```yaml
  promtail:
    image: grafana/promtail:3.5.0
    container_name: nginxwebui-promtail
    restart: unless-stopped
    volumes:
      - nginxwebui_log:/var/log/nginx:ro
      - ./promtail-config.yml:/etc/promtail/config.yml:ro
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki
```

new_string:
```yaml
  promtail:
    image: ghcr.io/elf-express/nginxwebui-promtail:${NGINX_WEBUI_VERSION:-latest}
    build:
      context: ./promtail
      dockerfile: Dockerfile
    container_name: nginxwebui-promtail
    restart: unless-stopped
    volumes:
      - nginxwebui_log:/var/log/nginx:ro
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki
```

- [ ] **Step 2: 驗證 yaml**

Run:
```bash
cd docker && docker compose config > /dev/null && echo "OK" || echo "FAIL"
cd ..
```

Expected: `OK`

---

### Task 4.3: 改 crowdsec service — 用新 image + 移除 volumes

**Files:**
- Modify: `docker/docker-compose.yml`（crowdsec service block，約 line 99-119）

- [ ] **Step 1: 用 Edit tool 改 crowdsec service**

old_string:
```yaml
  crowdsec:
    image: crowdsecurity/crowdsec:v1.7.8
    container_name: nginxwebui-crowdsec
    restart: unless-stopped
    volumes:
      - nginxwebui_log:/var/log/nginx:ro
      - crowdsec_data:/var/lib/crowdsec/data
      - crowdsec_config:/etc/crowdsec
      - ./crowdsec/acquis.yml:/etc/crowdsec/acquis.yaml:ro
      - ./crowdsec/abuseipdb.yaml:/etc/crowdsec/notifications/abuseipdb.yaml:ro
      - ./crowdsec/profiles.yaml:/etc/crowdsec/profiles.yaml:ro
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

new_string:
```yaml
  crowdsec:
    image: ghcr.io/elf-express/nginxwebui-crowdsec:${NGINX_WEBUI_VERSION:-latest}
    build:
      context: ./crowdsec
      dockerfile: Dockerfile
    container_name: nginxwebui-crowdsec
    restart: unless-stopped
    volumes:
      - nginxwebui_log:/var/log/nginx:ro
      - crowdsec_data:/var/lib/crowdsec/data
      - crowdsec_config:/etc/crowdsec
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

> **注意**：`crowdsec_config:/etc/crowdsec` named volume 保留 — 因為 baked config 會 COPY 進 image，但 CrowdSec runtime state（如 collections cache）仍需 volume。Image rebuild 時 baked config 會覆蓋 volume 中的 baked 副本（CrowdSec 啟動會合併 user config + image config）。

- [ ] **Step 2: 驗證 yaml**

Run:
```bash
cd docker && docker compose config > /dev/null && echo "OK" || echo "FAIL"
cd ..
```

Expected: `OK`

---

### Task 4.4: 移除 --init.admin / --init.pass 預設

**Files:**
- Modify: `docker/docker-compose.yml`（line 21，nginxwebui service environment）

- [ ] **Step 1: 用 Edit tool 改 BOOT_OPTIONS**

old_string:
```yaml
      - BOOT_OPTIONS=--spring.database.type=postgresql --spring.datasource.url=jdbc:postgresql://postgres:5432/nginxwebui --spring.datasource.username=nginxwebui --spring.datasource.password=nginxwebui123 --init.admin=${INIT_ADMIN:-admin} --init.pass=${INIT_PASS:-Admin123}
```

new_string:
```yaml
      - BOOT_OPTIONS=--spring.database.type=postgresql --spring.datasource.url=jdbc:postgresql://postgres:5432/nginxwebui --spring.datasource.username=nginxwebui --spring.datasource.password=nginxwebui123
```

- [ ] **Step 2: 驗證 yaml**

Run:
```bash
cd docker && docker compose config | grep BOOT_OPTIONS
cd ..
```

Expected: BOOT_OPTIONS 輸出**不含** `--init.admin` 與 `--init.pass`

- [ ] **Step 3: 全文搜尋確認移除乾淨**

Run:
```bash
grep -rE 'init\.(admin|pass)' docker/
```

Expected: **無輸出**（如有殘留必須清理）

---

### Task 4.5: 更新 docker/.env.example — 移除 INIT_*

**Files:**
- Modify: `docker/.env.example`

- [ ] **Step 1: 讀當前 .env.example**

Read `docker/.env.example` 完整內容

- [ ] **Step 2: 用 Edit tool 移除 INIT_ADMIN / INIT_PASS 段**

找到類似這段並刪除（具體行號依當前內容）：
```
# 初始管理員帳號（首次啟動時建立）
INIT_ADMIN=admin
INIT_PASS=Admin123
```

改成（加註說明）：
```
# 初始管理員：移除預設後，首次啟動會在 web UI 引導設定密碼
# （這是原作者設計，比預設密碼更安全）
```

- [ ] **Step 3: 確認 .env.example 仍有效**

Run:
```bash
cat docker/.env.example
```

確認剩下的 var 仍齊全：
- `NGINX_WEBUI_VERSION`
- `CROWDSEC_BOUNCER_KEY`
- `ABUSEIPDB_API_KEY`

---

### Task 4.6: Commit Phase 4

- [ ] **Step 1: 整理 staged changes**

Run:
```bash
git diff docker/docker-compose.yml | head -100
git diff docker/.env.example
git status --short
```

確認 diff 內容正確。

- [ ] **Step 2: Commit**

Run:
```bash
git add docker/docker-compose.yml docker/.env.example
git commit -m "$(cat <<'EOF'
refactor(compose): self-contained sidecar images + remove init.* defaults

Three breaking changes in docker/docker-compose.yml:

1. Sidecar services (grafana, promtail, crowdsec) now use custom
   fork-built images (ghcr.io/elf-express/nginxwebui-{grafana,promtail,crowdsec})
   which bake their config files into the image. All './xxx' bind
   mounts for config removed. Compose now deployable from any Docker
   UI tool with just compose.yml + .env.

2. Removed --init.admin and --init.pass from BOOT_OPTIONS. First
   boot now follows original cym1102 design: nginxWebUI's UI guides
   admin password setup. No more 'admin/Admin123' default leak.

3. Updated .env.example to remove INIT_ADMIN / INIT_PASS lines and
   document the new behaviour.

Verification: InitConfig.java:114-116 (calls addAdmin only if both
non-blank) + InitConfig.java:660-674 (addAdmin returns early if any
admin exists) confirms removing args triggers UI wizard on empty DB.
EOF
)"
```

---

## Phase 5: 本機 End-to-End 驗證

### Task 5.1: Maven build 主應用 jar

**Files:** 無修改

- [ ] **Step 1: 跑 mvn clean package**

Run:
```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS，產出 `target/nginxWebUI-5.1.0.jar`（或當前版本號）

如 FAIL → 看錯誤訊息，可能是 worktree merge 後依賴問題。

- [ ] **Step 2: 確認 jar 大小合理**

Run:
```bash
ls -lh target/nginxWebUI-*.jar
```

Expected: 80-150 MB（含依賴的 fat jar）

---

### Task 5.2: docker compose build — 4 image 全 build

**Files:** 無修改

- [ ] **Step 1: 跑 docker compose build**

Run:
```bash
cd docker
docker compose build --no-cache 2>&1 | tail -30
cd ..
```

Expected: 4 個 image 全部 build 成功
- `ghcr.io/elf-express/nginxwebui:<version>` (主應用 from Dockerfile in root)
- `ghcr.io/elf-express/nginxwebui-grafana:<version>` (from docker/grafana/Dockerfile)
- `ghcr.io/elf-express/nginxwebui-promtail:<version>` (from docker/promtail/Dockerfile)
- `ghcr.io/elf-express/nginxwebui-crowdsec:<version>` (from docker/crowdsec/Dockerfile)

如有 image 失敗 → 看 build context / Dockerfile 路徑。

- [ ] **Step 2: 列出 4 個 image 確認存在**

Run:
```bash
docker images | grep -E 'nginxwebui(-grafana|-promtail|-crowdsec)?\s'
```

Expected: 4 行

---

### Task 5.3: 建立測試用 .env + 啟動 stack

**Files:**
- Create: `docker/.env`（測試用，不 commit — 已被 .gitignore line 41 排除）

- [ ] **Step 1: 建立測試 .env**

Run:
```bash
cp docker/.env.example docker/.env
```

- [ ] **Step 2: 填入測試 CROWDSEC_BOUNCER_KEY（隨機字串）**

Use Edit tool on `docker/.env`：找 `CROWDSEC_BOUNCER_KEY=` 行，填入 32+ 字元亂數，例如：
```
CROWDSEC_BOUNCER_KEY=test-only-bouncer-key-32-char-pad
```

ABUSEIPDB_API_KEY 留空或填 dummy（**測試時不會 call AbuseIPDB API**）

- [ ] **Step 3: 啟動 stack**

Run:
```bash
cd docker
docker compose up -d
cd ..
```

Expected: 7 service 全部開始啟動

- [ ] **Step 4: 等 60 秒讓 healthcheck 跑完**

Run:
```bash
sleep 60
```

> 不要用 polling — healthcheck interval 30s，等 60s 至少跑 2 輪

---

### Task 5.4: 驗證 7 個 container 全 healthy

**Files:** 無修改

- [ ] **Step 1: 看 container 狀態**

Run:
```bash
cd docker
docker compose ps
cd ..
```

Expected: 7 個 service 全 `Up` 且狀態為 `healthy`（或無 healthcheck 但 running）：
- nginxwebui — Up (healthy)
- nginxwebui-postgres — Up (healthy)
- nginxwebui-loki — Up (healthy)
- nginxwebui-grafana — Up
- nginxwebui-crowdsec — Up (healthy)
- nginxwebui-promtail — Up
- nginxwebui-bouncer — Up

**任一 service 狀態為 `Restarting` 或 `Exited` → STOP，看 logs 找因。不允許「大部分 OK」就宣稱通過。**

- [ ] **Step 2: 看每個 container 的 last log（健康診斷）**

Run:
```bash
for svc in nginxwebui postgres grafana promtail crowdsec bouncer loki; do
  echo "=== $svc ==="
  docker logs nginxwebui${svc:+-${svc#nginxwebui}} 2>&1 | tail -3
done
```

Expected: 每個 container 都有 startup log，**無** `panic` / `fatal` / `Error starting`

具體要看的訊息：
- nginxwebui: `started nginxWebUI` 或 `Solon` 啟動成功訊息
- postgres: `database system is ready to accept connections`
- grafana: `Listen=:3000`
- promtail: `Started Promtail`
- crowdsec: `Starting processing data`
- bouncer: `Listening on port 8181`
- loki: `module msg="module ready"`

---

### Task 5.5: HTTP 端點驗證

**Files:** 無修改

- [ ] **Step 1: 主應用 nginxWebUI**

Run:
```bash
curl -sI http://localhost:12300/ | head -3
```

Expected:
```
HTTP/1.1 302 Found
Location: /adminPage/login   或   /adminPage/setup
```

如 Location 是 `/adminPage/setup`（或類似 first-run wizard 路徑）→ **證明 --init.* 移除生效，UI 設定精靈跳出來**

- [ ] **Step 2: 用 curl follow redirect 確認 setup wizard 頁可達**

Run:
```bash
curl -sL http://localhost:12300/ | grep -iE '(設定管理員|setup|首次)' | head -3
```

Expected: 看到「設定管理員密碼」或類似 setup 字串

如未跳 setup wizard 而是 login page → **失敗**，--init.* 沒清乾淨或本機 build 有舊 jar 殘留。

- [ ] **Step 3: Grafana**

Run:
```bash
curl -sI http://localhost:3000/ | head -3
```

Expected: `HTTP/1.1 302 Found` redirect 到 `/login`

- [ ] **Step 4: Promtail（內部 only）**

Run:
```bash
docker exec nginxwebui-promtail wget -qO- http://localhost:9080/ready
```

Expected: `ready`

- [ ] **Step 5: Loki**

Run:
```bash
docker exec nginxwebui-loki wget -qO- http://localhost:3100/ready
```

Expected: `ready`

- [ ] **Step 6: CrowdSec LAPI**

Run:
```bash
docker exec nginxwebui-crowdsec cscli version
```

Expected: 看到 `version: v1.7.8` 字串

---

### Task 5.6: 拆 stack 並清 volume

**Files:** 無修改

- [ ] **Step 1: stop + remove containers + volumes**

Run:
```bash
cd docker
docker compose down -v
cd ..
```

> `-v` flag 把 named volumes 一起刪（postgres_data / grafana_data / loki_data / crowdsec_data / crowdsec_config / nginxwebui_data / nginxwebui_log / nginxwebui_geoip）。**Production 不要用 -v**，但測試完要清乾淨。

- [ ] **Step 2: 確認都清完**

Run:
```bash
docker ps -a | grep nginxwebui
docker volume ls | grep nginxwebui
```

Expected: 兩個都無輸出

- [ ] **Step 3: 刪測試 .env**

Run:
```bash
rm docker/.env
```

---

## Phase 6: CI/CD 改造

### Task 6.1: 讀現有 build.yml 結構

**Files:** 無修改，純檢查

- [ ] **Step 1: 讀 .github/workflows/build.yml**

Run:
```bash
cat .github/workflows/build.yml
```

記錄：
- 觸發條件（push tag? workflow_dispatch?）
- build context（哪個 Dockerfile？）
- 推送 registry（ghcr.io? 路徑？）
- 多平台 buildx？

- [ ] **Step 2: 檢查 paths filter 是否含 `deploy/`**

Run:
```bash
grep -n 'deploy/' .github/workflows/build.yml
```

如有 → 後續要改 `docker/`

---

### Task 6.2: 改 build.yml — 用 matrix build 4 image

**Files:**
- Modify: `.github/workflows/build.yml`

⚠️ **這個 task 內容需根據 Task 6.1 讀到的現有結構調整**。以下示範假設原檔是 single-image push 結構。

- [ ] **Step 1: 用 Edit tool 改 build.yml**

詳細 diff 在 plan 執行時依照 Task 6.1 讀到的當前結構展開，整體變更方向：

舊結構（單 image build）：
```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: mvn package -DskipTests
      - uses: docker/build-push-action@v5
        with:
          context: .
          file: Dockerfile
          tags: ghcr.io/elf-express/nginxwebui:${{ github.ref_name }}
          push: true
```

新結構（matrix build 4 image）：
```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        include:
          - name: nginxwebui
            context: .
            dockerfile: Dockerfile
            needs_mvn: true
          - name: nginxwebui-grafana
            context: docker/grafana
            dockerfile: docker/grafana/Dockerfile
            needs_mvn: false
          - name: nginxwebui-promtail
            context: docker/promtail
            dockerfile: docker/promtail/Dockerfile
            needs_mvn: false
          - name: nginxwebui-crowdsec
            context: docker/crowdsec
            dockerfile: docker/crowdsec/Dockerfile
            needs_mvn: false
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 8
        if: matrix.needs_mvn
        uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
      - name: Build jar
        if: matrix.needs_mvn
        run: mvn package -DskipTests
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: ${{ matrix.context }}
          file: ${{ matrix.dockerfile }}
          tags: |
            ghcr.io/elf-express/${{ matrix.name }}:${{ github.ref_name }}
            ghcr.io/elf-express/${{ matrix.name }}:latest
          push: true
          platforms: linux/amd64,linux/arm64
```

- [ ] **Step 2: 改 paths filter（如有）**

如 `on: push: paths:` 含 `deploy/**` → 改成 `docker/**`

- [ ] **Step 3: yaml syntax 驗證**

Run:
```bash
# Option A: 用 act dry-run（如本機有裝 act）
which act && act -l .github/workflows/build.yml 2>&1 | head -10 || echo "act not installed"

# Option B: 用 python yaml parse
python -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))" && echo "YAML OK" || echo "YAML BAD"
```

Expected: 一個方法返回 OK

- [ ] **Step 4: Commit**

Run:
```bash
git add .github/workflows/build.yml
git commit -m "$(cat <<'EOF'
ci(build): matrix-build 4 images and update paths to docker/

Workflow now builds and pushes:
- ghcr.io/elf-express/nginxwebui (main app, requires mvn package)
- ghcr.io/elf-express/nginxwebui-grafana (sidecar)
- ghcr.io/elf-express/nginxwebui-promtail (sidecar)
- ghcr.io/elf-express/nginxwebui-crowdsec (sidecar)

Each image tagged with both git ref name and :latest.
Multi-platform (amd64 + arm64) build via buildx.

Paths filter updated from deploy/** to docker/**.
EOF
)"
```

---

### Task 6.3: 確認 scripts/release.sh 與新 CI 兼容

**Files:**
- Modify: `scripts/release.sh`（如有必要）

- [ ] **Step 1: 讀 release.sh**

Run:
```bash
cat scripts/release.sh
```

檢查重點：
- 是否會 push tag？
- 是否依賴特定 image 名稱？
- 是否驗證 image 已 push 成功？

- [ ] **Step 2: 評估是否需改**

如 release.sh 只是「bump version + commit + tag」，則 **不需修改**（CI matrix 會自動處理 4 image push）。

如 release.sh 有 image 名稱檢查或 manifest inspect，需擴展為 loop 檢查 4 image：

```bash
for img in nginxwebui nginxwebui-grafana nginxwebui-promtail nginxwebui-crowdsec; do
  docker manifest inspect ghcr.io/elf-express/$img:$VERSION > /dev/null || {
    echo "Image $img:$VERSION not found in registry"
    exit 1
  }
done
```

- [ ] **Step 3: 如有改，commit**

Run:
```bash
git add scripts/release.sh
git commit -m "ci(release): support 4-image push verification"
```

如無改，跳過 commit。

---

## Phase 7: deploy.sh 簡化

### Task 7.1: 簡化 deploy.sh — 移除 sidecar cp 邏輯

**Files:**
- Modify: `deploy.sh`

- [ ] **Step 1: 讀現有 deploy.sh**

Run:
```bash
cat deploy.sh
```

確認當前邏輯（sparse-checkout deploy/ → cp 8 個檔案到 /opt/nginxwebui）

- [ ] **Step 2: 用 Write tool 完全重寫**

Write file `deploy.sh`:
```bash
#!/bin/bash
# Nginx Web UI 一鍵部署腳本
# 用法：bash deploy.sh
#
# 本腳本：
# 1. 從 GitHub 拉取 docker/ 目錄（sparse-checkout）
# 2. 複製 docker-compose.yml 與 .env.example 到 /opt/nginxwebui
# 3. 提醒使用者編輯 .env 填入金鑰
# 4. 啟動 docker compose stack
#
# 設定檔已 bake 進 sidecar image，本腳本不再 copy 個別 yml/json。

set -e

REPO="https://github.com/elf-express/nginxWebUI.git"
INSTALL_DIR="/opt/nginxwebui"
TMP_DIR="/tmp/nginxwebui-deploy-$$"

echo "=== Nginx Web UI 部署腳本 ==="
echo ""

# 1. 檢查必要工具
for cmd in git docker; do
  if ! command -v $cmd &>/dev/null; then
    echo "[錯誤] 找不到 $cmd，請先安裝" >&2
    exit 1
  fi
done

if ! docker compose version &>/dev/null; then
  echo "[錯誤] 找不到 docker compose，請安裝 Docker Compose V2" >&2
  exit 1
fi

# 2. 判斷新裝還是升版
if [ -f "$INSTALL_DIR/docker-compose.yml" ]; then
  MODE="upgrade"
  echo "[升版模式] 偵測到現有安裝：$INSTALL_DIR"
else
  MODE="install"
  echo "[全新安裝] 目標目錄：$INSTALL_DIR"
fi

# 3. 從 GitHub sparse-checkout 拉 docker/ 目錄
echo ""
echo ">>> 從 GitHub 拉取部署檔案..."
rm -rf "$TMP_DIR"
git clone --depth 1 --filter=blob:none --sparse "$REPO" "$TMP_DIR"
cd "$TMP_DIR"
git sparse-checkout set docker
cd -

# 4. 建立安裝目錄
mkdir -p "$INSTALL_DIR"

# 5. 複製 compose 與 .env 範例（不覆蓋現有 .env）
echo ""
echo ">>> 複製部署檔案到 $INSTALL_DIR..."
cp "$TMP_DIR/docker/docker-compose.yml" "$INSTALL_DIR/"

if [ ! -f "$INSTALL_DIR/.env" ]; then
  cp "$TMP_DIR/docker/.env.example" "$INSTALL_DIR/.env"
  echo "[注意] 已建立 .env，請稍後編輯填入金鑰"
  NEED_ENV=true
else
  echo "[保留] .env 已存在，不覆蓋"
  NEED_ENV=false
fi

# 6. 清理暫存
rm -rf "$TMP_DIR"

# 7. 提醒編輯 .env
echo ""
echo "=== 部署檔案就緒 ==="
ls -la "$INSTALL_DIR/"
echo ""

if [ "$NEED_ENV" = true ]; then
  echo ">>> 請編輯 .env 填入金鑰："
  echo "    nano $INSTALL_DIR/.env"
  echo ""
  read -p "編輯完成後按 Enter 繼續啟動，或按 Ctrl+C 稍後手動啟動..."
fi

# 8. Pull image + 啟動
echo ""
echo ">>> 拉取最新 image..."
cd "$INSTALL_DIR"
docker compose pull

echo ""
echo ">>> 啟動 Docker Stack..."
docker compose up -d

echo ""
echo ">>> 等待服務啟動 (60s)..."
sleep 60
docker compose ps

echo ""
echo "=== 部署完成 ==="
HOST_IP=$(hostname -I | awk '{print $1}')
echo "  Nginx Web UI：http://${HOST_IP}:12300"
echo "  Grafana：     http://${HOST_IP}:3000 (admin/admin)"
echo ""
echo "  首次訪問 nginxWebUI 會引導你設定管理員密碼。"
```

- [ ] **Step 3: shellcheck 驗證**

Run:
```bash
shellcheck deploy.sh || echo "(shellcheck not installed, skipping)"
```

Expected: 無 error（warning 可接受）

- [ ] **Step 4: 模擬 dry-run（不真的執行）**

Run:
```bash
bash -n deploy.sh && echo "Syntax OK" || echo "Syntax FAIL"
```

Expected: `Syntax OK`

- [ ] **Step 5: Commit**

Run:
```bash
git add deploy.sh
git commit -m "$(cat <<'EOF'
chore(deploy.sh): simplify after sidecar images bake config

deploy.sh now only copies docker-compose.yml + .env.example since
sidecar images self-contain their configs. Removes 6 cp commands
and the crowdsec/ subdirectory mkdir.

Also updates sparse-checkout target from 'deploy' to 'docker' to
match the renamed directory.

docker compose pull added before up to ensure latest image.
EOF
)"
```

---

## Phase 8: 文件更新

### Task 8.1: CLAUDE.md 更新 — 路徑改名

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 找所有 deploy/ 引用**

Run:
```bash
grep -n 'deploy/' CLAUDE.md
```

- [ ] **Step 2: 用 Edit tool replace_all=true 改 deploy/ → docker/**

(具體 old_string 視 CLAUDE.md 當前內容；用 `replace_all: true` 一次改完)

Edit operations needed for each occurrence — use Edit tool with `replace_all: true`：
- `deploy/` → `docker/`
- `deploy 目錄` → `docker 目錄`
- `cd deploy &&` → `cd docker &&`

- [ ] **Step 3: 驗證沒漏**

Run:
```bash
grep -nE 'deploy/(\\.env|docker-compose|grafana|promtail|crowdsec)' CLAUDE.md
```

Expected: 無輸出

---

### Task 8.2: CLAUDE.md 更新 — 移除 init.admin / init.pass 段

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 找 init.admin / init.pass 段**

Run:
```bash
grep -nE 'init\.(admin|pass)' CLAUDE.md
```

- [ ] **Step 2: 用 Edit tool 改寫該段**

找類似這段：
```markdown
**跳過初始化精靈（直接帶入管理員）：**

```bash
java -jar target/nginxWebUI-5.0.3.jar \
     --init.admin=admin \
     --init.pass=admin123 \
     --init.api=true     # 同時開啟 API 呼叫權限
```

> `--init.*` 只在資料庫**還沒有任何管理員**時生效；之後請改用網頁的「管理員管理」修改。
```

改成：
```markdown
**自動化部署用：跳過初始化精靈直接帶入管理員（選用）**

```bash
java -jar target/nginxWebUI-5.1.0.jar \
     --init.admin=admin \
     --init.pass=YourStrongPasswordHere \
     --init.api=true
```

> ⚠️ `--init.*` 只在資料庫**還沒有任何管理員**時生效（[InitConfig.java:660](src/main/java/com/cym/config/InitConfig.java#L660)）。如不指定，**首次啟動會在 UI 引導設定密碼**，這是更安全的設計。Docker Compose 預設不帶 `--init.*` 參數。
```

---

### Task 8.3: CLAUDE.md 更新 — Docker 章節說明 sidecar image bake

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 找 Docker 部署章節**

Run:
```bash
grep -n '## 部署方式\|## 方式 [ABC]\|Docker' CLAUDE.md | head -10
```

- [ ] **Step 2: 在「方式 B：Docker Compose Stack」段加說明**

用 Edit tool 在合適位置插入：

```markdown
**Stack 是 self-contained**：
- `docker/docker-compose.yml` + `.env` 兩個檔案就能在任何 Docker 工具部署
- 所有 sidecar 設定檔（Grafana datasources/dashboards、Promtail config、CrowdSec acquis/profiles）已 **bake 進對應 image**（ghcr.io/elf-express/nginxwebui-grafana 等）
- 不再需要本機 clone repo 或 SSH copy 設定檔
- Stack 由 4 個 fork-built image 組成（主應用 + 3 個 sidecar），CI 自動 multi-platform build + push
```

---

### Task 8.4: CLAUDE.md 更新 — Release 章節說明多 image

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 找 Release 流程章節**

Run:
```bash
grep -n '## Release\|ghcr.io image' CLAUDE.md
```

- [ ] **Step 2: 加說明 4 image build**

用 Edit tool 在「ghcr.io image」段內加：

```markdown
**4 個 image 同步打 tag**：

每次 `scripts/release.sh x.y.z` + push tag 觸發 CI 後，**4 個 image 同時 build + push**：
- `ghcr.io/elf-express/nginxwebui:x.y.z` + `:latest`（主應用）
- `ghcr.io/elf-express/nginxwebui-grafana:x.y.z` + `:latest`
- `ghcr.io/elf-express/nginxwebui-promtail:x.y.z` + `:latest`
- `ghcr.io/elf-express/nginxwebui-crowdsec:x.y.z` + `:latest`

`docker-compose.yml` 用 `${NGINX_WEBUI_VERSION:-latest}` 引用版本，所有 4 image 走同一個 version 變數。
```

---

### Task 8.5: Commit 文件更新

- [ ] **Step 1: 看 diff**

Run:
```bash
git diff CLAUDE.md | head -100
```

確認改動範圍合理。

- [ ] **Step 2: Commit**

Run:
```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(claude.md): update for docker/ rename + sidecar image bake + init.* removal

Three documentation updates aligned with code changes:

1. Path rename: all 'deploy/' references → 'docker/'
2. Sidecar image bake: explain compose.yml is now self-contained
   (no local config files needed), 4 images get built per release
3. init.* removal: --init.admin / --init.pass now opt-in for
   automation. Default first-boot triggers UI password setup.
EOF
)"
```

---

## Phase 9: Worktree → Dev Branch Merge

### Task 9.1: 看完整 worktree commit 史

**Files:** 無修改

- [ ] **Step 1: 看 worktree commit log**

Run:
```bash
git log --oneline origin/dev..HEAD
```

Expected: 看到 Phase 1-8 的多個 commits

- [ ] **Step 2: 看 worktree vs dev 的 file diff summary**

Run:
```bash
git diff --stat origin/dev..HEAD
```

Expected: 看到改了哪些 files、各別 +/-

- [ ] **Step 3: User review checkpoint**

⚠️ **STOP** — 將完整 diff 給 user 看，等 user 確認再繼續 push。

Run:
```bash
git diff origin/dev..HEAD > /tmp/worktree-diff.patch
echo "Full diff saved to /tmp/worktree-diff.patch ($(wc -l < /tmp/worktree-diff.patch) lines)"
```

User 看完批准 → 進 Task 9.2

---

### Task 9.2: Push worktree branch + Merge to dev

**Files:** 無修改

- [ ] **Step 1: Push worktree branch**

Run:
```bash
git push -u origin worktree-zany-tickling-dragon
```

Expected: push 成功，GitHub 上能看到新 branch

- [ ] **Step 2: Fast-forward dev**

兩種做法，依 user 偏好：

**做法 A: Local fast-forward + push（推薦）**

Run:
```bash
git checkout dev
git pull origin dev
git merge --ff-only worktree-zany-tickling-dragon
git push origin dev
```

Expected: dev 多了 worktree 那批 commits，無 merge commit

**做法 B: PR + 等審查**

Run:
```bash
gh pr create --base dev --head worktree-zany-tickling-dragon \
  --title "refactor(deploy): rename deploy→docker, sidecar image bake, remove init.* defaults" \
  --body "$(cat docs/superpowers/plans/2026-05-23-deploy-stack-refactor.md | head -50)"
```

依 user 偏好選一個。

- [ ] **Step 3: 確認 CI 通過**

Run:
```bash
gh run list --workflow=build.yml --limit=3
```

Expected: 最新 run 為 `completed success`

如 CI 失敗 → 看 log，回到 Phase 6 修。

---

## Phase 10: Production Server 部署驗證

### Task 10.1: User 先 rotate ABUSEIPDB key

**Files:** 無修改（user 操作）

- [ ] **Step 1: User 開 https://www.abuseipdb.com/account/api**

⚠️ **STOP** — 等 user 確認：
1. 已在 AbuseIPDB 後台 revoke 舊 key
2. 已生成新 key 並複製

舊 key（前次對話暴露）：`19f264a4c292b3ec22109d4074dcad0a09ede1c73e1c484a04bf79fd4fb0d2a003a2a80e2d7a0072`

User 必須確認「已 rotate」才繼續。

---

### Task 10.2: 砍掉現有 server stack

**Files:** 無修改（DockHand UI 操作）

- [ ] **Step 1: 確認現有 stack 無業務資料**

User 在 DockHand → Stacks → nginxwebui → check：
- 主應用 uptime 是否 < 1 hour（< 數 mins 更好）
- 是否曾透過 UI 設定過任何反代規則 → 如**有**業務資料，先 export config 或 dump postgres
- 是否 Loki 已收集任何重要 log

⚠️ **如有業務資料** → STOP，先備份再繼續。

- [ ] **Step 2: 在 DockHand 砍 stack + 砍 volume**

DockHand → Stacks → nginxwebui → Stop → Remove → 勾選「Remove volumes」

或 SSH 進 server：
```bash
cd /opt/nginxwebui  # 或 stack 在 DockHand 內的目錄
docker compose down -v
```

- [ ] **Step 3: 確認 volume 都刪了**

Run on server:
```bash
docker volume ls | grep nginxwebui
```

Expected: 無輸出

---

### Task 10.3: Production server 重新部署 stack

**Files:** 無修改（DockHand UI 操作）

- [ ] **Step 1: DockHand → Stacks → New Stack（Web editor 模式）**

User 在 DockHand UI：
- Name: `nginxwebui`
- Editor 模式：Web editor（不是 Git，依先前決議）
- 貼上新版 `docker/docker-compose.yml` 內容（從 dev branch 抓 raw 內容）

- [ ] **Step 2: 設定環境變數**

在 DockHand stack 的 Environment 區或上傳 .env：
```
NGINX_WEBUI_VERSION=latest
CROWDSEC_BOUNCER_KEY=<server-side 隨機生成 32+ 字元，例如 openssl rand -hex 32>
ABUSEIPDB_API_KEY=<剛 rotate 完的新 key>
```

> Production 不要用 `latest` tag，建議釘版本（如 `5.1.0`），但首次部署用 `latest` 可接受。

- [ ] **Step 3: Deploy**

點 DockHand 的 Deploy / Up 按鈕

- [ ] **Step 4: 等 60-90 秒讓所有 healthcheck 跑完**

User 在 DockHand UI 觀察 container 狀態。

---

### Task 10.4: 驗證 production stack 全 healthy

**Files:** 無修改

- [ ] **Step 1: 7 個 container 全 running + healthy**

User 在 DockHand → Containers，確認看到：
- nginxwebui — running, healthy ✓
- nginxwebui-postgres — running, healthy ✓
- nginxwebui-loki — running, healthy ✓
- nginxwebui-grafana — running ✓
- nginxwebui-crowdsec — running, healthy ✓
- nginxwebui-promtail — running ✓
- nginxwebui-bouncer — running ✓

**任一 restarting / exited / created → STOP，看 log debug**

- [ ] **Step 2: 訪問 web UI**

User 用瀏覽器訪問 `http://<server-ip>:12300/`

Expected: **跳「設定管理員密碼」精靈**（不是登入頁）

如跳登入頁 → init.* 殘留，回頭看 Task 4.4 是否清乾淨。

- [ ] **Step 3: 設定強密碼**

User 在 UI 設新密碼，登入。

- [ ] **Step 4: 訪問 Grafana**

`http://<server-ip>:3000/`

Expected: Grafana 登入頁，用 `admin/admin` 登入後**看到 Nginx Monitor dashboard 預先載入**（bake 進 image 的效果）

如 dashboard 沒出現 → image build 沒 COPY 對位置，看 docker exec grafana 確認 `/var/lib/grafana/dashboards/nginx-monitor.json` 存在

---

### Task 10.5: Production 驗證通過後 release

**Files:** 無修改

- [ ] **Step 1: Tag release**

Local:
```bash
git checkout dev
scripts/release.sh 5.2.0   # 假設下一版號
git push origin dev --tags
```

- [ ] **Step 2: 等 CI 完成 4 image push**

```bash
gh run watch --workflow=build.yml
```

- [ ] **Step 3: 驗證 4 image 都在 registry**

```bash
for img in nginxwebui nginxwebui-grafana nginxwebui-promtail nginxwebui-crowdsec; do
  echo "=== $img ==="
  docker manifest inspect ghcr.io/elf-express/$img:5.2.0 > /dev/null && echo "OK" || echo "MISSING"
done
```

Expected: 全部 OK

- [ ] **Step 4: Fast-forward master**

```bash
git push origin dev:master
```

- [ ] **Step 5: 在 production server 升版到 5.2.0**

User 在 DockHand stack `.env` 改 `NGINX_WEBUI_VERSION=5.2.0`，redeploy。

---

## Verification Summary

每個 Phase 完成都必須通過對應的驗證指令。**任一驗證失敗都要回頭修，不允許口頭宣稱完成**。

| # | Phase | 關鍵驗證命令 | 通過條件 |
|---|---|---|---|
| V0 | Phase 0 | `git status --porcelain` | 空輸出（clean tree） |
| V1 | Phase 1 | `ls grafana-*.yml promtail-config.yml crowdsec/ 2>&1` | 全部 `No such file` |
| V2 | Phase 1 | `git status` | clean |
| V3 | Phase 2 | `cd docker && docker compose config > /dev/null && echo OK` | OK |
| V4 | Phase 3 | `docker images \| grep -E 'nginxwebui(-grafana\|-promtail\|-crowdsec)?\s'` | 4 行 |
| V5 | Phase 4 | `grep -rE 'init\.(admin\|pass)' docker/` | 無輸出 |
| V6 | Phase 5 | `docker compose ps` 60s 後 | 7 services 全 running/healthy |
| V7 | Phase 5 | `curl -sL http://localhost:12300/ \| grep -iE '設定管理員\|setup'` | 看到 wizard 字串 |
| V8 | Phase 6 | `python -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"` | 不 raise |
| V9 | Phase 7 | `bash -n deploy.sh` | Syntax OK |
| V10 | Phase 8 | `grep -nE 'deploy/(\\.env\|docker-compose)' CLAUDE.md` | 無輸出 |
| V11 | Phase 9 | `gh run list --workflow=build.yml --limit=1` | success |
| V12 | Phase 10 | Production `docker compose ps` | 7 healthy |
| V13 | Phase 10 | Production curl :12300 | 跳 setup wizard |
| V14 | Phase 10 | Grafana dashboard | Nginx Monitor 預載入顯示 |

---

## Risks & Mitigations

| 風險 | 影響 | 緩解 |
|---|---|---|
| Worktree HEAD (debe171) 與 dev HEAD (a9301ba) 有 4 個 commit 差距 | merge 時可能 conflict | Phase 0 Task 0.1 step 3 已先 merge dev 進來 |
| `git mv deploy/ docker/` 後 CI / 其他 scripts 引用 deploy/ 漏改 | CI 失敗 | Phase 6 + Phase 7 + Phase 8 全面 grep 並替換 |
| Sidecar image build 失敗（特別是 CrowdSec 對 config layout 嚴格） | Phase 3 卡住 | 每個 Dockerfile 都附啟動測試 step（Task 3.1-3.3 step 5），第一時間捕捉問題 |
| ABUSEIPDB key 已在前次對話暴露 | 攻擊者可用 quota | Phase 10 Task 10.1 強制 user 先 rotate |
| Production 砍 stack 會丟 volume 資料 | 業務資料遺失 | Phase 10 Task 10.2 step 1 確認 stack uptime + 有無業務資料 |
| 多平台 buildx 在 ARM 上跑 sidecar image 可能 base image 不支援 ARM | ARM platform 部署失敗 | grafana / promtail / crowdsec 上游 image 都有 ARM build，但執行時驗證 |
| Web 端 setup wizard 邏輯 nginxWebUI 5.x 版本可能行為改變 | UI 沒跳 wizard 而是 500 | Phase 5 step 5.5.2 用 curl follow redirect 驗證；如失敗，回 InitConfig.java 看是否 5.x 有新邏輯 |
| DockHand 自動拉 latest image 機制可能 cache | redeploy 沒用到新版 | Phase 10 Task 10.3 step 3 之前確認 image pull policy = always |

---

## Self-Review Checklist

執行此 plan 前我 (Claude) 已對照 spec self-review：

**1. Spec coverage**：
- ✅ Sidecar image bake → Phase 3
- ✅ deploy/ → docker/ 改名 → Phase 2
- ✅ 根目錄重複檔清理 → Phase 1
- ✅ --init.* 移除 → Phase 4.4
- ✅ CI matrix → Phase 6
- ✅ deploy.sh 簡化 → Phase 7
- ✅ CLAUDE.md 更新 → Phase 8
- ✅ Production 部署驗證 → Phase 10

**2. Placeholder scan**：
- ✅ 無 TBD / TODO / "fill in details"
- ⚠️ Task 6.2 標記了「依 Task 6.1 讀到的當前結構展開」— 這是因為 build.yml 內容會依當前版本變動，但**已給出明確的 old/new 結構模板**，執行時 follow 模板即可
- ✅ 每個 step 都有具體 file path + command + expected output

**3. Type consistency**：
- ✅ Sidecar image 名稱：`nginxwebui-grafana` / `nginxwebui-promtail` / `nginxwebui-crowdsec` — 全 plan 一致
- ✅ Build context 路徑：`docker/grafana` / `docker/promtail` / `docker/crowdsec` — 全 plan 一致
- ✅ Image registry：`ghcr.io/elf-express/<name>` — 全 plan 一致
- ✅ Version 引用：`${NGINX_WEBUI_VERSION:-latest}` — compose 與 CI 一致

**4. 工時預估**（含驗證）：

| Phase | 工時 |
|---|---|
| 0 | 10 min |
| 1 | 15 min |
| 2 | 10 min |
| 3 | 30 min |
| 4 | 20 min |
| 5 | 30 min |
| 6 | 30 min |
| 7 | 15 min |
| 8 | 25 min |
| 9 | 15 min |
| 10 | 30 min（user 操作） |
| **Total** | **~230 min (3.8 hours)** |

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-23-deploy-stack-refactor.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 我為每個 Task 派一個 fresh subagent，每完成一個 Task 後我 review，無關工作不污染主 context。適合這種 multi-phase + 多檔案改動的大改造。

**2. Inline Execution** — 在當前 session 連續執行所有 Phase，checkpoint 在 Phase 邊界停下來 review。Context 持續累積，但連續性高。

**Which approach?**
