# Module Param Templates + Slim nginx-mod Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a complete parameter-template editor (group dropdown + compact param rows + multi-select context tags) and a community-oriented template library, on top of a **slim** Docker nginx-mod image (~31 modules) and `MODULE_CATALOG`-ordered `load_module`.

**Architecture:** Templates are a pure library (`Template` + `Param`). `Template.def` = multi-select auto-apply contexts (`http,server,server1,server2,stream,location,upstream`), normalized/filtered by `TemplateDefUtils`. Top-level collapse groups come from `TemplateController.GROUP_DEFS` (**no `upload` group** — modules removed). Dynamic modules ship in the Docker image; `load_module` is driven only by Module-table enable flags + catalog order in `NginxService.getEnabledModulePaths()`, never from Basic `load_module` rows.

**Tech Stack:** Java 17 + Solon 3.x, Freemarker + Layui + jQuery, SQLite/PostgreSQL via SqlHelper, Docker Alpine 3.24 + nginx 1.30.x dynamic modules (slim set), Playwright E2E.

## Global Constraints

- **Do not change** the four existing rateLimit param *values* for http/server Connection Limit and Rate Limit (`conn_limit` / `req_limit` zone names stay).
- **Do not** seed templates for legacy `ngx_http_geoip` / `ngx_stream_geoip`.
- **Do not** re-add unmaintained modules: fair, legacy geoip, perl, upload*, zip, untar, slowfs, echo, dav_ext, fancyindex, xslt, shibboleth, log_zmq, accounting, redis2.
- User-facing strings: update **all three** `messages.properties` / `messages_zh_TW.properties` / `messages_en_US.properties` (ISO-8859-1 `\uXXXX` for CJK).
- Prefer manual `def=""` for new community templates; stream zone names use `s_` prefix to avoid shared-zone conflicts with http.
- Templates ≠ auto load_module: module must be ON under 基本參數 → 模組管理.
- Do not commit unrelated files (e.g. `squirrel.toml` dockhand junk).

## Implementation status (as of 2026-08-12)

| Area | Status |
|------|--------|
| Design spec | Done: `docs/superpowers/specs/2026-08-12-module-param-templates-design.md` |
| **Slim** Dockerfile nginx-mod set (~31 `.so`) | **Done** — not full ~48 community alpine set |
| MODULE_CATALOG + prune migration `moduleCatalogHardened20260812` | **Done** |
| load_module order = catalog (not DB seq) | **Done** |
| Template group dropdown UI + 40px textarea | **Done** |
| Community template seed | **Done** |
| Multi-select def tags + human labels | **Done** |
| Safety lock (UI disabled tags + save filter + stream inject) | **Done** — UI via `/adminPage/template/allowedDefs` |
| Playwright `34-template-group-editor` | **Done** (asserts `#defTags`, if→stream disabled) |
| Unit tests `TemplateDefUtilsTest` | **Done** |
| Structure docs cite nginx.org Context | **Done** (`docs/nginx結構.md`) |
| Git commits | `130a633c`, `e3997a01`, … |

---

## File map

| File | Responsibility |
|------|----------------|
| `Dockerfile` | **Slim** Alpine `nginx-mod-*` (~31 .so); no fair/legacy geoip/perl/upload*… |
| `NginxService.java` | `MODULE_CATALOG` order = load_module order; `getEnabledModulePaths()` |
| `TemplateDefUtils.java` | multi-def parse/normalize; `allowedContexts` / `normalizeAndFilter` |
| `InitConfig.java` | module prune; stream def sanitize; community seed |
| `ConfService.java` | load_module; inject def containing `http`/`stream`; stream top-level safety |
| `ParamService.java` | auto-apply by def contains; stream server1/2 filter |
| `TemplateController.java` | `GROUP_DEFS` (no upload); `allowedDefs` API |
| `template/index.html` + `index.js` | group editor; multi tags; **allowedDefs** for disable |
| `tests/e2e/34-template-group-editor.spec.js` | group + defTags + if→stream disable |
| `src/test/.../TemplateDefUtilsTest.java` | unit tests for safety lock |
| `docs/nginx結構.md` | structure tree + official Context citations |

