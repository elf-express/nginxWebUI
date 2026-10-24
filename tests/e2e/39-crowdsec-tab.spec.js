const { test, expect } = require('@playwright/test');
const { spawn, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const http = require('http');
const { PROJECT_ROOT, JAR_PATH, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA, login } = require('./helpers');

// 「CrowdSec 防護」頁的「連線設定」分頁（E-390 起在「防護與憑證」頁，E-385 搬到獨立頁面，Vue）：
//   1) 共用測試 server（18080，沒有任何 CROWDSEC_* 環境變數）→「未設定」提示、寬限秒數保存與後端驗證、
//      測試連線紅燈、A11y、離線
//   2) 本檔另起一個帶 bouncer 金鑰、machine 密碼與連不到的 LAPI 位址的 server（18082）→ 只顯示遮罩值與來源、
//      頁面與 API 沒有明文、紅燈、ASN 不再要求先設定 CrowdSec
//   E-387「驗證頁」區塊：共用 server 上整區停用、後端驗證與預覽輸出；18082（另帶交接票金鑰）上切換方式、儲存、
//      有效時間檢查、金鑰遮罩與預覽按鈕

const CDN = /esm\.sh|cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com/;
// antd 會在兩個中文字的按鈕文字間插入空白（「儲 存」）
const TEST_CONN = /測試連線|测试连接|Test connection/;
const SAVED = /已儲存|已保存|Saved/;
const NOT_SET = /未設定|未配置|Not configured/;
const UNREACHABLE = /連不到 CrowdSec|连不上 CrowdSec|Cannot reach CrowdSec/;
const SOURCE_ENV = /來源：環境變數|来源：环境变量|Source: environment variable/;
const CHALLENGE_DISABLED = /尚未設定 CrowdSec|尚未配置 CrowdSec|CrowdSec is not configured/;
const EXTERNAL_MODE = /外部驗證站|外部验证站|External verification site/;
const BUILTIN_MODE = /內建|内置|Built-in/;
const CHECKBOX_STYLE = /勾選「我不是機器人」|勾选「我不是机器人」|Tick "I'm not a robot"/;
const WILL_GENERATE = /尚未產生|尚未生成|Not generated yet/;
const CHALLENGE_HEADING = /正在確認您不是機器人|正在确认您不是机器人|Checking that you are not a robot/;
const CHALLENGE_DEFAULTS = { mode: 'builtin', style: 'auto', externalUrl: '', ttlMinutes: '60' };

const ENV_PORT = 18082;
const ENV_BASE = `http://localhost:${ENV_PORT}`;
// 放在已被 .gitignore 的 test-data/ 底下；helpers.cleanTestData() 只刪 test-data/sqlite.db
const ENV_DATA_DIR = path.join(__dirname, 'test-data', 'crowdsec').replace(/\\/g, '/');
// 每次執行隨機產生，避免把看起來像憑證的字串寫進原始碼（會被密鑰掃描標紅）
const BOUNCER_KEY = crypto.randomBytes(16).toString('hex');
const MACHINE_PASSWORD = crypto.randomBytes(16).toString('hex');
const TICKET_SECRET = crypto.randomBytes(16).toString('hex');
const MASK = '••••••';

async function openTab(page) {
  await page.goto('/adminPage/crowdsecGuard#conn');
  // 同一網址只差 hash 時 goto 不會重新載入，重新載入讓畫面讀回目前的設定
  await page.reload();
  const app = page.locator('#crowdsecGuardApp');
  await expect(app.locator('h2#crowdsec-title')).toBeVisible();
  await expect(app.locator('.crowdsec-desc')).toBeVisible();
  // 掛載時會自動測一次連線，等它回來再操作
  await expect(app.locator('.crowdsec-light')).not.toHaveAttribute('data-light', 'gray', { timeout: 15000 });
  return app;
}

async function getChallenge(page) {
  const res = await page.request.get('/adminPage/crowdsec/getChallenge');
  const json = await res.json();
  expect(json.success).toBe(true);
  return json.obj;
}

function saveChallenge(page, form) {
  return page.request.post('/adminPage/crowdsec/saveChallenge', { form: { ...CHALLENGE_DEFAULTS, ...form } });
}

async function openAsnTab(page) {
  await page.goto('/adminPage/protectionCert');
  await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
  // asnCatalog.js 的 applyUiGates() 先改橫幅再寫提示文字；提示出現代表橫幅已依 profile 回應更新過
  await expect(page.locator('#asnProfileTip')).toHaveText(/\S/, { timeout: 15000 });
}

async function clickTest(page, app) {
  const status = page.waitForResponse((r) => r.url().includes('/adminPage/crowdsec/status'));
  await app.getByRole('button', { name: TEST_CONN }).click();
  return (await status).json();
}

async function getConfig(page) {
  const res = await page.request.get('/adminPage/crowdsec/getConfig');
  const json = await res.json();
  expect(json.success).toBe(true);
  return json.obj;
}

function waitForReady(base, timeoutMs = 90000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const check = () => {
      if (Date.now() - start > timeoutMs) {
        return reject(new Error(`CrowdSec 測試 server 未在 ${timeoutMs}ms 內啟動`));
      }
      const req = http.get(base, () => resolve());
      req.on('error', () => setTimeout(check, 1000));
      req.end();
    };
    setTimeout(check, 3000);
  });
}

