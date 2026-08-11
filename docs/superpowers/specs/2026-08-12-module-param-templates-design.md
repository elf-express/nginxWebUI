# 模組參數模板擴充 + 模板編輯 UI

**日期:** 2026-08-12  
**狀態:** Approved direction from user (group dropdown, seed all optional community packs, annotations)

## Goals

1. 參數模板新增/編輯：補「分組」下拉（對齊 CORS 等頂級 collapse 標題）。
2. 參數 name/value 輸入高度改約 40px（原 ~100px 過高）。
3. 為已安裝之開源模組（**不含**舊版 geoip v1）seed 社群慣用參數模板；`def` 預設空＝僅手動套用；名稱內含建議註解。
4. 不強制引用：模板庫可選用，模組仍須在「模組管理」開啟才 load_module。

## Non-goals

- 舊 `ngx_http_geoip` / `ngx_stream_geoip` 模板。
- 修改既有四個 rateLimit 的 **param 值**。
- 一次展開各模組全部指令（只做社群常用子集）。

## UI

- 彈窗欄位序：分組 → 模板名稱 → 自動套用到 → 參數表。
- 分組 options = `GROUP_DEFS` + 可選「自訂」填字串。
- `#paramList textarea` height/min-height 40px。

## Template groups (new)

proxy, cache, cors, rateLimit, security, geoip, crowdsec（既有）  
+ observe, auth, njs, keyval, util, media, upload, realtime, waf, compress, upstream_ext, mail

## Seed policy

- 名稱不存在才 insert（migration flag `moduleCommunityTemplatesSeeded`）。
- 值參考 nginx.org / 模組 README 常見寫法；名稱標「建議 / 需模組 / 層級」。
- stream zone 繼續用 `s_` 前綴隔離。

## Success

- 新增模板可選分組並正確落入 collapse。
- 參數列高度約 40px。
- 新裝/升級後參數模板頁可見各分組社群範本；舊模板參數不變。
