# Release flow / 發版流程

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。發版前請把這一頁讀完。
> 延伸：[docs/superpowers/plans/2026-05-21-dev-release-workflow.md](../superpowers/plans/2026-05-21-dev-release-workflow.md)

## 誰是發版觸發器：**master 的 push**，不是 dev

這一點常被記錯，所以先講清楚，並附上可自行複驗的依據。

`.github/workflows/build.yml` 的 `on.push.branches` 是 `[master, dev]`，**兩個分支都會跑 CI**，
但真正產出版本的兩個 job 被閘在 master：

```
:41   if: github.event_name == 'push' && github.ref == 'refs/heads/master'   ← Release image
:121  if: github.event_name == 'push' && github.ref == 'refs/heads/master'   ← Auto-tag + GitHub Release
```

實際執行結果（5.2.9 發版當下）：

| push 目標 | Build JAR | Release image | Auto-tag + GitHub Release |
|---|---|---|---|
| `dev` | 跑，35s | **跳過**（0s） | **跳過**（0s） |
| `master` | 跑，41s | 跑，兩個 image | 跑，建出 `v5.2.9` |

所以：**push 到 dev 只有 build & test，不會發版；push 到 master 才會 build+push image、打 tag、建 GitHub Release。**

`release/*` 分支**不是**發版觸發器。它在下面「PR 變體」裡的角色只是「通往 master 的一條路」，
好讓 claude-code-review 有機會審；版本仍然是在它併進 master 之後才產生的。

## 分支角色

- `dev` — 常駐日常開發分支。
- `master` — **push 觸發發版**。CI 有版本閘控：pom 版本在 ghcr 還沒有才 build+push；自動打 `v*` tag + 建 GitHub Release。
- `hotfix/*` — 從 `master` 開。

## 主要路徑：在 dev bump，直接 push 到 master（不開 PR）

```bash
git checkout dev && git pull origin dev
scripts/release.sh 5.2.9        # 只在 dev 或 hotfix/* 能跑(script 有分支閘)
                                # 只改 pom 的 nginxWebUI <version> + commit,不打 tag、不 push
git push origin dev             # 同步 origin/dev（此步不發版）
git push origin dev:master      # ← 這一步才發版
docker manifest inspect ghcr.io/elf-express/nginxwebui:5.2.9   # 確認 image pushed
gh release view v5.2.9                                          # 確認 Release 建出來
```

> **不要開 dev → master 的 PR** —— GitHub merge 後的「Delete branch」會刪掉常駐 `dev`。
> 直接 push `dev:master` 不走 PR、不刪 dev（代價是沒有 claude-code-review 自動審）。

## 可選的 PR 變體（要 claude-code-review 保險時）

先在 `dev` 跑 `scripts/release.sh`（它的分支閘不接受 `release/*`），再 `git checkout -b release/5.2.9`
推上去開 `release/5.2.9 → master` PR。merge 時 GitHub 刪的是 release 分支，`dev` 不動；
merge 後 `git checkout dev && git pull` 即已同步（bump commit 本來就在 dev）。

**注意**：版本是在這個 PR 併進 master 的那一刻才產生的，不是在 `release/*` 被推上去的時候。

## release.sh 的兩道守衛

腳本只改 `pom.xml`，不碰 README / README_TW / CLAUDE.md / .env —— 部署文件刻意「不綁版本」
（`:latest` + `master` raw URL + jar 萬用字元）。它會驗證：

1. 改到的是 `<artifactId>nginxWebUI</artifactId>` 後面那個 `<version>`，**不是** parent 的 `solon-parent` 版本。
2. parent 版本仍為 `3.10.7`（升 Solon 時要同步改腳本裡這個數字）。

任一驗證失敗會 `git checkout -- pom.xml` 還原並中止。

## Hotfix

從 `master` 開 `hotfix/*`，同樣 `scripts/release.sh x.y.z` 之後 `git push origin hotfix/xxx:master`。

## 在 worktree 裡發版的注意事項

`scripts/release.sh` 的分支閘只接受 `dev` 或 `hotfix/*`。若主 checkout 正佔用 `dev`
（git 不允許同一分支在兩個 worktree 同時 checkout），worktree 裡就跑不了這個腳本。
這時手動做腳本的同樣三步即可，但**兩道守衛要自己補上**：確認改的是 nginxWebUI 的版本、
確認 parent 仍是 3.10.7。
