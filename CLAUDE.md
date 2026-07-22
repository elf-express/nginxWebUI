# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- SPECKIT START -->
For additional context about the active work, see plans under [docs/superpowers/plans/](docs/superpowers/plans/) — the most recent dated file is usually the current focus.
<!-- SPECKIT END -->

> 本檔以英文為主、關鍵處附中文註解。新增使用者可見字串仍須同步三份 i18n（見下）。
> **Code navigation:** this repo is indexed by CodeGraph (`.codegraph/codegraph.db`). Reach for `codegraph_explore` (MCP) or `codegraph explore "<question>"` (shell) BEFORE grep/find/Read when locating or understanding code — one call returns verbatim source + call paths in far fewer tokens than a grep/read loop.

## Overview
nginxWebUI is a web tool that simplifies NGINX configuration — users fill in UI forms instead of hand-writing `nginx.conf` (reverse proxy, SSL, load balancing, security hardening).

Entry point: [com.cym.NginxWebUI](src/main/java/com/cym/NginxWebUI.java) — `@SolonMain` + `@EnableScheduling`.
> 注意：啟動時會先殺掉同名舊 jar process 再 `Solon.start()`。

## Tech Stack
- **Backend:** Java 17 (LTS) + [Solon 3.10.7](https://solon.noear.org/) — **NOT Spring Boot**
  - DI: `@Component` (services) / `@Controller` (controllers) / `@Inject` (not `@Service` / `@Autowired`)
  - Routing: `@Mapping("/path")` on both class and method level
  - Scheduling: `@Scheduled` from `solon-scheduling-simple`
- **Frontend:** Layui + jQuery + Freemarker (server-side rendered — **not an SPA**)
- **DB:** SQLite (default) / PostgreSQL / MySQL — switch via `--spring.database.type`
- **GeoIP:** `com.maxmind.db:maxmind-db` **4.1.0** reads MMDB `build_epoch` for the version badge.
  > 注意：4.1.0 起 `Metadata` 改為 Java **record**（需 Java 16+），`getBuildDate()` 已移除 → 用 `buildTime()`（Instant）。此版由 dependabot 於 2026-07-05 升級（同批帶動 Java 8→17 地基升級）；讀取邏輯見 `GeoipService.readBuildDate`。
- **Build:** Maven → `target/nginxWebUI-<version>.jar` (fat jar, `jar-with-dependencies`)
- **Tests:** Playwright E2E (**no JUnit for end-to-end**)
- **Containers:** Docker Compose stack (PostgreSQL + CrowdSec). **Two self-built images: `nginxwebui` + `nginxwebui-crowdsec`** (crowdsec = official base + config baked from [docker/crowdsec/](docker/crowdsec/)).
  > 注意：Loki + Promtail + Grafana 已於 2026-06-30 從本專案移除 — nginx 內建 access/error log 已足夠排查,CrowdSec 直接從共享的 `nginxwebui_log` volume 讀 nginx log,不需要 Loki 中介。

## Directory Structure
```
src/main/java/com/cym/
├── config/         # init, filters, config (InitConfig, AppFilter)
├── controller/     # adminPage/ (28 page controllers) + api/ (11 REST API controllers)
├── ext/            # view DTOs (e.g. DenyAllowExt, GeoipDbInfo) — 非 @Table
├── model/          # data models (@Table entities: Server, Location, Http, ...)
├── service/        # business logic (@Component + @Inject SqlHelper)
├── sqlhelper/      # home-grown ORM
└── utils/          # utilities

src/main/resources/
├── WEB-INF/view/adminPage/   # Freemarker templates (HTML)
├── static/js/adminPage/      # frontend JS (one dir per page)
├── static/lib/               # third-party libs (layui, codemirror...)
├── messages.properties       # i18n 简体中文
├── messages_zh_TW.properties # i18n 繁體中文
├── messages_en_US.properties # i18n English
└── app.yml                   # app config

tests/e2e/          # Playwright specs
docs/               # design docs & plans
```

## Conventions

### Core principles / 核心原則
1. **Don't break existing business logic** — changes are UI polish or additive features only.
2. **Multilingual** — every new user-facing string updates all 3 `messages*.properties`.
3. **Automated tests** — every new/changed feature ships a Playwright test.
4. **Zero-risk first** — prefer pure-frontend / purely-additive changes.
5. **A11y baseline** — never introduce `<a href="javascript:...">` pseudo-links for actions; use `<button type="button">`. Header / sidebar / table-action / modal / captcha already migrated; guarded by [tests/e2e/27-a11y-buttons.spec.js](tests/e2e/27-a11y-buttons.spec.js). Icon-only controls need `aria-label`. New pages need an `<h1>` landmark.
6. **Offline-first frontend** — vendor third-party libs into `static/lib/` rather than loading from a public CDN (this is a self-hosted admin tool, often deployed air-gapped). Guarded by [tests/e2e/26-offline-no-cdn.spec.js](tests/e2e/26-offline-no-cdn.spec.js).

### Frontend
- Use Layui components; refresh `select` / `checkbox` with `form.render()`.
- JS lives per page (e.g. `static/js/adminPage/server/index.js`); reachable at URL `/js/...` (not `/static/js/...`).
- **i18n key convention:** `<page>Str.<field>` (e.g. `serverStr.add`, `geoipStr.download`). Controller injects `MessageUtils m`; template uses `${serverStr.xxx}`.
- JS i18n globals (e.g. `commonStr`, `geoipStr`) are auto-generated in [common.html](src/main/resources/WEB-INF/view/adminPage/common.html) from `messageHeaders` — a new prefix appears automatically once added to properties.
> 注意：新增任何使用者可見字串，必須同步改三份 properties：`messages.properties`（簡）、`messages_zh_TW.properties`（繁）、`messages_en_US.properties`（英）。CJK 值用 `\uXXXX` escape（檔案是 ISO-8859-1）。

### Backend
- Controllers live under `controller/adminPage/` (page controllers, incl. CrowdSec / Geo / Asn / ProtectionCert / SiteResource / Geoip) and `controller/api/` (REST API: `*Api` for basic/cert/denyAllow/nginx/param/password/server/upstream/www + `Token` / `Upload`) — none in `controller/` root.
- Services: `@Component` + `@Inject SqlHelper sqlHelper;`. Persistence via `SqlHelper` (home-grown ORM, not JPA — see cheatsheet).
- Primary keys: always `SnowFlakeUtils.getId()` (snowflake; stored as String, generated as Long).
- Init logic in `InitConfig.java`; runtime config via `app.yml` or launch args.
- **Seed-on-empty pattern:** fork ships sensible defaults so users don't bootstrap from zero — e.g. `InitConfig.start()` seeds 6 malicious-IP blocklist rules via `DenyAllowService.defaultBlocklistRules()` when the table is empty (guarded by `denyAllowSeeded` setting flag; async first-fetch fills IPs). Apply the same pattern for any new feature where "empty DB ≈ broken UX."

### Testing (see docs/superpowers/plans/playwright-guide.md)
- Specs in `tests/e2e/` — numbered `01-login` … `33-server-save` (contiguous) plus standalone (`flag-svg-integrity`). New feature → next number.
- **PG smoke:** `npm run test:pg` — docker 起 postgres:18-alpine(port 15432),跑 01+33 驗證 PostgreSQL 上的登入與 server 儲存(主套件只跑 SQLite,跨 DB 行為差異靠這層抓)。
- Match 簡/繁 button text with regex: `/批量輸入|批量输入/`.
- Drive Layui widgets via `page.evaluate()`.
- Run: `npm test` (headed) · `npm run test:fast` (headless/CI) · `npx playwright test tests/e2e/08-crowdsec.spec.js` (one file) · `npm run report` (http://localhost:9400).
> 注意：測試會自動啟動獨立 server（port 18080）+ 獨立 SQLite，不碰 `./dev-home/`。`tests/e2e/helpers.js` 動態解析 `target/nginxWebUI-*.jar`，所以跑測試前要先 `mvn package`。

### Docker (see docs/superpowers/plans/docker-guide.md — partially superseded)
- container_name: flat `nginxwebui` (app) / `nginxwebui-<service>` (sidecar) — no version suffix since 5.1.0.
- volume name: `nginxwebui_{purpose}_data` (explicit `name:` to dodge compose project prefix).
- healthcheck + startup order required; `entrypoint.sh` must be LF (`.gitattributes` enforces).
- **Two self-built images:** `nginxwebui` (root Dockerfile) + `nginxwebui-crowdsec` (`docker/crowdsec/Dockerfile` = official crowdsec base + baked config). CrowdSec is opt-in via compose **profile** `security`; default `docker compose up -d` starts only nginxwebui + postgres.
- Container-side GeoIP refresh: [scripts/update-geoip-cf.sh](scripts/update-geoip-cf.sh) — downloads GeoLite2 Country/City/ASN mmdb + Cloudflare ips-v4/v6 into `/etc/nginx/geoip`（entrypoint 啟動跑一次 + crontab 每週三、六;7 天內已更新則跳過,避免每次 restart 重抓 ~80 MB）。

## Architecture Flow
A typical "user edits HTTP params" request crosses these layers:
```
Freemarker view (WEB-INF/view/adminPage/*.html)
   ↓ Layui submit / jQuery ajax
Controller — @Controller @Mapping  → routed by Solon, @Inject Service
   ↓
Service (@Component) — @Inject SqlHelper
   ↓
SqlHelper (home-grown ORM) — ConditionAndWrapper / Page / Sort → JDBC
   ↓
SQLite / PostgreSQL / MySQL
```

**Shortest path to add a CRUD page / 新增 CRUD 頁最短路徑:**
1. `model/Xxx.java` with `@Table`, boxed types (`Long`/`Boolean`), key from `BaseModel`.
2. `service/XxxService.java` — `@Component` + inject `SqlHelper`.
3. `controller/adminPage/XxxController.java` — `@Controller @Mapping("/adminPage/xxx")`.
4. View `WEB-INF/view/adminPage/xxx/index.html` + JS `static/js/adminPage/xxx/index.js`.
5. **Add i18n keys to all 3 `messages*.properties`.**
6. Add `tests/e2e/NN-xxx.spec.js` (next number).

## SqlHelper Cheatsheet
`SqlHelper` is [src/main/java/com/cym/sqlhelper/utils/SqlHelper.java](src/main/java/com/cym/sqlhelper/utils/SqlHelper.java) — not JPA, not MyBatis.

```java
@Component
public class HttpService {
    @Inject SqlHelper sqlHelper;

    List<Http> all = sqlHelper.findAll(new Sort("seq", Direction.ASC), Http.class);       // sorted findAll
    Http http = sqlHelper.findById(httpId, Http.class);                                    // by PK
    Http one  = sqlHelper.findOneByQuery(new ConditionAndWrapper().eq("name","x"), Http.class);
    List<Param> p = sqlHelper.findListByQuery(new ConditionAndWrapper().eq(Param::getTemplateId, id), Param.class);

    sqlHelper.insert(entity);          // insert (ID supplied externally)
    sqlHelper.updateById(entity);      // full-row update
    sqlHelper.insertOrUpdate(entity);  // empty ID → insert; else update
    sqlHelper.deleteById(id, Http.class);
}
```

**Gotchas / 踩雷點:**
- PK type is `String` but value comes from `SnowFlakeUtils.getId()` (Long → auto toString). 新增前可不指定 ID，交給 `insertOrUpdate`。
- `ConditionAndWrapper` chains are immutable; compose with `.and(...)` / `.or(...)`.
- No `@Repository`, no Mapper interface — **don't grep for DAO**; read the matching `XxxService.java`.
- Paging returns `Page<T>` (`records` / `total`) with `new Page<>(pageNum, pageSize)`.

## Dev Environment

| Tool | Version | Check |
|---|---|---|
| JDK | Java 17 (LTS) | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| Git | 2.30+ | `.gitattributes` forces LF cross-platform |
| Docker (optional) | 20.10+ | incl. Compose v2 |

> 注意：Java 17 是建置目標（2026-07-05 從 Java 8 升級，配合 maxmind-db 4.1.0 需 Java 16+）；CI 以 JDK 17 為準（build.yml）。跑 E2E 時 `spawn('java')` 走 PATH，需確保 PATH 的 java 是 17（否則 Java 8 跑 Java 17 jar 會 UnsupportedClassVersionError）。

```bash
git clone <repo-url> nginxWebUI && cd nginxWebUI
npm install && npx playwright install --with-deps chromium   # Node deps
mvn clean package -DskipTests                                # → target/nginxWebUI-<version>.jar
```

IDE: Main class `com.cym.NginxWebUI` · Program args `--server.port=8080 --project.home=./dev-home/` · JVM args `-Dfile.encoding=UTF-8`.

## Run & Deploy

**Minimal (SQLite, port 8080):**
```bash
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-<version>.jar --server.port=8080 --project.home=./dev-home/
```
First visit prompts to set the admin password.

**Useful launch flags / 常用啟動參數:**
- PostgreSQL: `--spring.database.type=postgresql --spring.datasource.url=... --spring.datasource.username=... --spring.datasource.password=...`
- Reset password: `--project.findPass=true` (prints password then exits)
- Test captcha: `--project.testCaptcha=1234` (CAPTCHA always accepts 1234 — for E2E)
- Skip wizard: `--init.admin=admin --init.pass=admin123 --init.api=true`
  > 注意：`--init.*` 只在 DB 還沒有任何管理員時生效。自 5.1.0 起 compose 的 `BOOT_OPTIONS` 不再內建 `--init.admin/pass`（首次走 UI 引導）。

**Docker Compose (recommended)** — run from `docker/`. Deploy needs `docker-compose.yml` + `.env` (crowdsec config is baked into its self-built image, no bind-mount needed). Default `up -d` = nginxwebui + postgres only; add `--profile security` for CrowdSec IDS:
```bash
cd docker
docker compose pull && docker compose up -d     # pull release images (:latest = newest tag)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build   # build nginxwebui from source
docker compose ps                                # all healthy
```

**Stack** (from [docker/docker-compose.yml](docker/docker-compose.yml)): **always on** — nginxwebui (host **12300**→8080, 80, 443) · postgres:18-alpine. **Optional via profile** — `security`: crowdsec · crowdsec-bouncer. CrowdSec = self-built `nginxwebui-crowdsec`（官方 base + 烤 config）。
> 注意：crowdsec config（acquis/profiles/abuseipdb）已烤進自建 image（`docker/crowdsec/Dockerfile`），升版跟著 image 走;runtime secret 仍走 `.env`。
> 注意：Loki / Promtail / Grafana monitoring profile 已於 2026-06-30 從本專案移除。若 server 上還有 `nginxwebui_loki_data` / `nginxwebui_grafana_data` volume 是歷史遺留，可手動 `docker volume rm` 清理。

## Release Flow (see docs/superpowers/plans/2026-05-21-dev-release-workflow.md)
**Branches:** `dev` (常駐日常開發) · `master` (**push 觸發發版**;CI 版本閘控:pom 版本在 ghcr 沒有才 build+push;自動打 `v*` tag + 建 GitHub Release) · `hotfix/*` (從 `master` 開).

**Primary path — bump on `dev`, push straight to `master` (no PR):**
```bash
git checkout dev && git pull origin dev
scripts/release.sh 5.2.6        # 只在 dev 或 hotfix/* 能跑(script 有分支閘);只改 pom 的 nginxWebUI <version> + commit,不打 tag、不 push
git push origin dev             # 同步 origin/dev
git push origin dev:master      # 觸發 CI:build+push 2 images (amd64) + auto-tag v5.2.6 + GitHub Release(--generate-notes)
docker manifest inspect ghcr.io/elf-express/nginxwebui:5.2.6   # 確認 image pushed
```
> 注意：**不要開 dev → master 的 PR** — GitHub merge 後的「Delete branch」會刪掉常駐 `dev`。直接 push `dev:master` 不走 PR、不刪 dev(代價是沒有 claude-code-review 自動審)。

**Optional PR variant(要 claude-code-review 保險時):** 先在 `dev` 跑 `scripts/release.sh`(它的分支閘不接受 `release/*`),再 `git checkout -b release/5.2.6` 推上去開 release/5.2.6 → master PR;merge 時 GitHub 刪的是 release 分支,`dev` 不動,merge 後 `git checkout dev && git pull` 即已同步(bump commit 本來就在 dev)。
> `release.sh` 只改 pom.xml,不碰 README/README_TW/CLAUDE/.env — 部署文件刻意「不綁版本」(`:latest` + `master` raw URL + jar 萬用字元)。Hotfix:從 `master` 開 `hotfix/*`,同樣 `scripts/release.sh x.y.z` 後 `git push origin hotfix/xxx:master`。

## Feature Inventory
**UI/UX:** batch param input · TLS default fix · conf indent + CodeMirror highlight · login password toggle · default http params/templates · HTTP param grouping (`HttpController.GROUP_DEFS`) · template grouping · IP/DenyAllow tag-ization · edit mode · conf error diagnosis · lang switch (flag SVG) · brand logo upload + header 200×60 align · HTTP param panel phase 2/3: tri-state enable mode + nginx module-availability filter (specs 28–31;5.2.7 起面板移至 http 參數配置頁 — 全域設定歸全域頁,server 精靈只留逐站步驟①Location ②server 參數).
**Accessibility (Wave 1/2 audit, ongoing):** site-wide pseudo-link `<a href="javascript:">` → `<button>` migration (header, sidebar, table actions, modals, captcha) · semantic landmarks (`<nav>` sidebar, `<h1>` on key pages) · icon button `aria-label`. Specs 27-a11y-buttons + crawler-style assertions guard this.
**Security:** CrowdSec (IDS + bouncer) · GeoIP2 country block · ASN block · Protection Cert · Real-IP module · **DenyAllow black/white lists — 全站自動生效** (`type` deny/allow, `@InitValue("deny")`; seeded 6 malicious-IP feed rules + daily URL refresh + async first-fetch; rules auto-apply at http/stream level via `ConfService.buildDenyAllow` — allow before deny, default allow, **no per-server binding**(舊綁定 UI 已於 5.2.6 移除,Settings/欄位保留不讀); cross-type IP conflict rejected on save) · **firewall page = 6 tabs** (IP database / 黑名單 / 白名單 / GeoIP country / ASN / Protection Cert).
**GeoIP DB module (v5.2.0+):** header shows Country/City/ASN/Cloudflare status in a 2×2 grid（4 列直排會撐破 60px header） (`GeoipService` via maxmind-db; build-date cache keyed by file mtime — 避免每 request 重讀 ~80MB mmdb) · ProtectionCert Tab-1 IP-database table (version / schedule / manual download / status cross-verify: `GeoipService.evaluateStatus` + `reverifyAll` + per-file stat/status fields) · **Cloudflare Real-IP auto-download** (`/adminPage/geoip/downloadCloudflare` → `realip.conf`, Cloudflare status row in the same table) · `GeoipController` `/adminPage/geoip/{versions,download,downloadCloudflare,…}` · Java/Hutool download (jar + Docker).
**Monitoring/Ops:** nginx module auto-detect (`/adminPage/monitor/nginxInfo`) · Site Resource · connectivity test.
**Deploy/Test:** test captcha · Compose stack (PG18 + CrowdSec) · **CrowdSec = self-built `nginxwebui-crowdsec` (official base + baked config)** · optional `security` profile · **master-triggered release: CI version-gated builds 2 images (nginxwebui + nginxwebui-crowdsec, amd64) + auto-tag + auto GitHub Release** · **geoip MMDB baked at build (offline-ready)** · `.gitattributes` LF · Playwright E2E suite (offline-CDN guard + a11y crawler) · `@claude` mention responder ([.github/workflows/claude.yml](.github/workflows/claude.yml)) · **save-path hardening (5.2.6):** `nginx -t` precheck 15s timeout + 無法執行/逾時→SKIPPED 不回滾(修死鎖) · realip.conf 啟動 placeholder · ORM 綁定正規化(Boolean→'1'/'0' + 啟動 migration) + DML SQLException 不再靜默.

## Docs
- **README:** `README.md`=英文(主) · `README_TW.md`=繁中;語言切換連結雙向,改內容須同步兩版。
- [Improvement plans & reports](docs/superpowers/plans/)
- [Playwright guide](docs/superpowers/plans/playwright-guide.md) · [Docker guide](docs/superpowers/plans/docker-guide.md) · [Docker standard](docs/superpowers/plans/docker-standard.md)
- [Dev/release workflow](docs/superpowers/plans/2026-05-21-dev-release-workflow.md)

## Quick Commands
```bash
mvn clean package -DskipTests                 # build
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-<version>.jar --server.port=8080   # run
npm test            # E2E (headed)            #   npm run test:fast (headless)
npm run report      # test report (port 9400)
cd docker && docker compose up -d --build     # docker build+run
codegraph explore "<question or symbol>"      # 1-call code lookup (prefer over grep/find)
```

## app.yml Key Params
```yaml
project: { home: /home/nginxWebUI/, findPass: false }   # home: data dir (db/log/cert)
spring:
  database: { type: sqlite }                              # sqlite / postgresql / mysql
  datasource: { url: , username: , password: }            # PG/MySQL JDBC
init: { admin: , pass: , api: }                           # empty → UI wizard
```
