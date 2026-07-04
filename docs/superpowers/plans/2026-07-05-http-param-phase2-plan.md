# http 參數 panel Phase 2 存檔 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓 server modal ① http 參數 panel 的「存檔」真正生效 —— 勾選 → update 全域 Http.enable，存檔前跑 nginx -t 預檢，失敗則 rollback。

**Architecture:** 前端 `saveHttpParamPanel()` 收集勾選 id → POST `/adminPage/http/saveEnable`；後端批量 update `Http.enable`，呼叫 `ConfService.precheckConf()`（buildConf → 臨時檔 → nginx -t），失敗還原舊 enable。不自動 reload（沿用「啟用配置」頁統一套用）。

**Tech Stack:** Java 17 + Solon（`@Controller`/`@Inject`/`@Mapping`，非 Spring）；Layui + jQuery 前端；Playwright E2E（無 JUnit）；SqlHelper（自製 ORM）。

## Global Constraints

- 後端框架 Solon：DI 用 `@Inject`、路由 `@Mapping`、controller `extends BaseController`（提供 `sqlHelper`/`homeConfig`/`renderSuccess`/`renderError`）。
- i18n 每個使用者可見字串同步三份：`messages.properties`（簡）、`messages_zh_TW.properties`（繁）、`messages_en_US.properties`（英）。檔案 ISO-8859-1，Edit 工具會把中文自動轉 `\uXXXX`。
- 存檔語意 = **全域**（update Http 表 enable，影響所有 server），**不做 per-server**。
- 存檔 + 預檢通過後 **不自動 reload**。
- nginxExe 未設 → **跳過預檢照存**（E2E 測試環境走此路徑）。
- 測試：Playwright E2E，新 spec 編號 **29**。
- 現況已確認：`Http.enable`（`@InitValue("true")`）存在；`ConfService.buildConf:126` 生成 conf 時 enable=false 會 skip；`ConfController.check:212` 有 nginx -t 流程可參考。

---

## File Structure

- **Modify** `src/main/java/com/cym/service/ConfService.java` — 加 `precheckConf()`（build 目前 conf → 臨時檔 → nginx -t）
- **Modify** `src/main/java/com/cym/controller/adminPage/HttpController.java` — 加 `saveEnable(String)` + inject `ConfService`
- **Modify** `src/main/resources/messages.properties` / `messages_zh_TW.properties` / `messages_en_US.properties` — 4 個 i18n key
- **Modify** `src/main/resources/WEB-INF/view/adminPage/server/index.html` — panel 加「全域設定」提示
- **Modify** `src/main/resources/static/js/adminPage/server/index.js` — `saveHttpParamPanel()` placeholder → 真 ajax
- **Create** `tests/e2e/29-http-param-save.spec.js` — E2E 驗證

---

## Task 1: E2E 測試（先寫，建立 red 基線）

**Files:**
- Create: `tests/e2e/29-http-param-save.spec.js`

**Interfaces:**
- Consumes: 既有 `helpers.js` 的 `login`；panel 全域函式 `openHttpParamPanel()` / `saveHttpParamPanel()`；panel DOM `#httpParamPanelDiv`、`input[name="httpParamItem"]`。
- Produces: 無（測試檔）。

- [ ] **Step 1: 寫測試檔**

```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 開 add-proxy modal → 開 http 參數 panel，回傳 panel 已就緒的 page
async function openPanel(page) {
  await page.getByRole('button', { name: /添加反向代理/ }).click();
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}

test.describe('server modal — ① http 參數 panel 存檔（phase 2）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
  });

  test('取消一項存檔後,重開 panel 該項仍為未勾（enable 落 DB）', async ({ page }) => {
    await openPanel(page);

    // 取消第一個勾選項,記其 id
    const targetId = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"]:checked');
      cb.checked = false;
      cb.dispatchEvent(new Event('change'));
      return cb.value;
    });
    expect(targetId).toBeTruthy();

    // 存檔
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    await page.waitForTimeout(1000); // 等 ajax + layer

    // 重載 server 頁 + 重開 panel
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await openPanel(page);

    // 該 id 的 checkbox 應為未勾（enable=false 已落 DB）
    const stillChecked = await page.evaluate((id) => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
      return cb ? cb.checked : null;
    }, targetId);
    expect(stillChecked).toBe(false);

    // 還原:重新勾選該項 + 存檔,避免影響其他 spec 的共用 DB
    await page.evaluate((id) => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
      cb.checked = true;
      cb.dispatchEvent(new Event('change'));
    }, targetId);
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    await page.waitForTimeout(1000);
  });

  test('存檔顯示成功 toast（測試環境 nginxExe 未設 → 略過預檢照存）', async ({ page }) => {
    await openPanel(page);
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    const toast = page.locator('.layui-layer-msg');
    await expect(toast).toBeVisible();
  });

  test('panel 顯示「全域設定」提示', async ({ page }) => {
    await openPanel(page);
    const text = await page.evaluate(() => document.getElementById('httpParamPanelDiv').textContent);
    expect(text).toMatch(/全域|全局|global/i);
  });
});
```

