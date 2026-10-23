const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 「CrowdSec 防護」頁（E-385，Vue）。共用測試 server（18080）沒有 CROWDSEC_* 環境變數。
const CDN = /esm\.sh|cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com/;

async function api(page, path, form) {
  const res = form
    ? await page.request.post(path, { form })
    : await page.request.get(path);
  const json = await res.json();
  expect(json.success, JSON.stringify(json)).toBe(true);
  return json.obj;
}

async function addList(page, type, name, ip) {
  await api(page, '/adminPage/denyAllow/addOver', { type, name, ip });
}

test.describe('CrowdSec 防護頁', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('選單進入，三個分頁可切換，有 h1，沒有 CDN', async ({ page }) => {
    const cdn = [];
    page.on('request', (r) => { if (CDN.test(r.url())) cdn.push(r.url()); });
    // 從「防護與憑證」頁進入：monitor 頁在沒裝 nginx 的環境會自動彈出安裝方式視窗，遮罩會擋住選單
    await page.goto('/adminPage/protectionCert');
    await page.locator('a[href$="/adminPage/crowdsecGuard"]').click();
    await expect(page.locator('h1')).toHaveText(/CrowdSec/);
    // 預設就在 IP 查詢，所以先切到其他兩個，最後切回來
    const tabs = [
      [/封鎖名單|封锁名单|Ban list/, 'bans'],
      [/連線設定|连接设置|Connection/, 'conn'],
      [/IP 查詢|IP 查询|IP lookup/, 'ip'],
    ];
    for (const [name, key] of tabs) {
      await page.getByRole('tab', { name }).click();
      await expect(page.getByRole('tab', { name })).toHaveAttribute('aria-selected', 'true');
      // 分頁切換會同步網址 hash，重新整理或分享連結都停在同一個分頁
      await expect.poll(() => page.evaluate(() => window.location.hash)).toMatch(new RegExp(`^#${key}`));
    }
    expect(cdn).toEqual([]);
  });

  test('動作控制項都是 button', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.5');
    await expect(page.locator('.ip-layer')).toHaveCount(4);
    // 側邊欄 layui nav-tree 的群組標題是既有的 javascript:; 連結，與 27-a11y-buttons 一樣排除
    const bad = await page.evaluate(() =>
      Array.from(document.querySelectorAll('a[href^="javascript:"]')).filter((a) => !a.closest('.layui-nav-tree')).length);
    expect(bad).toBe(0);
  });

  test('白名單 IP：白名單層命中，其他層略過', async ({ page }) => {
    await addList(page, 'allow', 'e385-allow', '203.0.113.10');
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.10');
    await expect(page.locator('.ip-layer[data-layer="allowlist"]')).toHaveAttribute('data-state', 'match');
    await expect(page.locator('.ip-layer[data-layer="crowdsec"]')).toHaveAttribute('data-state', 'skipped');
    await expect(page.locator('.ip-verdict')).toHaveAttribute('data-verdict', 'success');
  });

  test('黑名單 IP（網段）：黑名單層命中', async ({ page }) => {
    await addList(page, 'deny', 'e385-deny', '198.51.100.0/24');
    await page.goto('/adminPage/crowdsecGuard#ip=198.51.100.9');
    await expect(page.locator('.ip-layer[data-layer="denylist"]')).toHaveAttribute('data-state', 'match');
    await expect(page.locator('.ip-verdict')).toContainText(/黑名單|黑名单|denylist/);
    await expect(page.locator('.ip-verdict')).toHaveAttribute('data-verdict', 'error');
  });

  test('有層查不到結果且沒有任何層擋下：總結是警告，不是綠色「沒有被擋」', async ({ page }) => {
    // 測試環境沒有 GeoIP 資料庫，國家層查不到才是「無法完整判斷」的來源；
    // 沒設定 CrowdSec 不算（nginx 不載入 bouncer，結果是確定的），那個情況由 IpLookupServiceTest 驗證
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.21');
    await expect(page.locator('.ip-layer[data-layer="geo"]')).toHaveAttribute('data-state', 'unknown');
    await expect(page.locator('.ip-verdict')).toHaveAttribute('data-verdict', 'warning');
    await expect(page.locator('.ip-verdict')).toContainText(/無法完整判斷|无法完整判断|cannot be fully determined/);
  });

  test('國家封鎖：測試環境沒有 GeoIP 資料庫時顯示查不到國家', async ({ page }) => {
    // 預設種子是只允許 17 國的全域規則；E2E server 沒有 mmdb，國家判定的正確性由 IpLookupServiceTest 驗證，
    // 實機（有 mmdb）的被封鎖國家 IP 在步驟 5.4 的 Docker 驗證查一次
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.20');
    await expect(page.locator('.ip-layer[data-layer="geo"]')).toHaveAttribute('data-state', 'unknown');
  });

  test('未設定 CrowdSec 時 CrowdSec 層顯示無法查詢', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.30');
    await expect(page.locator('.ip-layer[data-layer="crowdsec"]')).toHaveAttribute('data-state', 'unavailable');
    // 原因文字要查得到翻譯（訊息 key 不能多一個點，否則整批 JS 文案都會變 undefined）
    await expect(page.locator('.ip-layer[data-layer="crowdsec"] .ip-reason')).toContainText(/bouncer/i);
  });

  test('加入白名單後「黑名單／白名單」分頁看得到該 IP', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.40');
    await page.locator('.ip-whitelist').click();
    await page.locator('.ant-popconfirm .ant-btn-primary').click();
    await expect(page.locator('.ant-message')).toContainText(/白名單|白名单|allowlist/i);
    await page.goto('/adminPage/protectionCert');
    await expect(page.locator('body')).toContainText('203.0.113.40');
  });

  test('加入白名單時黑名單有同一個 IP：提示衝突的黑名單名稱，不改黑名單', async ({ page }) => {
    await addList(page, 'deny', 'e385-conflict', '203.0.113.50');
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.50');
    await page.locator('.ip-whitelist').click();
    await page.locator('.ant-popconfirm .ant-btn-primary').click();
    await expect(page.locator('.ant-message')).toContainText('e385-conflict');
    // 黑名單照舊在「防護與憑證」頁，沒有被改動
    await page.goto('/adminPage/protectionCert');
    await expect(page.locator('body')).toContainText('e385-conflict');
  });

  test('手動封鎖視窗的三個欄位都有可存取名稱', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#bans');
    await page.locator('.bans-add').click();
    for (const name of [/IP.*(網段|网段|range)/i, /封鎖時長|封锁时长|Duration/i, /原因|Reason/i]) {
      await expect(page.getByRole('dialog').getByRole('textbox', { name })).toBeVisible();
    }
  });

  test('最近被擋下的 IP：讀不到日誌時顯示空表，不報錯', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard');
    await expect(page.locator('.blocked .ant-table')).toBeVisible();
    // 站台下拉預設選「全部站台」，要看得到文字
    await expect(page.locator('.blocked-site')).toContainText(/全部站台|全部站点|All sites/);
  });

  test('封鎖名單：未設定 CrowdSec 時顯示錯誤與連線設定連結', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#bans');
    await expect(page.locator('.bans-error')).toBeVisible();
    await page.locator('.bans-error a[href="#conn"]').click();
    await expect(page.getByRole('tab', { name: /連線設定|连接设置|Connection/ })).toHaveAttribute('aria-selected', 'true');
  });

  test('「防護與憑證」不再有 CrowdSec 分頁；ASN 分頁沒有開發者用語', async ({ page }) => {
    await page.goto('/adminPage/protectionCert');
    await expect(page.locator('.layui-tab-title li', { hasText: /^\s*CrowdSec\s*$/ })).toHaveCount(0);
    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
    const text = await page.locator('#asnTabContent').innerText();
    expect(text).not.toMatch(/Phase 1|post-merge/i);
  });

  test('ASN 分頁的舊版規則區：沒有舊資料時隱藏，有資料時才顯示', async ({ page }) => {
    const legacy = await api(page, '/adminPage/asn/list');
    if (legacy.length) {
      await api(page, '/adminPage/asn/del', { id: legacy.map((r) => r.id).join(',') });
    }
    try {
      await page.goto('/adminPage/protectionCert');
      await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
      await expect(page.locator('#asnTableBody tr')).toHaveCount(1);
      await expect(page.locator('#asnLegacySection')).toBeHidden();

      await api(page, '/adminPage/asn/addOver', { asn: '64501', orgName: 'e385-legacy' });
      await page.reload();
      await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
      await expect(page.locator('#asnLegacySection')).toBeVisible();
    } finally {
      const rest = await api(page, '/adminPage/asn/list');
      if (rest.length) {
        await api(page, '/adminPage/asn/del', { id: rest.map((r) => r.id).join(',') });
      }
    }
  });
});
