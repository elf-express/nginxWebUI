const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('防護與證書（合併頁面）', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('防護與證書頁面可正常載入', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');
    const tab = page.locator('.layui-tab');
    await expect(tab).toBeVisible();
  });

  test('有三個 Tab：黑白名單、GeoIP、證書', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    const tabs = page.locator('.layui-tab-title li');
    await expect(tabs).toHaveCount(3);
  });

  test('Tab 可切換到 GeoIP', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.locator('.layui-tab-title li').nth(1).click();
    await page.waitForTimeout(500);

    const geoTab = page.locator('#geoTabContent');
    await expect(geoTab).toBeVisible({ timeout: 5000 });
  });

  test('Tab 可切換到證書管理', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.locator('.layui-tab-title li').nth(2).click();
    await page.waitForTimeout(500);

    const certTab = page.locator('.layui-tab-item').nth(2);
    const isActive = await certTab.evaluate(el => el.classList.contains('layui-show'));
    expect(isActive).toBe(true);
  });

  test('黑白名單 Tab 使用 denyAllowNS 命名空間', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    const content = await page.content();
    expect(content).toContain('denyAllowNS.add()');
    expect(content).toContain('denyAllowNS.delMany()');
  });

  test('選單中有防護與證書連結', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-nav');

    const menuLink = page.locator('a[href*="/adminPage/protectionCert"]');
    await expect(menuLink).toBeVisible();
  });

  test('原黑名單頁面仍可訪問（向後相容）', async ({ page }) => {
    const response = await page.goto('/adminPage/denyAllow');
    expect(response.status()).toBe(200);
  });

  test('原證書頁面仍可訪問（向後相容）', async ({ page }) => {
    const response = await page.goto('/adminPage/cert');
    expect(response.status()).toBe(200);
  });

});
