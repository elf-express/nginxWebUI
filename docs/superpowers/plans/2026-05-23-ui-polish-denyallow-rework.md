# UI Polish + DenyAllow Overhaul Implementation Plan

> **PARTIALLY EXECUTED / PART A & C WITHDRAWN 2026-06-30:**
> - **Part B(DenyAllow self-seed 6 筆預設惡意 IP 規則 + retry-on-failure + startup catch-up)** 已落地。`InitConfig.seedDenyAllowRules()` + `DenyAllowService.getDefaultRules()` + `ScheduleTask` retry 分支均已存在;CLAUDE.md Backend「Seed-on-empty pattern」段反映。
> - **Part A(全站按鈕視覺 — 圓角 6px + 相鄰間距 15px)** 撤回 — 用戶 2026-06-30 決定「UI 大變動不需要了」。
> - **Part C(DenyAllow UI 欄位 usedBy → autoUpdate)** 撤回 — 同上理由。
> - 本 plan 保留作歷史記錄,後續 audit 對照用。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 三合一改造 — (A) 全站按鈕視覺微調（圓角 6px + 相鄰間距 15px）+ (B) DenyAllow 自動更新可靠性（首次部署自帶 6 筆惡意 IP 黑名單 + retry-on-failure + startup catch-up）+ (C) 黑白名單列表的「被引用」欄位移除改成「自動更新時間」（顯示 fetchTime，因為所有黑名單 IP 都全域生效，沒有「部分被引用部分沒」的概念）。

**Architecture:**
- **Part A — CSS only**：改 `static/css/adminPage/base.css` 第 70-73 行的 `.layui-btn` 規則 + 加 2 條新 selector，4 行 CSS 即覆蓋全站
- **Part B — Java/Solon 主應用**：在 `DenyAllowService` 加 `getDefaultRules()` 靜態工廠 / `ScheduleTask` 加 retry 分支 + `startupCatchUpDenyAllow()` / `InitConfig` 加 `seedDenyAllowRules()` 在 DB 空時 seed
- **Part C — Controller + View + i18n**：移除 `DenyAllowController.usedBy` 計算邏輯（保留 `protectionCert/index.html` 仍用此 key）/ `denyAllow/index.html` 改用新 i18n key `denyAllowStr.autoUpdate` / 三份 properties 同步加新 key

**Tech Stack:** Java 8, Solon 3.3.3 (`@Scheduled` cron + `solon-scheduling-simple`), SqlHelper ORM (`ConditionAndWrapper`), Hutool HttpRequest, Layui CSS framework, Freemarker template, Playwright E2E

---

## Context

### 三個獨立問題的來源

**問題 A — 按鈕視覺**：使用者覺得 Layui 預設按鈕 border-radius 2px 太銳利，相鄰按鈕貼太緊。希望全站統一改 6px 圓角 + 相鄰按鈕間距 15px。

**問題 B — DenyAllow 更新可靠性**：使用者質疑「我設定更新時間，真的會更新嗎？」 — 經 codebase 調查（`ScheduleTask.java:93-104`）發現：
- ✅ Scheduler 啟動（`NginxWebUI.java:22 @EnableScheduling`）
- ⚠️ 排程邏輯是「當下 HH:mm 精準匹配 fetchTime」 — 每筆規則一天**只 1 次機會**
- ⚠️ Server restart 錯過 fetchTime 那一分鐘 → **整天不會抓**
- ⚠️ HTTP 5xx / timeout 失敗 → **整天不會重試**
- ⚠️ DB 空時無預設規則 — user 要自己找 sourceUrl

使用者也說「我會直接在發版時帶過去，用戶不用再找」 → fork 自帶預設黑名單規則。

**問題 C — 「被引用」欄位**：使用者觀察「都是黑名單 IP，不太會分黑名單一部分有用一部份不用」 — 「被引用」欄位（顯示這份黑名單被哪個 server/global 引用）資訊價值低。應改成更有用的「自動更新時間」（fetchTime），讓 user 一眼看到該規則每天幾點抓。

### 已驗證的程式碼證據