---

### Task 1: Verify Docker **slim** module image (supersedes “full module”)

**Files:**
- `Dockerfile`
- Verify: container `/usr/lib/nginx/modules/*.so`

**Interfaces:**
- Produces: Image with **~31** dynamic `.so` files (product default)

- [x] **Step 1: Slim Dockerfile** — curated `nginx-mod-*` only; excludes fair, legacy geoip, perl, upload*, zip, untar, slowfs, echo, dav, fancyindex, xslt, shibboleth, log_zmq, accounting, redis2.

- [x] **Step 2: Build and start** — `mvn package` + compose `--build nginxwebui` healthy.

- [x] **Step 3: Count modules on disk** — expect **31** (not ≥45).

- [x] **Step 4: No slowfs/fair/upload on disk** — verified GONE after harden.

---

### Task 2: Module catalog in app (SAFE + DB seed) — **Done (slim catalog)**

**Files:**
- `NginxService.java` — `MODULE_CATALOG` ~31 rows; catalog order for load
- `InitConfig.java` — `moduleCatalogHardened20260812` prune + resequence
- i18n moduleStr.descr*

**Interfaces:**
- Produces: `MODULE_CATALOG`; seed/prune migrations

- [x] **Step 1: MODULE_CATALOG matches slim disk set** (not full alpine community set).

- [x] **Step 2: Migrations** — `moduleCatalogFullSeeded` + `moduleCatalogHardened20260812`.

- [x] **Step 3: DB row count** — ~31 module rows after prune.

Expected: ≈ catalog size (~48).

- [ ] **Step 4: Confirm ConfService load_module path**

`ConfService.buildConf` on Linux must call `nginxService.getEnabledModulePaths()` and emit `load_module <path>;` for each. Basic table `load_module` rows must be skipped.

---

### Task 3: Template editor UI — group dropdown + 40px params

**Files:**
- Modify: `src/main/resources/WEB-INF/view/adminPage/template/index.html`
- Modify: `src/main/resources/static/js/adminPage/template/index.js`
- Modify: `src/main/java/com/cym/controller/adminPage/TemplateController.java`
- Modify: `messages*.properties` (`templateStr.group*`, `templateGroup.*`)

**Interfaces:**
- Produces: `groupOptions` model list of `{key, label, desc}`; POST `/adminPage/template/addOver` accepts `groupName`
- Consumes: `Template.groupName`, `Template.def`

- [ ] **Step 1: Verify modal field order**

Editor modal must show, in order:

1. **分組** `#groupName` select (GROUP_DEFS labels + desc, plus `_custom`)
2. Custom input `#groupNameCustom` when `_custom` selected
3. **模板名稱** `#name`
4. **自動套用到** `#def`
5. Param table `#paramList`

- [ ] **Step 2: Verify CSS compact textareas**

In `template/index.html` style block:

```css
#paramList textarea.layui-textarea {
	height: 40px !important;
	min-height: 40px !important;
}
```

- [ ] **Step 3: Verify JS payload**

`addOver()` must send:

```javascript
{
  id, name, def,
  groupName: resolveGroupName(), // never empty
  paramJson: JSON.stringify([{name, value}, ...])
}
```

`resolveGroupName()`: if select is `_custom`, use trimmed `#groupNameCustom`; else select value.

- [ ] **Step 4: Manual smoke**

1. Open `/adminPage/template`
2. Click 添加參數模板
3. Choose group like CORS (option text includes description)
4. Name + one param → submit → row appears under that collapse

---

### Task 4: Community template seed (modules except legacy geoip)

**Files:**
- Modify: `src/main/java/com/cym/config/InitConfig.java` (`communityTemplateDefs`, `seedModuleCommunityTemplatesIfMissing`, flag `moduleCommunityTemplatesSeeded`)
- Modify: `TemplateController.GROUP_DEFS` for new groups
- Modify: `messages*.properties` for new `templateGroup.*` keys

