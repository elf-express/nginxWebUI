# 2026-05-20 LF 規範化 / 國旗修正 / 語言 UI 接線 / CLAUDE.md 安裝部署補完

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性完成四件相互關聯的清整工作 — 全專案 LF 規範化、三國國旗 SVG 修正、語言切換 UI 接國旗 icon、CLAUDE.md 補完開發/部署步驟。

**Architecture:**

*   LF：以 `.gitattributes`（`eol=lf`）為單一真實來源，配合 `git add --renormalize` 與工作區 sed 一次性收斂。
*   國旗：純 inline SVG（光芒/五星/米字旗），不依賴外部資源；放大關鍵特徵以利 16~24px icon 仍可辨識。
*   UI：保留 Layui `<select>` 邏輯入口（仍透過 `changeLangOver()` 提交），但把表單改成 radio 列表 + CSS `background-image` 顯示對應 `.svg`；header / login 的 Language 按鈕另外用 `<img>` 直接嵌入目前語系國旗。
*   CLAUDE.md：新增「開發環境準備」與「部署方式」兩大段，把 docs/superpowers/plans/ 既有的 docker-guide / playwright-guide 內容摘要嵌入，補上版本要求與從零到啟動的逐步命令。

**Tech Stack:** Java 8、Solon 3.3.3、Maven、Layui + jQuery + Freemarker、SQLite 3.47.0.0 / PG 42.7.2 / MySQL 9.1.0、Playwright 1.58.2、Docker。

---

## Context

**為什麼做這份計畫**

*   **LF**：Windows Git 預設 `core.autocrlf=true`，導致 `package.json / select_lang.html / select_lang.js` 在 commit 時被警告「LF will be replaced by CRLF」，跨平台檔案不一致；CLAUDE.md 明文要求 `entrypoint.sh` 必須 LF 換行，缺乏 repo 層的強制機制。
*   **國旗**：`static/img/{tw,cn,gb}.svg` 雖已建立，但 `tw.svg` 的青天白日光芒被限制在白色圓內、`cn.svg` 只有 1 顆大星缺 4 顆小星、`gb.svg` 米字旗的紅白條結構有錯位；同時 3 個對應的 `.png` 都是 0 bytes 空檔。
*   **UI 接線**：國旗檔已建立但 grep 後全 repo 找不到任何引用，等於只放圖沒接 UI;使用者明確表達「想要把 UI 接起來」。
*   **CLAUDE.md**：使用者對「安裝與部署方式」產生質疑 — 經 Explore agent 確認，CLAUDE.md 第 70-90 行的「常用指令」只列了 4 條命令，**完全沒有先決條件**（Java 8 / Maven / Node.js / Playwright 安裝）、沒有從零到啟動的逐步流程、沒有開發 vs 部署兩種模式的區隔。

**目標產出**

*   `.gitattributes` 落地、整個 repo 工作區 + git index 全 LF
*   `tw.svg / cn.svg / gb.svg` 視覺正確、`.png` 空檔刪除
*   三個語言選擇 UI 位置（select\_lang 下拉、header 按鈕、login 按鈕）都顯示對應國旗
*   CLAUDE.md 增「開發環境準備」「本地開發啟動」「Docker 部署」「測試流程」四段，新人按文件可從零到啟動成功
*   全部以 Playwright 測試 + 編譯驗證守住

---

## File Structure

