# Wave 2 a11y / UI Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Plan file final location:** This file lives at `C:\Users\EDDIE\.claude\plans\structured-forging-kitten.md` only because plan-mode hard-codes the path. After approval, copy/move to `docs/superpowers/plans/2026-06-29-ui-audit-wave2.md` (per user memory \[feedback_plan_location\](file:///C:/Users/EDDIE/.claude/projects/e--nginxWebUI/memory/feedback_plan_location.md)).

## Context

User reported "前面改得很亂,需要對整個網站進行審計". The 2026-06-29 audit pass (logged in \[docs/superpowers/plans/2026-06-29-ui-audit.md\](file:///e:/nginxWebUI/docs/superpowers/plans/2026-06-29-ui-audit.md)) found \~150 a11y / UI / encoding issues across all admin pages. Wave 1 already shipped (commit `c41a0ee5` — xmSelect demo deleted, common.html viewport/charset/theme-color, index.html lang). This Wave 2 closes **every remaining audit finding** in one PR. User direction (verbatim): "要修就全部一次修理好,這次計劃就是修復". User-reported new issues will go to a separate plan after this ships.

**Goal:** Ship one PR that closes every audit finding marked H1/H2/H3/H4/M1/M2/L1/L2/L3 in \[docs/superpowers/plans/2026-06-29-ui-audit.md\](file:///e:/nginxWebUI/docs/superpowers/plans/2026-06-29-ui-audit.md).

**Architecture:** Reuse the existing `getMainArea()` helper in \[base.js\](file:///e:/nginxWebUI/src/main/resources/static/js/adminPage/base.js) (shipped in commit `acff0aff`) for remaining main-editor modals. Add two CSS classes once in `common.html` (`.link-button`, `.header-link-btn`) and apply globally to avoid inline-styling 80+ sites. Add one Playwright crawler spec that asserts the structural rules (no `<a href="javascript:">`, every `<button>` has accessible name, no `javacript:` typo) — this replaces enumerating 80+ button-conversion checks in the plan.

**Tech Stack:** Java 8 + Solon 3.3.3 (not Spring Boot), Layui 2.5.5 + jQuery + Freemarker server-rendered (not React), SQLite/PG/MySQL, Maven build → fat jar, Playwright e2e tests (26 specs + 1 new).

## Global Constraints

Verbatim from project-level rules:

- **i18n strict rule (CLAUDE.md):** every new user-visible string MUST be added to ALL THREE `messages*.properties`: `messages.properties` (zh-CN, ISO-8859-1 escaped `\uXXXX`), `messages_zh_TW.properties` (zh-TW, escaped), `messages_en_US.properties` (en, ASCII).
- **No external CDN (\[memory feedback_no_external_cdn_offline\](file:///C:/Users/EDDIE/.claude/projects/e--nginxWebUI/memory/feedback_no_external_cdn_offline.md)):** any new asset must be vendored under `src/main/resources/static/lib/`.
- **No Spring Boot / no JPA / no JUnit** — only the existing stack. Tests = Playwright e2e under `tests/e2e/`.
- **Layui behavior is sacred:** `<a href="javascript:;">` inside `.layui-nav-tree` is owned by Layui's tree-collapse JS and MUST NOT be converted to `<button>`. The crawler spec excludes them explicitly.
- **Java 8 binary compatibility:** any new JS must work in modern browsers; we are NOT shipping Java code in this plan.
- **Don't break existing business logic** (CLAUDE.md core principle 1). All changes are a11y annotations + CSS class swaps + helper adoption.
- **Auto-commit/push to** `dev` (\[memory feedback_auto_sync_commit_push\](file:///C:/Users/EDDIE/.claude/projects/e--nginxWebUI/memory/feedback_auto_sync_commit_push.md)): every task's last step is commit + push, no asking.
- **Verification before completion** (\[memory feedback_full_superpowers_lifecycle\](file:///C:/Users/EDDIE/.claude/projects/e--nginxWebUI/memory/feedback_full_superpowers_lifecycle.md)): every task ends with running tests and confirming actual output before claiming done.

---

## File Structure (what gets touched)

**Modified (HTML / view templates):**

- `src/main/resources/WEB-INF/view/adminPage/common.html` — add `.link-button` + `.header-link-btn` CSS once
- `src/main/resources/WEB-INF/view/adminPage/header.html` — convert 5 `<a href="javascript:">` to `<button>`, currentUser label to `<span>`
- `src/main/resources/WEB-INF/view/adminPage/menu.html` — wrap sidebar in `<nav role="navigation">`
- `src/main/resources/WEB-INF/view/adminPage/select_lang.html` — replace `transition: all`
- `src/main/resources/WEB-INF/view/adminPage/login/index.html` — autocomplete attrs + captcha img-to-button wrapper
- `src/main/resources/WEB-INF/view/adminPage/admin/index.html` — autocomplete attr + 2 `<a href="javascript:">` to `<button>` + icon `aria-hidden`
- `src/main/resources/WEB-INF/view/adminPage/remote/index.html` — autocomplete + 2 `<a href="javascript:">` to `<button>` + captcha button wrapper + icons
- `src/main/resources/WEB-INF/view/adminPage/server/index.html` — `<a href="javascript:">` to `<button>` + icon `aria-hidden`
- `src/main/resources/WEB-INF/view/adminPage/upstream/index.html` — same
- `src/main/resources/WEB-INF/view/adminPage/http/index.html` — icon `aria-hidden` on all `<i class="layui-icon">` inside `<button>`
- `src/main/resources/WEB-INF/view/adminPage/stream/index.html` — same + 1 decorative icon
- `src/main/resources/WEB-INF/view/adminPage/cert/index.html` — same
- `src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html` — same + 1 decorative icon
- `src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html` — add `<h1>` + icon `aria-hidden`
- `src/main/resources/WEB-INF/view/adminPage/monitor/index.html` — fix `javacript:` typo (replace pseudo-link with `<span>`)

**Modified (JS):**

- `src/main/resources/static/js/adminPage/base.js` — fix line 68 autocomplete blanket override
- `src/main/resources/static/js/adminPage/cert/index.js` — main editor `showWindow` → `getMainArea()`
- `src/main/resources/static/js/adminPage/http/index.js` — guide modal width cap
- `src/main/resources/static/js/adminPage/denyAllow/index.js` — width cap
- `src/main/resources/static/js/adminPage/bak/index.js` — width cap
- `src/main/resources/static/js/adminPage/template/index.js` — width cap

**Modified (i18n properties — three files for every new key):**

- `src/main/resources/messages.properties`
- `src/main/resources/messages_zh_TW.properties`
- `src/main/resources/messages_en_US.properties`

**New:**

- `tests/e2e/27-a11y-buttons.spec.js` — Playwright crawler that asserts the H2 rules globally

---

## Pre-flight (do once, no commit)

- \[ \] **Step 1:** Confirm clean baseline build: `mvn package -DskipTests` → `BUILD SUCCESS`, jar mtime updated.
- \[ \] **Step 2:** Baseline tests green: `npm --prefix tests/e2e run test:fast` → all 26 specs PASS.
- \[ \] **Step 3:** `git status` clean on branch `dev` (or create `feat/wave2-a11y` if user prefers).
- \[ \] **Step 4:** Open \[audit doc\](file:///e:/nginxWebUI/docs/superpowers/plans/2026-06-29-ui-audit.md) in editor — track which finding each task closes.

**Stop-the-line conditions (apply to every task):**

- Baseline `test:fast` red → fix or abort plan.
- Any task verification fails → revert that task, do NOT continue.
- DevTools console shows new JS error → revert.
- Visual regression on header/sidebar/buttons → revert and fix CSS first.
- aria-label string added to working tree without all 3 properties updated → revert.

---

## Task L2: Replace `transition: all` in language picker

**Closes audit finding:** L2.

**Files:**

- Modify: `src/main/resources/WEB-INF/view/adminPage/select_lang.html:15`

- \[ \] **Step 1:** Edit line 15.

  Before:

  ```
  transition: all 0.18s ease;
  ```

  After:

  ```
  transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
  ```

- \[ \] **Step 2:** Verify: `grep -n "transition:" src/main/resources/WEB-INF/view/adminPage/select_lang.html` shows only the new line.

- \[ \] **Step 3:** `mvn package -DskipTests` → BUILD SUCCESS.

- \[ \] **Step 4:** Smoke: `npm --prefix tests/e2e run test:fast -- --grep "22-lang"` → PASS.

- \[ \] **Step 5:** Commit + push.

  ```
  git add src/main/resources/WEB-INF/view/adminPage/select_lang.html
  git commit -m "style(a11y): scope select_lang transitions to specific properties"
  git push origin dev
  ```

---

## Task L1: Decorative icons get `aria-hidden="true"`

**Closes audit finding:** L1 (decorative `<i>` next to text).

**Files:**

- Modify: `src/main/resources/WEB-INF/view/adminPage/stream/index.html:97`

- Modify: `src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html:268`

- Modify: any other `<i class="layui-icon-face-surprised">` or `<i class="layui-icon-about">` outside H2's scope

- \[ \] **Step 1:** Find all empty-state decorative icons.

  ```
  grep -rn 'layui-icon-face-surprised\|layui-icon-about' src/main/resources/WEB-INF/view/adminPage
  ```

  Expected hits include the two above plus possibly cert / protectionCert.

- \[ \] **Step 2:** For each match, add `aria-hidden="true"` to the `<i>` tag.

  Pattern:

  ```
  <i class="layui-icon layui-icon-face-surprised"></i>
  ```

  →

  ```
  <i class="layui-icon layui-icon-face-surprised" aria-hidden="true"></i>
  ```

- \[ \] **Step 3:** Verify: `grep -c 'aria-hidden' src/main/resources/WEB-INF/view/adminPage/stream/index.html` → returns ≥1.

- \[ \] **Step 4:** `mvn package -DskipTests`; `npm --prefix tests/e2e run test:fast` → all green.

- \[ \] **Step 5:** Commit + push.

  ```
  git add src/main/resources/WEB-INF/view/adminPage/stream/index.html src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html
  git commit -m "a11y: hide decorative empty-state icons from assistive tech"
  git push origin dev
  ```

---

## Task M1: Password autocomplete + fix base.js blanket override

**Closes audit finding:** M1 (login/admin/remote password autocomplete).

**Critical context:** \[base.js:68\](file:///e:/nginxWebUI/src/main/resources/static/js/adminPage/base.js#L68) currently has `$("input").attr("autocomplete", "off");` which **overwrites every input's autocomplete on every page load**. Adding `autocomplete="current-password"` in HTML alone won't work — must patch base.js first.

**Files:**

- Modify: `src/main/resources/static/js/adminPage/base.js:68`

- Modify: `src/main/resources/WEB-INF/view/adminPage/login/index.html:39,45,81,87,93`

- Modify: `src/main/resources/WEB-INF/view/adminPage/admin/index.html:214`

- Modify: `src/main/resources/WEB-INF/view/adminPage/remote/index.html:129,136`

- \[ \] **Step 1:** Patch base.js to skip inputs that already declare autocomplete.

  Before:

  ```
  $("input").attr("autocomplete", "off");
  ```

  After:

  ```
  $("input:not([autocomplete])").attr("autocomplete", "off");
  ```

- \[ \] **Step 2:** login/index.html — add autocomplete attrs.

  Line 39 (login username):

  ```
  <input type="text" name="name" id="name" class="layui-input" value="" autocomplete="username">
  ```

  Line 45 (login password):

  ```
  <input type="password" name="pass" id="pass" class="layui-input" value="" autocomplete="current-password" onkeyup="getKey()">
  ```

  Line 81 (new-admin username — first-time setup form):

  ```
  <input type="text" name="name" id="adminName" class="layui-input" value="" autocomplete="username">
  ```

  Lines 87, 93 (new-admin pass / repeat — first-time setup):

  ```
  <input type="password" name="pass" id="adminPass" class="layui-input" value="" autocomplete="new-password">
  ```

  ```
  <input type="password" id="repeatPass" class="layui-input" value="" autocomplete="new-password">
  ```

- \[ \] **Step 3:** admin/index.html:214 (changing existing admin's pass):

  ```
  <input type="password" name="pass" id="pass" class="layui-input" placeholder="${adminStr.passHint}" autocomplete="new-password">
  ```

- \[ \] **Step 4:** remote/index.html — lines 129 (`#name` SSH) and 136 (`#pass` SSH):

  ```
  <input type="text" name="name" id="name" class="layui-input" autocomplete="username">
  ```

  ```
  <input type="password" name="pass" id="pass" class="layui-input" autocomplete="current-password">
  ```

- \[ \] **Step 5:** `mvn package -DskipTests` → SUCCESS. Restart jar:

  ```
  netstat -ano | grep ':8081' | grep LISTENING | awk '{print $5}' | xargs -I {} taskkill //F //PID {}
  java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.2.2.jar --server.port=8081 --project.home=E:/nginxWebUI/dev-home/ --spring.database.type=postgresql --spring.datasource.url=jdbc:postgresql://localhost:5433/nginxwebui --spring.datasource.username=nginxwebui --spring.datasource.password=nginxwebui123 --project.testCaptcha=1234 &
  ```

- \[ \] **Step 6:** Verify in DevTools console after loading login page:

  ```
  document.getElementById('pass').autocomplete
  // Expected: "current-password"  (NOT "off")
  ```

  Use mcp playwright `browser_evaluate` if no manual browser available.

- \[ \] **Step 7:** Run `npm --prefix tests/e2e run test:fast -- --grep "01-login"` → PASS (login flow unaffected).

- \[ \] **Step 8:** Commit + push.

  ```
  git add src/main/resources/static/js/adminPage/base.js src/main/resources/WEB-INF/view/adminPage/login/index.html src/main/resources/WEB-INF/view/adminPage/admin/index.html src/main/resources/WEB-INF/view/adminPage/remote/index.html
  git commit -m "a11y(forms): add semantic autocomplete to credential inputs and preserve them in base.js"
  git push origin dev
  ```

---

## Task M2: Captcha image → button wrapper

**Closes audit finding:** M2 (clickable `<img>` without alt on login + remote).

**New i18n key:** `commonStr.refreshCaptcha`.

**Files:**

- Modify: `src/main/resources/WEB-INF/view/adminPage/login/index.html:54`

- Modify: `src/main/resources/WEB-INF/view/adminPage/remote/index.html:305`

- Modify: `src/main/resources/messages.properties`

- Modify: `src/main/resources/messages_zh_TW.properties`

- Modify: `src/main/resources/messages_en_US.properties`

- \[ \] **Step 1:** Add i18n key to all three properties files.

  `messages.properties` (zh-CN, escaped):

  ```
  commonStr.refreshCaptcha = 刷新验证码
  ```

  `messages_zh_TW.properties` (zh-TW, escaped):

  ```
  commonStr.refreshCaptcha = 重新整理驗證碼
  ```

  `messages_en_US.properties` (en):

  ```
  commonStr.refreshCaptcha = Refresh captcha
  ```

- \[ \] **Step 2:** login/index.html:54 — replace `<img onclick>` with `<button>` wrapping `<img alt="">`.

  Before:

  ```
  <img src="${ctx}/adminPage/login/getCode?t=${jsrandom}" id="codeImg" onclick="refreshCode('codeImg')">
  ```

  After:

  ```
  <button type="button" class="captcha-btn" onclick="refreshCode('codeImg')" aria-label="${commonStr.refreshCaptcha}" style="padding:0;border:0;background:transparent;cursor:pointer;vertical-align:middle;">
      <img src="${ctx}/adminPage/login/getCode?t=${jsrandom}" id="codeImg" alt="">
  </button>
  ```

- \[ \] **Step 3:** remote/index.html:305 — identical pattern. Before:

  ```
  <img src="" id="codeImg" onclick="refreshCode('codeImg')">
  ```

  After:

  ```
  <button type="button" class="captcha-btn" onclick="refreshCode('codeImg')" aria-label="${commonStr.refreshCaptcha}" style="padding:0;border:0;background:transparent;cursor:pointer;vertical-align:middle;">
      <img src="" id="codeImg" alt="">
  </button>
  ```

- \[ \] **Step 4:** `mvn package -DskipTests` → SUCCESS. Restart jar.

- \[ \] **Step 5:** Verify rendering — load `/adminPage/login`, captcha image still appears; clicking refreshes (the existing `refreshCode` JS targets by id and is unaffected).

- \[ \] **Step 6:** Run `npm --prefix tests/e2e run test:fast -- --grep "01-login"` → PASS.

- \[ \] **Step 7:** Commit + push.

  ```
  git add src/main/resources/messages.properties src/main/resources/messages_zh_TW.properties src/main/resources/messages_en_US.properties src/main/resources/WEB-INF/view/adminPage/login/index.html src/main/resources/WEB-INF/view/adminPage/remote/index.html
  git commit -m "a11y(captcha): wrap captcha image in button with aria-label"
  git push origin dev
  ```

---

## Task H3-a: Fix `javacript:` typo in monitor/index.html

**Closes audit finding:** typo originally mis-attributed to protectionCert; actual location is `monitor/index.html:71` (verified by grep — only hit in repo).

**Files:**

- Modify: `src/main/resources/WEB-INF/view/adminPage/monitor/index.html:71`

- \[ \] **Step 1:** Edit line 71.

  Before:

  ```
  <span class="layui-breadcrumb"><a href="javacript:">${disk.path} (${disk.useSpace} / ${disk.totalSpace})</a></span>
  ```

  After:

  ```
  <span class="layui-breadcrumb"><span>${disk.path} (${disk.useSpace} / ${disk.totalSpace})</span></span>
  ```

  Rationale: the typo means the link was never functional. Visually it was always a static label. Convert to `<span>` (no action), not `<button>`.

- \[ \] **Step 2:** Verify: `grep -rn 'javacript' src/main/resources` → returns NOTHING.

- \[ \] **Step 3:** `mvn package -DskipTests` → SUCCESS.

- \[ \] **Step 4:** Run `npm --prefix tests/e2e run test:fast -- --grep "10-nginx-info"` (monitor page coverage) → PASS.

- \[ \] **Step 5:** Commit + push.

  ```
  git add src/main/resources/WEB-INF/view/adminPage/monitor/index.html
  git commit -m "fix(monitor): remove broken javacript: pseudo-link, render disk label as span"
  git push origin dev
  ```

---

## Task H3-b: Add `<h1>` page heading to protectionCert

**Closes audit finding:** protectionCert missing `<h1>` (uses `<legend>` for headings).

**Files:**

- Modify: `src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html` (insert near top of body)

- \[ \] **Step 1:** Read the file around the breadcrumb to find insertion point.

- \[ \] **Step 2:** Insert immediately before the breadcrumb `<span class="layui-breadcrumb">…</span>`:

  ```
  <h1 class="page-title" style="font-size:18px;font-weight:bold;margin:0 0 10px;">${menuStr.protectionCert}</h1>
  ```

  Verify `menuStr.protectionCert` exists:

  ```
  grep -n "menuStr.protectionCert" src/main/resources/messages*.properties
  ```

  If absent in any file, add it (default value: "防護與證書" / "防护与证书" / "Protection & Cert").

- \[ \] **Step 3:** `mvn package -DskipTests` → SUCCESS.

- \[ \] **Step 4:** Manual: load `/adminPage/protectionCert`, confirm `<h1>` renders, page layout unchanged.

- \[ \] **Step 5:** Commit + push.

  ```
  git add src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html
  git commit -m "a11y(protectionCert): add h1 page heading for semantic landmark"
  git push origin dev
  ```

---

## Task H1: Sidebar `<nav>` + header action `<button>` + new i18n keys

**Closes audit findings:** menu.html missing `<nav>`, header.html 5 `<a href="javascript:">`.

**New i18n keys:** `commonStr.mainNav`, `commonStr.changeLang`, `commonStr.moduleList`.

**Files:**

- Modify: `src/main/resources/WEB-INF/view/adminPage/common.html` — add `.header-link-btn` CSS once

- Modify: `src/main/resources/WEB-INF/view/adminPage/menu.html` — wrap in `<nav>`

- Modify: `src/main/resources/WEB-INF/view/adminPage/header.html` — 5 conversions

- Modify: all 3 messages\*.properties

- \[ \] **Step 1:** Add 3 i18n keys to all 3 properties files.

  `messages.properties`:

  ```
  commonStr.mainNav = 主导航
  commonStr.changeLang = 切换语言
  commonStr.moduleList = 模块列表
  ```

  `messages_zh_TW.properties`:

  ```
  commonStr.mainNav = 主導航
  commonStr.changeLang = 切換語言
  commonStr.moduleList = 模組列表
  ```

  `messages_en_US.properties`:

  ```
  commonStr.mainNav = Main navigation
  commonStr.changeLang = Change language
  commonStr.moduleList = Module list
  ```

- \[ \] **Step 2:** Add `.header-link-btn` CSS to common.html. Find existing `<style>` block (or add new one in head fragment) and append:

  ```
  .header-link-btn { background:transparent; border:0; cursor:pointer; padding:0; font:inherit; color:inherit; }
  .header-link-btn:hover, .header-link-btn:focus { text-decoration:underline; outline:none; }
  .header-link-btn:focus-visible { outline:2px solid #fff; outline-offset:2px; }
  ```

- \[ \] **Step 3:** menu.html — wrap sidebar content in `<nav>`. After `<div class="layui-side layui-bg-black">`:

  Before (line 1-2 area):

  ```
  <div class="layui-side layui-bg-black">
      <div class="layui-side-scroll">
  ```

  After:

  ```
  <div class="layui-side layui-bg-black">
      <nav role="navigation" aria-label="${commonStr.mainNav}">
      <div class="layui-side-scroll">
  ```

  At the end of `.layui-side` content (find matching closing `</div>`s near line 87-88), add closing `</nav>`:

  ```
      </div>
      </nav>
  </div>
  ```

  Lines 7 + 63 — `<a href="javascript:;">` inside `.layui-nav-tree` are Layui's tree-collapse toggles. **DO NOT TOUCH.**

- \[ \] **Step 4:** header.html — convert 5 action anchors + 1 fake-link span. Read file first to confirm exact line text, then apply each transformation:

  **Line 15 (showModuleList)**:Before: `<a href="javascript:showModuleList()" style="color: #01AAED;" id="nginxVersionLink"></a>`After: `<button type="button" class="header-link-btn" onclick="showModuleList()" style="color: #01AAED;" id="nginxVersionLink" aria-label="${commonStr.moduleList}"></button>`

  **Line 33 (currentUser — pure label, no action)**:Before: `<a href="javascript:" style="color: white;">${commonStr.currentUser}: ${admin.name}</a>`After: `<span style="color: white;">${commonStr.currentUser}: ${admin.name}</span>`

  **Lines 37-46 (changeLang block) — convert outer** `<a>` **to** `<button>`**, keep** `<img>` **children but add** `aria-hidden="true"`:Before:

  ```
  <a href="javascript:changeLang()" style="display:inline-flex;align-items:center;gap:6px;">
      <#if lang?? && lang == "zh_TW">
          <img src="${ctx}/img/tw.svg" alt="tw" style="...">
      <#elseif lang?? && lang == "en_US">
          <img src="${ctx}/img/gb.svg" alt="en" style="...">
      <#else>
          <img src="${ctx}/img/cn.svg" alt="cn" style="...">
      </#if>
      <span>${langType}</span>
  </a>
  ```

  After:

  ```
  <button type="button" class="header-link-btn" onclick="changeLang()" style="display:inline-flex;align-items:center;gap:6px;" aria-label="${commonStr.changeLang}">
      <#if lang?? && lang == "zh_TW">
          <img src="${ctx}/img/tw.svg" alt="tw" aria-hidden="true" style="...">
      <#elseif lang?? && lang == "en_US">
          <img src="${ctx}/img/gb.svg" alt="en" aria-hidden="true" style="...">
      <#else>
          <img src="${ctx}/img/cn.svg" alt="cn" aria-hidden="true" style="...">
      </#if>
      <span>${langType}</span>
  </button>
  ```

  (Keep `style` values verbatim.)

  **Line 50 (showHelp)**:Before: `<a href="javascript:showHelp()">${commonStr.userTutorial}</a>`After: `<button type="button" class="header-link-btn" onclick="showHelp()">${commonStr.userTutorial}</button>`

  **Line 56 (showUpdate — preserve full onclick args verbatim)**:Before: `<a href="javascript:showUpdate(...)" style="font-weight: bold;color: red;">${commonStr.newVersion} ${newVersion.version}</a>`After: `<button type="button" class="header-link-btn" onclick="showUpdate(...)" style="font-weight: bold;color: red;">${commonStr.newVersion} ${newVersion.version}</button>`

  **Line 62 (loginOut)**:Before: `<a href="javascript:loginOut();">${commonStr.exit}</a>`After: `<button type="button" class="header-link-btn" onclick="loginOut()">${commonStr.exit}</button>`

- \[ \] **Step 5:** `mvn package -DskipTests` → SUCCESS. Restart jar.

- \[ \] **Step 6:** Manual + Playwright verification:

  - Load any admin page; visually compare header — buttons should look identical (`.header-link-btn` strips browser chrome).
  - Tab key reaches each header button — visible focus outline (`:focus-visible`).
  - Lang switch works (click → modal opens).
  - Logout works.
  - Sidebar nav landmark exists: in DevTools `document.querySelector('nav[aria-label]')` → returns the element.

- \[ \] **Step 7:** Run `npm --prefix tests/e2e run test:fast -- --grep "22-lang|01-login"` → PASS.

- \[ \] **Step 8:** Commit + push.

  ```
  git add src/main/resources/messages.properties src/main/resources/messages_zh_TW.properties src/main/resources/messages_en_US.properties src/main/resources/WEB-INF/view/adminPage/common.html src/main/resources/WEB-INF/view/adminPage/menu.html src/main/resources/WEB-INF/view/adminPage/header.html
  git commit -m "a11y(layout): wrap sidebar in nav landmark, convert header pseudo-links to buttons"
  git push origin dev
  ```

---

## Task H4: Adopt `getMainArea()` for remaining main editors + 90vw cap for medium modals

**Closes audit finding:** H4 (modal width inconsistency).

**Reuse:** existing helper \[`getMainArea()` in base.js\](file:///e:/nginxWebUI/src/main/resources/static/js/adminPage/base.js) (lines \~328-352, shipped in commit `acff0aff`).

**Files:**

- Modify: `src/main/resources/static/js/adminPage/cert/index.js` (around line 291)

- Modify: `src/main/resources/static/js/adminPage/http/index.js:271`

- Modify: `src/main/resources/static/js/adminPage/denyAllow/index.js:253`

- Modify: `src/main/resources/static/js/adminPage/bak/index.js:17`

- Modify: `src/main/resources/static/js/adminPage/template/index.js:28`

- \[ \] **Step 1:** cert/index.js main `showWindow` — adopt getMainArea.

  Before:

  ```
  area: ['1000px', '630px'], // 宽高
  content: $('#windowDiv')
  ```

  After:

  ```
  var a = getMainArea();
  area: [a.w, a.h],
  offset: a.offset,
  content: $('#windowDiv')
  ```

  (Refactor surrounding `layer.open({...})` to declare `var a = getMainArea();` before the call.)

- \[ \] **Step 2:** http/index.js:271 guide modal — wrap in 90vw cap (guide is documentation, not main editor).

  Before:

  ```
  area: ['800px', '90%']
  ```

  After:

  ```
  area: ['min(800px, 90vw)', '90%']
  ```

- \[ \] **Step 3:** denyAllow/index.js:253: Before: `area: ['750px', '600px']`After: `area: ['min(750px, 90vw)', 'min(600px, 90vh)']`

- \[ \] **Step 4:** bak/index.js:17: Before: `area: ['800px', '600px']`After: `area: ['min(800px, 90vw)', 'min(600px, 90vh)']`

- \[ \] **Step 5:** template/index.js:28: Before: `area: ['800px', '600px']`After: `area: ['min(800px, 90vw)', 'min(600px, 90vh)']`

- \[ \] **Step 6:** `mvn package -DskipTests` → SUCCESS. Restart jar.

- \[ \] **Step 7:** Verify with mcp playwright at 2 viewports (1280 + 1920):

  ```
  // open cert add modal
  add();
  document.querySelector('.layui-layer').getBoundingClientRect();
  // 1280: width ~= 1080 (main area), modal sits at x=200
  // 1920: width ~= 1720, modal sits at x=200
  ```

- \[ \] **Step 8:** Run `npm --prefix tests/e2e run test:fast` → all green (no modal-opening spec breaks).

- \[ \] **Step 9:** Commit + push.

  ```
  git add src/main/resources/static/js/adminPage/cert/index.js src/main/resources/static/js/adminPage/http/index.js src/main/resources/static/js/adminPage/denyAllow/index.js src/main/resources/static/js/adminPage/bak/index.js src/main/resources/static/js/adminPage/template/index.js
  git commit -m "a11y(modals): adopt getMainArea for cert main editor, cap medium modals at 90vw"
  git push origin dev
  ```

---

## Task H2-PRE: Playwright a11y crawler (TDD — must FAIL first)

**Closes audit finding:** sets up gate for H2.

**Reuse:** existing `login()` helper in `tests/e2e/helpers.js`.

**Files:**

- Create: `tests/e2e/27-a11y-buttons.spec.js`

- \[ \] **Step 1:** Create the spec.

  ```
  const { test, expect } = require('@playwright/test');
  const { login } = require('./helpers');
  
  const LISTING_PAGES = [
    '/adminPage/server',
    '/adminPage/upstream',
    '/adminPage/http',
    '/adminPage/stream',
    '/adminPage/cert',
    '/adminPage/denyAllow',
    '/adminPage/protectionCert',
    '/adminPage/admin',
    '/adminPage/remote',
  ];
  
  test.beforeEach(async ({ page }) => {
    await login(page);
  });
  
  for (const path of LISTING_PAGES) {
    test(`${path}: no <a href="javascript:"> outside layui nav-tree`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      const bad = await page.evaluate(() => {
        const all = Array.from(document.querySelectorAll('a[href^="javascript:"]'));
        return all.filter(a => !a.closest('.layui-nav-tree')).length;
      });
      expect(bad, `${path} has <a href="javascript:"> outside layui nav-tree`).toBe(0);
    });
  
    test(`${path}: every visible <button> has accessible name`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      const buttons = await page.locator('button:visible').all();
      const missing = [];
      for (const b of buttons) {
        const ariaLabel = (await b.getAttribute('aria-label')) || '';
        const text = (await b.innerText()) || '';
        if (!ariaLabel.trim() && !text.trim()) {
          missing.push(await b.evaluate(el => el.outerHTML.slice(0, 200)));
        }
      }
      expect(missing, `buttons missing accessible name on ${path}: ${JSON.stringify(missing)}`).toEqual([]);
    });
  
    test(`${path}: no 'javacript:' typo`, async ({ page }) => {
      await page.goto(path);
      const html = await page.content();
      expect(html, `${path} contains 'javacript:' typo`).not.toContain('javacript:');
    });
  }
  ```

- \[ \] **Step 2:** Run the spec — MUST FAIL on current branch (H2 not done yet).

  ```
  npm --prefix tests/e2e run test:fast -- --grep "27-a11y"
  ```

  Expected: at least 5-6 of the 27 assertions FAIL (the ones for pages that still have `<a href="javascript:">` and unlabeled icon buttons).

- \[ \] **Step 3:** Confirm failure modes are the assertion messages above (not a setup error). If setup error, fix the spec before continuing.

- \[ \] **Step 4:** Commit + push (knowingly red).

  ```
  git add tests/e2e/27-a11y-buttons.spec.js
  git commit -m "test(a11y): add crawler spec asserting all listings have accessible buttons (currently failing)"
  git push origin dev
  ```

---

## Task H2: Mass button conversion across 8 listing pages

**Closes audit finding:** H2 (\~80+ `<a href="javascript:">` + icon-only `<button>` without `aria-label` across all listing pages).

**Drive:** iterate per-file, run the `27-a11y` spec after each file, stop when all 27-a11y assertions are green.

**Reuse:** add CSS class `.link-button` once in common.html (do this in Step 1).

### ▼▼Step 1: Add `.link-button` CSS once in common.html

- \[ \] Append to common.html's style block:

  ```
  .link-button {
      background: transparent;
      border: 0;
      cursor: pointer;
      padding: 0;
      font: inherit;
      color: #bbbbbb;
      text-decoration: underline;
  }
  .link-button:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }
  ```

### ▼▼Step 2: Per-file transformations (apply patterns A + B)

**Pattern A —** `<a href="javascript:fn(...)">label</a>` **(descriptor / inline action)**:Before:

```
<a href="javascript:editDescr('${id}')" style="color: #bbbbbb; text-decoration: underline;">${descr}</a>
```

After:

```
<button type="button" class="link-button" onclick="editDescr('${id}')" aria-label="${commonStr.edit}">${descr}</button>
```

**Pattern B — icon-only** `<button>` **without text**:Before:

```
<button type="button" class="layui-btn layui-btn-sm" onclick="edit('${id}')">
    <i class="layui-icon layui-icon-edit"></i>
</button>
```

After:

```
<button type="button" class="layui-btn layui-btn-sm" onclick="edit('${id}')" aria-label="${commonStr.edit}">
    <i class="layui-icon layui-icon-edit" aria-hidden="true"></i>
</button>
```

**Pattern C —** `<button>` **already has text adjacent to icon — just add** `aria-hidden`:Before:

```
<button type="button" class="layui-btn" onclick="add()">
    <i class="layui-icon layui-icon-add-1"></i> ${commonStr.add}
</button>
```

After:

```
<button type="button" class="layui-btn" onclick="add()">
    <i class="layui-icon layui-icon-add-1" aria-hidden="true"></i> ${commonStr.add}
</button>
```

### ▼▼Step 3: Apply per file (gate after each)

For each file:

1. Apply pattern A everywhere `href="javascript:"` appears.
2. Apply pattern B everywhere `<button>` contains only `<i class="layui-icon">` (no adjacent text).
3. Apply pattern C everywhere `<i class="layui-icon">` appears inside a `<button>` that ALSO has text.
4. `mvn package -DskipTests`.
5. Run `npm --prefix tests/e2e run test:fast -- --grep "27-a11y"` — assertions for this page should go green (others may still be red).

- \[ \] **3a.** `server/index.html` — lines 213, 222-223 + toolbar / row icons.
- \[ \] **3b.** `upstream/index.html` — lines 119, 122 + toolbar / row icons.
- \[ \] **3c.** `http/index.html` — toolbar (41-56) + row actions (102-115).
- \[ \] **3d.** `stream/index.html` — toolbar (39-54) + row actions (80-89).
- \[ \] **3e.** `cert/index.html` — toolbar (36-40) + row actions (128-160).
- \[ \] **3f.** `denyAllow/index.html` — toolbar (90-94) + row actions (140-144).
- \[ \] **3g.** `admin/index.html` — lines 95-96 (`qr`, `test`) + row action icons.
- \[ \] **3h.** `remote/index.html` — lines 95-96 (protocol/group dropdowns) + toolbar 42-60.
  - **Note:** remote table rows are rendered by JS `treeTable` config. Audit those JS template strings too — open `src/main/resources/static/js/adminPage/remote/index.js` and apply the same patterns to inline HTML strings.

### ▼▼Step 4: Final gate

- \[ \] Run full `npm --prefix tests/e2e run test:fast` — all 27 specs PASS (26 originals + 27-a11y all green).

- \[ \] Manual smoke: load each listing page, tab through buttons — every button receives focus and has an accessible name.

- \[ \] Commit + push.

  ```
  git add src/main/resources/WEB-INF/view/adminPage/common.html src/main/resources/WEB-INF/view/adminPage/server/index.html src/main/resources/WEB-INF/view/adminPage/upstream/index.html src/main/resources/WEB-INF/view/adminPage/http/index.html src/main/resources/WEB-INF/view/adminPage/stream/index.html src/main/resources/WEB-INF/view/adminPage/cert/index.html src/main/resources/WEB-INF/view/adminPage/denyAllow/index.html src/main/resources/WEB-INF/view/adminPage/admin/index.html src/main/resources/WEB-INF/view/adminPage/remote/index.html src/main/resources/static/js/adminPage/remote/index.js
  git commit -m "a11y(tables): convert pseudo-link actions to buttons and label icons across 8 listing pages"
  git push origin dev
  ```

---

## Wrap-up

- \[ \] `git log --oneline c41a0ee5..HEAD` — confirm \~10 conventional commits, each scoped.
- \[ \] Update audit doc \[docs/superpowers/plans/2026-06-29-ui-audit.md\](file:///e:/nginxWebUI/docs/superpowers/plans/2026-06-29-ui-audit.md) — mark each finding closed with commit hash.
- \[ \] Move this plan file from `~/.claude/plans/structured-forging-kitten.md` to `docs/superpowers/plans/2026-06-29-ui-audit-wave2.md` per user memory rules.

---

## Verification (end-to-end)

After all tasks complete:

1. **mvn build & jar size sanity:**

   ```
   mvn clean package -DskipTests
   ls -la target/nginxWebUI-*.jar
   ```

   Expected: BUILD SUCCESS, jar size similar to current (\~70-80 MB).

2. **Full Playwright suite:**

   ```
   npm --prefix tests/e2e run test:fast
   ```

   Expected: 27 specs PASS.

3. **mcp playwright manual at 1280×800:**

   - Login → all listing pages (server, upstream, http, stream, cert, denyAllow, protectionCert, admin, remote).
   - For each: tab through every interactive element; each receives visible focus; each has accessible name.
   - DevTools axe-core (or similar) on each page — zero "serious" or "critical" violations on the categories we audited.

4. **CDN / typo regression check:**

   ```
   grep -rn 'unpkg\.com\|cdn\.jsdelivr\|layuicdn\|hm\.baidu\|javacript:' src/main/resources
   ```

   Expected: NO matches.

5. **i18n triple-sync check:**

   ```
   diff <(grep -oE '^[a-zA-Z.]+' src/main/resources/messages.properties | sort -u) <(grep -oE '^[a-zA-Z.]+' src/main/resources/messages_zh_TW.properties | sort -u)
   diff <(grep -oE '^[a-zA-Z.]+' src/main/resources/messages.properties | sort -u) <(grep -oE '^[a-zA-Z.]+' src/main/resources/messages_en_US.properties | sort -u)
   ```

   Expected: NO diff output.

---

## Open Questions (resolve before starting)

1. **remote/index.html table rows are rendered by JS** `treeTable` — must Pattern A/B/C apply to JS template strings inside `remote/index.js` too? Read `treeTable` config before Task H2-3h.
2. **menu.html collapsable items in** `layui-nav-tree` — `<a href="javascript:;">` at lines 7, 63 are Layui-controlled. Spec must exclude them via `.layui-nav-tree` (verify the exclusion in H2-PRE works).
3. **mcp playwright + browser autofill** noted in earlier session — should the autocomplete attrs in M1 be tested via fresh incognito context or accept the autofill warning? Plan assumes the latter.

---

## Self-Review

- **Spec coverage:** Every audit finding (H1, H2, H3a, H3b, H4, M1, M2, L1, L2) has a dedicated task. L3 was investigated and found to need no change (cert placeholder is i18n key, value already uses `…`); no task created.
- **Placeholders:** None. Every snippet shows before/after code. No "similar to" references.
- **Type / name consistency:** `getMainArea()` returns `{w, h, offset}` — used identically in H4 as in commit `acff0aff`. `.link-button` and `.header-link-btn` are distinct CSS classes used per documented purpose. i18n keys `commonStr.mainNav`, `commonStr.changeLang`, `commonStr.moduleList`, `commonStr.refreshCaptcha` defined once each in M2/H1 and referenced consistently.
- **Pattern repetition:** H2 lists pattern A/B/C once and lists 8 representative files, per skill rules ("describe the pattern once and list a few representative paths — do not enumerate every file or line number").