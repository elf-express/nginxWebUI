const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 開 add-proxy modal → 開 http 參數 panel,回傳 panel 已就緒的 page
async function openPanel(page) {
  await page.getByRole('button', { name: /添加反向代理/ }).click();
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}

test.describe('server modal — ① http 參數 panel 存檔（phase 2）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
  });

  test('取消一項存檔後,重開 panel 該項仍為未勾（enable 落 DB）', async ({ page }) => {
    await openPanel(page);

    // 取消第一個勾選項,記其 id
    const targetId = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"]:checked');
      cb.checked = false;
      cb.dispatchEvent(new Event('change'));
      return cb.value;
    });
    expect(targetId).toBeTruthy();

    // 存檔
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    await page.waitForTimeout(1000); // 等 ajax + layer

    // 重載 server 頁 + 重開 panel
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await openPanel(page);

    // 該 id 的 checkbox 應為未勾（enable=false 已落 DB）
    const stillChecked = await page.evaluate((id) => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
      return cb ? cb.checked : null;
    }, targetId);
    expect(stillChecked).toBe(false);

    // 還原:重新勾選該項 + 存檔,避免影響其他 spec 的共用 DB
    await page.evaluate((id) => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
      cb.checked = true;
      cb.dispatchEvent(new Event('change'));
    }, targetId);
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    await page.waitForTimeout(1000);
  });

  test('存檔顯示成功 toast（測試環境 nginxExe 未設 → 略過預檢照存）', async ({ page }) => {
    await openPanel(page);
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    const toast = page.locator('.layui-layer-msg');
    await expect(toast).toBeVisible();
  });

  test('panel 顯示「全域設定」警示提示', async ({ page }) => {
    await openPanel(page);
    const text = await page.evaluate(() => document.getElementById('httpParamPanelDiv').textContent);
    // 精確驗證新增的 httpParamGlobalHint(非說明裡泛泛的「全局」字):特徵是「套用到所有 server」
    expect(text).toMatch(/套用到所有 server|applies to all servers/i);
  });
});
