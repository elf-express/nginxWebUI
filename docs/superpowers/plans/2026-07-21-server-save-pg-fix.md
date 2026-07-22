# 修復 /adminPage/server 存檔失效（PG 型別衝突）＋ denyAllow 全站自動生效重設計（2026-07-21）

## Context

線上 Docker 部署（PG18 stack，程式碼 = dev HEAD，已用 MD5 比對 live JS 證實）症狀：列表看得到、修改存不進去、存檔會掛掉。

根因（高信心）：PostgreSQL 型別衝突使所有寫入失效。
- 自製 ORM 把所有欄位建成 TEXT（SqlUtils.checkOrCreateColumn），但 insert/update 直接綁 Java 原生型別；BaseModel 有 Long createTime/updateTime，所以 PG 上每一筆 insert 都炸 42804（不只 Server）——包括 InitConfig 種子。SQLite 動態型別無感，E2E 只跑 SQLite 所以全綠。
- JdbcTemplate.execute (112-120) 吞掉 SQLException 只寫 log → UI 假成功、資料沒存（＝「修改不能修改」）；SqlHelper.insertOrUpdate 另一路徑包 RuntimeException → 500（＝「存檔掛掉」）。
- 附帶挖出既有 semantic bug：ConditionWrapper.build() 把查詢值 .toString()（eq(enable,true) 綁 'true'），但寫入路徑 sqlite-jdbc 存的是 "1" → geo 規則 eq("enable", true) 在 SQLite 上也永遠查不到（ConfService:795/801）。
- 次要：http-param 面板儲存跑 nginx -t 無 timeout 且 saveEnable synchronized → 卡住會讓所有後續儲存永久轉圈。
- 潛在 NPE：ConfService.buildDenyAllow:546/571 對 server.getDenyAllow() 拆箱。

測試結構性缺口：沒有任何 spec 呼叫過 /adminPage/server/addOver；全套 E2E 只跑 SQLite。

使用者三個決定：(1) denyAllow 綁定 UI 殘留徹底移除；(2) 驗證後直接發版 5.2.6；(3) 設計以用戶立場出發——改為「防護頁維護的黑白名單自動全站生效，零綁定操作」（兌現「發版自帶預設黑名單、用戶不用自己弄」——目前種子 6 條規則因全域開關預設 0，出廠沒在擋）。

## 實作步驟

### 1. 先重現，確認 42804
PG18 容器（port 15432）+ jar 以 postgresql 型別啟動（port 18081，testCaptcha）→ Playwright 登入、新增 proxy、存檔 → 預期 UI 假成功＋log 42804。同時驗證 SQLite Boolean 存 "1"/"0" 且 @InitValue backfill 留 "true" 混雜。

### 2. ORM 綁定正規化（單一正準形：所有參數 String 或 null）
normalize(Object)：null→null；Boolean→"1"/"0"；Number→String.valueOf；String 原樣；其他→toString()。
- JdbcTemplate.queryForList/execute 綁參前 normalize（最低 choke point）。
- ConditionWrapper.build() 三處 toString() 改 normalize（修 eq(enable,true) 潛在 bug；LIKE 保留 %% 包裹）。
- SqlHelper.updateMulti 同改。
- TableUtils.initTable：Boolean 欄位 @InitValue 轉 "1"/"0" 再 backfill；新增幂等啟動 migration：UPDATE t SET c='1' WHERE c='true' / '0' WHERE c='false'。
- 讀取路徑不改（hutool Convert 都能轉）。

### 3. SQLException 不再靜默（DDL/DML 分流）
- JdbcTemplate.execute 改 log + throw RuntimeException。
- 新增 executeQuietly() 只給 SqlUtils 三個 best-effort DDL（checkOrCreateTable/Index/Column）。
- updateDefaultValue 與 Boolean migration 走嚴格版。

