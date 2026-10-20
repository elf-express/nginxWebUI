# nginxWebUI · elf-express fork

> 圖形化管理 nginx 設定的網頁工具 — 整合 GeoIP / CrowdSec 的安全強化版

**English** · [README.md](./README.md)　**繁體中文** · 本檔

---

## 這是什麼

[nginxWebUI](https://github.com/cym1102/nginxWebUI) 是 [cym1102](https://gitee.com/cym1102) 開源的 nginx 圖形化設定工具。原版功能完整、但設定**全在單一容器內、無觀測性、無安全強化、UI 偏舊**。

**這個 fork ([elf-express/nginxWebUI](https://github.com/elf-express/nginxWebUI)) 把它演進成 production-grade 部署：**

| 維度 | 上游 cym1102 | elf-express fork |
|---|---|---|
| **資料庫** | SQLite（單檔） | **PostgreSQL 18-alpine**（多容器、可備援） |
| **安全防護** | 純 IP 黑白名單 | + **CrowdSec** 入侵偵測、+ **GeoIP2** 國家封鎖、+ **ASN** 封鎖、+ **多 list 自動 fetch** |
| **語系** | 簡中為主 | **繁中為主**（簡 / 繁 / 英三份）|
| **前端** | 純 Layui + jQuery | + **Vue 3 局部 mount**（template picker / **Vue Dashboard**）|
| **CI/Release** | 手動打 jar | **GitHub Actions** 人工 `v*` tag → 驗證＋測試 → 版本閘控 build image (linux/amd64) push 到 ghcr.io |
| **開發流程** | 原作者的開發流程 | **dev → release → main**、版號 PR、人在 release 建 annotated tag；`scripts/release.sh` 改版號＋commit |

> 不取代上游，**互補**：你要極輕量單機部署用上游；要企業級觀測性與安全強化用這個 fork。

---

## 快速開始

### 用 Docker Compose 一鍵啟動完整 stack

```bash
git clone --branch release https://github.com/elf-express/nginxWebUI.git
cd nginxWebUI/docker          # release 是穩定版與部署來源
docker compose up -d          # image 預設拉 :latest，永遠跟最新 release
```

打開瀏覽器 → **http://localhost:12300** → 首次啟動依畫面精靈設定管理員帳密（不再內建預設密碼）

預設只起核心兩個 service；CrowdSec 透過 compose `security` profile 視需要開啟：

| Service | Port | 用途 | 預設 |
|---|---|---|---|
| **nginxwebui** | 12300:8080 / 80 / 443 | 主應用 | ✅ |
| postgres | 5432 | 資料庫 | ✅ |
| crowdsec | —（僅內部網路）| 入侵偵測 + AppSec WAF（v1.8.1）；封鎖由 nginx 內的 Lua bouncer 執行 | profile `security` |

### Stack 架構

```
┌─ nginxwebui (Solon 3.10.7 + Java 17) ────────────────┐
│                                                       │
│  ┌─ Web UI (Layui + Vue 3 局部 mount) ─────────────┐ │
│  │  防護與憑證 / 反向代理 / Stream / Upstream / ...│ │
│  └──────────────────────────────────────────────────┘ │
│                       ↓ SqlHelper (自製 ORM)          │
│            PostgreSQL ← cert / server / denyAllow     │
│                       ↓ ConfService 生成             │
│            nginx.conf + 反向代理 + GeoIP/ASN block    │
└───────────────────────────────────────────────────────┘
            ↓ access / error log    ↑ nginx Lua bouncer 每個請求查詢
┌─ CrowdSec (LAPI :8080 + AppSec WAF :7422，僅內部網路) ┐
└───────────────────────────────────────────────────────┘
```

> 日誌排查直接看 nginx 內建 access/error log（`nginxwebui_log` volume，CrowdSec 也是從這裡讀）。不再使用 Loki / Promtail / Grafana。

---

## 主要功能

### 🛡 安全防護

- **IP 黑白名單清單** — 防護頁集中管理、**自動全站生效**（白名單優先於黑名單，零綁定操作）；出廠自帶 6 條惡意 IP feed 規則，**每日定時從 URL 自動抓**（Spamhaus DROP / Blocklist.de / Emerging Threats / CINS Army / Feodo Tracker / GreenSnow）
- **GeoIP2 國家封鎖** — 預設白名單 17 國（CN/JP/HK/KR/SG/TH/MY/TW/VN/GB/FR/DE/GR/CA/US/MO/LA），用戶可自訂
- **ASN 封鎖** — 按 Autonomous System Number 封整段網路
- **CrowdSec 整合** — 官方 nginx Lua bouncer 直接在 nginx 裡執行：被封鎖的 IP 直接斷線（444）、被 AppSec WAF 擋下的請求回 403、白名單 IP 跳過所有防護層（見 [CrowdSec 防護](#crowdsec-防護)）
- **防爬蟲憑證** — 「防護與憑證」頁面集中管理

### 🌐 反向代理 / 負載均衡

- HTTP / HTTPS / TCP / UDP 全支援、自動產生 `nginx.conf`
- TLS 1.2 / 1.3 支援、Let's Encrypt 自動續簽（acme.sh DNS 模式）
- 上游負載均衡（upstream）含 weight / backup / down 設定
- **nginx 區塊編輯器**支援 HTTP/TCP/UDP upstream 與 Stream 子區塊：貼上並驗證原始指令、預覽產生的設定，保留註解、排版與 nginx 變數；既有表單資料仍可照常使用，需明確轉換後才會改用區塊模式。
- **20 個內建參數模板**（含中文註解）：WebSocket Proxy / Proxy Headers / Large File Upload / CORS / Rate Limit / Security Headers / GeoIP / ASN

### 📊 觀測性

- nginx 內建 access log / error log（在 `nginxwebui_log` volume，jar 模式則在 `--project.home` 下 `log/`）
- 系統指標頁面（CPU / Mem / Disk / Net，OSHI 採集）
- CrowdSec cscli / decisions API 可查封鎖列表

> 此 fork 曾整合 Loki + Promtail + Grafana 完整 log pipeline，2026-06-30 移除；nginx 本身的 access/error log 已足夠日常排查。

### 🎨 UI

- 繁中為主、簡 / 英三語 i18n（國旗 icon 切換）
- 反向代理 modal 單欄向左對齊、不蓋 top header
- **shadcn-vue 風格** template picker（Vue 3 + 自製 Combobox）

### AI 助理整合

- **nginx 文件 MCP 服務** — 969 條官方指令定義以 MCP 提供：精準查詢、全文搜尋、context 反查、拿設定草稿裡的指令與區塊位置對照文件檢查 context。預設關閉，以 `--mcp.token` opt-in 啟用（見 [nginx 文件 MCP 服務](#nginx-文件-mcp-服務)）

### 🚀 開發流程

- **dev → release → main**：開發分支經 PR 進 dev，測試通過後快轉 release；main 只從 release 快轉（見 [BRANCHING.md](BRANCHING.md)）
- **`scripts/release.sh`** 在開發分支自動化 pom bump + commit；版號 PR 進 dev，tag 由人在 release 建立
- **GitHub Actions** 分支 push／PR 只測試；人工 `v*` tag 或手動指定既有 tag，驗證後才發布兩個 image 與 GitHub Release
- **Dependabot** 每週掃 Maven + Docker + Actions 依賴升級

---

## 部署選項

### A. Docker Compose（推薦、生產環境）

**兩個 image 都是自建**：`nginxwebui` 與 `nginxwebui-crowdsec`（官方 CrowdSec base、config 烤進 image — 不需 bind-mount）。預設只起核心兩個服務，IDS 用 compose `security` **profile** 視需要開啟。

**只跑核心（nginxwebui + postgres）—— 線上只需兩個檔：**

```bash
mkdir nginxwebui && cd nginxwebui
curl -O https://raw.githubusercontent.com/elf-express/nginxWebUI/release/docker/docker-compose.yml
curl -o .env https://raw.githubusercontent.com/elf-express/nginxWebUI/release/docker/.env.example
# 編輯 .env：image 預設 :latest，要釘版本就設 NGINX_WEBUI_VERSION=x.y.z
docker compose up -d                      # 只起 nginxwebui + postgres
```

**要加 CrowdSec IDS —— 加上 compose `security` profile 即可（config 已烤進 `nginxwebui-crowdsec` image，不需 bind-mount）：**

```bash
git clone --branch release https://github.com/elf-express/nginxWebUI.git && cd nginxWebUI/docker
cp .env.example .env
openssl rand -hex 32                       # 把輸出貼到 .env 的 CROWDSEC_BOUNCER_KEY=
docker compose --profile security up -d
# 或在 .env 設 COMPOSE_PROFILES=security 後直接 docker compose up -d
```

接著在網頁介面套用一次設定，nginx 才會載入 bouncer。金鑰、503 行為與升級注意事項見 [CrowdSec 防護](#crowdsec-防護)。

> 從原始碼自建 nginxwebui image：clone 後在 `docker/` 跑（先 `mvn clean package -DskipTests`）：
> `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build`

### B. 純 jar 部署（最小化、開發環境）

```bash
# 1. 編譯
mvn clean package -DskipTests

# 2. 啟動
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-*.jar \
     --server.port=8080 \
     --project.home=./dev-home/
```

啟動參數：

| 參數 | 預設 | 說明 |
|---|---|---|
| `--server.port` | 8080 | 監聽埠 |
| `--project.home` | `/home/nginxWebUI/` | 資料目錄（DB / cert / log）|
| `--spring.database.type` | sqlite | sqlite / postgresql / mysql |
| `--init.admin` | （空，網頁設定）| 初始 admin 名稱 |
| `--init.pass` | （空，網頁設定）| 初始 admin 密碼 |
| `--project.findPass` | false | true 印密碼後退出（救援用） |

完整參數見 [CLAUDE.md](./CLAUDE.md#app-yml-重要參數)。

### C. 直接拉 Docker image

```bash
docker pull ghcr.io/elf-express/nginxwebui:latest
# 或釘特定版本：ghcr.io/elf-express/nginxwebui:x.y.z（:latest 永遠等於最新 tag build）
```

Platform: linux/amd64（單一平台，非多架構）

---

## CrowdSec 防護

開啟 `security` profile 後，由 nginx 自己執行 CrowdSec 的判斷：`nginxwebui` image 內建 CrowdSec 官方的 nginx Lua
bouncer（`lua-cs-bouncer`，取自 cs-nginx-bouncer v1.2.3），在每個 HTTP 站台的 access 階段執行，不再有獨立的
bouncer 容器。`nginxwebui-crowdsec` image 已烤入日誌解析器與 AppSec WAF 規則（virtual patching 與 OWASP CRS），
啟動時不需要連 CrowdSec hub。LAPI（8080）與 AppSec（7422）只在 compose 網路內，不對外 publish。

每個請求依序檢查：

1. **白名單優先。** 「防護與憑證」頁白名單裡的 IP／CIDR 直接放行，不再做任何檢查：不受國家封鎖、CrowdSec
   封鎖、WAF 與 503 影響。
2. **CrowdSec 連得到嗎？** CrowdSec 沒回應超過寬限秒數（預設 120 秒，從 nginx 啟動起算）時，請求會收到 503
   「防護暫停」頁，附 `Retry-After: 30`。防護失效時網站維持關閉，不會變成不設防（fail-closed）。nginx 每 5 秒探測
   一次 CrowdSec，結果寫在 `<home>/crowdsec/state/crowdsec_state.json`。
3. **IP 封鎖。** 有 ban decision 的 IP 直接斷線、不回應（nginx 444）。
4. **AppSec WAF。** 符合 virtual patching 規則的請求回 403（例如 `GET /.env`）。OWASP CRS 規則以 out-of-band 執行：
   只產生告警，同一個 IP 一再觸發才會被加上 ban decision。

只保護 HTTP 站台；stream（TCP／UDP）不受 CrowdSec 保護。

**bouncer 金鑰**

- 用 `openssl rand -hex 32` 產生，填進 `docker/.env` 的 `CROWDSEC_BOUNCER_KEY`。crowdsec 容器第一次啟動時會用這組
  金鑰自動註冊名為 `nginx` 的 bouncer，不必再執行 `cscli bouncers add`。
- nginxWebUI 看不到 compose profile，只看有沒有金鑰：有金鑰就產生 bouncer 設定，沒有就完全不產生。
  **只有啟用 `security` profile 時才填金鑰。** 填了金鑰但 CrowdSec 沒起來，寬限秒數一過，所有非白名單的請求都會收到
  503 頁。
- 金鑰不存進資料庫，也不寫進 nginx.conf，只寫在 `<home>/crowdsec/crowdsec-nginx-bouncer.conf`（權限 600）。
- nginx.conf 是在套用設定時產生，不是在容器啟動時。新增或更換金鑰後，要再套用一次設定（介面上的套用／重新載入），nginx 才會載入 bouncer。移除金鑰後直接重啟容器也安全：啟動時會刪掉過期的 bouncer 設定檔，bouncer 不啟用、流量照常通過（不必先套用）。
- 不想讓金鑰出現在容器環境變數與 `docker inspect`：改用 secrets 覆寫檔（`.env` 的 `CROWDSEC_BOUNCER_KEY` 留空）：

  ```bash
  mkdir -p secrets
  openssl rand -hex 32 | tr -d '\n' > secrets/crowdsec_bouncer_key
  docker compose -f docker-compose.yml -f docker-compose.secrets.yml --profile security up -d
  ```

- 要更換金鑰：crowdsec 容器不會重新註冊已存在的名稱，所以先刪掉已註冊的 bouncer：
  `docker exec nginxwebui-crowdsec cscli bouncers delete nginx`，再 `docker compose up -d`。

**注意事項**

- 站台或 location 自己寫了 `access_by_lua_*` 指令時，該範圍內會取代全站的 bouncer（nginx 不會合併不同層級的
  `access_by_lua_*`）。要保留防護，在自己的區塊開頭呼叫 `require("nwu_crowdsec").access()`。
- 用 `return` 回應的 location（例如轉址）在 access 階段之前就結束，bouncer 看不到這些請求。
- 把自己的 `/etc/crowdsec` bind-mount 進 crowdsec 容器，會蓋掉 image 內烤好的 hub 內容（日誌解析器、情境、AppSec
  規則）。請沿用 image 內的 `/etc/crowdsec`，或自行安裝同樣的 collection。

**從 traefik bouncer 架構升級**（舊版：有 `crowdsec-bouncer` service 與 `auth_request` 模板）

- crowdsec 資料卷裡已經有名為 `nginx` 的 bouncer，新版啟動時不會再用金鑰註冊。沿用原本的 `CROWDSEC_BOUNCER_KEY`；
  要換新金鑰，先 `docker exec nginxwebui-crowdsec cscli bouncers delete nginx`，再 `docker compose up -d`。
- 舊的 `nginxwebui-bouncer` 容器已不在 compose 檔裡，用 `docker compose --profile security up -d --remove-orphans`
  移除（或 `docker rm -f nginxwebui-bouncer`）。
- 種子模板「CrowdSec Auth Request」指向一個從未存在的 location，升級時會自動移除（使用者改過的保留）。升級後請套用一次設定。

---

## nginx 文件 MCP 服務

一個唯讀的 [MCP](https://modelcontextprotocol.io/) 端點，把 nginx 官方指令文件提供給 AI 助理查，
讓它照文件回答而不是憑記憶。索引在啟動時從打包進 jar 的 150 頁文件建立 —— **969 條指令定義 /
803 個相異名稱 / 99 個模組 / 15 個 context** —— 全程不連外網。

五個唯讀工具：`nginx_directive`（精準查詢單一指令）、`nginx_search`（全文搜尋）、
`nginx_module`（列出某模組的所有指令）、`nginx_context`（反查 `location`、`server`、`upstream` 等
context 裡能合法使用哪些指令）、`nginx_check_config`（拿設定草稿裡的**指令與區塊位置**對照文件檢查 context）。

**`nginx_check_config` 檢查什麼、不檢查什麼。** 它逐行讀設定，靠追蹤大括號知道每一行落在哪個區塊，
然後檢查**指令行**：這是不是真的指令、目前這一層是不是文件允許的 context。開區塊那一行在推進堆疊之前
也用同一套規則檢查，所以區塊開錯位置——`if { }` 直接寫在 `http` 底下、`server { }` 寫在 `location` 裡、
`upstream { }` 寫在 `server` 裡——同樣會被報出來。不檢查的是語法細節與參數值：context 正確但參數亂寫
會通過。文件裡沒有的區塊名（第三方模組的 `geoip2 { }`）一律當作看不懂，整層內容保持沉默。
大括號追蹤同時也是「回報看起來斬釘截鐵、卻仍可能是錯的」
的原因：括號不平衡時，從不平衡那一點之後每一行都會被算在錯的區塊裡；而**括號即使完全平衡也可能出錯**
——只要 `}` 沒有自己獨佔一行。`} }`、`}}`、`} location /b {` 這幾種寫法已經追蹤得對，行尾的 `}`
（`listen 80; }`）則還沒有。因此但書講的是「`}` 與其他內容寫在同一行」這**整類**情形，而不是列舉特定
寫法——列舉一旦漏掉某種寫法，反而等於替它背書；但書並附上一個涵蓋整類的驗證方式：把每個 `}` 拆成
獨立一行再檢查一次，兩次結果一致才代表判斷可信。**左**大括號與其他內容同行（`server { listen 80;`）
則走另一條路：那一層直接轉為看不懂，深度跟著對齊、裡面的內容不報而不是報錯的。而且沒有回報永遠
不等於設定正確——這個工具只講能確定的事，其餘一律刻意保持沉默。

### 如何啟用

**這個端點預設是關閉的。** 只有啟動時加上 `--mcp.token=<token>` 才會存在，而這個 token 同時就是
client 必須出示的憑證。沒加這個參數時 `/mcp` 一律回 `404`，文件索引也完全不會被解析 ——
既有部署升級後行為零變化。

token 自己挑一個難猜的字串即可（`openssl rand -hex 16` 產生的就很適合）。

**跑 jar：**

```bash
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-*.jar \
     --server.port=8080 \
     --project.home=./dev-home/ \
     --mcp.token=REPLACE_WITH_YOUR_TOKEN
```

**Docker Compose：** `docker/docker-compose.yml` 的 `BOOT_OPTIONS` 已帶 `--mcp.token=${MCP_TOKEN:-}`。
在 `docker/.env`（或 stack 管理工具的環境變數）設定 `MCP_TOKEN`，再 `docker compose up -d`。
留空則端點維持關閉。

```bash
MCP_TOKEN=REPLACE_WITH_YOUR_TOKEN
```

> **為什麼用啟動參數而不是環境變數。** 設定鍵是 `mcp.token`，中間那個點讓它**不是合法的 POSIX shell
> 識別字**：`export mcp.token=...` 在 sh/bash 會直接語法錯誤。在沒有 shell 介入的地方它仍然可以用環境變數
> 給 —— compose 的 `environment:` 條目、或 `docker run -e mcp.token=...` —— 但啟動參數哪裡都能用，優先用它。

啟用之後，打 `/mcp` 必須帶 header `Authorization: Bearer <token>`；沒帶或帶錯一律 `401`。
傳輸走 streamable-stateless HTTP，所以裸 `POST` 直接回純 JSON —— 不需要 session 握手，也沒有 SSE 包裝。

要確認有沒有起來，跟它要一次工具清單：

```bash
curl -s -X POST http://localhost:8080/mcp \
     -H 'Accept: application/json, text/event-stream' \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer REPLACE_WITH_YOUR_TOKEN' \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

端點正常的話會回一個列出五個工具的 JSON 物件。

> **`Accept` 這個 header 是必要的，而且兩種型態都要列。** MCP streamable 規格要求 client 必須同時接受
> `application/json` **與** `text/event-stream`；不帶這個 header、或只寫 `application/json`，
> 請求會被擋成 **`400` 且 body 完全是空的** —— 不會告訴你原因。MCP client 自己會帶對，
> 所以這個坑只有在用 curl 手動測試時才會踩到。

### client 端怎麼設定

專案根目錄的 [`.mcp.json`](./.mcp.json) 已經幫 Claude Code 註冊好這個 server。它讀兩個環境變數，
所以 token 不會進版控：

| 變數 | 預設 | 說明 |
|---|---|---|
| `NGINX_WEBUI_MCP_TOKEN` | 無 —— **必填** | 必須與啟動時 `--mcp.token` 的值一致 |
| `NGINX_WEBUI_MCP_URL` | `http://localhost:12300/mcp` | `12300` 只是 Docker Compose 對外映射的 port。直接跑 jar 的話要改成 `--server.port` 給的那個，通常是 `http://localhost:8080/mcp` |

這兩個名字都是正常的 shell 識別字，啟動 client 前照一般方式 export 即可：

```bash
export NGINX_WEBUI_MCP_TOKEN=REPLACE_WITH_YOUR_TOKEN
export NGINX_WEBUI_MCP_URL=http://localhost:8080/mcp   # 不是走 Compose 那個 port 才需要設
```

---

## 升級

```bash
git switch release
git pull --ff-only origin release
cd docker
docker compose pull
docker compose up -d
```

> **2026-06-30 行為變更（monitoring 移除）**：Loki / Promtail / Grafana 三個 sidecar 已從專案完全移除（nginx 內建 access/error log 已足夠排查；CrowdSec 直接讀 nginx log volume，不再經 Loki 中介）。若你原本有跑 monitoring profile：升級後 `docker compose up -d` 不再啟動這三個 service，現存的 `nginxwebui_loki_data` / `nginxwebui_grafana_data` volume 可手動 `docker volume rm` 清理。CrowdSec 從 `--profile security` 啟動的行為不變。

> **CrowdSec Lua bouncer（取代 traefik bouncer）**：原本有跑 `security` profile 的部署，請先看
> [從 traefik bouncer 架構升級](#crowdsec-防護)（沿用 bouncer 金鑰、移除舊的 `nginxwebui-bouncer` 容器）。

PostgreSQL schema 由 SqlHelper（自製 ORM）**CodeFirst 自動 ALTER TABLE** 加新欄位，**不需手動 migration**。

---

## 開發指南

- [`CLAUDE.md`](./CLAUDE.md) — 完整開發環境設置、技術棧、目錄結構、SqlHelper 速查、Solon DI 註解規範、release 流程
- [`docs/superpowers/plans/`](./docs/superpowers/plans/) — 所有 design 文件 + 實作報告
- [`tests/e2e/`](./tests/e2e/) — Playwright E2E 測試（31 個 spec）

```bash
# 開發環境
npm install && npx playwright install --with-deps chromium
mvn clean package -DskipTests # 第一次建置會一併下載 Node 24 並建置 Vue 前端（frontend/）
npm test                      # 跑 E2E（headed）
npm run test:fast             # 跑 E2E（headless / CI）
```

---

## Release 歷史（近期）

| Tag | 主軸 |
|---|---|
| **[v5.2.8](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.8)** | nginx 官方文件在地化＋程式碼安全工具：822 個程式碼範例從引用塊轉為 code fence，以字元級內容指紋把關；另修 138 處機器翻譯損壞 |
| [v5.2.7](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.7) | 全域 http 參數面板移至 http 參數配置頁；header GeoIP 狀態改為 2x2 grid |
| [v5.2.6](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.6) | 修復全站儲存死鎖（`nginx -t` 前置檢查逾時）＋ DenyAllow 改為全站自動生效，取消逐站綁定 |
| [v5.2.5](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.5) | 安全修復（CodeQL：zip-slip path traversal / DOM XSS / 敏感資訊入 log）+ 依賴升級收齊 |
| [v5.2.4](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.4) | CI 發版自動建 GitHub Release（不再手動補、頁面不再落後）|
| [v5.2.0](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.0) | GeoIP DB 模組：header 顯示 Country/City/ASN MMDB build date + 手動下載 |
| [v5.1.1](https://github.com/elf-express/nginxWebUI/releases/tag/v5.1.1) | 自建 sidecar baked images（config 燒進 image）+ CI matrix build；目前為 **2 個自建 image**（nginxwebui + nginxwebui-crowdsec）|
| [v5.1.0](https://github.com/elf-express/nginxWebUI/releases/tag/v5.1.0) | Sidecar baked image 自包含部署（config 燒進 image）+ `deploy/` 改名 `docker/` + compose 移除 init.* 預設 + 品牌 Logo 上傳 |
| [v5.0.13](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.13) | UI 大改造：modal layout + template picker + 黑名單 CSV 多選 |
| [v5.0.12](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.12) | DenyAllow URL fetch redirect-follow + 最後更新時間 column |
| [v5.0.11](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.11) | URL 抓取 IP 自動去重 |
| [v5.0.10](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.10) | DenyAllow JS 放寬：URL 非空允許 IP empty 提交 |
| [v5.0.7](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.7) | DenyAllow URL 每日自動抓 + 預設國家白名單 + Grafana menu link |
| [v5.0.6](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.6) | ASN tab 進防護憑證 + SpecSnap inspector + port 12300 |
| [v5.0.4](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.4) | dev/release pipeline 建立 |

完整 changelog: https://github.com/elf-express/nginxWebUI/releases

---

## 授權與致謝

**License:** MIT

**原作者:** [cym1102](https://gitee.com/cym1102)（[gitee.com/cym1102/nginxWebUI](https://gitee.com/cym1102/nginxWebUI)）— 此 fork 的所有核心功能（nginx 設定產生、反向代理、acme.sh 證書、SqlHelper ORM 等）皆來自上游。

**Fork 維護:** [elf-express](https://github.com/elf-express)（ELF International Express）

**問題 / PR:** https://github.com/elf-express/nginxWebUI/issues

**上游問題:** QQ 群 560797506（cym1102 維護）
