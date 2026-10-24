const { test, expect } = require('@playwright/test');
const { spawn, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const http = require('http');
const { PROJECT_ROOT, JAR_PATH, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA, login } = require('./helpers');

// 「CrowdSec 防護」頁的「WAF 事件」分頁（E-394，Vue）：
//   1) 共用測試 server（18080，沒有 CROWDSEC_* 環境變數）→「沒有 machine 憑證」提示、立即同步、保留天數驗證與保存、
//      IP 查詢沒有事件、A11y、離線
//   2) 本檔另起一個 server（18083），CROWDSEC_LAPI_URL 指向本檔的假 LAPI（回固定的 WAF alert，格式照 crowdsec v1.8.1）→
//      同步、vpatch 與 CRS 併成一列、篩選、展開、查這個 IP、IP 查詢的最近 WAF 事件

const CDN = /esm\.sh|cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com/;
const SYNC_NOW = /立即同步|Sync now/;
const ENV_PORT = 18083;
const ENV_BASE = `http://localhost:${ENV_PORT}`;
// 放在已被 .gitignore 的 test-data/ 底下；helpers.cleanTestData() 只刪 test-data/sqlite.db
const ENV_DATA_DIR = path.join(__dirname, 'test-data', 'waf').replace(/\\/g, '/');
// 每次執行隨機產生，避免把看起來像憑證的字串寫進原始碼
const MACHINE_PASSWORD = crypto.randomBytes(16).toString('hex');
const BOUNCER_KEY = crypto.randomBytes(16).toString('hex');
const IP_ENV = '198.51.100.9'; // GET /.env：vpatch 擋下，CRS 930130 同一個請求
const IP_SQLI = '198.51.100.10'; // SQL 注入：CRS 只記錄
const UA = 'Mozilla/5.0 e394-e2e';
const SQLI_URI = '/search?q=1%27%20UNION%20SELECT%20password%20FROM%20users--';

async function api(page, p, form) {
  const res = form ? await page.request.post(p, { form }) : await page.request.get(p);
  const json = await res.json();
  expect(json.success, JSON.stringify(json)).toBe(true);
  return json.obj;
}

// 帶一個每次不同的查詢字串：同一頁只改 hash 不會重新載入，前一個測試留下的篩選會沿用
async function openWafTab(page) {
  await page.goto(`/adminPage/crowdsecGuard?t=${Date.now()}#waf`);
  const app = page.locator('#crowdsecGuardApp');
  await expect(app.locator('h2#waf-title')).toBeVisible();
  await expect(app.locator('.waf-table')).toBeVisible();
  return app;
}

// 下拉選單是虛擬捲動：選項不在可視範圍時不會渲染，且重新打開時會捲到已選的項目；
// 所以每次重試前把清單捲到下一個位置（到底就從頭再來），直到選項出現
async function choose(page, select, name) {
  await select.click();
  const dropdown = page.locator('.ant-select-dropdown:visible');
  const option = dropdown.locator('.ant-select-item-option', { hasText: name }).first();
  const holder = dropdown.locator('.rc-virtual-list-holder');
  let top = 0;
  await expect(async () => {
    if (!(await option.isVisible())) {
      await holder.evaluate((el, y) => {
        el.scrollTop = y % (el.scrollHeight - el.clientHeight + 1);
      }, top);
      top += 100;
    }
    await option.click({ timeout: 1000 });
  }).toPass({ timeout: 20000 });
}

// ---------- 假 LAPI ----------

const list = (...v) => JSON.stringify(v);
function meta(...kv) {
  const a = [];
  for (let i = 0; i < kv.length; i += 2) {
    a.push({ key: kv[i], value: kv[i + 1] });
  }
  return a;
}
const event = (rule, message, uri, fqdn) => ({ meta: meta('rule_name', rule, 'message', message, 'uri', uri, 'target_fqdn', fqdn) });

function alert(id, kind, message, ip, at, m, events) {
  const iso = new Date(Math.floor(at / 1000) * 1000).toISOString().replace('.000Z', 'Z');
  const a = { id, message, created_at: iso, start_at: iso, stop_at: iso, source: { ip, value: ip, scope: 'Ip' }, meta: m, events };
  if (kind) {
    a.kind = kind;
  }
  return a;
}

function fixtures() {
  const at = Date.now() - 2 * 60 * 1000;
  return JSON.stringify([
    alert(1, 'waf', `WAF block: crowdsecurity/vpatch-env-access from ${IP_ENV} (127.0.0.1)`, IP_ENV, at,
      meta('msg', list('Detect access to .env files'), 'method', list('GET'), 'target_uri', list('/.env'),
        'name', list('crowdsecurity/vpatch-env-access'), 'user_agent', list(UA)),
      [event('crowdsecurity/vpatch-env-access', 'Detect access to .env files', '/.env', 'blog.e2e.test:8443')]),
    alert(2, 'waf', `WAF out-of-band match: anomaly score out-of-band: lfi: 5, anomaly: 5,  from ${IP_ENV} (127.0.0.1)`, IP_ENV, at,
      meta('method', list('GET'), 'target_uri', list('/.env'), 'name', list('native_rule:930130'), 'user_agent', list(UA)),
      [event('native_rule:930130', 'Restricted File Access Attempt', '/.env', 'blog.e2e.test:8443'),
        event('native_rule:949110', 'Inbound Anomaly Score Exceeded (Total Score: 5)', '/.env', 'blog.e2e.test:8443')]),
    alert(3, 'waf', `WAF out-of-band match: anomaly score out-of-band: sql_injection: 10, anomaly: 10,  from ${IP_SQLI} (127.0.0.1)`,
      IP_SQLI, at - 30000,
      meta('method', list('GET'), 'target_uri', list(SQLI_URI), 'name', list('native_rule:942100'), 'user_agent', list(UA)),
      [event('native_rule:942100', 'SQL Injection Attack Detected via libinjection', SQLI_URI, 'shop.e2e.test')]),
    alert(4, null, "Ip 198.51.100.20 performed 'crowdsecurity/http-probing'", '198.51.100.20', at, meta('target_uri', list('/a')), []),
  ]);
}

function startFakeLapi(alerts) {
  const tokens = new Set();
  const server = http.createServer((req, res) => {
    req.resume();
    req.on('end', () => {
      const url = new URL(req.url, 'http://127.0.0.1');
      const send = (code, body) => {
        res.writeHead(code, { 'Content-Type': 'application/json' });
        res.end(typeof body === 'string' ? body : JSON.stringify(body));
      };
      if (url.pathname === '/v1/watchers/login') {
        const token = crypto.randomBytes(8).toString('hex');
        tokens.add(token);
        return send(200, { code: 200, expire: '2099-01-01T00:00:00Z', token });
      }
      if (url.pathname === '/v1/alerts') {
        if (!tokens.has(String(req.headers.authorization || '').replace(/^Bearer /, ''))) {
          return send(401, { message: 'Token is expired' });
        }
        // 缺口探測（limit=1）回空陣列；其他回固定的 alert（重複拉取由 alert id 去重）
        return send(200, url.searchParams.get('limit') === '1' ? '[]' : alerts);
      }
      if (url.pathname === '/v1/decisions') {
        return send(200, 'null');
      }
      return send(404, { message: 'not found' });
    });
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

function waitForReady(base, timeoutMs = 90000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const check = () => {
      if (Date.now() - start > timeoutMs) {
        return reject(new Error(`WAF 測試 server 未在 ${timeoutMs}ms 內啟動`));
      }
      const req = http.get(base, () => resolve());
      req.on('error', () => setTimeout(check, 1000));
      req.end();
    };
    setTimeout(check, 3000);
  });
}

// ---------- 共用 server ----------

test.describe('WAF 事件分頁（共用 server，沒有 CrowdSec 變數）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test.afterEach(async ({ page }) => {
    // 保留天數還原成預設值，不影響其他測試
    await page.request.post('/adminPage/crowdsecGuard/waf/retention', { form: { days: '30' } });
  });

  test('沒有 machine 憑證時顯示提示，立即同步也回同一個提示', async ({ page }) => {
    const app = await openWafTab(page);
    await expect(app.locator('.waf-notice-notConfigured')).toBeVisible();
    await app.getByRole('button', { name: SYNC_NOW }).click();
    await expect(page.locator('.ant-message')).toContainText(/machine/);
    const res = await page.request.post('/adminPage/crowdsecGuard/waf/sync');
    expect((await res.json()).obj.outcome).toBe('notConfigured');
  });

  test('保留天數：後端只收 1–365 的整數，存好後重新整理仍保留', async ({ page }) => {
    for (const days of ['0', '366', 'abc', '7.5']) {
      const res = await page.request.post('/adminPage/crowdsecGuard/waf/retention', { form: { days } });
      expect((await res.json()).success, days).toBe(false);
    }
    let app = await openWafTab(page);
    await app.locator('.waf-retention input').fill('14');
    await app.locator('.waf-retention-save').click();
    await expect(page.locator('.ant-message')).toContainText(/14/);
    await page.reload();
    app = await openWafTab(page);
    await expect(app.locator('.waf-retention input')).toHaveValue('14');
    expect((await api(page, '/adminPage/crowdsecGuard/waf/status')).retentionDays).toBe(14);
  });

  test('IP 查詢沒有 WAF 事件時顯示「沒有 WAF 事件」', async ({ page }) => {
    await page.goto('/adminPage/crowdsecGuard#ip=203.0.113.77');
    await expect(page.locator('.ip-waf-none')).toBeVisible();
    expect((await api(page, '/adminPage/crowdsecGuard/ipLookup?ip=203.0.113.77')).wafEvents).toEqual([]);
  });

  test('A11y 與離線：按鈕都是 type=button 且有名稱、沒有 javascript: 連結、沒有 CDN', async ({ page }) => {
    const cdn = [];
    page.on('request', (r) => {
      if (CDN.test(r.url())) {
        cdn.push(r.url());
      }
    });
    const app = await openWafTab(page);
    const buttons = await app.locator('button').all();
    expect(buttons.length).toBeGreaterThan(0);
    for (const b of buttons) {
      expect(await b.getAttribute('type')).toBe('button');
      // 只有圖示的按鈕（搜尋）用圖示的 aria-label 當名稱
      const name = await b.evaluate((el) =>
        (el.getAttribute('aria-label') || el.textContent || el.querySelector('[aria-label]')?.getAttribute('aria-label') || '').trim());
      expect(name, await b.evaluate((el) => el.outerHTML.slice(0, 200))).not.toBe('');
    }
    await expect(app.locator('a[href^="javascript:"]')).toHaveCount(0);
    await expect(app.locator('.waf-retention input')).toHaveAttribute('aria-label', /保留天數|保留天数|Keep/);
    expect(cdn).toEqual([]);
  });
});

