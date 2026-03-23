const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('啟用配置 - 編輯模式', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForSelector('.CodeMirror');
  });

  test('編輯模式按鈕可見，退出按鈕隱藏', async ({ page }) => {
    const editBtn = page.locator('#editModeBtn');
    const exitBtn = page.locator('#exitEditBtn');

    await expect(editBtn).toBeVisible();
    await expect(exitBtn).toBeHidden();
  });

  test('進入編輯模式後顯示橫幅與退出按鈕', async ({ page }) => {
    // 呼叫 enterEditMode 打開 layer.confirm
    await page.evaluate(() => { enterEditMode(); });
    await page.waitForTimeout(500);

    // 點擊 layer.confirm 的確認按鈕
    await page.locator('.layui-layer-btn a').first().click();
    await page.waitForTimeout(500);

    // 橫幅可見
    const banner = page.locator('#editModeBanner');
    await expect(banner).toBeVisible();

    // 退出按鈕可見，進入按鈕隱藏
    await expect(page.locator('#exitEditBtn')).toBeVisible();
    await expect(page.locator('#editModeBtn')).toBeHidden();
  });

  test('退出編輯模式後恢復正常', async ({ page }) => {
    // 進入
    await page.evaluate(() => { enterEditMode(); });
    await page.waitForTimeout(500);
    await page.locator('.layui-layer-btn a').first().click();
    await page.waitForTimeout(500);

    // 退出
    await page.evaluate(() => { exitEditMode(); });
    await page.waitForTimeout(500);
    await page.locator('.layui-layer-btn a').first().click();
    await page.waitForTimeout(1000);

    // 橫幅隱藏
    await expect(page.locator('#editModeBanner')).toBeHidden();
    // 進入按鈕恢復可見
    await expect(page.locator('#editModeBtn')).toBeVisible();
  });

  test('編輯模式中左側編輯器有橙色邊框', async ({ page }) => {
    await page.evaluate(() => { enterEditMode(); });
    await page.waitForTimeout(500);
    await page.locator('.layui-layer-btn a').first().click();
    await page.waitForTimeout(500);

    // 左側 CodeMirror 的邊框應為 2px solid #FF5722 (瀏覽器轉為 rgb)
    const cmWrap = page.locator('.CodeMirror').first();
    const border = await cmWrap.evaluate(el => el.style.border);
    expect(border).toContain('rgb(255, 87, 34)');
  });

});
