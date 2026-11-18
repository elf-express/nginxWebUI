/**
 * E-161：後台變更端點只收 POST，且要帶與 session 綁定的 CSRF token；
 * session cookie 與 token cookie 都是 SameSite=Strict。
 */
const { test, expect } = require('@playwright/test');
const { BASE_URL, login, csrfHeaders } = require('./helpers');

const ATTACKER = 'http://attacker.test';

async function addBasic(page, name) {
  const res = await page.request.post('/adminPage/basic/addOver', {
    form: { name, value: '1' },
    headers: await csrfHeaders(page),
  });
  expect((await res.json()).success).toBe(true);
  return basicId(page, name);
}

async function basicId(page, name) {
  const res = await page.request.get('/adminPage/basic/pageData');
  const { obj } = await res.json();
  const found = obj.basicList.find((b) => b.name === name);
  return found ? found.id : null;
}

/** 在 attacker.test 這個不同站台放一頁攻擊者的 HTML，打開它 */
async function openAttackerPage(page, html) {
  await page.route(`${ATTACKER}/**`, (route) =>
    route.fulfill({ status: 200, contentType: 'text/html', body: `<!DOCTYPE html><html><body>${html}</body></html>` }),
  );
  await page.goto(`${ATTACKER}/evil.html`);
}

test.describe('後台變更端點的 CSRF 防護（E-161）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('變更端點收 GET 一律回 405', async ({ page }) => {
    const delAll = await page.request.get('/adminPage/bak/delAll', { maxRedirects: 0 });
    expect(delAll.status()).toBe(405);

    const change = await page.request.get('/adminPage/admin/changePassOver?id=1&pass=x&auth=false', { maxRedirects: 0 });
    expect(change.status()).toBe(405);

    const id = await addBasic(page, 'csrf_get_probe');
    const del = await page.request.get(`/adminPage/basic/del?id=${id}`, { maxRedirects: 0 });
    expect(del.status()).toBe(405);
    expect(await basicId(page, 'csrf_get_probe')).toBe(id);
  });

  test('缺 token 或 token 錯誤的 POST 回 403 且不改資料', async ({ page }) => {
    const id = await addBasic(page, 'csrf_token_probe');

    const missing = await page.request.post('/adminPage/basic/del', { form: { id }, maxRedirects: 0 });
    expect(missing.status()).toBe(403);

    const wrong = await page.request.post('/adminPage/basic/del', {
      form: { id },
      headers: { 'X-CSRF-Token': 'not-the-token' },
      maxRedirects: 0,
    });
    expect(wrong.status()).toBe(403);

    const wrongField = await page.request.post('/adminPage/basic/del', { form: { id, _csrf: 'not-the-token' }, maxRedirects: 0 });
    expect(wrongField.status()).toBe(403);

    expect(await basicId(page, 'csrf_token_probe')).toBe(id);
  });

  test('session cookie 與 token cookie 都是 SameSite=Strict', async ({ page }) => {
    const cookies = await page.context().cookies(BASE_URL);
    const session = cookies.find((c) => c.name === 'SOLONID');
    const token = cookies.find((c) => c.name === 'NWUI_CSRF');
    expect(session, 'SOLONID').toBeTruthy();
    expect(session.sameSite).toBe('Strict');
    expect(session.httpOnly).toBe(true);
    expect(token, 'NWUI_CSRF').toBeTruthy();
    expect(token.sameSite).toBe('Strict');
    expect(token.httpOnly).toBe(false);
  });

  test('跨站表單送出改不到資料', async ({ page }) => {
    const id = await addBasic(page, 'csrf_form_probe');
    await openAttackerPage(page, `
      <form id="f" method="post" action="${BASE_URL}/adminPage/basic/del">
        <input name="id" value="${id}">
      </form>
      <script>document.getElementById('f').submit()</script>`);
    await page.waitForURL(`${BASE_URL}/**`);
    await page.waitForLoadState('load');

    // 跨站請求不帶 session cookie，伺服器會發新的 session，所以重新登入再查
    await login(page);
    expect(await basicId(page, 'csrf_form_probe')).toBe(id);
  });

  test('跨站頂層導覽改不到資料', async ({ page }) => {
    const id = await addBasic(page, 'csrf_nav_probe');
    await openAttackerPage(page, `<script>location.href = '${BASE_URL}/adminPage/basic/del?id=${id}'</script>`);
    await page.waitForURL(`${BASE_URL}/**`);
    await page.waitForLoadState('load');

    await login(page);
    expect(await basicId(page, 'csrf_nav_probe')).toBe(id);
  });

  test('同源舊版頁面的 AJAX、表單與 Vue 頁照常可用', async ({ page }) => {
    // 舊版頁：jQuery AJAX 由 base.js 自動帶 token
    const id = await addBasic(page, 'csrf_legacy_ajax');
    await page.goto('/adminPage/bak');
    const result = await page.evaluate(
      (target) =>
        new Promise((resolve) => {
          $.ajax({ type: 'POST', url: ctx + '/adminPage/basic/del', data: { id: target }, dataType: 'json', success: resolve, error: (xhr) => resolve({ status: xhr.status }) });
        }),
      id,
    );
    expect(result.success).toBe(true);
    expect(await basicId(page, 'csrf_legacy_ajax')).toBeNull();

    // 舊版頁：分頁／搜尋表單以 POST 送出，要自動帶上 _csrf 欄位
    await page.goto('/adminPage/server');
    const reloaded = page.waitForResponse((res) => res.url().endsWith('/adminPage/server') && res.request().method() === 'POST');
    const loaded = page.waitForEvent('load');
    await page.evaluate(() => $('#searchForm').submit());
    expect((await reloaded).status()).toBe(200);
    await loaded;

    // 清空備份按鈕改用 POST
    await page.goto('/adminPage/bak');
    page.once('dialog', (dialog) => dialog.accept());
    const cleared = page.waitForResponse((res) => res.url().includes('/adminPage/bak/delAll'));
    await page.evaluate(() => delAll());
    const res = await cleared;
    expect(res.request().method()).toBe('POST');
    expect(res.status()).toBe(200);
  });

  test('Vue 頁的 POST 帶 token 照常可用', async ({ page }) => {
    await addBasic(page, 'csrf_vue_probe');
    await page.goto('/adminPage/basic');
    const row = page.locator('#app .basic-table .ant-table-row', { has: page.locator('td', { hasText: /^csrf_vue_probe$/ }) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /刪除|删除/ }).click();
    await page.getByRole('dialog').getByRole('button', { name: /提\s*交/ }).click();
    await expect(row).toHaveCount(0);
  });
});
