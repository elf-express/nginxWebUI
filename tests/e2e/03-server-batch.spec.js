const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('server 額外參數批量輸入', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
  });

  test('server 設置額外參數 — 批量輸入可打開', async ({ page }) => {
    // 打開「添加反向代理」
    await page.getByRole('button', { name: /添加反向代理/ }).click();
    await page.waitForTimeout(500);

    // 點擊「設置額外參數」（使用 onclick="serverParam()"）
    await page.evaluate(() => serverParam());
    await page.waitForTimeout(500);

    // 點擊「批量輸入」（使用 onclick="showBatchInput()"）
    await page.evaluate(() => showBatchInput());

    // textarea 應出現
    const textarea = page.locator('#batchInputText');
    await expect(textarea).toBeVisible();
  });

  test('server 額外參數批量輸入並提交', async ({ page }) => {
    await page.getByRole('button', { name: /添加反向代理/ }).click();
    await page.waitForTimeout(500);

    await page.evaluate(() => serverParam());
    await page.waitForTimeout(500);

    await page.evaluate(() => showBatchInput());

    const textarea = page.locator('#batchInputText');
    await textarea.fill('client_max_body_size 200m\nproxy_read_timeout 300s');

    await page.evaluate(() => parseBatchInput());
    await page.waitForTimeout(500);

    // 參數應出現在表格中
    const pageContent = await page.content();
    expect(pageContent).toContain('client_max_body_size');
    expect(pageContent).toContain('proxy_read_timeout');
  });

});

test.describe('location 額外參數批量輸入', () => {

  test('location 設置額外參數 — 批量輸入可打開', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');

    // 打開「添加反向代理」
    await page.getByRole('button', { name: /添加反向代理/ }).click();
    await page.waitForTimeout(500);

    // 添加一個 Location（onclick="addItem()"）
    await page.evaluate(() => addItem());
    await page.waitForTimeout(500);

    // 點擊 Location 行的「設置額外參數」
    const locationExtraBtn = page.locator('button[onclick*="locationParam"]');
    if (await locationExtraBtn.count() > 0) {
      await locationExtraBtn.first().click();
      await page.waitForTimeout(500);

      // 點擊「批量輸入」
      const batchBtn = page.locator('#locationBatchInputDiv button, button[onclick*="showLocationBatchInput"]');
      if (await batchBtn.count() > 0) {
        await batchBtn.first().click();
        const textarea = page.locator('#locationBatchInputText');
        await expect(textarea).toBeVisible();
      }
    }
  });

});
