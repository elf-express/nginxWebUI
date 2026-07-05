const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// HTTP 參數 panel — module availability filter（雙軌偵測 geoip2/brotli，Linux 才警示）
// E2E 跑 Windows(非 Linux)→ ServerController isLinux=false → fallback:視為全可用、不警示。
// 因此本檔只能測「非 Linux fallback」:geoip/brotli 不出現缺失紅 badge、checkbox 不被 module filter 停用。
// Linux 「偵測不到 → 紅 badge」正向路徑 + 雙軌(.so / nginx -V)邏輯無法在 Windows E2E 覆蓋,
// 靠 code review + 邏輯正確性保障(見設計 spec 可測性限制)。

async function openPanel(page) {
  await page.getByRole('button', { name: /添加反向代理/ }).click();
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}

test.describe('server modal — ① http 參數 panel module availability filter（非 Linux fallback）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    await openPanel(page);
  });

  test('非 Linux:panel 內不出現任何 module 缺失紅 badge', async ({ page }) => {
    const badgeCount = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      return scope.querySelectorAll('.layui-badge.layui-bg-red').length;
    });
    expect(badgeCount).toBe(0); // isLinux=false → <#if isLinux && ...> 不 render
  });

  test('非 Linux:geoip/brotli checkbox 不被 module filter 停用', async ({ page }) => {
    const info = await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const pick = (g) => [...scope.querySelectorAll(`input[name="httpParamItem"][data-group="${g}"]`)];
      const geoip = pick('geoip');
      const brotli = pick('brotli');
      return {
        geoipCount: geoip.length,
        brotliCount: brotli.length,
        geoipNoneDisabled: geoip.every((c) => !c.disabled),
        brotliNoneDisabled: brotli.every((c) => !c.disabled),
      };
    });
    // module filter 不 disable checkbox（geoip/brotli 非 locked group）
    expect(info.geoipCount).toBeGreaterThan(0);        // 測試 DB 有 geoip 指令
    expect(info.geoipNoneDisabled).toBe(true);
    if (info.brotliCount > 0) expect(info.brotliNoneDisabled).toBe(true);
  });
});
