const { test, expect } = require('@playwright/test');
const { spawnSync } = require('child_process');
const { login } = require('./helpers');

// HTTP 參數 panel — module availability filter（雙軌偵測 geoip2/brotli，Linux 才警示）
// 非 Linux:HttpController isLinux=false → fallback,視為全可用、不顯示缺失 badge。
// Linux 且沒有 nginx(CI 的 ubuntu runner):.so 與 nginx -V 都偵測不到 → geoip/brotli 顯示缺失紅 badge。
// Linux 且有 nginx(容器內):結果取決於實際模組,不在此斷言。
// 不論平台,module filter 都只警示、不停用 checkbox。

const IS_LINUX = process.platform === 'linux';
const HAS_NGINX = IS_LINUX && !spawnSync('nginx', ['-V']).error;

async function openPanel(page) {
  await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
  await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
  await page.waitForTimeout(300);
}

function missingBadgeGroups(page) {
  return page.evaluate(() => {
    const scope = document.getElementById('httpParamPanelDiv');
    const groups = new Set();
    scope.querySelectorAll('.layui-badge.layui-bg-red').forEach((badge) => {
      const input = badge.closest('label').querySelector('input[name="httpParamItem"]');
      groups.add(input ? input.dataset.group : '');
    });
    return { total: scope.querySelectorAll('.layui-badge.layui-bg-red').length, groups: [...groups].sort() };
  });
}

test.describe('http 參數配置頁 — 參數面板 module availability filter', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('table');
    await openPanel(page);
  });

  test('非 Linux:panel 內不出現任何 module 缺失紅 badge', async ({ page }) => {
    test.skip(IS_LINUX, '只在非 Linux 驗 fallback');
    const badges = await missingBadgeGroups(page);
    expect(badges.total).toBe(0); // isLinux=false → <#if isLinux && ...> 不 render
  });

  test('Linux 無 nginx:geoip/brotli 顯示缺失紅 badge', async ({ page }) => {
    test.skip(!IS_LINUX || HAS_NGINX, '只在沒有 nginx 的 Linux 驗缺失路徑');
    const badges = await missingBadgeGroups(page);
    expect(badges.total).toBeGreaterThan(0);
    expect(badges.groups).toEqual(['brotli', 'geoip']);
  });

  test('geoip/brotli checkbox 不被 module filter 停用', async ({ page }) => {
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