test.describe('CrowdSec 分頁（共用 server，沒有 CrowdSec 變數）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test.afterEach(async ({ page }) => {
    // 寬限秒數與驗證頁設定還原成預設值，不影響其他測試
    await page.request.post('/adminPage/crowdsec/saveConfig', { form: { graceSeconds: '120' } });
    await saveChallenge(page, {});
  });

  test('顯示「未設定」並提示要設哪個變數或檔案', async ({ page }) => {
    const app = await openTab(page);
    await expect(app.locator('.crowdsec-lapi')).toContainText(NOT_SET);
    await expect(app.locator('.crowdsec-lapi')).toContainText('CROWDSEC_LAPI_URL');
    await expect(app.locator('.crowdsec-bouncer')).toContainText(NOT_SET);
    await expect(app.locator('.crowdsec-bouncer')).toContainText('/run/secrets/crowdsec_bouncer_key');
    await expect(app.locator('.crowdsec-bouncer')).toContainText('CROWDSEC_BOUNCER_KEY');
    await expect(app.locator('.crowdsec-machine')).toContainText('nginxwebui');
    await expect(app.locator('.crowdsec-machine')).toContainText('/run/secrets/crowdsec_machine_password');
    await expect(app.locator('.crowdsec-machine')).toContainText('CROWDSEC_MACHINE_PASSWORD');
    await expect(app.locator('.crowdsec-last-ok')).toContainText(/尚無紀錄|暂无记录|No record yet/);
  });

  test('修改寬限秒數後重新載入仍保留', async ({ page }) => {
    let app = await openTab(page);
    await expect(app.locator('.crowdsec-grace input')).toHaveValue('120');
    await app.locator('.crowdsec-grace input').fill('45');
    await app.locator('.crowdsec-save').click();
    await expect(page.locator('.ant-message')).toContainText(SAVED);
    expect((await getConfig(page)).graceSeconds).toBe(45);

    await page.reload();
    app = await openTab(page);
    await expect(app.locator('.crowdsec-grace input')).toHaveValue('45');
  });

  test('畫面擋下超出範圍的寬限秒數', async ({ page }) => {
    const app = await openTab(page);
    await app.locator('.crowdsec-grace input').fill('5');
    await app.locator('.crowdsec-save').click();
    await expect(page.locator('.ant-message')).toContainText(/10/);
    expect((await getConfig(page)).graceSeconds).toBe(120);
  });

  test('後端拒絕不是 10–3600 整數的寬限秒數', async ({ page }) => {
    for (const value of ['5', '3601', 'abc']) {
      const res = await page.request.post('/adminPage/crowdsec/saveConfig', { form: { graceSeconds: value } });
      expect((await res.json()).success).toBe(false);
    }
    expect((await getConfig(page)).graceSeconds).toBe(120);
  });

  test('測試連線在沒有 CrowdSec 時顯示紅燈', async ({ page }) => {
    const app = await openTab(page);
    const json = await clickTest(page, app);
    expect(json.obj.lapi).toBe('notConfigured');
    await expect(app.locator('.crowdsec-light')).toHaveAttribute('data-light', 'red');
    await expect(app.locator('.crowdsec-light')).toContainText(NOT_SET);
  });

  test('ASN 分頁在未設定 CrowdSec 時顯示「請先設定 CrowdSec」橫幅（對照組）', async ({ page }) => {
    await openAsnTab(page);
    await expect(page.locator('#asnCrowdsecBanner')).toBeVisible();
  });

  test('A11y：按鈕都是 type=button 且有名稱、頁面有 h1、沒有 javascript: 連結', async ({ page }) => {
    const app = await openTab(page);
    expect(await page.locator('h1').count()).toBeGreaterThan(0);
    const buttons = await app.locator('button').all();
    expect(buttons.length).toBeGreaterThan(0);
    for (const b of buttons) {
      expect(await b.getAttribute('type')).toBe('button');
      const name = ((await b.getAttribute('aria-label')) || (await b.innerText()) || '').trim();
      expect(name, await b.evaluate((el) => el.outerHTML.slice(0, 200))).not.toBe('');
    }
    await expect(app.locator('a[href^="javascript:"]')).toHaveCount(0);
    await expect(app.locator('.crowdsec-grace input')).toHaveAttribute('aria-label', /寬限秒數|宽限秒数|Grace period/);
  });

  test('驗證頁區塊在 CrowdSec 未設定時整區停用並說明原因', async ({ page }) => {
    const app = await openTab(page);
    const box = app.locator('.crowdsec-challenge');
    await expect(box.locator('#crowdsec-challenge-title')).toBeVisible();
    await expect(box.locator('.crowdsec-challenge-disabled')).toContainText(CHALLENGE_DISABLED);
    await expect(box.locator('.crowdsec-challenge-save')).toBeDisabled();
    await expect(box.locator('.crowdsec-challenge-preview')).toBeDisabled();
    await expect(box.locator('.crowdsec-challenge-mode input[value="external"]')).toBeDisabled();
    await expect(box.locator('.crowdsec-challenge-ttl input')).toBeDisabled();
    await expect(box.locator('.crowdsec-challenge-ttl input')).toHaveValue('60');
  });

  test('驗證頁設定：後端拒絕不合法的值，外部驗證站需要交接票金鑰', async ({ page }) => {
    const bad = [{ ttlMinutes: '4' }, { ttlMinutes: '1441' }, { ttlMinutes: 'abc' }, { mode: 'other' },
      { style: 'other' }, { externalUrl: 'not a url' }, { mode: 'external', externalUrl: '' }];
    for (const form of bad) {
      expect((await (await saveChallenge(page, form)).json()).success, JSON.stringify(form)).toBe(false);
    }
    const ext = await (await saveChallenge(page, { mode: 'external', externalUrl: 'https://verify.example/' })).json();
    expect(ext.success).toBe(false);
    expect(ext.msg).toMatch(/交接票金鑰|交接票密钥|handoff ticket key/);
    expect((await (await saveChallenge(page, { style: 'checkbox', ttlMinutes: '30' })).json()).success).toBe(true);
    const c = await getChallenge(page);
    expect([c.mode, c.style, c.ttlMinutes, c.enabled]).toEqual(['builtin', 'checkbox', 30, false]);
  });

  test('預覽驗證頁：兩種樣式都是同一份頁面，不從外網載資源', async ({ page }) => {
    for (const style of ['auto', 'checkbox']) {
      const res = await page.request.get(`/adminPage/crowdsec/challengePreview?style=${style}`);
      expect(res.status()).toBe(200);
      expect(res.headers()['content-type']).toContain('text/html');
      const html = await res.text();
      expect(html).toContain('<altcha-widget');
      expect(html).toContain(`data-style="${style}"`);
      expect(html.includes('auto="onload"')).toBe(style === 'auto');
      expect(html).not.toMatch(/https?:\/\//);
    }
  });

  test('整頁與分頁操作都不請求外網 CDN', async ({ page }) => {
    const hits = [];
    page.on('request', (req) => {
      if (CDN.test(req.url())) {
        hits.push(req.url());
      }
    });
    const app = await openTab(page);
    await clickTest(page, app);
    expect(hits, `不應有外網 CDN 請求，實際：${hits.join(', ')}`).toEqual([]);
  });
});

