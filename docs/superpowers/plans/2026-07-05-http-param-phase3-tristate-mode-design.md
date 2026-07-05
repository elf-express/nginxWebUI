# http 參數 panel Phase 3 子項一:指令三態 mode(locked / mutex / optional)

**日期:** 2026-07-05
**狀態:** 設計批准,待寫 implementation plan
**分支:** feature/http-param-phase3(base = dev 81b68bd5,含 phase 2)
**前置:** Phase 2(save 全域 enable + nginx -t 預檢)已上線。本設計是 Phase 3 backlog 的第一個子項(其餘:自動 reload / module availability filter / stream 擴展 各自另輪)。

---

## 背景

Phase 2 的 http 參數 panel 每個指令都是純 toggle(可任意勾/取消 → update Http.enable)。但有些指令是核心(關了會連鎖失效),有些建議互斥。三態 mode 讓 panel 依 group 分級:

- **locked** = {base, realip}:核心不可關。base=mime.types/default_type(靜態檔 Content-Type 依賴)、realip=Real IP(關了 GeoIP/log 全廢)。
- **mutex** = {geoip}:country/city/asn 同時全開較吃記憶體(各自 auto_reload mmdb),建議三選一,但 **warn-only 不強制**(尊重 power user)。
- **optional** = {gzip, brotli, headers, proxy, logging}:純 toggle(維持 phase 2 現狀)。

## 設計決策(brainstorming 定案)

1. **mode 對應**:locked={base,realip} / mutex={geoip} / 其餘 optional。
2. **locked = 後端 enforce + 前端 disabled**:前端 checkbox disabled + 鎖頭是 UX;真正保證在後端 —— saveEnable 強制把 LOCKED_GROUPS 的 items 設 enable=true,不管前端送什麼(防 API 繞過關掉 Real IP)。
3. **mutex = 存檔時 warn 確認**:同一 mutex group 選 >1 個 → 存檔前 `layer.confirm` 提示「建議三選一,同開較吃記憶體,確定繼續?」,確認後照存,不阻止。
4. **單一真相來源**:group→mode 對照放 HttpController public static(LOCKED_GROUPS / MUTEX_GROUPS),saveEnable 與 ServerController render 共用。

## 現況(codegraph 確認)

- [HttpController.GROUP_DEFS](../../../src/main/java/com/cym/controller/adminPage/HttpController.java#L44):8 個 group(base/realip/geoip/gzip/brotli/headers/proxy/logging),已是 `{groupName, i18n名, i18n描述, moduleNote}` 4 欄。
- [Http.java](../../../src/main/java/com/cym/model/Http.java):有 `groupName`。panel `#list httpList as h` 每個 h 有 groupName。
- [HttpController.saveEnable](../../../src/main/java/com/cym/controller/adminPage/HttpController.java#L240)(phase 2):checked → enable=true/false + nginx -t 預檢 + rollback。三態在此基礎上加 locked enforce。
- panel checkbox 目前無 group 標記。

## 元件(檔案級)

1. **[HttpController](../../../src/main/java/com/cym/controller/adminPage/HttpController.java)** — 加 `public static final Set<String> LOCKED_GROUPS = Set.of("base","realip")` + `MUTEX_GROUPS = Set.of("geoip")`。
2. **saveEnable enforce** — 算完 checked 後,把 LOCKED_GROUPS 內所有 http item 強制加入「enable=true」集合(不管前端送不送)。實作:`for (Http h : httpList) { boolean want = checked.contains(h.id) || LOCKED_GROUPS.contains(h.groupName); ... }`。
3. **[ServerController](../../../src/main/java/com/cym/controller/adminPage/ServerController.java)** — render server add-proxy 頁時 `modelAndView.put("lockedGroups", HttpController.LOCKED_GROUPS)` + `mutexGroups`。
4. **[server/index.html](../../../src/main/resources/WEB-INF/view/adminPage/server/index.html) panel** — checkbox 加 `data-group="${h.groupName!''}"`;`<#if lockedGroups?seq_contains(h.groupName!'')>` → `disabled` + 鎖頭 icon(layui-icon-password 或類似)+ `title` tooltip。
5. **[server/index.js](../../../src/main/resources/static/js/adminPage/server/index.js) saveHttpParamPanel** — 送出前掃 mutex:對每個 mutexGroup 統計 `input[data-group=g]:checked` 數,>1 則 `layer.confirm` warn,確認才續送(取消則中止)。
6. **i18n(三語)** — `serverStr.httpParamLockedTip`(鎖頭 tooltip)、`serverStr.httpParamMutexWarn`(互斥 confirm 訊息)。

## 資料流

```
server 頁 render(pass lockedGroups / mutexGroups)
  → panel checkbox:locked → disabled + 鎖頭 + tooltip;全部帶 data-group
  → 使用者勾選 → 點存檔
  → saveHttpParamPanel:掃 mutexGroups,若某 group 選 >1 → layer.confirm(warn)
       取消 → 中止;確認 → 送 checkedIds
  → saveEnable:want = checked.contains(id) || LOCKED_GROUPS.contains(groupName)
       → 強制 locked enable=true → nginx -t 預檢 → rollback(phase 2 既有)
```

## Error handling

- locked checkbox disabled+checked:`:checked` 仍收集(disabled 不影響 :checked),送出含 locked;即使前端漏送,saveEnable 的 `|| LOCKED_GROUPS.contains` 補回。雙保險。
- mutex confirm 取消 → 不送(中止存檔),panel 保持開啟。
- 空 mutex group / 單選 → 不 warn。

## 測試(新增 tests/e2e/30-http-param-mode.spec.js)

1. **locked 前端**:base/realip group 的 checkbox `disabled` 且有鎖頭 icon。
2. **locked 後端 enforce**:`page.evaluate` 直接 POST `/adminPage/http/saveEnable` 帶「不含任何 locked id」的 checkedIds → 重開 panel,locked items 仍勾(enable=true)。這是安全核心。
3. **mutex warn**:geoip group 勾 >1(country+city)→ 點存檔跳 `layer.confirm`;取消則不存。
4. **optional**:gzip 之類正常 toggle 存檔(不受 mode 影響)。

## i18n keys(三語)

- `serverStr.httpParamLockedTip` — 例:核心指令,不可停用(關閉會連鎖影響 GeoIP / 日誌等)
- `serverStr.httpParamMutexWarn` — 例:同類指令建議擇一啟用(同時開啟較耗記憶體),確定要一起套用嗎?

## 風險

- **ServerController 依賴 HttpController public static**:controller 互相引用常數可接受(單一來源優於複製);若想更乾淨可移到 HttpService,但 YAGNI,先放 HttpController。
- **Freemarker `?seq_contains`**:確認 lockedGroups 以 Set/List 傳入時 Freemarker built-in 可用;若型別問題改傳 List。
- **鎖頭 icon**:用 layui 內建 icon class(離線,不引外部)。

## Out of scope(其他 phase 3 子項,各自另輪)
自動 reload、module availability filter、stream 段擴展、phase 2 遺留 Minor hardening(null-列 rollback / temp 競態 / 空 checkedIds)。
