/**
 * E-65：登入前後的端點不得回傳密碼雜湊、TOTP 密鑰與 API token；
 * getAuth 帶 remote 參數時仍要驗證碼。新增遠端節點改為先輸入遠端圖形驗證碼。
 */
const { test, expect, request } = require('@playwright/test');
const { BASE_URL, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA, login } = require('./helpers');

const b64 = (v) => Buffer.from(Buffer.from(v).toString('base64')).toString('base64');
const SECRET_FIELDS = ['pass', 'key', 'token'];

async function freshApi() {
  return request.newContext({ baseURL: BASE_URL });
}

test.describe('登入端點不回傳機密（E-65）', () => {

  test('getAuth 帶 remote 但沒有驗證碼會被拒絕', async () => {
    const api = await freshApi();
    const res = await api.post('/adminPage/login/getAuth', {
      form: { name: b64(TEST_ADMIN), pass: b64(TEST_PASS), remote: '1' },
    });
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.obj ?? null).toBeNull();
    await api.dispose();
  });

  test('getAuth 只回傳是否開啟兩步驗證', async () => {
    const api = await freshApi();
    await api.get('/adminPage/login/getCode');
    const res = await api.post('/adminPage/login/getAuth', {
      form: { name: b64(TEST_ADMIN), pass: b64(TEST_PASS), code: b64(TEST_CAPTCHA) },
    });
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(Object.keys(body.obj)).toEqual(['auth']);
    await api.dispose();
  });

  test('login 與 autoLogin 的回應不含密碼雜湊、密鑰與 token', async () => {
    const api = await freshApi();
    await api.get('/adminPage/login/getCode');
    const res = await api.post('/adminPage/login/login', {
      form: { name: b64(TEST_ADMIN), pass: b64(TEST_PASS), code: b64(TEST_CAPTCHA), authCode: b64('') },
    });
    const body = await res.json();
    expect(body.success).toBe(true);
    for (const field of SECRET_FIELDS) {
      expect(body.obj).not.toHaveProperty(field);
    }
    expect(typeof body.obj.autoKey).toBe('string');

    const auto = await (await api.post('/adminPage/login/autoLogin', { form: { autoKey: body.obj.autoKey } })).json();
    expect(auto.success).toBe(true);
    for (const field of SECRET_FIELDS) {
      expect(auto.obj ?? {}).not.toHaveProperty(field);
    }
    await api.dispose();
  });

  test('新增遠端節點：先輸入遠端圖形驗證碼即可完成', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/remote');

    await page.locator('button[onclick="add()"]').click();
    await page.locator('#ip').fill('127.0.0.1');
    await page.locator('#port').fill(String(new URL(BASE_URL).port));
    await page.locator('#name').fill(TEST_ADMIN);
    await page.locator('#pass').fill(TEST_PASS);
    await page.locator('#windowDiv button[onclick="addOver()"]').click();

    // 對話框先顯示遠端圖形驗證碼，不顯示身份驗證碼
    await expect(page.locator('#imgCode')).toBeVisible();
    await expect(page.locator('#authCode')).toBeHidden();
    await page.locator('#codeInput').fill(TEST_CAPTCHA);

    const reloaded = page.waitForEvent('load');
    await page.locator('#codeDiv button[onclick="addOverSubmit()"]').click();
    await reloaded;

    const table = await (await page.request.get('/adminPage/remote/allTable')).json();
    const added = (table.data || []).filter((r) => r.ip === '127.0.0.1' && String(r.port) === new URL(BASE_URL).port);
    expect(added).toHaveLength(1);

    for (const r of added) {
      await page.request.post('/adminPage/remote/del', { form: { id: r.id } });
    }
  });

});
