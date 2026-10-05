const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// common.html 把 messages 逐條輸出成 JS 字串常值；值裡的引號沒跳脫時整段 script 會語法錯誤，
// 之後所有 *Str 全域變數都不存在，登入頁的 layer 也打不開。英文語系有含引號的字串，所以用 en_US 驗。
test.describe('i18n 字串輸出成 JS 時正確跳脫', () => {

  async function setLang(page, lang) {
    const res = await page.request.post('/adminPage/login/changeLang', { form: { lang } });
    expect(res.ok()).toBeTruthy();
  }

  test('en_US 下頁面沒有 JS 錯誤，*Str 全域變數都在', async ({ page }) => {
    await login(page);
    await setLang(page, 'en_US');

    try {
      const errors = [];
      page.on('pageerror', (err) => errors.push(err.message));

      for (const path of ['/adminPage/login', '/adminPage/monitor']) {
        await page.goto(path);
        await page.waitForLoadState('domcontentloaded');

        const globals = await page.evaluate(() => ({
          common: typeof window.commonStr,
          conf: typeof window.confStr,
          ssl: window.confStr && window.confStr.diagSslDeprecated,
        }));
        expect(globals.common, path).toBe('object');
        expect(globals.conf, path).toBe('object');
        expect(globals.ssl, path).toMatch(/ssl/);
      }

      expect(errors).toEqual([]);
    } finally {
      await setLang(page, 'zh');
    }
  });

});
