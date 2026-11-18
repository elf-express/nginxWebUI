/**
 * E-64：管理員管理端點（/adminPage/admin/*）只給超管。
 * 受限帳號（type=1）不論帶不帶 showAdmin=true 都被拒，資料不變；超管照常可用。
 */
const { test, expect, request } = require('@playwright/test');
const { BASE_URL, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA, apiPost } = require('./helpers');

const b64 = (v) => Buffer.from(Buffer.from(v).toString('base64')).toString('base64');

const LIMITED = { name: 'e64-limited', pass: 'E64-limited-pass' };
const VICTIM = { name: 'e64-victim', pass: 'E64-victim-pass' };

/** 以 API 登入，回傳帶 session 的 request context；登入失敗回傳 null */
async function loginAs(name, pass) {
  const api = await request.newContext({ baseURL: BASE_URL });
  await api.get('/adminPage/login/getCode');
  const res = await api.post('/adminPage/login/login', {
    form: { name: b64(name), pass: b64(pass), code: b64(TEST_CAPTCHA), authCode: b64('') },
  });
  const body = await res.json();
  if (!body.success) {
    await api.dispose();
    return null;
  }
  return api;
}

/** 從管理員列表頁找出帳號 id */
async function adminId(api, name) {
  const html = await (await api.get('/adminPage/admin?limit=1000')).text();
  const match = html.match(new RegExp(`value="([^"]+)">\\s*</td>\\s*<td>${name}</td>`));
  return match ? match[1] : null;
}

/** 由超管建立受限帳號並設定密碼，回傳 id */
async function createLimitedAdmin(root, { name, pass }) {
  const added = await (await apiPost(root, '/adminPage/admin/addOver', { form: { name, api: 'false', type: '1' } })).json();
  expect(added.success).toBe(true);
  const id = await adminId(root, name);
  expect(id).toBeTruthy();
  const changed = await (await apiPost(root, '/adminPage/admin/changePassOver', { form: { id, pass, auth: 'false' } })).json();
  expect(changed.success).toBe(true);
  return id;
}

test.describe('管理員管理端點只給超管（E-64）', () => {
  let root;
  let limitedId;
  let victimId;

  test.beforeAll(async () => {
    root = await loginAs(TEST_ADMIN, TEST_PASS);
    expect(root).not.toBeNull();
    limitedId = await createLimitedAdmin(root, LIMITED);
    victimId = await createLimitedAdmin(root, VICTIM);
  });

  test.afterAll(async () => {
    const ids = [limitedId, victimId].filter(Boolean).join(',');
    if (ids) {
      await apiPost(root, '/adminPage/admin/del', { form: { id: ids } });
    }
    await root.dispose();
  });

  for (const showAdmin of [undefined, 'true']) {
    test(`受限帳號${showAdmin ? '帶 showAdmin=true ' : ''}被拒且資料不變`, async () => {
      const limited = await loginAs(LIMITED.name, LIMITED.pass);
      expect(limited).not.toBeNull();
      const extra = showAdmin ? { showAdmin } : {};

      const attempts = [
        ['addOver', { id: limitedId, name: LIMITED.name, api: 'false', type: '0' }],
        ['changePassOver', { id: victimId, pass: 'hacked-pass', auth: 'false' }],
        ['del', { id: victimId }],
        ['getMailSetting', {}],
      ];
      for (const [action, form] of attempts) {
        const body = await (await apiPost(limited, `/adminPage/admin/${action}`, { form: { ...form, ...extra } })).json();
        expect(body.success, action).toBe(false);
        expect(body.status, action).toBe('401');
        expect(body.obj ?? null, action).toBeNull();
      }
      await limited.dispose();

      // 受限帳號沒被升為超管
      const self = await (await root.get(`/adminPage/admin/detail?id=${limitedId}`)).json();
      expect(self.obj.admin.type).toBe(1);
      // 目標帳號沒被刪除、密碼沒被改
      expect(await adminId(root, VICTIM.name)).toBe(victimId);
      const victim = await loginAs(VICTIM.name, VICTIM.pass);
      expect(victim).not.toBeNull();
      await victim.dispose();
    });
  }

  test('超管照常可讀郵件設定', async () => {
    const body = await (await apiPost(root, '/adminPage/admin/getMailSetting')).json();
    expect(body.success).toBe(true);
    expect(body.status).toBe('200');
  });
});