| 檔案 | 動作 | 責任 |
| --- | --- | --- |
| `.gitattributes` | 已建立（前置工作） | 強制 LF 換行;二進位檔標 binary |
| `src/main/resources/static/img/tw.svg` | 已修正（前置工作） | 標準青天白日滿地紅 |
| `src/main/resources/static/img/cn.svg` | 已修正（前置工作） | 五星紅旗 1 大 + 4 小 |
| `src/main/resources/static/img/gb.svg` | **修改** | 米字旗修正紅/白十字結構 |
| `src/main/resources/static/img/{tw,cn,gb}.png` | **刪除** | 0 bytes 空檔，無用 |
| `src/main/resources/WEB-INF/view/adminPage/select_lang.html` | **修改** | radio 列表 + 國旗 icon |
| `src/main/resources/static/js/adminPage/select_lang.js` | **修改** | 讀取 radio 值 |
| `src/main/resources/WEB-INF/view/adminPage/header.html` | **修改** | Language 按鈕內加目前語系國旗 |
| `src/main/resources/WEB-INF/view/adminPage/login/index.html` | **修改** | Language 按鈕內加目前語系國旗 |
| `src/main/resources/static/css/lang-flags.css` | **新增** | 國旗 icon 樣式 |
| `tests/e2e/flag-svg-integrity.spec.js` | **新增** | SVG 結構檢查 |
| `tests/e2e/lang-switch.spec.js` | **新增** | 語言切換 UI 驗證 |
| `CLAUDE.md` | **修改** | 新增「開發環境準備」「部署方式」「測試流程」三章節 |

---

## Task 1: LF 規範化 — 收尾與 commit

**前置現況（plan mode 之前已完成）：**

*   `.gitattributes` 已建立並寫入完整規則（行尾、二進位）
*   `git add --renormalize` 已對所有非 deleted 檔案執行，index 已收斂為 LF
*   工作區所有 `.java / .js / .html / .ftl / .css / .xml / .yml / .properties / .svg / .md / LICENSE` 等已 `sed -i 's/\r$//'` 轉成 LF
*   `mvn clean package -DskipTests` 已驗證 BUILD SUCCESS

**Files:**

Modify: `.gitattributes`（已存在）

Verify-only: 全 repo tracked files

 **Step 1: 確認 git status 無「will be replaced by CRLF」警告**

```
cd "e:/nginxWebUI"
git status 2>&1 | grep -i "will be" | head -5
```

Expected: 無輸出。

*   **Step 2: 確認 tracked text 檔已全 LF**

```
cd "e:/nginxWebUI"
git ls-files | while read f; do
  [ -f "$f" ] && file "$f" 2>/dev/null | grep -lq CRLF && echo "$f"
done
```

Expected: 只剩 `hs_err_pid*.log`（已被 02ea78f commit 從工作區刪除，index 待同步），其他無 CRLF 殘留。

*   **Step 3: 確認編譯沒被 LF 轉換破壞**

```
cd "e:/nginxWebUI"
mvn clean package -DskipTests 2>&1 | grep -E "BUILD (SUCCESS|FAILURE)"
```

Expected: `[INFO] BUILD SUCCESS`。

*   **Step 4: 暫存 .gitattributes 與 renormalized 變更**

```
cd "e:/nginxWebUI"
git add .gitattributes
git add -u
```

*   **Step 5: commit LF 規範化**

```
cd "e:/nginxWebUI"
git commit -m "$(cat <<'EOF'
chore: enforce LF line endings repo-wide via .gitattributes

- Add .gitattributes with `* text=auto eol=lf` and explicit per-extension rules
- Mark .bat/.cmd/.ps1 as CRLF (Windows-only scripts)
- Mark binaries (.png, .jpg, .jar, .woff, etc.) as binary
- Renormalize all tracked text files to LF in both index and working tree
- Removes the "LF will be replaced by CRLF" warnings on Windows clones
- Ensures entrypoint.sh & all shell scripts remain LF (Docker requirement)
EOF
)"
```

---

## Task 2: 修正 gb.svg（英國國旗米字旗）+ 刪除空 PNG

**問題：** 現有 gb.svg 紅/白十字結構失衡。

**Files:**

Modify: `src/main/resources/static/img/gb.svg`

Delete: `src/main/resources/static/img/{tw,cn,gb}.png`

Create: `tests/e2e/flag-svg-integrity.spec.js`

 **Step 1: 寫 SVG 結構檢查測試**

Create: `tests/e2e/flag-svg-integrity.spec.js`