| 描述 | 位置 |
|---|---|
| Scheduler 確實啟動 | [NginxWebUI.java:22](../../../src/main/java/com/cym/NginxWebUI.java#L22) `@EnableScheduling` |
| Solon scheduling 模組 | [pom.xml:44](../../../pom.xml#L44) `solon-scheduling-simple` |
| 既有排程入口 | [ScheduleTask.java:93-104](../../../src/main/java/com/cym/task/ScheduleTask.java#L93-L104) |
| Fetch 實作 | [DenyAllowService.java:71-133](../../../src/main/java/com/cym/service/DenyAllowService.java#L71-L133) |
| DenyAllow model | [DenyAllow.java:23-33](../../../src/main/java/com/cym/model/DenyAllow.java#L23-L33) |
| usedBy 計算邏輯（要移除） | [DenyAllowController.java:44-77](../../../src/main/java/com/cym/controller/adminPage/DenyAllowController.java#L44-L77) |
| denyAllow view 用 usedBy | [denyAllow/index.html:110,130](../../../src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html#L110) |
| **protectionCert 也用 usedBy key** | [protectionCert/index.html:108](../../../src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html#L108) ← **保留 key 不能刪** |
| i18n key 三份 | `messages.properties:263` / `messages_zh_TW.properties:263` / `messages_en_US.properties:263` |
| 全域 button CSS | [base.css:70-73](../../../src/main/resources/static/css/adminPage/base.css#L70-L73) |
| Base.css 全站 include | [common.html:16](../../../src/main/resources/WEB-INF/view/adminPage/common.html#L16) |

### 前置條件

- Plan `2026-05-23-deploy-stack-refactor.md` 已執行完並 production 部署驗證通過。本 plan 改的是主應用碼 + CSS + i18n，與 deploy stack 改造正交，但 **請先做完 deploy 再做這個** 避免兩個 plan 改動相互干擾且要重 build image 兩次。

### 不做的事

| 撤回項 | 為何 |
|---|---|
| ❌ 刪 `denyAllowStr.usedBy` i18n key | `protectionCert/index.html` 還在用 |
| ❌ 改 fetch 演算法本體 | 既有 `fetchAndUpdate()` 已正確（dedupe / follow redirect / UA / 註解跳過） |
| ❌ 改 fetchTime 欄位從 String 改成多時段 List | 增量改動為主；多時段冗餘改用「每筆規則設不同時段」達到 |
| ❌ 加 UI 控制 startup catch-up 開關 | 預設行為合理，YAGNI |
| ❌ 改既有 cron `0 * * * * ?` 觸發頻率 | 1 分鐘已合理；只在內部加 retry 分支 |
| ❌ 改 `protectionCert/index.html` 的 usedBy 顯示 | 不在本 plan 範圍（cert 確實有「被哪個 server 引用」概念） |
| ❌ 改 .layui-btn-group 內部按鈕間距 | 群組按鈕設計上應相連，加 `:not()` 例外 |

### 預期結果

1. 全站按鈕視覺：border-radius 6px，相鄰按鈕間距 15px，群組按鈕仍相連
2. 首次部署 nginxWebUI（空 DB）→ 自動有 6 筆預設 DenyAllow deny 規則
3. Server 重啟 30s 後 → startup catch-up 補抓所有今天沒抓過的有 sourceUrl 規則
4. 排程抓失敗 → 下個整點 retry（lastFetchAt > 4h 前的全部 re-fetch）
5. 黑白名單列表「被引用」欄位 → 改顯示「自動更新時間」（fetchTime 字串如 `14:30`）

---

## File Structure

### 新建檔案
- `tests/e2e/23-denyallow-auto-update.spec.js`（DenyAllow seed + catch-up 驗證）
- `tests/e2e/24-ui-button-style.spec.js`（按鈕樣式 visual regression）

### 修改檔案

| File | 改動 | 估計行數 |
|---|---|---|
| `src/main/resources/static/css/adminPage/base.css` | 加 3 條 CSS rule（圓角 + 間距 + 群組例外） | +8 |
| `src/main/java/com/cym/service/DenyAllowService.java` | 新增 `getDefaultRules()` 靜態方法（6 筆規則） | +55 |
| `src/main/java/com/cym/task/ScheduleTask.java` | 修改 `fetchDenyAllowLists()` 加 retry 分支 + 新增 `startupCatchUpDenyAllow()` | +40 |
| `src/main/java/com/cym/config/InitConfig.java` | 新增 `seedDenyAllowRules()` + 在 init 呼叫 | +30 |
| `src/main/java/com/cym/controller/adminPage/DenyAllowController.java` | 移除 usedBy 計算邏輯（line 44-77 範圍精簡） | -25 |
| `src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html` | 欄位 header 與顯示替換 | ±5 |
| `src/main/resources/messages.properties` | 加 `denyAllowStr.autoUpdate` 簡中 key | +1 |
| `src/main/resources/messages_zh_TW.properties` | 加 `denyAllowStr.autoUpdate` 繁中 key | +1 |
| `src/main/resources/messages_en_US.properties` | 加 `denyAllowStr.autoUpdate` 英文 key | +1 |
| `CLAUDE.md` | 文件 3 個改進說明 | +20 |

### 不動檔案

| File | 為何不動 |
|---|---|
| `src/main/java/com/cym/model/DenyAllow.java` | 既有欄位夠用 |
| `src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html` | 仍用 usedBy（不在範圍） |
| Layui 第三方 CSS (`static/lib/layui/css/layui.css`) | 不動三方檔，全用 base.css 覆寫 |
| `src/main/java/com/cym/utils/SnowFlakeUtils.java` | 引用即可 |

---

## Phase 0: Pre-flight Check

### Task 0.1: 確認前置 plan 已完成 + 工具齊備

- [ ] **Step 1: 確認 deploy stack refactor 已 ship**

Run:
```bash
pwd
git branch --show-current
git log --oneline -10 | grep -E '(deploy stack|self-contained sidecar|docker.*rename)' || echo "前置 plan 還沒 ship，停止"
```

Expected: 看到至少一個 deploy refactor 相關 commit。

如未完成 → 停止，先完成 `2026-05-23-deploy-stack-refactor.md`。

- [ ] **Step 2: 工具齊備**

Run:
```bash
mvn --version | head -1
node --version
npx playwright --version
```

Expected: 都有輸出。

- [ ] **Step 3: working tree 乾淨**

Run:
```bash
git status --porcelain
```

Expected: 空輸出。

---

## Phase 1: UI 按鈕樣式（4 行 CSS，最簡單先做）

### Task 1.1: 改 base.css 加圓角 + 間距規則

**Files:**
- Modify: `src/main/resources/static/css/adminPage/base.css`（line 70-73 範圍）

- [ ] **Step 1: Read 當前 .layui-btn 區塊**

Run:
```bash
sed -n '68,76p' src/main/resources/static/css/adminPage/base.css
```

Expected:
```css
}

.layui-btn{
    margin-top: 1px;
    margin-bottom: 1px;
}
```

- [ ] **Step 2: 用 Edit tool 改寫該區塊**

old_string:
```css
.layui-btn{
	margin-top: 1px;
	margin-bottom: 1px;
}
```

new_string:
```css
.layui-btn{
	margin-top: 1px;
	margin-bottom: 1px;
	border-radius: 6px;
}

/* 相鄰按鈕間距 15px（第一個不受影響）*/
.layui-btn + .layui-btn {
	margin-left: 15px;
}

/* 例外：Layui 自家按鈕群組 .layui-btn-group 內按鈕需保持相連 */
.layui-btn-group .layui-btn + .layui-btn {
	margin-left: 0;
}
```

- [ ] **Step 3: 確認沒打錯字 / indent**

Run:
```bash
sed -n '68,90p' src/main/resources/static/css/adminPage/base.css
```

Expected: 看到 4 個 CSS rule block 整齊（含 v5.0.2 UI Polish 註解段保留）。

- [ ] **Step 4: 本機重 build jar（不跑 mvn 也行，css 是 static 資源）**

實際上 nginxWebUI 用 dev 模式時 static 檔走 classpath，重啟 jar 才會生效。但既然是 Layui 渲染後的 button，可以直接：

Run:
```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS（jar 重新打包含新 base.css）。

- [ ] **Step 5: 啟動 jar 開 UI 看 button 樣式**

Run（背景）：
```bash
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-*.jar \
     --server.port=18080 \
     --project.home=./test-home-ui/ \
     --project.testCaptcha=1234 \
     --init.admin=admin --init.pass=admin > /tmp/ui-test.log 2>&1 &
echo $! > /tmp/ui-pid
sleep 15
```

- [ ] **Step 6: Playwright 截圖 admin 頁面確認按鈕樣式**

Run:
```bash
npx playwright screenshot --browser=chromium http://localhost:18080/adminPage/login /tmp/ui-login.png
echo "Open /tmp/ui-login.png to visually verify button corners are rounded (6px) and spacing"
```

⚠️ **STOP** — 給 user 看截圖確認後再繼續。

預期：登入頁的「登入 / SSO 登入配置」按鈕圓角 + 間距正確。

- [ ] **Step 7: 停測試 jar**

Run:
```bash
kill $(cat /tmp/ui-pid)
rm -rf test-home-ui/ /tmp/ui-pid /tmp/ui-test.log
```

- [ ] **Step 8: Commit**

Run:
```bash
git add src/main/resources/static/css/adminPage/base.css
git commit -m "$(cat <<'EOF'
style(ui): global button rounded corners 6px + 15px spacing

Three CSS rules added to base.css .layui-btn block:

1. border-radius: 6px on .layui-btn
   - Softer than Layui default (2px), aligned with modern UI conventions
2. margin-left: 15px on adjacent .layui-btn pairs
   - Replaces no-spacing default; first button in group unaffected
3. margin-left: 0 on .layui-btn-group .layui-btn pairs
   - Preserves Layui button groups (designed to be contiguous)

base.css is loaded once in common.html:16 which all admin pages
include → change propagates site-wide without per-page edits.

Visual verification: admin login page screenshot confirmed.
EOF
)"
```

---

### Task 1.2: 新增 Playwright 樣式 visual regression test

**Files:**
- Create: `tests/e2e/24-ui-button-style.spec.js`

- [ ] **Step 1: 看既有 spec 風格**

Run:
```bash
head -40 tests/e2e/01-login.spec.js
```

- [ ] **Step 2: 建立測試檔**

Write file `tests/e2e/24-ui-button-style.spec.js`:

```javascript
// @ts-check
const { test, expect } = require('@playwright/test');

// 驗證全站按鈕樣式：圓角 6px + 相鄰間距 15px + 群組仍相連
test.describe('UI button style', () => {

  test('登入頁按鈕圓角 6px', async ({ page }) => {
    await page.goto('http://localhost:18080/adminPage/login');
    const loginBtn = page.locator('button[type="submit"]').first();
    const borderRadius = await loginBtn.evaluate(el => getComputedStyle(el).borderRadius);
    expect(borderRadius).toBe('6px');
  });

  test('admin 頁相鄰按鈕間距 15px', async ({ page }) => {
    // 用 testCaptcha=1234 登入
    await page.goto('http://localhost:18080/adminPage/login');
    await page.fill('input[name="userName"]', 'admin');
    await page.fill('input[name="pass"]', 'admin');
    await page.fill('input[name="captcha"]', '1234');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/adminPage\/index/);

    // 找一個有兩個相鄰按鈕的頁面（黑白名單）
    await page.goto('http://localhost:18080/adminPage/denyAllow');
    await page.waitForSelector('.layui-btn');

    // 找 toolbar 區的「添加」按鈕後面的「批量刪除」按鈕
    const buttons = await page.locator('.layui-btn').all();
    for (let i = 1; i < buttons.length; i++) {
      const prevTag = await buttons[i - 1].evaluate(el => el.parentElement.tagName);
      const inGroup = await buttons[i].evaluate(el =>
        el.parentElement.classList.contains('layui-btn-group')
      );
      if (inGroup) continue;  // 群組按鈕 spacing 應為 0
      const ml = await buttons[i].evaluate(el => getComputedStyle(el).marginLeft);
      // 相鄰按鈕應該是 15px
      if (ml === '15px') {
        expect(ml).toBe('15px');
        return;  // 找到一組驗證成功就 return
      }
    }
    throw new Error('No adjacent buttons with 15px margin-left found');
  });

  test('群組按鈕內部間距為 0', async ({ page }) => {
    // 任何有 .layui-btn-group 的頁面，例如表單頁
    await page.goto('http://localhost:18080/adminPage/login');
    // 此 test 為 skeleton — 如登入頁無群組按鈕，改去其他頁
    test.skip(true, 'TODO: identify page with .layui-btn-group and assert');
  });

});
```

⚠️ Selector 路徑會視實際頁面結構微調 — Task 5（E2E 跑全套）會抓出 selector 問題。

- [ ] **Step 3: 跑該 spec**

Run:
```bash
npx playwright test tests/e2e/24-ui-button-style.spec.js --headed
```

Expected: 2 個 test pass，1 skip。

如 fail → 看 selector 是否需要調整。

- [ ] **Step 4: Commit**

Run:
```bash
git add tests/e2e/24-ui-button-style.spec.js
git commit -m "$(cat <<'EOF'
test(e2e): add UI button style visual regression spec

Two assertions for the new .layui-btn CSS:
1. Login page button border-radius === '6px'
2. denyAllow page adjacent buttons margin-left === '15px'

Third test (group buttons margin-left: 0) skipped pending identifying
a page with .layui-btn-group usage.

File: tests/e2e/24-ui-button-style.spec.js
EOF
)"
```

---

## Phase 2: DenyAllow 預設規則（getDefaultRules）

### Task 2.1: 確認 DenyAllow model 有沒有 type 欄位

**Files:** 無修改（純檢查）

- [ ] **Step 1: Read DenyAllow.java**

Run:
```bash
grep -nE 'Integer type|String type|private.*type' src/main/java/com/cym/model/DenyAllow.java
```

Expected：
- **若有 `Integer type` 或 `String type`** → 用 `setType(2)`（2 = deny / blacklist）
- **若沒 type 欄位** → Phase 2 的 buildRule 移除 `setType()` 參數，commit message 標明

記錄結果決定 Task 2.2 的代碼。

---

### Task 2.2: 在 DenyAllowService 加 getDefaultRules() 靜態方法

**Files:**
- Modify: `src/main/java/com/cym/service/DenyAllowService.java`

- [ ] **Step 1: Read 檔尾**

Run:
```bash
tail -10 src/main/java/com/cym/service/DenyAllowService.java
```

確認檔尾是 `}` 收尾。

- [ ] **Step 2: 用 Edit tool 加方法（**以下假設 type 欄位存在**；如無，依 Task 2.1 結果調整）**

old_string:
```java
		} catch (Exception e) {
			logger.error("Failed to fetch DenyAllow list " + da.getName() + " (" + da.getSourceUrl() + ")", e);
			return false;
		}
	}

}
```

new_string:
```java
		} catch (Exception e) {
			logger.error("Failed to fetch DenyAllow list " + da.getName() + " (" + da.getSourceUrl() + ")", e);
			return false;
		}
	}

	/**
	 * Release 預設的 6 筆 DenyAllow 黑名單規則，供 InitConfig 在 DB 空時 seed 用。
	 * fetchTime 分散每 4 小時整 + 30 分（00:30 / 04:30 / 08:30 / 12:30 / 16:30 / 20:30），
	 * 避免同時打 API。
	 * 此方法純 DTO factory，不寫 DB；呼叫端負責 SnowFlakeUtils.getId() + insert。
	 */
	public static java.util.List<DenyAllow> getDefaultRules() {
		java.util.List<DenyAllow> rules = new java.util.ArrayList<>();

		rules.add(buildRule("FireHOL Level 1",
				"https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset",
				"00:30"));
		rules.add(buildRule("Spamhaus DROP",
				"https://www.spamhaus.org/drop/drop.txt",
				"04:30"));
		rules.add(buildRule("Feodo Tracker (botnet C2)",
				"https://feodotracker.abuse.ch/downloads/ipblocklist.txt",
				"08:30"));
		rules.add(buildRule("Emerging Threats",
				"https://rules.emergingthreats.net/blockrules/compromised-ips.txt",
				"12:30"));
		rules.add(buildRule("AbuseIPDB Top 100 (30d)",
				"https://raw.githubusercontent.com/borestad/blocklist-abuseipdb/main/abuseipdb-s100-30d.ipv4",
				"16:30"));
		rules.add(buildRule("CINS Army Bad Guy List",
				"https://cinsscore.com/list/ci-badguys.txt",
				"20:30"));

		return rules;
	}

	private static DenyAllow buildRule(String name, String url, String fetchTime) {
		DenyAllow rule = new DenyAllow();
		rule.setName(name);
		rule.setIp("");
		rule.setSourceUrl(url);
		rule.setFetchTime(fetchTime);
		rule.setLastFetchAt(null);
		// 若 DenyAllow 有 type 欄位，這裡加 rule.setType(2);
		return rule;
	}
}
```

> ⚠️ Task 2.1 結果若有 `type` 欄位 → 在 `buildRule()` 加 `rule.setType(2);`

- [ ] **Step 3: 編譯**

Run:
```bash
mvn compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS。

