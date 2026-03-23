const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('HTTP 參數分組顯示', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('.layui-collapse');
  });

  test('頁面使用可折疊分組佈局', async ({ page }) => {
    const collapse = page.locator('.layui-collapse');
    await expect(collapse).toBeVisible();

    // 應有多個分組
    const groups = page.locator('.layui-colla-item');
    const count = await groups.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('每個分組標題有參數數量 badge', async ({ page }) => {
    // 每個 colla-title 應有 layui-badge（灰色數字 badge）
    const badges = page.locator('.layui-colla-title .layui-badge.layui-bg-gray');
    const count = await badges.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('gzip 分組包含 gzip 相關參數', async ({ page }) => {
    const content = await page.content();
    expect(content).toContain('gzip');
    expect(content).toContain('gzip_min_length');
    expect(content).toContain('gzip_types');
  });

  test('安全 Headers 分組包含 add_header 參數', async ({ page }) => {
    const content = await page.content();
    expect(content).toContain('X-Frame-Options');
    expect(content).toContain('X-Content-Type-Options');
  });

  test('分組可折疊/展開', async ({ page }) => {
    // 點擊第一個分組標題來折疊
    const firstTitle = page.locator('.layui-colla-title').first();
    await firstTitle.click();
    await page.waitForTimeout(500);

    // 第一個 content 應被折疊（失去 layui-show class）
    const firstContent = page.locator('.layui-colla-content').first();
    const hasShow = await firstContent.evaluate(el => el.classList.contains('layui-show'));
    expect(hasShow).toBe(false);

    // 再次點擊展開
    await firstTitle.click();
    await page.waitForTimeout(500);
    const hasShowAfter = await firstContent.evaluate(el => el.classList.contains('layui-show'));
    expect(hasShowAfter).toBe(true);
  });

  test('每個分組內有獨立的表格', async ({ page }) => {
    const tables = page.locator('.layui-colla-content table.layui-table');
    const count = await tables.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

});
