const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('參數模板分組編輯器', () => {
  test('新增彈窗含分組下拉且參數 textarea 高度約 40px', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    await page.locator('button', { hasText: /添加参数模板|添加參數模板|Add parameter template/ }).click();
    await page.waitForSelector('#windowDiv', { state: 'visible' });
    // Layui form.render() hides native <select>; assert presence + name input visibility
    await expect(page.locator('#groupName')).toBeAttached();
    await expect(page.locator('#name')).toBeVisible();
    await expect(page.locator('#def')).toBeAttached();
    // Group dropdown should be rendered in the dialog (label + Layui select UI)
    await expect(page.locator('#windowDiv').getByText(/分组|分組|Group/).first()).toBeVisible();

    // add one param row
    await page.locator('#windowDiv button', { hasText: /添加参数|添加參數|Add parameter/ }).click();
    const ta = page.locator('#paramList textarea').first();
    await expect(ta).toBeVisible();
    const box = await ta.boundingBox();
    expect(box.height).toBeLessThanOrEqual(56); // 40px + padding
    expect(box.height).toBeGreaterThanOrEqual(32);
  });

  test('列表存在分組 collapse（含 CORS 或 compress）', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    const title = page.locator('.layui-colla-title');
    await expect(title.first()).toBeVisible();
    const text = await page.locator('.layui-collapse').innerText();
    expect(text.length).toBeGreaterThan(20);
    // Seed groups should include cors/compress-related content (case-insensitive)
    expect(/cors|compress|gzip|跨域|壓縮|压缩/i.test(text)).toBeTruthy();
  });
});
