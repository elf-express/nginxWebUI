# 修復 UI 改造後的前後端接線問題(參數欄位 + GeoIP 排程)

> **REQUIRED SUB-SKILL:** 用 `superpowers:subagent-driven-development`(建議)或 `superpowers:executing-plans` 逐 task 執行。步驟用 checkbox(`- [ ]`)追蹤。
> **分支:** 全程在 `dev`(已確認 HEAD=origin/dev)。改完依使用者慣例直接 commit + push 到 `dev`。

## Context

**為什麼做這件事:** nginxWebUI 的 UI 改造(v5.x「T1-T4 + template picker」等)當時只做了視覺驗證(看畫面),沒有重新檢查前後端對接。使用者實際使用時撞到兩個「畫面有、實際沒接上」的功能,並懷疑還有更多。

**調查方式(已完成):** 4 個 Explore agent(參數 UI/JS/後端三線、GeoIP UI+排程兩線、全站接線 diff 一線)+ 親自讀關鍵碼 + 使用者實機回報(F12)。

**全站接線稽核結論(掃過 39 controller + 36 JS):整體約 98% 完整。** 只揪出下列真實問題,沒有大規模假功能:

- **問題 1 — 新增代理時「參數」textarea 無法輸入**
  - 根因:Layui 疊層彈窗的 runtime 互動問題(後端接線正常,稽核 + 讀碼雙重佐證)。確切機制待實機 F12 釘死。另有一個確定 typo。
  - 證據:`server/index.html:478` `#paramJsonDiv` / `server/index.js:845` `fillTable` / `server/index.js:202` `#paramJson` typo。
- **問題 2 — GeoIP「排程下載」沒實現,只能手動**
  - 根因:後端無任何 `@Scheduled` 跑 GeoIP;UI 的「排程」欄是寫死的靜態文字;唯一自動機制是 Docker crontab(JAR 模式完全沒有)。已完全確認。
  - 證據:`GeoipService.java:84`(寫死 scheduleStr)/ `ScheduleTask.java`(5 個排程無 GeoIP)/ `Dockerfile:53`(cron)。
- **問題 3(選做)— `/adminPage/login/getLayuiWord` 潛在 404**
  - 根因:前端 `translateLayui()` 呼叫但後端無此 endpoint;目前被註解,啟用才會爆。
  - 證據:`base.js:304`(呼叫)/ `base.js:91`(註解處)。

**「前後端 API 沒連上」的體感澄清:** 參數編輯器設計上是純前端(參數存進隱藏欄位 `serverParamJson` / `locationParamJson`,最後整包隨表單 POST `addOver`),開編輯器本來就不發 AJAX → Network 看不到請求是正常,並非斷線。真正的 bug 只有「打不了字」。

### 不做的事

- 不動其他 98% 正常的頁面接線。
- 不把 `2026-05-23-ui-polish-denyallow-rework.md`(另一份未執行的 UI/DenyAllow 計畫)併進來——正交、另案。
- 不改 `docs/cert-guide.md` / `dev-release-workflow.md` 那兩個未 commit 的 cosmetic 重排(與本案無關,待使用者另行決定)。

---

## Phase 1 — Task 1:參數 textarea 無法輸入(repro-first,鐵則:沒確認根因不修)

### Task 1.1:實機重現 + 釘死「打不了字」的確切機制

**Files:** 無修改(純診斷)

