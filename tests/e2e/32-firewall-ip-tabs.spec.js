const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('防火牆管理 — 6 tab + IP 資料庫交叉驗證', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab-title');
  });

  test('① 6 個頂層 tab 存在', async ({ page }) => {
    const tabs = page.locator('.layui-tab-title > li');
    await expect(tabs).toHaveCount(6);
    const texts = (await tabs.allTextContents()).join('|');
    expect(texts).toMatch(/IP\s?資料庫|IP\s?数据库|IP Database/);
    expect(texts).toMatch(/黑名單|黑名单|Blacklist/);
    expect(texts).toMatch(/白名單|白名单|Whitelist/);
  });

  test('② IP資料庫表格有 Cloudflare 列 + 8 欄表頭', async ({ page }) => {
    const headers = await page.locator('#geoipTableBody').locator('xpath=../thead//th').allTextContents();
    expect(headers.length).toBe(8);
    const bodyText = await page.locator('#geoipTableBody').textContent();
    expect(bodyText).toMatch(/Cloudflare/);
  });

  test('③ 黑名單 / 白名單 tab 可切換且各自有新增按鈕', async ({ page }) => {
    await page.locator('.layui-tab-title > li').nth(1).click();
    await expect(page.locator("button[onclick=\"denyAllowNS.add('deny')\"]")).toBeVisible();
    await page.locator('.layui-tab-title > li').nth(2).click();
    await expect(page.locator("button[onclick=\"denyAllowNS.add('allow')\"]")).toBeVisible();
  });

  test('④ 狀態欄呈現徽章;待確認時伴隨原因文字', async ({ page }) => {
    const badges = page.locator('#geoipTableBody .layui-badge');
    await expect(badges.first()).toBeVisible();
    const warnBadge = page.locator('#geoipTableBody .layui-bg-orange');
    if (await warnBadge.count() > 0) {
      const rowText = await warnBadge.first().locator('xpath=ancestor::td').textContent();
      expect(rowText).toMatch(/·/);
    }
  });
});
