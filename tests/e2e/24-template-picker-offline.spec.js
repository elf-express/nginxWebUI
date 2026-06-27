const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 驗證:參數模板選取器改用「本地打包 Vue」後,在離線/內網(封鎖外網 CDN)
// 也能開啟選取器並列出 seed 的參數模板。
//
// 修復前:template-picker.js 從 https://esm.sh CDN 動態 import Vue,離線部署
// 連不到 → 選取器開不起來 → 使用者無法「呼叫模板 → 選 → 帶入參數」。
// 修復後:Vue 改成隨 app 打包的本地檔(static/lib/vue/vue.esm-browser.prod.js)。
//
// 依使用者要求:登入「只做一次」(beforeAll),之後多個檢查共用同一 session。
test.describe.serial('模板選取器離線可用 (本地 Vue, 不靠 esm.sh CDN)', () => {
  let context;
  let page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
    // 封鎖外網 CDN,模擬離線 / 內網部署。舊版會在此情況下開不了選取器。
    await page.route(/esm\.sh|cdn\.jsdelivr\.net|unpkg\.com/, (route) => route.abort());
    await login(page); // 只登入這一次
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('本地 Vue 檔可被伺服器提供 (HTTP 200, 內容為 Vue)', async () => {
    const resp = await page.request.get('/lib/vue/vue.esm-browser.prod.js');
    expect(resp.status()).toBe(200);
    const body = await resp.text();
    expect(body).toContain('vue v3'); // 檔頭版權註解 "* vue v3.5.x"
  });

  test('封鎖 CDN 下,「以模板新增參數」仍能開啟選取器並列出 seed 模板', async () => {
    await page.goto('/adminPage/server');
    await page.waitForFunction(() => typeof window.selectTemplateAsParam === 'function');

    // 用 page.evaluate 直接呼叫頁面既有全域函式(Layui 元件慣例)開到模板選取器
    await page.evaluate(() => add());            // 新增代理視窗
    await page.waitForTimeout(400);
    await page.evaluate(() => serverParam());    // 參數編輯器
    await page.waitForTimeout(400);
    await page.evaluate(() => selectTemplateAsParam('paramList')); // 觸發模板選取器

    // 選取器(本地 Vue)應開啟 → 證明不靠被封鎖的 CDN
    await expect(page.locator('.tp-overlay')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.tp-modal h3')).toContainText('模板');

    // 應列出 InitConfig.initDefaultTemplates() seed 的模板(數量 + 指定名稱)
    const items = page.locator('.tp-item');
    await expect(items.first()).toBeVisible({ timeout: 5000 });
    expect(await items.count()).toBeGreaterThanOrEqual(10);
    await expect(page.locator('.tp-item', { hasText: 'Proxy Headers' })).toBeVisible();
    await expect(page.locator('.tp-item', { hasText: 'CORS Allow All' })).toBeVisible();
    await expect(page.locator('.tp-item', { hasText: 'WebSocket Proxy' })).toBeVisible();
  });
});
