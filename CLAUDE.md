# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

> 🌐 本檔以英文為主、關鍵處附中文註解。新增使用者可見字串仍須同步三份 i18n（見下）。

## 📖 Overview

nginxWebUI is a web tool that simplifies NGINX configuration — users fill in UI forms instead of hand-writing `nginx.conf` (reverse proxy, SSL, load balancing, security hardening).

Entry point: [com.cym.NginxWebUI](src/main/java/com/cym/NginxWebUI.java) — `@SolonMain` + `@EnableScheduling`.

> ⚠️ 啟動時會先殺掉同名舊 jar process 再 `Solon.start()`。

## 🧱 Tech Stack

*   **Backend:** Java 8 + [Solon 3.3.3](https://solon.noear.org/) — **NOT Spring Boot**
    *   DI: `@Component` (services) / `@Controller` (controllers) / `@Inject` (not `@Service` / `@Autowired`)
    *   Routing: `@Mapping("/path")` on both class and method level
    *   Scheduling: `@Scheduled` from `solon-scheduling-simple`
*   **Frontend:** Layui + jQuery + Freemarker (server-side rendered — **not an SPA**)
*   **DB:** SQLite (default) / PostgreSQL / MySQL — switch via `--spring.database.type`
*   **GeoIP:** `com.maxmind.db:maxmind-db` **2.1.0** reads MMDB `build_epoch` for the version badge.

> ⚠️ 2.1.0 是最後支援 Java 8 的版本（3.x 需 Java 11），**勿升級**。

*   **Build:** Maven → `target/nginxWebUI-<version>.jar` (fat jar, `jar-with-dependencies`)
*   **Tests:** Playwright E2E (**no JUnit for end-to-end**)
*   **Containers:** Docker Compose stack (PostgreSQL + Loki + Grafana + CrowdSec). Compose & sidecar baked images both live in [docker/](docker/).

## 📁 Directory Structure

```
src/main/java/com/cym/
├── config/         # init, filters, config (InitConfig, AppFilter)
├── controller/     # controllers (feature-split under adminPage/)
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

## 📐 Conventions

### Core principles / 核心原則

1.  **Don't break existing business logic** — changes are UI polish or additive features only.
2.  **Multilingual** — every new user-facing string updates all 3 `messages*.properties`.
3.  **Automated tests** — every new/changed feature ships a Playwright test.
4.  **Zero-risk first** — prefer pure-frontend / purely-additive changes.

### 🎨 Frontend

*   Use Layui components; refresh `select` / `checkbox` with `form.render()`.
*   JS lives per page (e.g. `static/js/adminPage/server/index.js`); reachable at URL `/js/...` (not `/static/js/...`).
*   **i18n key convention:** `<page>Str.<field>` (e.g. `serverStr.add`, `geoipStr.download`). Controller injects `MessageUtils m`; template uses `${serverStr.xxx}`.
*   JS i18n globals (e.g. `commonStr`, `geoipStr`) are auto-generated in [common.html](src/main/resources/WEB-INF/view/adminPage/common.html) from `messageHeaders` — a new prefix appears automatically once added to properties.

> ⚠️ **新增任何使用者可見字串，必須同步改三份 properties**：`messages.properties`（簡）、`messages_zh_TW.properties`（繁）、`messages_en_US.properties`（英）。CJK 值用 `\uXXXX` escape（檔案是 ISO-8859-1）。

### ⚙️ Backend

*   Controllers go under `controller/adminPage/` (currently **29**, incl. CrowdSec / Geo / Asn / ProtectionCert / SiteResource / **Geoip**).
*   Services: `@Component` + `@Inject SqlHelper sqlHelper;`. Persistence via `SqlHelper` (home-grown ORM, not JPA — see cheatsheet).
*   Primary keys: always `SnowFlakeUtils.getId()` (snowflake; stored as String, generated as Long).
*   Init logic in `InitConfig.java`; runtime config via `app.yml` or launch args.

### 🧪 Testing (see docs/superpowers/plans/playwright-guide.md)

*   Specs in `tests/e2e/` — **24** files (`01-login` … `23-geoip-version` + `flag-svg-integrity`).
*   Match 簡/繁 button text with regex: `/批量輸入|批量输入/`.
*   Drive Layui widgets via `page.evaluate()`.
*   Run: `npm test` (headed) · `npm run test:fast` (headless/CI) · `npx playwright test tests/e2e/08-crowdsec.spec.js` (one file) · `npm run report` (http://localhost:9400).

> ⚠️ 測試會自動啟動獨立 server（port 18080）+ 獨立 SQLite，**不碰** `./dev-home/`。`tests/e2e/helpers.js` 動態解析 `target/nginxWebUI-*.jar`，所以\*\*跑測試前要先 `mvn package`\*\*。

### 🐳 Docker (see docs/superpowers/plans/docker-guide.md)

*   container\_name: flat `nginxwebui` (app) / `nginxwebui-<service>` (sidecar) — no version suffix since 5.1.0.
*   volume name: `nginxwebui_{purpose}_data` (explicit `name:` to dodge compose project prefix).
*   healthcheck + startup order required; `entrypoint.sh` must be LF (`.gitattributes` enforces).
*   Sidecars (grafana / promtail / crowdsec) use baked images `ghcr.io/elf-express/nginxwebui-<service>:<version>` — config baked in, **no bind-mount config needed** on deploy.

## 🔁 Architecture Flow

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

1.  `model/Xxx.java` with `@Table`, boxed types (`Long`/`Boolean`), key from `BaseModel`.
2.  `service/XxxService.java` — `@Component` + inject `SqlHelper`.
3.  `controller/adminPage/XxxController.java` — `@Controller @Mapping("/adminPage/xxx")`.
4.  View `WEB-INF/view/adminPage/xxx/index.html` + JS `static/js/adminPage/xxx/index.js`.
5.  **Add i18n keys to all 3** `**messages*.properties**`**.**
6.  Add `tests/e2e/NN-xxx.spec.js` (next number).

## 🗃️ SqlHelper Cheatsheet

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

*   PK type is `String` but value comes from `SnowFlakeUtils.getId()` (Long → auto toString). 新增前可不指定 ID，交給 `insertOrUpdate`。
*   `ConditionAndWrapper` chains are immutable; compose with `.and(...)` / `.or(...)`.
*   No `@Repository`, no Mapper interface — **don't grep for DAO**; read the matching `XxxService.java`.
*   Paging returns `Page<T>` (`records` / `total`) with `new Page<>(pageNum, pageSize)`.

## 🧰 Dev Environment

| Tool | Version | Check |
| --- | --- | --- |
| JDK | Java 8 (1.8) | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| Git | 2.30+ | `.gitattributes` forces LF cross-platform |
| Docker (optional) | 20.10+ | incl. Compose v2 |

> ⚠️ Java 8 是建置目標。若本機只有較新 JDK，可用它編譯（`-source/-target 1.8`）；CI 用 JDK 8 為準。

```
git clone <repo-url> nginxWebUI && cd nginxWebUI
npm install && npx playwright install --with-deps chromium   # Node deps
mvn clean package -DskipTests                                # → target/nginxWebUI-<version>.jar
```

IDE: Main class `com.cym.NginxWebUI` · Program args `--server.port=8080 --project.home=./dev-home/` · JVM args `-Dfile.encoding=UTF-8`.

## 🚀 Run & Deploy

**Minimal (SQLite, port 8080):**

```
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-<version>.jar --server.port=8080 --project.home=./dev-home/
```

First visit prompts to set the admin password.

**Useful launch flags / 常用啟動參數:**

*   PostgreSQL: `--spring.database.type=postgresql --spring.datasource.url=... --spring.datasource.username=... --spring.datasource.password=...`
*   Reset password: `--project.findPass=true` (prints password then exits)
*   Test captcha: `--project.testCaptcha=1234` (CAPTCHA always accepts 1234 — for E2E)
*   Skip wizard: `--init.admin=admin --init.pass=admin123 --init.api=true`

> ⚠️ `--init.*` 只在 DB 還沒有任何管理員時生效。自 5.1.0 起 compose 的 `BOOT_OPTIONS` **不再內建** `--init.admin/pass`（首次走 UI 引導）。

**Docker Compose (recommended)** — run from `docker/`. Deploy host only needs `docker-compose.yml` + `.env` (sidecar config is baked into images):

```
cd docker
docker compose pull && docker compose up -d     # pull release images (:latest = newest tag)
docker compose up -d --build                     # build from source (runs ../Dockerfile + each sidecar Dockerfile)
docker compose ps                                # all healthy
```

**Stack** (from [docker/docker-compose.yml](docker/docker-compose.yml)): nginxwebui (host **12300**→8080, 80, 443) · postgres:18-alpine · loki · grafana (baked, 3000) · promtail (baked) · crowdsec (baked) · crowdsec-bouncer.

> ⚠️ crowdsec config 走 named volume，首次從 baked image seed；之後改 baked config 要 `docker compose down -v` 才重新 seed。

## 🏷️ Release Flow (see docs/superpowers/plans/2026-05-21-dev-release-workflow.md)

**Branches:** `dev` (daily dev + release actions) · `master` (snapshot pointer to last release; never commit/tag here directly) · `tag v*` (cut by `scripts/release.sh`; CI builds image only on tags).

```
git checkout dev && git pull origin dev
scripts/release.sh 5.2.1            # bumps pom.xml + commit + tag v5.2.1 (only touches pom.xml)
git push origin dev --tags          # CI matrix-builds 4 images → ghcr.io :5.2.1 + :latest
docker manifest inspect ghcr.io/elf-express/nginxwebui:5.2.1   # confirm pushed
git push origin dev:master          # fast-forward master
gh release create v5.2.1 ...        # GitHub Release entry
```

> ⚠️ `release.sh` **只改 pom.xml**，不碰 README/CLAUDE/.env — 所以部署文件刻意採「不綁版本」寫法（`:latest` + `master` raw URL + jar 萬用字元）避免每次 release 變舊。Hotfix 從 `master` 開 `hotfix/*` 分支（script 允許）。

## 📦 Feature Inventory

**UI/UX:** batch param input · TLS default fix · conf indent + CodeMirror highlight · login password toggle · default http params/templates · HTTP param grouping (`HttpController.GROUP_DEFS`) · template grouping · IP/DenyAllow tag-ization · edit mode · conf error diagnosis · lang switch (flag SVG) · brand logo upload + header 200×60 align.  
**Security:** CrowdSec (IDS + bouncer) · GeoIP2 country block · ASN block · Protection Cert · Real-IP module.  
**🆕 GeoIP DB module (v5.2.0):** header shows Country/City/ASN MMDB build dates (`GeoipService` via maxmind-db) · ProtectionCert Tab-1 GeoIP table (version / schedule / manual download) · `GeoipController` `/adminPage/geoip/{versions,download}` · Java/Hutool download (jar + Docker).  
**Monitoring/Ops:** nginx module auto-detect (`/adminPage/monitor/nginxInfo`) · Site Resource · connectivity test.  
**Deploy/Test:** test captcha · Compose stack (PG18 + Loki + Grafana + CrowdSec) · sidecar baked images · CI matrix-build 4 images · `.gitattributes` LF · Playwright suite (24 specs).

## 📚 Docs

*   [Improvement plans & reports](docs/superpowers/plans/)
*   [Playwright guide](docs/superpowers/plans/playwright-guide.md) · [Docker guide](docs/superpowers/plans/docker-guide.md) · [Docker standard](docs/superpowers/plans/docker-standard.md)
*   [Dev/release workflow](docs/superpowers/plans/2026-05-21-dev-release-workflow.md)

## ⚡ Quick Commands

```
mvn clean package -DskipTests                 # build
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-<version>.jar --server.port=8080   # run
npm test            # E2E (headed)            #   npm run test:fast (headless)
npm run report      # test report (port 9400)
cd docker && docker compose up -d --build     # docker build+run
```

## ⚙️ app.yml Key Params

```
project: { home: /home/nginxWebUI/, findPass: false }   # home: data dir (db/log/cert)
spring:
  database: { type: sqlite }                              # sqlite / postgresql / mysql
  datasource: { url: , username: , password: }            # PG/MySQL JDBC
init: { admin: , pass: , api: }                           # empty → UI wizard
```