**Interfaces:**
- Produces: Template rows with unique `name`, `groupName`, `def` (usually `""`), Param children
- Setting: `moduleCommunityTemplatesSeeded=1`

- [ ] **Step 1: Confirm GROUP_DEFS keys**

Must include at least:  
`proxy, cache, cors, rateLimit, security, geoip, crowdsec, compress, observe, auth, njs, keyval, util, media, upload, realtime, waf, upstream_ext, mail`

- [ ] **Step 2: Confirm community packs exist (names exact)**

Examples that must be present or seedable:

| Name contains | groupName |
|---------------|-----------|
| Brotli Full | compress |
| Zstd Full | compress |
| VTS Zone | observe |
| VTS Status Location | observe |
| Auth JWT | auth |
| njs Import / njs Content | njs |
| Keyval Zone HTTP / Stream | keyval |
| Set Misc / Echo / Cookie Flag / Headers More | util |
| Image Filter / VOD | media |
| Upload Progress | upload |
| Nchan PubSub | realtime |
| NAXSI Basic | waf |
| Upstream Fair / Dynamic Healthcheck | upstream_ext |
| Mail Auth Basic | mail |
| Cache Purge Location | cache |

- [ ] **Step 3: Hard rules for seed data**

- Do **not** change param values of:
  - Connection Limit (http/server) with `conn_limit`
  - Rate Limit (http/server) with `req_limit`
- Stream connection templates use zone **`s_conn_perip`** only
- No templates for legacy geoip v1
- New community templates: `def=""` unless product explicitly sets stream/server1 for stream conn packs

- [ ] **Step 4: Verify DB after restart**

```bash
docker exec nginxwebui-postgres psql -U nginxwebui -d nginxwebui -c \
  "SELECT group_name, count(*) FROM template GROUP BY 1 ORDER BY 1;"
```

Expected: multiple groups including compress, observe, auth, util, etc.; rateLimit still present.

- [ ] **Step 5: Optional gap fill (only if user asks)**

If packs are still thin for redis2 / dav / fancyindex / shibboleth, add **one** skeleton template each under `util` or dedicated group — name must include 「建議」and required module.

---

### Task 5: Stream def injection + load_module visibility

**Files:**
- Modify: `src/main/java/com/cym/service/ConfService.java`
- Verify: 启用配置 left pane

**Interfaces:**
- Produces: For each `Template` with `def="stream"`, inject all its `Param` rows into top-level `stream { }` before deny/upstream/server

- [ ] **Step 1: Read buildConf stream section**

After iterating `Stream` table rows, before `buildDenyAllow(..., "stream")`, templates with `def=stream` must be applied as `name value;` entries.

- [ ] **Step 2: Manual check**

Open 启用配置. With stream module + any `def=stream` template (e.g. Connection Limit stream), left preview should show corresponding directives under `stream {` **or** only when those templates exist and modules are loaded appropriately.

- [ ] **Step 3: Confirm Basic load_module is not duplicated**

Left preview must **not** double-load modules from Basic table rows.

---

### Task 6: Playwright E2E

**Files:**
- Create: `tests/e2e/34-template-group-editor.spec.js`
- Modify if needed: none of 26-offline / 27-a11y unless introducing CDN or `<a href="javascript:">`

**Interfaces:**
- Consumes: login helper, page `/adminPage/template`

- [ ] **Step 1: Write spec**

