const { test, expect } = require('@playwright/test');
const { spawn, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const http = require('http');
const { PROJECT_ROOT, JAR_PATH, TEST_DATA_DIR, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA, login } = require('./helpers');

// 「CrowdSec 防護 → WAF 規則」分頁（E-395，Vue）：
//   1) 共用測試 server（18080）沒有 crowdsec 容器：規則目錄直接寫進 <home>/crowdsec-waf/catalog/，
//      套用結果由測試寫 result.json，模擬 crowdsec 容器內的監看程式
//   2) 本檔另起一個 server（18084），CROWDSEC_LAPI_URL 指向本檔的假 LAPI（回一筆命中 CRS 942100 的 WAF alert）→
//      WAF 事件分頁的「誤判？調整規則」跳到 WAF 規則分頁的那條規則

const CDN = /esm\.sh|cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com/;
const LOG_ONLY = /只記錄|仅记录|Log only/;
const WAF_DIR = path.join(TEST_DATA_DIR, 'crowdsec-waf');
const CATALOG = path.join(WAF_DIR, 'catalog');

function writeCatalog(dir = CATALOG) {
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'vpatch.json'), JSON.stringify([
    { name: 'crowdsecurity/vpatch-CVE-2017-9841', description: 'PHPUnit RCE', label: 'PHPUnit - RCE', cves: ['CVE-2017-9841'] },
    { name: 'crowdsecurity/vpatch-env-access', description: 'env', label: 'Generic .env access', cves: [] },
  ]));
  fs.writeFileSync(path.join(dir, 'crs.tsv'), '942100\t1\tSQL Injection Attack Detected via libinjection\n941100\t1\tXSS Attack Detected via libinjection\n');
  fs.writeFileSync(path.join(dir, 'meta.json'), JSON.stringify({ generatedAt: '2026-10-24T00:00:00Z', crowdsec: 'v1.8.1' }));
}

// 模擬監看程式：讀 nginxWebUI 寫的套用編號，回報結果；成功時像監看程式一樣把生效的設定存成 last-good.yaml
function writeResult(status, message) {
  const id = fs.readFileSync(path.join(WAF_DIR, 'request'), 'utf8').trim();
  if (status === 'ok') {
    fs.copyFileSync(path.join(WAF_DIR, 'custom.yaml'), path.join(WAF_DIR, 'last-good.yaml'));
  }
  fs.writeFileSync(path.join(WAF_DIR, 'result.json'), JSON.stringify({ id, status, message, at: '2026-10-24T01:00:00Z' }));
  return id;
}

async function pickOption(page, name) {
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: name }).click();
}

// 操作日誌列表只有按鈕，內容要逐筆讀 detail
async function operateLogHas(page, text) {
  const list = await (await page.request.post('/adminPage/operateLog', { form: { curr: '1', limit: '100' } })).text();
  const ids = [...list.matchAll(/detail\('(\d+)'\)/g)].map((m) => m[1]);
  for (const id of ids) {
    const detail = await (await page.request.get(`/adminPage/operateLog/detail?id=${id}`)).text();
    if (detail.includes(text)) {
      return true;
    }
  }
  return false;
}