```javascript
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const IMG_DIR = path.resolve(__dirname, '../../src/main/resources/static/img');

test('tw.svg 含青天白日結構（紅底 + 藍底 + 12 道光芒）', () => {
  const svg = fs.readFileSync(path.join(IMG_DIR, 'tw.svg'), 'utf8');
  expect(svg).toContain('#FE0000');
  expect(svg).toContain('#000095');
  expect(svg).toMatch(/rotate\(30\)[\s\S]*rotate\(330\)/);
  expect(svg).toContain('<circle r="50"');
});

test('cn.svg 含五星紅旗結構（紅底 + 大星 + 4 顆小星）', () => {
  const svg = fs.readFileSync(path.join(IMG_DIR, 'cn.svg'), 'utf8');
  expect(svg).toContain('#DE2910');
  expect(svg).toContain('#FFDE00');
  const useStars = (svg.match(/<use href="#star"/g) || []).length;
  expect(useStars).toBe(5);
});

test('gb.svg 含米字旗結構（藍底 + 紅白十字）', () => {
  const svg = fs.readFileSync(path.join(IMG_DIR, 'gb.svg'), 'utf8');
  expect(svg).toContain('#012169');
  expect(svg).toContain('#C8102E');
  expect(svg).toContain('#FFFFFF');
  expect(svg).toMatch(/clip-path|stroke-width/);
});
```

*   **Step 2: 跑測試確認 gb FAIL（tw / cn 應通過）**

```
cd "e:/nginxWebUI"
npx playwright test --config=tests/e2e/playwright.config.js tests/e2e/flag-svg-integrity.spec.js
```

Expected: tw/cn PASS、gb FAIL。

*   **Step 3: 寫入修正版 gb.svg**

Replace `src/main/resources/static/img/gb.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 300">
  <clipPath id="t"><path d="M300,150 v150 h-300 z M300,150 v-150 h300 z M300,150 h300 v150 z M300,150 h-300 v-150 z"/></clipPath>
  <path d="M0,0 v300 h600 v-300 z" fill="#012169"/>
  <path d="M0,0 L600,300 M600,0 L0,300" stroke="#FFFFFF" stroke-width="60"/>
  <path d="M0,0 L600,300 M600,0 L0,300" clip-path="url(#t)" stroke="#C8102E" stroke-width="40"/>
  <path d="M300,0 v300 M0,150 h600" stroke="#FFFFFF" stroke-width="100"/>
  <path d="M300,0 v300 M0,150 h600" stroke="#C8102E" stroke-width="60"/>
</svg>
```

*   **Step 4: 跑測試確認全通過**

```
cd "e:/nginxWebUI"
npx playwright test --config=tests/e2e/playwright.config.js tests/e2e/flag-svg-integrity.spec.js
```

Expected: 3 個測試全 PASS。

*   **Step 5: 手動視覺確認**

開啟 `e:/nginxWebUI/src/main/resources/static/img/gb.svg` 於瀏覽器，確認比例 2:1、藍底、白色對角線與紅色對角線、白色直十字與紅色直十字（白色都比紅色寬）。

*   **Step 6: 刪除 0 bytes 空 PNG**

```
cd "e:/nginxWebUI"
rm -f src/main/resources/static/img/tw.png \
      src/main/resources/static/img/cn.png \
      src/main/resources/static/img/gb.png
```

*   **Step 7: Commit 國旗修正**

```
cd "e:/nginxWebUI"
git add src/main/resources/static/img/tw.svg \
        src/main/resources/static/img/cn.svg \
        src/main/resources/static/img/gb.svg \
        tests/e2e/flag-svg-integrity.spec.js
git rm src/main/resources/static/img/tw.png \
       src/main/resources/static/img/cn.png \
       src/main/resources/static/img/gb.png 2>/dev/null || true
git commit -m "fix(i18n): correct flag SVGs for tw/cn/gb and remove empty PNGs

- tw.svg: 12 個光芒從太陽中心發散（原版光芒被限制在圓內）
- cn.svg: 補上 4 顆小星，每顆朝向大星中心
- gb.svg: 修正米字旗對角線與十字的紅白比例
- Remove tw.png / cn.png / gb.png (0 bytes empty files)
- Add structural integrity tests for all three flags"
```

