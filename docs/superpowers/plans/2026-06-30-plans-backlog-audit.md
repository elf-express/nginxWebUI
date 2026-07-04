# 2026-06-30 docs/superpowers/plans/ 全集審計報告

> **目的:** 釐清 `docs/superpowers/plans/` 下 27 份 plan 各自的實作狀態,給出 backlog 與清理建議。
> **觸發:** 使用者詢問「過去計畫有哪些沒實作」。
> **方法:** 對照 codebase(CodeGraph + Grep + Read)逐項驗證,不靠記憶或 plan 自述。

---

## 1. 已完成 ✅(共 12 份)

**v5.0.1 原始改造組**(無日期前綴,5 份對檔 `plan-*` + `impl-*`,由 [completion-report.md](completion-report.md) + [final-report.md](final-report.md) 正式結案):

- [plan-batch-param-input.md](plan-batch-param-input.md) + [impl-batch-param-input.md](impl-batch-param-input.md)
- [plan-crowdsec.md](plan-crowdsec.md) + [impl-crowdsec.md](impl-crowdsec.md)
- [plan-realip.md](plan-realip.md) + [impl-realip.md](impl-realip.md)
- [plan-syntax-highlight.md](plan-syntax-highlight.md) + [impl-syntax-highlight.md](impl-syntax-highlight.md)
- [plan.md](plan.md) + [impl-plan.md](impl-plan.md)

**日期前綴、已落地(7 份):**

| Plan | 證據 |
|---|---|
| [2026-03-22-nginx-info-auto-modules-geo-blocking.md](2026-03-22-nginx-info-auto-modules-geo-blocking.md) | CLAUDE.md Feature Inventory「nginx module auto-detect」+「GeoIP2 country block」 |
| [2026-05-20-lf-flags-ui-claude-md.md](2026-05-20-lf-flags-ui-claude-md.md) | CLAUDE.md「lang switch (flag SVG)」+ spec `flag-svg-integrity` |
| [2026-05-21-dev-release-workflow.md](2026-05-21-dev-release-workflow.md) | `scripts/release.sh` + CLAUDE.md Release Flow 段 |
| [2026-06-04-geoip-version-header-and-protection-table.md](2026-06-04-geoip-version-header-and-protection-table.md) | CLAUDE.md「GeoIP DB module (v5.2.0)」 |
| [2026-06-27-deploy-simplify-single-image.md](2026-06-27-deploy-simplify-single-image.md) | docker-compose.yml 只 build nginxwebui;sidecar 改 bind-mount |
| [2026-06-29-ui-audit.md](2026-06-29-ui-audit.md) | a11y Wave 1 commits 已合進 dev |
| [2026-06-29-ui-audit-wave2.md](2026-06-29-ui-audit-wave2.md) | git log `c6eca95f docs(audit): mark Wave 2 closed` + spec `27-a11y-buttons` |

---

## 2. 未完成 / 部分完成 ⚠️(4 份)

### 2.1 [2026-05-22-monitor-dashboard-v2.md](2026-05-22-monitor-dashboard-v2.md) — **0% 完全沒動**

**證據:**
- [monitor/index.html](../../../src/main/resources/WEB-INF/view/adminPage/monitor/index.html) 仍是原 Layui card,沒有 Vue mount、沒有 4 tabs
- 規劃的新 endpoint `/adminPage/monitor/security/stats`、`/traffic/stats`、`/crowdsec/stats`、`/tls/stats` 都不存在

**狀態變化:** 此 plan **已於 2026-06-30 標記 SUPERSEDED**(commit `2f9c2412`)— 因 monitoring stack(Loki/Promtail/Grafana)整套移除,plan 規劃的 security/traffic/TLS tab 與 Loki data source 都已失效。OSHI 系統指標部分仍可參考。

### 2.2 [2026-05-23-ui-polish-denyallow-rework.md](2026-05-23-ui-polish-denyallow-rework.md) — **1/3 完成,Part A/C 撤回**

