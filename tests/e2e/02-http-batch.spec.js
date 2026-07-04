const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// http 頁工具列於 2026-06-30 依 user 決定精簡:移除「批量輸入 / IP黑白名單 / 添加模板作為參數」
// 三個按鈕(見 http/index.html 註解),只保留 添加http參數配置 / 簡易配置向導 / 預覽。
// 批量輸入功能的「行為」測試改由 server 頁的 03-server-batch 覆蓋(server/location 仍有批量輸入)。
// 本 spec 鎖定「http 頁不再提供批量輸入按鈕」,防止意外回退該精簡決定。
// NOTE(backlog): http/index.html 仍殘留 batchInputDiv / parseBatchInput 死 code,待清理。
test.describe('http 頁工具列精簡（2026-06-30）', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('table');
  });

  test('http 頁工具列不再有批量輸入按鈕', async ({ page }) => {
    await expect(page.getByRole('button', { name: /批量輸入|批量输入/ })).toHaveCount(0);
  });

  test('http 頁工具列保留 預覽 與 新增 按鈕', async ({ page }) => {
    await expect(page.getByRole('button', { name: /預覽|预览/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /添加http參數配置|添加http参数配置/ })).toBeVisible();
  });

});
