const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('IP 標籤編輯器美化', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // ─── 獨立頁面 (/adminPage/denyAllow) ───

  test('獨立頁：格式提示可見', async ({ page }) => {
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('table');

    // 點新增按鈕打開 modal
    await page.locator('button', { hasText: /添加|新增|Add/ }).first().click();
    await page.waitForTimeout(500);

    const hint = page.locator('.ip-format-hint');
    await expect(hint).toBeVisible();
    const text = await hint.textContent();
    expect(text).toContain('192.168.1.1');
    expect(text).toContain('/16');
  });

  test('獨立頁：空狀態有圖示', async ({ page }) => {
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('table');

    await page.locator('button', { hasText: /添加|新增|Add/ }).first().click();
    await page.waitForTimeout(500);

    const emptyState = page.locator('.ip-empty-state');
    await expect(emptyState).toBeVisible();
    // 應有 icon
    const icon = emptyState.locator('i.layui-icon');
    await expect(icon).toBeVisible();
  });

  test('獨立頁：輸入有效 IP 產生 pill 藍色標籤', async ({ page }) => {
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('table');

    await page.locator('button', { hasText: /添加|新增|Add/ }).first().click();
    await page.waitForTimeout(500);

    await page.locator('#tagInput').fill('192.168.1.100');
    await page.locator('#tagInput').press('Enter');
    await page.waitForTimeout(300);

    const tag = page.locator('.ip-tag-valid');
    await expect(tag).toBeVisible();
    const text = await tag.textContent();
    expect(text).toContain('192.168.1.100');
  });

  test('獨立頁：輸入無效 IP 產生紅色邊框標籤', async ({ page }) => {
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('table');

    await page.locator('button', { hasText: /添加|新增|Add/ }).first().click();
    await page.waitForTimeout(500);

    await page.locator('#tagInput').fill('999.999.999.999');
    await page.locator('#tagInput').press('Enter');
    await page.waitForTimeout(300);

    const tag = page.locator('.ip-tag-invalid');
    await expect(tag).toBeVisible();
  });

  test('獨立頁：空列表存檔被攔截', async ({ page }) => {
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('table');

    // 用 JS 打開新增對話框
    await page.evaluate(() => { add(); });
    await page.waitForTimeout(500);

    // 填寫名稱
    await page.locator('#name').fill('test-empty');
    // 直接呼叫 addOver（不添加任何 IP）
    await page.evaluate(() => { addOver(); });
    await page.waitForTimeout(500);

    // 應看到 layer.msg 提示（不應 reload）
    const msg = page.locator('.layui-layer-msg');
    await expect(msg).toBeVisible({ timeout: 3000 });
  });

  test('獨立頁：含無效 IP 存檔彈出確認框', async ({ page }) => {
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('table');

    // 用 JS 打開新增對話框
    await page.evaluate(() => { add(); });
    await page.waitForTimeout(500);

    await page.locator('#name').fill('test-invalid');
    await page.locator('#tagInput').fill('bad-ip');
    await page.locator('#tagInput').press('Enter');
    await page.waitForTimeout(300);

    // 呼叫 addOver 觸發驗證
    await page.evaluate(() => { addOver(); });
    await page.waitForTimeout(500);

    // 應彈出 layer.confirm
    const confirm = page.locator('.layui-layer-dialog');
    await expect(confirm).toBeVisible({ timeout: 3000 });
  });

  // ─── 合併頁面 (/adminPage/protectionCert) ───

  test('合併頁：格式提示可見', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    const hint = page.locator('.ip-format-hint');
    await expect(hint).toBeVisible();
    const text = await hint.textContent();
    expect(text).toContain('192.168.1.1');
  });

  test('合併頁：pill 標籤樣式正確', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    await page.locator('#daTagInput').fill('10.0.0.1');
    await page.locator('#daTagInput').press('Enter');
    await page.waitForTimeout(300);

    const tag = page.locator('.ip-tag-valid');
    await expect(tag).toBeVisible();

    // 驗證 pill 樣式 (border-radius: 12px)
    const radius = await tag.evaluate(el => getComputedStyle(el).borderRadius);
    expect(radius).toBe('12px');
  });

  test('合併頁：空列表存檔被攔截', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    await page.locator('#daName').fill('test-empty');
    // 點提交
    await page.evaluate(() => { denyAllowNS.addOver(); });
    await page.waitForTimeout(500);

    const msg = page.locator('.layui-layer-msg');
    await expect(msg).toBeVisible({ timeout: 3000 });
  });

});
