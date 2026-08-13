# Stack, layout & data access / 技術棧、目錄與資料存取

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。

## Tech Stack 細節

- **Backend:** Java 17 (LTS) + [Solon 3.10.7](https://solon.noear.org/) —— **不是 Spring Boot**
  - DI：`@Component`（services）/ `@Controller`（controllers）/ `@Inject`（不是 `@Service` / `@Autowired`）
  - Routing：`@Mapping("/path")` 類別層與方法層都要
  - Scheduling：`solon-scheduling-simple` 的 `@Scheduled`
- **Frontend:** Layui + jQuery + Freemarker（伺服器端渲染 —— **不是 SPA**）
- **DB:** SQLite（預設）/ PostgreSQL / MySQL —— 用 `--spring.database.type` 切換
- **GeoIP:** `com.maxmind.db:maxmind-db` **4.1.0** 讀 MMDB 的 `build_epoch` 作為版本徽章。
  > 注意：4.1.0 起 `Metadata` 改為 Java **record**（需 Java 16+），`getBuildDate()` 已移除 → 改用 `buildTime()`（Instant）。此版由 dependabot 於 2026-07-05 升級（同批帶動 Java 8→17 地基升級）；讀取邏輯見 `GeoipService.readBuildDate`。
- **Build:** Maven → `target/nginxWebUI-<version>.jar`（fat jar，`jar-with-dependencies`）
- **Tests:** Playwright E2E（**端對端不用 JUnit**）+ JUnit 5（單元層，走 `solon-test`）
- **Containers:** Docker Compose stack（PostgreSQL + CrowdSec）。**兩個自建 image：`nginxwebui` + `nginxwebui-crowdsec`**（crowdsec = 官方 base + 從 [docker/crowdsec/](../../docker/crowdsec/) 烤進設定）。
  > 注意：Loki + Promtail + Grafana 已於 2026-06-30 從本專案移除 —— nginx 內建的 access/error log 已足夠排查，CrowdSec 直接從共享的 `nginxwebui_log` volume 讀 nginx log，不需要 Loki 中介。

## Directory Structure

```
src/main/java/com/cym/
├── config/         # init, filters, config (InitConfig, AppFilter)
├── controller/     # adminPage/ (頁面控制器) + api/ (REST API)
├── ext/            # view DTOs (e.g. DenyAllowExt, GeoipDbInfo) — 非 @Table
├── mcp/            # NginxDocMcpServer — MCP 協定門面
├── model/          # data models (@Table entities: Server, Location, Http, ...)
├── service/        # business logic (@Component + @Inject SqlHelper)
├── sqlhelper/      # home-grown ORM
└── utils/          # utilities (NginxDocParser, NginxConfChecker, ...)

src/main/resources/
├── WEB-INF/view/adminPage/   # Freemarker templates (HTML)
├── static/js/adminPage/      # frontend JS (one dir per page)
├── static/lib/               # third-party libs (layui, codemirror...)
├── messages.properties       # i18n 简体中文
├── messages_zh_TW.properties # i18n 繁體中文
├── messages_en_US.properties # i18n English
└── app.yml                   # app config

docs/nginxdocumentation/      # 150 頁 nginx 官方文件 (打包進 jar，MCP 索引來源)
docs/memory/                  # 本目錄：CLAUDE.md 的延伸記憶
tests/e2e/                    # Playwright specs
```

## Architecture Flow

一個典型的「使用者編輯 HTTP 參數」請求會穿過這幾層：

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

**新增 CRUD 頁最短路徑：**

1. `model/Xxx.java`，加 `@Table`、用包裝型別（`Long`/`Boolean`）、主鍵來自 `BaseModel`。
2. `service/XxxService.java` —— `@Component` + 注入 `SqlHelper`。
3. `controller/adminPage/XxxController.java` —— `@Controller @Mapping("/adminPage/xxx")`。
4. View `WEB-INF/view/adminPage/xxx/index.html` + JS `static/js/adminPage/xxx/index.js`。
5. **三份 `messages*.properties` 都要加 i18n key。**
6. 加 `tests/e2e/NN-xxx.spec.js`（下一個編號）。

## SqlHelper Cheatsheet

`SqlHelper` 在 [src/main/java/com/cym/sqlhelper/utils/SqlHelper.java](../../src/main/java/com/cym/sqlhelper/utils/SqlHelper.java) —— 不是 JPA，也不是 MyBatis。

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

**踩雷點：**

- PK 型別是 `String`，但值來自 `SnowFlakeUtils.getId()`（Long → 自動 toString）。新增前可不指定 ID，交給 `insertOrUpdate`。
- `ConditionAndWrapper` 的鏈是不可變的；用 `.and(...)` / `.or(...)` 組合。
- 沒有 `@Repository`、沒有 Mapper interface —— **不要去 grep DAO**；直接讀對應的 `XxxService.java`。
- 分頁回傳 `Page<T>`（`records` / `total`），用 `new Page<>(pageNum, pageSize)`。
- `@InitValue` 的啟動 DDL 會先於 `InitConfig` 的 migration 填入預設值 —— 所以「用欄位是否為空」判斷是否已遷移的 gate 會被架空，改用 setting flag 當單一閘門。
