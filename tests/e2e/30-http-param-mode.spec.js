const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

async function openPanel(page) {
  await page.getByRole('button', { name: /添加反向代理/ }).click();
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}
async function reopenPanel(page) {
  await page.goto('/adminPage/server');
  await page.waitForSelector('table');
  await openPanel(page);
}
// 重新全選 + 存檔,把共用 DB 的 Http.enable 還原成全開
async function restoreAllEnabled(page) {
  await reopenPanel(page);
  await page.evaluate(() => {
    document.querySelectorAll('#httpParamPanelDiv input[name="httpParamItem"]:not([disabled])').forEach((cb) => {
      if (!cb.checked) { cb.checked = true; cb.dispatchEvent(new Event('change')); }
    });
  });
  await page.locator('button[onclick="saveHttpParamPanel()"]').click();
  // 全勾含 geoip mutex → 會跳 confirm,點確認才真存(否則還原 POST 不會送出)
  const confirmBtn = page.locator('.layui-layer-dialog .layui-layer-btn0');
  try { await confirmBtn.click({ timeout: 2000 }); } catch (e) { /* 無 mutex confirm */ }
  await page.waitForTimeout(1000);
}

test.describe('server modal — ① http 參數 panel 三態 mode（phase 3）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await openPanel(page);
  });

  test('locked group(base/realip)的 checkbox 為 disabled', async ({ page }) => {
    const info = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cbs = [...scope.querySelectorAll('input[name="httpParamItem"]')];
      const locked = cbs.filter((c) => ['base', 'realip'].includes(c.getAttribute('data-group')));
      return { lockedCount: locked.length, allDisabled: locked.every((c) => c.disabled) };
    });
    expect(info.lockedCount).toBeGreaterThan(0);   // 測試 DB 有 base/realip 指令
    expect(info.allDisabled).toBe(true);
  });

  test('後端 enforce:送空 checkedIds,locked 仍 enable=true', async ({ page }) => {
    try {
      const status = await page.evaluate(async () => {
        const res = await fetch('/adminPage/http/saveEnable', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: 'checkedIds=',
        });
        return res.status;
      });
      expect(status).toBe(200);

      await reopenPanel(page);
      const lockedAllChecked = await page.evaluate(() => {
        const scope = document.getElementById('httpParamPanelDiv');
        const locked = [...scope.querySelectorAll('input[name="httpParamItem"]')]
          .filter((c) => ['base', 'realip'].includes(c.getAttribute('data-group')));
        return locked.length > 0 && locked.every((c) => c.checked);
      });
      expect(lockedAllChecked).toBe(true);   // enforce:locked 不受空送影響
      // 反向不變量:空 POST 確實生效(optional gzip 被打成未勾),證明非 no-op
      const gzipUnchecked = await page.evaluate(() => {
        const scope = document.getElementById('httpParamPanelDiv');
        const gzip = scope.querySelector('input[name="httpParamItem"][data-group="gzip"]');
        return gzip ? gzip.checked : null;
      });
      expect(gzipUnchecked).toBe(false);
    } finally {
      await restoreAllEnabled(page);
    }
  });

  test('mutex group(geoip)勾 >1 → 存檔跳 confirm', async ({ page }) => {
    const geoipCount = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const geo = [...scope.querySelectorAll('input[name="httpParamItem"][data-group="geoip"]')];
      geo.forEach((c) => { if (!c.checked) { c.checked = true; c.dispatchEvent(new Event('change')); } });
      return geo.length;
    });
    expect(geoipCount).toBeGreaterThan(1);

    await page.locator('button[onclick="saveHttpParamPanel()"]').click();
    const confirmBox = page.locator('.layui-layer-dialog');
    await expect(confirmBox).toBeVisible();
    const txt = await confirmBox.textContent();
    expect(txt).toMatch(/擇一|择一|三選一|三选一|一起|memory|耗記憶|耗内存/i);
  });

  test('optional group(gzip)可正常 toggle', async ({ page }) => {
    const gzip = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const c = scope.querySelector('input[name="httpParamItem"][data-group="gzip"]');
      return c ? { disabled: c.disabled, group: c.getAttribute('data-group') } : null;
    });
    expect(gzip).not.toBeNull();
    expect(gzip.disabled).toBe(false);   // optional 不 disabled
  });

  test('checkbox 點擊取消後能再勾回（lay-ignore fix:防 layui label 雙重觸發回歸）', async ({ page }) => {
    // bug:checkbox 無 lay-skin 又被 layui 渲染 + 包在 label 裡,點擊時 label 原生 toggle
    // 與 layui 假 checkbox 雙重觸發互相抵消 → 取消後勾不回。修法:lay-ignore 讓 layui 別碰它。
    // 關鍵:必須用「真實 label.click()」走使用者點擊路徑;.checked= 直接改 DOM 會繞過事件、測不到此 bug。
    const label = page.locator('#httpParamPanelDiv label').filter({
      has: page.locator('input[name="httpParamItem"][data-group="gzip"]'),
    }).first();
    const cb = label.locator('input[name="httpParamItem"]');

    await expect(cb).toBeChecked();       // gzip 預設 enable
    await label.click();
    await expect(cb).not.toBeChecked();   // 點一下取消
    await label.click();
    await expect(cb).toBeChecked();       // 再點勾回 ← 修復前這裡會失敗
    await label.click();
    await expect(cb).not.toBeChecked();   // 再來回一次確保穩定
    await label.click();
    await expect(cb).toBeChecked();
  });
});
