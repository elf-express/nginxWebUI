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

// 國家層逐站台計算：沒有啟用的 HTTP 站台時 nginx 不會執行國家封鎖，所以國家層的測試要先有一個站台，結束後刪掉
async function withHttpServer(page, serverName, fn) {
  const serverJson = JSON.stringify({ proxyType: 0, serverName, listen: '18990', enable: true, def: 0 });
  await api(page, '/adminPage/server/addOver', { serverJson, serverParamJson: '[]', locationJson: '[]' });
  const servers = await api(page, '/adminPage/crowdsecGuard/servers');
  const created = servers.find((s) => s.name === serverName);
  expect(created, JSON.stringify(servers)).toBeTruthy();
  try {
    await fn();
  } finally {
    await api(page, '/adminPage/server/del', { id: created.id });
  }
}

test.describe('CrowdSec 防護頁', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('選單進入，五個分頁可切換，有 h1，沒有 CDN', async ({ page }) => {
    const cdn = [];
    page.on('request', (r) => { if (CDN.test(r.url())) cdn.push(r.url()); });
    // 從「防護與憑證」頁進入：monitor 頁在沒裝 nginx 的環境會自動彈出安裝方式視窗，遮罩會擋住選單
    await page.goto('/adminPage/protectionCert');
    await page.locator('a[href$="/adminPage/crowdsecGuard"]').click();
    await expect(page.locator('h1')).toHaveText(/CrowdSec/);
    // 預設就在 IP 查詢，所以先切到其他四個，最後切回來
    const tabs = [
      [/封鎖名單|封锁名单|Ban list/, 'bans'],
      [/WAF 事件|WAF events/, 'waf'],
      [/WAF 規則|WAF 规则|WAF rules/, 'rules'],
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
    await withHttpServer(page, 'e385-geo-warn.test', async () => {
      await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.21');
      await expect(page.locator('.ip-layer[data-layer="geo"]')).toHaveAttribute('data-state', 'unknown');
      await expect(page.locator('.ip-verdict')).toHaveAttribute('data-verdict', 'warning');
      await expect(page.locator('.ip-verdict')).toContainText(/無法完整判斷|无法完整判断|cannot be fully determined/);
    });
  });

  test('國家封鎖：測試環境沒有 GeoIP 資料庫時顯示查不到國家，明細逐站台列出套用的規則', async ({ page }) => {
    // 預設種子是只允許 17 國的全域規則；E2E server 沒有 mmdb，國家判定的正確性由 IpLookupServiceTest 驗證，
    // 實機（有 mmdb）的被封鎖國家 IP 在步驟 5.4 的 Docker 驗證查一次
    await withHttpServer(page, 'e385-geo.test', async () => {
      await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.20');
      await expect(page.locator('.ip-layer[data-layer="geo"]')).toHaveAttribute('data-state', 'unknown');
      const site = page.locator('.ip-geo-site', { hasText: 'e385-geo.test' });
      await expect(site).toHaveAttribute('data-source', 'global');
      await expect(site).toContainText(/全域規則|全局规则|Global rule/);
      await expect(site).toContainText(/查不到國家|查不到国家|Country unknown/);
    });
  });

  test('未設定 CrowdSec 時 CrowdSec 層顯示無法查詢', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.30');
    await expect(page.locator('.ip-layer[data-layer="crowdsec"]')).toHaveAttribute('data-state', 'unavailable');
    // 原因文字要查得到翻譯（訊息 key 不能多一個點，否則整批 JS 文案都會變 undefined）
    await expect(page.locator('.ip-layer[data-layer="crowdsec"] .ip-reason')).toContainText(/bouncer/i);
  });

  test('CrowdSec 有封鎖紀錄但 nginx 沒載入 bouncer：顯示不會擋，標籤是警告色', async ({ page }) => {
    // 測試 server 沒有 CrowdSec，這個狀態的判定由 IpLookupServiceTest 驗證，這裡只驗畫面
    const ip = '203.0.113.76';
    const layer = (name, state, extra = {}) => ({ layer: name, state, lists: [], details: [], decisions: [], ...extra });
    await page.route('**/adminPage/crowdsecGuard/ipLookup**', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, status: '200', obj: {
        result: { ip, layers: [
          layer('allowlist', 'none'), layer('geo', 'allowed'), layer('denylist', 'none'),
          layer('crowdsec', 'notEnforced', { decisions: [{ id: 76, type: 'ban', scope: 'Ip', value: ip, origin: 'cscli', scenario: 'manual', duration: '4h' }] }),
        ], blockedBy: 'none' },
        serverNames: {},
      } }),
    }));
    await page.goto(`/adminPage/crowdsecGuard#ip=${ip}`);
    const cs = page.locator('.ip-layer[data-layer="crowdsec"]');
    await expect(cs).toHaveAttribute('data-state', 'notEnforced');
    await expect(cs.locator('.ant-tag')).toContainText(/nginx 未啟用 CrowdSec|nginx 未启用 CrowdSec|not enforced/);
    await expect(cs.locator('.ant-tag')).toHaveClass(/ant-tag-warning/);
    await expect(cs.locator('.ip-detail')).toContainText(ip);
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

  test('最近被擋下的 IP：標題、工具列、下一個元素依序排列，互不重疊', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard');
    await expect(page.locator('.blocked .ant-table')).toBeVisible();
    const box = await page.evaluate(() => {
      const title = document.querySelector('#blocked-title').getBoundingClientRect();
      const bar = document.querySelector('.blocked-filters');
      const next = bar.nextElementSibling.getBoundingClientRect();
      const b = bar.getBoundingClientRect();
      return { titleBottom: title.bottom, barTop: b.top, barBottom: b.bottom, nextTop: next.top };
    });
    expect(box.titleBottom).toBeLessThanOrEqual(box.barTop);
    expect(box.barBottom).toBeLessThanOrEqual(box.nextTop);
  });

  test('空表格依介面語系顯示 ant 元件的文字，不是英文 No data', async ({ page }) => {
    const setLang = async (lang) => {
      const res = await page.request.post('/adminPage/login/changeLang', { form: { lang } });
      expect((await res.json()).success).toBe(true);
    };
    try {
      await page.goto('/adminPage/crowdsecGuard');
      await expect(page.locator('.blocked .ant-empty-description')).toHaveText('暂无数据');
      await setLang('zh_TW');
      await page.reload();
      await expect(page.locator('.blocked .ant-empty-description')).toHaveText('無此資料');
      await setLang('en_US');
      await page.reload();
      await expect(page.locator('.blocked .ant-empty-description')).toHaveText('No data');
    } finally {
      await setLang('zh');
    }
  });

  test('連續查不同 IP：較早送出、較晚回來的結果不會蓋掉最後一次查詢', async ({ page }) => {
    let release;
    const gate = new Promise((r) => { release = r; });
    await page.route('**/adminPage/crowdsecGuard/ipLookup**', async (route) => {
      if (route.request().url().includes('203.0.113.61')) {
        const res = await route.fetch();
        await gate;
        await route.fulfill({ response: res });
      } else {
        await route.continue();
      }
    });
    // 查詢中的搜尋框不接受 Enter，第二次查詢改由網址 hash 觸發（與其他分頁的「查這個 IP」同一條路徑）
    const first = page.waitForRequest((r) => r.url().includes('ipLookup') && r.url().includes('203.0.113.61'));
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.61');
    await first;
    await page.evaluate(() => { window.location.hash = '#ip=203.0.113.62'; });
    await expect(page.locator('.ip-verdict')).toContainText('203.0.113.62');
    // 最後一次查詢結束就結束 loading，不必等舊請求
    await expect(page.locator('.ip-input .ant-btn-loading')).toHaveCount(0);
    const late = page.waitForResponse((r) => r.url().includes('203.0.113.61'));
    release();
    await late;
    await page.waitForTimeout(300);
    await expect(page.locator('.ip-verdict')).toContainText('203.0.113.62');
  });

  test('封鎖名單對已查過的同一個 IP 按「查這個 IP」會重新查詢', async ({ page }) => {
    const ip = '203.0.113.70';
    await page.route('**/adminPage/crowdsecGuard/bans**', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, status: '200', obj: {
        records: [{ id: 70, type: 'ban', scope: 'Ip', value: ip, origin: 'cscli', scenario: 'manual', duration: '4h' }],
        count: 1, createdAvailable: false,
      } }),
    }));
    let lookups = 0;
    page.on('request', (r) => { if (r.url().includes('/ipLookup') && r.url().includes(ip)) lookups += 1; });
    await page.goto(`/adminPage/crowdsecGuard#ip=${ip}`);
    await expect(page.locator('.ip-verdict')).toContainText(ip);
    await expect.poll(() => lookups).toBe(1);
    await page.getByRole('tab', { name: /封鎖名單|封锁名单|Ban list/ }).click();
    await page.locator('.bans-lookup').first().click();
    await expect(page.getByRole('tab', { name: /IP 查詢|IP 查询|IP lookup/ })).toHaveAttribute('aria-selected', 'true');
    await expect.poll(() => lookups).toBe(2);
    await expect.poll(() => page.evaluate(() => window.location.hash)).toBe(`#ip=${ip}`);
  });

  test('搜尋框查 IP 會更新網址 hash，重新整理後查的是同一個 IP', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.71');
    await expect(page.locator('.ip-verdict')).toContainText('203.0.113.71');
    await page.locator('.ip-input input').fill('203.0.113.72');
    await page.locator('.ip-input input').press('Enter');
    await expect(page.locator('.ip-verdict')).toContainText('203.0.113.72');
    await expect.poll(() => page.evaluate(() => window.location.hash)).toBe('#ip=203.0.113.72');
    const again = page.waitForRequest((r) => r.url().includes('/ipLookup') && r.url().includes('203.0.113.72'));
    await page.reload();
    await again;
    await expect(page.locator('.ip-verdict')).toContainText('203.0.113.72');
  });

  test('最近被擋下的 IP：「查這個 IP」更新網址 hash；分不出站台的行列出候選站台', async ({ page }) => {
    await page.route('**/adminPage/crowdsecGuard/blocked**', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, status: '200', obj: {
        rows: [
          { ip: '198.51.100.73', host: 'same.example.com', count: 2, lastTime: Date.now(), statuses: { 444: 2 },
            ambiguous: true, candidates: ['same.example.com (80)', 'same.example.com (8080)'] },
          { ip: '198.51.100.74', host: 'one.example.com', count: 1, lastTime: Date.now(), statuses: { 403: 1 }, ambiguous: false },
        ],
        hostSupported: true, truncated: false,
      } }),
    }));
    await page.goto('/adminPage/crowdsecGuard');
    const ambiguous = page.locator('.blocked .ant-table-row', { hasText: '198.51.100.73' }).locator('.blocked-ambiguous');
    await expect(ambiguous).toContainText(/可能屬於多個站台|可能属于多个站点|May belong to several sites/);
    await expect(ambiguous).toContainText('same.example.com (8080)');
    await expect(page.locator('.blocked .ant-table-row', { hasText: '198.51.100.74' }).locator('.blocked-ambiguous')).toHaveCount(0);
    await page.locator('.blocked .ant-table-row', { hasText: '198.51.100.74' }).locator('.blocked-lookup').click();
    await expect(page.locator('.ip-verdict')).toContainText('198.51.100.74');
    await expect.poll(() => page.evaluate(() => window.location.hash)).toBe('#ip=198.51.100.74');
  });

  test('加白名單後的背景重查不會在切到其他分頁後改掉網址', async ({ page }) => {
    const ip = '203.0.113.75';
    let release;
    const gate = new Promise((r) => { release = r; });
    await page.route('**/adminPage/crowdsecGuard/whitelist**', async (route) => {
      const res = await route.fetch();
      await gate;
      await route.fulfill({ response: res });
    });
    await page.goto(`/adminPage/crowdsecGuard#ip=${ip}`);
    await expect(page.locator('.ip-verdict')).toContainText(ip);
    await page.locator('.ip-whitelist').click();
    const posted = page.waitForRequest((r) => r.url().includes('/whitelist'));
    await page.locator('.ant-popconfirm .ant-btn-primary').click();
    await posted;
    await page.getByRole('tab', { name: /連線設定|连接设置|Connection/ }).click();
    await expect.poll(() => page.evaluate(() => window.location.hash)).toBe('#conn');
    const relookup = page.waitForRequest((r) => r.url().includes('/ipLookup') && r.url().includes(ip));
    release();
    await relookup;
    await page.waitForTimeout(300);
    expect(await page.evaluate(() => window.location.hash)).toBe('#conn');
  });

  test('最近被擋下的 IP：連續切換時間範圍時只顯示最後一次的結果', async ({ page }) => {
    const body = (ip) => JSON.stringify({ success: true, status: '200', obj: {
      rows: [{ ip, host: 'race.example.com', count: 1, lastTime: Date.now(), statuses: { 444: 1 } }],
      hostSupported: true, truncated: false,
    } });
    let release;
    const gate = new Promise((r) => { release = r; });
    await page.route('**/adminPage/crowdsecGuard/blocked**', async (route) => {
      const minutes = new URL(route.request().url()).searchParams.get('minutes');
      if (minutes === '360') {
        await gate;
        await route.fulfill({ contentType: 'application/json', body: body('198.51.100.66') });
      } else {
        await route.fulfill({ contentType: 'application/json', body: body(minutes === '1440' ? '198.51.100.24' : '198.51.100.1') });
      }
    });
    await page.goto('/adminPage/crowdsecGuard');
    await expect(page.locator('.blocked .ant-table')).toContainText('198.51.100.1');
    for (const label of [/6 (小時|小时|hours)/, /24 (小時|小时|hours)/]) {
      await page.locator('.blocked-span').click();
      await page.locator('.ant-select-item-option', { hasText: label }).click();
    }
    await expect(page.locator('.blocked .ant-table')).toContainText('198.51.100.24');
    const late = page.waitForResponse((r) => r.url().includes('minutes=360'));
    release();
    await late;
    await page.waitForTimeout(300);
    await expect(page.locator('.blocked .ant-table')).toContainText('198.51.100.24');
    await expect(page.locator('.blocked .ant-table')).not.toContainText('198.51.100.66');
  });

  test('封鎖名單：連續切換本機／社群時只顯示最後一次的結果', async ({ page }) => {
    const body = (value) => JSON.stringify({ success: true, status: '200', obj: {
      records: [{ id: value.length, type: 'ban', scope: 'Ip', value, origin: 'crowdsec', scenario: 'race', duration: '1h' }],
      count: 1, createdAvailable: false,
    } });
    let release;
    let localCalls = 0;
    const gate = new Promise((r) => { release = r; });
    await page.route('**/adminPage/crowdsecGuard/bans**', async (route) => {
      const scope = new URL(route.request().url()).searchParams.get('scope');
      if (scope === 'community') {
        await gate;
        await route.fulfill({ contentType: 'application/json', body: body('192.0.2.200') });
      } else {
        localCalls += 1;
        await route.fulfill({ contentType: 'application/json', body: body(localCalls === 1 ? '192.0.2.1' : '192.0.2.22') });
      }
    });
    await page.goto('/adminPage/crowdsecGuard#bans');
    await expect(page.locator('.bans .ant-table')).toContainText('192.0.2.1');
    await page.locator('.bans-scope').getByText(/社群名單|社区名单|Community/).click();
    await page.locator('.bans-scope').getByText(/本機封鎖|本机封锁|Local/).click();
    await expect(page.locator('.bans .ant-table')).toContainText('192.0.2.22');
    const late = page.waitForResponse((r) => r.url().includes('scope=community'));
    release();
    await late;
    await page.waitForTimeout(300);
    await expect(page.locator('.bans .ant-table')).toContainText('192.0.2.22');
    await expect(page.locator('.bans .ant-table')).not.toContainText('192.0.2.200');
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
