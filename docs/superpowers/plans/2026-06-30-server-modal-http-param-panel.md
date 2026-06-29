# Server modal — ① 設置 http 參數 panel (60/40 split + terminal preview)

**日期:** 2026-06-30
**狀態:** 批准執行
**觸發:** user 連續對話確認「分開設置 → 先 http → 預設打勾 → 60/40 split → 右邊仿終端機」

---

## Goal

把添加反向代理 modal 的 toolbar 從現有「① Location ② http ③ server ④ proxy」**重排**為 **「① http ② Location ③ server ④ proxy」**(nginx conf 結構順序:`http { server { location { proxy_* } } }`)。

點 ① 開新 modal「設置 http 參數」:
- **左 60%**:checkbox 列表,把 `/adminPage/http` 的全域 http 參數(InitConfig 初始 16 項 + 用戶自訂)都列出,**預設全勾**(對應「預設已有設定」)。user 可取消勾要排除的。
- **右 40%**:**dark area + 存檔 button**(視覺參考 `/adminPage/conf` 啟用配置頁的 CodeMirror dark 樣式)。**不做 live preview** — user 連續訊息「不用預覽 只有存檔」。右邊只顯示「將存 X 個指令」訊息 + 「存檔」+「關閉」button。

提交邏輯本期**不接後端**(存哪、影響什麼 conf 生成、phase 2 spec)— 此 commit 範圍只到 UI prototype + 存檔 button 是 placeholder。

## Architecture

```
server modal toolbar (reordered: ① http ② Location ③ server ④ proxy)
  [① 設置http參數] -click-> openHttpParamPanel()
                              |
                              v
   ┌─────────── new modal (layer.open type:1) 90vw 70vh ───────────┐
   │ ┌─ 60% left (checkbox list) ─┐ ┌─ 40% right (dark area) ─────┐│
   │ │ ☑ include  mime.types       │ │  # nginx 全域 http 段參數   ││
   │ │ ☑ default_type  application │ │                              ││
   │ │ ☑ proxy_headers_hash_max... │ │  將套用 X 個指令到此 server  ││
   │ │ ☐ access_log  log/access... │ │                              ││
   │ │ ...                         │ │  (本期不做 live preview)     ││
   │ │                             │ │                              ││
   │ │                             │ │                              ││
   │ │                             │ │  [ 存檔 ]    [ 關閉 ]        ││
   │ └─────────────────────────────┘ └─────────────────────────────┘│
   └─────────────────────────────────────────────────────────────────┘
```

Right pane 視覺 = `/adminPage/conf` 啟用配置頁那種黑底 + monospace 風格(`background:#1e1e1e; color:#d4d4d4; font-family:Consolas,Menlo,monospace`),但**內容不是 conf 預覽**而是「count + save button」action panel。

## Tech notes

- **資料來源:** 加新 endpoint `GET /adminPage/http/listJson` → JSON `[{id,name,value,enable}, ...]`。或直接由 ServerController 在 render add modal 時 pass `httpList` 進 Freemarker(避免新 endpoint)。**選後者**(template-passed list,jQuery 從 hidden JSON `<script type="application/json">` 讀)。
- **Terminal CSS:** vanilla CSS,`background:#1e1e1e; color:#d4d4d4; font-family:Consolas,Menlo,monospace; padding:12px; overflow:auto; white-space:pre;`(類似 VS Code dark)。**不引入新 lib**(no CodeMirror — 預覽純 text 不需 highlight)。
- **JS:** `static/js/adminPage/server/http-param-panel.js` 新檔。從 hidden JSON 讀 list,render checkbox + bind change event 即時更新 terminal pane。
- **i18n:** 加 `serverStr.httpParamPanelTitle`、`serverStr.previewHint` 3 份 properties。
- **打開方式:** `layer.open({ type: 1, area: ['90vw', '70vh'], content: $('#httpParamPanelDiv').html() })`,div 預藏在 server/index.html 內 `display:none` wrapper。

## File Structure

### 修改
- `src/main/resources/WEB-INF/view/adminPage/server/index.html` —
  - swap toolbar DOM order(① http 提到第一個 div.layui-inline)
  - 重編 number prefix(`①②③④`)
  - 新增 `<div id="httpParamPanelDiv" style="display:none">` modal HTML(內含 layui-row 60/40 split)
  - inline `<script type="application/json" id="httpListJson">${httpListJson}</script>`(讓 JS 讀)
- `src/main/resources/static/js/adminPage/server/index.js` —
  - 改 ① button onclick 從 placeholder → `openHttpParamPanel()`
  - 新增 `openHttpParamPanel()` 函式
  - 新增 `renderHttpPreview()` (依 checked 狀態更新 terminal pane)
- `src/main/java/com/cym/controller/adminPage/ServerController.java`(或對應 add modal render handler) — render add modal 時 inject `httpListJson`(JSON string of all http params)
- `src/main/resources/messages*.properties` × 3 — 加 2 i18n keys

### 不動
- 後端 conf 生成邏輯(本期 UI prototype 不接 save)
- HttpController(沿用,不加新 endpoint)
- DenyAllow / GeoIP / 其他全站 form

## Risks

- 中:Freemarker render JSON 字串需 escape(避免 `"` / `\n` 破壞 inline script)。用 `?json_string` Freemarker built-in。
- 低:layer.open 預設可能跟 add modal z-index 衝突;若覆蓋失敗,加 `zIndex: 999999999` override。
- 低:checkbox 列表項目多時(現有 16 + 用戶加的)需 scroll;left 60% pane 加 `overflow:auto`。

## Verification

- Playwright:open server add modal → click ① → new modal 出現 → left pane 列出 ≥ 16 checkbox,全勾 → right pane terminal 顯示同等行數 conf → 取消勾一個 → terminal 對應行消失。

## Out of scope(本期)

- 「設置 server 參數」「設置 proxy 參數」modal(③ ④ 還是 placeholder)
- 後端 save 邏輯(checkbox 取消後是否影響 conf 生成,phase 2 spec)
- CodeMirror nginx 語法 highlight(本期純 text preview;有需要再升級)
- 預覽範圍從「單行指令」擴展到「整段 server block」(本期只 http 段)
