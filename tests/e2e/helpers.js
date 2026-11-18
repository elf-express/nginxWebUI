const { spawn, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');

const PROJECT_ROOT = path.resolve(__dirname, '../..');

// 動態解析 target/ 下的 jar（不綁死版本，避免每次 release 後測試找不到 jar）。
// 取最新修改的 nginxWebUI-*.jar，相容 mvn clean package 後只有單一 jar 的情況。
function resolveJarPath() {
  const targetDir = path.join(PROJECT_ROOT, 'target');
  let jars = [];
  try {
    jars = fs.readdirSync(targetDir)
      .filter((f) => /^nginxWebUI-.*\.jar$/.test(f))
      .map((f) => ({ f, m: fs.statSync(path.join(targetDir, f)).mtimeMs }))
      .sort((a, b) => b.m - a.m);
  } catch (e) {
    // target 目錄不存在
  }
  if (jars.length === 0) {
    throw new Error('找不到 target/nginxWebUI-*.jar，請先執行：mvn clean package -DskipTests');
  }
  return path.join(targetDir, jars[0].f);
}
const JAR_PATH = resolveJarPath();
const TEST_DATA_DIR = path.join(__dirname, 'test-data').replace(/\\/g, '/');
const TEST_PORT = 18080;
const BASE_URL = `http://localhost:${TEST_PORT}`;
const TEST_CAPTCHA = '1234';

// 測試帳號
const TEST_ADMIN = 'admin';
const TEST_PASS = 'Admin1234';

let serverProcess = null;

/**
 * 清空測試資料目錄（刪除資料庫）
 */
function cleanTestData() {
  const dbFile = path.join(TEST_DATA_DIR, 'sqlite.db');
  if (fs.existsSync(dbFile)) {
    fs.unlinkSync(dbFile);
  }
  // 清掉 geoip 目錄裡的 mmdb 與下載中途的 .tmp：E2E 一律在「沒有 mmdb」的環境起跑
  const geoipDir = path.join(TEST_DATA_DIR, 'geoip');
  if (fs.existsSync(geoipDir)) {
    for (const name of fs.readdirSync(geoipDir)) {
      if (name.endsWith('.mmdb') || name.endsWith('.tmp')) {
        fs.rmSync(path.join(geoipDir, name), { force: true });
      }
    }
  }
}

/**
 * 等待 HTTP 端口就緒
 */
function waitForReady(timeoutMs = 20000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const check = () => {
      if (Date.now() - start > timeoutMs) {
        return reject(new Error('App failed to start within timeout'));
      }
      const req = http.get(BASE_URL, (res) => {
        resolve();
      });
      req.on('error', () => {
        setTimeout(check, 1000);
      });
      req.end();
    };
    setTimeout(check, 3000);
  });
}

/**
 * 啟動測試用 app（使用獨立資料庫和固定驗證碼）
 */
async function startApp() {
  cleanTestData();

  // 確保 test-data 目錄存在
  if (!fs.existsSync(TEST_DATA_DIR)) {
    fs.mkdirSync(TEST_DATA_DIR, { recursive: true });
  }

  const args = [
    '-jar',
    '-Dfile.encoding=UTF-8',
    // GeoIP 資料庫目錄指到測試資料夾：不讀也不寫本機 /etc/nginx/geoip，E2E 一律在「沒有 mmdb」的環境跑
    `-Dgeoip.dir=${TEST_DATA_DIR}/geoip/`,
    JAR_PATH,
    `--server.port=${TEST_PORT}`,
    `--project.home=${TEST_DATA_DIR}/`,
    `--init.admin=${TEST_ADMIN}`,
    `--init.pass=${TEST_PASS}`,
    `--project.testCaptcha=${TEST_CAPTCHA}`,
    '--project.skipSeedFetch=true',
    '--project.skipKillSelf=true',
    // 排程不要在整點補抓 mmdb（約 75 MB），否則 geoip 目錄會被污染
    '--project.skipGeoipFetch=true',
  ];

  // PG smoke 模式:E2E_DB=postgresql 時連 PostgreSQL(容器由 global-setup-pg.js 啟動,fresh DB)
  if (process.env.E2E_DB === 'postgresql') {
    args.push(
      '--spring.database.type=postgresql',
      `--spring.datasource.url=${process.env.E2E_PG_URL || 'jdbc:postgresql://localhost:15432/nginxwebui'}`,
      `--spring.datasource.username=${process.env.E2E_PG_USER || 'nginxwebui'}`,
      `--spring.datasource.password=${process.env.E2E_PG_PASS || 'nginxwebui123'}`,
    );
  }

  // PATH 上的 java 可能是 Java 8(跑不動 Java 17 jar);優先用 JAVA_HOME
  const javaBin = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', 'java')
    : 'java';

  serverProcess = spawn(javaBin, args, {
    cwd: PROJECT_ROOT,
    stdio: 'pipe',
  });

  // stdout / stderr 都要讀掉:pipe 緩衝區寫滿後 app 寫 log 會被阻塞,請求跟著卡死
  serverProcess.stdout.on('data', () => {});
  serverProcess.stderr.on('data', () => {});

  await waitForReady();
}