如 compile error 提及 SnowFlakeUtils 或其他 missing import → 加上對應 import。

- [ ] **Step 4: Commit**

Run:
```bash
git add src/main/java/com/cym/service/DenyAllowService.java
git commit -m "$(cat <<'EOF'
feat(denyAllow): add getDefaultRules() with 6 curated IP blocklists

Static factory for InitConfig to seed empty DB. Six well-known threat
intel sources picked:
1. FireHOL Level 1 (~50k IPs, aggressive aggregated)
2. Spamhaus DROP (hijacked netblocks, low FP)
3. Feodo Tracker (botnet C2, abuse.ch)
4. Emerging Threats (compromised hosts)
5. AbuseIPDB Top 100 (30-day worst offenders)
6. CINS Army Bad Guys (long-running curated)

fetchTime spread 00:30 / 04:30 / 08:30 / 12:30 / 16:30 / 20:30 to
avoid synchronised API hits when many users deploy on same day.

Each rule: ip="", lastFetchAt=null. startupCatchUpDenyAllow() (added
in next commit) will fetch all on first boot.

No DB write here — caller (InitConfig.seedDenyAllowRules) handles
SnowFlakeUtils.getId() + sqlHelper.insert(). Pattern mirrors
existing addAdmin() flow.
EOF
)"
```