test.describe.serial('WAF 規則分頁（共用 server，規則目錄與結果以檔案模擬）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test.afterAll(async ({ browser }) => {
    // 把這組測試存的設定還原成預設，再清掉模擬的共用目錄
    const context = await browser.newContext({ baseURL: 'http://localhost:18080' });
    try {
      const page = await context.newPage();
      await login(page);
      writeCatalog();
      await page.request.post('/adminPage/crowdsecGuard/wafRules/apply', {
        form: {
          changes: JSON.stringify([
            { scope: 'category', ruleKey: 'crs-sqli', action: 'count', excludedServerIds: '' },
            { scope: 'category', ruleKey: 'crs-xss', action: 'count', excludedServerIds: '' },
            { scope: 'rule', ruleKey: '942100', action: 'inherit', excludedServerIds: '' },
          ]),
        },
      });
    } finally {
      await context.close();
      fs.rmSync(WAF_DIR, { recursive: true, force: true });
    }
  });

  test('沒有規則目錄時顯示 CrowdSec 未提供規則清單', async ({ page }) => {
    fs.rmSync(WAF_DIR, { recursive: true, force: true });
    await page.goto('/adminPage/crowdsecGuard#rules');
    await expect(page.locator('.waf-no-catalog')).toBeVisible();
    const res = await page.request.post('/adminPage/crowdsecGuard/wafRules/apply', { form: { changes: '[]' } });
    expect((await res.json()).success).toBe(false);
  });

  test('預設只列有命中的規則；顯示全部與搜尋 CVE；沒有 CDN；動作都是 button', async ({ page }) => {
    writeCatalog();
    const cdn = [];
    page.on('request', (r) => { if (CDN.test(r.url())) cdn.push(r.url()); });
    await page.goto('/adminPage/crowdsecGuard#rules');
    await expect(page.locator('.waf-only-hits')).toBeVisible();
    await expect(page.locator('.waf-section[data-source="crs"] .ant-table-row')).toHaveCount(0);
    await page.locator('.waf-show-all-switch').click();
    await expect(page.locator('.waf-section[data-source="crs"] .ant-table-row')).toHaveCount(2);
    await page.locator('.waf-search input').fill('CVE-2017-9841');
    await expect(page.locator('.waf-section[data-source="vpatch"] .ant-table-row')).toHaveCount(1);
    await expect(page.locator('.waf-section[data-source="crs"] .ant-table-row')).toHaveCount(0);
    expect(await page.locator('.waf-rules a[href^="javascript:"]').count()).toBe(0);
    await expect(page.locator('.waf-apply')).toHaveAttribute('type', 'button');
    expect(cdn).toEqual([]);
  });

  test('工具列和下一行說明不重疊', async ({ page }) => {
    writeCatalog();
    await page.goto('/adminPage/crowdsecGuard#rules');
    await expect(page.locator('.waf-catalog')).toBeVisible();
    const box = await page.evaluate(() => ({
      barBottom: document.querySelector('.waf-bar').getBoundingClientRect().bottom,
      catalogTop: document.querySelector('.waf-catalog').getBoundingClientRect().top,
    }));
    expect(box.barBottom).toBeLessThanOrEqual(box.catalogTop);
  });

  test('調整分類與單條、排除站台，套用後寫出設定與操作日誌，重新整理仍保留', async ({ page }) => {
    writeCatalog();
    const serverJson = JSON.stringify({ proxyType: 0, serverName: 'waf-e2e.test', listen: '18991', enable: true, def: 0 });
    await page.request.post('/adminPage/server/addOver', { form: { serverJson, serverParamJson: '[]', locationJson: '[]' } });
    const servers = (await (await page.request.get('/adminPage/crowdsecGuard/servers')).json()).obj;
    const site = servers.find((s) => s.name === 'waf-e2e.test');
    expect(site, 'waf-e2e.test 站台建立失敗').toBeTruthy();
    try {
      await page.goto('/adminPage/crowdsecGuard#rules=942100');
      const sqli = page.locator('.waf-section[data-source="crs"] .ant-table-row').filter({ hasText: /SQL/ }).first();
      await sqli.locator('.waf-cat-action').click();
      await pickOption(page, LOG_ONLY);
      const rule = page.locator('.waf-rule-table .ant-table-row').filter({ hasText: '942100' });
      await rule.locator('.waf-rule-exclude').click();
      await pickOption(page, 'waf-e2e.test');
      await page.keyboard.press('Escape');
      await expect(page.locator('.waf-changes')).toContainText('2');
      await page.locator('.waf-apply').click();
      await expect(page.locator('.waf-status')).toContainText(/等待|waiting/);
      const custom = fs.readFileSync(path.join(WAF_DIR, 'custom.yaml'), 'utf8');
      expect(custom).toContain('.id in [942100]');
      expect(custom).toContain('lower(split(req.Host, ":")[0]) in ["waf-e2e.test"]');
      expect(custom).toContain('RemoveOutBandRuleByID(942100)');
      writeResult('ok', '');
      await expect(page.locator('.waf-status')).toContainText(/套用成功|应用成功|Applied/, { timeout: 10000 });
      await page.reload();
      const sqli2 = page.locator('.waf-section[data-source="crs"] .ant-table-row').filter({ hasText: /SQL/ }).first();
      await expect(sqli2.locator('.waf-cat-action')).toContainText(LOG_ONLY);
      await expect(page.locator('.waf-not-applied')).toHaveCount(0);
      expect(await operateLogHas(page, '[CrowdSec WAF] apply')).toBe(true);
    } finally {
      await page.request.post('/adminPage/server/del', { form: { id: site.id } });
    }
  });

  test('套用失敗時顯示 CrowdSec 回報的原因', async ({ page }) => {
    writeCatalog();
    await page.goto('/adminPage/crowdsecGuard#rules');
    await page.locator('.waf-show-all-switch').click();
    const xss = page.locator('.waf-section[data-source="crs"] .ant-table-row').filter({ hasText: /XSS/ }).first();
    await xss.locator('.waf-cat-action').click();
    await pickOption(page, /停用|Disabled/);
    await page.locator('.waf-apply').click();
    // 上一支測試的成功結果在載入時就會顯示；等到「已送出」才代表這次的 request 檔已寫好
    await expect(page.locator('.waf-status')).toContainText(/等待|waiting/);
    writeResult('failed', 'yaml: line 3: did not find expected node content');
    await expect(page.locator('.waf-status')).toContainText('did not find expected node content', { timeout: 10000 });
  });

  test('站台網域改過後顯示尚未套用，沒有變更也能重新套用', async ({ page }) => {
    writeCatalog();
    const serverJson = (extra) => JSON.stringify({ proxyType: 0, serverName: 'waf-sync.test', listen: '18992', enable: true, def: 0, ...extra });
    await page.request.post('/adminPage/server/addOver', { form: { serverJson: serverJson({}), serverParamJson: '[]', locationJson: '[]' } });
    const servers = (await (await page.request.get('/adminPage/crowdsecGuard/servers')).json()).obj;
    const site = servers.find((s) => s.name === 'waf-sync.test');
    expect(site, 'waf-sync.test 站台建立失敗').toBeTruthy();
    try {
      // 排除這個站台的 942100，並模擬監看程式套用成功（custom.yaml 同時存成 last-good.yaml）
      const res = await page.request.post('/adminPage/crowdsecGuard/wafRules/apply', {
        form: { changes: JSON.stringify([{ scope: 'rule', ruleKey: '942100', action: 'inherit', excludedServerIds: site.id }]) },
      });
      expect((await res.json()).success).toBe(true);
      writeResult('ok', '');
      await page.goto('/adminPage/crowdsecGuard#rules');
      await expect(page.locator('.waf-apply')).toBeVisible();
      await expect(page.locator('.waf-not-applied')).toHaveCount(0);

      // 改掉被排除站台的 server_name：資料庫設定產生的設定和生效的不同了
      await page.request.post('/adminPage/server/addOver', {
        form: { serverJson: serverJson({ id: site.id, serverName: 'waf-sync-renamed.test' }), serverParamJson: '[]', locationJson: '[]' },
      });
      await page.reload();
      await expect(page.locator('.waf-not-applied')).toBeVisible();
      // 尚未套用時不顯示上一次的「套用成功」，兩則訊息互相矛盾
      await expect(page.locator('.waf-status')).toHaveCount(0);

      // 不改任何規則直接套用：寫出新的套用編號，設定檔帶新的網域
      const before = fs.readFileSync(path.join(WAF_DIR, 'request'), 'utf8').trim();
      await page.locator('.waf-apply').click();
      await expect(page.locator('.waf-status')).toContainText(/等待|waiting/);
      expect(fs.readFileSync(path.join(WAF_DIR, 'request'), 'utf8').trim()).not.toBe(before);
      expect(fs.readFileSync(path.join(WAF_DIR, 'custom.yaml'), 'utf8')).toContain('"waf-sync-renamed.test"');
      writeResult('ok', '');
      await expect(page.locator('.waf-status')).toContainText(/套用成功|应用成功|Applied/, { timeout: 10000 });
      await expect(page.locator('.waf-not-applied')).toHaveCount(0);
    } finally {
      await page.request.post('/adminPage/server/del', { form: { id: site.id } });
    }
  });

  test('後端拒絕不合法的設定', async ({ page }) => {
    writeCatalog();
    const res = await page.request.post('/adminPage/crowdsecGuard/wafRules/apply', {
      form: { changes: JSON.stringify([{ scope: 'rule', ruleKey: '942100', action: 'block', excludedServerIds: '' }]) },
    });
    const json = await res.json();
    expect(json.success).toBe(false);
    expect(json.msg).toContain('942100');
  });
});

