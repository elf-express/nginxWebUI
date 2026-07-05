# 5.2.3 發版 + image pipeline 重新設計 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: 用 `superpowers:subagent-driven-development`(建議)或 `superpowers:executing-plans` 逐 task 執行。步驟用 checkbox(`- [ ]`)追蹤。
> **分支:** worktree `worktree-release-5-2-3`(從 dev)。**docs(本計畫 + spec)已在主線 dev;程式碼改動在 worktree,審核後 merge 回 dev。**
> **Spec:** `docs/superpowers/plans/2026-07-05-release-image-pipeline-redesign-design.md`

**Goal:** 把發版改成主線(master)觸發、版本閘控、CI 自動打 tag;自建 2 個 image(nginxwebui + nginxwebui-crowdsec);geoip build-time bake(離線可用);build 只 amd64;版本 5.2.3。

**Architecture:** `build.yml` 的 release job 改由 push `master` 觸發,版本從 `pom.xml` 抓,查 ghcr 已有該版就跳過(冪等),新版才 build+push 兩個 image(amd64)並自動打 `v<版本>` tag。crowdsec 恢復自建(官方 base + 烤 config)。root Dockerfile build 時把 3 個 MMDB 抓進 image。

**Tech Stack:** GitHub Actions、Docker buildx、docker-compose、shell(release.sh / entrypoint / update-geoip-cf.sh)、Maven。

## Global Constraints

- **所有 GitHub Action 必須 SHA-pin**(elf-express 組織政策,否則 workflow `startup_failure`)。本計畫用到的 SHA:
  - `actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7`
  - `actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5`
  - `docker/setup-buildx-action@bb05f3f5519dd87d3ba754cc423b652a5edd6d2c # v4`
  - `docker/login-action@af1e73f918a031802d376d3c8bbc3fe56130a9b0 # v4`
  - `docker/build-push-action@53b7df96c91f9c12dcc8a07bcb9ccacbed38856a # v7`
- **Java 17**(CI setup-java `'17'`;pom `java.version=17`)。
- **Image registry:** `ghcr.io/elf-express/<name>`,只 build `linux/amd64`。
- **geoip MMDB 來源**(P3TERX 鏡像,與 `update-geoip-cf.sh` 同源):
  - `https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb`
  - `https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb`
  - `https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-ASN.mmdb`
- **無新增使用者可見字串** → 不動 i18n 三檔。
- **可測性限制:** master-triggered CI 的完整行為只有真的 push master 才驗得到(Task 8);前面 tasks 用 build/lint/run 驗證各自產物。E2E 跑 Windows 前 PATH 的 `java` 需為 17。

---

### Task 1: 恢復自建 crowdsec Dockerfile

**Files:**
- Create: `docker/crowdsec/Dockerfile`(config 檔 `acquis.yml`/`profiles.yaml`/`abuseipdb.yaml` 已在該目錄)

- [ ] **Step 1: 建立 Dockerfile**

```dockerfile
# Custom CrowdSec image for nginxWebUI stack
# Bakes acquisition / profiles / notification config into image
# Runtime secrets (BOUNCER_KEY, ABUSEIPDB_API_KEY) still come from .env
FROM crowdsecurity/crowdsec:v1.7.8

COPY acquis.yml     /etc/crowdsec/acquis.yaml
COPY profiles.yaml  /etc/crowdsec/profiles.yaml
COPY abuseipdb.yaml /etc/crowdsec/notifications/abuseipdb.yaml
```

- [ ] **Step 2: 本地 build 驗證**

Run: `docker build -t test-crowdsec docker/crowdsec/`
Expected: build 成功。

- [ ] **Step 3: 驗證 config 已烤進**

Run: `docker run --rm test-crowdsec cat /etc/crowdsec/acquis.yaml`
Expected: 印出 acquis.yml 內容(非空)。

- [ ] **Step 4: 清理測試 image + commit**

