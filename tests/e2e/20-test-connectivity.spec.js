const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('啟用配置 - 測試連線', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForSelector('.CodeMirror');
  });

  test('測試連線按鈕存在', async ({ page }) => {
    const btn = page.locator('button', { hasText: /测试连线|測試連線|Test Connectivity/ });
    await expect(btn).toBeVisible();
  });

  test('testConnectivity 函數存在', async ({ page }) => {
    const exists = await page.evaluate(() => typeof testConnectivity === 'function');
    expect(exists).toBe(true);
  });

  test('showConnectivityResults 函數存在', async ({ page }) => {
    const exists = await page.evaluate(() => typeof showConnectivityResults === 'function');
    expect(exists).toBe(true);
  });

  test('空結果顯示提示訊息', async ({ page }) => {
    const msg = await page.evaluate(() => {
      return new Promise((resolve) => {
        var origMsg = layer.msg;
        layer.msg = function(text) { resolve(text); };
        showConnectivityResults([]);
        layer.msg = origMsg;
      });
    });
    expect(msg).toBeTruthy();
  });

  test('結果表格正確渲染 OK 和 FAIL', async ({ page }) => {
    await page.evaluate(() => {
      showConnectivityResults([
        { server: 'example.com :443', location: '/', destination: '10.10.10.1:8080', status: 'OK' },
        { server: 'test.com :80', location: '/api', destination: '192.168.1.1:3000', status: 'FAIL' }
      ]);
    });

    const layerContent = page.locator('.layui-layer-content');
    await expect(layerContent).toBeVisible();

    await expect(layerContent.locator('text=10.10.10.1:8080')).toBeVisible();
    await expect(layerContent.locator('text=OK')).toBeVisible();

    await expect(layerContent.locator('text=192.168.1.1:3000')).toBeVisible();
    await expect(layerContent.locator('text=FAIL')).toBeVisible();
  });

});
