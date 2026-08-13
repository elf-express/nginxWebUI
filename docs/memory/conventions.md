# Conventions / 專案慣例

> 從 [CLAUDE.md](../../CLAUDE.md) 進來的。核心六原則留在 CLAUDE.md，這裡是各層的細則。

## Frontend

- Use Layui components; refresh `select` / `checkbox` with `form.render()`.
- JS lives per page (e.g. `static/js/adminPage/server/index.js`); reachable at URL `/js/...` (not `/static/js/...`).
- **i18n key convention:** `<page>Str.<field>`（例如 `serverStr.add`、`geoipStr.download`）。Controller 注入 `MessageUtils m`；template 用 `${serverStr.xxx}`。
- JS i18n globals（`commonStr`、`geoipStr` 等）在 [common.html](../../src/main/resources/WEB-INF/view/adminPage/common.html) 由 `messageHeaders` 自動產生 —— 新前綴只要加進 properties 就會自動出現。

> 注意：新增任何使用者可見字串，必須同步改三份 properties：`messages.properties`（簡）、
> `messages_zh_TW.properties`（繁）、`messages_en_US.properties`（英）。CJK 值用 `\uXXXX` escape（檔案是 ISO-8859-1）。

## Backend

- Controllers 分兩處：`controller/adminPage/`（頁面控制器，含 CrowdSec / Geo / Asn / ProtectionCert / SiteResource / Geoip）與 `controller/api/`（REST API：basic/cert/denyAllow/nginx/param/password/server/upstream/www 的 `*Api` 加上 `Token` / `Upload`）—— `controller/` 根目錄不放東西。
- Services：`@Component` + `@Inject SqlHelper sqlHelper;`。持久化一律走 `SqlHelper`（自寫 ORM，不是 JPA —— 見 [stack-and-layout.md](stack-and-layout.md) 的 cheatsheet）。
- 主鍵：一律 `SnowFlakeUtils.getId()`（snowflake；存成 String，產生時是 Long）。
- 初始化邏輯在 `InitConfig.java`；執行期設定走 `app.yml` 或啟動參數。
- **Seed-on-empty pattern：** 這個 fork 會帶合理預設，使用者不必從零開始 —— 例如 `InitConfig.start()` 在表為空時透過 `DenyAllowService.defaultBlocklistRules()` 種入 6 條惡意 IP 黑名單（由 `denyAllowSeeded` setting flag 把關；非同步首次抓取填 IP）。任何「空 DB ≈ 壞掉的體驗」的新功能都套用同一模式。

## Testing（詳見 [docs/superpowers/plans/playwright-guide.md](../superpowers/plans/playwright-guide.md)）

- Specs 在 `tests/e2e/`，編號 `01-login` … `35-mcp`（連號）外加獨立的 `flag-svg-integrity`。新功能 → 下一個編號。
- `35-mcp` 是唯一自己另起 server 的 spec（port 18081 + `--mcp.token`，資料落在已被 gitignore 的 `test-data/mcp/`）。共用實例（18080）沒帶 token，所以它同時守得住「未啟用時 404」。
- **PG smoke：** `npm run test:pg` —— docker 起 postgres:18-alpine（port 15432），跑 01+33 驗證 PostgreSQL 上的登入與 server 儲存（主套件只跑 SQLite，跨 DB 行為差異靠這層抓）。
- 簡/繁按鈕文字用 regex 比對：`/批量輸入|批量输入/`。
- Layui 元件用 `page.evaluate()` 驅動。
- 執行：`npm test`（headed）· `npm run test:fast`（headless/CI）· `npx playwright test tests/e2e/08-crowdsec.spec.js`（單檔）· `npm run report`（http://localhost:9400）。

> 注意：測試會自動啟動獨立 server（port 18080）+ 獨立 SQLite，不碰 `./dev-home/`。
> `tests/e2e/helpers.js` 動態解析 `target/nginxWebUI-*.jar`，所以跑測試前要先 `mvn package`。
> 推論：跑過 `mvn clean` 之後 jar 會消失，Playwright 會起不來 —— 先 `mvn -o package -DskipTests` 再跑。

## Docker（詳見 [docs/superpowers/plans/docker-guide.md](../superpowers/plans/docker-guide.md) —— 部分已被取代）