---

## Task 3: select\_lang.html / .js 改 radio + 國旗 icon

**設計：** 保留 `changeLangOver()` 既有提交流程。把原本 `<select>` 改為 radio 列表，每個選項旁放對應 SVG（用 CSS `background-image`）。

**Files:**

Modify: `src/main/resources/WEB-INF/view/adminPage/select_lang.html`

Modify: `src/main/resources/static/js/adminPage/select_lang.js`

Create: `src/main/resources/static/css/lang-flags.css`

Create: `tests/e2e/lang-switch.spec.js`

 **Step 1: 寫 Playwright 測試**

Create: `tests/e2e/lang-switch.spec.js`

```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test('語言切換對話框顯示三個語系國旗', async ({ page }) => {
  await login(page);
  await page.click('text=Language');
  await page.waitForSelector('#changeLangDiv', { state: 'visible' });

  const zhFlag = page.locator('.lang-flag-zh');
  const twFlag = page.locator('.lang-flag-zh_TW');
  const enFlag = page.locator('.lang-flag-en_US');

  await expect(zhFlag).toBeVisible();
  await expect(twFlag).toBeVisible();
  await expect(enFlag).toBeVisible();

  const zhBg = await zhFlag.evaluate(el => getComputedStyle(el).backgroundImage);
  expect(zhBg).toContain('cn.svg');
});

test('切換到繁體中文後 header 顯示 tw 國旗', async ({ page }) => {
  await login(page);
  await page.click('text=Language');
  await page.locator('label[for="lang-zh_TW"]').click();
  await page.click('button:has-text("OK")');
  await page.waitForLoadState('networkidle');

  const headerFlag = page.locator('.header-current-lang-flag').first();
  await expect(headerFlag).toBeVisible();
  const src = await headerFlag.evaluate(el => el.getAttribute('src'));
  expect(src).toContain('tw.svg');
});
```

*   **Step 2: 跑測試確認失敗**

```
cd "e:/nginxWebUI"
npx playwright test --config=tests/e2e/playwright.config.js tests/e2e/lang-switch.spec.js
```

Expected: FAIL — `.lang-flag-*` 元素不存在。

*   **Step 3: 建立 CSS 樣式**

Create: `src/main/resources/static/css/lang-flags.css`

```css
.lang-radio-group { display: flex; flex-direction: column; gap: 10px; }
.lang-radio-item { display: flex; align-items: center; gap: 10px; cursor: pointer; padding: 6px 10px; border-radius: 4px; }
.lang-radio-item:hover { background: #f5f5f5; }
.lang-radio-item input[type="radio"] { margin: 0; }
.lang-flag { width: 32px; height: 22px; background-size: cover; background-position: center; border: 1px solid #ddd; flex-shrink: 0; }
.lang-flag-zh { background-image: url('../img/cn.svg'); }
.lang-flag-zh_TW { background-image: url('../img/tw.svg'); }
.lang-flag-en_US { background-image: url('../img/gb.svg'); }
.header-current-lang-flag { width: 22px; height: 16px; vertical-align: middle; margin-right: 4px; border: 1px solid rgba(255,255,255,0.3); }
```

*   **Step 4: 改寫 select\_lang.html**

Replace `src/main/resources/WEB-INF/view/adminPage/select_lang.html`:

```html
<link rel="stylesheet" href="${ctx}/css/lang-flags.css?v=${jsrandom}"/>
<script src="${ctx}/js/adminPage/select_lang.js?v=${jsrandom}" type="text/javascript"></script>

<div style="height: 0px; width: 0px; overflow: hidden;">
    <div class="layui-form" id="changeLangDiv" style="padding: 15px; display: none">
        <div class="layui-form-item">
            <label class="layui-form-label">Language</label>
            <div class="layui-input-block">
                <div class="lang-radio-group">
                    <label class="lang-radio-item" for="lang-zh">
                        <input type="radio" name="lang" id="lang-zh" value="zh" ${(lang=='zh')?string('checked','')}>
                        <span class="lang-flag lang-flag-zh"></span>
                        <span>简体中文</span>
                    </label>
                    <label class="lang-radio-item" for="lang-zh_TW">
                        <input type="radio" name="lang" id="lang-zh_TW" value="zh_TW" ${(lang=='zh_TW')?string('checked','')}>
                        <span class="lang-flag lang-flag-zh_TW"></span>
                        <span>繁體中文</span>
                    </label>
                    <label class="lang-radio-item" for="lang-en_US">
                        <input type="radio" name="lang" id="lang-en_US" value="en_US" ${(lang=='en_US')?string('checked','')}>
                        <span class="lang-flag lang-flag-en_US"></span>
                        <span>English</span>
                    </label>
                </div>
            </div>
        </div>
        <div class="layui-form-item center" style="text-align: center; margin-top: 20px;">
            <button type="button" class="layui-btn layui-btn-normal" onclick="changeLangOver()">OK</button>
            <button type="button" class="layui-btn " onclick="layer.close(changeLangIndex)">Cancel</button>
        </div>
    </div>
</div>
```

*   **Step 5: 同步修改 select\_lang.js**

Modify: `src/main/resources/static/js/adminPage/select_lang.js`，把：

```javascript
data: {
    lang: $("#lang").val()
},
```

改為：

```javascript
data: {
    lang: $('input[name="lang"]:checked').val()
},
```

*   **Step 6: 跑測試確認對話框部分通過**

```
cd "e:/nginxWebUI"
npx playwright test --config=tests/e2e/playwright.config.js tests/e2e/lang-switch.spec.js -g "對話框"
```

Expected: 「對話框顯示三個語系國旗」PASS。

---

## Task 4: header.html Language 按鈕加目前語系國旗

**Files:**

Modify: `src/main/resources/WEB-INF/view/adminPage/header.html`

 **Step 1: 確認** `**lang**` **變數在 Freemarker context 中可取得**

```
cd "e:/nginxWebUI"
grep -rn 'attr("lang"' src/main/java/com/cym/
grep -rn 'putAttr.*"lang"' src/main/java/com/cym/
```