test.describe('CrowdSec 分頁（帶金鑰與管理憑證的 server）', () => {
  let proc = null;
  let context = null;
  let page = null;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180000);
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
      // 共用的 18080 server 還在跑；不帶這個，啟動時會把它當舊版本殺掉
      '--project.skipKillSelf=true',
    ], {
      cwd: PROJECT_ROOT,
      stdio: 'pipe',
      env: {
        ...process.env,
        CROWDSEC_BOUNCER_KEY: BOUNCER_KEY,
        CROWDSEC_MACHINE_PASSWORD: MACHINE_PASSWORD,
        NWU_CHALLENGE_TICKET_SECRET: TICKET_SECRET,
        // 沒有東西在聽的埠：測試連線必定連不到
        CROWDSEC_LAPI_URL: 'http://127.0.0.1:9',
      },
    });
    proc.stdout.on('data', () => {});
    proc.stderr.on('data', () => {});
    await waitForReady(ENV_BASE);

    context = await browser.newContext({ baseURL: ENV_BASE });
    page = await context.newPage();
    await login(page);
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
    try {
      fs.rmSync(ENV_DATA_DIR, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    } catch (e) {
      console.warn(`清除 ${ENV_DATA_DIR} 失敗（不影響測試結果）：${e.message}`);
    }
  });

  test('金鑰與管理憑證只顯示遮罩值與來源', async () => {
    const app = await openTab(page);
    await expect(app.locator('.crowdsec-bouncer')).toContainText(MASK + BOUNCER_KEY.slice(-4));
    await expect(app.locator('.crowdsec-bouncer')).toContainText(SOURCE_ENV);
    await expect(app.locator('.crowdsec-machine')).toContainText(MASK + MACHINE_PASSWORD.slice(-4));
    await expect(app.locator('.crowdsec-machine')).toContainText(SOURCE_ENV);
    await expect(app.locator('.crowdsec-lapi')).toHaveText('http://127.0.0.1:9');
    const html = await page.content();
    expect(html).not.toContain(BOUNCER_KEY);
    expect(html).not.toContain(MACHINE_PASSWORD);
  });

  test('getConfig 不回傳明文', async () => {
    const res = await page.request.get('/adminPage/crowdsec/getConfig');
    const text = await res.text();
    expect(text).not.toContain(BOUNCER_KEY);
    expect(text).not.toContain(MACHINE_PASSWORD);
    const obj = JSON.parse(text).obj;
    expect(obj.bouncerKey.source).toBe('ENV');
    expect(obj.machinePassword.source).toBe('ENV');
    expect(obj.configured).toBe(true);
  });

  test('LAPI 連不到時測試連線顯示紅燈', async () => {
    const app = await openTab(page);
    const json = await clickTest(page, app);
    expect(json.obj.lapi).toBe('unreachable');
    await expect(app.locator('.crowdsec-light')).toHaveAttribute('data-light', 'red');
    await expect(app.locator('.crowdsec-light')).toContainText(UNREACHABLE);
  });

  test('驗證頁：切換方式時外部網址欄位啟用／停用，儲存後重新載入仍保留', async () => {
    let app = await openTab(page);
    const box = app.locator('.crowdsec-challenge');
    await expect(box.locator('.crowdsec-challenge-disabled')).toHaveCount(0);
    await expect(box.locator('.crowdsec-challenge-url')).toBeDisabled();
    await box.locator('.crowdsec-challenge-mode').getByText(EXTERNAL_MODE).click();
    await expect(box.locator('.crowdsec-challenge-url')).toBeEnabled();
    await box.locator('.crowdsec-challenge-url').fill('https://verify.example.workers.dev/');
    await box.locator('.crowdsec-challenge-style').getByText(CHECKBOX_STYLE).click();
    await box.locator('.crowdsec-challenge-ttl input').fill('30');
    await box.locator('.crowdsec-challenge-save').click();
    await expect(page.locator('.ant-message')).toContainText(SAVED);
    const c = await getChallenge(page);
    expect([c.mode, c.style, c.externalUrl, c.ttlMinutes])
      .toEqual(['external', 'checkbox', 'https://verify.example.workers.dev/', 30]);

    await page.reload();
    app = await openTab(page);
    await expect(app.locator('.crowdsec-challenge-url')).toHaveValue('https://verify.example.workers.dev/');
    await expect(app.locator('.crowdsec-challenge-url')).toBeEnabled();
    await expect(app.locator('.crowdsec-challenge-ttl input')).toHaveValue('30');
    await app.locator('.crowdsec-challenge-mode').getByText(BUILTIN_MODE).click();
    await expect(app.locator('.crowdsec-challenge-url')).toBeDisabled();
    expect((await (await saveChallenge(page, {})).json()).success).toBe(true);
  });

  test('驗證頁：畫面擋下超出範圍的有效時間', async () => {
    const app = await openTab(page);
    await app.locator('.crowdsec-challenge-ttl input').fill('4');
    await app.locator('.crowdsec-challenge-save').click();
    await expect(page.locator('.ant-message')).toContainText(/1440/);
    expect((await getChallenge(page)).ttlMinutes).toBe(60);
  });

  test('驗證頁：金鑰只顯示遮罩值與來源', async () => {
    const app = await openTab(page);
    await expect(app.locator('.crowdsec-challenge-ticket')).toContainText(MASK + TICKET_SECRET.slice(-4));
    await expect(app.locator('.crowdsec-challenge-ticket')).toContainText(SOURCE_ENV);
    await expect(app.locator('.crowdsec-challenge-cookie')).toContainText(WILL_GENERATE);
    expect(await page.content()).not.toContain(TICKET_SECRET);
    const text = await (await page.request.get('/adminPage/crowdsec/getChallenge')).text();
    expect(text).not.toContain(TICKET_SECRET);
    expect(JSON.parse(text).obj.ticketSecret.source).toBe('ENV');
  });

  test('驗證頁：預覽按鈕開出驗證頁', async () => {
    const app = await openTab(page);
    const [popup] = await Promise.all([
      page.waitForEvent('popup'),
      app.locator('.crowdsec-challenge-preview').click(),
    ]);
    await popup.waitForLoadState();
    expect(popup.url()).toContain('/adminPage/crowdsec/challengePreview?style=auto');
    await expect(popup.locator('h1')).toHaveText(CHALLENGE_HEADING);
    await expect(popup.locator('.nwu-ip')).toHaveText('203.0.113.10');
    await popup.close();
  });

  test('ASN 分頁不再要求先設定 CrowdSec', async () => {
    const res = await page.request.get('/adminPage/asn/profile');
    expect((await res.json()).obj.crowdsecConfigured).toBe(true);
    await openAsnTab(page);
    await expect(page.locator('#asnCrowdsecBanner')).toBeHidden();
  });
});
