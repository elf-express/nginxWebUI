# Dashboard 重做計畫 — nginxWebUI Monitor v2

> **SUPERSEDED 2026-06-30:** Monitoring stack (Loki / Promtail / Grafana) has been removed from the project — nginx 內建 access/error log 已足夠日常排查,Loki query 整合不再需要。本 plan 大部分內容(security/traffic/TLS tab、Loki data source)已失效,僅保留 OSHI 系統指標的部分仍可參考。文件保留作歷史記錄。

**日期：** 2026-05-22
**狀態：** ~~已批准，待執行~~ → **SUPERSEDED**(monitoring stack 已撤除)
**目標版本：** 5.1.0（cross-minor feature release）
**前置版本：** 5.0.13（T1-T4 + template picker + 黑名單 CSV 已 ship）

## Context

**現況**（5.0.12 → 5.0.13 候選版）：
- [`/adminPage/monitor`](src/main/resources/WEB-INF/view/adminPage/monitor/index.html) 只有 4 個指標：CPU / 記憶體 / 磁碟（progress bar） + 網速（折線圖）
- 視覺風格停留在 Layui 預設、使用者明確說「UI 實在不能看」
- 缺乏安全防護指標 — 使用者一直在加 GeoIP / CrowdSec / DenyAllow，但**看不到效果統計**

**目標**：取代現有 `/adminPage/monitor` 頁面，做成現代化 dashboard，整合 4 大類指標、shadcn-vue 風格、即時更新。

**已確認的決策**（brainstorming 階段）：

| 決策 | 選擇 |
|---|---|
| Q1: 指標範圍 | **全部 4 類**：封鎖統計 + Nginx 流量 + CrowdSec + TLS/Cert |
| Q2: UI 結構 | **取代** `/adminPage/monitor`，原 URL 不變 |

---

## 整體架構

### 分層結構

```
┌─ Vue 3 SPA (mount 在 monitor/index.html 內 #dashboard-mount) ────┐
│                                                                  │
│   ┌─ Tab Bar ────────────────────────────────────────────────┐  │
│   │  [系統] [安全] [流量] [TLS]                                │  │
│   └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│   ┌─ Card Grid (依 tab 動態顯示) ─────────────────────────────┐  │
│   │  [Stat Card]  [Stat Card]  [Stat Card]  [Stat Card]      │  │
│   │  [Chart Card (big)]         [Top N List Card]            │  │
│   │  [Chart Card]               [Chart Card]                 │  │
│   └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│   每 30 秒自動 refetch；右上角手動 [refresh] 按鈕              │
└──────────────────────────────────────────────────────────────────┘
                        ↓ HTTP (JsonResult)
┌─ Backend (Solon @Controller) ───────────────────────────────────┐
│                                                                  │
│   /adminPage/monitor/load                  系統 (既有)          │
│   /adminPage/monitor/network               網速 (既有)          │
│   /adminPage/monitor/security/stats     ★ 新                    │
│   /adminPage/monitor/traffic/stats      ★ 新                    │
│   /adminPage/monitor/crowdsec/stats     ★ 新                    │
│   /adminPage/monitor/tls/stats          ★ 新                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
                        ↓ 各自查詢
┌─ Data Sources ──────────────────────────────────────────────────┐
│  OSHI / Java Sigar  系統 metrics (CPU/Mem/Disk/Net) — 既有       │
│  PostgreSQL          Cert / DenyAllow / GeoRule / AsnRule        │
│  GeoIP2 MMDB        IP → 國家碼 (~/etc/nginx/geoip/*.mmdb)       │
│  Loki HTTP API       nginx access log 統計 (port 3100 內網)     │
│  CrowdSec LAPI       alerts / decisions / metrics                │
│  Nginx error/access  本地檔案 fallback (若 Loki 不可達)         │
└──────────────────────────────────────────────────────────────────┘
```

---

## 指標規格（4 大類、共 ~14 個 widget）

### Tab 1: 系統（保留現有，重新排版）