**Part A(全站按鈕視覺):** 🚫 **撤回(2026-06-30 用戶決定「UI 大變動不需要了」)**
- 證據曾為 [base.css:85,98](../../../src/main/resources/static/css/adminPage/base.css#L85) 仍是 `margin-right: 6px/3px`,沒有 6px border-radius 全站規則

**Part B(DenyAllow self-seed 6 筆規則):** ✅ 已落地
- CLAUDE.md Backend「Seed-on-empty pattern」段反映
- `InitConfig.seedDenyAllowRules()` + `DenyAllowService.getDefaultRules()` 存在

**Part C(UI 欄位 usedBy → autoUpdate):** 🚫 **撤回(2026-06-30 用戶決定「UI 大變動不需要了」)**
- 證據曾為 [denyAllow/index.html:110](../../../src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html#L110) 仍 `${denyAllowStr.usedBy}`,i18n 沒 `autoUpdate` key

**最終狀態:** plan 於 2026-06-30 加 `PARTIALLY EXECUTED / PART A & C WITHDRAWN` header 並 commit 進 dev,從 untracked 升格為歷史記錄。Part B 已落地、Part A/C 不再追蹤。

### 2.3 [2026-06-27-frontend-backend-wiring-fixes.md](2026-06-27-frontend-backend-wiring-fixes.md) — **1/2(GeoIP 排程)**

**Task 2(GeoIP `@Scheduled`):** ✅ 已落地
- [ScheduleTask.java:115-156](../../../src/main/java/com/cym/task/ScheduleTask.java#L115) 有 `fetchGeoip()` + cron

**Task 1.2(typo `#paramJson` → `#serverParamJson`):** ❌ 未修
- [server/index.js:202](../../../src/main/resources/static/js/adminPage/server/index.js#L202) 仍是 `$("#paramJson").val("")` — `#paramJson` element 不存在;此 typo 會讓「編輯既有代理後再新增」殘留上一個代理的 server-level 參數

**Task 1.3(根因修法 — 參數 textarea 打不了字):** ❌ 未動(plan 要求 repro-first)

**建議優先級:** **高** — 是真實 bug,使用者反映過。Task 1.2 typo 一行修;Task 1.3 根因需先實機重現(F12 蒐證:`readOnly` / `pointerEvents` / `elementFromPoint` / `activeElement` / z-index)再決定修法。

### 2.4 已被取代 🔄(2 份)

| Plan | 狀態 |
|---|---|
| [2026-05-23-deploy-stack-refactor.md](2026-05-23-deploy-stack-refactor.md) | **SUPERSEDED**(2026-06-30 加 header)— 規劃 bake 4 sidecar image,實際走 6/27 simplify 路線(只 build nginxwebui + 官方 image bind-mount),之後 monitoring stack 又於 6/30 整套移除 |
| [2026-05-25-sidecar-images-ci-release.md](2026-05-25-sidecar-images-ci-release.md) | **PARTIALLY SUPERSEDED**(2026-06-30 加 header)— sidecar bake 路線同上撤回;Release flow 概念仍適用 |

---

## 3. 建議排序(若回頭清 backlog)

> 2026-06-30 用戶決定「UI 大變動不需要了」 — 原表中 UI 性質的兩項(全站按鈕圓角 + 大型 dashboard 改造)已撤回 / SUPERSEDED,僅剩 Bug 性質。

| # | 優先級 | 項目 | 工作量 |
|---|---|---|---|
| 1 | **高(Bug)** | 修 [server/index.js:202](../../../src/main/resources/static/js/adminPage/server/index.js#L202) typo(`#paramJson` → `#serverParamJson`) | 一行 |
| 2 | **高(Bug)** | 重現參數 textarea 打不了字 → 根因 → 修(2.3 Task 1.3) | 中(需實機 F12 蒐證) |
| ~~3~~ | ~~中(UX)~~ | ~~`ui-polish-denyallow-rework` Part A/C~~ → **撤回 2026-06-30** | — |
| ~~4~~ | ~~低(大規模)~~ | ~~`monitor-dashboard-v2`~~ → **已 SUPERSEDED**(monitoring stack 拆除連帶) | — |

---

## 4. 文件清理執行狀態(2026-06-30 完成)

| Plan | 動作 | 狀態 |
|---|---|---|
| `2026-05-22-monitor-dashboard-v2.md` | 加 SUPERSEDED header | ✅ commit `2f9c2412` |
| `docker-guide.md` | 加 PARTIALLY SUPERSEDED header | ✅ commit `2f9c2412` |
| `2026-05-23-deploy-stack-refactor.md` | 加 SUPERSEDED header | ✅ commit `edc54e00` |
| `2026-05-25-sidecar-images-ci-release.md` | 加 PARTIALLY SUPERSEDED header | ✅ commit `edc54e00` |
| `2026-05-23-ui-polish-denyallow-rework.md` 進 dev | 加 PARTIALLY EXECUTED / PART A & C WITHDRAWN header + commit | ✅ 2026-06-30 完成(Part B 已落地;Part A/C 撤回) |

---

## 5. Meta — 本份審計報告的成因

此報告對應的 plan 行為先前**只在對話中呈現**(未落檔),使用者於 2026-06-30 糾正:「下次 plan 是要直接 md 放 doc 的,不能終端機做數」。本檔即為**示範改正** — 從此起所有非瑣碎的 plan / 審計報告都先 Write 成 .md 落 `docs/superpowers/plans/YYYY-MM-DD-<slug>.md`,對話只給摘要 + 連結。