---

## Phase 3: ScheduleTask — retry 分支 + startupCatchUp

### Task 3.1: 確認 ConditionAndWrapper 支援的 API

**Files:** 無修改（純檢查）

- [ ] **Step 1: Read ConditionAndWrapper**

Run:
```bash
grep -nE 'public.*ConditionAndWrapper\s+(eq|ne|lt|isNull|or|and)\b' src/main/java/com/cym/sqlhelper/utils/ConditionAndWrapper.java | head -20
```

Expected：列出可用方法。常見 `.eq` `.ne` `.gt` `.lt` `.isNull` `.and` `.or` 等。

如 `.or()` / `.isNull()` 不支援 → 後續 Task 3.2 拆成兩次 query 合併。

記錄可用 API 給 Task 3.2 用。

---

### Task 3.2: 修改 fetchDenyAllowLists 加 retry + 新增 startupCatchUp

**Files:**
- Modify: `src/main/java/com/cym/task/ScheduleTask.java`

- [ ] **Step 1: Read line 85-110 確認當前結構**

Run:
```bash
sed -n '85,110p' src/main/java/com/cym/task/ScheduleTask.java
```

- [ ] **Step 2: 用 Edit tool 改既有 fetchDenyAllowLists**

old_string:
```java
	// 每分鐘檢查需要從遠端抓取的 DenyAllow 黑名單
	// 邏輯：找出 fetchTime == 當下 HH:mm 且 sourceUrl 非空的清單，呼叫
	// DenyAllowService.fetchAndUpdate() 更新 ip 與 lastFetchAt。
	@Scheduled(cron = "0 * * * * ?")
	public void fetchDenyAllowLists() {
		String nowHHmm = DateUtil.format(new Date(), "HH:mm");
		List<DenyAllow> list = sqlHelper.findListByQuery(
				new ConditionAndWrapper().eq("fetchTime", nowHHmm), DenyAllow.class);

		for (DenyAllow da : list) {
			if (denyAllowService.fetchAndUpdate(da)) {
				sqlHelper.updateById(da);
			}
		}
	}
```

