const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('防火牆管理 — 6 tab + IP 資料庫交叉驗證', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab-title');
  });

  test('① 6 個頂層 tab 存在', async ({ page }) => {
    const tabs = page.locator('.layui-tab-title > li');
    await expect(tabs).toHaveCount(6);
    const texts = (await tabs.allTextContents()).join('|');
    expect(texts).toMatch(/IP\s?資料庫|IP\s?数据库|IP Database/);
    expect(texts).toMatch(/黑名單|黑名单|Blacklist/);
    expect(texts).toMatch(/白名單|白名单|Whitelist/);
  });

  test('② IP資料庫表格有 Cloudflare 列 + 8 欄表頭', async ({ page }) => {
    const headers = await page.locator('#geoipTableBody').locator('xpath=../thead//th').allTextContents();
    expect(headers.length).toBe(8);
    const bodyText = await page.locator('#geoipTableBody').textContent();
    expect(bodyText).toMatch(/Cloudflare/);
  });

  test('③ 黑名單 / 白名單 tab 可切換且各自有新增按鈕', async ({ page }) => {
    await page.locator('.layui-tab-title > li').nth(1).click();
    await expect(page.locator("button[onclick=\"denyAllowNS.add('deny')\"]")).toBeVisible();
    await page.locator('.layui-tab-title > li').nth(2).click();
    await expect(page.locator("button[onclick=\"denyAllowNS.add('allow')\"]")).toBeVisible();
  });

  test('④ 狀態欄呈現徽章;待確認時伴隨原因文字', async ({ page }) => {
    const badges = page.locator('#geoipTableBody .layui-badge');
    await expect(badges.first()).toBeVisible();
    const warnBadge = page.locator('#geoipTableBody .layui-bg-orange');
    if (await warnBadge.count() > 0) {
      const rowText = await warnBadge.first().locator('xpath=ancestor::td').textContent();
      expect(rowText).toMatch(/·/);
    }
  });
});

test.describe('引用端下拉依 type 過濾 — server 編輯頁', () => {
  // 策略：透過 API 建一筆 deny 規則與一筆 allow 規則，
  // 再 GET /adminPage/server，驗 HTML 中 #denyDiv 只含黑名單名稱、
  // #allowDiv 只含白名單名稱，不互相混入。
  test('⑤ server 頁 denyDiv 只含黑名單、allowDiv 只含白名單', async ({ page }) => {
    // 先登入（page context 持有 session cookie）
    await login(page);

    const denyName = 'test-deny-rule-task16';
    const allowName = 'test-allow-rule-task16';

    // 用 page.request 呼叫 API（繼承 page 的 cookie，已通過認證）
    // 新增黑名單
    const denyResp = await page.request.post('/adminPage/denyAllow/addOver', {
      form: {
        name: denyName,
        type: 'deny',
        content: '192.0.2.1',
      },
    });
    expect(denyResp.ok()).toBeTruthy();
    const denyBody = await denyResp.json();
    expect(denyBody.success).toBeTruthy();

    // 新增白名單
    const allowResp = await page.request.post('/adminPage/denyAllow/addOver', {
      form: {
        name: allowName,
        type: 'allow',
        content: '192.0.2.2',
      },
    });
    expect(allowResp.ok()).toBeTruthy();
    const allowBody = await allowResp.json();
    expect(allowBody.success).toBeTruthy();

    // 前往 server 首頁（controller 會把 denyList/allowList 傳入 view）
    // denyDiv/allowDiv 在頁面 DOM 中但預設 hidden，只需確保 DOM 已附加而非 visible
    await page.goto('/adminPage/server');
    await page.waitForSelector('#denyDiv', { state: 'attached' });

    const denyHtml = await page.locator('#denyDiv').innerHTML();
    const allowHtml = await page.locator('#allowDiv').innerHTML();

    // denyDiv 含黑名單名稱、不含白名單名稱
    expect(denyHtml).toContain(denyName);
    expect(denyHtml).not.toContain(allowName);

    // allowDiv 含白名單名稱、不含黑名單名稱
    expect(allowHtml).toContain(allowName);
    expect(allowHtml).not.toContain(denyName);
  });
});
