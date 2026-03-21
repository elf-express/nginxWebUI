const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('預設 http 參數', () => {

  test('應包含 gzip 相關預設參數', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('table');

    const content = await page.content();
    expect(content).toContain('gzip');
    expect(content).toContain('gzip_min_length');
    expect(content).toContain('gzip_comp_level');
    expect(content).toContain('gzip_types');
  });

  test('應包含安全 Headers 預設參數', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('table');

    const content = await page.content();
    expect(content).toContain('X-Frame-Options');
    expect(content).toContain('X-Content-Type-Options');
    expect(content).toContain('X-XSS-Protection');
  });

});

test.describe('預設模板', () => {

  test('應有預設模板（含安全、跨域、限流等）', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    await page.waitForSelector('table');

    const content = await page.content();
    // 代理類
    expect(content).toContain('WebSocket Proxy');
    expect(content).toContain('Proxy Headers');
    expect(content).toContain('Large File Upload');
    // 緩存類
    expect(content).toContain('Static File Cache');
    expect(content).toContain('Proxy Cache');
    // 跨域
    expect(content).toContain('CORS');
    // 限流
    expect(content).toContain('Rate Limit');
    // 安全
    expect(content).toContain('Security Headers');
    expect(content).toContain('Hide Server Info');
    // GeoIP
    expect(content).toContain('GeoIP');
    // CrowdSec
    expect(content).toContain('CrowdSec');
  });

  test('WebSocket Proxy 模板應配置到 location', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    await page.waitForSelector('table');

    // 表格中 WebSocket Proxy 那行應顯示 location 相關文字
    const row = page.locator('tr', { hasText: 'WebSocket Proxy' });
    const rowText = await row.textContent();
    expect(rowText).toContain('location');
  });

  test('Large File Upload 模板應配置到 server', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    await page.waitForSelector('table');

    const row = page.locator('tr', { hasText: 'Large File Upload' });
    const rowText = await row.textContent();
    expect(rowText).toContain('server');
  });

});