- [ ] **Step 2: build 現況 jar + 跑測試確認 red**

Run（worktree root，PATH 需含 jdk-17）:
```
mvn clean package -DskipTests -q
npx playwright test tests/e2e/29-http-param-save.spec.js --config=tests/e2e/playwright.fast.config.js
```
Expected: FAIL — 測試 1「stillChecked 應為 false 但得 true」（phase 1 saveHttpParamPanel 是 placeholder，存檔不生效）；測試 3「全域提示」也 FAIL（尚未加）。

- [ ] **Step 3: Commit（red 基線）**

```
git add tests/e2e/29-http-param-save.spec.js
git commit -m "test: add phase-2 http param save E2E (red baseline)"
```

---

## Task 2: i18n 三語 key

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_zh_TW.properties`
- Modify: `src/main/resources/messages_en_US.properties`

**Interfaces:**
- Produces: i18n key `serverStr.httpParamGlobalHint` / `serverStr.httpParamSaved` / `serverStr.httpParamPrecheckSkipped` / `serverStr.httpParamPrecheckFail`（Freemarker `${serverStr.xxx}` 與 JS `serverStr.xxx` 皆可用，由 common.html 自動生成）。

- [ ] **Step 1: 三份 properties 各在 `serverStr.httpParamSelected` 行後插入 4 key**

繁（messages_zh_TW.properties，插在 `serverStr.httpParamSelected` 之後）:
```
serverStr.httpParamGlobalHint     = 此為全域 http 設定，存檔會套用到所有 server。
serverStr.httpParamSaved          = 已存，請至「啟用配置」頁套用。
serverStr.httpParamPrecheckSkipped= 已存（nginx 未設定，略過語法預檢）。
serverStr.httpParamPrecheckFail   = nginx 語法預檢失敗，已還原設定：
```

簡（messages.properties）:
```
serverStr.httpParamGlobalHint     = 此为全局 http 设置，存档会套用到所有 server。
serverStr.httpParamSaved          = 已存，请至「启用配置」页套用。
serverStr.httpParamPrecheckSkipped= 已存（nginx 未设置，略过语法预检）。
serverStr.httpParamPrecheckFail   = nginx 语法预检失败，已还原设置：
```

英（messages_en_US.properties）:
```
serverStr.httpParamGlobalHint     = This is a global http setting; saving applies to all servers.
serverStr.httpParamSaved          = Saved. Apply it on the "Enable Config" page.
serverStr.httpParamPrecheckSkipped= Saved (nginx not configured; syntax precheck skipped).
serverStr.httpParamPrecheckFail   = nginx syntax precheck failed, settings rolled back:
```

（用 Edit 工具，old_string 取各檔 `serverStr.httpParamSelected` 那行、new_string 為該行 + 上列 4 行；Edit 會自動把中文轉 `\uXXXX`。）

- [ ] **Step 2: Commit**

```
git add src/main/resources/messages.properties src/main/resources/messages_zh_TW.properties src/main/resources/messages_en_US.properties
git commit -m "i18n: add http param save keys (global hint / saved / precheck)"
```

---

## Task 3: 後端 — ConfService.precheckConf + HttpController.saveEnable

**Files:**
- Modify: `src/main/java/com/cym/service/ConfService.java`
- Modify: `src/main/java/com/cym/controller/adminPage/HttpController.java`

**Interfaces:**
- Produces: `ConfService.precheckConf()` → `String`（`null`=通過；`"SKIPPED"`=nginxExe 未設略過；其他=nginx -t 錯誤訊息）。
- Produces: `HttpController.saveEnable(String checkedIds)` → `JsonResult`。
- Consumes: `ConfService.buildConf(Boolean, Boolean)`、`ConfService.replace(String, String, List, List, Boolean, String)`、`settingService.get()`、`homeConfig.home`。

- [ ] **Step 1: ConfService 加 precheckConf()**

先確認 ConfService 頂部已 import（buildConf 已用大部分；缺者補上）:
```java
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.cym.utils.ToolUtils;
```
確認 ConfService 已 `@Inject SettingService settingService;`（`buildDenyAllow` 已使用，若缺則補）。

在 `buildConf(...)` 方法之後加：
```java
	/**
	 * 用「目前 DB 狀態」build 出 nginx.conf，寫臨時檔後跑 nginx -t 語法預檢。
	 * @return null=預檢通過；"SKIPPED"=nginxExe 未設定，略過；其他字串=nginx -t 錯誤訊息。
	 */
	public synchronized String precheckConf() {
		String nginxExe = ToolUtils.handleConf(settingService.get("nginxExe"));
		if (cn.hutool.core.util.StrUtil.isEmpty(nginxExe)) {
			return "SKIPPED";
		}
		String nginxDir = ToolUtils.handleConf(settingService.get("nginxDir"));

		String decompose = settingService.get("decompose");
		boolean decomposeFlag = cn.hutool.core.util.StrUtil.isNotEmpty(decompose) && decompose.equals("true");
		ConfExt confExt = buildConf(decomposeFlag, false);
		if (confExt == null) {
			return "buildConf 失敗";
		}

		// 寫臨時檔（與 ConfController.check 同路徑/同 replace 流程，isReplace=false 不備份）
		FileUtil.del(homeConfig.home + "temp");
		String fileTemp = homeConfig.home + "temp/nginx.conf";
		List<String> subContent = new ArrayList<>();
		List<String> subName = new ArrayList<>();
		for (ConfFile cf : confExt.getFileList()) {
			subContent.add(cf.getConf());
			subName.add(cf.getName());
		}
		replace(fileTemp, confExt.getConf(), subContent, subName, false, null);

		String cmd = nginxExe + " -t -c " + fileTemp;
		if (cn.hutool.core.util.StrUtil.isNotEmpty(nginxDir)) {
			cmd += " -p " + nginxDir;
		}
		String rs;
		try {
			rs = RuntimeUtil.execForStr(cmd);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return e.getMessage();
		}
		return rs.contains("test is successful") ? null : rs;
	}