| Widget | 來源 | 既有? |
|---|---|---|
| CPU 利用率（含核心數） | `monitorService.getMonitorInfoOshi()` | ✅ |
| 記憶體使用 | 同上 | ✅ |
| 磁碟使用（per mountpoint） | `monitorService.getDiskInfo()` | ✅ |
| 網速 sparkline（即時） | `NetWorkUtil.getNetworkDownUp()` | ✅ |
| Nginx Worker 狀態（version + module 數） | `nginxService.getNginxVersion()` | ✅ |

### Tab 2: 安全防護（★ 重點）

| Widget | 資料來源 | 視覺 |
|---|---|---|
| **今日 deny IP 累計數** | Loki query `nginx access log {status=~"403|444"}` 過去 24h | 大數字 + 趨勢 sparkline |
| **被封鎖國家 Top 10** | Loki query + 用 nginx log 的 `$geoip2_data_country_code` 欄位 group by | 水平 bar chart |
| **被封鎖 IP Top 10** | Loki query group by `$remote_addr` | 表格（IP + 次數 + 國家旗幟） |
| **被封鎖 ASN Top 10** | Loki query + nginx log `$geoip2_data_asn_org` | 水平 bar chart |
| **DenyAllow 清單健康** | `sqlHelper.findAll(DenyAllow.class)` | Card 列表：每筆清單 → ip_count + last_fetch_at + 過期警告 |

### Tab 3: Nginx 流量

| Widget | 資料來源 | 視覺 |
|---|---|---|
| **RPS (requests / sec)** | Loki 每分鐘 count、轉 RPS | 折線圖（last 60 min） |
| **HTTP status 分布** | Loki group by status code | Donut（2xx/3xx/4xx/5xx）|
| **Top 10 訪問 path** | Loki group by `$request_uri` | 表格 |
| **平均 response time** | Loki query `$request_time` avg | 大數字 + 折線圖 |

### Tab 4: TLS / Cert

| Widget | 資料來源 | 視覺 |
|---|---|---|
| **憑證到期警告** | `sqlHelper.findAll(Cert.class)` + 計算 endTime | Card 列表，<30 days 紅、<60 黃 |
| **Let's Encrypt 自動續簽狀態** | 同上 + cert.autoRenew | 顯示「N 個自動續簽中、上次成功時間」 |
| **客戶端 TLS 版本分布** | Loki query `$ssl_protocol` 欄位 | Donut（TLSv1.2 / 1.3 / older） |
| **今日 HTTPS 連線數** | Loki count + scheme=https | 大數字 |

### Tab 5: CrowdSec（如果 container 存在）

| Widget | 資料來源 | 視覺 |
|---|---|---|
| **Active decisions 數** | `docker exec crowdsec cscli decisions list -o json` | 大數字 + 趨勢 |
| **今日 alerts 數** | `cscli alerts list -o json --since 24h` | 大數字 |
| **Bouncer hits** | bouncer container `/api/v1/metrics` Prometheus format | 折線圖 |
| **Top 攻擊類型** | alerts group by scenario | 水平 bar chart |

---

## 技術棧決定

| 元件 | 選擇 | 理由 |
|---|---|---|
| **前端框架** | Vue 3 (esm.sh CDN) | 已用於 SpecSnap + template-picker、cache 命中、不引入 build pipeline |
| **圖表庫** | **ECharts 5** (esm.sh CDN) | 比 Chart.js 強大、原生 darkmode、繁中字體完整、enterprise dashboard 標配 |
| **設計風格** | shadcn-vue inspired（自製 CSS） | 與 5.0.6 SpecSnap panel + 5.0.13 template-picker 一致 |
| **狀態管理** | Vue ref/computed (無 Pinia) | 單頁、無跨組件共享需求 |
| **路由** | tab 切換用 Vue ref + v-show | 不必引入 vue-router |

---

## 階段交付（一次到位）

**使用者選擇 v1 一次做完含 Loki**。CrowdSec 可選、若 container 偵測得到就含、否則 hide。

### v1 release 內容（預估 4-6 天工作量）

