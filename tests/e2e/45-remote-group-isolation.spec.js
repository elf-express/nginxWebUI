/**
 * E-66：遠端節點依群組隔離。受限帳號只看得到、只能讀取與切換自己群組內的節點；
 * 任何遠端節點回應都不含連線密碼與 creditKey，超級管理員行為不變。
 */
const { test, expect, request } = require('@playwright/test');
const { BASE_URL, TEST_ADMIN, TEST_PASS, TEST_CAPTCHA, login } = require('./helpers');

const b64 = (v) => Buffer.from(Buffer.from(v).toString('base64')).toString('base64');
const PORT = new URL(BASE_URL).port;
const SUFFIX = Date.now().toString(36);
const GROUP_A = `e66-group-a-${SUFFIX}`;
const GROUP_B = `e66-group-b-${SUFFIX}`;
const NODE_A = `e66-node-a-${SUFFIX}`;
const NODE_B = `e66-node-b-${SUFFIX}`;
const LIMITED = `e66limited${SUFFIX}`;
const LIMITED_PASS = 'Limited1234';

/** 回應文字不得含連線憑證欄位 */
function expectNoCredentials(text) {
  expect(text).not.toContain('"pass"');
  expect(text).not.toContain('"creditKey"');
}

async function allTable(api) {
  const res = await api.get('/adminPage/remote/allTable');
  const text = await res.text();
  expectNoCredentials(text);
  return JSON.parse(text).data || [];
}

/** 以本服務自己當遠端節點；ip 不同才不會被判定重複 */
async function addNode(api, ip, descr, parentId) {
  await api.get('/adminPage/login/getRemoteCode');
  const body = await (await api.post('/adminPage/remote/addOver', {
    form: {
      protocol: 'http', ip, port: PORT, descr, parentId,
      name: TEST_ADMIN, pass: TEST_PASS, code: TEST_CAPTCHA, auth: '', monitor: '0',
    },
  })).json();
  expect(body.success, JSON.stringify(body)).toBe(true);
}

