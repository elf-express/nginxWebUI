const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('Nginx 資訊顯示', () => {

  test('Nginx 資訊 API 回傳正確結構', async ({ page, baseURL }) => {
    await login(page);

    const response = await page.request.get(baseURL + '/adminPage/monitor/nginxInfo');
    const data = await response.json();
    expect(data.success).toBe(true);
    expect(data.obj).toHaveProperty('version');
    expect(data.obj).toHaveProperty('modules');
    expect(data.obj).toHaveProperty('hasGeoIp2');
    expect(Array.isArray(data.obj.modules)).toBe(true);
  });

  test('基本參數頁面可正常載入', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/basic');
    await page.waitForSelector('table');

    const content = await page.content();
    expect(content).toContain('worker_processes');
    expect(content).toContain('events');
  });

  test('啟用配置頁面可正常載入', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForSelector('table', { timeout: 10000 });

    const title = await page.title();
    expect(title).toContain('nginxWebUI');
  });
});