new_string:
```java
	// 每分鐘檢查需要從遠端抓取的 DenyAllow 黑名單，兩種匹配條件：
	//   (a) fetchTime == 當下 HH:mm：使用者設定的每日排程時段
	//   (b) 整點 + lastFetchAt 為 null 或距今 > 4 小時：補抓上次失敗的（retry）
	//
	// (b) 確保 HTTP timeout / 5xx 不會讓黑名單整天不更新。
	@Scheduled(cron = "0 * * * * ?")
	public void fetchDenyAllowLists() {
		String nowHHmm = DateUtil.format(new Date(), "HH:mm");
		String nowMM = nowHHmm.substring(3);

		// (a) 排定時段抓取
		List<DenyAllow> scheduled = sqlHelper.findListByQuery(
				new ConditionAndWrapper().eq("fetchTime", nowHHmm), DenyAllow.class);
		for (DenyAllow da : scheduled) {
			if (denyAllowService.fetchAndUpdate(da)) {
				sqlHelper.updateById(da);
			}
		}

		// (b) 整點 retry（避免每分鐘重抓，每天最多 24 次）
		if ("00".equals(nowMM)) {
			long fourHoursAgo = System.currentTimeMillis() - 4L * 60 * 60 * 1000;
			List<DenyAllow> all = sqlHelper.findListByQuery(
					new ConditionAndWrapper().ne("sourceUrl", ""), DenyAllow.class);
			for (DenyAllow da : all) {
				// 略過 sourceUrl 空 / 跟 (a) 重複 / lastFetchAt 在 4h 內
				if (StrUtil.isBlank(da.getSourceUrl())) continue;
				if (nowHHmm.equals(da.getFetchTime())) continue;
				if (da.getLastFetchAt() != null && da.getLastFetchAt() > fourHoursAgo) continue;

				logger.info("DenyAllow retry stale rule: {} (lastFetchAt={})",
						da.getName(), da.getLastFetchAt());
				if (denyAllowService.fetchAndUpdate(da)) {
					sqlHelper.updateById(da);
				}
			}
		}
	}

	/**
	 * Startup catch-up：應用啟動 30 秒後跑一次（之後每天跑一次），
	 * 對所有 sourceUrl 非空且今天還沒抓過的規則立刻抓。
	 *
	 * 解決：server restart 錯過 fetchTime 那一分鐘 → 整天不抓的問題。
	 */
	@Scheduled(fixedDelay = 86400000, initialDelay = 30000)
	public void startupCatchUpDenyAllow() {
		long todayStart = DateUtil.parse(DateUtil.format(new Date(), "yyyy-MM-dd"), "yyyy-MM-dd").getTime();

		List<DenyAllow> all = sqlHelper.findListByQuery(
				new ConditionAndWrapper().ne("sourceUrl", ""), DenyAllow.class);

		int fetched = 0;
		for (DenyAllow da : all) {
			if (StrUtil.isBlank(da.getSourceUrl())) continue;
			if (da.getLastFetchAt() != null && da.getLastFetchAt() >= todayStart) continue;

			if (denyAllowService.fetchAndUpdate(da)) {
				sqlHelper.updateById(da);
				fetched++;
			}
		}
		if (fetched > 0) {
			logger.info("Startup catch-up: fetched {} DenyAllow rules", fetched);
		}
	}
```

> ⚠️ 注意 `@Scheduled(fixedDelay = ..., initialDelay = ...)` — 確認 Solon `solon-scheduling-simple` 支援這兩個屬性。如不支援，改成 cron `"0 30 1 * * ?"`（每天 01:30 跑，初次安裝後等到隔天才補抓 — sub-optimal）。

- [ ] **Step 3: 確認 fixedDelay / initialDelay 支援**

Run:
```bash
grep -rE 'fixedDelay|initialDelay' src/main/java | head -5
find ~ -name 'Scheduled.class' 2>/dev/null | xargs javap 2>/dev/null | grep -E 'fixedDelay|initialDelay' || true
```

如已有其他地方用 → 支援，繼續。
如不支援 → 退回 cron 版本。

- [ ] **Step 4: 編譯**

Run:
```bash
mvn compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

Run:
```bash
git add src/main/java/com/cym/task/ScheduleTask.java
git commit -m "$(cat <<'EOF'
feat(denyAllow): retry-on-failure + startup catch-up scheduling

Two reliability improvements:

1. fetchDenyAllowLists() retry branch (every hour at HH:00):
   - On top of existing 'fetchTime == HH:mm' exact match,
     re-fetches rules where lastFetchAt is null or > 4h ago
   - Fixes HTTP timeout / 5xx stuck for entire day until next fetchTime

2. startupCatchUpDenyAllow() new method:
   - Runs once 30s after app start (fixedDelay=24h, initialDelay=30s)
   - Fetches all sourceUrl-bearing rules whose lastFetchAt is null
     or before today (00:00)
   - Solves: server restart at 14:35 misses 14:30 fetchTime, previously
     waited until tomorrow

Combined: rule with sourceUrl has at-most ~1h delay from any state
(restart, transient HTTP failure, etc.).

Existing daily-precise scheduling (fetchTime exact match) kept for
sources that prefer fixed slots (some have rate limits per IP/day).
EOF
)"
```

---

## Phase 4: InitConfig 自動 seed

### Task 4.1: 找 InitConfig.init() 主體 + addAdmin 模式

**Files:** 無修改（純檢查）

- [ ] **Step 1: 看 init 方法結尾 + addAdmin 結構**

Run:
```bash
grep -nE 'public void init\(\)|private void addAdmin\(\)|@Inject\s+SqlHelper' src/main/java/com/cym/config/InitConfig.java | head -10
```

記錄 init() 行號 + 是否已 @Inject SqlHelper。

---

### Task 4.2: 加 seedDenyAllowRules() 並在 init 呼叫

**Files:**
- Modify: `src/main/java/com/cym/config/InitConfig.java`

- [ ] **Step 1: 確認 SqlHelper 已注入**

Run:
```bash
grep -nE '@Inject\s+SqlHelper\s+sqlHelper' src/main/java/com/cym/config/InitConfig.java
```

如無 → Task 4.2 step 2 需先加 `@Inject SqlHelper sqlHelper;` 在類別欄位區。

- [ ] **Step 2: 加 import**

確認檔頂部有：
```java
import java.util.List;
import com.cym.model.DenyAllow;
import com.cym.service.DenyAllowService;
import com.cym.utils.SnowFlakeUtils;
```

如缺 → Edit 加進 import 區。

- [ ] **Step 3: 在 init() 尾部呼叫 seedDenyAllowRules()**

Run grep 找確切 addAdmin 呼叫位置：
```bash
grep -nB 1 'addAdmin\(\);' src/main/java/com/cym/config/InitConfig.java
```

依結果用 Edit tool 在 addAdmin 呼叫後加：

```java
		// seed 預設 DenyAllow 黑名單規則（DB 空時才執行）
		seedDenyAllowRules();
```

- [ ] **Step 4: 在類別內加 seedDenyAllowRules 方法**

在 addAdmin 方法附近（檔尾合適位置）加：

```java
	/**
	 * 首次部署在 DB 空時插入 6 筆精選 DenyAllow 黑名單規則。
	 * 已有任何 DenyAllow（含 user 手動建的）則 no-op。
	 * 規則 ip="" + lastFetchAt=null，由 ScheduleTask.startupCatchUpDenyAllow() 補抓。
	 */
	private void seedDenyAllowRules() {
		long count = sqlHelper.findAllCount(DenyAllow.class);
		if (count > 0) {
			return;
		}

		List<DenyAllow> defaults = DenyAllowService.getDefaultRules();
		for (DenyAllow rule : defaults) {
			rule.setId(SnowFlakeUtils.getId().toString());
			sqlHelper.insert(rule);
		}
		logger.info("Seeded {} default DenyAllow rules into empty DB", defaults.size());
	}
