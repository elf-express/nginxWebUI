# nginxWebUI · elf-express fork

> 圖形化管理 nginx 設定的網頁工具 — 整合 GeoIP / CrowdSec / Loki / Grafana / Vue Dashboard 的安全強化版

**English** · [README_EN.md](./README_EN.md)　**繁體中文** · 本檔

---

## 這是什麼

[nginxWebUI](https://github.com/cym1102/nginxWebUI) 是 [cym1102](https://gitee.com/cym1102) 開源的 nginx 圖形化設定工具。原版功能完整、但設定**全在單一容器內、無觀測性、無安全強化、UI 偏舊**。

**這個 fork ([elf-express/nginxWebUI](https://github.com/elf-express/nginxWebUI)) 把它演進成 production-grade 部署：**

| 維度 | 上游 cym1102 | elf-express fork |
|---|---|---|
| **資料庫** | SQLite（單檔） | **PostgreSQL 18-alpine**（多容器、可備援） |
| **觀測性** | 無 | **Loki + Promtail + Grafana** 完整 log/metric 管線 |
| **安全防護** | 純 IP 黑白名單 | + **CrowdSec** 入侵偵測、+ **GeoIP2** 國家封鎖、+ **ASN** 封鎖、+ **多 list 自動 fetch** |
| **語系** | 簡中為主 | **繁中為主**（簡 / 繁 / 英三份）|
| **前端** | 純 Layui + jQuery | + **Vue 3 局部 mount**（template picker / SpecSnap inspector / **5.1.0 Dashboard**）|
| **CI/Release** | 手動打 jar | **GitHub Actions** 自動 build multi-platform image (linux/amd64 + linux/arm64) push 到 ghcr.io |
| **開發流程** | 直接 push master | **dev/master 分支**、git tag-based release、`scripts/release.sh` 自動化 |

> 不取代上游，**互補**：你要極輕量單機部署用上游；要企業級觀測性與安全強化用這個 fork。

---

## 快速開始

### 用 Docker Compose 一鍵啟動完整 stack

```bash
git clone https://github.com/elf-express/nginxWebUI.git
cd nginxWebUI
git checkout v5.0.13          # 最新 release
cd deploy
docker compose up -d
```

打開瀏覽器 → **http://localhost:12300** → 預設帳密 `admin` / `Admin123`

七個 service 一起起來：

| Service | Port | 用途 |
|---|---|---|
| **nginxwebui** | 12300:8080 / 80 / 443 | 主應用 |
| postgres | 5432 | 資料庫 |
| loki | 3100 | 日誌聚合 |
| **grafana** | 3000 | 監控儀表板（admin/admin）|
| promtail | — | 把 nginx + app log 推到 Loki |
| crowdsec | — | 入侵偵測（v1.7.8）|
| crowdsec-bouncer | — | nginx 流量過濾（0.5.0）|

### Stack 架構

```
┌─ nginxwebui (Solon 3.3.3 + Java 8) ──────────────────┐
│                                                       │
│  ┌─ Web UI (Layui + Vue 3 局部 mount) ─────────────┐ │
│  │  防護與憑證 / 反向代理 / Stream / Upstream / ...│ │
│  └──────────────────────────────────────────────────┘ │
│                       ↓ SqlHelper (自製 ORM)          │
│            PostgreSQL ← cert / server / denyAllow     │
│                       ↓ ConfService 生成             │
│            nginx.conf + 反向代理 + GeoIP/ASN block    │
└───────────────────────────────────────────────────────┘
            ↓ access log                ↑ HTTP query
┌─ Promtail ─→ Loki ←─ Grafana Dashboard ──────────────┐
│                  ←─ nginxwebui Monitor (5.1.0)        │
└───────────────────────────────────────────────────────┘
            ↓ access log              ↑ cscli / API
┌─ CrowdSec (入侵偵測) ──→ Bouncer ──→ nginx auth_request
└───────────────────────────────────────────────────────┘
```

---

## 主要功能

### 🛡 安全防護

- **IP 黑白名單清單** — 多個 list、可同時套用（CSV multi-select）、**每日定時從 URL 自動抓**（SpamHaus DROP / FireHOL / Emerging Threats / IPsum / Binary Defense 等）
- **GeoIP2 國家封鎖** — 預設白名單 17 國（CN/JP/HK/KR/SG/TH/MY/TW/VN/GB/FR/DE/GR/CA/US/MO/LA），用戶可自訂
- **ASN 封鎖** — 按 Autonomous System Number 封整段網路
- **CrowdSec 整合** — 容器化部署、bouncer 攔截攻擊 IP
- **防爬蟲憑證** — 「防護與憑證」頁面集中管理

### 🌐 反向代理 / 負載均衡

- HTTP / HTTPS / TCP / UDP 全支援、自動產生 `nginx.conf`
- TLS 1.2 / 1.3 支援、Let's Encrypt 自動續簽（acme.sh DNS 模式）
- 上游負載均衡（upstream）含 weight / backup / down 設定
- **19 個內建參數模板**（含中文註解）：WebSocket Proxy / Proxy Headers / Large File Upload / CORS / Rate Limit / Security Headers / GeoIP / CrowdSec 認證

### 📊 觀測性（5.1.0 重點強化中）

- Grafana 預先配 dashboard、看 nginx 流量 + 系統 metric
- Loki 收集所有 nginx access log + nginxwebui app log
- Promtail 自動轉發
- **5.1.0 即將推出**：原生 Vue Dashboard 整合 Loki query、4 大類指標
  - 系統 (CPU/Mem/Disk/Net)
  - **安全（封鎖 IP/國家/ASN Top N、CrowdSec alerts/decisions）**
  - 流量（RPS / status code / response time / top path）
  - TLS（憑證到期警告 / TLS 版本分布）

→ [完整 5.1.0 設計文件](./docs/superpowers/plans/2026-05-22-monitor-dashboard-v2.md)

### 🎨 UI

- 繁中為主、簡 / 英三語 i18n（國旗 icon 切換）
- 反向代理 modal 單欄向左對齊、不蓋 top header
- **shadcn-vue 風格** template picker（Vue 3 + 自製 Combobox）
- **SpecSnap Inspector** dev tool（右上角 🔍）— 點 UI 元素自動 capture 結構化 metadata 給 AI

### 🚀 開發流程

- **dev / master 雙分支模型**：日常開發在 dev、release 才打 tag、master = 最近一次 release 快照
- **`scripts/release.sh`** 自動化 pom bump + commit + git tag
- **GitHub Actions** 看到 `v*` tag → 自動 build multi-platform image → push 到 ghcr.io
- **Dependabot** 每週掃 Maven + Docker + Actions 依賴升級

---

## 部署選項

### A. Docker Compose（推薦、生產環境）

見上面「快速開始」。完整 stack 7 個 service。

### B. 純 jar 部署（最小化、開發環境）

```bash
# 1. 編譯
mvn clean package -DskipTests

# 2. 啟動
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-5.0.13.jar \
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
docker pull ghcr.io/elf-express/nginxwebui:5.0.13
# 或 :latest（永遠等於最新 tag build）
```

multi-platform: linux/amd64 + linux/arm64

---

## 升級

```bash
git pull origin master
cd deploy
docker compose pull
docker compose up -d
```

PostgreSQL schema 由 SqlHelper（自製 ORM）**CodeFirst 自動 ALTER TABLE** 加新欄位，**不需手動 migration**。

---

## 開發指南

- [`CLAUDE.md`](./CLAUDE.md) — 完整開發環境設置、技術棧、目錄結構、SqlHelper 速查、Solon DI 註解規範、release 流程
- [`docs/superpowers/plans/`](./docs/superpowers/plans/) — 所有 design 文件 + 實作報告
- [`tests/e2e/`](./tests/e2e/) — Playwright E2E 測試（24+ 場景）

```bash
# 開發環境
npm install && npx playwright install --with-deps chromium
mvn clean package -DskipTests
npm test                      # 跑 E2E（headed）
npm run test:fast             # 跑 E2E（headless / CI）
```

---

## Release 歷史（近期）

| Tag | 主軸 |
|---|---|
| **[v5.0.13](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.13)** | UI 大改造：modal layout + template picker + 黑名單 CSV 多選 |
| [v5.0.12](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.12) | DenyAllow URL fetch redirect-follow + 最後更新時間 column |
| [v5.0.11](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.11) | URL 抓取 IP 自動去重 |
| [v5.0.10](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.10) | DenyAllow JS 放寬：URL 非空允許 IP empty 提交 |
| [v5.0.7](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.7) | DenyAllow URL 每日自動抓 + 預設國家白名單 + Grafana menu link |
| [v5.0.6](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.6) | ASN tab 進防護憑證 + SpecSnap inspector + port 12300 |
| [v5.0.4](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.4) | dev/release pipeline 建立 |

完整 changelog: https://github.com/elf-express/nginxWebUI/releases

---

## Roadmap

- **v5.1.0**（進行中）— Vue Dashboard 重做：4 大類指標 + ECharts + Loki query 整合
- v5.2.0（規劃）— Grafana 預配 dashboard JSON 升級、加 alert rules
- v5.3.0（規劃）— Dashboard 加 widget 拖拉排序 + 歷史趨勢頁

---

## 授權與致謝

**License:** MIT

**原作者:** [cym1102](https://gitee.com/cym1102)（[gitee.com/cym1102/nginxWebUI](https://gitee.com/cym1102/nginxWebUI)）— 此 fork 的所有核心功能（nginx 設定產生、反向代理、acme.sh 證書、SqlHelper ORM 等）皆來自上游。

**Fork 維護:** [elf-express](https://github.com/elf-express)（ELF International Express）

**問題 / PR:** https://github.com/elf-express/nginxWebUI/issues

**上游問題:** QQ 群 560797506（cym1102 維護）
