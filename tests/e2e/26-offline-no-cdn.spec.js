const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 離線守門:主要頁面「載入時」都不應對外網 CDN(esm.sh / jsdelivr / unpkg / cdnjs)發請求。
// 背景:此 fork 常跑離線/內網部署。曾有「模板選取器」「SpecSnap Inspector」從 esm.sh
// 載 Vue,離線即失效。此測試守「頁面載入即打 CDN」的回歸;「lazy 觸發才打」(如點開
// 選取器)由 24-template-picker-offline 守(實際點開 picker + 封鎖 esm.sh)。
//
// 登入只做一次(beforeAll),連續巡覽主要頁面後一次斷言。
test.describe.serial('離線守門:前端不依賴外網 CDN', () => {
  let context;
  let page;
  const cdnHits = [];

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
    // 記錄任何對外網 CDN 的請求(不封鎖,純觀察;有依賴就會被記到 → 測試紅)。
    page.on('request', (req) => {
      if (/esm\.sh|cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com/.test(req.url())) {
        cdnHits.push(req.url());
      }
    });
    await login(page); // 只登入這一次
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('主要頁面載入皆不請求外網 CDN', async () => {
    const pages = ['/adminPage/monitor', '/adminPage/server', '/adminPage/http', '/adminPage/protectionCert'];
    for (const path of pages) {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(600); // 給 module import / 動態載入一點時間觸發
    }
    expect(cdnHits, `不應有任何外網 CDN 請求,實際: ${cdnHits.join(', ')}`).toHaveLength(0);
  });
});
