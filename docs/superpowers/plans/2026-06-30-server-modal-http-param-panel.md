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

---

## Phase 2 Backlog(2026-06-30 brainstorm 結果,user 決定先上線 prototype,不繼續寫 design spec / writing-plans)

User 提的關鍵 design 觀念(留底,未來補強用):

**1. Modal 內容 = Picker(非編輯器)— user 想要的是「減少打字、不背 nginx 語法」**
- 取自 user 截圖「參數模板」概念:常用語法預先準備,user 勾選就好。
- 並非 raw nginx 指令編輯器。

**2. 指令分三種 mode(group metadata)**
- `locked` — 核心不可關,如 Real IP(關了 GeoIP/log 都廢)、`default_type`。UI:checkbox disabled + 鎖頭 icon + tooltip。
- `mutex` — 建議互斥,如 GeoIP country/city/asn 三選一(同時全開太吃重)。UI:warn-only,不強制 enforce(尊重 power user)。
- `optional` — 純 toggle,如 gzip/brotli/X-Frame-Options。

Group metadata 來源建議:InitConfig 寫死 `groupName → mode` 對照表,避免 schema change。

**3. http 段本質是全域 — 不做 per-server override**
- nginx 慣例:上層 inherit / 下層覆寫;要 per-server 例外應該去 ②③④(server / location / proxy 段)做覆寫。
- ① http modal 的存在價值 = **server modal 內快速調整全域 http 設定的捷徑**,save = update Http 表 enable flag(全域影響所有 server)。

**4. Save 後驗證**
- nginx -t 預檢(現有 ConfService 是否已有此 flow 需確認)
- 失敗 rollback + 顯示錯誤

**5. Module availability filter(phase 3+)**
- 某些 user 跑自 build nginx 可能沒裝某 module(如 stream / brotli)
- Modal 內對應指令灰掉 + tooltip「你 nginx 沒此 module」
- 來源:`/adminPage/monitor/nginxInfo` 已有 nginx -V 解析的 modules list

**6. Stream 段擴展(out of scope this round)**
- nginxWebUI 已有獨立 `/adminPage/stream` 頁(對應 `ngx_stream_core_module`)
- production Docker image 含 `nginx-mod-stream*` 5 個 packages 完整支援
- 未來可考慮把 ①②③④ toolbar 設計套到 stream server modal,但結構不同(stream 無 location / 無 server_name domain)
- 本期不做,審計或需求觸發再評估

**7. 上線決策(user 2026-06-30)**
> 「這一論目標先上線,補強後面再跟進,如果有遇到審計部符合,則退回再修」

也就是 commit `d1367718`(prototype)+ `5ae9685a`(XSS escape fix)是 phase 1 完整範圍。Phase 2 待觸發。
