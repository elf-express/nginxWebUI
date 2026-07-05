# http 參數 panel Phase 3 三態 mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** http 參數 panel 依 group 分三態:locked(base/realip 不可關,後端 enforce)、mutex(geoip 存檔時 warn)、optional(其餘純 toggle)。

**Architecture:** 一份 group→mode 對照(HttpController public static)驅動兩端 —— 前端 panel checkbox 依 mode render(locked disabled+鎖頭、mutex 標 data-mutex)、後端 saveEnable 強制 locked enable=true。mutex 由前端存檔時掃 data-mutex 統計 >1 → layer.confirm。

**Tech Stack:** Java 17 + Solon;Layui + jQuery + Freemarker;Playwright E2E。

## Global Constraints

- Solon:`@Inject`/`@Mapping`,controller extends BaseController(sqlHelper/homeConfig/renderSuccess/renderError)。
- i18n 三語同步:messages.properties(简)/messages_zh_TW(繁)/messages_en_US;ISO-8859-1,Edit 自動轉 \uXXXX;值內**無裸雙引號/換行**(會破壞 common.html 生成的 JS)。
- mode 對應:locked={base,realip} / mutex={geoip} / optional={gzip,brotli,headers,proxy,logging}。
- locked:後端 enforce(saveEnable 強制 enable=true)+ 前端 disabled+鎖頭。
- mutex:存檔時 layer.confirm warn,確認才送,不強制。
- E2E:Playwright,新 spec 編號 **30**。
- 現況:[HttpController.saveEnable](../../../src/main/java/com/cym/controller/adminPage/HttpController.java#L240)(phase 2)已有 checked→enable + nginx -t 預檢 + rollback;[GROUP_DEFS](../../../src/main/java/com/cym/controller/adminPage/HttpController.java#L44) 8 group;[ServerController.index:122](../../../src/main/java/com/cym/controller/adminPage/ServerController.java#L122) pass httpList;panel checkbox 在 server/index.html 285-292。

---

## File Structure

- **Modify** `src/main/java/com/cym/controller/adminPage/HttpController.java` — 加 LOCKED_GROUPS/MUTEX_GROUPS public static + saveEnable enforce locked
- **Modify** `src/main/java/com/cym/controller/adminPage/ServerController.java` — index() pass lockedGroups/mutexGroups
- **Modify** `src/main/resources/messages*.properties` ×3 — 2 個 i18n key
- **Modify** `src/main/resources/WEB-INF/view/adminPage/server/index.html` — panel checkbox 加 data-group/data-mutex/disabled/鎖頭
- **Modify** `src/main/resources/static/js/adminPage/server/index.js` — saveHttpParamPanel 加 mutex confirm(抽 doSaveHttpParam)
- **Create** `tests/e2e/30-http-param-mode.spec.js`

---

## Task 1: E2E 測試(red 基線)

**Files:**
- Create: `tests/e2e/30-http-param-mode.spec.js`

**Interfaces:**
- Consumes: `helpers.login`;panel 全域 `openHttpParamPanel()`;DOM `#httpParamPanelDiv`、`input[name="httpParamItem"]`(將新增 `data-group`/`data-mutex`/`disabled`)。

- [ ] **Step 1: 寫測試檔**

```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

async function openPanel(page) {
  await page.getByRole('button', { name: /添加反向代理/ }).click();
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}
async function reopenPanel(page) {
  await page.goto('/adminPage/server');
  await page.waitForSelector('table');
  await openPanel(page);
}
// 重新全選 + 存檔,把共用 DB 的 Http.enable 還原成全開
async function restoreAllEnabled(page) {
  await reopenPanel(page);
  await page.evaluate(() => {
    document.querySelectorAll('#httpParamPanelDiv input[name="httpParamItem"]:not([disabled])').forEach((cb) => {
      if (!cb.checked) { cb.checked = true; cb.dispatchEvent(new Event('change')); }
    });
  });
  await page.locator('button[onclick="saveHttpParamPanel()"]').click();
  await page.waitForTimeout(1000);
}

test.describe('server modal — ① http 參數 panel 三態 mode（phase 3）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await openPanel(page);
  });

  test('locked group(base/realip)的 checkbox 為 disabled', async ({ page }) => {
    const info = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cbs = [...scope.querySelectorAll('input[name="httpParamItem"]')];
      const locked = cbs.filter((c) => ['base', 'realip'].includes(c.getAttribute('data-group')));
      return { lockedCount: locked.length, allDisabled: locked.every((c) => c.disabled) };
    });
    expect(info.lockedCount).toBeGreaterThan(0);   // 測試 DB 有 base/realip 指令
    expect(info.allDisabled).toBe(true);
  });

  test('後端 enforce:送空 checkedIds,locked 仍 enable=true', async ({ page }) => {
    try {
      // 直接 POST 空 checkedIds(模擬 API 繞過關全部)
      const status = await page.evaluate(async () => {
        const res = await fetch('/adminPage/http/saveEnable', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: 'checkedIds=',
        });
        return res.status;
      });
      expect(status).toBe(200);

      await reopenPanel(page);
      const lockedAllChecked = await page.evaluate(() => {
        const scope = document.getElementById('httpParamPanelDiv');
        const locked = [...scope.querySelectorAll('input[name="httpParamItem"]')]
          .filter((c) => ['base', 'realip'].includes(c.getAttribute('data-group')));
        return locked.length > 0 && locked.every((c) => c.checked);
      });
      expect(lockedAllChecked).toBe(true);   // enforce:locked 不受空送影響
    } finally {
      await restoreAllEnabled(page);
    }
  });

  test('mutex group(geoip)勾 >1 → 存檔跳 confirm', async ({ page }) => {
    // 確保 geoip 至少 2 個勾選
    const geoipCount = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const geo = [...scope.querySelectorAll('input[name="httpParamItem"][data-group="geoip"]')];
      geo.forEach((c) => { if (!c.checked) { c.checked = true; c.dispatchEvent(new Event('change')); } });
      return geo.length;
    });
    expect(geoipCount).toBeGreaterThan(1);

    // 點存檔應跳 confirm(.layui-layer-dialog 帶按鈕);捕捉後取消
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    const confirmBox = page.locator('.layui-layer-dialog');
    await expect(confirmBox).toBeVisible();
    const txt = await confirmBox.textContent();
    expect(txt).toMatch(/擇一|择一|三選一|三选一|一起|memory|耗記憶|耗内存/i);
  });

  test('optional group(gzip)可正常 toggle', async ({ page }) => {
    const gzip = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const c = scope.querySelector('input[name="httpParamItem"][data-group="gzip"]');
      return c ? { disabled: c.disabled, group: c.getAttribute('data-group') } : null;
    });
    expect(gzip).not.toBeNull();
    expect(gzip.disabled).toBe(false);   // optional 不 disabled
  });
});
```

- [ ] **Step 2: build + 跑確認 red**

Run(worktree root,PATH 含 jdk-17):
```
mvn clean package -DskipTests -q
npx playwright test tests/e2e/30-http-param-mode.spec.js --config=tests/e2e/playwright.fast.config.js
```
Expected: FAIL — checkbox 尚無 `data-group`(test 1 lockedCount=0)、無 enforce、無 confirm。

- [ ] **Step 3: Commit**
```
git add tests/e2e/30-http-param-mode.spec.js
git commit -m "test: add phase-3 tristate mode E2E (red baseline)"
```

---

## Task 2: i18n 三語 key

**Files:** Modify `src/main/resources/messages.properties` / `messages_zh_TW.properties` / `messages_en_US.properties`

**Interfaces:** Produces i18n `serverStr.httpParamLockedTip` / `serverStr.httpParamMutexWarn`。

- [ ] **Step 1: 三份各在 `serverStr.httpParamGlobalHint` 行後插入 2 key(Edit 自動轉 \uXXXX)**

繁(messages_zh_TW.properties):
```
serverStr.httpParamLockedTip      = 核心指令，不可停用（關閉會連鎖影響 GeoIP／日誌等）。
serverStr.httpParamMutexWarn      = 同類指令建議擇一啟用（同時開啟較耗記憶體），確定要一起套用嗎？
```
簡(messages.properties):
```
serverStr.httpParamLockedTip      = 核心指令，不可停用（关闭会连锁影响 GeoIP／日志等）。
serverStr.httpParamMutexWarn      = 同类指令建议择一启用（同时开启较耗内存），确定要一起套用吗？
```
英(messages_en_US.properties):
```
serverStr.httpParamLockedTip      = Core directive; cannot be disabled (turning it off cascades to GeoIP / logging).
serverStr.httpParamMutexWarn      = Enabling just one of these is recommended (multiple uses more memory). Apply them together anyway?
```
(用 Edit,old_string 取各檔 `serverStr.httpParamGlobalHint` 那行、new_string 為該行 + 上列 2 行。)

- [ ] **Step 2: Commit**
```
git add src/main/resources/messages.properties src/main/resources/messages_zh_TW.properties src/main/resources/messages_en_US.properties
git commit -m "i18n: add tristate mode locked tip / mutex warn keys"
```

---

## Task 3: 後端 — mode 常數 + saveEnable enforce + ServerController pass

**Files:** Modify `HttpController.java`、`ServerController.java`

**Interfaces:**
- Produces: `HttpController.LOCKED_GROUPS` / `MUTEX_GROUPS`(public static Set<String>);ServerController render pass model `lockedGroups`/`mutexGroups`(List<String>)。

- [ ] **Step 1: HttpController 加 import + mode 常數**

頂部 import 加(若缺):
```java
import java.util.Set;
```
(`java.util.List`/`Map`/`HashSet`/`HashMap`/`Objects` phase 2 已 import。)

在 `GROUP_DEFS` 宣告之後加:
```java
	/** 三態 mode:核心不可關(後端 enforce enable=true)。 */
	public static final Set<String> LOCKED_GROUPS = Set.of("base", "realip");
	/** 三態 mode:建議互斥(前端存檔時 warn,不強制)。 */
	public static final Set<String> MUTEX_GROUPS = Set.of("geoip");
```

- [ ] **Step 2: saveEnable 的套用 loop 改為 enforce locked**

把現有(HttpController.java 約 258-264):
```java
		// 套用新 enable（只更新有變動的）
		for (Http http : httpList) {
			boolean want = checked.contains(http.getId());
			if (!Objects.equals(http.getEnable(), want)) {
				http.setEnable(want);
				sqlHelper.updateById(http);
			}
		}
```
改為:
```java
		// 套用新 enable（只更新有變動的）；locked group 強制 enable=true（後端 enforce，防繞過）
		for (Http http : httpList) {
			boolean want = checked.contains(http.getId())
					|| (http.getGroupName() != null && LOCKED_GROUPS.contains(http.getGroupName()));
			if (!Objects.equals(http.getEnable(), want)) {
				http.setEnable(want);
				sqlHelper.updateById(http);
			}
		}
```

- [ ] **Step 3: ServerController.index 加 pass lockedGroups/mutexGroups**

頂部 import 加(若缺):
```java
import java.util.ArrayList;
```
(ServerController 已用 ArrayList,通常已 import。)

在 `modelAndView.put("httpList", ...)`(ServerController.java:122)之後加:
```java
		modelAndView.put("lockedGroups", new ArrayList<>(HttpController.LOCKED_GROUPS));
		modelAndView.put("mutexGroups", new ArrayList<>(HttpController.MUTEX_GROUPS));
```

- [ ] **Step 4: 編譯確認**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS(exit 0)。

- [ ] **Step 5: Commit**
```
git add src/main/java/com/cym/controller/adminPage/HttpController.java src/main/java/com/cym/controller/adminPage/ServerController.java
git commit -m "feat(http): tristate mode constants + saveEnable enforce locked; server passes groups"
```

---

## Task 4: 前端 — panel checkbox mode 呈現 + mutex confirm,跑綠

**Files:** Modify `server/index.html`、`server/index.js`;Test `tests/e2e/30-http-param-mode.spec.js`

**Interfaces:** Consumes model `lockedGroups`/`mutexGroups`(Task 3)、i18n(Task 2)。

- [ ] **Step 1: index.html panel checkbox 加 data-group/data-mutex/disabled/鎖頭**

把現有(server/index.html 285-292):
```html
						<#list httpList as h>
						<label style="display:flex;align-items:center;padding:6px 8px;cursor:pointer;border-bottom:1px solid #f5f5f5;">
							<input type="checkbox" name="httpParamItem" value="${h.id}" ${(h.enable!true)?then('checked','')} style="margin-right:10px;" onchange="updateHttpParamCount()">
							<span style="font-family:Consolas,Menlo,monospace;font-size:12px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
								<span style="color:#0066cc;font-weight:bold;">${h.name?html}</span>
								<#if h.value??><span style="color:#888;margin-left:8px;">${h.value?html}</span></#if>
							</span>
						</label>
						</#list>
```
改為:
```html
						<#list httpList as h>
						<#assign gname = h.groupName!''>
						<#assign isLocked = lockedGroups?seq_contains(gname)>
						<#assign isMutex = mutexGroups?seq_contains(gname)>
						<label style="display:flex;align-items:center;padding:6px 8px;cursor:pointer;border-bottom:1px solid #f5f5f5;">
							<input type="checkbox" name="httpParamItem" value="${h.id}" data-group="${gname}"${isMutex?then(' data-mutex="1"','')} ${(h.enable!true)?then('checked','')} ${isLocked?then('disabled','')} style="margin-right:10px;" onchange="updateHttpParamCount()">
							<span style="font-family:Consolas,Menlo,monospace;font-size:12px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
								<span style="color:#0066cc;font-weight:bold;">${h.name?html}</span>
								<#if h.value??><span style="color:#888;margin-left:8px;">${h.value?html}</span></#if>
								<#if isLocked><i class="layui-icon layui-icon-password" title="${serverStr.httpParamLockedTip}" style="color:#c0392b;margin-left:6px;font-size:14px;"></i></#if>
							</span>
						</label>
						</#list>
```

- [ ] **Step 2: index.js saveHttpParamPanel 抽出 doSaveHttpParam + 加 mutex confirm**

把現有 `function saveHttpParamPanel() { ... ajax ... }` 整個換成:
```javascript
function saveHttpParamPanel() {
  // mutex 檢查:任一 data-mutex group 勾選 >1 → warn confirm(不強制)
  var perGroup = {};
  $('#httpParamPanelDiv input[name="httpParamItem"][data-mutex="1"]:checked').each(function () {
    var g = $(this).attr('data-group');
    perGroup[g] = (perGroup[g] || 0) + 1;
  });
  var over = Object.keys(perGroup).some(function (g) { return perGroup[g] > 1; });
  if (over) {
    layer.confirm(serverStr.httpParamMutexWarn, function (idx) {
      layer.close(idx);
      doSaveHttpParam();
    });
    return;
  }
  doSaveHttpParam();
}

function doSaveHttpParam() {
  var ids = $('input[name="httpParamItem"]:checked').map(function(){ return this.value; }).get();
  var loadIndex = layer.load(2);
  $.ajax({
    type: 'POST',
    url: ctx + '/adminPage/http/saveEnable',
    data: { checkedIds: ids.join(",") },
    dataType: 'json',
    success: function(data) {
      layer.close(loadIndex);
      if (data.success) {
        layer.msg(data.obj);
      } else {
        layer.alert(data.msg);
      }
    },
    error: function() {
      layer.close(loadIndex);
      layer.alert(commonStr.errorInfo);
    }
  });
}
```
(注意:`#httpParamPanelDiv` scope + `data-mutex` 掃描,前端不需硬編 group 名;`:checked` 對 disabled+checked 的 locked 仍收集,後端 enforce 為準。)

- [ ] **Step 3: build + 跑 30 確認 green**

Run(PATH 含 jdk-17):
```
mvn clean package -DskipTests -q
npx playwright test tests/e2e/30-http-param-mode.spec.js --config=tests/e2e/playwright.fast.config.js
```
Expected: 4 passed(locked disabled / enforce / mutex confirm / optional toggle)。

- [ ] **Step 4: 全套回歸**

Run: `npx playwright test --config=tests/e2e/playwright.fast.config.js`
Expected: 全 passed。特別確認 28/29(http-param panel 既有)未被 data-group/disabled 影響 —— 29 的 enable 落 DB、toast 仍綠(disabled 的 locked checkbox `:checked` 仍被收集)。

- [ ] **Step 5: Commit**
```
git add src/main/resources/WEB-INF/view/adminPage/server/index.html src/main/resources/static/js/adminPage/server/index.js
git commit -m "feat(server): tristate mode panel — locked disabled+lock icon, mutex save-time warn"
```

---

## Self-Review(plan 對照 spec)

- **Spec coverage:** locked=base/realip disabled+enforce(Task 3 常數+enforce、Task 4 disabled)✓;mutex=geoip 存檔 warn(Task 4 confirm)✓;optional 純 toggle(不動)✓;單一真相 LOCKED/MUTEX_GROUPS(Task 3)✓;i18n(Task 2)✓;E2E(Task 1 + Task 4 Step 3-4)✓。
- **Placeholder scan:** 無 TBD;每步完整 code/指令/預期。
- **Type consistency:** `LOCKED_GROUPS`/`MUTEX_GROUPS`(Set<String>,Task 3 定義)→ ServerController 包成 ArrayList pass、saveEnable enforce 用;前端 `data-group`/`data-mutex`(Task 4 render)→ 30 spec + saveHttpParamPanel 消費一致;i18n key `httpParamLockedTip`/`httpParamMutexWarn`(Task 2)→ Task 4 引用一致。

## 風險備註
- Freemarker `?seq_contains` 需 lockedGroups/mutexGroups 為 sequence(List)→ Task 3 用 `new ArrayList<>(...)` pass。
- 30 spec enforce 測試改全域 enable → try/finally `restoreAllEnabled` 還原(避免污染 28/29;且 30 為最後編號)。
- 鎖頭 icon 用 layui 內建 `layui-icon-password`(離線,不引外部)。