Expected: 至少一處設定 `model.attr("lang", ...)` 或類似。若無，**需要在** `**HomeController.java**` **/** `**HomeConfig.java**` **注入** `**lang**` **變數**，例如：

```java
ctx.attr("lang", LocaleUtil.getCurrentLang());
```

*   **Step 2: 修改 header.html 第 11-13 行**

把：

```html
<li class="layui-nav-item">
    <a href="javascript:changeLang()">Language ${langType}</a>
</li>
```

改為：

```html
<li class="layui-nav-item">
    <a href="javascript:changeLang()">
        <#if lang == "zh"><img class="header-current-lang-flag" src="${ctx}/img/cn.svg" alt="zh"/></#if>
        <#if lang == "zh_TW"><img class="header-current-lang-flag" src="${ctx}/img/tw.svg" alt="zh_TW"/></#if>
        <#if lang == "en_US"><img class="header-current-lang-flag" src="${ctx}/img/gb.svg" alt="en_US"/></#if>
        Language ${langType}
    </a>
</li>
```

*   **Step 3: 確認 lang-flags.css 已透過 select\_lang.html include 載入**

```
cd "e:/nginxWebUI"
grep -n "lang-flags.css" src/main/resources/WEB-INF/view/adminPage/select_lang.html
```

Expected: 第 1 行有 `<link rel="stylesheet" href="${ctx}/css/lang-flags.css?...`。

*   **Step 4: 跑測試**

```
cd "e:/nginxWebUI"
mvn clean package -DskipTests
# 在另一個 shell 啟動 server：
# java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.3.jar --server.port=18080 --project.home=./dev-home/
npx playwright test --config=tests/e2e/playwright.config.js tests/e2e/lang-switch.spec.js
```

Expected: 兩個測試全 PASS。

---

## Task 5: login/index.html Language 按鈕加國旗

**Files:**

Modify: `src/main/resources/WEB-INF/view/adminPage/login/index.html`

 **Step 1: 修改 login/index.html 第 99 行**

把：

```html
<button type="button" class="layui-btn layui-btn-primary" onclick="changeLang()">Language ${langType}</button>
```

改為：

```html
<button type="button" class="layui-btn layui-btn-primary" onclick="changeLang()">
    <#if lang == "zh"><img class="header-current-lang-flag" src="${ctx}/img/cn.svg" alt="zh"/></#if>
    <#if lang == "zh_TW"><img class="header-current-lang-flag" src="${ctx}/img/tw.svg" alt="zh_TW"/></#if>
    <#if lang == "en_US"><img class="header-current-lang-flag" src="${ctx}/img/gb.svg" alt="en_US"/></#if>
    Language ${langType}
</button>
```

*   **Step 2: 手動驗證登入頁**

啟動 server 後造訪 `http://localhost:8080`，未登入狀態下 addUser 對話框內的 Language 按鈕應顯示對應國旗。

*   **Step 3: Commit UI 接線**

```
cd "e:/nginxWebUI"
git add src/main/resources/static/css/lang-flags.css \
        src/main/resources/WEB-INF/view/adminPage/select_lang.html \
        src/main/resources/static/js/adminPage/select_lang.js \
        src/main/resources/WEB-INF/view/adminPage/header.html \
        src/main/resources/WEB-INF/view/adminPage/login/index.html \
        tests/e2e/lang-switch.spec.js
# 若有改 HomeController.java 也一併 add
git commit -m "feat(i18n): show country flag icons in language selector UI

- select_lang.html: convert <select> to radio list with flag icons
- header.html: show current locale flag next to Language link
- login/index.html: same for Language button on first-run admin setup
- Add static/css/lang-flags.css for flag icon styling
- Add tests/e2e/lang-switch.spec.js Playwright tests"
```

---

## Task 6: 更新 CLAUDE.md — 補完開發/部署步驟

**設計：** 在「常用指令」**之前** 插入「開發環境準備」「部署方式」「測試流程」三章節，並把現有「常用指令」更名為「快速指令參考」。

**Files:**

Modify: `e:/nginxWebUI/CLAUDE.md`

 **Step 1: 確認 CLAUDE.md 現況**

```
cd "e:/nginxWebUI"
wc -l CLAUDE.md
grep -n "^##" CLAUDE.md
```

*   **Step 2: 在「常用指令」之前插入新章節（內容如下）**

````
## 開發環境準備

### 先決條件

| 工具 | 版本要求 | 驗證指令 | 安裝建議 |
|---|---|---|---|
| **JDK** | Java 8 (1.8) | `java -version` | OpenJDK 8 / Zulu 8 / Temurin 8 |
| **Maven** | 3.6+ | `mvn -version` | 3.8+ 推薦 |
| **Node.js** | 18+ | `node -v` | LTS 版本 |
| **npm** | 隨 Node 8+ | `npm -v` | — |
| **Git** | 2.30+ | `git --version` | 開啟 `core.autocrlf=false` 或讓 `.gitattributes` 接管 |
| **Docker**（選用） | 20.10+ | `docker --version` | 含 Compose v2 |

> **跨平台換行符**：本 repo 已透過 `.gitattributes` 強制 LF。如果你的 git 全域 `core.autocrlf=true` 也沒關係，`.gitattributes` 會覆蓋它。

### 初次開發環境設置

```bash
# 1. Clone
git clone <repo-url> nginxWebUI
cd nginxWebUI

# 2. 驗證 JDK / Maven 可用
java -version
mvn -version

# 3. 安裝 Node 端依賴（Playwright + 瀏覽器）
npm install
npx playwright install --with-deps chromium

# 4. 編譯 Java 端
mvn clean package -DskipTests
# 產物：target/nginxWebUI-${version}.jar （${version} 見 pom.xml）
```

### 本地開發啟動

**最小啟動（SQLite，預設 8080 port）：**

```bash
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-5.0.3.jar \
     --server.port=8080 \
     --project.home=./dev-home/
```

**指定 PostgreSQL：**

```bash
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.3.jar \
     --server.port=8080 \
     --project.home=./dev-home/ \
     --spring.database.type=postgresql \
     --spring.datasource.url=jdbc:postgresql://localhost:5432/nginxwebui \
     --spring.datasource.username=postgres \
     --spring.datasource.password=postgres
```

**忘記密碼（重置）：**

```bash
java -jar target/nginxWebUI-5.0.3.jar --project.findPass=true
```

**測試用驗證碼（限本機）：**

```bash
java -jar target/nginxWebUI-5.0.3.jar --project.testCaptcha=true
```

### IDE 設置（無預設 .run/.vscode 配置）

- **Main class**：`com.cym.NginxWebUI`
- **Program args**：`--server.port=8080 --project.home=./dev-home/`
- **JVM args**：`-Dfile.encoding=UTF-8`
- **Working directory**：repo 根目錄

## 部署方式

### 方式 A：純 jar 部署

```bash
mvn clean package -DskipTests
scp target/nginxWebUI-5.0.3.jar user@host:/home/nginxWebUI/
ssh user@host
cd /home/nginxWebUI
nohup java -jar -Dfile.encoding=UTF-8 nginxWebUI-5.0.3.jar \
      --server.port=8080 --project.home=/home/nginxWebUI/ \
      > app.log 2>&1 &
```

### 方式 B：Docker Compose Stack（推薦）

詳見 [docs/superpowers/plans/docker-guide.md](docs/superpowers/plans/docker-guide.md)。

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f nginxwebui
```

**Stack 組成：**

| Service | Port | 用途 |
|---|---|---|
| nginxwebui | 8080 / 80 / 443 | 主應用 |
| postgres | 5432 | 資料庫 |
| loki | 3100 | 日誌聚合 |
| grafana | 3000 | 監控 dashboard |
| promtail | — | log 轉發 |
| crowdsec | — | 入侵偵測 |
| crowdsec-bouncer | — | nginx 流量過濾 |

### 方式 C：多平台映像建構

```bash
bash buildx.sh         # linux/amd64 + linux/arm64 + push
bash local_build.sh    # 僅 linux/amd64 本地
```

### 部署後驗證

```bash
curl http://localhost:8080/
curl http://localhost:8080/adminPage/monitor/nginxInfo
```

## 測試流程

```bash
npx playwright --version
npm test              # headed
npm run test:fast     # headless
npm run report        # 開 http://localhost:9400
```

詳見 [docs/superpowers/plans/playwright-guide.md](docs/superpowers/plans/playwright-guide.md)。
````

*   **Step 3: 把現有「常用指令」更名為「快速指令參考」**

```
cd "e:/nginxWebUI"
sed -i 's/^## 常用指令$/## 快速指令參考/' CLAUDE.md
```

*   **Step 4: 驗證 CLAUDE.md 結構**

```
cd "e:/nginxWebUI"
grep -n "^##" CLAUDE.md
```

Expected: 看到「開發環境準備」「部署方式」「測試流程」「快速指令參考」等章節。

*   **Step 5: 確認文件連結有效**

```
cd "e:/nginxWebUI"
for f in docs/superpowers/plans/docker-guide.md docs/superpowers/plans/playwright-guide.md; do
  [ -f "$f" ] && echo "OK: $f" || echo "MISSING: $f"
done
```

Expected: 兩個 OK。

*   **Step 6: Commit CLAUDE.md 更新**

```
cd "e:/nginxWebUI"
git add CLAUDE.md
git commit -m "docs: expand CLAUDE.md with prerequisites and detailed deployment steps

- Add '開發環境準備' section: version requirements table + setup commands
- Add '部署方式' section: jar / docker compose / multi-arch buildx variants
- Add '測試流程' section: Playwright + Maven test commands
- Rename '常用指令' to '快速指令參考'
- Cross-link to docs/superpowers/plans/{docker,playwright}-guide.md"
```

---

## Task 7: 端對端驗證

*   **Step 1: 從零 clone 模擬**

```
cd /tmp
git clone "e:/nginxWebUI" verify-clone
cd verify-clone
git status
```

Expected: 完全乾淨，無 LF/CRLF 警告。

*   **Step 2: 按 CLAUDE.md 新章節從零跑一遍**

完整按照「初次開發環境設置」「本地開發啟動」「測試流程」逐步執行，確認沒有缺漏指令。

*   **Step 3: 跑全 E2E 測試**

```
cd "e:/nginxWebUI"
npm test
```

Expected: 所有測試 PASS，含 flag-svg-integrity + lang-switch。

*   **Step 4: 瀏覽器手動煙霧測試**

啟動本地 server，依序：

1.  開啟登入頁，確認 addUser 對話框 Language 按鈕顯示「目前語系國旗」
2.  點 Language → 看到三個選項各有對應國旗
3.  切到繁體中文 → reload 後 header / login 顯示台灣國旗
4.  切到 English → 顯示英國國旗
5.  切回简体中文 → 顯示中國國旗

*   **Step 5: 最終 git log 檢查**

```
cd "e:/nginxWebUI"
git log --oneline -5
```

Expected: 看到本計畫產出的 4 個 commit（LF / Flags / UI / CLAUDE.md）。

---

## Verification Summary

執行完成後必須全部勾選：

*   `.gitattributes` 存在於 repo 根
*   `git status` 無「will be replaced by CRLF」警告
*   `git ls-files | xargs file` 無 CRLF 殘留（除 deleted log）
*   `mvn clean package -DskipTests` BUILD SUCCESS
*   `tw.svg / cn.svg / gb.svg` 在瀏覽器內視覺正確
*   `tw.png / cn.png / gb.png` 已刪除
*   `npm test` 全通過（含新增的 2 個 spec）
*   瀏覽器手動煙霧測試 5 步全通過
*   CLAUDE.md `grep "^##"` 看到「開發環境準備」「部署方式」「測試流程」「快速指令參考」
*   CLAUDE.md 內所有相對連結（`docs/superpowers/plans/*.md`）皆有效
*   `git log --oneline -5` 看到 4 個預期 commit

---

## Notes for Executor

1.  **執行順序**：Task 1 → 2 → 3 → 4 → 5 → 6 → 7。
2.  **TDD 適用範圍**：Tasks 2-5 採 TDD（先寫 Playwright 測試）。Task 1（LF）與 Task 6（CLAUDE.md）以「執行命令確認預期輸出」代替自動測試。
3.  **Task 1 是補 commit**：`.gitattributes` 建立、renormalize、工作區轉換已在 plan mode 之前完成。執行階段只需驗證 + commit。如果驗證失敗（例如某次 git stash 把 .gitattributes 弄丟），請回頭重做 LF 步驟。
4.  **Task 4 可能要碰 Java**：如果 Freemarker context 沒有注入 `lang` 變數，需要在 `HomeController.java` 或 `HomeConfig.java` 補一行。這是本計畫**唯一可能動 Java** 的地方。
5.  **不動範圍**：本計畫不修改任何 Java 業務邏輯（service）。所有改動限資源檔 + 配置 + 測試 + 文件。
6.  **回滾**：每個 task 都是獨立 commit，可單獨 `git revert`。