# Sidecar Image CI + Release v5.1.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 補完前一個 plan (`docs/superpowers/plans/2026-05-23-deploy-stack-refactor.md`) 漏做的 Phase 6 CI 部分,讓 `.github/workflows/build.yml` 用 matrix 同時 build + push 4 個 image,接著發 v5.1.1 把新架構真正推上 ghcr.io,最後指導 DockHand 端重新部署到全 healthy。

**Architecture:** 一條線:CI matrix → tag v5.1.1 → 4 image 上 ghcr.io → DockHand 拉新 compose + 新 image → 7 container healthy。不改 sidecar Dockerfile(已存在於 dev)、不改 compose(已重構完成)、不改 release.sh(它跟 image 名稱無關,純 bump pom + tag)。

**Tech Stack:** GitHub Actions matrix build, docker/build-push-action@v6, ghcr.io, Docker Compose v2, Bash, DockHand UI (https://github.com/Finsys/dockhand)

---

## Context

**問題現況(2026-05-25 prod DockHand 部署失敗):**
- nginxwebui-crowdsec 不斷重啟,log:`read /etc/crowdsec/profiles.yaml: is a directory`
- 之前的 promtail mount 錯誤(`not a directory`)同樣 root cause
- 兩個都是 docker daemon 對「不存在的 bind source 自動建空目錄」所產生的副作用

**根本原因鏈(已從 repo 證據完全驗證,非猜測):**
1. 前一個 plan `2026-05-23-deploy-stack-refactor.md` 把 sidecar config 重構成 baked image 架構(commits `1879022` / `3669edf` / `6547e97`),但只執行到 Phase 5 就停下,**Phase 6 CI matrix build 沒做**
2. tag `v5.1.0` 指向 commit `debe171`(`git tag --contains 6547e97` 返回空 → sidecar 改動沒被任何 tag 涵蓋)
3. `.github/workflows/build.yml` 至今只 build 主 `ghcr.io/elf-express/nginxwebui`,從沒 build 過 `nginxwebui-grafana` / `-promtail` / `-crowdsec`
4. DockHand 拉 `:latest`(因 `.env` 沒設 `NGINX_WEBUI_VERSION`),拉到舊架構的 v5.1.0 主 image,而舊架構的 compose 還需要 host 帶 7 個 config 檔(`./promtail-config.yml`、`./crowdsec/profiles.yaml` 等)
5. DockHand stack 目錄只有 compose.yml + .env,docker daemon 把缺失的 bind source 自動建成空目錄,結果:
   - promtail container:把空目錄掛到 image 內既有的檔案 `/etc/promtail/config.yml` → "not a directory"
   - crowdsec container:cscli 讀 `/etc/crowdsec/profiles.yaml` 發現是目錄 → "is a directory"

**為什麼這個 plan 不重複前一個 plan 的工作:**
前一個 plan 的 Phase 1-5 全部 commit 已在 dev 分支:
- `c73181e` (Phase 1: 倉庫清理)
- `1879022` (Phase 2: deploy/→docker/ rename)
- `3669edf` (Phase 3: sidecar Dockerfiles)
- `6547e97` (Phase 4: compose 重構 + .env.example 更新)

只是 Phase 6 (CI matrix) 從未執行,所以前述改動沒被任何 release tag 帶上 ghcr.io。本 plan 從 Phase 6 接續做完,加上發 v5.1.1 + DockHand 重部署驗證。

**預期結果:**
- ghcr.io 上有 4 個 `:5.1.1` + `:latest` image
- DockHand 上 stack 拉新 compose,7 container 全 healthy
- 首次部署若 DB 為空,nginxWebUI 跳 UI 設定密碼精靈(原作者設計)

**前置條件:**
- worktree 已在 dev 分支
- 本機 Docker / Maven / Node 可用
- GitHub Actions secrets `GITHUB_TOKEN` 已有 packages:write 權限(原 workflow 已有)
- DockHand 端 prod stack 目前可被砍掉重建(已知會 `down -v` 清 volume)

---

## File Structure

### 修改檔案
- `.github/workflows/build.yml` — 整個 docker job 改 matrix

### 新建檔案
- 無

### 不動檔案
- `docker/docker-compose.yml`(commit `6547e97` 已改完)
- `docker/grafana/Dockerfile` / `docker/promtail/Dockerfile` / `docker/crowdsec/Dockerfile`(commit `3669edf` 已建)
- `docker/.env.example`(commit `6547e97` 已改)
- `scripts/release.sh`(跟 image 名稱解耦,不需改)
- `Dockerfile`(主應用 Dockerfile,Phase 6 改的 CI 已涵蓋)

### Prod 端(DockHand 機器)動作
- 替換 stack 目錄內的 `docker-compose.yml`
- `docker compose down -v` 清掉舊的空目錄污染 named volume
- `docker compose pull && up -d`

---

## Phase 1: CI Matrix Build 改造

### Task 1.1: 改 .github/workflows/build.yml — matrix 同時 build 4 image

**Files:**
- Modify: `.github/workflows/build.yml`(整個 `docker:` job 重寫)

整段以 matrix 改寫,新增 4 個 image 條目:
```yaml
strategy:
  fail-fast: false
  matrix:
    image:
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
```

Build & Push step 用 `${{ matrix.image.name }}` 動態組 tag;`Set up JDK 8` / `Build JAR` / `pom verify` 加 `if: matrix.image.needs_mvn` 條件,只在主 image 跑。Cache scope per-image 避免 cross-image cache poisoning。

驗證:`python -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml'))"` 應回 OK。

Commit message:`ci(build): matrix-build all 4 images on tag push`

### Task 1.2: 本機 smoke test — 確認 4 個 Dockerfile 都能 build

```bash
mvn clean package -DskipTests -q
docker build -t smoke-nginxwebui:latest -f Dockerfile .
docker build -t smoke-nginxwebui-grafana:latest -f docker/grafana/Dockerfile docker/grafana
docker build -t smoke-nginxwebui-promtail:latest -f docker/promtail/Dockerfile docker/promtail
docker build -t smoke-nginxwebui-crowdsec:latest -f docker/crowdsec/Dockerfile docker/crowdsec
```

驗證 baked config:
```bash
docker run --rm --entrypoint sh smoke-nginxwebui-grafana:latest -c 'ls /etc/grafana/provisioning/datasources/loki.yml /etc/grafana/provisioning/dashboards/default.yml /var/lib/grafana/dashboards/nginx-monitor.json'
docker run --rm --entrypoint sh smoke-nginxwebui-promtail:latest -c 'ls /etc/promtail/config.yml'
docker run --rm --entrypoint sh smoke-nginxwebui-crowdsec:latest -c 'ls /etc/crowdsec/acquis.yaml /etc/crowdsec/profiles.yaml /etc/crowdsec/notifications/abuseipdb.yaml'
```

清掉 smoke image:`docker rmi smoke-nginxwebui:latest smoke-nginxwebui-grafana:latest smoke-nginxwebui-promtail:latest smoke-nginxwebui-crowdsec:latest`

無 commit。

---

## Phase 2: Release v5.1.1

### Task 2.1: 跑 release.sh 5.1.1

```bash
git branch --show-current  # 應為 dev
git status --porcelain     # 應空
scripts/release.sh 5.1.1
git log --oneline -3
git tag --sort=-v:refname | head -3
```

預期 log 顯示 `chore(release): bump version to 5.1.1`,tag 列表第一個 `v5.1.1`。

### Task 2.2: Push dev + tag,觸發 CI

```bash
git push origin dev --tags
```

開 `https://github.com/elf-express/nginxWebUI/actions` 看 workflow 啟動。應有 1 個 `Build JAR` job + 4 個 `Build & Push Docker image (...)` matrix job。預估 8-15 分鐘。可用 `gh run watch` 監控。

### Task 2.3: 驗證 ghcr.io 上 4 個 image 都齊

```bash
for img in nginxwebui nginxwebui-grafana nginxwebui-promtail nginxwebui-crowdsec; do
  echo "=== $img ==="
  for tag in 5.1.1 latest; do
    if docker manifest inspect ghcr.io/elf-express/$img:$tag > /dev/null 2>&1; then
      echo "  ✓ :$tag exists"
    else
      echo "  ✗ :$tag MISSING"
    fi
  done
done
```

預期 8 行全部 `✓ exists`。並確認 multi-platform manifest:
```bash
docker manifest inspect ghcr.io/elf-express/nginxwebui-crowdsec:5.1.1 | grep -E '"architecture"|"os"'
```
應看到 `amd64` 跟 `arm64` 各一。

### Task 2.4: master fast-forward

```bash
git push origin dev:master
```

如 non-fast-forward → 停下呼叫 user。

---

## Phase 3: DockHand Prod 端重部署

> ⚠️ 此 phase 不在 worktree 機器執行,在 prod DockHand 機器(`/mnt/HDD/dockhand/dockhand_data/stacks/prod/nginxwebui/`)操作。

### Task 3.1: 備份現有 stack 狀態(預防回滾)

```bash
cd /mnt/HDD/dockhand/dockhand_data/stacks/prod/nginxwebui
docker compose ps > /tmp/nginxwebui-before-state.txt
cp docker-compose.yml /tmp/nginxwebui-compose-backup-$(date +%Y%m%d).yml
cp .env /tmp/nginxwebui-env-backup-$(date +%Y%m%d)
```

### Task 3.2: 拉新 compose + 設定 .env 版本釘選

```bash
curl -fsSL -o docker-compose.yml.new \
  https://raw.githubusercontent.com/elf-express/nginxWebUI/v5.1.1/docker/docker-compose.yml
diff -u docker-compose.yml docker-compose.yml.new | head -50
mv docker-compose.yml.new docker-compose.yml
```

編輯 `.env` 加(或修改):
```
NGINX_WEBUI_VERSION=5.1.1
CROWDSEC_BOUNCER_KEY=DHhymIrfGtVpD9RMA3lmbHgAQJKG02gSLHFOQwH7JBQ
ABUSEIPDB_API_KEY=
```

驗證:
```bash
docker compose config > /dev/null && echo "OK" || echo "FAIL"
docker compose config | grep -E 'image:' | sort -u
```
所有 image 應為 `ghcr.io/elf-express/...:5.1.1` 或 standard images。

### Task 3.3: 清舊 stack(含污染的 named volume)

⚠️ 需 user 確認:`docker compose down -v` 會刪 PostgreSQL admin 資料庫 + 所有 sidecar 狀態。

```bash
docker compose down -v
# 額外清 docker daemon 之前自動建的空目錄
ls -la promtail-config.yml grafana-*.yml grafana-*.json crowdsec/ 2>&1 | grep -E "^d|^-"
rm -rf promtail-config.yml grafana-datasources.yml grafana-dashboards.yml grafana-nginx-dashboard.json crowdsec/
ls -la  # 應只剩 docker-compose.yml 跟 .env
```

### Task 3.4: Pull 新 image + 啟動

```bash
docker compose pull
docker compose up -d
sleep 90
```

### Task 3.5: 驗證 7 個 container 全 healthy

```bash
docker compose ps
# 7 個 service 全 Up;有 healthcheck 的應為 (healthy);無 Restarting / Exited / 重啟循環

for svc in nginxwebui postgres loki grafana promtail crowdsec bouncer; do
  case $svc in
    bouncer) container=nginxwebui-bouncer ;;
    *) container=nginxwebui${svc:+-$svc}; [ "$svc" = nginxwebui ] && container=nginxwebui ;;
  esac
  echo "=== $container ==="
  docker logs $container 2>&1 | tail -5
  echo ""
done
# 無 panic / fatal / is a directory / not a directory / Error starting

HOST_IP=$(hostname -I | awk '{print $1}')
curl -sI http://localhost:12300/ | head -3   # 302 → /adminPage/setup
curl -sI http://localhost:3000/ | head -3    # 302 → /login
```

瀏覽器開 `http://<HOST_IP>:12300/` 應出現「設定管理員密碼」表單(證明 init.* 已移除,UI wizard 正常觸發)。

### Task 3.6: 設定 admin 密碼 + 確認 dashboard 正常

UI 設定 admin 帳密(**不要用 Admin123**)。確認登入、Grafana dashboard 載入、CrowdSec collections 已安裝:
```bash
docker exec nginxwebui-crowdsec cscli decisions list
docker exec nginxwebui-crowdsec cscli collections list | head
```

---

## Phase 4: 收尾

### Task 4.1: 把這份 plan commit 進 docs/

```bash
git add docs/superpowers/plans/2026-05-25-sidecar-images-ci-release.md
git commit -m "docs(plan): add 2026-05-25 sidecar images CI + release v5.1.1 plan"
git push origin dev
```

### Task 4.2: 更新 CLAUDE.md(可選)

如 user 要把版本範例 bump 到 5.1.1:
```bash
# Edit CLAUDE.md replace_all 5.1.0 → 5.1.1
git add CLAUDE.md
git commit -m "docs(claude): bump version examples to 5.1.1"
git push origin dev
```

---

## Verification(整體端到端驗收)

1. **CI:** `gh run list --limit 1` 顯示最近 v5.1.1 run 為 ✓ success
2. **Registry:** 8 個 manifest 全在 ghcr.io(4 image × 2 tag)
3. **Prod stack:** `docker compose ps` 7 個 service 全 healthy
4. **首次 setup wizard:** 瀏覽器開 `http://<HOST_IP>:12300` 看到設定密碼表單
5. **無 mount 錯誤:** prod 端 `docker compose logs crowdsec` 不再出現 `is a directory`
6. **Plan 在 repo:** `git log --oneline -- docs/superpowers/plans/2026-05-25-*` 顯示 commit

---

## Rollback Plan

如 Phase 2 CI 失敗或 Phase 3 prod 無法起 → 不要強推:

1. **Prod 端 rollback:** 把 backup compose 覆蓋回去,改 `.env` `NGINX_WEBUI_VERSION=5.1.0`,但回 5.1.0 仍需手動 scp 7 個 config 檔。乾脆 `down` 整個 stack 等 v5.1.1 修好。

2. **CI rollback:**
```bash
git push origin :v5.1.1   # 刪遠端 tag
git tag -d v5.1.1          # 刪本地 tag
git reset --hard HEAD~1    # 撤銷 release commit(若還沒推 master)
```
⚠️ 如已 push master → **不要** reset,改在 dev 上 commit fix 後重打 v5.1.2

---

## Notes

- `scripts/release.sh` 不需改 — 它只 bump pom + commit + tag,跟 image 名稱解耦。CI matrix 自動處理 4 image。
- `docker/.env.example` 不需改 — commit `6547e97` 已把 INIT_* 註解掉,加 NGINX_WEBUI_VERSION 是 user 各機器自決定。
- CrowdSec named volume 第一次 seed 後,baked config 升版需 `docker compose down -v` 才會 re-seed。這是 documented trade-off(commit `6547e97` message 末段)。新 prod 部署是首次 seed,沒這個問題。
- arm64 build 速度會比 amd64 慢 1.5-2x(因 GHA runner 是 amd64,arm64 用 QEMU emulation)。預估整體 CI 8-15 分鐘。