```bash
docker rmi test-crowdsec
git add docker/crowdsec/Dockerfile
git commit -m "feat(docker): restore self-built crowdsec image (official base + baked config)"
```

---

### Task 2: geoip build-time bake(root Dockerfile)

**Files:**
- Modify: `Dockerfile`(root)

**Interfaces:**
- Produces: image 內 `/etc/nginx/geoip/` 含 3 個 MMDB(供 nginx geoip2 module + 離線使用)。

- [ ] **Step 1: 在 crontab 那個 RUN 之後,新增 geoip bake 的 RUN**

在 `Dockerfile` 中,`echo "0 3 * * 3,6 ... > /etc/crontabs/root` 那個 RUN(結尾)之後、`VOLUME` 之前,插入:

```dockerfile
# Bake GeoLite2 MMDB at build time → geoip 離線開箱可用
# (entrypoint 首啟 + cron 週三六仍會刷新;7 天 freshness 會跳過剛烤好的)
RUN curl -fL --retry 3 -o /etc/nginx/geoip/GeoLite2-Country.mmdb https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb \
    && curl -fL --retry 3 -o /etc/nginx/geoip/GeoLite2-City.mmdb    https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb \
    && curl -fL --retry 3 -o /etc/nginx/geoip/GeoLite2-ASN.mmdb     https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-ASN.mmdb
```

- [ ] **Step 2: build jar(Dockerfile COPY 需要)**

Run: `mvn clean package -DskipTests -q`
Expected: `target/nginxWebUI-5.2.2.jar` 產生(此時 pom 仍 5.2.2;Task 8 才升 5.2.3)。

- [ ] **Step 3: build image + 驗證 MMDB 已烤進**

Run: `docker build -t test-nwui . && docker run --rm test-nwui ls -l /etc/nginx/geoip/`
Expected: 見 `GeoLite2-Country.mmdb`、`GeoLite2-City.mmdb`、`GeoLite2-ASN.mmdb`(各非 0 byte)。

- [ ] **Step 4: 清理 + commit**

```bash
docker rmi test-nwui
git add Dockerfile
git commit -m "feat(docker): bake GeoLite2 MMDB at build time (offline geoip out-of-box)"
```

---

### Task 3: build.yml 重寫(主線觸發 + 版本閘控 + 自動 tag + 2-image + amd64)

**Files:**
- Modify: `.github/workflows/build.yml`(整檔替換為下方內容)

- [ ] **Step 1: 以下列內容覆寫 `.github/workflows/build.yml`**

