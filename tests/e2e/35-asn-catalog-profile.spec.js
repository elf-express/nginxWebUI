const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

/**
 * ASN catalog + protection profile (protectionCert ASN tab).
 * No full GitHub AsMeta sync; no live CrowdSec required.
 *
 * Stable selectors (Task 7 DOM):
 *   #asnProfileLight | #asnProfileManual | #asnProfileStrict
 *   name=protectionProfile
 *   #asnCatalogQ | #asnCatalogBody | #btnAsnSyncMeta
 *   #asnIntentBody | #btnAsnAddIntent
 *   #btnAsnSuggestHosting (optional / strict-only)
 */
test.describe('ASN catalog + profile', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');
    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
    // Wait for ASN tab markup + profile radios (init may already have run on DOM ready)
    await page.waitForSelector('#asnProfileLight', { state: 'attached' });
    await page.waitForSelector('#asnTabContent', { state: 'visible' });
    // loadProfile is async; wait until light radio is applied or add-intent gated
    await page.waitForFunction(() => {
      const light = document.querySelector('#asnProfileLight');
      const addBtn = document.querySelector('#btnAsnAddIntent');
      if (!light || !addBtn) return false;
      // default / light: radio checked OR add button already disabled by applyUiGates
      return light.checked || addBtn.disabled || addBtn.classList.contains('layui-btn-disabled');
    }, { timeout: 10000 });
  });

  test('profile control present default light', async ({ page }) => {
    await expect(page.locator('#asnProfileLight')).toBeAttached();
    await expect(page.locator('#asnProfileManual')).toBeAttached();
    await expect(page.locator('#asnProfileStrict')).toBeAttached();

    const radios = page.locator('input[name="protectionProfile"]');
    await expect(radios).toHaveCount(3);

    // Default profile is light — checked on input and/or visible label text
    await expect(page.locator('#asnProfileLight')).toBeChecked();

    // Multilingual profile label (Layui radio title)
    await expect(page.locator('#asnTabContent')).toContainText(/Light|輕量|轻量/i);

    // Catalog / intent shell present
    await expect(page.locator('#asnCatalogQ')).toBeVisible();
    await expect(page.locator('#asnCatalogBody')).toBeVisible();
    await expect(page.locator('#btnAsnSyncMeta')).toBeVisible();
    await expect(page.locator('#asnIntentBody')).toBeVisible();
    await expect(page.locator('#btnAsnAddIntent')).toBeVisible();
  });

  test('add intent disabled or blocked in light', async ({ page }) => {
    // Ensure still on light
    await expect(page.locator('#asnProfileLight')).toBeChecked();

    const addBtn = page.locator('#btnAsnAddIntent');
    await expect(addBtn).toBeVisible();

    // UI gate: disabled attribute and/or layui-btn-disabled class
    const disabled = await addBtn.isDisabled();
    const hasDisabledClass = await addBtn.evaluate((el) =>
      el.classList.contains('layui-btn-disabled') || el.hasAttribute('disabled')
    );
    expect(disabled || hasDisabledClass).toBeTruthy();

    // Suggest hosting is strict-only — hidden in light
    await expect(page.locator('#btnAsnSuggestHosting')).toBeHidden();

    // API hard-gate: light profile rejects addIntent (no CS required)
    const apiRes = await page.evaluate(async () => {
      const r = await fetch(ctx + '/adminPage/asn/addIntent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'asn=15169&duration=24h&note=e2e-light-block',
      });
      return r.json();
    });
    expect(apiRes.success).toBeFalsy();
  });

  test('catalog search API reachable', async ({ page }) => {
    const res = await page.evaluate(async () => {
      const r = await fetch(ctx + '/adminPage/asn/catalog?curr=1&limit=10');
      return r.json();
    });
    expect(res.success).toBeTruthy();
    // Empty catalog is OK — success means route + service respond
    expect(res.obj != null).toBeTruthy();
  });

  test('profile API returns light by default', async ({ page }) => {
    const res = await page.evaluate(async () => {
      const r = await fetch(ctx + '/adminPage/asn/profile');
      return r.json();
    });
    expect(res.success).toBeTruthy();
    expect(res.obj).toBeTruthy();
    expect(String(res.obj.profile || '').toLowerCase()).toBe('light');
    // crowdsecConfigured may be false in E2E — just ensure field exists
    expect('crowdsecConfigured' in res.obj).toBeTruthy();
  });

});
