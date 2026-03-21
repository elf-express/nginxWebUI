const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('Conf 語法高亮', () => {

  test('conf 頁面應使用 CodeMirror', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForTimeout(2000);

    // CodeMirror 元素應存在
    const cm = page.locator('.CodeMirror');
    await expect(cm.first()).toBeVisible();
  });

  test('應使用 monokai 深色主題', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForTimeout(2000);

    // monokai 主題 class 應存在
    const monokai = page.locator('.cm-s-monokai');
    const count = await monokai.count();
    expect(count).toBeGreaterThan(0);
  });

  test('應有語法高亮 token', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForTimeout(2000);

    // CodeMirror 會為不同 token 加上 cm-* class
    // nginx mode 會產生 cm-keyword, cm-variable, cm-number 等
    const hasTokens = await page.evaluate(() => {
      const tokens = document.querySelectorAll('.cm-keyword, .cm-variable, .cm-number, .cm-string');
      return tokens.length;
    });
    expect(hasTokens).toBeGreaterThan(0);
  });

  test('左右兩側都應有 CodeMirror', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForTimeout(2000);

    // 應有兩個 CodeMirror 實例（左側可編輯 + 右側唯讀）
    const cmCount = await page.locator('.CodeMirror').count();
    expect(cmCount).toBeGreaterThanOrEqual(2);
  });

});