```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('參數模板分組編輯器', () => {
  test('新增彈窗含分組下拉且參數 textarea 高度約 40px', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    await page.locator('button', { hasText: /添加参数模板|添加參數模板|Add parameter template/ }).click();
    await page.waitForSelector('#windowDiv', { state: 'visible' });
    await expect(page.locator('#groupName')).toBeVisible();
    await expect(page.locator('#name')).toBeVisible();
    await expect(page.locator('#def')).toBeVisible();

    // add one param row
    await page.locator('#windowDiv button', { hasText: /添加参数|添加參數|Add parameter/ }).click();
    const ta = page.locator('#paramList textarea').first();
    await expect(ta).toBeVisible();
    const box = await ta.boundingBox();
    expect(box.height).toBeLessThanOrEqual(56); // 40px + padding
    expect(box.height).toBeGreaterThanOrEqual(32);
  });

  test('列表存在分組 collapse（含 CORS 或 compress）', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    const title = page.locator('.layui-colla-title');
    await expect(title.first()).toBeVisible();
    const text = await page.locator('.layui-collapse').innerText();
    expect(text.length).toBeGreaterThan(20);
  });
});
```

- [ ] **Step 2: Run test**

```bash
mvn package -DskipTests -q
npx playwright test tests/e2e/34-template-group-editor.spec.js --config=tests/e2e/playwright.fast.config.js
```

Expected: PASS (adjust button regex if locale differs).

- [ ] **Step 3: Commit test**

```bash
git add tests/e2e/34-template-group-editor.spec.js
git commit -m "test(e2e): cover template group editor and compact param rows"
```

---

### Task 7: Integration rebuild + commit feature set

**Files:**
- All feature files above + design/plan docs
- **Exclude:** `squirrel.toml` unless intentional

- [ ] **Step 1: Rebuild and healthcheck**

```bash
mvn package -DskipTests -q
cd docker && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build nginxwebui
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps
```

Expected: `nginxwebui` healthy.

- [ ] **Step 2: Smoke checklist**

| Check | Expected |
|-------|----------|
| `/adminPage/template` groups | Multiple collapses with community packs |
| Add modal group select | Shows CORS-style labels with descriptions |
| Param textarea | ~40px tall |
| 启用配置 top | `load_module` lines for enabled modules only |
| 基本參數 → 模組管理 | ~48 rows, new ones default off |

- [ ] **Step 3: Commit (split if large)**

```bash
git add Dockerfile \
  src/main/java/com/cym/config/InitConfig.java \
  src/main/java/com/cym/controller/adminPage/TemplateController.java \
  src/main/java/com/cym/model/Template.java \
  src/main/java/com/cym/service/ConfService.java \
  src/main/java/com/cym/service/NginxService.java \
  src/main/resources/WEB-INF/view/adminPage/template/index.html \
  src/main/resources/static/js/adminPage/template/index.js \
  src/main/resources/static/css/adminPage/base.css \
  src/main/resources/messages.properties \
  src/main/resources/messages_zh_TW.properties \
  src/main/resources/messages_en_US.properties \
  docs/superpowers/specs/2026-08-12-module-param-templates-design.md \
  docs/superpowers/plans/2026-08-12-module-param-templates-plan.md

git commit -m "$(cat <<'EOF'
feat(template): group editor, community packs, full nginx-mod catalog

Add template group dropdown and 40px param rows; seed optional community
param packs for Alpine modules; ship full dynamic module image and catalog.
EOF
)"
```

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| Group dropdown like CORS top-level | Task 3 |
| Param height ~40px | Task 3 |
| Seed community templates, def empty / annotated names | Task 4 |
| No legacy geoip v1 templates | Task 4 Step 3 |
| Do not change old rateLimit param values | Task 4 Step 3 |
| Modules installed for expansion | Task 1–2 |
| Manual apply; load_module from module manager | Task 2 Step 4, Task 5 |
| Success criteria (list + seed + UI) | Task 3–4, Task 7 smoke |

**Placeholder scan:** none intentional.  
**Gaps for later (out of this plan):** picker `contexts` filter (http/stream incompatible UI); auto-apply policy for `def=http` enum; replace-path nginx -t gate (separate conf safety work).

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-12-module-param-templates-plan.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — this session with executing-plans + checkpoints  

**Which approach?**  

Note: large parts of Tasks 1–5 are already on the working tree; prefer a verification-first pass, then Task 6 (E2E) + Task 7 (commit).
