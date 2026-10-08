const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// basic 頁 Vue 版（E-206 Phase 0）：預設新頁、功能與舊頁等價、legacy 逃生口、
// bundle／字典失敗回落、資料失敗可重試、session 過期導回登入。
const H1 = /基本參數配置|基本参数配置/;
const ADD = /添加基本參數配置|添加基本参数配置/;
// antd 會在兩個中文字的按鈕文字間插入空白（「提 交」）
const SUBMIT = /提\s*交/;

function row(page, name) {
  return page.locator('#app .basic-table .ant-table-row', {
    has: page.locator('td', { hasText: new RegExp(`^${name}$`) }),
  });
}

async function names(page) {
  return page.locator('#app .basic-table .ant-table-row td:nth-child(2)').allTextContents();
}

async function openBasic(page) {
  await page.goto('/adminPage/basic');
  await expect(page.locator('#app h1')).toHaveText(H1);
  await expect(row(page, 'worker_processes')).toBeVisible();
}

async function addParam(page, name, value) {
  await page.getByRole('button', { name: ADD }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('#basic-form-name').fill(name);
  await dialog.locator('#basic-form-value').fill(value);
  await dialog.getByRole('button', { name: SUBMIT }).click();
  await expect(row(page, name)).toBeVisible();
}

async function deleteParam(page, name) {
  await row(page, name).getByRole('button', { name: /刪除|删除/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: SUBMIT }).click();
  await expect(row(page, name)).toHaveCount(0);
}

test.describe('basic 頁 Vue 版', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('預設載入 Vue 版', async ({ page }) => {
    await openBasic(page);
    await expect(page.locator('script[src*="/js/spa/basic.js"]')).toHaveCount(1);
    await expect(page.locator('#app .layui-table')).toHaveCount(0);
    await expect(row(page, 'events')).toBeVisible();
  });

  test('新增 → 編輯 → 刪除', async ({ page }) => {
    await openBasic(page);
    await addParam(page, 'e2e_spa_crud', 'on');

    await row(page, 'e2e_spa_crud').getByRole('button', { name: /編輯|编辑/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.locator('#basic-form-value')).toHaveValue('on');
    await dialog.locator('#basic-form-value').fill('off');
    await dialog.getByRole('button', { name: SUBMIT }).click();
    await expect(row(page, 'e2e_spa_crud')).toContainText('off');

    await deleteParam(page, 'e2e_spa_crud');
  });

  test('空名稱不送出', async ({ page }) => {
    await openBasic(page);
    await page.getByRole('button', { name: ADD }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: SUBMIT }).click();
    await expect(page.locator('.ant-message')).toContainText(/名稱為空|名称为空/);
    await expect(dialog).toBeVisible();
  });

  test('上移改變順序', async ({ page }) => {
    await openBasic(page);
    await addParam(page, 'e2e_spa_order', '1');
    const before = (await names(page)).indexOf('e2e_spa_order');
    expect(before).toBeGreaterThan(0);

    await row(page, 'e2e_spa_order').getByRole('button', { name: /上移/ }).click();
    await expect.poll(async () => (await names(page)).indexOf('e2e_spa_order')).toBe(before - 1);

    await deleteParam(page, 'e2e_spa_order');
  });

  test('批次刪除與未選擇提示', async ({ page }) => {
    await openBasic(page);
    await page.getByRole('button', { name: /批量刪除|批量删除/ }).click();
    await expect(page.locator('.ant-message')).toContainText(/未選擇|未选择/);

    await addParam(page, 'e2e_spa_bulk_a', '1');
    await addParam(page, 'e2e_spa_bulk_b', '2');
    await row(page, 'e2e_spa_bulk_a').locator('input[type="checkbox"]').check();
    await row(page, 'e2e_spa_bulk_b').locator('input[type="checkbox"]').check();
    await page.getByRole('button', { name: /批量刪除|批量删除/ }).click();
    await page.getByRole('dialog').getByRole('button', { name: SUBMIT }).click();

    await expect(row(page, 'e2e_spa_bulk_a')).toHaveCount(0);
    await expect(row(page, 'e2e_spa_bulk_b')).toHaveCount(0);
  });

  test('?legacy=1 回舊版頁', async ({ page }) => {
    await page.goto('/adminPage/basic?legacy=1');
    await expect(page.locator('table.layui-table').first()).toBeVisible();
    await expect(page.locator('#windowDiv')).toHaveCount(1);
    await expect(page.locator('#app')).toHaveCount(0);
  });

  test('bundle 載入失敗自動回舊版', async ({ page }) => {
    await page.route('**/js/spa/basic.js*', (route) => route.fulfill({ status: 404, body: '' }));
    await page.goto('/adminPage/basic');
    await page.waitForURL(/legacy=1/);
    await expect(page.locator('table.layui-table').first()).toBeVisible();
  });

  test('bundle 執行時丟錯自動回舊版', async ({ page }) => {
    await page.route('**/js/spa/basic.js*', (route) =>
      route.fulfill({ status: 200, contentType: 'application/javascript', body: 'throw new Error("boom")' }));
    await page.goto('/adminPage/basic');
    await page.waitForURL(/legacy=1/);
    await expect(page.locator('table.layui-table').first()).toBeVisible();
  });

  test('i18n 字典取不到時回舊版', async ({ page }) => {
    await page.route('**/adminPage/i18n*', (route) => route.fulfill({ status: 500, body: '' }));
    await page.goto('/adminPage/basic');
    await page.waitForURL(/legacy=1/);
    await expect(page.locator('table.layui-table').first()).toBeVisible();
  });

  test('頁面資料失敗時顯示錯誤，重新載入後恢復', async ({ page }) => {
    let fail = true;
    await page.route('**/adminPage/basic/pageData*', (route) =>
      fail ? route.fulfill({ status: 500, body: '' }) : route.continue());
    await page.goto('/adminPage/basic');
    await expect(page.locator('#app .ant-alert-error')).toBeVisible();

    fail = false;
    await page.getByRole('button', { name: /重新載入|重新加载/ }).click();
    await expect(row(page, 'worker_processes')).toBeVisible();
  });

  test('session 過期時操作會導回登入頁', async ({ page, context }) => {
    await openBasic(page);
    await context.clearCookies();
    await row(page, 'worker_processes').getByRole('button', { name: /編輯|编辑/ }).click();
    await page.waitForURL(/adminPage\/login/, { waitUntil: 'commit' });
  });

  test('模組開關：啟用連帶啟用依賴，停用連帶停用相依', async ({ page }) => {
    const GEO = 'ngx_stream_geoip2_module.so';
    const STREAM = 'ngx_stream_module.so';
    // CI 與 Windows 上磁碟沒有模組，改寫 pageData 讓開關出現；setModuleEnable 照常送到真後端
    const idOf = {};
    await page.route('**/adminPage/basic/pageData*', async (route) => {
      const res = await route.fetch();
      const json = await res.json();
      for (const m of json.obj.moduleList) {
        idOf[m.name] = String(m.id);
      }
      json.obj.isLinux = true;
      json.obj.modulesOnDisk = [STREAM, GEO];
      await route.fulfill({ response: res, json });
    });
    const calls = [];
    page.on('request', (req) => {
      if (req.url().includes('/adminPage/basic/setModuleEnable')) {
        calls.push(req.postData());
      }
    });

    await page.goto('/adminPage/basic');
    await expect(page.locator('#app h1')).toHaveText(H1);
    const geo = page.getByRole('switch', { name: GEO });
    const stream = page.getByRole('switch', { name: STREAM });
    await expect(geo).not.toBeChecked();
    await expect(stream).not.toBeChecked();

    try {
      await geo.click();
      await expect(stream).toBeChecked();
      await expect(geo).toBeChecked();
      await expect(page.locator('.ant-message')).toContainText(/已自動啟用依賴模組|已自动启用依赖模组/);
      expect(calls.map((c) => new URLSearchParams(c).get('enable'))).toEqual(['1', '1']);
      expect(calls.map((c) => new URLSearchParams(c).get('id'))).toEqual([idOf[GEO], idOf[STREAM]]);

      calls.length = 0;
      await stream.click();
      await expect(stream).not.toBeChecked();
      await expect(geo).not.toBeChecked();
      await expect(page.locator('.ant-message')).toContainText(/已自動停用相依模組|已自动停用相依模组/);
      expect(calls.map((c) => new URLSearchParams(c).get('enable'))).toEqual(['0', '0']);
      expect(calls.map((c) => new URLSearchParams(c).get('id'))).toEqual([idOf[STREAM], idOf[GEO]]);
    } finally {
      // 失敗時把兩個模組都關回去
      for (const sw of [geo, stream]) {
        if (await sw.isChecked()) {
          await sw.click();
          await expect(sw).not.toBeChecked();
        }
      }
    }
  });

  test('英文語系顯示英文字串', async ({ page }) => {
    try {
      const res = await page.request.post('/adminPage/login/changeLang', { form: { lang: 'en_US' } });
      expect((await res.json()).success).toBe(true);
      await page.goto('/adminPage/basic');
      await expect(page.locator('#app h1')).toHaveText('Basic configuration');
      await expect(page.getByRole('button', { name: /Add basic parameter/ })).toBeVisible();
    } finally {
      await page.request.post('/adminPage/login/changeLang', { form: { lang: 'zh' } });
    }
  });
});