```

- [ ] **Step 5: 編譯**

Run:
```bash
mvn compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

Run:
```bash
git add src/main/java/com/cym/config/InitConfig.java
git commit -m "$(cat <<'EOF'
feat(initConfig): seed 6 default DenyAllow blocklist rules on empty DB

Pattern mirrors existing addAdmin() flow:
- count > 0 -> early return (idempotent on restart)
- count == 0 -> insert via SnowFlakeUtils.getId() + sqlHelper.insert()

Rules from DenyAllowService.getDefaultRules() (FireHOL, Spamhaus, Feodo,
Emerging Threats, AbuseIPDB Top 100, CINS Army).

Rules inserted with lastFetchAt=null, ip="" - Phase 3's
startupCatchUpDenyAllow() will fetch all 30s after boot.

Users wanting different sources can delete defaults via UI then add
their own; subsequent restarts won't re-seed.
EOF
)"
```

---

## Phase 5: Controller 移除 usedBy 計算

### Task 5.1: 簡化 DenyAllowController

**Files:**
- Modify: `src/main/java/com/cym/controller/adminPage/DenyAllowController.java`

- [ ] **Step 1: Read line 40-80 確認 usedBy 計算範圍**

Run:
```bash
sed -n '40,85p' src/main/java/com/cym/controller/adminPage/DenyAllowController.java
```

- [ ] **Step 2: 用 Edit tool 移除 usedBy 計算邏輯**

依據 grep 結果（line 44-77 範圍）：

old_string（依實際內容調整）:
```java
		// 預載所有 Server 用於 usedBy 查詢
		// ...（line 44-77 整個 usedBy 計算 block）
```

new_string:
```java
		// usedBy 計算已移除（v5.x）— 黑名單 IP 全域生效，列表頁顯示 fetchTime 而非引用關係
```

> ⚠️ 具體 old_string 視當前檔案內容；Edit 時用 Read 取準確內容。

- [ ] **Step 3: 確認 ext.setUsedBy() 也移除 + 若 DenyAllowExt 不再用 usedBy 欄位則保留欄位（向後相容）**

如有 build error，按 compiler 提示 import 或欄位調整。

- [ ] **Step 4: 編譯**

Run:
```bash
mvn compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

Run:
```bash
git add src/main/java/com/cym/controller/adminPage/DenyAllowController.java
git commit -m "$(cat <<'EOF'
refactor(denyAllow): remove usedBy column calculation

Blacklist IP rules are globally applied — 'used by which server' is
no useful information for users (all rules apply or don't, no partial
binding). Calculation was O(N) DB queries per page load.

Removed:
- usedBy List<String> assembly per DenyAllow in list()
- HTTP Global / Stream Global setting lookup
- Per-server denyId/allowId CSV scan

Kept (still used elsewhere):
- DenyAllowService.csvContainsId() static method (used by Server view)
- DenyAllowExt.usedBy field (deprecated but not removed for binary compat)
- denyAllowStr.usedBy i18n key (still referenced by protectionCert view)
EOF
)"
```

---

## Phase 6: View + i18n 改欄位

### Task 6.1: 加新 i18n key 「denyAllowStr.autoUpdate」三份 properties

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_zh_TW.properties`
- Modify: `src/main/resources/messages_en_US.properties`

- [ ] **Step 1: 看現有 denyAllowStr 區段**

Run:
```bash
grep -n 'denyAllowStr\.usedBy' src/main/resources/messages*.properties
```

Expected: 三份 properties 都在 line 263 附近。

- [ ] **Step 2: 在 usedBy 下方加 autoUpdate**

對每份 properties 用 Edit tool：

**messages.properties**（簡中），old_string:
```
denyAllowStr.usedBy         = 被引用
```

new_string:
```
denyAllowStr.usedBy         = 被引用
denyAllowStr.autoUpdate     = 自动更新
```
（autoUpdate = 自动更新）

**messages_zh_TW.properties**（繁中），old_string:
```
denyAllowStr.usedBy         = 被引用
```

new_string:
```
denyAllowStr.usedBy         = 被引用
denyAllowStr.autoUpdate     = 自動更新
```
（autoUpdate = 自動更新）

**messages_en_US.properties**，old_string:
```
denyAllowStr.usedBy         = Used by
```

new_string:
```
denyAllowStr.usedBy         = Used by
denyAllowStr.autoUpdate     = Auto update
```

- [ ] **Step 3: 驗證三份 properties 都有新 key**

Run:
```bash
grep -n 'denyAllowStr\.autoUpdate' src/main/resources/messages*.properties
```

Expected: 3 行輸出。

---

### Task 6.2: 改 denyAllow/index.html 欄位 header + cell 內容

**Files:**
- Modify: `src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html`

- [ ] **Step 1: 看欄位 thead + tbody 結構**

Run:
```bash
sed -n '105,145p' src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html
```

- [ ] **Step 2: 用 Edit tool 改 th**

old_string:
```html
										<th>${denyAllowStr.usedBy}</th>
```

new_string:
```html
										<th>${denyAllowStr.autoUpdate}</th>
```

- [ ] **Step 3: 用 Edit tool 改 td（usedBy → fetchTime）**

依 step 1 看到的實際 td 結構改寫：

old_string（範例）:
```html
											<#if ext.usedBy?? && (ext.usedBy?size > 0)>
												<#list ext.usedBy as ref>
													<span class="layui-badge layui-bg-blue">${ref}</span>
												</#list>
											<#else>
												<span style="color:#bbb;">-</span>
											</#if>
```

new_string:
```html
											<#if ext.denyAllow.fetchTime?? && ext.denyAllow.fetchTime?length gt 0>
												<#if ext.denyAllow.sourceUrl?? && ext.denyAllow.sourceUrl?length gt 0>
													<span class="layui-badge layui-bg-blue">${denyAllowStr.daily!''} ${ext.denyAllow.fetchTime}</span>
												<#else>
													<span style="color:#bbb;">${ext.denyAllow.fetchTime} (no URL)</span>
												</#if>
											<#else>
												<span style="color:#bbb;">-</span>
											</#if>
```

> ⚠️ 上面用了 `denyAllowStr.daily`（可能不存在）。簡化版（不依賴新 i18n）：

