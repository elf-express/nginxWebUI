/**
 * E-184：remote/src 只代為讀取遠端節點的驗證碼圖片。
 * file:／ftp:、其他路徑、link-local 與雲端中繼資料位址一律回 400 且不帶內容；
 * 新增節點時讀對方的 getRemoteCode 照常。
 */
const { test, expect } = require('@playwright/test');
const { BASE_URL, login } = require('./helpers');

const CODE_PATH = '/adminPage/login/getRemoteCode';

test.describe('remote/src 不可當成任意網址代理（E-184）', () => {
  let api;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage({ baseURL: BASE_URL });
    await login(page);
    api = page.request;
  });

  const src = (url) => api.get('/adminPage/remote/src', { params: { url } });

  test('新增節點時可讀對方的驗證碼圖片', async () => {
    const res = await src(`${BASE_URL}${CODE_PATH}?t=${Date.now()}`);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toMatch(/^image\/(png|gif|jpeg)/);
    expect((await res.body()).length).toBeGreaterThan(0);
  });

  for (const url of [
    'file:///etc/passwd',
    'file:///C:/Windows/win.ini',
    'ftp://127.0.0.1:21/x',
    `${BASE_URL}/adminPage/login`,
    `${BASE_URL}${CODE_PATH}/../../login`,
    'http://169.254.169.254/latest/meta-data/',
    `http://169.254.169.254${CODE_PATH}`,
    `http://0.0.0.0${CODE_PATH}`,
  ]) {
    test(`拒絕 ${url}`, async () => {
      const res = await src(url);
      expect(res.status()).toBe(400);
      expect((await res.body()).length).toBe(0);
    });
  }
});