### 4. denyAllow 重設計：中央規則全站自動生效
- ConfService.buildDenyAllow 重寫：http 與 stream 層級無條件套用中央規則——先 allow（type=allow）再 deny（type=deny），不加 deny all;（預設放行、白名單覆蓋黑名單）。捨棄 Settings CSV 模式（資料列保留）；刪除 server 分支（DB 欄位保留不讀）。
- 移除三頁綁定 UI 與後端注入（server/http/stream 的死對話框、隱藏欄位、setDenyAllow 系列 JS、controller denyList/allowList 注入與 get/setDenyAllow endpoints）。
- 防護頁黑白名單 tab 加「規則自動全站生效」說明標籤；新字串同步三份 messages*.properties。
- 行為變更寫進 release notes。

### 5. nginx -t timeout 防呆
ConfService.precheckConf：RuntimeUtil.execForStr 改 exec + waitFor(15s) 逾時 destroyForcibly 回報 timeout。

### 6. 測試
- 新 tests/e2e/33-server-save.spec.js：新增→存檔（斷言 addOver success）→重載斷言持久→編輯→enable 切換→清理；斷言 toolbar 無黑白名單按鈕。
- 修 32-firewall-ip-tabs 測試⑤（server 頁 #denyDiv/#allowDiv 已移除）；grep 同步其他引用。
- PG smoke：helpers.js 支援 E2E_DB=postgresql；playwright.pg.config.js（testMatch 01+33、globalSetup 起 PG 容器）；npm run test:pg。CI 不動。

### 7. 文件同步
CLAUDE.md Feature Inventory DenyAllow 子句改中央全站自動生效、Testing 範圍 01–33；README 如有提及一併改。

### 8. 驗證矩陣 → 發版
修復前重現 42804 → 全套 E2E (SQLite) 33 spec 綠 → PG smoke 綠 → geo 規則出現在 conf → 全站黑白名單自動出現在 conf → docker compose dev overlay 全 stack Playwright 實測 + 容器重啟資料仍在 + logs 無 42804 → release.sh 5.2.6 → push dev + dev:master → manifest inspect 確認 image。

升級步驟（192.168.25.100）：cd docker && docker compose pull && docker compose up -d。既有 PG volume 因寫入全失敗形同空庫，首次啟動重跑種子＋管理員精靈；種子黑名單自動全站生效。

## Second-opinion 審查結論（2026-07-22）

無 CRITICAL。FIX-FIRST 兩項已修：(1) collectIps 逐行嚴格 IPv4/IPv6/CIDR 驗證（含拒絕 /0 與前導零），防單行壞 feed 資料重現全站存檔死鎖；(7) 升級型安裝補設 denyAllowSeeded flag。另隨手修：precheck 輸出改背景執行緒邊讀邊等（防 pipe buffer 卡死）、InterruptedException 還原旗標、首抓寫回前 findById 防復活 race、preview include 檔名對齊實際（deny_http.conf）、種子 include 改用 GeoipService.GEOIP_DIR 常數、E2E 加 --project.skipSeedFetch 不打外部 feed、刪 addCountById 死碼（PG 上必炸、零呼叫者）、PG smoke docker rm 加 -v。

## 後續事項（不擋 5.2.6）

1. 全站 deny 清單量級：6 feed 約 4-6 萬條 deny 指令為線性比對，每請求皆有成本、conf 約 1-2MB。後續可改 geo map（hash 查找）。
2. precheck 相對 include 以 live prefix 解析：首次 fetch 後、首次 apply 前，temp conf 的 include 指向線上 conf.d 尚不存在的檔 → 預檢誤報一次（apply 後自癒）。
3. hasStream=false 時 deny_stream.conf 仍寫入 conf.d（orphan 無害雜物）。
4. setAsycPack 匯入在 strict execute 下失敗中止後續表且外層吞錯 → 建議 catch 回報。
5. 孤兒 i18n key 清理（denyAllowStr.usedBy、serverStr.denyAllowModel/ipDenyAllow/blacklist/whitelist/黑白模式系列）。

## 明確不改
- DB 欄位不刪（Server.denyAllow/denyId/allowId、Settings CSV 列保留）。
- SqlHelper.addCountById 死碼不動（後續清理）。
- ConfController 其餘 execForStr、CI workflow。