```yaml
name: Build & Test & Release

# Trigger 模型:
#   push dev        → build/test(不發 image)
#   push master     → build/test + release job(pom 版本在 ghcr 沒有才 build+push+tag)
#   pull_request→master → build/test
on:
  push:
    branches: [master, dev]
  pull_request:
    branches: [master]

env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true

jobs:
  build:
    name: Build JAR
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7
      - name: Set up JDK 17
        uses: actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: Build with Maven
        run: mvn clean package -DskipTests -q

  release:
    name: Release image (${{ matrix.image.name }})
    needs: build
    runs-on: ubuntu-latest
    # 只在 push master 時跑
    if: github.event_name == 'push' && github.ref == 'refs/heads/master'
    permissions:
      contents: read
      packages: write
    strategy:
      fail-fast: false
      matrix:
        image:
          - name: nginxwebui
            dockerfile: Dockerfile
            context: .
            needs_mvn: true
          - name: nginxwebui-crowdsec
            dockerfile: docker/crowdsec/Dockerfile
            context: docker/crowdsec
            needs_mvn: false
    steps:
      - name: Checkout
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7

      - name: Extract version from pom.xml
        id: version
        run: |
          VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
          echo "VERSION=$VER" >> "$GITHUB_OUTPUT"
          echo "偵測到 pom 版本: $VER"

      - name: Check if this version already on ghcr
        id: exists
        run: |
          if docker manifest inspect ghcr.io/elf-express/${{ matrix.image.name }}:${{ steps.version.outputs.VERSION }} >/dev/null 2>&1; then
            echo "PUBLISHED=true" >> "$GITHUB_OUTPUT"
            echo "${{ matrix.image.name }}:${{ steps.version.outputs.VERSION }} 已存在 → 跳過發布"
          else
            echo "PUBLISHED=false" >> "$GITHUB_OUTPUT"
            echo "${{ matrix.image.name }}:${{ steps.version.outputs.VERSION }} 尚未發布 → 將 build+push"
          fi

      - name: Set up JDK 17
        if: steps.exists.outputs.PUBLISHED == 'false' && matrix.image.needs_mvn
        uses: actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build JAR
        if: steps.exists.outputs.PUBLISHED == 'false' && matrix.image.needs_mvn
        run: mvn clean package -DskipTests -q

      - name: Set up Docker Buildx
        if: steps.exists.outputs.PUBLISHED == 'false'
        uses: docker/setup-buildx-action@bb05f3f5519dd87d3ba754cc423b652a5edd6d2c # v4

      - name: Login to GitHub Container Registry
        if: steps.exists.outputs.PUBLISHED == 'false'
        uses: docker/login-action@af1e73f918a031802d376d3c8bbc3fe56130a9b0 # v4
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and Push (amd64)
        if: steps.exists.outputs.PUBLISHED == 'false'
        uses: docker/build-push-action@53b7df96c91f9c12dcc8a07bcb9ccacbed38856a # v7
        with:
          context: ${{ matrix.image.context }}
          file: ${{ matrix.image.dockerfile }}
          push: true
          platforms: linux/amd64
          tags: |
            ghcr.io/elf-express/${{ matrix.image.name }}:${{ steps.version.outputs.VERSION }}
            ghcr.io/elf-express/${{ matrix.image.name }}:latest
          cache-from: type=gha,scope=${{ matrix.image.name }}
          cache-to: type=gha,mode=max,scope=${{ matrix.image.name }}

  tag:
    name: Auto-tag release
    needs: release
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/master'
    permissions:
      contents: write
    steps:
      - name: Checkout
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7
      - name: Create v<version> tag if new
        run: |
          VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
          if git rev-parse "v$VER" >/dev/null 2>&1; then
            echo "tag v$VER 已存在,跳過"
          else
            git tag -a "v$VER" -m "Release v$VER"
            git push origin "v$VER"
            echo "已打並推送 tag v$VER"
          fi
```

- [ ] **Step 2: YAML/action-lint 驗證**

Run: `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest -color .github/workflows/build.yml`
Expected: 無 error(若無 actionlint,退而用 `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build.yml'))"` 確認 YAML 合法)。

- [ ] **Step 3: 邏輯自審(checklist,寫進 commit message 或 PR)**

確認:release job `if refs/heads/master`;version 從 pom;exists step 用 manifest inspect;所有後續 step 都有 `if PUBLISHED=='false'`;platforms 只 amd64;tag job `contents: write` + 打前查 `git rev-parse`。

- [ ] **Step 4: commit**

```bash
git add .github/workflows/build.yml
git commit -m "ci: master-triggered release, version-gated, auto-tag, 2-image matrix, amd64-only"
```

> 完整行為(版本閘控 + 自動 tag)在 Task 8 真的 push master 時驗收。

---

### Task 4: docker-compose crowdsec → 自建 image

**Files:**
- Modify: `docker/docker-compose.yml`(crowdsec service)

- [ ] **Step 1: 改 crowdsec service 的 image + 移除 3 個 config bind-mount**

把 `crowdsec` service 的 `image: crowdsecurity/crowdsec:v1.7.8` 改為:

```yaml
    image: ghcr.io/elf-express/nginxwebui-crowdsec:${NGINX_WEBUI_VERSION:-latest}
```

並在該 service 的 `volumes:` 移除這三行(config 已烤進 image),保留 log(ro)與 data volume:

```yaml
      - ./crowdsec/acquis.yml:/etc/crowdsec/acquis.yaml:ro
      - ./crowdsec/profiles.yaml:/etc/crowdsec/profiles.yaml:ro
      - ./crowdsec/abuseipdb.yaml:/etc/crowdsec/notifications/abuseipdb.yaml:ro
```

(保留 `nginxwebui_log:/var/log/nginx:ro`、`crowdsec_data:/var/lib/crowdsec/data`、`environment`(secrets)、`healthcheck`。)

- [ ] **Step 2: 驗證 compose 合法**

Run: `cd docker && docker compose config >/dev/null && echo OK`
Expected: `OK`(無語法錯)。

- [ ] **Step 3: commit**

```bash
git add docker/docker-compose.yml
git commit -m "chore(docker): compose crowdsec uses self-built nginxwebui-crowdsec image"
```

---

### Task 5: release.sh 縮成 bump-pom-only

**Files:**
- Modify: `scripts/release.sh`

- [ ] **Step 1: 讀現有 release.sh 確認 tag 相關行**

Run: `grep -n 'git tag\|git push\|tag' scripts/release.sh`
記下打 tag / push tag 的行號。

- [ ] **Step 2: 移除「打 tag」與「push tag / 提示 push tag」的區塊**

保留:分支檢查、`git diff --quiet` 乾淨檢查、tag-不存在檢查(可留可移,移除較一致)、改 pom nginxWebUI 版本的 awk、parent 版本不變的安全檢查、`git add pom.xml` + `git commit`。
移除:`git tag -a "v$VERSION" ...`(打 tag 那行)。
把結尾 echo 改為:

```bash
echo ""
echo "✓ pom 已升到 $VERSION 並 commit。下一步:"
echo "    git push origin dev:master     # master 觸發 CI 自動 build + push image + 打 tag v$VERSION"
```

- [ ] **Step 3: 驗證(dry run,不真的改版)**

Run: `bash -n scripts/release.sh && echo "語法OK"`
Expected: `語法OK`。並人工確認檔內已無 `git tag`。
Run: `grep -c 'git tag' scripts/release.sh`
Expected: `0`。

- [ ] **Step 4: commit**

```bash
git add scripts/release.sh
git commit -m "chore(release): release.sh bumps pom only; CI auto-tags on master push"
```

---

### Task 6: dependabot 加 crowdsec docker ecosystem

**Files:**
- Modify: `.github/dependabot.yml`

- [ ] **Step 1: 在現有 docker ecosystem(directory `/`)後,新增第二個 docker ecosystem 指 `/docker/crowdsec`**

在 `.github/dependabot.yml` 的 `updates:` 下,root docker ecosystem 區塊之後插入:

```yaml
  # crowdsec 自建 image 的 base(crowdsecurity/crowdsec)
  - package-ecosystem: "docker"
    directory: "/docker/crowdsec"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 5
    labels:
      - "dependencies"
      - "docker"
    commit-message:
      prefix: "chore(deps)"
      include: "scope"
```

- [ ] **Step 2: 驗證 YAML 合法**

Run: `python -c "import yaml; yaml.safe_load(open('.github/dependabot.yml'))" && echo OK`
Expected: `OK`。

- [ ] **Step 3: commit**

```bash
git add .github/dependabot.yml
git commit -m "chore(ci): dependabot track crowdsec Dockerfile base image"
```

---

### Task 7: 文件更新(CLAUDE.md + 部署文件)

**Files:**
- Modify: `CLAUDE.md`(Release Flow 段、Docker / Feature Inventory 相關描述)

- [ ] **Step 1: 更新 CLAUDE.md 的發版描述**

把「tag 觸發 / release.sh 打 tag」的敘述改為:
- 發版 = **push master 觸發**;版本從 `pom.xml`;CI 版本閘控(ghcr 沒有才發)+ 自動打 `v<版本>` tag。
- 日常:`scripts/release.sh X.Y.Z`(只 bump pom)→ `git push origin dev:master`。
- Image = **2 個自建**:`nginxwebui` + `nginxwebui-crowdsec`(官方 base + 烤 config);build 只 `linux/amd64`。
- geoip:MMDB **build 時烤進 image**(離線可用)+ entrypoint/cron 刷新。

