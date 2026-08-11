# Module Param Templates + Full nginx-mod Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a complete parameter-template editor (group dropdown + compact param rows) and a community-oriented template library for all Alpine nginx open-source modules (except legacy GeoIP v1), on top of a full-module Docker image and module-management catalog.

**Architecture:** Templates remain a pure library (`Template` + `Param` rows, `def=""` = manual only). Top-level collapse groups come from `TemplateController.GROUP_DEFS`. New community packs are seeded once via `InitConfig` migration flags. Dynamic modules ship in the Docker image; `load_module` is driven only by Module-table enable flags in `ConfService.buildConf`, never from Basic `load_module` rows.

**Tech Stack:** Java 17 + Solon 3.x, Freemarker + Layui + jQuery, SQLite/PostgreSQL via SqlHelper, Docker Alpine 3.24 + nginx 1.30.x dynamic modules, Playwright E2E.

## Global Constraints

- **Do not change** the four existing rateLimit param *values* for http/server Connection Limit and Rate Limit (`conn_limit` / `req_limit` zone names stay).
- **Do not** seed templates for legacy `ngx_http_geoip` / `ngx_stream_geoip`.
- User-facing strings: update **all three** `messages.properties` / `messages_zh_TW.properties` / `messages_en_US.properties` (ISO-8859-1 `\uXXXX` for CJK).
- Prefer manual `def=""` for new community templates; stream zone names use `s_` prefix to avoid shared-zone conflicts with http.
- Templates ≠ auto load_module: module must be ON under 基本參數 → 模組管理.
- Do not commit unrelated files (e.g. `squirrel.toml` dockhand junk).

## Implementation status (session context)

Much of the feature is **already on the working tree** (`dev`, uncommitted relative to `1dc5f1c3`). This plan is the **source of truth for verification + finish work**: each task must still be checked; if code matches the step, tick it and only fill gaps.

| Area | Status |
|------|--------|
| Design spec | Written: `docs/superpowers/specs/2026-08-12-module-param-templates-design.md` |
| Full Dockerfile nginx-mod set | Done (working tree) |
| MODULE_CATALOG + migration | Done (working tree) |
| Template group dropdown UI + 40px textarea | Done (working tree) |
| Community template seed (~22) | Done (working tree) |
| Playwright coverage | **Missing** |
| Git commit of this feature set | **Missing** |

---

## File map

| File | Responsibility |
|------|----------------|
| `Dockerfile` | Install all Alpine `nginx-mod-*` runtime packages (no `nginx-mod-dev`) |
| `src/main/java/com/cym/service/NginxService.java` | `MODULE_CATALOG`, `SAFE_MODULES`, dependency map, enabled paths |
| `src/main/java/com/cym/config/InitConfig.java` | Module seed/migration; stream conn templates; community template seed |
| `src/main/java/com/cym/service/ConfService.java` | `load_module` from enabled modules; inject `def=stream` template params into `stream{}` |
| `src/main/java/com/cym/controller/adminPage/TemplateController.java` | `GROUP_DEFS`, group options for view, list grouping |
| `src/main/resources/WEB-INF/view/adminPage/template/index.html` | Editor modal (group → name → def → params), 40px textareas |
| `src/main/resources/static/js/adminPage/template/index.js` | groupName resolve/custom, addOver payload |
| `src/main/resources/messages*.properties` | templateGroup.*, templateStr.group*, moduleStr.descr* |
| `src/main/resources/static/css/adminPage/base.css` | Global input border contrast (related polish) |
| `tests/e2e/34-template-group-editor.spec.js` | **Create** — E2E for group field + compact param UI |
| `docs/superpowers/specs/2026-08-12-module-param-templates-design.md` | Spec |
| `docs/superpowers/plans/2026-08-12-module-param-templates-plan.md` | This plan |

---

### Task 1: Verify Docker full module image

**Files:**
- Modify (if gap): `Dockerfile`
- Verify: running container `/usr/lib/nginx/modules/*.so`

**Interfaces:**
- Produces: Image with ≥40 dynamic `.so` files for nginx modules

- [ ] **Step 1: Confirm Dockerfile lists runtime modules**

Open `Dockerfile` and ensure `apk add` includes at least: stream/*, http-js, keyval, set-misc, array-var, encrypted-session, auth-jwt, naxsi, nchan, vts, vod, redis2, image-filter, xslt-filter, brotli, zstd, lua, lua-upstream, mail, rtmp, dynamic-upstream, dynamic-healthcheck, and does **not** require `nginx-mod-dev`.

- [ ] **Step 2: Build and start**

```bash
mvn package -DskipTests -q
cd docker && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build nginxwebui
```

Expected: exit 0, container healthy.

- [ ] **Step 3: Count modules on disk**

```bash
docker exec nginxwebui sh -c "ls /usr/lib/nginx/modules/*.so | wc -l"
```

Expected: count ≥ 45 (session verified ~48).

- [ ] **Step 4: Note slowfs actual filename**

```bash
docker exec nginxwebui sh -c "ls /usr/lib/nginx/modules/*slow*"
```

Expected: `ngx_http_slowfs_module.so` (catalog must use this name, not `ngx_slowfs_cache_module.so`).

---

### Task 2: Module catalog in app (SAFE + DB seed)

**Files:**
- Modify: `src/main/java/com/cym/service/NginxService.java`
- Modify: `src/main/java/com/cym/config/InitConfig.java`
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_zh_TW.properties`
- Modify: `src/main/resources/messages_en_US.properties`

**Interfaces:**
- Consumes: disk `.so` under `/usr/lib/nginx/modules`
- Produces: `NginxService.MODULE_CATALOG` (`String[][]` of `{filename, descrKey}`); `InitConfig.seedMissingModulesFromCatalog()`; setting flag `moduleCatalogFullSeeded=1`

- [ ] **Step 1: Confirm MODULE_CATALOG covers disk modules**

`MODULE_CATALOG` must include every managed `.so` (stream*, ndk, lua*, geoip2, brotli*, zstd*, headers_more, cache_purge, echo, js, keyval, fair, zip, upload*, perl, cookie_flag, dav_ext, fancyindex, image_filter, xslt, auth_jwt, naxsi, nchan, vts, vod, redis2, log_zmq, accounting, acme, shibboleth, slowfs, untar, dynamic_*, mail, rtmp).  
**Exclude** legacy-only if product requires — do **not** promote geoip v1 templates later; catalog may still list geoip v1 modules for load_module UI.

- [ ] **Step 2: Confirm migration path**

On startup, if `moduleCatalogFullSeeded` ≠ `1`, insert missing Module rows with `enable=false`, then set flag. Empty DB uses full catalog seed.

- [ ] **Step 3: Verify DB row count after restart**

```bash
docker exec nginxwebui-postgres psql -U nginxwebui -d nginxwebui -c "SELECT count(*) FROM module;"
```

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
