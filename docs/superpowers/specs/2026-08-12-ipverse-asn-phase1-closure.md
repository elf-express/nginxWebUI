# ipverse ASN — Phase 1 closure (Light + catalog / API)

日期: 2026-08-12  
分支: `feat/fixbug`  
狀態: **本階段完成（可合併）**

## 本階段範圍（ship）

| 能力 | 狀態 |
|------|------|
| `AsnSourceUrls` 全部來源網址 | Done |
| AsMeta 全量名錄同步 + 分頁搜尋 | Done |
| 三檔 light / manual / strict（互斥，預設 light） | Done |
| Z 切回 light（keep / revoke；revoke 先於改 profile） | Done |
| CrowdSec range 推送／撤銷／白名單（程式備好） | Done |
| AsnRule map 預設關 + 遷移 intent=pending | Done |
| 防護中心 UI + i18n×3 + E2E smoke | Done |
| 名錄 catalog limit 上限 100 | Done |
| 手動／排程 **共用** sync single-flight | Done |
| suggestCandidates 安全上限 50 + capped 回傳 | Done |
| Phase 1 / bulk 警告橫幅 | Done |

**產品定位：**

- **Light + 名錄／API**：本階段正式可用。  
- **Manual 單 ASN 推送**：可用但屬實驗；大 prefix 集合可能逾時／部分成功。  
- **Strict 大批量**：有 cap 與警告，**不宣傳為生產級大封**。

## Post-merge 硬化（下一階段，不在本 PR 必做）

1. AsMeta 同步：streaming JSON + batch upsert + 建議 heap  
2. pushIntent：非同步 job + 進度 + CIDR 嚴格驗證  
3. revoke：LAPI 殘餘 re-scan 後再標 local revoked  
4. suggestCandidates：索引化 / 更高 cap 策略  
5. E2E：Z 對話、manual flip、push mock  
6. 可選：shared progress UI for sync  

## 相關文件

- Design: `docs/superpowers/specs/2026-08-12-ipverse-asn-crowdsec-design.md`  
- Plan: `docs/superpowers/plans/2026-08-12-ipverse-asn-crowdsec-plan.md`  
- Post-impl review: `.superpowers/sdd/2026-08-12-ipverse-asn-crowdsec-plan/post-impl-code-review.md`  
