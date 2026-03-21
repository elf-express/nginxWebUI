const { test, expect } = require('@playwright/test');
const { login, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA } = require('./helpers');

test.describe('登入功能', () => {

  test('首次登入成功', async ({ page }) => {
    await login(page);

    // 驗證已登入 — 頁面應顯示「當前用戶: admin」
    const userLink = page.locator('text=admin').first();
    await expect(userLink).toBeVisible();
  });

  test('密碼顯示/隱藏切換（眼睛圖標）', async ({ page }) => {
    await page.goto('/adminPage/login');
    await page.waitForSelector('#pass');

    // 預設為 password 類型
    const passInput = page.locator('#pass');
    await expect(passInput).toHaveAttribute('type', 'password');

    // 填入密碼
    await passInput.fill(TEST_PASS);

    // 點擊眼睛圖標切換顯示
    const toggle = page.locator('#passToggle');
    await toggle.click();
    await expect(passInput).toHaveAttribute('type', 'text');

    // 再次點擊隱藏
    await toggle.click();
    await expect(passInput).toHaveAttribute('type', 'password');
  });

  test('錯誤密碼應顯示錯誤訊息', async ({ page }) => {
    await page.goto('/adminPage/login');
    await page.waitForSelector('#name');

    await page.locator('#name').fill(TEST_ADMIN);
    await page.locator('#pass').fill('wrongpass');
    await page.locator('#codeImg').waitFor();
    await page.locator('#code').fill(TEST_CAPTCHA);
    await page.getByRole('button', { name: /登入|登录/ }).click();

    // 應顯示錯誤提示
    const errorMsg = page.locator('text=/密碼錯誤|密码错误|用戶名密碼錯誤|用户名密码错误/');
    await expect(errorMsg).toBeVisible({ timeout: 5000 });
  });

});
