const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('TLS 版本預設值與棄用標註', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
  });

  test('TLSv1 和 TLSv1.1 標示為已棄用', async ({ page }) => {
    // 打開「添加反向代理」
    await page.getByRole('button', { name: '添加反向代理' }).click();
    await page.waitForTimeout(500);

    // 開啟 SSL
    await page.evaluate(() => {
      document.getElementById('ssl').value = '1';
      checkSsl('1');
    });
    await page.waitForTimeout(500);

    // 檢查標籤文字
    const content = await page.content();
    expect(content).toContain('TLSv1');
    expect(content).toContain('TLSv1.1');

    // 應包含「已棄用」或「已弃用」或「deprecated」
    const hasDeprecatedLabel = content.includes('已棄用') || content.includes('已弃用') || content.includes('deprecated');
    expect(hasDeprecatedLabel).toBe(true);
  });

  test('TLSv1 和 TLSv1.1 預設不勾選', async ({ page }) => {
    await page.getByRole('button', { name: '添加反向代理' }).click();
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      document.getElementById('ssl').value = '1';
      checkSsl('1');
    });
    await page.waitForTimeout(500);

    // TLSv1 checkbox 不應被勾選
    const tlsv1 = page.locator('input[value="TLSv1"]');
    await expect(tlsv1).not.toBeChecked();

    // TLSv1.1 checkbox 不應被勾選
    const tlsv11 = page.locator('input[value="TLSv1.1"]');
    await expect(tlsv11).not.toBeChecked();
  });

  test('TLSv1.2 和 TLSv1.3 預設勾選', async ({ page }) => {
    await page.getByRole('button', { name: '添加反向代理' }).click();
    await page.waitForTimeout(500);

    await page.evaluate(() => {
      document.getElementById('ssl').value = '1';
      checkSsl('1');
    });
    await page.waitForTimeout(500);

    // TLSv1.2 應被勾選
    const tlsv12 = page.locator('input[value="TLSv1.2"]');
    await expect(tlsv12).toBeChecked();

    // TLSv1.3 應被勾選
    const tlsv13 = page.locator('input[value="TLSv1.3"]');
    await expect(tlsv13).toBeChecked();
  });

});
