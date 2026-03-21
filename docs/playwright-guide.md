# Playwright 自動化測試開發規範

> 本規範為**強制性**標準，所有測試開發必須遵守，無選擇性條目。

## 一、環境準備

### 安裝（首次）
```bash
npm install @playwright/test playwright --save-dev
npx playwright install chromium
```

### 目錄結構（固定）
```
tests/
├── e2e/
│   ├── playwright.config.js    # 配置檔（勿任意修改）
│   ├── global-setup.js         # 測試前啟動 app
│   ├── global-teardown.js      # 測試後停止 app
│   ├── helpers.js              # 共用工具（登入、啟停 app）
│   ├── 01-login.spec.js        # 測試檔（按編號排序）
│   ├── 02-xxx.spec.js
│   └── test-data/              # 測試資料庫（gitignore）
└── playwright-report/          # 報告與影片輸出（gitignore）
    ├── html/                   # HTML 報告
    └── test-results/           # 截圖、影片、trace
```

### 固定端口
| 用途 | 端口 | 說明 |
|------|------|------|
| 測試 app | 18080 | 定義在 `helpers.js` |
| HTML 報告 | 9400 | 定義在 `package.json` |
| 正式 app | 8080 | 不衝突 |

---

## 二、執行指令（三個就夠）

```bash
# 1. 執行測試（帶瀏覽器 + 錄影 + 截圖 + trace + HTML 報告）
npm test

# 2. 快速測試（無瀏覽器畫面，CI/CD 用）
npm run test:fast

# 3. 查看報告（測試完後執行）
npm run report
```

---

## 三、開發流程（強制）

每次新增或修改功能，必須按以下順序完成：

### Step 1：寫代碼
實作功能。

### Step 2：寫測試
在 `tests/e2e/` 新增 `NN-功能名.spec.js`。

### Step 3：執行測試
```bash
npm test
```

### Step 4：確認全過
所有測試必須通過。如有失敗，修復後重跑。

### Step 5：產生報告
測試通過後，`npm run report` 檢視報告，確認影片和截圖正確。

---

## 四、寫測試的規範（強制）

### 檔名
- 格式：`NN-功能名.spec.js`（NN 為兩位數編號）
- 編號連續，不跳號

### 多語言
按鈕文字**必須**用正則匹配簡繁體：
```javascript
// ✅ 必須這樣寫
await page.getByRole('button', { name: /批量輸入|批量输入/ });
await page.getByRole('button', { name: /確認添加|确认添加/ });
await page.getByRole('button', { name: /登入|登录/ });
await page.getByRole('button', { name: /關閉|关闭/ });
await page.getByRole('button', { name: /提交/ });
await page.getByRole('button', { name: /添加反向代理/ });
```

### 結構
```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('功能名稱', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/xxx');
    await page.waitForSelector('table');
  });

  test('測試案例描述', async ({ page }) => {
    // 操作
    await page.getByRole('button', { name: /按鈕/ }).click();
    // 驗證
    await expect(page.locator('#element')).toBeVisible();
  });

});
```

### layui 元件操作
layui 的 select、checkbox 無法用標準方式操作，**必須**用 `page.evaluate`：
```javascript
await page.evaluate(() => {
  document.getElementById('ssl').value = '1';
  checkSsl('1');
});

await page.evaluate(() => serverParam());
await page.evaluate(() => addItem());
await page.evaluate(() => addOver());
```

### 等待策略
```javascript
// 等待元素出現
await page.waitForSelector('#elementId');

// 等待跳轉
await page.waitForURL('**/adminPage/monitor');

// layui 動畫需要短暫等待
await page.waitForTimeout(500);
```

---

## 五、測試環境隔離（固定）

測試 app 啟動參數：
```bash
java -jar target/nginxWebUI-4.3.8.jar \
  --server.port=18080 \
  --project.home=tests/e2e/test-data/ \
  --init.admin=admin \
  --init.pass=Admin1234 \
  --project.testCaptcha=1234
```

| 項目 | 值 | 說明 |
|------|------|------|
| 端口 | 18080 | 不影響正式 8080 |
| 資料庫 | tests/e2e/test-data/sqlite.db | 每次測試前清空 |
| 帳號 | admin | 固定 |
| 密碼 | Admin1234 | 固定 |
| 驗證碼 | 1234 | 固定，正式環境不帶此參數 |

---

## 六、跟 AI 協作的提示詞（直接複製貼上）

### 新增功能 + 測試
```
請實作 [功能描述]。完成後：
1. 在 tests/e2e/ 新增 Playwright 測試
2. 按鈕文字用正則匹配簡繁體
3. layui 元件用 page.evaluate 操作
4. 執行 npm test 確認全部通過
5. 執行 npm run report 產生報告
6. 告訴我測試結果和影片位置
```

### 修 Bug + 測試
```
[Bug 描述]。修復後：
1. 補上對應的 Playwright 測試
2. 執行 npm test 確認全部通過（含舊測試）
3. 產生報告
```

### 錄製展示影片
```
請用 Playwright 錄影模式跑測試。
slowMo 設 1200ms，我要展示給 [對象] 看。
跑完告訴我影片在哪裡。
```

### 只跑測試 + 報告
```
請執行 npm test，確認所有測試通過。
然後 npm run report 產生報告給我看。
```

---

## 七、常用 API 速查

| 操作 | 語法 |
|------|------|
| 導航 | `await page.goto('/path')` |
| 點擊 | `await page.getByRole('button', { name: /文字/ }).click()` |
| 填入 | `await page.locator('#id').fill('value')` |
| 驗證可見 | `await expect(locator).toBeVisible()` |
| 驗證文字 | `await expect(locator).toContainText('xxx')` |
| 驗證勾選 | `await expect(locator).toBeChecked()` |
| 驗證未勾選 | `await expect(locator).not.toBeChecked()` |
| 截圖 | `await page.screenshot({ path: 'shot.png' })` |
| 執行 JS | `await page.evaluate(() => myFunc())` |
| 等待元素 | `await page.waitForSelector('#id')` |
| 等待跳轉 | `await page.waitForURL('**/path')` |
| 頁面內容 | `const html = await page.content()` |
