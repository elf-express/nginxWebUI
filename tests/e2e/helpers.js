const { spawn, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');

const PROJECT_ROOT = path.resolve(__dirname, '../..');
const JAR_PATH = path.join(PROJECT_ROOT, 'target', 'nginxWebUI-5.0.1.jar');
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

  serverProcess = spawn('java', [
    '-jar',
    '-Dfile.encoding=UTF-8',
    JAR_PATH,
    `--server.port=${TEST_PORT}`,
    `--project.home=${TEST_DATA_DIR}/`,
    `--init.admin=${TEST_ADMIN}`,
    `--init.pass=${TEST_PASS}`,
    `--project.testCaptcha=${TEST_CAPTCHA}`,
  ], {
    cwd: PROJECT_ROOT,
    stdio: 'pipe',
  });

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
};
