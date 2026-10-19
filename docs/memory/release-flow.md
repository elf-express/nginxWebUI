# Release flow / 發版流程

> 分支規範見 [BRANCHING.md](../../BRANCHING.md)。本倉庫是第 3 種自有專案。
> 版號來源是 `pom.xml` 的 nginxWebUI project version；根 `package.json` 的版號不作為產品版號。

## 分支與觸發

- 開發分支從最新 `origin/dev` 開出，經 PR 用 merge commit 合進 `dev`。
- `release` 是唯一穩定版與部署來源，只從測過的 `origin/dev` 快轉；緊急修正從 `origin/hotfix/*` 快轉。
- `main` 只從 `origin/release` 快轉，不作為部署來源。
- push `dev`／`release`／`hotfix/**`，或 PR 目標為 `dev`／`release`：只跑 build/test，`verify`、image 與 GitHub Release job 跳過。
- 人推送 `v*` tag，或 `workflow_dispatch` 指定既有 tag：先 `verify`，再跑 build/test，通過後才發布兩個 image 與 GitHub Release。
- workflow 不提交版號、不建立或推送 tag；`ci` 彙總上述 job 結果。

## 版號 PR

由維護者確認本次版號後，在從最新 `origin/dev` 開出的開發分支執行：

```bash
scripts/release.sh X.Y.Z
git push -u origin <目前開發分支>
gh pr create --base dev --head <目前開發分支> --title "chore(release): vX.Y.Z"
```

版號 PR 只改 `pom.xml`；功能另開 PR。腳本只修改 nginxWebUI version 並 commit，
不 pull、不 push、不打 tag。拒絕 `dev`／`release`／`main`、detached HEAD 與非開發分支。
保留工作區及 staging area 檢查，以及兩道版號守衛：

1. 改到的是 `<artifactId>nginxWebUI</artifactId>` 後面的 `<version>`，不是 parent。
2. parent `solon-parent` 版本仍為 `3.10.7`；驗證失敗會還原 pom 並停止。

## 發佈與驗證

每一步先列出內容並取得 Eddie 確認；AI 不合併 PR、不修改 repo／組織設定、不 force push。

1. Eddie 合併版號 PR 後，查 `origin/dev` 當前提交的最新 check runs，全部通過才繼續。
2. 列出 `git log --oneline origin/release..origin/dev`，確認後把 `release` 從 `origin/dev` 快轉並推送；再確認該提交全部 checks 通過。
3. 確認後由人在 `origin/release` 建立 annotated tag：`git tag -a vX.Y.Z -m "nginxWebUI vX.Y.Z" origin/release`。
4. 再次確認後只推這個 tag：`git push origin vX.Y.Z`。
5. 查 tag workflow：`verify` 必須確認既有 tag 名稱等於 `v` 加上 pom 的直接 project version，checkout 等於 tag commit，且該 commit 是 `origin/release` 的祖先。
6. 確認兩個映像與 GitHub Release 已產出：

```bash
docker manifest inspect ghcr.io/elf-express/nginxwebui:X.Y.Z
docker manifest inspect ghcr.io/elf-express/nginxwebui-crowdsec:X.Y.Z
gh release view vX.Y.Z
```

7. 列出 `git log --oneline origin/main..origin/release`，確認後才把 `main` 從 `origin/release` 快轉並推送。

只看分支的 workflow 清單不足以判斷發佈資格；須查目標提交的 check runs。
任何快轉失敗、合併衝突、推送被 git-guard 擋下時立刻停止回報。

## 首次遷移（E-330）

先合併分支文件與 CI／版號遷移兩個 PR。CI PR 合併後，先核對該次 push 到 `dev` 的 run：
build/test 必須執行並成功，`verify` 與發版 job 必須跳過，貼出 run 連結後才確認建立 `release`。
建立來源為 `origin/main`（預期 `da9f8cff`），推送前重新核對；組織保護規則只查詢，不自行變更。
第一個產品版號為 `5.3.0`，另開版號 PR；建立 `release`、改版號、打 tag、快轉各自等待確認。
`release` 建立前，README 的 release 下載連結尚無對應分支；建立後再驗證實際可下載。

## 重跑既有 tag

從 Actions 的 `workflow_dispatch` 輸入完整既有 tag，例如 `v5.3.0`。
事件觸發的 checkout 固定事件 `github.sha`；手動執行先以指定 tag 驗證並輸出 commit，
下游 build 與兩個發布 job 都固定使用該 commit，避免 tag 或分支移動造成測試與發布內容不同。
所有 tag／手動發布（含不同版號）共用一個 workflow 併發群組，避免同時寫入版本映像、`:latest` 與 GitHub Release；branch／PR 的測試群組彼此獨立。
每個 image 先登入 GHCR，再檢查該版本 manifest；已存在就跳過 build/push，GitHub Release 已存在也跳過。
重建缺少的舊版 image 時仍沿用現有 `:latest` 發布行為，執行前由維護者確認要重建的版本。

## Hotfix

從 `origin/release` 開 `hotfix/e-<編號>-<短名>`，測試後依分支規範逐步確認發布。
發布後立刻把 `release` 合回 `dev`；衝突先停止回報。`main` 仍只從 `release` 快轉。
