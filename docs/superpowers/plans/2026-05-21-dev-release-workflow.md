# nginxWebUI Dev 分支與 Release 流程計畫

**日期：** 2026-05-21
**狀態：** 已批准，執行中
**目標版本：** 第一次 release v5.0.4

## Context

**為什麼需要這份計畫：**

*   過去 5.0.0–5.0.3 都是「直接在 master 改 pom.xml → push → CI 自動 build image」（[commit e9fe104](https://github.com/elf-express/nginxWebUI/commit/e9fe104) 是最後一次 release）。沒有 dev 分支、沒有 git tag、GitHub Releases 頁是空的。
*   使用者要建立正式的「dev 開發 → release」工作流程，包括 dev 分支、版本 tag、release 過程。
*   repo 內目前有**三份 docker-compose**：根目錄 `docker-compose.yml` / 根目錄 `docker-compose.dev.yml` / `deploy/docker-compose.yml`。根目錄那兩份引用相對路徑找不到配置檔（`./promtail-config.yml` 等實際在 `deploy/` 下），所以**只有** `**deploy/docker-compose.yml**` **真的能跑**。
*   CI ([.github/workflows/build.yml](.github/workflows/build.yml)) 現邏輯是「push to master 即推 `:<pom 版本>` + `:latest`」— 開 dev 分支後若直接合 master 會覆蓋已 release 的 image。
*   第三方 image `crowdsecurity/crowdsec:latest` 和 `fbonalair/traefik-crowdsec-bouncer:latest` 沒鎖版本，是 production 隱患。

**目標：**

1.  清掉壞掉/重複的 docker-compose、把 `deploy/` 確立為**唯一**部署來源。
2.  鎖定所有第三方 image 版本。
3.  建立 `dev` 長期分支，日常開發在 dev。
4.  release 改為「在 dev 上跑 release script 打 git tag → push dev --tags 觸發 CI build → 完成後 push dev:master fast-forward」。CI 邏輯升級：tag 才推 `:x.y.z`，master 平時 push 不覆蓋已 release 版本。
5.  寫 release script 自動化「改 pom + commit + tag + push」三步動作。
6.  文件同步更新（CLAUDE.md、README）。

---

## 確認過的決策

| # | 決策 | 選擇 |
| --- | --- | --- |
| Q1 | Release 模型 | dev 開發 + git tag（dev = 開發中、master = 已 release 快照、tag v\* = release 動作） |
| Q2 | docker-compose 清理 | `deploy/` 為唯一來源，刪除根目錄兩份 |
| Q3 | 版本號 bump | 手動 + `scripts/release.sh x.y.z` 輔助 |
| Q4 | 第三方 image pinning | 全部鎖到具體 minor/patch；加 Dependabot 自動 PR 提醒 |
| Q5 | 配置檔 release | 跟 git tag 帶走，**不打 tarball**（5.0.3 經驗：只發 image，配置靠 git） |
| Q6 | CI trigger | 改成「push to master 跑測試、push tag v\* 才 build + push image」；dev push 跑測試不 push image |

---

## 整體架構

### 分支模型

```
dev ............... 日常開發 + release 動作都在這裡；HEAD 永遠領先或等於 master
  ↓ (release 完成後手動 push origin dev:master)
master ............ 只是「最後一次 release 的快照指針」；不在 master 上 commit、不在 master 上 tag
  ↑ (tag 標在 dev 上某個 commit)
tag v5.0.4 ........ 由 release script 在 dev 上打的；CI 看到 tag 才 build + push image

ghcr.io/elf-express/nginxwebui:
  :5.0.4 .......... 對應 tag v5.0.4 build 的 image（不可變）
  :latest ......... 永遠等於最新的 tag build 結果
```

**為什麼 master 不做任何主動動作？** master 變成「最近一次成功 release 的書籤」，給外部使用者 `git checkout master` 永遠拿到「對應線上 :latest image 的程式碼」。所有寫入動作（commit、tag）都在 dev 上完成，master 只接收 fast-forward。沒有 master 獨立 commit 的話，永遠不會出現「master 和 dev 分叉」的麻煩。

### Release 流程（一次完整 release，使用者確認的順序）

```
# === 在 dev 上開發 ===
git checkout dev
# ... 開發、commit ...
git push origin dev          # CI 跑 build + test、不 push image

# === 要 release 5.0.4 時，仍然在 dev 上 ===
scripts/release.sh 5.0.4
# release.sh 內部會做：
#   1. 確認在 dev、工作區乾淨
#   2. 確認 tag v5.0.4 不存在
#   3. 改 pom.xml 版本
#   4. git commit "chore(release): bump version to 5.0.4"
#   5. git tag -a v5.0.4 -m "Release v5.0.4"

git push origin dev --tags   # 推 dev 含 tag → CI 看到 tag 觸發 build + push image

# === 等 CI 完成、確認 ghcr.io 上 :5.0.4 + :latest 都到位 ===
docker manifest inspect ghcr.io/elf-express/nginxwebui:5.0.4

# === 最後一步：把 dev 推到 master（fast-forward）===
git push origin dev:master
# master 現在 HEAD = dev HEAD = tag v5.0.4 commit

# === 在 GitHub Releases 頁開 v5.0.4 條目（手動，貼 CHANGELOG）===

# === 使用者拉新版 ===
git clone https://github.com/elf-express/nginxWebUI.git
git checkout v5.0.4       # 或 git checkout master，效果等同
cd deploy && docker compose pull && docker compose up -d
```

### Hotfix 情境

如果 production（v5.0.4）出 bug 要急修，但 dev 上已經有一堆未完成功能不能一起推：

```
# 從 master 開 hotfix 分支（master = 已 release 的乾淨快照）
git checkout master
git checkout -b hotfix/5.0.5

# 修 bug、commit
git commit -m "fix: <bug 描述>"

# 在 hotfix 分支跑 release script
scripts/release.sh 5.0.5
git push origin hotfix/5.0.5 --tags     # CI 看到 tag 觸發 build

# 等 CI 完成、推 master
git push origin hotfix/5.0.5:master

# 把 hotfix 同步回 dev，避免下個版本忘了帶
git checkout dev
git merge hotfix/5.0.5
git push origin dev

# 清理 hotfix 分支
git branch -d hotfix/5.0.5
git push origin :hotfix/5.0.5
```

---

## 具體改動清單

### Stage A：repo 結構清理（一次性，PR 進 dev 分支）

**A1. 刪除重複的 docker-compose**

*   刪除 `docker-compose.yml`（根目錄，壞掉的副本）
*   刪除 `docker-compose.dev.yml`（根目錄，依賴上述壞檔）

**A2. 在 deploy/ 加 dev override**

*   新增 [deploy/docker-compose.dev.yml](deploy/docker-compose.dev.yml)：

**A3. 鎖定第三方 image**

修改 [deploy/docker-compose.yml](deploy/docker-compose.yml)：

實作前先確認當下穩定版號（不要直接套用本 plan 列的版本）：

```
# CrowdSec 主程式：到 https://hub.docker.com/r/crowdsecurity/crowdsec/tags 看最新 stable
# 或本地：
docker pull crowdsecurity/crowdsec:latest
docker inspect crowdsecurity/crowdsec:latest --format '{{index .Config.Labels "org.opencontainers.image.version"}}'

# CrowdSec bouncer：到 https://hub.docker.com/r/fbonalair/traefik-crowdsec-bouncer/tags 看
docker pull fbonalair/traefik-crowdsec-bouncer:latest
```

把查到的版本寫進 compose（**用實際查到的版本，不要照抄 plan**）：

*   `crowdsecurity/crowdsec:latest` → 例如 `crowdsecurity/crowdsec:v1.6.4`
*   `fbonalair/traefik-crowdsec-bouncer:latest` → 例如 `fbonalair/traefik-crowdsec-bouncer:0.4.0`
*   其餘已鎖版本不動：`postgres:18-alpine` / `grafana/promtail:3.5.0` / `grafana/loki:3.5.0` / `grafana/grafana:11.6.0`

**A4. 加 Dependabot 自動提醒**

新增 [.github/dependabot.yml](.github/dependabot.yml)：

```
version: 2
updates:
  - package-ecosystem: "docker"
    directory: "/deploy"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

**A5. 更新 CLAUDE.md 與 README**

*   CLAUDE.md：把 `docker compose up -d` 改成 `cd deploy && docker compose up -d`
*   README.md：在 docker 安裝段落加上 `cd deploy` 的提示

---

### Stage B：建立 dev 分支與 CI 改造

**B1. 建立 dev 分支**

```
git checkout master
git pull origin master
git checkout -b dev
git push -u origin dev
```

**（可選）GitHub 後台保護分支設定**：因為 master 在這個模型下只接收 `git push origin dev:master` 的 fast-forward，**不要**設「require pull request」（會擋 fast-forward push），但可以設「require linear history」+「allow only specific users」。dev 不必保護（個人開發者太麻煩）。

**B2. 改造 CI workflow**

修改 [.github/workflows/build.yml](.github/workflows/build.yml)：

```
name: Build & Test & Release

on:
  push:
    branches: [master, dev]
    tags: ['v*']                  # ★ 新增 tag trigger
  pull_request:
    branches: [master]

env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
          cache: maven
      - run: mvn clean package -DskipTests -q
      - uses: actions/upload-artifact@v4
        with:
          name: nginxWebUI-jar
          path: target/nginxWebUI-*.jar
          retention-days: 7

  docker:
    needs: build
    runs-on: ubuntu-latest
    # ★ 改成：只在「push tag v*」時才 push image
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
          cache: maven
      - run: mvn clean package -DskipTests -q
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # ★ 從 tag 抓版本（不再從 pom），tag = 真相
      - id: version
        run: |
          VER="${GITHUB_REF#refs/tags/v}"
          echo "VERSION=$VER" >> "$GITHUB_OUTPUT"
          # 驗證 pom.xml 版本和 tag 一致
          POM_VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
          if [ "$POM_VER" != "$VER" ]; then
            echo "::error::tag ($VER) 和 pom.xml ($POM_VER) 不一致"
            exit 1
          fi

      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          platforms: linux/amd64,linux/arm64
          tags: |
            ghcr.io/elf-express/nginxwebui:${{ steps.version.outputs.VERSION }}
            ghcr.io/elf-express/nginxwebui:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

**改動要點：**

*   加 `tags: ['v*']` trigger
*   `dev` push 觸發 → 跑 build 但不跑 docker job（`if:` 排除了）
*   `master` push 觸發 → 跑 build 但不跑 docker job（同上）
*   `v*` tag push 觸發 → 跑 build + docker job → push image
*   docker job 加版本一致性驗證（tag 必須等於 pom）

**B3. 加 release script**

新增 [scripts/release.sh](scripts/release.sh)：

```
#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法：$0 <x.y.z>  （例如 $0 5.0.4）"
  exit 1
fi

# 1. 確認在允許的分支、工作區乾淨
CURRENT_BRANCH="$(git branch --show-current)"
case "$CURRENT_BRANCH" in
  dev|hotfix/*)
    : ;;
  *)
    echo "錯誤：請在 dev 或 hotfix/* 分支執行（目前在 $CURRENT_BRANCH）"
    exit 1
    ;;
esac
if ! git diff --quiet; then
  echo "錯誤：工作區有未 commit 的變更"
  exit 1
fi
if ! git diff --cached --quiet; then
  echo "錯誤：staging area 有未 commit 的變更"
  exit 1
fi

# 2. 確認 tag 不存在
if git rev-parse "v$VERSION" >/dev/null 2>&1; then
  echo "錯誤：tag v$VERSION 已存在"
  exit 1
fi

# 3. 拉最新（同名遠端分支）
git pull origin "$CURRENT_BRANCH"

# 4. 改 pom.xml 版本
#    注意：pom.xml 第一個 <version> 是 <parent> solon-parent:3.3.3，不能改！
#    要改的是 <artifactId>nginxWebUI</artifactId> 之後的那個 <version>
CURRENT_VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
echo "從 $CURRENT_VER 升級到 $VERSION"

# 用 awk：先找到 <artifactId>nginxWebUI</artifactId>、再改它後面的第一個 <version>
awk -v new="$VERSION" '
  /<artifactId>nginxWebUI<\/artifactId>/ { found=1 }
  found && !done && /<version>[0-9]+\.[0-9]+\.[0-9]+<\/version>/ {
    sub(/<version>[0-9]+\.[0-9]+\.[0-9]+<\/version>/, "<version>" new "</version>")
    done=1; found=0
  }
  { print }
' pom.xml > pom.xml.tmp && mv pom.xml.tmp pom.xml

# 驗證確實只改到 nginxWebUI 的版本
NEW_VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
if [ "$NEW_VER" != "$VERSION" ]; then
  echo "錯誤：pom.xml 改寫失敗（預期 $VERSION、實際 $NEW_VER）"
  git checkout -- pom.xml
  exit 1
fi
# 防呆：parent 版本必須維持 3.3.3
PARENT_VER=$(awk '/<parent>/,/<\/parent>/' pom.xml | grep -oP '<version>\K[0-9.]+')
if [ "$PARENT_VER" != "3.3.3" ]; then
  echo "錯誤：誤改了 parent 版本（現在是 $PARENT_VER、應是 3.3.3）"
  git checkout -- pom.xml
  exit 1
fi

# 5. commit + tag + push
git add pom.xml
git commit -m "chore(release): bump version to $VERSION"
git tag -a "v$VERSION" -m "Release v$VERSION"

echo ""
echo "✓ 本地完成。下一步："
echo "    git push origin $CURRENT_BRANCH --tags        # 推 dev/hotfix + tag → CI build image"
echo "    # 等 CI 完成、確認 ghcr.io 上 :$VERSION 已 push"
echo "    git push origin $CURRENT_BRANCH:master        # 把 dev fast-forward 到 master"
echo ""
echo "  CI 會自動 build：ghcr.io/elf-express/nginxwebui:$VERSION + :latest"
```

權限：`chmod +x scripts/release.sh`（在 git index 裡標可執行）。

---

### Stage C：文件更新（隨 Stage A/B 一起 PR）

**C1. CLAUDE.md 加「Release 流程」章節**

在「## 已完成的改進」之前插入：

```
## Release 流程

### 日常開發（dev 分支）

\`\`\`bash
git checkout dev
# 開發、commit
git push origin dev
# CI 自動跑：mvn build + e2e test，不 push image
\`\`\`

### Release 新版本（手動觸發，dev → tag → master）

\`\`\`bash
# 1. 在 dev 上 (保持在 dev、不切 master)
git checkout dev
git pull origin dev

# 2. 跑 release script（自動改 pom + commit + 打 tag）
scripts/release.sh 5.0.4

# 3. 推 dev 含 tag
git push origin dev --tags
# CI 看到 tag v5.0.4 → buildx → push ghcr.io/.../nginxwebui:5.0.4 + :latest

# 4. 等 CI 完成，確認 image 已 push 後，把 dev fast-forward 到 master
git push origin dev:master

# 5. 在 GitHub Releases 頁開 v5.0.4 release 條目（手動，貼 CHANGELOG）
\`\`\`

### Hotfix（緊急修 release 版，不能把 dev 帶上來時）

\`\`\`bash
# 從 master 開 hotfix 分支 (master = 已 release 的乾淨快照)
git checkout master
git checkout -b hotfix/5.0.5

# 修 bug、commit
# ...

# 跑 release script (script 允許在 hotfix/* 分支上跑)
scripts/release.sh 5.0.5
git push origin hotfix/5.0.5 --tags        # CI 觸發 build

# 等 image 上去後
git push origin hotfix/5.0.5:master

# 同步回 dev (避免下版本忘了帶這個修)
git checkout dev
git merge hotfix/5.0.5
git push origin dev

# 清理 hotfix 分支
git branch -d hotfix/5.0.5
git push origin :hotfix/5.0.5
\`\`\`
```

**C2. README.md 補** `**cd deploy**` **提示**

在「docker 安裝說明」章節，把所有 `docker compose ...` 指令前面補上 `cd deploy &&`，並標註 `git clone` 後第一步必須進 `deploy/`。

---

## 影響到的檔案彙整

**會修改：**

*   [.github/workflows/build.yml](.github/workflows/build.yml) — CI trigger 改造
*   [deploy/docker-compose.yml](deploy/docker-compose.yml) — 鎖第三方 image
*   [CLAUDE.md](CLAUDE.md) — 加 Release 流程章節、更新 docker 指令
*   [README.md](README.md) — 加 cd deploy 提示
*   [pom.xml](pom.xml) — 由 release script 在第一次 release 時改

**會新增：**

*   [.github/dependabot.yml](.github/dependabot.yml) — 第三方依賴自動 PR
*   [deploy/docker-compose.dev.yml](deploy/docker-compose.dev.yml) — 從原始碼 build 的 override
*   [scripts/release.sh](scripts/release.sh) — release 一鍵腳本

**會刪除：**

*   [docker-compose.yml](docker-compose.yml)（根目錄，壞副本）
*   [docker-compose.dev.yml](docker-compose.dev.yml)（根目錄，依賴壞副本）

**新分支：**

*   `dev`（從 master 開出）

---

## 驗證計畫

實作完成後，按順序執行驗證：

### Phase 1：repo 清理本地驗證

```
# 確認 root 沒有 docker-compose
ls docker-compose*.yml 2>/dev/null  # 應該無輸出

# 確認 deploy/ 自洽
cd deploy
docker compose config > /dev/null   # 不應有錯
docker compose -f docker-compose.yml -f docker-compose.dev.yml config > /dev/null
```

### Phase 2：dev 分支與 CI 行為

```
# (a) 在 dev push 一個 README typo 修正
git checkout dev
# 加一個小改動
git commit -am "test: trigger CI on dev"
git push origin dev
# 預期：GitHub Actions 跑 build job (mvn package) + test、不跑 docker job
# 確認方式：到 https://github.com/elf-express/nginxWebUI/actions 看最新 workflow

# (b) 模擬 master fast-forward (此時不該觸發 docker job)
git push origin dev:master
# 預期：master 觸發一次 build job、但 if 條件擋住 docker job、不 push image
```

### Phase 3：第一次正式 release (5.0.4)

```
# 1. 確保在 dev、工作區乾淨
git checkout dev
git pull origin dev

# 2. 跑 release script
scripts/release.sh 5.0.4
# 看到本地：pom.xml 改成 5.0.4、commit "chore(release): bump version to 5.0.4"
# 看到本地：tag v5.0.4 已建立但未推

# 3. push dev + tag
git push origin dev --tags

# 4. 等 CI 完成（~5-10 分鐘）
# 在 https://github.com/elf-express/nginxWebUI/actions 確認 docker job 跑完
# 預期：ghcr.io 上有 :5.0.4 + :latest 雙 tag
docker manifest inspect ghcr.io/elf-express/nginxwebui:5.0.4
docker manifest inspect ghcr.io/elf-express/nginxwebui:latest

# 5. 確認 image 都到位後，把 dev fast-forward 到 master
git push origin dev:master
```

### Phase 4：端對端部署驗證

```
# 在一台乾淨的 Linux 機器：
git clone https://github.com/elf-express/nginxWebUI.git
cd nginxWebUI
git checkout v5.0.4
cd deploy
docker compose pull
docker compose up -d

# 確認所有 service healthy
docker compose ps    # 全部 (healthy)

# 確認 nginxwebui 起來
curl -sf http://localhost:8080/
# 預期：HTTP 200，看到登入頁面

# 確認 grafana 起來
curl -sf http://localhost:3000/
# 確認 crowdsec 起來
docker compose exec crowdsec cscli version
```

### Phase 5：Dependabot 行為（一週後）

*   預期一週內看到 1-2 個 Dependabot PR（grafana/promtail/crowdsec 升版提醒）
*   PR title 類似：`chore(deps): bump grafana/grafana from 11.6.0 to 11.6.1`

---

## 風險與緩解

| 風險 | 機率 | 緩解 |
| --- | --- | --- |
| CI tag trigger 改錯導致下次 release 失敗 | 中 | Stage B push 前先在 fork repo 用 dummy tag 測一次 |
| crowdsec / bouncer 鎖到舊版後新版 image 拉不到 | 低 | 鎖版本前先 `docker pull crowdsecurity/crowdsec:v1.6.4` 確認 image 存在 |
| dev 分支 merge to master 衝突累積 | 低 | 早期 release 頻率低、可週週 merge；若衝突可頻繁 rebase dev onto master |
| release script 的 sed/awk 在 Windows 環境壞掉 | 中 | 加 README 註明 Windows 須在 Git Bash / WSL 跑；或 macOS BSD sed 用 GNU awk 替代 |
| 5.0.3 image 已經在外部跑、改 CI 後再次 push 5.0.3 會被拒？ | 低 | tag 是 immutable 的，重複打 tag git 會擋；ghcr 上 5.0.3 已存在但 build 流程只允許新 tag |

---

## ExitPlanMode 後的執行順序

1.  **建立 dev 分支**（執行 Stage B1：從 master 開 dev、`git push -u origin dev`）
2.  plan 從 `compressed-floating-otter.md` **搬到** `**docs/superpowers/plans/2026-05-21-dev-release-workflow.md**` 並 commit 到 dev
3.  執行 **Stage A**（清理 docker-compose、鎖第三方 image、加 dependabot）— commit 到 dev
4.  執行 **Stage B2 + B3**（改 CI workflow、加 release script）— commit 到 dev
5.  執行 **Stage C**（CLAUDE.md / README 文件更新）— commit 到 dev
6.  push 全部到 dev、確認 CI 跑 build job 通過、**不**跑 docker job
7.  **第一次正式 release：** 在 dev 跑 `scripts/release.sh 5.0.4` → `git push origin dev --tags` → 等 CI 完成 → `git push origin dev:master`
8.  在 GitHub Releases 頁手動寫 v5.0.4 changelog
9.  完成 Phase 1-5 驗證

```
# 用於本地從原始碼 build image 測試
# 用法：cd deploy && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
services:
  nginxwebui:
    build:
      context: ..          # build context 指向 repo 根
      dockerfile: Dockerfile
```