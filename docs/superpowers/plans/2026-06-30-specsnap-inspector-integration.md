# SpecSnap Inspector 整合到 nginxWebUI Admin UI

**日期:** 2026-06-30
**狀態:** 已批准,即將執行
**觸發:** 使用者「我要加一個測量 css 的功能」+「之前開發好的 SpecSnap 工具,加到 package,UI 上面做一個圖示」

---

## Goal

把 [`@tw199501/specsnap-inspector-core`](https://www.npmjs.com/package/@tw199501/specsnap-inspector-core)(框架無關版)整合到 nginxWebUI admin UI header,讓使用者點圖示按鈕 → 啟動 SpecSnap inspector → 多選元素 → 自動 capture box-model + inter-element gaps + viewport context → 下載 markdown + PNG bundle 傳給 AI(我)做 UI 調整。

## Why SpecSnap inspector-core(不是 inspector-vue / inspector-react)

nginxWebUI 主體是 **Layui + jQuery + Freemarker**(server-side rendered),Vue 3 只在局部 mount。`inspector-core` 是 framework-agnostic,直接 `createInspector({scope, onSave?})` + bind 自己的按鈕 → toggle picker,跟任何前端框架共存。

## Architecture

```
header.html [SpecSnap button] ──onclick──> launchSpecSnap()
                                                │
                                                ▼
                            window.__specsnap.toggle()  ← exported by launcher
                                                │
                                                ▼
                            inspector-core picker overlay
                                                │
                                                ▼
                            user clicks elements (multi-select with numbered badges)
                                                │
                                                ▼
                            saveBundle() → storage ladder:
                              1. File System Access (Chromium)
                              2. ZIP via fflate (all modern)
                              3. Individual <a download> (fallback)
```

**Module 載入:** browser-native import map(無 bundler)— nginxWebUI `package.json` 只有 Playwright,**沒有 webpack / rollup / vite**。`static/lib/vue/vue.esm-browser.prod.js` 已是 vendored ESM 先例,SpecSnap 採同模式。

**Import map**(在 common.html 注入):
```html
<script type="importmap">
{
  "imports": {
    "@tw199501/specsnap-core": "/lib/specsnap/specsnap-core.mjs",
    "@tw199501/specsnap-inspector-core": "/lib/specsnap/specsnap-inspector-core.mjs",
    "fflate": "/lib/specsnap/fflate.mjs"
  }
}
</script>
```

Import map 在 Chrome 89+ / Safari 16.4+ / Firefox 108+ 原生支援,admin 工具不會有 IE 用戶。

## Tech Stack

- **SpecSnap inspector-core 0.0.9**(已在 `node_modules/`)+ specsnap-core 0.0.9
- **fflate** ~10KB(需 `npm install fflate` 加進 production deps)
- **Browser ESM + import map**(無 bundler)
- **Layui** 圖示(用內建 `layui-icon-screen` 或類似)

## Vendor 來源 / 目的

| 來源 | 目的 | 大小 |
|---|---|---|
| `node_modules/@tw199501/specsnap-core/dist/index.mjs` | `src/main/resources/static/lib/specsnap/specsnap-core.mjs` | 23KB |
| `node_modules/@tw199501/specsnap-inspector-core/dist/index.mjs` | `src/main/resources/static/lib/specsnap/specsnap-inspector-core.mjs` | 19KB |
| `node_modules/fflate/esm/browser.js`(或 `index.js`) | `src/main/resources/static/lib/specsnap/fflate.mjs` | ~10KB |

**為何 vendor 而不用 esm.sh CDN:** [feedback_no_external_cdn_offline.md] — 自架/離線部署不可依賴外網 CDN。

## File Structure

### 新建
- `src/main/resources/static/lib/specsnap/specsnap-core.mjs`
- `src/main/resources/static/lib/specsnap/specsnap-inspector-core.mjs`
- `src/main/resources/static/lib/specsnap/fflate.mjs`
- `src/main/resources/static/js/specsnap-launcher.js`(用 ESM `<script type="module">` 載入)

### 修改
- `src/main/resources/WEB-INF/view/adminPage/common.html` — 注入 import map + launcher script
- `src/main/resources/WEB-INF/view/adminPage/header.html` — 加 SpecSnap 圖示按鈕(在「使用教程」旁)
- `src/main/resources/messages.properties` + `_zh_TW.properties` + `_en_US.properties` — 加 3 個 i18n key:`commonStr.specSnap`(按鈕 tooltip)
- `package.json` — `npm install fflate`(production dependency)

### 不動
- 任何 controller / service / model / view 邏輯 — 純前端 vendor + UI 觸發
- 主應用 Java 代碼

## Launcher 邏輯(`specsnap-launcher.js`)

```js
import { createInspector } from '@tw199501/specsnap-inspector-core';

const inspector = createInspector({
  scope: document.body
  // 不提供 onSave → 走預設 storage ladder(自動下載)
});

window.launchSpecSnap = () => inspector.toggle();
```

## Header button

在 `header.html` 加(位置:現有「使用教程」按鈕**前面**,讓開發/QA 容易看到):

```html
<li class="layui-nav-item">
  <button type="button" class="header-link-btn" onclick="launchSpecSnap()"
          aria-label="${commonStr.specSnap}" title="${commonStr.specSnap}">
    <i class="layui-icon layui-icon-screen-full"></i>
    <span>SpecSnap</span>
  </button>
</li>
```

## 風險與回退

| 風險 | 緩解 |
|---|---|
| `fflate` dynamic import 失敗 → ZIP 路徑壞 | 已 vendor + import map resolve;如真壞,storage ladder 自動 fallback 到 individual `<a download>` |
| `import map` 在老瀏覽器不支援 | admin 工具用戶可控,modern browser only;launcher script `<script type="module">` 在不支援的瀏覽器會 silently skip,不影響其他功能 |
| SpecSnap inspector overlay 可能蓋住 Layui modal | inspector 預設 z-index 應該夠高;如有衝突,加 z-index override CSS |
| 第三方 npm `fflate` 加進 prod deps | fflate 是純 JS、MIT、~10KB、無 transitive deps,風險低 |
| `node_modules/fflate` 之後 npm install 才會出現 | vendor 完即可刪 node_modules,vendored 檔已在 static/lib/ |

回退:`git revert` 一個 commit 即可,**無 schema / 業務邏輯改動**。

## Scope decision

**所有 admin 都看得到 SpecSnap 按鈕**(不藏 dev mode 後面),理由:
- nginxWebUI 是 admin tool,只有 admin 登入後看得到
- SpecSnap inspector 對非 dev 用戶無害 — 點 toggle 才啟動,不啟動就是個小按鈕
- 若用戶想藏起來,後續可加 admin role check(本 plan 不做)

## Verification

1. `mvn clean package -DskipTests` — fat jar bundle static
2. 啟 server,登入 admin
3. Header 看到 SpecSnap 按鈕(在「使用教程」旁)
4. 點 SpecSnap → inspector overlay 啟用
5. 滑鼠 hover 元素 → 顯示 box-model overlay
6. 點 element → 加入 numbered badge
7. 點 SpecSnap 的 save 按鈕 → 自動下載 ZIP(或 individual files,視 fflate 是否成功 load)
8. 開 ZIP / markdown,確認內容(box-model / gaps / viewport / annotations)

## Out of scope(刻意不做)

- 不寫 Playwright spec(SpecSnap inspector 是純 UI 工具,行為靠肉眼驗證更實際;之後若整合到 review flow 再加)
- 不加 admin role check(現階段所有 admin 可用)
- 不寫 server-side `/api/specsnap` endpoint(用戶說「截圖給你」,直接走 browser download 即可,不需 server 儲存)
- 不整合 `data-i18n-key` / `data-v-source` reverse lookup(那需要 Vite plugin 在 build 時注入屬性;nginxWebUI 是 Freemarker 沒 build pipeline)

## Out of scope (刻意保留 user 決定)

- inspector-vue / inspector-react 不裝(主體是 Layui;局部 Vue 區用 inspector-core 也夠)
- SpecSnap 按鈕是否藏在 query string(`?devtools=1`)後面 — 預設不藏,要藏的話後續一行 if 即可

---

## Execution checklist(本 plan 完成後立即執行)

- [ ] `npm install fflate` 進 production deps
- [ ] mkdir `static/lib/specsnap/`
- [ ] Copy 3 個 .mjs 進 vendor 目錄
- [ ] Write `static/js/specsnap-launcher.js`
- [ ] Edit `common.html` 注入 import map + launcher
- [ ] Edit `header.html` 加按鈕
- [ ] Edit 3 份 `messages*.properties` 加 `commonStr.specSnap` key
- [ ] `mvn clean package -DskipTests` rebuild jar
- [ ] 啟測試 server,Playwright 自動驗收 — 按鈕在 header 出現 + 點擊不報錯
- [ ] commit + push(單一 commit:`feat(specsnap): integrate SpecSnap inspector for in-UI element measurement`)