- [ ] Step 1 — build + 起測試 server:`mvn clean package -DskipTests -q`,再用 `java -jar target/nginxWebUI-*.jar --server.port=18080 --project.home=./test-home-param/ --project.testCaptcha=1234 --init.admin=admin --init.pass=admin`(背景跑)。
- [ ] Step 2 — 重現並蒐證(Playwright 或瀏覽器 F12):新增代理 →(加一個 location)→ 點「設置額外參數」開參數編輯器 → 嘗試在 name/value textarea 輸入。記錄:(a) textarea runtime 是否 `readOnly`/`disabled`;(b) `getComputedStyle(textarea).pointerEvents` 是否 `none`;(c) `document.elementFromPoint(x,y)` 在 textarea 座標上回傳的是不是 textarea 本身;(d) 點擊後 `document.activeElement` 是否變成該 textarea;(e) Console 紅色錯誤、兩層 layer 的 z-index。
- [ ] Step 3 — 比對對照組:同頁「批量輸入」(`#batchInputDiv` 的 `#batchInputText`)textarea 能否打字?denyAllow 彈窗?(判斷是參數編輯器專屬,還是所有疊層彈窗通病)
- [ ] Step 4 — 結論:依證據從候選根因選定一個寫進 commit message。(A) 疊層 layer focus/遮罩順序;(B) `#paramJsonDiv` 在 `height:0;width:0;overflow:hidden` 包裹層(`index.html:270`)導致 layer 內容非互動;(C) runtime 被加 readonly/disabled / 重複 id / 殘留前次 `tr[name='param']`。
- [ ] Step 5 — 停 server + 清理 test-home-param/。

> STOP:把 Step 2/3 觀察結果回報使用者,確認根因後再進 Task 1.3。

### Task 1.2:修掉已確定的 typo(獨立於上,先做)

**Files:** Modify `src/main/resources/static/js/adminPage/server/index.js`

- [ ] `add()` line 202 把 `$("#paramJson").val("");` 改成 `$("#serverParamJson").val("");`(`#paramJson` 不存在;此 typo 造成「編輯既有代理後再新增」會殘留上一個代理的 server-level 參數)。
- [ ] commit:`fix(server): clear correct hidden field (#serverParamJson) on add()`

### Task 1.3:依 Task 1.1 確認的根因施作修法

**Files:** `server/index.js` 和/或 `server/index.html`

- [ ] 若 (A)/(B):最穩健做法是把彈窗 div(`#paramJsonDiv` 等)移出 `height:0;width:0;overflow:hidden` 包裹層,或讓參數 layer 不被父 layer 遮罩攔截(視 repro 結果決定確切手法)。
- [ ] 若 (C):移除 runtime readonly/disabled 來源,或在 `fillTable` 開窗前清掉殘留 `tr[name='param']`。
- [ ] commit(訊息含 Task 1.1 確認的根因證據)。

### Task 1.4:Playwright 驗證(防回歸)

**Files:** Create/extend `tests/e2e/NN-server-param.spec.js`

- [ ] 新增代理 → 開參數編輯器 → 在 name/value 輸入 → 斷言值寫得進去 → 提交 → 重開斷言參數仍在 → 整體存檔後 `GET detail` 斷言 `serverParamJson` 含該參數。
- [ ] `npx playwright test tests/e2e/NN-server-param.spec.js` 通過。

---

## Phase 2 — Task 2:GeoIP 排程自動下載(Java `@Scheduled`,JAR/Docker 通用)

> 修法照抄現成範本 `ScheduleTask.fetchDenyAllowLists()`(line 93,cron `0 * * * * ?` 比對 `fetchTime`)。GeoIP 下載邏輯 `GeoipService.download(key)`(line 141)已是 public、可重用、會落 `geoip.{key}.updatedAt`。

### Task 2.1:確認 schedule 設定的儲存方式

- [ ] 決定 fetchTime 存哪:沿用 `SettingService`(GeoIP 既已用 `geoip.{key}.updatedAt`)存 `geoip.fetchTime`(預設如 `03:00`)。純檢查,記錄結論。

### Task 2.2:`ScheduleTask` 加 GeoIP 排程方法

**Files:** Modify `src/main/java/com/cym/task/ScheduleTask.java`

- [ ] 加 `@Scheduled(cron = "0 * * * * ?")` 的 `fetchGeoip()`:當下 `HH:mm` == 設定的 `geoip.fetchTime` 時,對 country/city/asn 呼叫 `geoipService.download(key)`(注入 `GeoipService`)。
- [ ] 加 startup catch-up(`@Scheduled(fixedDelay=86400000, initialDelay=...)` 或退回 cron):開機後若 `geoip.{key}.updatedAt` 為空/超過 N 天,補抓一次。先確認 Solon `solon-scheduling-simple` 支援 `fixedDelay/initialDelay`,不支援則退回 cron(同 DenyAllow 計畫的 caveat)。
- [ ] `mvn compile` 通過。