```

- [ ] **Step 2: HttpController 加 inject ConfService + saveEnable**

頂部 import 加：
```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import com.cym.service.ConfService;
```
（`java.util.List`/`Map`/`ArrayList` 已 import。）

class 內加 inject：
```java
	@Inject
	ConfService confService;
```

在 `setEnable(...)` 之後加：
```java
	/**
	 * http 參數 panel 存檔:全域 update Http.enable（勾選=true，未勾=false）,
	 * 存檔前跑 nginx -t 預檢,失敗則 rollback。不自動 reload。
	 */
	@Mapping("saveEnable")
	public synchronized JsonResult saveEnable(String checkedIds) {
		Set<String> checked = new HashSet<>();
		if (StrUtil.isNotEmpty(checkedIds)) {
			for (String id : checkedIds.split(",")) {
				if (StrUtil.isNotBlank(id)) {
					checked.add(id.trim());
				}
			}
		}

		List<Http> httpList = sqlHelper.findAll(Http.class);
		Map<String, Boolean> oldEnable = new HashMap<>();
		for (Http http : httpList) {
			oldEnable.put(http.getId(), http.getEnable());
		}

		// 套用新 enable（只更新有變動的）
		for (Http http : httpList) {
			boolean want = checked.contains(http.getId());
			if (!Objects.equals(http.getEnable(), want)) {
				http.setEnable(want);
				sqlHelper.updateById(http);
			}
		}

		String precheck = confService.precheckConf();
		if (precheck == null) {
			return renderSuccess(m.get("serverStr.httpParamSaved"));
		}
		if ("SKIPPED".equals(precheck)) {
			return renderSuccess(m.get("serverStr.httpParamPrecheckSkipped"));
		}
		// 預檢失敗 → rollback
		for (Http http : httpList) {
			Boolean old = oldEnable.get(http.getId());
			if (!Objects.equals(http.getEnable(), old)) {
				http.setEnable(old);
				sqlHelper.updateById(http);
			}
		}
		return renderError(m.get("serverStr.httpParamPrecheckFail") + "<br>" + precheck.replace("\n", "<br>"));
	}