**後端 — 4 個新 endpoint + 2 個新 service：**
- `LokiQueryService` — wrapping `http://loki:3100/loki/api/v1/query_range`，含 timeout、retry、空回應 fallback
- `SecurityStatsService` — 對外 `/security/stats`，整合 Loki query + GeoIP 反查國家旗幟
- `TrafficStatsService` — 對外 `/traffic/stats`，RPS / status / path / response time
- `TlsStatsService` — 對外 `/tls/stats`，憑證到期 (PG) + TLS 版本分布 (Loki)
- `CrowdSecStatsService` — 對外 `/crowdsec/stats`，先試 `docker exec crowdsec cscli ... -o json`，container 不存在則 return `{ available: false }`

**前端 — 一個 Vue 3 dashboard SPA：**
- `static/js/adminPage/monitor/dashboard.js` — esm.sh 載 vue@3.5 + ECharts 5、~600-800 行
- 4-5 tabs：系統 / 安全 / 流量 / TLS /（CrowdSec 條件式）
- 每 tab 內 widget grid，~14 個 widget 共
- 30 秒 auto-refetch + 右上手動 refresh button
- shadcn-vue 風格 cards + ECharts 5 圖表

**驗收**（開 `/adminPage/monitor`）：

1. 4 tabs 全亮、Tab 內 widget 都不是「loading 中」卡住
2. 「安全 → 被封鎖國家 Top 10」顯示水平 bar chart、至少有 1 筆 data（可故意 curl 503 觸發）
3. 「流量 → RPS」折線圖過去 60 min、刻度合理
4. 「TLS → 憑證到期」紅黃綠 card 顯示對應顏色
5. CrowdSec container 在就顯示 Tab 5、不在則完全 hide
6. 30 秒後自動更新（觀察 chart 內 timestamp 變化）
7. Loki 斷線時 fallback「Loki 暫不可用」、其他 tabs（系統 / TLS 部分）仍能跑

### v2（之後優化）— 選擇性

- e2e 測試 `tests/e2e/23-dashboard-v2.spec.js` 補完
- 加 widget 自訂排序（拖拉）
- 加歷史趨勢頁（30 天 / 7 天時段切換）
- Loki Recording Rules 加速 query
- 加導出 PNG / PDF 報表（給老闆看）

---

## 影響的檔案

### 會新增

- `src/main/java/com/cym/service/LokiQueryService.java` — Loki HTTP wrapper（v2）
- `src/main/java/com/cym/service/SecurityStatsService.java` — 封鎖統計聚合（v2）
- `src/main/java/com/cym/service/TrafficStatsService.java` — 流量統計（v2）
- `src/main/java/com/cym/service/CrowdSecStatsService.java` — CrowdSec 包裝（v3）
- `src/main/java/com/cym/ext/SecurityStatsExt.java`、`TrafficStatsExt.java`、`TlsStatsExt.java` — DTO
- `src/main/resources/static/js/adminPage/monitor/dashboard.js` — Vue 3 mount、~400-500 行
- `src/main/resources/static/css/dashboard.css` — shadcn-vue 風格 utility class（或 inline 在 dashboard.js）

### 會修改

- `MonitorController.java` — 加 4 個新 `@Mapping` endpoint（security/stats、traffic/stats、tls/stats、crowdsec/stats）
- `monitor/index.html` — 大改寫：移除既有 layui card grid、改成單一 `<div id="dashboard-mount">` Vue mount point
- `messages*.properties` × 3 — 加 `monitorStr.*` i18n keys（簡繁英）

### 會刪除

無 — 既有 endpoint (`load`、`network`、`nginxInfo`) 保留供 Vue dashboard 呼叫。

---

## 驗證計畫

### v1 完成後

```bash
# 1. 主應用起來
curl http://localhost:12300/adminPage/monitor/load | jq .
# 預期 success: true + CPU/Mem/Disk 數值

# 2. 開 UI 看 Vue mount 成功
open http://localhost:12300/adminPage/monitor
# 預期：左側 menu 進「系統運行狀態」→ 4 個 tabs（系統 / 安全 / 流量 / TLS）
#       「系統」tab 內 CPU/Mem/Disk progress（重新設計版）+ 網速折線
#       「安全」tab 內 DenyAllow 清單健康 card
#       「TLS」tab 內憑證到期警告 card
#       30 秒自動 refresh

# 3. e2e test (新增)
tests/e2e/23-dashboard-v2.spec.js
```