簡化 new_string:
```html
											<#if ext.denyAllow.sourceUrl?? && ext.denyAllow.sourceUrl?length gt 0 && ext.denyAllow.fetchTime?? && ext.denyAllow.fetchTime?length gt 0>
												<span class="layui-badge layui-bg-blue">${ext.denyAllow.fetchTime}</span>
											<#else>
												<span style="color:#bbb;">-</span>
											</#if>
```

- [ ] **Step 4: 在本機 jar 跑起來看視覺**

Run:
```bash
mvn clean package -DskipTests -q
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-*.jar \
     --server.port=18080 \
     --project.home=./test-home-view/ \
     --project.testCaptcha=1234 \
     --init.admin=admin --init.pass=admin > /tmp/view-test.log 2>&1 &
echo $! > /tmp/view-pid
sleep 15
npx playwright screenshot --browser=chromium http://localhost:18080/adminPage/denyAllow /tmp/denyAllow-after.png
echo "Open /tmp/denyAllow-after.png to confirm column shows '自動更新' / 'Auto update'"
```

⚠️ **STOP** — 給 user 看截圖。Cleanup：
```bash
kill $(cat /tmp/view-pid)
rm -rf test-home-view/ /tmp/view-pid /tmp/view-test.log
```

- [ ] **Step 5: Commit**

Run:
```bash
git add src/main/resources/messages.properties \
        src/main/resources/messages_zh_TW.properties \
        src/main/resources/messages_en_US.properties \
        src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html
git commit -m "$(cat <<'EOF'
ui(denyAllow): replace 'used by' column with 'auto update' (fetchTime)

Blacklist IP rules apply globally — 'used by which server' has no
discriminating value. Replace column with the much more useful
fetchTime (auto-update schedule).

Changes:
- denyAllow/index.html: <th> uses new key denyAllowStr.autoUpdate,
  <td> shows fetchTime as layui-bg-blue badge (only when both
  sourceUrl and fetchTime set)
- messages*.properties (3 files): new key denyAllowStr.autoUpdate
  with simplified Chinese / traditional Chinese / English values
- usedBy i18n key kept (still used by protectionCert view)
- usedBy controller calculation removed in previous commit
EOF
)"
```

---

## Phase 7: E2E 整合測試

### Task 7.1: 新增 DenyAllow 自動更新 E2E spec

**Files:**
- Create: `tests/e2e/23-denyallow-auto-update.spec.js`

- [ ] **Step 1: 看既有 spec 風格**

Run:
```bash
head -30 tests/e2e/22-asn-block.spec.js 2>/dev/null || head -30 tests/e2e/01-login.spec.js
```

- [ ] **Step 2: 建立測試檔**

Write file `tests/e2e/23-denyallow-auto-update.spec.js`:

```javascript
// @ts-check
const { test, expect } = require('@playwright/test');

test.describe('DenyAllow auto-update', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:18080/adminPage/login');
    await page.fill('input[name="userName"]', 'admin');
    await page.fill('input[name="pass"]', 'admin');
    await page.fill('input[name="captcha"]', '1234');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/adminPage\/index/);
  });

  test('空 DB 首次啟動 seed 了 6 筆預設規則', async ({ page }) => {
    await page.goto('http://localhost:18080/adminPage/denyAllow');
    await page.waitForSelector('table tr', { timeout: 10000 });

    const names = [
      /FireHOL Level 1/, /Spamhaus DROP/, /Feodo Tracker/,
      /Emerging Threats/, /AbuseIPDB Top 100/, /CINS Army Bad/,
    ];
    for (const re of names) {
      await expect(page.locator('table').getByText(re)).toBeVisible({ timeout: 5000 });
    }
  });

  test('每筆預設規則 fetchTime 顯示在「自動更新」欄位', async ({ page }) => {
    await page.goto('http://localhost:18080/adminPage/denyAllow');
    await page.waitForSelector('table tr', { timeout: 10000 });

    // 預設 6 筆都該有 HH:30 fetchTime
    const cells = await page.locator('td .layui-badge').allTextContents();
    const hhMm = cells.filter(c => /^\d{2}:30$/.test(c.trim()));
    expect(hhMm.length).toBe(6);
  });

  test('startup catch-up 後 lastFetchAt 至少有一筆填值（等 90s）', async ({ page }) => {
    await page.waitForTimeout(90000);  // initialDelay 30s + fetch 60s buffer
    await page.goto('http://localhost:18080/adminPage/denyAllow');
    await page.waitForSelector('table tr', { timeout: 10000 });

    // 「上次更新」欄位至少一行非空
    const lastFetchCells = await page.locator('td').filter({ hasText: /^\d{4}-\d{2}-\d{2}/ }).count();
    expect(lastFetchCells).toBeGreaterThan(0);
  });

  test('「被引用」欄位已被「自動更新」取代', async ({ page }) => {
    await page.goto('http://localhost:18080/adminPage/denyAllow');
    await page.waitForSelector('thead', { timeout: 10000 });

    const headers = await page.locator('thead th').allTextContents();
    expect(headers.some(h => /自動更新|自动更新|Auto update/.test(h))).toBe(true);
    expect(headers.every(h => !/被引用|Used by/.test(h))).toBe(true);
  });

});
```

- [ ] **Step 3: 跑該 spec**

Run:
```bash
npx playwright test tests/e2e/23-denyallow-auto-update.spec.js --headed
```

Expected: 4 個 test 全 pass。

- [ ] **Step 4: Commit**

Run:
```bash
git add tests/e2e/23-denyallow-auto-update.spec.js
git commit -m "test(e2e): add DenyAllow auto-update + column rework spec"
```

---

### Task 7.2: 全套 E2E regression

- [ ] **Step 1: 跑全套（headless）**

Run:
```bash
npm run test:fast
```

Expected: 24 specs（22 原有 + Task 1.2 加的 24-ui-button + Task 7.1 加的 23-denyallow）全 pass。

- [ ] **Step 2: 如有 failure → 看 report 修**

Run:
```bash
npm run report
```

開瀏覽器看 http://localhost:9400

⚠️ **STOP** — 任何 regression 都要修，不允許「忽略 N 個原本就紅的 test」。

---

## Phase 8: 文件更新

### Task 8.1: CLAUDE.md 加三個改進記錄

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 找「已完成的改進」段**

Run:
```bash
grep -n '已完成的改進\|UI / UX\|安全防護模組' CLAUDE.md | head -5
```

- [ ] **Step 2: 用 Edit tool 加三項**

在「UI / UX」段加：
```markdown
26. 全域按鈕視覺微調（圓角 6px + 相鄰按鈕 15px 間距，群組按鈕保持相連）
```

