# README 語言對調 + 內容修正 + CLAUDE.md release/* 流程

日期:2026-07-09
關聯:README.md / README_EN.md / CLAUDE.md

## 目標
1. README 語言主次對調:英文為主 `README.md`、繁中為副 `README_TW.md`。
2. 修正兩版過時/錯誤技術描述(與 CI / pom / 實際一致)。
3. CLAUDE.md 加 README 結構說明,並把 Release Flow 改為 `release/*` 分支流程(走 PR → Claude 自動 review → 不刪 dev)。

## 1. 語言對調(git mv 保留歷史)
- `README.md`(繁中)→ `README_TW.md`
- `README_EN.md`(英文)→ `README.md`
- 順序:先 Edit 修正兩檔內容 + 語言切換連結(指向對調後路徑)→ 再 git mv 對調。

## 2. 內容修正(英文 README.md + 中文 README_TW.md 兩版同步)

| 位置 | 錯 | 正 |
|---|---|---|
| 架構圖 | `Solon 3.3.3 + Java 8` | `Solon 3.10.7 + Java 17` |
| 表格 CI/Release 列 | `multi-platform (amd64+arm64)`、`git-tag-based` | `amd64-only`、`master-triggered, CI auto-tags` |
| 開發流程「GitHub Actions 看 v* tag → multi-platform build」 | v* tag 觸發 + 多平台 | push master 觸發(版本閘控)+ amd64;CI 自動打 tag |
| 直接拉 image「Multi-platform: amd64 + arm64」 | 多平台 | `Platform: linux/amd64` |
| 開發指南 E2E `(24+ scenarios)` | 24 | 31 個 spec |
| Release 歷史 | 停在 v5.1.1、"4 image + 3 sidecar" | 更新到 v5.2.5;修正為 2 self-built images(Loki/Grafana 已移除) |

crowdsec `v1.7.8` / bouncer `0.5.0` 經核對正確,**不改**。

## 3. CLAUDE.md
- Overview/Docs 區加一行 README 結構:`README.md`=英文(主)、`README_TW.md`=繁中。
- **Release Flow 段**:把「`git push origin dev:master`」改為 `release/*` 分支流程:
  - `git checkout -b release/x.y.z` from dev → `scripts/release.sh` bump → push → `release/x.y.z → master` PR
  - merge 觸發 CI 發版 + `claude-code-review.yml` 自動 review;merge 後刪 release 分支(dev 常駐不動)
  - 理由:同時滿足 PR review 保險 + dev 不被誤刪 + Git Flow 規範。
- line 198 註解的 README 引用同步(對調後仍成立)。

## 4. 驗證 + 提交
- 對調後確認 `README.md`=英文、`README_TW.md`=繁中,語言切換連結雙向正確。
- commit + push dev。

## 風險/回退
- 純文件變更,無程式碼風險。git mv 保留歷史,回退即反向 mv。
</content>