- container_name：扁平命名 `nginxwebui`（app）/ `nginxwebui-<service>`（sidecar）—— 自 5.1.0 起不帶版本後綴。
- volume 命名：`nginxwebui_{purpose}_data`（明寫 `name:` 以避開 compose 專案前綴）。
- healthcheck + 啟動順序是必要的；`entrypoint.sh` 必須是 LF（`.gitattributes` 強制）。
- **兩個自建 image：** `nginxwebui`（根目錄 Dockerfile）+ `nginxwebui-crowdsec`（`docker/crowdsec/Dockerfile` = 官方 crowdsec base + 烤進設定）。CrowdSec 透過 compose **profile** `security` opt-in；預設 `docker compose up -d` 只起 nginxwebui + postgres。
- 容器側 GeoIP 更新：[scripts/update-geoip-cf.sh](../../scripts/update-geoip-cf.sh) —— 下載 GeoLite2 Country/City/ASN mmdb + Cloudflare ips-v4/v6 到 `/etc/nginx/geoip`（entrypoint 啟動跑一次 + crontab 每週三、六；7 天內已更新則跳過，避免每次 restart 重抓 ~80 MB）。
- **nginx modules（精簡集，~31 個 `.so`）：** 根目錄 [Dockerfile](../../Dockerfile) 安裝一組精選的 `nginx-mod-*`。**不要**把這些沒人維護／高風險的套件加回去：`upstream_fair`、舊版 `geoip`/`stream_geoip`（`.dat`）、`perl`、`upload`/`uploadprogress`、`zip`/`untar`/`slowfs`、`echo`、`dav_ext`、`fancyindex`、`xslt`、`shibboleth`、`log_zmq`、`accounting`、`redis2`。
- **`load_module` 順序**由 `NginxService.MODULE_CATALOG` 決定（NDK→Lua 生態 → stream/mail/rtmp → geoip2 → js/keyval → compress → filters → dynamic upstream → 功能模組）。`getEnabledModulePaths()` 依 **catalog 順序**而非 DB `seq`（避免相依性斷裂）。Migration `moduleCatalogHardened20260812` 清掉過時的模組列並重新排序。
- **不要在 image 裡裸跑 `nginx -t`**（不帶 `-c`）—— Alpine 的 `/etc/nginx/nginx.conf` 會自動 include 套件設定，而其 lua/lua_upstream 順序是壞的。要測 UI 產生的設定請用：`nginx -t -c /home/nginxWebUI/temp/nginx.conf -p /home/nginxWebUI/temp/`。

## Parameter templates / 參數模板（`Template.def`）

- **多選自動套用的 context** 存在 `Template.def`，格式是小寫逗號分隔的標籤（由 `TemplateDefUtils` 正規化）。
- **UI：** [adminPage/template](../../src/main/resources/WEB-INF/view/adminPage/template/index.html) 上的標籤晶片 —— 人類標籤 + 灰色內部代碼。
- **鍵值對照（內部 → 意義）：**

  | key | meaning |
  |-----|---------|
  | `http` | 全域 `http { }`（zones、map、log_format、geoip2…）—— 由 `ConfService` 注入 |
  | `server` | 每個 **HTTP** `server { }`（站台／反向代理）—— `ParamService` |
  | `server1` | 每個 **TCP** stream `server { }`（L4，無 location）—— `ParamService` |
  | `server2` | 每個 **UDP** stream `server { }` —— `ParamService` |
  | `stream` | 全域 `stream { }`（例如 `limit_conn_zone`）—— `ConfService` |
  | `location` | 每個 `location { }` —— `ParamService` |
  | `upstream` | 每個 HTTP `upstream { }` —— `ParamService` |

- `def` 為空 = 只能透過「選擇參數模板」手動套用。
- **標籤鎖（read-only disabled）：** `TemplateDefUtils.allowedContexts(params)` 是**唯一真實來源**。UI 呼叫 `POST /adminPage/template/allowedDefs`（debounced）把非法標籤變灰／`disabled`；**存檔路徑**跑 `normalizeAndFilter`。不要在 JS 裡再複製一份 HTTP_ONLY 清單。
- **stream 安全性：** ConfService 自動注入 `stream{}` 時會略過 HTTP-only 指令（`if`、`add_header`…）；並在 `limit_conn` 之前先輸出 `limit_conn_zone`。Migration `streamDefTemplatesSanitized20260812` 清掉 GeoIP／`if` 模板上誤設的 `def=stream`；只有 Connection Limit（stream 層）保留 `def=stream`，stream-server 的限制改用 `server1`。
- **不要**把 HTTP 的 `if` / `$request` log_format 放進 stream 模板。見 [docs/nginx結構.md](../nginx結構.md)。
