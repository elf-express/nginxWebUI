# Stream + Upstream UI 排版重整 + 語法測試

**日期:** 2026-06-30
**狀態:** Plan skeleton — 排版細節等 user 截圖 + 標註
**Branch:** `feature/stream-upstream-rework`(worktree:`e:/nginxWebUI-stream-rework`)
**Triggered by:** user 對話「不夠 還有欠 Stream 參數配置 負載均衡(upstream),這次 UI 修改包含語法測試」+「這幾頁 UI 排版都有問題」

---

## Goal(待 user 補)

修 `/adminPage/stream` 與 `/adminPage/upstream` 兩個頁面的「排版問題」(細節 TBD),並三個頁面(包含 `/adminPage/server`)統一加入 nginx -t 「語法測試」button。

## Scope

| 範圍 | 狀態 | 備註 |
|---|---|---|
| `/adminPage/stream` 排版整理 | TBD — 等 user 截圖 | 整頁 layout? toolbar? table? add modal? |
| `/adminPage/upstream` 排版整理 | TBD — 等 user 截圖 | 同上 |
| nginx -t 語法測試 button | 確認方向後加 | 加在三個頁面(server / stream / upstream) |
| toolbar cleanup(沿用 http 頁那組) | 待確認 | 可能順帶拿掉「IP黑白名單 / 添加模板作為參數 / 批量刪除」(stream 頁也有同樣 button) |

## Tech Stack(沿用)

- Backend:Solon @Controller(`StreamController` / `UpstreamController`)
- View:Freemarker + Layui
- nginx -t API:**已存在** `ConfController @Mapping("check")`(`/adminPage/conf/check`)
  - 邏輯:接收 base64-encoded nginx content + sub configs → 寫 temp `nginx.conf` → 跑 `nginx -t -c <temp>` → 回 success/fail + cmd output
  - Server modal 套用:可包裝成簡化 API(預覽當前 server 對應 conf 片段 → 跑 check)或直接 reuse

## File Structure(TBD)

待 user 給排版細節後填:
- [TBD] `src/main/resources/WEB-INF/view/adminPage/stream/index.html`
- [TBD] `src/main/resources/WEB-INF/view/adminPage/upstream/index.html`
- [TBD] `src/main/resources/WEB-INF/view/adminPage/server/index.html`(可能加語法測試 button)
- [TBD] `src/main/resources/static/js/adminPage/stream/index.js`
- [TBD] `src/main/resources/static/js/adminPage/upstream/index.js`
- [TBD] `src/main/resources/messages*.properties` × 3

## 已知 context(prep work done)

**Stream 頁 toolbar 現狀**(`stream/index.html:38-54`)— 7 個 button:
1. 添加 stream(add)
2. 簡易配置向導(guide)
3. IP黑白名單(setDenyAllow) — 可能拿掉(同 http 頁邏輯)
4. 添加模板作為參數(selectTemplateAsStream) — 可能拿掉
5. 預覽(preview)
6. 批量刪除(delMany) — 可能拿掉

**Upstream 頁 toolbar 現狀**(`upstream/index.html:68-83`)— 4 個 button:
1. 添加 upstream(add)
2. upstream 監控(upstreamMonitor)
3. 批量刪除(delMany) — 可能拿掉
4. 搜尋(search)

**nginx -t flow** — `ConfController#check` 已實作完整,可重用。

## TBD(等 user 補)

- [ ] Stream 頁排版問題具體位置 + 截圖
- [ ] Upstream 頁排版問題具體位置 + 截圖
- [ ] 「語法測試」button UX:modal 內 save 前自動跑、顯式 button、page header
- [ ] toolbar 是否照 http 頁一樣 cleanup(stream / upstream 也有同樣 button 群)
- [ ] Server modal 是否同步加語法測試 button

## Verification(待 spec 完成後填)

- Playwright spec:點測試 button → 看 confController#check 回成功 / 失敗
- 視覺截圖比對排版前後

## Out of scope(預先排除)

- Stream / Upstream backend save 邏輯(本期假設沿用,只動 UI 排版 + 語法測試)
- ① http modal phase 2(backend save 邏輯)— 仍 backlog
- 套 ①②③④ Picker pattern 到 stream/upstream(等 user 確認要不要做)
