const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 讀取 http 參數面板內的統計:總數 / 勾選數 / 顯示的 count 數字 / 全文。
// openHttpParamPanel() 以 content:$('#httpParamPanelDiv') 傳 DOM(不複製),
// 因此 #httpParamPanelDiv 全站唯一,可直接 getElementById 取值。
// 注意:layui form.render() 會把原生 checkbox input 隱藏(以自訂 div 顯示),
// 所以一律用 querySelectorAll 查 DOM(attached),不依賴 input 的可見性。
async function readPanelStats(page) {
  return page.evaluate(() => {
    const scope = document.getElementById('httpParamPanelDiv');
    if (!scope) return null;
    const total = scope.querySelectorAll('input[name="httpParamItem"]').length;
    const checked = scope.querySelectorAll('input[name="httpParamItem"]:checked').length;
    const countEl = scope.querySelector('#httpParamCountNum');
    const shown = countEl ? parseInt(countEl.textContent, 10) : NaN;
    return { total, checked, shown, text: scope.textContent };
  });
}

test.describe('http 參數配置頁 — 全域參數啟用面板', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/http');
    await page.waitForSelector('table');
    // 走真實 UI 路徑:http 參數配置頁 toolbar 的「設置http參數」按鈕
    // (面板已自 server 編輯視窗移入本頁:全域設定歸全域頁)
    await page.getByRole('button', { name: /设置http参数|設置http參數|HTTP params/ }).click();
    // input 被 layui 隱藏,等 attached 而非 visible;再等 success callback 把 count 算出來(>0)
    await page.waitForSelector('input[name="httpParamItem"]', { state: 'attached' });
    await page.waitForFunction(() => {
      const el = document.querySelector('#httpParamCountNum');
      return el && parseInt(el.textContent, 10) > 0;
    });
  });

  test('列出全域 http 指令、預設全勾、count 與勾選數一致', async ({ page }) => {
    const s = await readPanelStats(page);
    expect(s).not.toBeNull();
    expect(s.total).toBeGreaterThan(0);   // 全新 DB 已 seed 預設 http 參數
    expect(s.checked).toBe(s.total);      // 預設全勾
    expect(s.shown).toBe(s.total);        // 「將套用 N 個指令」的 N = 勾選數(修正重複 id 後才正確)
  });

  test('取消勾選一項 → count 同步減 1', async ({ page }) => {
    const before = await readPanelStats(page);
    await page.evaluate(() => {
      const scope = document.getElementById('httpParamPanelDiv');
      const cb = scope.querySelector('input[name="httpParamItem"]:checked');
      cb.checked = false;
      cb.dispatchEvent(new Event('change'));   // 觸發 onchange="updateHttpParamCount()"
    });
    const after = await readPanelStats(page);
    expect(after.checked).toBe(before.checked - 1);
    expect(after.shown).toBe(before.shown - 1);
  });

  test('i18n:標題與面板文案由 messages*.properties 提供(無 raw key / undefined)', async ({ page }) => {
    // layer 標題來自 serverStr.httpParm
    const titles = await page.evaluate(() =>
      [...document.querySelectorAll('.layui-layer-title')].map((e) => e.textContent)
    );
    expect(titles.some((t) => /设置http参数|設置http參數|HTTP params/.test(t))).toBe(true);

    const s = await readPanelStats(page);
    // count 單位 + 套用前綴由 i18n render(而非硬編)
    expect(s.text).toMatch(/个指令|個指令|directives/);
    expect(s.text).toMatch(/将套用|將套用|Apply/);
    // 不應殘留未解析的 raw key 或 undefined
    expect(s.text).not.toContain('serverStr.');
    expect(s.text).not.toContain('undefined');
  });
});
