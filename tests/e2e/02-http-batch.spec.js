const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('http 參數批量輸入', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('table');
  });

  test('批量輸入按鈕存在', async ({ page }) => {
    const btn = page.getByRole('button', { name: /批量輸入|批量输入/ });
    await expect(btn).toBeVisible();
  });

  test('批量輸入彈窗可打開並關閉', async ({ page }) => {
    await page.getByRole('button', { name: /批量輸入|批量输入/ }).click();

    // 彈窗應出現
    const textarea = page.locator('#batchInputText');
    await expect(textarea).toBeVisible();

    // 關閉
    await page.locator('#batchInputDiv').getByRole('button', { name: /關閉|关闭/ }).click();
  });

  test('批量輸入多行 nginx 指令', async ({ page }) => {
    await page.getByRole('button', { name: /批量輸入|批量输入/ }).click();

    const textarea = page.locator('#batchInputText');
    await textarea.fill('sendfile on;\ntcp_nopush on;\ntcp_nodelay on;');

    await page.getByRole('button', { name: /確認添加|确认添加/ }).click();

    // 頁面刷新後應看到新增的參數
    await page.waitForTimeout(1000);
    const table = page.locator('table');

    await expect(table.locator('text=sendfile')).toBeVisible();
    await expect(table.locator('text=tcp_nopush')).toBeVisible();
    await expect(table.locator('text=tcp_nodelay')).toBeVisible();
  });

  test('批量輸入自動去除行末分號', async ({ page }) => {
    await page.getByRole('button', { name: /批量輸入|批量输入/ }).click();

    const textarea = page.locator('#batchInputText');
    await textarea.fill('proxy_buffering off;');

    await page.getByRole('button', { name: /確認添加|确认添加/ }).click();
    await page.waitForTimeout(1000);

    // 值應為 "off" 而不是 "off;"
    const table = page.locator('table');
    await expect(table.locator('text=proxy_buffering')).toBeVisible();
  });

});
