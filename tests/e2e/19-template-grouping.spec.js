const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('參數模板分組顯示', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/template');
    await page.waitForSelector('.layui-collapse');
  });

  test('頁面使用可折疊分組佈局', async ({ page }) => {
    const collapse = page.locator('.layui-collapse');
    await expect(collapse).toBeVisible();

    const groups = page.locator('.layui-colla-item');
    const count = await groups.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('每組有標題和數量 badge', async ({ page }) => {
    const firstTitle = page.locator('.layui-colla-title').first();
    await expect(firstTitle).toBeVisible();

    // 應有數量 badge
    const badge = firstTitle.locator('.layui-badge');
    await expect(badge).toBeVisible();
  });

  test('每組有說明文字', async ({ page }) => {
    const firstTitle = page.locator('.layui-colla-title').first();
    const desc = firstTitle.locator('span').last();
    await expect(desc).toBeVisible();
    const text = await desc.textContent();
    expect(text.length).toBeGreaterThan(0);
  });

  test('代理組包含 WebSocket Proxy', async ({ page }) => {
    const content = await page.content();
    expect(content).toContain('WebSocket Proxy');
  });

  test('緩存組包含 Static File Cache', async ({ page }) => {
    const content = await page.content();
    expect(content).toContain('Static File Cache');
  });

  test('安全組包含 Security Headers', async ({ page }) => {
    const content = await page.content();
    expect(content).toContain('Security Headers');
  });

  test('每組有獨立 checkbox', async ({ page }) => {
    const groupCheckboxes = page.locator('input[lay-filter="checkGroup"]');
    const count = await groupCheckboxes.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('編輯對話框仍可正常使用', async ({ page }) => {
    // 找第一個編輯按鈕
    const editBtn = page.locator('button', { hasText: /编辑|編輯|Edit/ }).first();
    await editBtn.click();
    await page.waitForTimeout(500);

    // 對話框應可見
    const windowDiv = page.locator('#windowDiv');
    await expect(windowDiv).toBeVisible();

    // 名稱欄位應有值
    const nameVal = await page.locator('#name').inputValue();
    expect(nameVal.length).toBeGreaterThan(0);
  });

  test('新增按鈕可正常打開空對話框', async ({ page }) => {
    await page.locator('button', { hasText: /添加参数模板|添加參數模板|Add parameter template/ }).first().click();
    await page.waitForTimeout(500);

    const windowDiv = page.locator('#windowDiv');
    await expect(windowDiv).toBeVisible();

    const nameVal = await page.locator('#name').inputValue();
    expect(nameVal).toBe('');
  });

});
