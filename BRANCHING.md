# 分支規範：本 repo 為【第 3 種：自有專案】

> 完整規範：[分支規範：三種 repo 類型](https://linear.app/elf-express/document/分支規範三種-repo-類型貢獻型-fork私有-fork自有專案-128bf6e5e1f9)（Linear）
> 本檔只記錄這個 repo 的設定。人與 AI 動手前請先讀完本檔。

> **遷移過渡期（E-330）**：下列發佈、更新主庫與 hotfix 命令是**目標流程**，
> 須先合併 [CI／版號遷移 PR #95](https://github.com/elf-express/nginxWebUI/pull/95)，
> 確認合併後的 dev push run 測試成功、verify 與發版 job 跳過，再取得 Eddie 同意建立 `release`；
> 在此之前不要執行下列發佈命令，每一步的確認要求仍適用。
>
> 遷移前的 `build.yml` 只接受 push／PR 到 `master`、`dev`，發版 job 只接受 push `master`。
> 因此下列 `release` 快轉不會發布映像或 GitHub Release，hotfix PR 的 `release` 目標也不會觸發這個 workflow 的 build/tests。
> `master` 已改名為 `main`，目前發版暫停；不要照舊推送到 `master`。
>
> [目前發版程序與限制](docs/memory/release-flow.md) 在 #95 合併前仍記錄舊流程，僅供對照，
> 以本段的暫停與確認要求為準；遷移後依該頁更新後的流程操作。

## 本 repo 資訊

| 項目                  | 值                                     |
| --------------------- | -------------------------------------- |
| 類型                  | 第 3 種：自有專案（沒有上游）          |
| 本 repo（`origin`） | `elf-express/nginxWebUI`          |
| 部署位置              | `Docker Compose（docker/）；映像發佈至 GHCR（elf-express/nginxwebui、elf-express/nginxwebui-crowdsec）` |

行尾：LF，由 `.gitattributes` 規定（範本：elf-team-rule `assets/gitattributes`）

## 分支用途

| 分支                      | 用途                 | 可否提交                 | 從哪裡更新      |
| ------------------------- | -------------------- | ------------------------ | --------------- |
| `main`                  | 主庫，只用來同步     | **不提交，只快轉** | `release`     |
| `dev`                   | 整合測試             | 只能經由開發分支合併     | —              |
| `feat/`、`fix/`、`docs/`、`chore/`、`test/` | 開發                 | 可以                     | 從 `dev` 開出 |
| `release`               | 唯一的穩定版，部署用 | **不提交，只快轉** | 測過的 `dev`（緊急修正時 `hotfix/`） |
| `hotfix/`               | 緊急修正正式版       | 可以                     | 從 `origin/release` 開出 |

開發分支命名 `<類型>/e-<編號>-<英文短名>`（類型：`feat`、`fix`、`docs`、`chore`、`test`、`hotfix`；有 Spec／Plan 用 Plan 編號，單一工作單用該單編號）。

## 常用指令

開發：

```
git fetch origin
git switch -c feat/e-<編號>-<短名> origin/dev
```

發佈（`dev` 測試通過後）：

```
git fetch origin
git log --oneline origin/release..origin/dev   # 先列出會帶進去的提交，給使用者確認
git switch release
git merge --ff-only origin/release   # 先把本機 release 更新到最新
git merge --ff-only origin/dev
git push origin release
```

更新主庫：

```
git fetch origin
git log --oneline origin/main..origin/release   # 先列出會帶進去的提交
git switch main
git merge --ff-only origin/release
git push origin main
```

緊急修正（hotfix，`dev` 有未測完的內容時；完整步驟見 elf-team-rule `references/03-own-project.md`）：

```
git fetch origin
git switch -c hotfix/e-<編號>-<短名> origin/release
# 修改、測試、提交後 git push -u origin hotfix/e-<編號>-<短名>
gh pr create --repo elf-express/nginxWebUI --draft --base release --head hotfix/e-<編號>-<短名> --title "hotfix(E-<編號>): <說明>（僅觸發 CI，不要合併）" --body "E-<編號>"   # CI 沒有 hotfix/** 推送觸發時才需要；repo 沒有任何 workflow 時省略
git log --oneline origin/release..origin/hotfix/e-<編號>-<短名>   # 先列出會帶進去的提交，給使用者確認
gh api "repos/elf-express/nginxWebUI/commits/$(git rev-parse origin/hotfix/e-<編號>-<短名>)/check-runs?filter=latest&per_page=100" --jq '.check_runs[]|"\(.status) \(.conclusion) \(.name)"'   # 全部通過才發佈（判斷方式見 elf-team-rule references/04 第 0 節）
git fetch origin && git switch release && git merge --ff-only origin/release && git merge --ff-only origin/hotfix/e-<編號>-<短名> && git push origin release   # 任何一步失敗 → 停下來回報
git fetch origin && git switch dev && git merge --ff-only origin/dev   # 立刻合回 dev；失敗 → 停下來回報
git log --oneline origin/dev..dev   # 必須是空的；有輸出 → 本機 dev 有沒推的提交，停下來回報
git merge --no-edit origin/release   # 有衝突 → git merge --abort，停下來回報
git push origin dev
git switch main && git merge --ff-only origin/main && git merge --ff-only origin/release && git push origin main
```

## 分支保護設定建議

建議在 GitHub 設定分支保護規則（Branch Protection Rules 或 Ruleset）：

* **`main` 與 `release`**：禁止 force push、禁止刪除、只允許維護者 push。**不要**設「必須經過 Pull Request」：這兩條分支是在本機 `merge --ff-only` 後直接 push 的，GitHub 的 PR 合併會產生新提交或新 SHA，無法快轉。
* **`dev`**：禁止 force push、禁止刪除；開發分支（`feat/*`、`fix/*`、`docs/*`、`chore/*`、`test/*`、`merge/*`）一律經 PR 合併，用 merge commit（elf-team-rule 規則 37），維護者保留直接 push 權限，給 hotfix 發佈後的 `release` → `dev` 合回使用。

## 禁止事項

* 不在 `main`、`release` 上提交，也不 force push。緊急修正一律走 hotfix，不直接改 `release`／`main`。
* 不在 check runs 未全部通過、或沒有 CI 且使用者未明確同意例外時快轉 `release`。
* 不把 `main` 當穩定版或部署來源；部署一律用 `release`。
* 不直接在 `dev` 上開發。
* `--ff-only` 失敗或合併有衝突時，停下來回報，不要自行處理。

## 來源

本專案源自 Gitee 的 `cym1102/nginxWebUI`。
因後續改動已與原專案大幅分岔，Eddie 於 2026-10-18 確認本倉庫
依【第 3 種：自有專案】維護，已與原上游脫勾：
不同步、不向原上游送 PR，亦不設定 `upstream` remote。

`origin` 為 `elf-express/nginxWebUI`；
分支流程為 `dev` → `release` → `main`，
其中 `release` 是唯一穩定版與部署來源。
