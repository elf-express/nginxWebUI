// server 儲存流程端對端:新增 → 存檔 → 重載驗證持久化 → 編輯 → enable 切換 → 刪除清理。
// 背景:此前沒有任何 spec 觸發過 /adminPage/server/addOver(儲存零覆蓋),
// 生產環境(PostgreSQL)儲存壞掉時測試照樣全綠。本 spec 同時被 playwright.pg.config.js
// 選入 PG smoke,於 PostgreSQL 上驗證同一條路徑。
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('server 儲存流程 — 新增/編輯/enable 持久化', () => {
  const NAME = 'e2e-save-33.test';

  test('新增 → 存檔 → 編輯 → enable 切換 → 刪除,全程持久化', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');

    // === 新增 ===
    await page.getByRole('button', { name: /添加反向代理|新增反向代理/ }).click();
    await page.waitForSelector('#listen', { state: 'visible' });

    // 殘留移除回歸:編輯視窗不應再有黑白名單綁定 UI
    expect(await page.locator("button[onclick='setDenyAllow()']").count()).toBe(0);
    expect(await page.locator('#denyAllowDiv').count()).toBe(0);

    await page.locator('#serverName').fill(NAME);
    await page.locator('#listen').fill('9001');

    // 成功後 JS 會 location.reload(),導航後 body 不可讀 → 只驗 HTTP ok,
    // 持久化證明交給重載後的表格斷言
    const [addResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/adminPage/server/addOver')),
      page.locator(".layui-layer button[onclick='addOver()']").click(),
    ]);
    expect(addResp.ok()).toBeTruthy();
    await page.waitForLoadState('load');

    // 重載驗證持久化(捕捉「假成功、沒寫進 DB」的靜默失敗)
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    const row = page.locator('tr', { hasText: NAME });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('9001');

    // === 編輯 ===
    await row.locator("button[onclick*=\"edit('\"]").click();
    await page.waitForSelector('#listen', { state: 'visible' });
    await expect(page.locator('#serverName')).toHaveValue(NAME);
    await page.locator('#listen').fill('9002');

    const [editResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/adminPage/server/addOver')),
      page.locator(".layui-layer button[onclick='addOver()']").click(),
    ]);
    expect(editResp.ok()).toBeTruthy();
    await page.waitForLoadState('load');

    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await expect(page.locator('tr', { hasText: NAME })).toContainText('9002');

    // === enable 切換(Boolean 欄位 update 路徑;PG 上曾因型別表現形不一致而失效) ===
    const row2 = page.locator('tr', { hasText: NAME });
    await expect(row2.locator('input[name="enable"]')).toBeChecked();
    const [enableResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/adminPage/server/setEnable')),
      row2.locator('.layui-form-switch').click(),
    ]);
    expect(enableResp.ok()).toBeTruthy();

    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await expect(page.locator('tr', { hasText: NAME }).locator('input[name="enable"]')).not.toBeChecked();

    // === 刪除清理(共用 DB,避免影響其他 spec;del() 用原生 confirm → 需 dialog handler) ===
    page.on('dialog', (dialog) => dialog.accept());
    const row3 = page.locator('tr', { hasText: NAME });
    const [delResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/adminPage/server/del')),
      row3.locator("button[onclick*=\"del('\"]").click(),
    ]);
    expect(delResp.ok()).toBeTruthy();
    await page.waitForLoadState('load');

    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await expect(page.locator('tr', { hasText: NAME })).toHaveCount(0);
  });
});
