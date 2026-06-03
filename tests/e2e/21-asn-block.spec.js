const { test, expect } = require('@playwright/test');
const { login, BASE_URL } = require('./helpers');

test.describe('ASN 封鎖管理', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('.layui-tab');
  });

  test('ASN 封鎖 Tab 存在', async ({ page }) => {
    const tab = page.locator('.layui-tab-title li', { hasText: /ASN/ });
    await expect(tab).toBeVisible();
  });

  test('切換到 ASN Tab 顯示表格', async ({ page }) => {
    // 點擊 ASN tab
    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
    await page.waitForTimeout(500);

    const table = page.locator('#asnTabContent table');
    await expect(table).toBeVisible();
  });

  test('新增 ASN 規則', async ({ page }) => {
    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
    await page.waitForTimeout(500);

    // 點擊新增按鈕
    await page.locator('#asnTabContent button', { hasText: /添加|新增|Add/ }).click();
    await page.waitForSelector('#asnWindowDiv', { state: 'visible' });

    // 填入 ASN
    await page.locator('#asnNumber').fill('4134');
    await page.locator('#asnOrgName').fill('China Telecom');

    // 提交
    await page.locator('#asnWindowDiv button', { hasText: /提交|Submit/ }).click();
    await page.waitForTimeout(1000);

    // 驗證列表出現
    const row = page.locator('#asnTableBody', { hasText: '4134' });
    await expect(row).toBeVisible();
    await expect(page.locator('#asnTableBody', { hasText: 'China Telecom' })).toBeVisible();
  });

  test('重複 ASN 會被拒絕', async ({ page }) => {
    // 先透過 API 新增
    await page.evaluate(async () => {
      await fetch(ctx + '/adminPage/asn/addOver', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'asn=9999&orgName=TestASN&enable=true'
      });
    });

    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
    await page.waitForTimeout(500);

    // 嘗試新增重複
    await page.locator('#asnTabContent button', { hasText: /添加|新增|Add/ }).click();
    await page.waitForSelector('#asnWindowDiv', { state: 'visible' });
    await page.locator('#asnNumber').fill('9999');
    await page.locator('#asnWindowDiv button', { hasText: /提交|Submit/ }).click();
    await page.waitForTimeout(1000);

    // 應顯示錯誤訊息
    const msg = page.locator('.layui-layer-content', { hasText: /已存在|already exists/ });
    await expect(msg).toBeVisible();
  });

  test('刪除 ASN 規則', async ({ page }) => {
    // 先透過 API 新增
    await page.evaluate(async () => {
      await fetch(ctx + '/adminPage/asn/addOver', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'asn=7777&orgName=DeleteMe&enable=true'
      });
    });

    // 重載頁面，讓伺服端渲染的表格帶入剛由 API 新增的 7777（原測試漏了這步而 timeout）
    await page.reload();
    await page.waitForSelector('.layui-tab');

    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
    await page.waitForTimeout(1000);

    // 找到刪除按鈕並點擊
    const row = page.locator('#asnTableBody tr', { hasText: '7777' });
    await expect(row).toBeVisible({ timeout: 5000 });
    await row.locator('button', { hasText: /删除|刪除|Del/ }).click();
    await page.waitForTimeout(500);

    // 確認刪除
    await page.locator('.layui-layer-btn0').click();
    await page.waitForTimeout(1000);

    // 驗證已刪除
    await expect(page.locator('#asnTableBody', { hasText: '7777' })).not.toBeVisible();
  });

});