// ---------- 假 LAPI 的 server ----------

test.describe('WAF 事件分頁（假 LAPI）', () => {
  let lapi = null;
  let proc = null;
  let context = null;
  let page = null;
  // 這組所有測試（篩選、展開、查這個 IP）共用同一頁，最後一個測試斷言沒有 CDN 請求
  const cdn = [];

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180000);
    lapi = await startFakeLapi(fixtures());
    fs.rmSync(ENV_DATA_DIR, { recursive: true, force: true });
    fs.mkdirSync(ENV_DATA_DIR, { recursive: true });

    // PATH 上的 java 可能是 Java 8（跑不動 Java 17 jar）；優先用 JAVA_HOME（同 helpers.js）
    const javaBin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
    proc = spawn(javaBin, [
      '-jar',
      '-Dfile.encoding=UTF-8',
      JAR_PATH,
      `--server.port=${ENV_PORT}`,
      `--project.home=${ENV_DATA_DIR}/`,
      `--init.admin=${TEST_ADMIN}`,
      `--init.pass=${TEST_PASS}`,
      `--project.testCaptcha=${TEST_CAPTCHA}`,
      '--project.skipSeedFetch=true',
      '--project.skipGeoipFetch=true',
      // 共用的 18080 server 還在跑；不帶這個，啟動時會把它當舊版本殺掉
      '--project.skipKillSelf=true',
    ], {
      cwd: PROJECT_ROOT,
      stdio: 'pipe',
      env: {
        ...process.env,
        CROWDSEC_LAPI_URL: `http://127.0.0.1:${lapi.address().port}`,
        CROWDSEC_MACHINE_PASSWORD: MACHINE_PASSWORD,
        CROWDSEC_BOUNCER_KEY: BOUNCER_KEY,
      },
    });
    proc.stdout.on('data', () => {});
    proc.stderr.on('data', () => {});
    await waitForReady(ENV_BASE);

    context = await browser.newContext({ baseURL: ENV_BASE });
    page = await context.newPage();
    page.on('request', (r) => {
      if (CDN.test(r.url())) {
        cdn.push(r.url());
      }
    });
    await login(page);
    // 兩個 HTTP 站台：SQL 注入那筆的 host 是 shop.e2e.test、/.env 那筆是 blog.e2e.test（帶埠）
    for (const serverName of ['shop.e2e.test', 'blog.e2e.test']) {
      const serverJson = JSON.stringify({ proxyType: 0, serverName, listen: '18991', enable: true, def: 0 });
      await api(page, '/adminPage/server/addOver', { serverJson, serverParamJson: '[]', locationJson: '[]' });
    }
    // 背景排程每分鐘第 30 秒也會同步；先同步一次，之後的測試不必等排程
    expect((await api(page, '/adminPage/crowdsecGuard/waf/sync', {})).outcome).toBe('ok');
  });

  test.afterAll(async () => {
    if (context) {
      await context.close();
    }
    if (proc) {
      if (process.platform === 'win32') {
        try {
          execFileSync('taskkill', ['/F', '/PID', String(proc.pid), '/T'], { stdio: 'ignore' });
        } catch (e) {
          // 已經自己結束了
        }
      } else {
        proc.kill('SIGTERM');
      }
      proc = null;
    }
    if (lapi) {
      lapi.close();
    }
    try {
      fs.rmSync(ENV_DATA_DIR, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    } catch (e) {
      console.warn(`清除 ${ENV_DATA_DIR} 失敗（不影響測試結果）：${e.message}`);
    }
  });

  test('同步後兩個事件：/.env 的 vpatch 與 CRS 併成一列且擋下，SQL 注入只記錄', async () => {
    const app = await openWafTab(page);
    await app.getByRole('button', { name: SYNC_NOW }).click();
    await expect(page.locator('.ant-message')).toContainText(/同步完成|Sync done/);
    const rows = app.locator('.waf-table tbody tr.ant-table-row');
    await expect(rows).toHaveCount(2);
    const envRow = rows.filter({ hasText: IP_ENV });
    await expect(envRow.locator('.waf-rule')).toHaveText(['vpatch-env-access', 'CRS 930130']);
    await expect(envRow.locator('.waf-action')).toHaveAttribute('data-action', 'block');
    await expect(envRow).toContainText('blog.e2e.test');
    await expect(rows.filter({ hasText: IP_SQLI }).locator('.waf-action')).toHaveAttribute('data-action', 'log');
    await expect(app.locator('.waf-last-sync')).toContainText(/上次同步|Last sync/);
  });

  test('依類別、結果、IP、站台篩選', async () => {
    const app = await openWafTab(page);
    const rows = app.locator('.waf-table tbody tr.ant-table-row');
    await expect(rows).toHaveCount(2);

    await choose(page, app.locator('.waf-category'), /SQL 注入|SQL injection/);
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(IP_SQLI);
    await choose(page, app.locator('.waf-category'), /全部類別|全部类别|All categories/);

    await choose(page, app.locator('.waf-action-filter'), /擋下|拦下|Blocked/);
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(IP_ENV);
    await choose(page, app.locator('.waf-action-filter'), /全部結果|全部结果|All results/);

    await app.locator('.waf-ip input').fill(IP_SQLI);
    await app.locator('.waf-ip input').press('Enter');
    await expect(rows).toHaveCount(1);
    await app.locator('.waf-ip input').fill('');
    await app.locator('.waf-ip input').press('Enter');
    await expect(rows).toHaveCount(2);

    await choose(page, app.locator('.waf-site'), 'shop.e2e.test');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(IP_SQLI);
  });

  test('展開看完整請求：方法、User-Agent、每條規則與說明', async () => {
    const app = await openWafTab(page);
    const envRow = app.locator('.waf-table tbody tr.ant-table-row', { hasText: IP_ENV });
    await envRow.locator('.ant-table-row-expand-icon').click();
    await expect(app.locator('.waf-detail-method')).toHaveText('GET');
    await expect(app.locator('.waf-detail-ua')).toHaveText(UA);
    await expect(app.locator('.waf-detail-rules li')).toHaveCount(2);
    await expect(app.locator('.waf-detail-rules')).toContainText('Restricted File Access Attempt');
  });

  test('查這個 IP：帶到 IP 查詢，列出這個 IP 最近的 WAF 事件', async () => {
    const app = await openWafTab(page);
    await app.locator('.waf-table tbody tr.ant-table-row', { hasText: IP_ENV }).locator('.waf-lookup').click();
    await expect.poll(() => page.evaluate(() => window.location.hash)).toBe(`#ip=${IP_ENV}`);
    await expect(app.locator('.ip-waf-item')).toHaveCount(1);
    await expect(app.locator('.ip-waf-item')).toHaveAttribute('data-action', 'block');
    const r = await api(page, `/adminPage/crowdsecGuard/ipLookup?ip=${IP_ENV}`);
    expect(r.wafEvents[0].rules.map((x) => x.ruleName)).toEqual(['crowdsecurity/vpatch-env-access', '930130']);
    expect(cdn).toEqual([]);
  });
});