// ---------- 假 LAPI 的 server ----------

const ENV_PORT = 18084;
const ENV_BASE = `http://localhost:${ENV_PORT}`;
// 放在已被 .gitignore 的 test-data/ 底下；helpers.cleanTestData() 只刪 test-data/sqlite.db
const ENV_DATA_DIR = path.join(__dirname, 'test-data', 'waf-rules').replace(/\\/g, '/');
const MACHINE_PASSWORD = crypto.randomBytes(16).toString('hex');
const BOUNCER_KEY = crypto.randomBytes(16).toString('hex');
const IP_SQLI = '198.51.100.10';
const SQLI_URI = '/search?q=1%27%20UNION%20SELECT%20password%20FROM%20users--';

const list = (...v) => JSON.stringify(v);
const meta = (...kv) => {
  const a = [];
  for (let i = 0; i < kv.length; i += 2) {
    a.push({ key: kv[i], value: kv[i + 1] });
  }
  return a;
};

// 格式照 crowdsec v1.8.1 的 WAF alert（同 41 號）：CRS 942100 只記錄，SQL 注入
function fixtures() {
  const at = Date.now() - 2 * 60 * 1000;
  const iso = new Date(Math.floor(at / 1000) * 1000).toISOString().replace('.000Z', 'Z');
  return JSON.stringify([{
    id: 1,
    kind: 'waf',
    message: `WAF out-of-band match: anomaly score out-of-band: sql_injection: 10, anomaly: 10,  from ${IP_SQLI} (127.0.0.1)`,
    created_at: iso,
    start_at: iso,
    stop_at: iso,
    source: { ip: IP_SQLI, value: IP_SQLI, scope: 'Ip' },
    meta: meta('method', list('GET'), 'target_uri', list(SQLI_URI), 'name', list('native_rule:942100'), 'user_agent', list('Mozilla/5.0 e395-e2e')),
    events: [{
      meta: meta('rule_name', 'native_rule:942100', 'message', 'SQL Injection Attack Detected via libinjection', 'uri', SQLI_URI, 'target_fqdn', 'shop.e2e.test'),
    }],
  }]);
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
        return reject(new Error(`WAF 規則測試 server 未在 ${timeoutMs}ms 內啟動`));
      }
      const req = http.get(base, () => resolve());
      req.on('error', () => setTimeout(check, 1000));
      req.end();
    };
    setTimeout(check, 3000);
  });
}