test.describe('遠端節點群組隔離（E-66）', () => {
  let admin;
  let limitedApi;
  let ids = {};

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage({ baseURL: BASE_URL });
    await login(page);
    admin = page.request;

    for (const name of [GROUP_A, GROUP_B]) {
      expect((await (await admin.post('/adminPage/remote/addGroupOver', { form: { name } })).json()).success).toBe(true);
    }
    let rows = await allTable(admin);
    ids.groupA = rows.find((r) => r.type === 1 && r.descr === GROUP_A).id;
    ids.groupB = rows.find((r) => r.type === 1 && r.descr === GROUP_B).id;

    await addNode(admin, '127.0.0.1', NODE_A, ids.groupA);
    await addNode(admin, 'localhost', NODE_B, ids.groupB);
    rows = await allTable(admin);
    ids.nodeA = rows.find((r) => r.descr === NODE_A).id;
    ids.nodeB = rows.find((r) => r.descr === NODE_B).id;

    // 只屬於 A 群組的受限帳號
    expect((await (await admin.post('/adminPage/admin/addOver', {
      form: { name: LIMITED, api: 'false', type: '1', parentId: ids.groupA },
    })).json()).success).toBe(true);
    await page.goto('/adminPage/admin');
    ids.limited = await page.locator('tr', { hasText: LIMITED }).locator('input[name="ids"]').getAttribute('value');
    expect((await (await admin.post('/adminPage/admin/changePassOver', {
      form: { id: ids.limited, pass: LIMITED_PASS, auth: 'false' },
    })).json()).success).toBe(true);

    limitedApi = await request.newContext({ baseURL: BASE_URL });
    await limitedApi.get('/adminPage/login/getCode');
    const loginBody = await (await limitedApi.post('/adminPage/login/login', {
      form: { name: b64(LIMITED), pass: b64(LIMITED_PASS), code: b64(TEST_CAPTCHA), authCode: b64('') },
    })).json();
    expect(loginBody.success, JSON.stringify(loginBody)).toBe(true);
  });

  test.afterAll(async () => {
    if (limitedApi) {
      await limitedApi.dispose();
    }
    if (!admin) {
      return;
    }
    for (const id of [ids.nodeA, ids.nodeB]) {
      if (id) await admin.post('/adminPage/remote/del', { form: { id } });
    }
    for (const id of [ids.groupA, ids.groupB]) {
      if (id) await admin.post('/adminPage/remote/delGroup', { form: { id } });
    }
    if (ids.limited) {
      await admin.post('/adminPage/admin/del', { form: { id: ids.limited } });
    }
  });

  test('受限帳號的清單只有自己群組的節點，且不含憑證', async () => {
    const rowIds = (await allTable(limitedApi)).map((r) => r.id);
    expect(rowIds).toContain(ids.nodeA);
    expect(rowIds).toContain(ids.groupA);
    expect(rowIds).not.toContain(ids.nodeB);
    expect(rowIds).not.toContain(ids.groupB);
  });

  test('受限帳號讀取自己群組的節點：不含憑證', async () => {
    const res = await limitedApi.get('/adminPage/remote/detail', { params: { id: ids.nodeA } });
    const text = await res.text();
    expectNoCredentials(text);
    const body = JSON.parse(text);
    expect(body.success).toBe(true);
    expect(body.obj.id).toBe(ids.nodeA);
  });

  test('受限帳號以其他群組的節點 id 呼叫 detail、change 被拒，session 未切換', async () => {
    const detail = await (await limitedApi.get('/adminPage/remote/detail', { params: { id: ids.nodeB } })).json();
    expect(detail.success).toBe(false);
    expect(detail.obj ?? null).toBeNull();

    const change = await (await limitedApi.post('/adminPage/remote/change', { form: { id: ids.nodeB } })).json();
    expect(change.success).toBe(false);

    const local = (await allTable(limitedApi)).find((r) => r.id === 'local');
    expect(local.select).toBe(true);
  });

  test('受限帳號只能讀自己的群組，不能新增、修改、刪除群組（E-183）', async () => {
    const own = await (await limitedApi.get('/adminPage/remote/groupDetail', { params: { id: ids.groupA } })).json();
    expect(own.success).toBe(true);
    expect(own.obj.id).toBe(ids.groupA);

    const other = await (await limitedApi.get('/adminPage/remote/groupDetail', { params: { id: ids.groupB } })).json();
    expect(other.success).toBe(false);
    expect(other.obj ?? null).toBeNull();

    const rename = await (await limitedApi.post('/adminPage/remote/addGroupOver', {
      form: { id: ids.groupB, name: `${GROUP_B}-hijacked` },
    })).json();
    expect(rename.success).toBe(false);
    const create = await (await limitedApi.post('/adminPage/remote/addGroupOver', {
      form: { name: `${GROUP_A}-new` },
    })).json();
    expect(create.success).toBe(false);
    const del = await (await limitedApi.post('/adminPage/remote/delGroup', { form: { id: ids.groupB } })).json();
    expect(del.success).toBe(false);

    const rows = await allTable(admin);
    expect(rows.find((r) => r.id === ids.groupB).descr).toBe(GROUP_B);
    expect(rows.find((r) => r.id === ids.nodeB).parentId).toBe(ids.groupB);
    expect(rows.some((r) => r.descr === `${GROUP_A}-new`)).toBe(false);
  });

  test('超級管理員仍看得到全部節點，回應不含憑證', async () => {
    const rowIds = (await allTable(admin)).map((r) => r.id);
    expect(rowIds).toEqual(expect.arrayContaining([ids.nodeA, ids.nodeB, ids.groupA, ids.groupB]));

    const res = await admin.get('/adminPage/remote/detail', { params: { id: ids.nodeB } });
    const text = await res.text();
    expectNoCredentials(text);
    expect(JSON.parse(text).success).toBe(true);
  });

  test('編輯節點時密碼欄位留空，需重新輸入', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/remote');
    await page.locator(`button[onclick="edit('${ids.nodeA}')"]`).click();
    await expect(page.locator('#ip')).toHaveValue('127.0.0.1');
    await expect(page.locator('#pass')).toHaveValue('');
  });
});