```

- [ ] **Step 3: 編譯確認**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS（exit 0）。

- [ ] **Step 4: Commit**

```
git add src/main/java/com/cym/service/ConfService.java src/main/java/com/cym/controller/adminPage/HttpController.java
git commit -m "feat(http): saveEnable endpoint + nginx -t precheck with rollback"
```

---

## Task 4: 前端 ajax + 全域提示 UI，跑綠 E2E

**Files:**
- Modify: `src/main/resources/static/js/adminPage/server/index.js`
- Modify: `src/main/resources/WEB-INF/view/adminPage/server/index.html`
- Test: `tests/e2e/29-http-param-save.spec.js`（Task 1 已建）

**Interfaces:**
- Consumes: `/adminPage/http/saveEnable`（Task 3）、i18n `serverStr.httpParamGlobalHint`（Task 2）。

- [ ] **Step 1: index.js `saveHttpParamPanel()` 改真 ajax**

把現有 placeholder（`console.log(...)` + `layer.msg(...)`）整個函式 body 換成：
```javascript
function saveHttpParamPanel() {
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
        layer.msg(data.msg);
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

- [ ] **Step 2: index.html panel 加「全域設定」提示**

在 `#httpParamPanelDiv` 左欄說明 `${serverStr.httpParamDesc}` 那個 `<p>` 之後，加一行醒目提示：
```html
					<p style="color:#c0392b;font-size:12px;margin:0 0 12px 0;font-weight:bold;">⚠ ${serverStr.httpParamGlobalHint}</p>
```
（緊接在 `<p ...>${serverStr.httpParamDesc}</p>` 下方。）

- [ ] **Step 3: build + 跑 29 spec 確認 green**

Run（PATH 含 jdk-17）:
```
mvn clean package -DskipTests -q
npx playwright test tests/e2e/29-http-param-save.spec.js --config=tests/e2e/playwright.fast.config.js
```
Expected: 3 passed（存檔落 DB / 成功 toast / 全域提示）。

- [ ] **Step 4: 回歸 — 跑全套確認 0 failed**

Run:
```
npx playwright test --config=tests/e2e/playwright.fast.config.js
```
Expected: 全部 passed（新增 29 的 3 個 + 既有）。特別確認 28-http-param-panel 未被影響。

- [ ] **Step 5: Commit**

```
git add src/main/resources/static/js/adminPage/server/index.js src/main/resources/WEB-INF/view/adminPage/server/index.html
git commit -m "feat(server): wire http param panel save to backend + global-setting hint"
```

---

## Self-Review（plan 對照 spec）

- **Spec coverage:** save 全域 enable（Task 3）✓；nginx -t 預檢 + rollback（Task 3 precheckConf + saveEnable）✓；nginxExe 未設跳過（Task 3 "SKIPPED"）✓；不自動 reload（saveEnable 無 reload）✓；全域提示 UI（Task 4 Step 2）✓；i18n 三語（Task 2）✓；E2E（Task 1 + Task 4 Step 3-4）✓。
- **Placeholder scan:** 無 TBD；每步含完整 code / 指令 / 預期輸出。
- **Type consistency:** `precheckConf()` 回 String 約定（null/"SKIPPED"/errmsg）在 Task 3 定義、saveEnable 消費一致；i18n key 名 Task 2 定義、Task 3/4 引用一致（httpParamSaved / httpParamPrecheckSkipped / httpParamPrecheckFail / httpParamGlobalHint）。

## 風險備註（實作時留意）

- ConfService 若未 inject `settingService`：Task 3 Step 1 補 `@Inject SettingService settingService;`。
- `ConfExt` / `ConfFile` getter：`confExt.getConf()`、`confExt.getFileList()`、`confFile.getConf()`、`confFile.getName()`（見 ConfController.getReplaceJson 用法）。
- 29 測試改全域 enable 有副作用 → 測試 1 已自我還原；且 29 為最後編號 spec。