### v2 完成後（追加）

```bash
# Loki 連線檢測
docker exec nginx-webui-5.0.3 sh -c 'curl -sI http://loki:3100/ready'
# 預期 HTTP 200

# 觸發一個 403 訪問
curl -H 'X-Forwarded-For: 1.2.3.4' http://localhost/adminPage/...   # 應該被 GeoBlock

# 等 1 分鐘 promtail 上傳到 loki
# 然後查 dashboard 安全 tab，應該看到那個 IP / 國家在 Top 10

# Loki 不可達時 fallback
docker stop nginx-webui-5.0.3-loki
# Dashboard 安全 tab 應該顯示「Loki 暫不可用」訊息、不 crash
docker start nginx-webui-5.0.3-loki
```

---

## 風險與緩解

| 風險 | 緩解 |
|---|---|
| Loki query 慢（24h range 大量 log） | 限制 query range max 6h、改用 Loki Recording Rules 預聚合 |
| ECharts CDN 載入時間長（~300 KB） | esm.sh edge cache 應 < 1s；fallback 顯示「載入中」骨架 |
| CrowdSec container 不存在 | 後端 `docker exec` fail → return `{ available: false }` → 前端 hide tab |
| 30 秒 polling 對 nginxWebUI 自身 load 影響 | 各 endpoint 限頻、加 cache（Caffeine） |
| Vue dashboard 改 monitor 後既有 e2e 測試 (`10-nginx-info.spec.js`) 失效 | 升級 e2e、保留 `/adminPage/monitor/nginxInfo` API 路徑不變 |

---

## ExitPlanMode 後的執行順序（一次到位）

1. plan 從 `compressed-floating-otter.md` 搬到 `docs/superpowers/plans/2026-05-22-monitor-dashboard-v2.md`、commit 到 dev
2. **後端骨架**：建立 5 個 service stub（LokiQueryService / SecurityStatsService / TrafficStatsService / TlsStatsService / CrowdSecStatsService）+ MonitorController 加 4 個新 `@Mapping`、回傳 mock 資料、確認前端能呼叫
3. **前端骨架**：`dashboard.js` Vue mount + 4-5 tabs 結構 + 每 tab 一個 placeholder widget；`monitor/index.html` 改成 `<div id="dashboard-mount">`
4. **後端實作**：
   - LokiQueryService → 對 `http://loki:3100/loki/api/v1/query_range` 發 HTTP（hutool HttpUtil）、parse JSON
   - SecurityStatsService → Loki LogQL `{filename=~".*access\\.log"} |~ " 4(03|44) "` group by status / country / asn
   - TrafficStatsService → LogQL count_over_time / topk
   - TlsStatsService → sqlHelper.findAll(Cert.class) + Loki query `$ssl_protocol`
   - CrowdSecStatsService → 用 hutool `RuntimeUtil.execForLines("docker", "exec", "crowdsec", "cscli", "decisions", "list", "-o", "json")` → parse；container missing 則 `{ available: false }`
5. **前端實作**：
   - ECharts 5 esm.sh import + 各 widget 對應 chart（bar / donut / line / 大數字 + sparkline）
   - shadcn-vue 風格 CSS（複用 template-picker.js 的 utility class）
   - 30 秒 setInterval refetch + 手動 refresh button
   - Loki 不可用時 fallback UI（不 crash）
6. **i18n**：三份 properties 加 ~30 個 monitorStr.* keys
7. **本機驗證**：跑 mvn package + docker recreate + 開 /adminPage/monitor 七步驗收清單
8. **release**：scripts/release.sh 5.1.0（minor bump、含 feature）+ push tag + CI + master + GitHub Release page
9. v2 列入 followup（e2e 測試 / 拖拉 / 歷史頁 / Loki Recording Rules / 報表導出）