/**
 * 停止測試用 app
 */
function stopApp() {
  if (serverProcess) {
    if (process.platform === 'win32') {
      try {
        execFileSync('taskkill', ['/F', '/PID', String(serverProcess.pid), '/T'], { stdio: 'ignore' });
      } catch (e) {
        // ignore
      }
    } else {
      serverProcess.kill('SIGTERM');
    }
    serverProcess = null;
  }
}

/**
 * 登入流程
 */
async function login(page) {
  await page.goto('/adminPage/login');
  await page.waitForSelector('#name');

  await page.locator('#name').fill(TEST_ADMIN);
  await page.locator('#pass').fill(TEST_PASS);

  // 先載入驗證碼圖片（觸發 session 設定固定驗證碼）
  await page.locator('#codeImg').waitFor();

  await page.locator('#code').fill(TEST_CAPTCHA);
  await page.getByRole('button', { name: /登入|登录/ }).click();

  // 等待跳轉到首頁
  await page.waitForURL('**/adminPage/monitor', { timeout: 10000 });
}

/**
 * 後台 CSRF token（E-161）：登入後由 NWUI_CSRF cookie 帶給前端，POST 時放進 X-CSRF-Token header。
 * 測試直接用 page.request 打 POST 端點時要自己帶，瀏覽器內的請求由前端程式帶。
 */
async function csrfHeaders(page) {
  const url = page.url();
  const cookies = await page.context().cookies(url.startsWith('http') ? url : undefined);
  const token = cookies.find((c) => c.name === 'NWUI_CSRF');
  return token ? { 'X-CSRF-Token': token.value } : {};
}

/** 帶 CSRF token 的 page.request.post */
async function csrfPost(page, url, options = {}) {
  const headers = { ...(await csrfHeaders(page)), ...(options.headers || {}) };
  return page.request.post(url, { ...options, headers });
}

/**
 * 獨立 APIRequestContext（request.newContext 或 page.request）版的 CSRF header：
 * token cookie 在登入後第一個後台請求才發，還沒有時先讀一次後台首頁
 */
async function apiCsrfHeaders(api) {
  const find = async () => (await api.storageState()).cookies.find((c) => c.name === 'NWUI_CSRF');
  let token = await find();
  if (!token) {
    await api.get('/adminPage/monitor');
    token = await find();
  }
  if (!token) {
    // 拿不到 token 時請求會被 403 擋下，測試可能因錯的原因通過，直接失敗
    throw new Error('NWUI_CSRF cookie not found: is this API context logged in?');
  }
  return { 'X-CSRF-Token': token.value };
}

/** 帶 CSRF token 的 APIRequestContext.post */
async function apiPost(api, url, options = {}) {
  const headers = { ...(await apiCsrfHeaders(api)), ...(options.headers || {}) };
  return api.post(url, { ...options, headers });
}

module.exports = {
  PROJECT_ROOT,
  JAR_PATH,
  TEST_DATA_DIR,
  TEST_PORT,
  BASE_URL,
  TEST_CAPTCHA,
  TEST_ADMIN,
  TEST_PASS,
  cleanTestData,
  startApp,
  stopApp,
  login,
  csrfHeaders,
  csrfPost,
  apiCsrfHeaders,
  apiPost,
};