### Task 2.3:讓「排程」欄反映真實狀態(不再寫死)

**Files:** `GeoipService.java`、`ext/GeoipDbInfo.java`、`protectionCert/index.html`、`geoip.js`(視範圍)

- [ ] `GeoipService.getDbInfos()` 的 `scheduleStr` 改成從設定的 `geoip.fetchTime` 動態組字串(而非寫死 `"Wed & Sat 03:00 (UTC)"`),並一併回 `lastUpdate`(`geoip.{key}.updatedAt`)。
- [ ] (選配)`protectionCert/index.html` 把「排程」欄做成可編輯/可設定 fetchTime,並加 `GeoipController` 存 `geoip.fetchTime` 的 endpoint。若不做可編輯,至少讓顯示值真實。
- [ ] 若新增使用者可見字串 → 三份 properties 同步(`messages.properties` 簡 / `messages_zh_TW.properties` 繁 / `messages_en_US.properties` 英,CJK 用 `\uXXXX`)。

### Task 2.4:Playwright 驗證

**Files:** Create `tests/e2e/NN-geoip-schedule.spec.js`

- [ ] 設一個近未來的 fetchTime → 等排程觸發(或直接驗 startup catch-up)→ 斷言 `geoip.{key}.updatedAt` 有更新;「排程」欄顯示真實值。

---

## Phase 3 —(選做)Task 3:`getLayuiWord` 潛在 404

**Files:** `static/js/adminPage/base.js`(和/或 `LoginController`)

- [ ] 二選一:(a) 既然 `translateLayui()` 已註解、用不到 → 連函式定義一起移除(死碼清理);(b) 若要保留 → 在 controller 補 `/adminPage/login/getLayuiWord` endpoint。建議 (a)。

---

## Phase 4 — 整體驗證

- [ ] V1 — `mvn clean package -DskipTests` → exit 0,jar 產生。
- [ ] V2 — 實機:新增代理 → 參數編輯器可正常輸入、提交、重開仍在。
- [ ] V3 — `grep '#paramJson"' server/index.js` → 0(typo 已修)。
- [ ] V4 — `grep fetchGeoip ScheduleTask.java` → 找到 method。
- [ ] V5 — 實機/測試:設 fetchTime 後 GeoIP 自動下載、`updatedAt` 更新。
- [ ] V6 — 「排程」欄顯示真實值(非寫死)。
- [ ] V7 — 新增字串三份 properties 齊全(grep 三份各 1)。
- [ ] V8 — `npm run test:fast` 全套 all pass(含新增 spec)。

> 任一項失敗 → 回頭修,不口頭宣稱完成(`superpowers:verification-before-completion`)。

---

## Risks & Mitigations

- Task 1 真根因要 repro 才能定 → Task 1.1 已列 F12 蒐證清單 + 對照組 + 候選根因,確認後再修。
- Solon `@Scheduled` 不支援 `fixedDelay/initialDelay`(startup catch-up)→ Task 2.2 先確認;不支援退回 cron(同 DenyAllow 計畫經驗)。
- GeoIP 下載來源(P3TERX mirror)失效 → catch-up 每輪重試;UI 仍可手動。
- 改 `server/index.html` 彈窗結構破壞其他疊層彈窗 → Phase 4 全套 E2E regression;Task 1.3 動結構前先確認 batch/denyAllow 對照組。
- 新字串漏同步三份 properties → 畫面出現 `${...}` 原文 → V7 grep 三份。

---

## 執行交接

- 全程在 `dev`,逐 task commit,完成後 push `dev`。
- 兩大主線可並行(Task 1 純前端 + repro;Task 2 純後端 Java)——適合 `subagent-driven-development` 拆給不同 subagent。
- 優先序:Task 1.2(typo,秒修)→ Task 2(GeoIP,根因已確認可直接做)→ Task 1.1 / 1.3(需 repro)→ Task 3(選做)。