test.describe('WAF 事件的「誤判？調整規則」（假 LAPI）', () => {
  let lapi = null;
  let proc = null;
  let context = null;
  let page = null;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180000);
    lapi = await startFakeLapi(fixtures());
    fs.rmSync(ENV_DATA_DIR, { recursive: true, force: true });
    fs.mkdirSync(ENV_DATA_DIR, { recursive: true });
    writeCatalog(path.join(ENV_DATA_DIR, 'crowdsec-waf', 'catalog'));

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
      // 不終止已在執行的共用 server
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
    await login(page);
    // 背景排程每分鐘第 30 秒也會同步；先同步一次，不必等排程
    const res = await page.request.post('/adminPage/crowdsecGuard/waf/sync');
    const json = await res.json();
    expect(json.success, JSON.stringify(json)).toBe(true);
    expect(json.obj.outcome).toBe('ok');
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

  test('WAF 事件按「誤判？調整規則」跳到該規則', async () => {
    await page.goto(`/adminPage/crowdsecGuard?t=${Date.now()}#waf`);
    const row = page.locator('.waf-table tbody tr.ant-table-row', { hasText: IP_SQLI });
    await expect(row).toHaveCount(1);
    await row.locator('.ant-table-row-expand-icon').click();
    await page.locator('.waf-tune').first().click();
    await expect.poll(() => page.evaluate(() => window.location.hash)).toBe('#rules=942100');
    await expect(page.locator('.waf-search input')).toHaveValue('942100');
    await expect(page.locator('.waf-rule-table .ant-table-row').filter({ hasText: '942100' })).toBeVisible();
  });
});
