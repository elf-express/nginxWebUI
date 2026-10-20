const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// E-376 Task 2：白名單 geo 與國家 map 拆成 include 檔，nginx.conf 只留 include。
test.describe.serial('白名單 geo 與國家 map include 檔', () => {
  const LIST_NAME = 'E2E-allowlist-38';
  const ALLOW_IP = '198.51.100.23';
  let context;
  let page;
  let baseURL;
  let originalRule = null;
  let createdRuleId = null;

  test.beforeAll(async ({ browser }, testInfo) => {
    baseURL = testInfo.project.use.baseURL || require('./helpers').BASE_URL;
    context = await browser.newContext();
    page = await context.newPage();
    await login(page);
  });

  test.afterAll(async () => {
    try {
      // 刪除本測試新增的白名單
      const html = await (await page.request.get(baseURL + '/adminPage/protectionCert')).text();
      const at = html.indexOf(LIST_NAME);
      const row = at >= 0 ? /denyAllowNS\.del\('([^']+)'\)/.exec(html.slice(at)) : null;
      if (row) {
        await page.request.post(baseURL + '/adminPage/denyAllow/del', { form: { id: row[1] } });
      }
      // 還原全域國家規則
      if (originalRule) {
        await page.request.post(baseURL + '/adminPage/geo/addOver', {
          form: {
            id: originalRule.id,
            mode: String(originalRule.mode),
            countries: originalRule.countries || '',
            enable: String(originalRule.enable !== false),
          },
        });
      } else if (createdRuleId) {
        await page.request.post(baseURL + '/adminPage/geo/del', { form: { id: createdRuleId } });
      }
    } finally {
      await context.close();
    }
  });

  test('白名單與國家 map 都在 include 檔，nginx.conf 只含 include', async () => {
    const add = await (await page.request.post(baseURL + '/adminPage/denyAllow/addOver', {
      form: { id: '', name: LIST_NAME, type: 'allow', ip: ALLOW_IP },
    })).json();
    expect(add.success).toBe(true);

    const before = await (await page.request.get(baseURL + '/adminPage/geo/detail')).json();
    expect(before.success).toBe(true);
    originalRule = before.obj;

    // 全域規則改為封鎖模式 + CN；種子規則不存在時新建一筆
    const form = { mode: '1', countries: 'CN', enable: 'true' };
    if (originalRule) {
      form.id = originalRule.id;
    }
    const save = await (await page.request.post(baseURL + '/adminPage/geo/addOver', { form })).json();
    expect(save.success).toBe(true);
    if (!originalRule) {
      const after = await (await page.request.get(baseURL + '/adminPage/geo/detail')).json();
      createdRuleId = after.obj.id;
    }

    const loaded = await (await page.request.get(baseURL + '/adminPage/conf/loadConf')).json();
    expect(loaded.success).toBe(true);
    const conf = loaded.obj.conf;
    expect(conf).toContain('include conf.d/allowlist_http.conf;');
    expect(conf).toContain('include conf.d/geo_http.conf;');
    expect(conf).not.toContain(ALLOW_IP);
    expect(conf).not.toContain('CN 1');
    expect(conf).not.toContain('crowdsec_http.conf');

    const files = Object.fromEntries(loaded.obj.fileList.map((f) => [f.name, f.conf]));
    expect(files['allowlist_http.conf']).toContain('geo $nwu_allowlist {');
    expect(files['allowlist_http.conf']).toContain(ALLOW_IP + ' 1;');
    expect(files['geo_http.conf']).toContain('map "$nwu_allowlist:$geo_block_global" $geo_deny_global');
    expect(files['geo_http.conf']).toContain('CN 1;');
  });
});
