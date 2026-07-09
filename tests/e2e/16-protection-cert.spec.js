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

  test('有六個 Tab：IP資料庫、黑名單、白名單、國家、ASN、證書', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    const tabs = page.locator('.layui-tab-title li');
    await expect(tabs).toHaveCount(6);
  });

  test('Tab 可切換到國家存取控制（#geoTabContent）', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    // 6-tab 重構後：國家存取控制（#geoTabContent）是第 4 個 tab（index 3）
    await page.locator('.layui-tab-title li').nth(3).click();
    await page.waitForTimeout(500);

    const geoTab = page.locator('#geoTabContent');
    await expect(geoTab).toBeVisible({ timeout: 5000 });
  });

  test('Tab 可切換到證書管理', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    // 6-tab 重構後：證書是第 6 個 tab（IP資料庫／黑名單／白名單／國家／ASN／證書）→ index 5
    await page.locator('.layui-tab-title li').nth(5).click();
    await page.waitForTimeout(500);

    const certTab = page.locator('.layui-tab-item').nth(5);
    const isActive = await certTab.evaluate(el => el.classList.contains('layui-show'));
    expect(isActive).toBe(true);
  });

  test('黑白名單 Tab 使用 denyAllowNS 命名空間', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    const content = await page.content();
    expect(content).toContain("denyAllowNS.add('deny')");
    expect(content).toContain("denyAllowNS.add('allow')");
    expect(content).toContain("denyAllowNS.delMany('black')");
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
