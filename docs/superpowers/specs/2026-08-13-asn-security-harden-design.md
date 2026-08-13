# ASN / CrowdSec security harden — Design Spec

日期: 2026-08-13  
分支: `feat/asn-security-harden`（自本機 `dev`）  
狀態: 定案（審計建議 + brainstorming）

## 1. 背景

Phase 1 ASN 名錄／CrowdSec 已合入 `dev`。代碼審計指出：已登入 admin 下 LAPI 面過寬、prefix 未 CIDR 校驗、duration 未校驗、ASN 正規化不一致、錯誤訊息可能外洩。

## 2. 目標

1. `banRange`／推送前 **CIDR 白名單**  
2. 依 reason 刪除／撤銷時強制 **`nginxwebui:` 前綴**  
3. **duration** 格式校驗  
4. ASN 一律 **`AsnSourceUrls.digits` 正規化**  
5. API 錯誤回 **固定 code / i18n**，細節只 log  

## 3. 非目標

- 全量 AsMeta streaming／batch upsert  
- push 非同步 job  
- suggestCandidates 架構重寫  
- 限制 `crowdsecUrl` SSRF（admin 自架邊界，本輪不擴）  

## 4. 架構（作法 B）

校驗下沉共用 util + `CrowdSecClient` 入口硬閘；Service 正規化 ASN 並過濾 feed 行。

```
Controller (admin session)
  → AsnBlockService / CrowdSecController
    → normalize ASN, filter lines, validate duration
    → CrowdSecClient (hard gates: CIDR, duration, reason prefix)
      → LAPI
```

## 5. 規則

### 5.1 CIDR

- IPv4: `a.b.c.d` 或 `a.b.c.d/0-32`（每段 0–255）  
- IPv6: 標準壓縮寫法 + 可選 `/0-128`  
- 拒絕：空白、`all`、含空白／控制字元、明顯非網段字串  

### 5.2 duration

- 空 → 呼叫端既有預設（intent `24h`、whitelist `4h`）  
- 非空：`^\d+[smhdwMy]?$`  

### 5.3 reason（刪除／依 tag 撤銷）

- 必須 `startsWith("nginxwebui:")`  
- 推送 reason 仍為 `nginxwebui:as-ban:AS{digits}`  

### 5.4 ASN

- `addIntent` / `reasonTagForAsn` / fetch：先 `AsnSourceUrls.digits`（支援 `AS` 前綴）  

### 5.5 錯誤

- 校驗失敗：`IllegalArgumentException` + 固定 code（如 `invalid_cidr`、`invalid_duration`、`invalid_reason`、`invalid_asn`）  
- LAPI／網路：log 全文；前端 `crowdsec_error` 或既有 notConfigured  

## 6. 檔案

| 檔 | 責任 |
|----|------|
| `src/main/java/com/cym/utils/NetGuard.java` | 純靜態：isValidCidr、isValidDuration、isAllowedWebuiReason |
| `CrowdSecClient.java` | postDecision / ban* / deleteByReason* 硬閘 |
| `AsnBlockService.java` | digits 正規化；fetch 後 filter CIDR；duration 預設 |
| `AsnController` / `CrowdSecController` | mapServiceError；不回 e.getMessage() |
| `NetGuardTest.java` 等 | 邊界單元測 |
| `messages*.properties` ×3 | 新錯誤字串 |

## 7. 測試

純單元，無真 LAPI：合法／非法 CIDR、duration、reason 前綴、ASN `AS51167`→`51167`。

## 8. 計畫

見 `docs/superpowers/plans/2026-08-13-asn-security-harden-plan.md`。
