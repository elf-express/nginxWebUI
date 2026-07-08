# CI 自動建立 GitHub Release(根治 Release 頁面版本落後)

日期:2026-07-09
分支:dev → master(發版)
關聯:release flow / build.yml / scripts/release.sh

## 問題(使用者回報)

pom / image 已到 5.2.3,但 GitHub「Releases」頁面停在 v5.2.1。使用者要求:
1. 發版到 5.2.4。
2. 「這種事不要再發生」—— 根治 Release 頁面落後,不要每次手動補。

## Phase 1 根因調查(證據)

| 產物 | 現況 | 落後? |
|---|---|---|
| pom.xml / git tag | 5.2.3、`v5.2.3` tag 存在 | 否 |
| ghcr `nginxwebui` image | 5.2.3 / 5.2.2 / latest | 否 |
| GitHub Releases 頁面 | 最新 `v5.2.1`(2026-06-03),缺 5.2.2 / 5.2.3 | **是** |
| ghcr `nginxwebui-crowdsec` | 有 5.2.3 / 5.2.1,**缺 5.2.2** | 次要缺口 |

**根因**:[.github/workflows/build.yml](../../../.github/workflows/build.yml) 的 job 只有 build / release(push image)/ tag(打 git tag)三步,**從未有「建立 GitHub Release entry」的步驟**。git tag ≠ GitHub Release。該步一直靠手動走 GitHub UI(CLAUDE.md 自述為 optional),5.2.2 / 5.2.3 沒人手動建 → 頁面停在 5.2.1。

crowdsec 缺 5.2.2 是獨立現象(matrix 版本閘控 per-image;推測 5.2.2 當次該 image build/push 未成功)。非本次主線,列為觀察項;5.2.4 發版會重新產生 crowdsec:5.2.4。

## 方案

在 `tag` job 打完 git tag 後,新增一步用 CI 內建 `GITHUB_TOKEN` 呼叫 `gh release create` 自動建立 GitHub Release。

要點:
- `tag` job 已宣告 `permissions: contents: write` —— 這正是建 Release 所需,無需額外 secret。
- CI 內建 `GITHUB_TOKEN` 不受本地組織 PAT 403 政策影響(該政策只卡本地 `gh` 寫操作)。
- **冪等**:先 `gh release view` 查存在才建,重跑不會重複。
- `--generate-notes`:GitHub 依上一個 tag 到本次的 commits/PR 自動產生 release notes。
- `ubuntu-latest` runner 預裝 `gh`。

## 實作變更清單

1. `.github/workflows/build.yml`
   - `tag` job 更名語意保留;於「Create v<version> tag if new」step 後新增「Create GitHub Release if new」step。
   - 以 pom 版本組 `v$VER`,冪等建立 Release。

2. `pom.xml`
   - nginxWebUI `<version>` 5.2.3 → 5.2.4(不動 parent solon-parent 3.10.7)。

3. 補歷史:本地 `gh release create v5.2.2 / v5.2.3 --generate-notes`;若組織 PAT 403 則走 GitHub UI 或等 CI(不會自動補舊版,需手動)。

## 發版步驟(順序關鍵:先修 CI 再發版,5.2.4 即為修復驗證)

1. 改 build.yml → commit 到 dev。
2. bump pom 5.2.4 → commit 到 dev。
3. `git push origin dev:master` → CI 用**新** workflow 跑:build/test → release(版本閘控 build+push 2 images)→ tag `v5.2.4` → **自動建 GitHub Release v5.2.4**。
4. 補歷史 v5.2.2 / v5.2.3 Release。

## 驗證

- CI 綠燈,且 `tag` job 的新 step log 顯示「已建立 GitHub Release v5.2.4」。
- `gh release list` 出現 v5.2.4(且 5.2.2 / 5.2.3 補齊)。
- ghcr `nginxwebui` 與 `nginxwebui-crowdsec` 皆出現 5.2.4 tag。

## 風險與回退

- 風險低:新增 step 為純附加,冪等;失敗只影響 Release entry,不影響 image/tag。
- 回退:移除該 step 即回到原狀;誤建的 Release 可 `gh release delete`。
