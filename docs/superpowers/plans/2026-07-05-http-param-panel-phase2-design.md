# Server modal — ① http 參數 panel Phase 2:存檔(全域 enable + nginx -t 預檢)

**日期:** 2026-07-05
**狀態:** 設計批准,待寫 implementation plan
**分支:** feature/http-param-phase2(base = dev)
**前置:** Phase 1(prototype + i18n + count bugfix)已上線;本設計實作 Phase 1 backlog 的核心。

---

## 背景

Phase 1 的 http 參數 panel(server add-proxy modal 的 ① 按鈕)是 UI prototype,`saveHttpParamPanel()` 只 `console.log` + toast placeholder,不接後端。Phase 2 讓「存檔」真正生效。

Phase 1 backlog(見 [2026-06-30-server-modal-http-param-panel.md](2026-06-30-server-modal-http-param-panel.md))提了 7 點;本輪聚焦核心 MVP,其餘留 Phase 3。

## 設計決策(brainstorming 定案)

1. **save 語意 = 全域捷徑**:panel 開在 server modal 內,但 Http 表是全域的(無 server 關聯)。save = update Http 表的 `enable` flag,影響所有 server 共用的全域 http block。**不做 per-server override**(符合 nginx `http { server { } }` 繼承模型;要 per-server 例外應去 server/location 段)。UI 需明示「這是全域設定」。
2. **範圍 = MVP**:save(全域 enable)+ nginx -t 預檢。三態 mode(locked/mutex/optional)、module availability filter、stream 擴展、自動 reload 全留 Phase 3。
3. **save 後行為 = 只存 DB + 預檢,不自動 reload**:符合 nginxWebUI 既有模式(改任何設定只存 DB,使用者統一去「啟用配置」頁 review → replace → reload)。

## 現況(codegraph 探索確認)

- [Http.java](../../../src/main/java/com/cym/model/Http.java):已有 `enable`(Boolean, `@InitValue("true")`)。
- [ConfService.buildConf:126-129](../../../src/main/java/com/cym/service/ConfService.java#L126):生成 conf 時 `if (enable == null || !enable) continue;` — **enable=false 的 http param 已不進 conf**。所以後端 conf 生成無需改,只要 update enable。
- [ConfController.check:212-259](../../../src/main/java/com/cym/controller/adminPage/ConfController.java#L212):既有 nginx -t 預檢流程(寫臨時檔 `home/temp/nginx.conf` → `nginx -t -c <tmp>` → 看 "test is successful")。Phase 2 復用此邏輯。

## 資料流

```
panel「存檔」→ saveHttpParamPanel() 收集勾選的 httpParamItem ids
  → POST /adminPage/http/saveEnable  { checkedIds: "id1,id2,..." }
      ↓ HttpController.saveEnable (synchronized)
  1. 讀所有 Http,記錄舊 enable 狀態(Map<id,Boolean>,rollback 用)
  2. update:id ∈ checkedIds → enable=true;其餘 → enable=false
  3. nginxExe 已設 → ConfService.precheckConf()(buildConf → 臨時檔 → nginx -t):
       ✗ 失敗 → rollback 全部 enable → 回錯(帶 nginx 訊息)
       ✓ 成功 → 保留 → 回成功
     nginxExe 未設 → 跳過預檢(照存),回成功但註明「未預檢」
  → 前端:成功 layer.msg「已存,請至『啟用配置』頁套用」;失敗 layer.alert(nginx 錯誤)
```

## 元件(檔案級)

1. **後端 endpoint** — [HttpController](../../../src/main/java/com/cym/controller/adminPage/HttpController.java) 加 `saveEnable(String checkedIds)`。放 HttpController(操作 Http 表,語意最合);panel 在 server 頁但 ajax 走絕對路徑無妨。`synchronized`。
2. **預檢 helper** — [ConfService](../../../src/main/java/com/cym/service/ConfService.java) 加 `precheckConf()`:buildConf → 寫 `home/temp/nginx.conf`(復用 `replace(fileTemp, ..., isReplace=false)`)→ `nginx -t` → 回 `{ok, msg}`。抽出 ConfController.check 的核心邏輯以便共用。
3. **前端** — [server/index.js](../../../src/main/resources/static/js/adminPage/server/index.js) `saveHttpParamPanel()` 從 placeholder 改真 ajax(POST checkedIds → 處理成功/失敗 layer)。
4. **UI 提示** — panel 加「此為全域 http 設定,存檔會套用到所有 server」(i18n 三語,對應 backlog #3)。新 i18n key `serverStr.httpParamGlobalHint`。

## Error handling

- **rollback**:update 前存舊 enable map;nginx -t 失敗逐一 `setEnable(舊值)` + `updateById` 還原。
- **nginxExe 未設**:跳過預檢照存(enable 是 DB 設定,與 nginx 是否安裝無關)。這也是 E2E 測試環境的路徑(測試不裝 nginx)。回成功但 msg 註明未預檢。
- **並發**:`saveEnable` synchronized,避免與其他 build conf 動作競爭臨時檔。

## 測試(新增 tests/e2e/29-http-param-save.spec.js)

1. 開 panel → 取消一項 → 存檔 → 重開 panel 確認該項未勾(enable 已落 DB)。
2. 測試環境 nginxExe 未設 → 走「跳過預檢」路徑,存成功 + toast。
3. 驗證「全域設定」提示存在。

## i18n(三語新增,對應 CLAUDE.md 核心原則 #2)

- `serverStr.httpParamGlobalHint` — 全域設定警語
- `serverStr.httpParamSaved` — 「已存,請至啟用配置頁套用」
- `serverStr.httpParamPrecheckSkipped` — 「已存(nginx 未設定,略過預檢)」
- 失敗訊息復用既有 nginx -t 錯誤輸出

## 風險

- **臨時檔競爭**:precheckConf 用 `home/temp/nginx.conf`,與 ConfController.check 同路徑。synchronized + 各自 build 前清理可緩解;或用唯一臨時檔名。
- **rollback 原子性**:update 是逐筆;若 rollback 中途失敗,DB 可能半新半舊。緩解:先全部算好新舊值,nginx -t 通過才 commit(但現有 update 即時寫)。MVP 接受逐筆 rollback(失敗機率低,且下次存檔會覆蓋)。
- **buildConf 成本**:每次存檔 build 全 conf。可接受(存檔非高頻)。

## Out of scope(Phase 3)

- 三態 mode(locked/mutex/optional)+ group→mode metadata
- 自動 reload、module availability filter、stream 段擴展
- http 頁殘留的 batchInputDiv/parseBatchInput 死 code 清理(獨立 backlog)
