const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 開 add-proxy modal → 開 http 參數 panel,回傳 panel 已就緒的 page
async function openPanel(page) {
  await page.getByRole('button', { name: /添加反向代理/ }).click();
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}

async function reopenServerPanel(page) {
  await page.goto('/adminPage/server');
  await page.waitForSelector('table');
  await openPanel(page);
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

    try {
      // 重載 server 頁 + 重開 panel,驗證該 id 未勾（enable=false 已落 DB）
      await reopenServerPanel(page);
      const stillChecked = await page.evaluate((id) => {
        const scope = document.getElementById('httpParamPanelDiv');
        const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
        return cb ? cb.checked : null;
      }, targetId);
      expect(stillChecked).toBe(false);
    } finally {
      // 還原:即使上面斷言失敗也重新啟用該項 + 存檔,避免污染共用 DB
      await reopenServerPanel(page);
      await page.evaluate((id) => {
        const scope = document.getElementById('httpParamPanelDiv');
        const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
        if (cb) { cb.checked = true; cb.dispatchEvent(new Event('change')); }
      }, targetId);
      await page.locator('button[onclick="saveHttpParamPanel()"]').click();
      await page.waitForTimeout(1000);
    }
  });

  test('存檔顯示後端 i18n 成功 toast（測試環境 nginxExe 未設 → 略過預檢照存）', async ({ page }) => {
    await openPanel(page);
    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    const toast = page.locator('.layui-layer-msg');
    await expect(toast).toBeVisible();
    // 驗證 toast 文字為後端 i18n（httpParamSaved / httpParamPrecheckSkipped）,
    // 非空白/undefined —— 守住 renderSuccess 訊息放 obj、前端須讀 data.obj 的接線。
    const msg = await toast.textContent();
    expect(msg).toMatch(/已存|Saved/i);
    expect(msg).not.toContain('undefined');
  });

  test('panel 顯示「全域設定」警示提示', async ({ page }) => {
    await openPanel(page);
    const text = await page.evaluate(() => document.getElementById('httpParamPanelDiv').textContent);
    // 精確驗證新增的 httpParamGlobalHint(非說明裡泛泛的「全局」字):特徵是「套用到所有 server」
    expect(text).toMatch(/套用到所有 server|applies to all servers/i);
  });
});
