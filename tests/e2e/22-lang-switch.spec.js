const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('語言切換 UI（國旗 icon）', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    // 關掉 monitor 頁面登入後可能 auto-open 的 layer（版本通知等），否則遮罩會擋住 header
    await page.evaluate(() => {
      if (window.layer && typeof window.layer.closeAll === 'function') {
        window.layer.closeAll();
      }
    });
    await page.locator('.layui-layer-shade').first()
      .waitFor({ state: 'hidden', timeout: 3000 })
      .catch(() => {});
  });

  test('header 的 Language 連結顯示目前語系國旗', async ({ page }) => {
    const langLink = page.locator('a[href*="changeLang"]').first();
    await expect(langLink).toBeVisible();

    const flagImg = langLink.locator('img');
    await expect(flagImg).toHaveCount(1);

    const src = await flagImg.getAttribute('src');
    expect(src).toMatch(/\/(cn|tw|gb)\.svg$/);
  });

  test('點 Language 開啟彈窗，三個語系卡片各帶國旗', async ({ page }) => {
    await page.locator('a[href*="changeLang"]').first().click();
    await page.waitForSelector('#changeLangDiv', { state: 'visible' });

    const zhCard = page.locator('.lang-option[data-lang="zh"]');
    const twCard = page.locator('.lang-option[data-lang="zh_TW"]');
    const enCard = page.locator('.lang-option[data-lang="en_US"]');

    await expect(zhCard).toBeVisible();
    await expect(twCard).toBeVisible();
    await expect(enCard).toBeVisible();

    await expect(zhCard.locator('img')).toHaveAttribute('src', /\/cn\.svg$/);
    await expect(twCard.locator('img')).toHaveAttribute('src', /\/tw\.svg$/);
    await expect(enCard.locator('img')).toHaveAttribute('src', /\/gb\.svg$/);
  });

  test('點卡片觸發 selected 樣式（pickLang）', async ({ page }) => {
    await page.locator('a[href*="changeLang"]').first().click();
    await page.waitForSelector('#changeLangDiv', { state: 'visible' });

    await page.locator('.lang-option[data-lang="en_US"]').click();

    await expect(page.locator('.lang-option[data-lang="en_US"]')).toHaveClass(/selected/);
    await expect(page.locator('.lang-option[data-lang="zh"]')).not.toHaveClass(/selected/);
    await expect(page.locator('.lang-option[data-lang="zh_TW"]')).not.toHaveClass(/selected/);
    expect(await page.locator('#lang').inputValue()).toBe('en_US');
  });

  test('切換到繁體中文後 reload，header 顯示 tw.svg', async ({ page }) => {
    await page.locator('a[href*="changeLang"]').first().click();
    await page.waitForSelector('#changeLangDiv', { state: 'visible' });
    await page.locator('.lang-option[data-lang="zh_TW"]').click();
    await expect(page.locator('#lang')).toHaveValue('zh_TW');

    await page.getByRole('button', { name: /^OK$/ }).click();

    // 不用 networkidle（後台有 polling 永遠不 idle），直接 poll header img src
    const flagImg = page.locator('a[href*="changeLang"]').first().locator('img');
    await expect(flagImg).toHaveAttribute('src', /\/tw\.svg$/, { timeout: 15000 });

    // 切回简体中文避免影響其他測試（cleanup）
    await page.evaluate(() => {
      if (window.layer && typeof window.layer.closeAll === 'function') {
        window.layer.closeAll();
      }
    });
    await page.locator('.layui-layer-shade').first()
      .waitFor({ state: 'hidden', timeout: 3000 }).catch(() => {});
    await page.locator('a[href*="changeLang"]').first().click();
    await page.waitForSelector('#changeLangDiv', { state: 'visible' });
    await page.locator('.lang-option[data-lang="zh"]').click();
    await page.getByRole('button', { name: /^OK$/ }).click();
    await expect(flagImg).toHaveAttribute('src', /\/cn\.svg$/, { timeout: 15000 });
  });

});
