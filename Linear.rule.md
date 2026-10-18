# Linear.rule.md — nginxWebUI 開發規範

> 適用範圍：`elf-express/nginxWebUI` 倉庫（主工作樹與所有 worktree），人與 AI 共同遵守，也適用於派出的隊友與子代理（派工時把「回報規則」一節原文放進指示）。
> 本檔比照 ELF EXPRESS 團隊 Linear 文件，團隊文件更新時以團隊文件為準，並回頭同步本檔：
> - [開發流程規範：Spec／Plan × superpowers](https://linear.app/elf-express/document/開發流程規範specplan-superpowers-45b0916cd43c)
> - [隊友規範：角色、命名與回報](https://linear.app/elf-express/document/隊友規範角色命名與回報-e4cb84a59189)
> - [分支規範：三種 repo 類型](https://linear.app/elf-express/document/分支規範三種-repo-類型貢獻型-fork私有-fork自有專案-128bf6e5e1f9)（本倉庫為第 3 種，見 [BRANCHING.md](BRANCHING.md)）

## 本倉庫與 XiHan.Framework 的差異

| | nginxWebUI | XiHan.Framework |
|---|---|---|
| 性質 | **自有倉庫**（elf-express 維護、自己發版） | fork 別人的倉庫（上游作者審 PR） |
| 規範檔 | 本檔進 git，`CLAUDE.md` 以 `@Linear.rule.md` 載入 | `Linear.rule.md` 放倉庫外、不進 git |
| 來源 | 源自 Gitee `cym1102/nginxWebUI`，已脫勾，**不同步、不送上游、不設定 upstream remote** | 每日同步上游、`pr/` 分支送上游 |
| 穩定版／主庫 | `release` 為唯一穩定版；`main` 只從 release 快轉；發版由人在 release 推 tag 觸發 | 依該 repo 的 BRANCHING.md |

## 回報規則

- 預設 3 行以內：做了什麼、結果（提交數、測試總數、PR）、下一步。
- 只有這三種情況才展開說明：
  1. 需要使用者決定的事：用選項列出，每個選項附一句代價。
  2. 計畫或任務書跟實際不符。
  3. 測試失敗、數字對不上，或要偏離計畫。
- 不說明推理過程、不寫怎麼驗證的、不預告接下來會怎麼做。細節寫在 Linear 留言或 commit 訊息，使用者要看再問。
- 不跟使用者確認「要不要順手做 X」。小事直接做，在 Linear 記一筆。

## 開發流程：Linear Spec／Plan × superpowers

- Linear（團隊 ELF EXPRESS，key `E`）是規格與計畫的唯一來源。brainstorming 的產出寫進 `[Spec]` 單，writing-plans 的產出寫進子單 `[Plan]`，**不再寫** `docs/superpowers/specs/`、`docs/superpowers/plans/`（既有檔案保留為歷史與指南，除非使用者明確要求另存）。
- 流程步驟、狀態轉換、estimate 必填、兩道保險（5-1 審查、6 驗證）不可省，全部照團隊文件；Claude Code 端用 `anthropic-skills:linear-superpowers` skill。
- 不需要設計的小單（單純維運、明確的錯誤修正、文件）不套 Spec／Plan，但仍走工作分支與 PR。
- 讀不到 Linear MCP 時先告訴使用者，不要改寫到本地檔案。

### 本倉庫的驗證指令（第 6 步「驗證證據」用）

```bash
mvn clean package -DskipTests      # 先產 jar，E2E 依賴它
mvn test                           # JUnit 5 單元測試（CI 也會跑，本機先跑過再開 PR）
npm run test:unit                  # Node 單元測試（docs 腳本）
npm run test:fast                  # Playwright E2E 全套（SQLite）
npm run test:pg                    # 動到 SQL／ORM／跨 DB 行為時加跑
```

留言附指令、exit code、通過／失敗數。

### Plan 的全域限制（本倉庫版）

團隊規範要求資料庫腳本冪等、不可 DROP 既有資料。本倉庫沒有手寫遷移腳本，對應的是 `InitConfig` 的啟動 migration 與 `@InitValue`：

- migration 必須可重複執行，以 setting flag 當單一閘門（不要用「欄位為空」判斷是否已遷移，`@InitValue` 的 DDL 會先填預設值）。
- 不刪既有資料表或使用者資料；淘汰欄位保留不讀。
- 每個 Plan 仍需符合 `CLAUDE.md` 的六條核心原則（三份 i18n、Playwright 測試、離線前端、A11y…）。

## 隊友命名

| 角色 | 系統名稱（工具參數） | 顯示名稱（Linear、回報） |
|---|---|---|
| 統籌 | — | `E-xx-統籌` |
| 計畫撰寫 | `exx-planner` | `E-xx-計畫撰寫` |
| 實作 | `exx-implementer` | `E-xx-實作` |
| Task 審查 | `exx-task-reviewer` | `E-xx-Task 審查` |
| 最終審查 | `exx-final-reviewer` | `E-xx-最終審查` |

- 編號用 **Plan** 編號，與分支名一致；每張單一組新隊友，不跨單沿用。同角色並行加流水號（`e87-implementer-2`）。
- 統籌不寫程式、不審程式；審查隊友必須全新派出，不能由實作隊友自審。
- 並行前先列寫集，確認不相交才並行，同時最多兩條線。

## 分支

動手前先讀 [BRANCHING.md](BRANCHING.md)，依 elf-team-rule 規範操作分支。

| 分支 | 用途 | 誰可以提交 |
|---|---|---|
| `dev` | 常駐整合測試線、開發分支起點 | 開發分支經 PR 合併；不用它直接開發或升版 |
| `release` | 唯一穩定版與部署來源 | 不提交，只從測過的 `origin/dev` 快轉；hotfix 從 `origin/hotfix/*` 快轉 |
| `main` | 主庫 | 不提交，只從 `origin/release` 快轉 |
| `feat/`、`fix/`、`docs/`、`chore/`、`test/` | 從最新 `origin/dev` 開出的開發分支 | 自己 |
| `hotfix/` | 從 `origin/release` 開出的緊急修正 | 自己 |
| `dependabot/*` | 依賴升級，PR 目標 dev | 機器人 |

- 開發分支名稱 `<類型>/e-<編號>-<英文短名>`；有 Spec／Plan 用 Plan 編號，單一工作單用該單編號。不要用 Linear 產生的中文分支名或個人前綴。
- 合回前先在開發分支吸收最新 `origin/dev` 並測試；衝突先停止回報，不自行解決。
- 開發分支一律經 PR 進 `dev`，用 merge commit，不 squash、不 rebase。**AI 不自行合併 PR、不直接 push dev**。
- 維護者的 `release` → `dev` hotfix 合回依團隊規範處理；不把這個例外當成直接開發或升版的許可。
- 版號來源維持 `pom.xml`，用 `scripts/release.sh` 在開發分支改版號，PR 標題 `chore(release): vX.Y.Z`，只 commit 不 push。
- 發版詳見 [release-flow.md](docs/memory/release-flow.md)：先查目標提交最新 checks，全綠且確認後才快轉 release，由人在 release 建立及推送 annotated tag；workflow 只驗證及發布。
- 建立 release、改版號、打 tag、推送 tag、快轉每一步都先取得 Eddie 確認；不 force push、不繞過 git-guard、不修改 GitHub repo／組織設定。
- 工作樹放在 repo 外，不在 `.ocrca/` 開發；不要改主工作樹既有變更。不同步 Gitee 原上游、不向其開 PR。

## Commit

- Conventional Commits，主旨沿用倉庫既有的英文寫法。
- 有 Linear 單時 scope 用 Plan 編號：`feat(E-32): add GeoIP schedule toggle`；沒有單時用模組名：`fix(mcp): ...`、`docs: ...`。
- 結尾附 AI 協作署名（依當次工具提供的 `Co-Authored-By` 行）。
