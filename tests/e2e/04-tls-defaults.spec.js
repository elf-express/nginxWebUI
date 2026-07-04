const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// TLSv1 / TLSv1.1 已於 2026-06-30 移除(IETF RFC 8996 deprecated),
// server add-proxy modal 只保留 TLSv1.2 / TLSv1.3,且預設勾選。
test.describe('TLS 版本預設值', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await page.getByRole('button', { name: '添加反向代理' }).click();
    await page.waitForTimeout(500);
    // 開啟 SSL 讓 protocols checkbox 區塊顯示
    await page.evaluate(() => {
      document.getElementById('ssl').value = '1';
      checkSsl('1');
    });
    await page.waitForTimeout(500);
  });

  test('TLSv1 和 TLSv1.1 已移除（IETF RFC 8996）', async ({ page }) => {
    // 精確 value 匹配:'TLSv1' / 'TLSv1.1' 不會誤中 'TLSv1.2'
    await expect(page.locator('input.protocols[value="TLSv1"]')).toHaveCount(0);
    await expect(page.locator('input.protocols[value="TLSv1.1"]')).toHaveCount(0);
  });

  test('TLSv1.2 和 TLSv1.3 存在且預設勾選', async ({ page }) => {
    const tlsv12 = page.locator('input[value="TLSv1.2"]');
    const tlsv13 = page.locator('input[value="TLSv1.3"]');
    await expect(tlsv12).toHaveCount(1);
    await expect(tlsv13).toHaveCount(1);
    await expect(tlsv12).toBeChecked();
    await expect(tlsv13).toBeChecked();
  });

});