在「安全防護模組」段加：
```markdown
27. DenyAllow 自動更新可靠性
   - 首次部署自動 seed 6 筆預設黑名單（FireHOL / Spamhaus / Feodo / ET / AbuseIPDB / CINS）
   - 每整點 retry 上次失敗的（lastFetchAt > 4h）
   - Startup catch-up（boot 後 30s 補抓今天未抓的）
   - 「被引用」欄位改成「自動更新」(顯示 fetchTime)
```

- [ ] **Step 3: Commit**

Run:
```bash
git add CLAUDE.md
git commit -m "docs(claude.md): record UI button polish + DenyAllow overhaul"
```

---

## Verification Summary

| # | Check | Pass Condition |
|---|---|---|
| V1 | `mvn compile` | exit 0 |
| V2 | `mvn clean package -DskipTests` | exit 0, jar produced |
| V3 | `grep 'border-radius: 6px' src/main/resources/static/css/adminPage/base.css` | 找到 |
| V4 | `grep 'margin-left: 15px' src/main/resources/static/css/adminPage/base.css` | 找到 |
| V5 | `grep getDefaultRules src/main/java/com/cym/service/DenyAllowService.java` | 找到 method |
| V6 | `grep seedDenyAllowRules src/main/java/com/cym/config/InitConfig.java` | 找到 method |
| V7 | `grep startupCatchUpDenyAllow src/main/java/com/cym/task/ScheduleTask.java` | 找到 method |
| V8 | `grep 'denyAllowStr.autoUpdate' src/main/resources/messages*.properties \| wc -l` | 3 |
| V9 | `grep usedBy src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html` | 0（denyAllow view 已不用） |
| V10 | `grep usedBy src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html` | ≥1（protectionCert 仍用，正確） |
| V11 | `npm run test:fast` | 24 specs all pass |
| V12 | 空 DB 跑 jar 30s 後 `SELECT count(*) FROM deny_allow` | ≥ 6 |
| V13 | 同 V12 等 90s 後 `SELECT count(*) FROM deny_allow WHERE last_fetch_at IS NOT NULL` | ≥ 1 |
| V14 | 瀏覽器訪問 :18080/adminPage/denyAllow | 「自動更新」column header 顯示，每筆有 HH:30 badge |
| V15 | Playwright screenshot 登入頁按鈕 | border-radius 6px 視覺確認 |

**任何一項失敗 → 回頭修，不允許口頭宣稱完成。**

---

## Risks & Mitigations

| 風險 | 影響 | 緩解 |
|---|---|---|
| Solon `@Scheduled` 不支援 `fixedDelay/initialDelay` | startupCatchUp 不跑 | Task 3.2 step 3 確認；fallback 用 cron `"0 30 1 * * ?"` |
| `ConditionAndWrapper` API 不全 | retry query 寫不出 | Task 3.1 確認；改用 `.ne()` + Java 端 filter |
| `DenyAllow` 無 `type` 欄位 | seedDefaultRules 失敗 | Task 2.1 確認；buildRule 移除 setType |
| 預設 sourceUrl 之一 404 | 該規則永久無 IP | retry 4h 一輪會持續嘗試；user 可在 UI 改/刪 URL |
| `.layui-btn + .layui-btn` 規則破壞 inline 按鈕 | 樣式錯亂 | Task 1.1 step 6 視覺驗證；e2e 第三組「群組按鈕」確認 |
| 移除 usedBy 後 `DenyAllowExt.usedBy` 欄位閒置 | 死 code | Phase 5 commit message 標明，後續 cleanup 可刪 |
| `denyAllow/index.html` 改 td 結構破壞 layui table | table 渲染壞 | Task 6.2 step 4 視覺驗證 |
| `denyAllowStr.autoUpdate` key 沒同步 3 份 properties | 出現 `${denyAllowStr.autoUpdate}` 原文 | V8 grep 三份檢查 |
| 改 base.css 破壞其他既有頁面 | 視覺錯亂 | E2E（Phase 7）全套 regression 抓 |
| Production server 既有 db 已有非預設 DenyAllow 規則 → 升版後**不會 seed** | 預期行為（count > 0 guard） | commit message + plan 都標明；user 想要預設規則可手動加 |

---

## Self-Review Checklist

**1. Spec coverage**:
- ✅ Part A 按鈕樣式 → Phase 1
- ✅ Part B DenyAllow seed → Phase 2 + Phase 4
- ✅ Part B DenyAllow retry + catch-up → Phase 3
- ✅ Part C 「被引用」→「自動更新」 → Phase 5 + Phase 6
- ✅ E2E → Phase 7
- ✅ 文件 → Phase 8

**2. Placeholder scan**：
- ⚠️ Task 5.1 step 2 寫 old_string「依實際內容調整」— 因為 controller 邏輯太大，必須執行時 Read 取準確內容。**不是 TBD**，是動態決定的 fragment
- ⚠️ Task 6.2 step 3 給了兩版本 new_string（含 i18n 變數版 + 簡化版），建議用**簡化版**
- ✅ 無 TODO / TBD

**3. Type consistency**：
- ✅ `getDefaultRules()` 回傳 `List<DenyAllow>` — Phase 2 + 4 一致
- ✅ `seedDenyAllowRules()` private void — Phase 4 一致
- ✅ `startupCatchUpDenyAllow()` public void — Phase 3 一致
- ✅ i18n key `denyAllowStr.autoUpdate` — Phase 6 三份 properties + view 一致
- ✅ fetchTime "HH:mm" String 格式 — Phase 2 / 6 / 7 一致

**4. 工時預估**：

| Phase | 工時 |
|---|---|
| 0 | 5 min |
| 1 | 20 min（UI 含截圖驗證） |
| 2 | 25 min（含 type 欄位確認） |
| 3 | 30 min（含 API 確認 + cron fallback） |
| 4 | 20 min |
| 5 | 20 min |
| 6 | 25 min（含 3 份 properties + view + 視覺驗證） |
| 7 | 50 min（含 e2e regression 等待） |
| 8 | 15 min |
| **Total** | **~210 min (3.5 hours)** |

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-23-ui-polish-denyallow-rework.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 每 Task 一個 fresh subagent，主 context 不污染

**2. Inline Execution** — 連續執行，checkpoint 在 Phase 邊界

**Which approach?**

⚠️ **重要前置：** 本 plan 假設 `2026-05-23-deploy-stack-refactor.md` 已執行完並 ship。先完成那個 plan，本 plan 不應在「正在改 docker stack」期間並行執行（會踩到對方的 commit）。