- [ ] **Step 2: 驗證關鍵字已更新**

Run: `grep -nE 'master 觸發|amd64|bake|nginxwebui-crowdsec' CLAUDE.md`
Expected: 命中上述新描述。

- [ ] **Step 3: commit**

```bash
git add CLAUDE.md
git commit -m "docs: update release flow (master-triggered, 2 images, geoip bake, amd64)"
```

---

### Task 8: 升版 5.2.3 + 實際發版(收尾,需真實 master push)

**Files:**
- Modify: `pom.xml`(經 release.sh)

- [ ] **Step 1: 用改好的 release.sh 升 pom 到 5.2.3**

Run: `bash scripts/release.sh 5.2.3`
Expected: pom nginxWebUI 版本 = 5.2.3、parent 不變、有 commit、**無 tag**。
Run: `grep -A1 'artifactId>nginxWebUI' pom.xml | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1`
Expected: `5.2.3`。

- [ ] **Step 2: 把 worktree 分支的所有改動 merge 回 dev(經 code review 後)**

先跑最終 whole-branch code review(subagent-driven 的最後一步)。通過後在主 checkout:
```bash
git -C /e/nginxWebUI merge --ff-only worktree-release-5-2-3    # 或走 PR
git -C /e/nginxWebUI push origin dev
```

- [ ] **Step 3: push dev:master 觸發發版**

```bash
git -C /e/nginxWebUI push origin dev:master
```
Expected(去 Actions 頁看,browser MCP):release job 對兩 image 顯示 `PUBLISHED=false` → build+push;tag job 打 `v5.2.3`。

- [ ] **Step 4: 驗證 image + tag 發出**

Run: `docker manifest inspect ghcr.io/elf-express/nginxwebui:5.2.3 >/dev/null 2>&1 && docker manifest inspect ghcr.io/elf-express/nginxwebui-crowdsec:5.2.3 >/dev/null 2>&1 && echo "兩 image OK"`
Expected: `兩 image OK`。
Run: `git ls-remote --tags origin v5.2.3`
Expected: 有 `v5.2.3`。

- [ ] **Step 5: 冪等驗證(可選)**

再 `git push origin dev:master`(無 pom 變更)→ Actions release job 兩 image 顯示 `PUBLISHED=true` → 跳過;tag job 顯示 tag 已存在跳過。確認不重發。

---

## 最終驗證(whole-branch)

- V1 — `mvn clean package -DskipTests` exit 0(注意 JAVA_HOME/PATH 為 17)。
- V2 — `docker build docker/crowdsec/` 成功、config 烤進。
- V3 — `docker build .` 後 `/etc/nginx/geoip/` 有 3 MMDB。
- V4 — `docker compose -f docker/docker-compose.yml config` 合法、crowdsec 指自建 image。
- V5 — `.github/workflows/build.yml` actionlint / YAML 合法;release `if refs/heads/master`;platforms 只 amd64。
- V6 — `grep -c 'git tag' scripts/release.sh` = 0。
- V7 — dependabot.yml 兩個 docker ecosystem(`/` + `/docker/crowdsec`)。
- V8 — `npm run test:fast` 全綠(零回歸;PATH java 17)。
- V9 — Task 8 真實 master push:兩 image + tag v5.2.3 發出;重複 push 不重發。

## Risks & Mitigations(承 spec)

- 版本閘控查 ghcr 失敗 → manifest inspect 失敗當「不存在」→ 重發(冪等、無害)。
- 自動 tag 撞既有 → 打前 `git rev-parse` 查。
- geoip bake +~80MB → 可接受。
- master 非 ff → 目前 master 是 dev 祖先;發版前確認。
- crowdsec 自建 vs 官方 → 只差 config bake,行為等同。
