# ipverse ASN 名錄 + CrowdSec 大封／小封 — Design Spec

日期: 2026-08-12  
分支: `feat/fixbug`  
狀態: baseline 已鎖定（brainstorming）  
來源: Lens/ipverse 分析 + 防護架構對齊 + 使用者定案

## 1. 背景與動機

1. **ASN 很多卻沒有權威名單** — 現有 `AsnRule` 僅手動 CRUD（asn / orgName / enable），conf 用 `map $geoip2_data_asn $blocked_asn`，無法搜尋、無分類、無全表。
2. **ipverse 權威性遠高於手填／觀點黑名單** — [as-metadata](https://github.com/ipverse/as-metadata) 全量 RIR 元數據；[as-ip-blocks](https://github.com/ipverse/as-ip-blocks) 提供每 ASN 宣告 prefix；[tools/crowdsec](https://github.com/ipverse/tools) 示範 `cscli decisions add --range` 大封路徑。
3. **實務運維** — 平時自動小封（CrowdSec scenario／社群）；刻意才大封 ASN；被打或備戰時切較嚴檔位。誤封 IP 以白名單／解封處理。
4. **一次到位** — 代碼備好、預設安全（Light）、需要才用、不雙開、不維護兩套模型、不反覆改執行引擎。

## 2. 目標

1. **全量**同步 ipverse as-metadata 到本地正規化表 `AsMeta`，可搜尋／篩選（asn、handle、description、country、category…）。
2. **執行主路徑 = CrowdSec LAPI**（對齊 ipverse/tools）：
   - **小封**：既有 decisions（scope=ip），UI 呈現／解封／白名單。
   - **大封**：選 ASN → 拉 as-ip-blocks → `scope=range` ban；`reason` 固定標記便於撤銷。
3. **運維檔位三選一互斥**：`light` | `manual` | `strict`（設定鍵 `protection.profile`，預設 `light`）。
4. **意圖與名錄分表（S1）**：`AsBlockIntent` 管生命週期；同步永不改意圖。
5. **無 CrowdSec**：名錄仍可同步與查詢；大封／推送 disabled（D1）。
6. **切回 Light（Z）**：UI 詢問「保留到期 / 立即撤銷本系統標記的 ASN range」。
7. **誤封**：解封／白名單以 CrowdSec 為準；可選同步 DenyAllow allow。

## 3. 非目標

- 不以 nginx `$blocked_asn` map 或 DenyAllow 全量 CIDR 作為**大封主引擎**。
- 不把 12 萬 ASN 的 prefix **全量**進庫。
- 不做「開 Strict 即自動封光所有 hosting」無確認全自動。
- 不做 AsMeta JSON blob 雙寫（只 P 正規化欄位）。
- 不在本 spec 重做 CrowdSec 安裝／bouncer 部署（沿用既有 compose profile）。

## 4. 架構

```
[Schedule] as-metadata → upsert AsMeta only
[UI] search AsMeta → create AsBlockIntent
[profile] light|manual|strict gates push / candidates
[push] intent → GET as-ip-blocks → CrowdSec LAPI range ban
       reason = nginxwebui:as-ban:AS{asn}
[auto] CrowdSec scenarios → ip decisions → UI
[unban] delete decision / CS whitelist (+ optional DenyAllow allow)
[Z] switch to light → keep until expiry | revoke by reason_tag
[nginx] bouncer enforces; WebUI reads LAPI + DB
```

### 4.1 元件職責

| 元件 | 職責 |
|------|------|
| `AsMeta` | 全量名錄，只被同步寫入 |
| `AsBlockIntent` | 刻意大封意圖 + 推送狀態機 |
| `AsnMetaService` | 同步、分頁搜尋 |
| `AsnBlockService` | 推送／撤銷／profile 門閘 |
| `CrowdSecController` 擴充 | range ban、依 reason 篩選／批量刪、whitelist |
| `protection.profile` | 三選一互斥 |
| 舊 `AsnRule` | 遷移後 deprecated；**不再**作為大封主路徑 |

### 4.2 三檔行為

| | Light | Manual | Strict |
|--|-------|--------|--------|
| 同步／搜尋 AsMeta | ✓ | ✓ | ✓ |
| CS 自動 IP 展示 | ✓ | ✓ | ✓ |
| 新增大封 Intent | ✗（提示切檔） | ✓ 手動 | ✓ 手動 |
| category → candidate | ✗ | ✗ | ✓（確認後才推） |
| 推送 range → CS | ✗ | ✓ | ✓ |
| 無 CS | 名錄可查；推送 disabled | 同左 | 同左 |

## 5. 資料模型

### 5.1 `AsMeta`（P 正規化，全量）

| Column | 來源 | 索引 |
|--------|------|------|
| `asn` (String PK 語意 unique) | asn | unique |
| `handle` | metadata.handle | index |
| `description` | metadata.description | |
| `countryCode` | countryCode | index |
| `category` | category | index |
| `networkRole` | networkRole | index |
| `origin` | origin | |
| `lastAnnounced` | lastAnnounced | |
| `syncedAt` | 本地 Long | |

同步來源（優先 CSV 輕量，需要 category 用 JSON 或 CSV+JSON 策略在 plan 定死）：

- CSV: `https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv`
- JSON: `https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json`（含 category / networkRole）

**實作定案（plan）**：第一版同步 **JSON**（一次拿到 category），串流／分批 upsert，避免 OOM；PG 為準，SqlHelper 同一 schema 支援 SQLite。

### 5.2 `AsBlockIntent`（S1 意圖表）

| Column | 用途 |
|--------|------|
| id | BaseModel PK |
| asn | 對應 AsMeta.asn |
| status | `candidate` \| `pending` \| `active` \| `failed` \| `revoked` |
| duration | 如 `24h` |
| reasonTag | 固定 `nginxwebui:as-ban:AS{asn}` |
| pushBatchId | 同一次推送批次 |
| lastPushAt | Long |
| lastError | String |
| createdByProfile | `manual` \| `strict` |
| note | 可選 |

**不存**全量 CIDR。推送時現拉：

`https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/ipv4-aggregated.txt`  
`https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/ipv6-aggregated.txt`

### 5.3 Settings

| Key | 值 |
|-----|-----|
| `protection.profile` | `light` \| `manual` \| `strict`（default light） |
| `asn.meta.lastSyncAt` | epoch millis string |
| `asn.meta.lastSyncError` | 可選 |
| `asn.meta.syncTime` | 每日 HH:mm，default 如 `04:15` |

### 5.4 CrowdSec 契約

- **大封**：LAPI `POST /v1/decisions`（或專案既有等價），`scope=range`，`type=ban`，`reason=nginxwebui:as-ban:AS{asn}`。
- **撤銷 Z**：列出 decisions，reason 前綴／相等匹配後 DELETE；或 LAPI 支援的 bulk delete。
- **白名單／解封**：DELETE decision；或 `type=whitelist`（若 LAPI 可用）— plan 實作時對齊既有 LAPI 版本行為。
- **可選**：解封時寫 DenyAllow `type=allow`。

### 5.5 舊 AsnRule

- Migration：既有 enable=true 的 AsnRule → AsBlockIntent status=active（**不**自動推 CS；標記需手動 re-push 或 migration 僅資料）。
- `ConfService` 停止以 AsnRule 產生 map 作為產品主路徑（或 feature flag 預設關）；避免雙引擎。

## 6. UI（防護中心 ASN 相關）

- Profile 三段控件（互斥）。
- 名錄：搜尋、category／country 篩選、分頁（不可一次渲染 12 萬列）。
- Intent 列表：status、推送、重試、撤銷。
- Strict：category 產生 candidate → 確認批次。
- 切 Light：Z 對話框。
- 無 CS：橫幅 + 推送 disabled。
- CrowdSec decisions／alerts 與解封／白名單強化（既有頁擴充）。

## 7. 排程

- `ScheduleTask`：每日 `asn.meta.syncTime` 全量同步 AsMeta（背景執行緒，比照 GeoIP 單飛鎖）。
- 手動「立即同步」API。

## 8. 測試

- 單元：reasonTag 格式、profile 門閘、CSV/JSON 解析（小 fixture）、狀態轉換。
- E2E：名錄搜尋 UI、三檔 disabled 狀態、無 CS 推送不可用；CS 可用 mock／skip 若環境無 LAPI。
- 三份 i18n。

## 9. 風險

| 風險 | 緩解 |
|------|------|
| JSON ~55–60MB 同步 | 分批 parse／streaming；背景執行；失敗保留舊資料 |
| 單 ASN 上百 range | 批次推送 + progress + lastError；timeout |
| 雙引擎 | 明確 deprecated AsnRule map |
| GitHub 不可達 | failed + 重試；不清空 AsMeta |
| 誤封雲 | 預設 Light；Strict 候選需確認 |

## 10. 已鎖定決策索引

| 決策 | 值 |
|------|-----|
| 作法 | 情報中樞 + CrowdSec 執行 |
| 表存法 | P 正規化 |
| 名錄/意圖 | S1 兩表 |
| 檔位 | light / manual / strict 互斥 |
| 切 Light | Z 詢問 |
| 無 CS | D1 |
| 誤封 | CS 為準 + 可選 DenyAllow |
| DB 實務 | PostgreSQL 優先索引；SqlHelper 共用 schema |
| 全量 | AsMeta 全量；prefix 不進全量 |

## 11. 外部來源網址（實作必用，不得遺漏）

> 實作時請收成 **Java 常數**（建議 `AsnSourceUrls` 或寫在 Service 頂部 `public static final`），禁止魔法字串散落。  
> Lens 僅供人工查詢文件連結；**runtime 只打 raw.githubusercontent.com / releases**。

### 11.1 文件與產品入口（UI 說明／關於可連）

| 用途 | URL |
|------|-----|
| Lens 查詢前端 | `https://lens.ipverse.net/` |
| as-metadata 倉庫 | `https://github.com/ipverse/as-metadata` |
| as-ip-blocks 倉庫 | `https://github.com/ipverse/as-ip-blocks` |
| ipverse tools（CrowdSec 大封參考） | `https://github.com/ipverse/tools` |
| tools CrowdSec README | `https://github.com/ipverse/tools/blob/main/crowdsec/README.md` |
| country-ip-blocks（本階段非主路徑，文件備註） | `https://github.com/ipverse/country-ip-blocks` |
| geo-ip-blocks（本階段非主路徑，文件備註） | `https://github.com/ipverse/geo-ip-blocks` |
| as-overlay（metadata 補正，非 runtime 必拉） | `https://github.com/ipverse/as-overlay` |

### 11.2 AsMeta 全量同步（runtime）

| 用途 | URL | 實作 |
|------|-----|------|
| 全量 JSON（含 category / networkRole）**第一版主用** | `https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json` | `AsnMetaService.META_JSON_URL` |
| 全量 CSV（輕量 4 欄，無 category）**備援／可選** | `https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv` | `AsnMetaService.META_CSV_URL` |

### 11.3 大封時 prefix 拉取（runtime，按 ASN 現拉）

路徑中 `{asn}` 為**純數字**（無 `AS` 前綴），例 Contabo = `51167`。

| 用途 | URL 模板 |
|------|----------|
| IPv4 聚合列表 | `https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/ipv4-aggregated.txt` |
| IPv6 聚合列表 | `https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/ipv6-aggregated.txt` |
| JSON（v4+v6+metadata，可選） | `https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/aggregated.json` |
| 全庫打包（本階段不預設全量進庫；僅文件／災備） | `https://github.com/ipverse/as-ip-blocks/releases/latest/download/as-ip-blocks.tar.gz` |

**注意：** 舊 repo 名 `asn-ip` 已更名 `as-ip-blocks`。ipverse/tools 腳本仍可能寫  
`https://raw.githubusercontent.com/ipverse/asn-ip/master/as/$1/...` — **本專案實作一律用 `as-ip-blocks`**，不要抄舊路徑。

### 11.4 HTTP 客戶端約定

- `User-Agent`: `nginxWebUI/AsnMeta-sync`（名錄）、`nginxWebUI/AsnBlock`（prefix）
- timeout: 同步 JSON 建議 ≥ 120s；單 ASN prefix ≥ 30s
- 跟隨 redirect（Hutool `setMaxRedirectCount(5)`）
- 授權: 資料 CC0；無需 API key

### 11.5 本機／既有（非 ipverse，但實作會碰到）

| 用途 | 來源 |
|------|------|
| CrowdSec LAPI | 設定 `crowdsecUrl` + `crowdsecApiKey`（既有） |
| GeoLite2 ASN MMDB | 既有 `GeoipService`（執行期 IP→ASN 查詢，與名錄分離） |

## 12. 實作計畫

見 `docs/superpowers/plans/2026-08-12-ipverse-asn-crowdsec-plan.md`。  
Plan 內 **Global Constraints** 與 **Task 0 / Source URL constants** 必須與 §11 一致。
