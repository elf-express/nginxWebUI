const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('站點資源（合併頁面）', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('站點資源頁面可正常載入', async ({ page }) => {
    await page.goto('/adminPage/siteResource');
    await page.waitForSelector('.layui-tab');
    const tab = page.locator('.layui-tab');
    await expect(tab).toBeVisible();
  });

  test('有兩個 Tab：靜態網頁和密碼文件', async ({ page }) => {
    await page.goto('/adminPage/siteResource');
    await page.waitForSelector('.layui-tab');

    const tabs = page.locator('.layui-tab-title li');
    await expect(tabs).toHaveCount(2);
  });

  test('Tab 可正常切換到密碼文件', async ({ page }) => {
    await page.goto('/adminPage/siteResource');
    await page.waitForSelector('.layui-tab');

    // 點擊第二個 tab（密碼文件）
    await page.locator('.layui-tab-title li').nth(1).click();
    await page.waitForTimeout(500);

    // 第二個 tab-item 變為可見
    const secondTab = page.locator('.layui-tab-item').nth(1);
    const isActive = await secondTab.evaluate(el => el.classList.contains('layui-show'));
    expect(isActive).toBe(true);
  });

  test('靜態網頁 Tab 有新增按鈕', async ({ page }) => {
    await page.goto('/adminPage/siteResource');
    await page.waitForSelector('.layui-tab');

    // WWW tab 中應有按鈕（使用 wwwNS.add）
    const content = await page.content();
    expect(content).toContain('wwwNS.add()');
  });

  test('密碼文件 Tab 有新增按鈕', async ({ page }) => {
    await page.goto('/adminPage/siteResource');
    await page.waitForSelector('.layui-tab');

    const content = await page.content();
    expect(content).toContain('pwdNS.add()');
  });

  test('選單中有站點資源連結', async ({ page }) => {
    await page.goto('/adminPage/siteResource');
    await page.waitForSelector('.layui-nav');

    const menuLink = page.locator('a[href*="/adminPage/siteResource"]');
    await expect(menuLink).toBeVisible();
  });

  test('原 WWW 頁面仍可訪問（向後相容）', async ({ page }) => {
    const response = await page.goto('/adminPage/www');
    expect(response.status()).toBe(200);
  });

  test('原密碼文件頁面仍可訪問（向後相容）', async ({ page }) => {
    const response = await page.goto('/adminPage/password');
    expect(response.status()).toBe(200);
  });

});
