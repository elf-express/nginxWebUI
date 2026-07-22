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

test.describe('黑白名單全站自動生效 — 綁定 UI 已移除', () => {
  // 策略：透過 API 建一筆 deny 規則與一筆 allow 規則，
  // 驗 (a) server 頁不再有逐 server 綁定殘留(denyDiv/allowDiv/黑白名單按鈕),
  // (b) http 層級 conf preview 自動掛上中央黑白名單 include(全站生效),
  // (c) 防護頁兩個 tab 顯示規則 + 全站生效徽章。
  test('⑤ 中央規則全站自動生效,server 頁無綁定殘留', async ({ page }) => {
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
        ip: '192.0.2.1',
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
        ip: '192.0.2.2',
      },
    });
    expect(allowResp.ok()).toBeTruthy();
    const allowBody = await allowResp.json();
    expect(allowBody.success).toBeTruthy();

    // (a) server 頁已無逐 server 綁定 UI 殘留
    await page.goto('/adminPage/server');
    await page.waitForSelector('table');
    expect(await page.locator('#denyDiv').count()).toBe(0);
    expect(await page.locator('#allowDiv').count()).toBe(0);
    expect(await page.locator("button[onclick='setDenyAllow()']").count()).toBe(0);

    // (b) http 層級 conf 自動掛上中央黑白名單 include(不需任何綁定操作)
    const previewResp = await page.request.post('/adminPage/main/preview', {
      form: { type: 'http' },
    });
    expect(previewResp.ok()).toBeTruthy();
    const previewBody = await previewResp.json();
    expect(previewBody.success).toBeTruthy();
    expect(String(previewBody.obj)).toMatch(/include .*deny_http\.conf/);

    // (c) 防護頁黑/白 tab 各自顯示規則與全站生效徽章
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab-title');
    const content = await page.content();
    expect(content).toContain(denyName);
    expect(content).toContain(allowName);
    expect(content).toMatch(/全站自動生效|全站自动生效|Site-wide/);
  });
});
