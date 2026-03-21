const { test, expect } = require('@playwright/test');
const { login, BASE_URL } = require('./helpers');
const http = require('http');

/**
 * 發送 HTTP 請求（不經 Playwright，直接 Node.js）
 */
function httpGet(url) {
  return new Promise((resolve) => {
    http.get(url, (res) => {
      resolve(res.statusCode);
    }).on('error', () => {
      resolve(0);
    });
  });
}

test.describe('安全防護 — CrowdSec', () => {

  test('正常登入請求應正常回應（200）', async ({ page }) => {
    const response = await page.goto('/adminPage/login');
    expect(response.status()).toBe(200);
  });

  test('正常頁面存取不受影響', async ({ page }) => {
    await login(page);

    // 存取多個頁面，都應正常
    const pages = [
      '/adminPage/monitor',
      '/adminPage/http',
      '/adminPage/server',
      '/adminPage/conf',
      '/adminPage/template',
    ];

    for (const p of pages) {
      const response = await page.goto(p);
      expect(response.status()).toBe(200);
    }
  });

  test('敏感路徑應返回非 200（404 或 403）', async () => {
    // 這些是攻擊者常掃描的路徑，nginx 應返回 404
    const sensitivePaths = [
      '/.env',
      '/.git/config',
      '/wp-admin/',
      '/phpmyadmin/',
      '/admin.php',
    ];

    for (const path of sensitivePaths) {
      const status = await httpGet(`${BASE_URL}${path}`);
      // 應該不是 200（可能是 404 或 403）
      expect(status).not.toBe(200);
    }
  });

});
