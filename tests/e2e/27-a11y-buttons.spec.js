const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

const LISTING_PAGES = [
  '/adminPage/server',
  '/adminPage/upstream',
  '/adminPage/http',
  '/adminPage/stream',
  '/adminPage/cert',
  '/adminPage/denyAllow',
  '/adminPage/protectionCert',
  '/adminPage/admin',
  '/adminPage/remote',
  '/adminPage/basic',
];

// Vue 版頁面要等內容掛載完才檢查，新遷移的頁面加進這裡
const SPA_PAGES = ['/adminPage/basic'];

async function gotoSettled(page, path) {
  await page.goto(path);
  await page.waitForLoadState('domcontentloaded');
  if (SPA_PAGES.includes(path)) {
    await expect(page.locator('#app h1')).toBeVisible();
  }
}

test.beforeEach(async ({ page }) => {
  await login(page);
});

for (const path of LISTING_PAGES) {
  test(`${path}: no <a href="javascript:"> outside layui nav-tree`, async ({ page }) => {
    await gotoSettled(page, path);
    const bad = await page.evaluate(() => {
      const all = Array.from(document.querySelectorAll('a[href^="javascript:"]'));
      return all.filter(a => !a.closest('.layui-nav-tree')).length;
    });
    expect(bad, `${path} has <a href="javascript:"> outside layui nav-tree`).toBe(0);
  });

  test(`${path}: every visible <button> has accessible name`, async ({ page }) => {
    await gotoSettled(page, path);
    const buttons = await page.locator('button:visible').all();
    const missing = [];
    for (const b of buttons) {
      const ariaLabel = (await b.getAttribute('aria-label')) || '';
      const text = (await b.innerText()) || '';
      if (!ariaLabel.trim() && !text.trim()) {
        missing.push(await b.evaluate(el => el.outerHTML.slice(0, 200)));
      }
    }
    expect(missing, `buttons missing accessible name on ${path}: ${JSON.stringify(missing)}`).toEqual([]);
  });

  test(`${path}: no 'javacript:' typo`, async ({ page }) => {
    await gotoSettled(page, path);
    const html = await page.content();
    expect(html, `${path} contains 'javacript:' typo`).not.toContain('javacript:');
  });
}
