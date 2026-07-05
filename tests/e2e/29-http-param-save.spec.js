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

// 存檔;phase 3 起 mutex group(geoip)勾 >1 會先跳 layer.confirm,若出現則點確認後才真存。
async function saveAndConfirm(page) {
  await page.locator('button[onclick="saveHttpParamPanel()"]').click();
  const confirmBtn = page.locator('.layui-layer-dialog .layui-layer-btn0');
  try { await confirmBtn.click({ timeout: 2000 }); } catch (e) { /* 無 mutex confirm */ }
  await page.waitForTimeout(900);
}

test.describe('server modal — ① http 參數 panel 存檔（phase 2）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
  });

  test('取消一個 optional 項存檔後,重開該項未勾（enable 落 DB）', async ({ page }) => {
    await openPanel(page);

    // 取一個 optional(非 locked disabled、非 mutex)可安全取消的項並取消
    // (phase 3:locked 不可關 + 後端 enforce;mutex 會跳 warn)
    const targetId = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"]:not([disabled]):not([data-mutex]):checked');
      cb.checked = false;
      cb.dispatchEvent(new Event('change'));
      return cb.value;
    });
    expect(targetId).toBeTruthy();

    await saveAndConfirm(page);

    try {
      await reopenServerPanel(page);
      const stillChecked = await page.evaluate((id) => {
        const scope = document.getElementById('httpParamPanelDiv');
        const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
        return cb ? cb.checked : null;
      }, targetId);
      expect(stillChecked).toBe(false);
    } finally {
      // 還原:重新啟用該項 + 存檔,避免污染共用 DB
      await reopenServerPanel(page);
      await page.evaluate((id) => {
        const scope = document.getElementById('httpParamPanelDiv');
        const cb = scope.querySelector('input[name="httpParamItem"][value="' + id + '"]');
        if (cb) { cb.checked = true; cb.dispatchEvent(new Event('change')); }
      }, targetId);
      await saveAndConfirm(page);
    }
  });

  test('存檔顯示後端 i18n 成功 toast（測試環境 nginxExe 未設 → 略過預檢照存）', async ({ page }) => {
    await openPanel(page);
    // 全勾含 geoip mutex → saveAndConfirm 會點掉 mutex 提示後真存
    await saveAndConfirm(page);
    const toast = page.locator('.layui-layer-msg');
    await expect(toast).toBeVisible();
    // 驗證 toast 文字為後端 i18n（httpParamSaved / httpParamPrecheckSkipped）,非空白/undefined
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
