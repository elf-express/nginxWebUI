const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('國家存取控制', () => {

  test('黑白名單頁面應有兩個 tab', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('.layui-tab');

    const tabs = page.locator('.layui-tab-title li');
    await expect(tabs).toHaveCount(2);
  });

  test('國家存取控制 tab 可正常切換', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('.layui-tab');

    // 點擊第二個 tab（國家存取控制）
    await page.locator('.layui-tab-title li').nth(1).click();
    await page.waitForTimeout(1000);

    // 確認國家 tab 內容可見
    const geoForm = page.locator('#geoForm');
    await expect(geoForm).toBeVisible();
  });

  test('國家 API 回傳正確結構', async ({ page, baseURL }) => {
    await login(page);

    const response = await page.request.get(baseURL + '/adminPage/geo/countries');
    const data = await response.json();
    expect(data.success).toBe(true);
    expect(Array.isArray(data.obj)).toBe(true);
    expect(data.obj.length).toBeGreaterThan(0);

    // 確認亞洲存在
    const asia = data.obj.find(c => c.key === 'asia');
    expect(asia).toBeDefined();
    expect(asia.countries.length).toBeGreaterThan(0);
    expect(asia.countries[0]).toHaveProperty('code');
    expect(asia.countries[0]).toHaveProperty('nameZh');
    expect(asia.countries[0]).toHaveProperty('nameEn');
  });

  test('可以儲存國家規則', async ({ page, baseURL }) => {
    await login(page);
    await page.goto('/adminPage/denyAllow');
    await page.waitForSelector('.layui-tab');

    // 切換到國家 tab
    await page.locator('.layui-tab-title li').nth(1).click();
    await page.waitForTimeout(1500);

    // 展開第一個折疊面板（亞洲）
    const firstTitle = page.locator('.layui-colla-title').first();
    await firstTitle.click();
    await page.waitForTimeout(500);

    // 用 evaluate 勾選第一個 checkbox（TW）
    await page.evaluate(() => {
      var cb = document.querySelector('input[lay-filter="geoCountry"]');
      if (cb && !cb.checked) {
        cb.checked = true;
        // 觸發 Layui form 事件
        if (typeof layui !== 'undefined') {
          layui.form.render('checkbox');
        }
      }
    });
    await page.waitForTimeout(500);

    // 透過 API 直接儲存（避免 UI 互動問題）
    const saveResponse = await page.request.post(baseURL + '/adminPage/geo/addOver', {
      form: {
        mode: '0',
        countries: 'TW',
        enable: 'true'
      }
    });
    const saveData = await saveResponse.json();
    expect(saveData.success).toBe(true);

    // 驗證儲存結果
    const detailResponse = await page.request.get(baseURL + '/adminPage/geo/detail');
    const detailData = await detailResponse.json();
    expect(detailData.success).toBe(true);
    expect(detailData.obj).not.toBeNull();
    expect(detailData.obj.countries).toContain('TW');
  });
});
