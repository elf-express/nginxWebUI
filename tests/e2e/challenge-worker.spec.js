/**
 * Browser execution, keyboard gating, script failure and CSP need a real browser.
 * All Turnstile requests are intercepted; fake Siteverify consumes captured render options.
 */
const { test, expect } = require('@playwright/test');
const { startServer } = require('../../cloudflare/challenge-worker/test/browser-server.cjs');
let server;
test.beforeAll(async () => { server = await startServer(); });
test.afterAll(async () => { await server?.close(); });
async function widget(page, { mutate = '', mode = 'success' } = {}) {
  const observations = [];
  await page.exposeFunction('captureOptions', value => { observations.push(value); });
  await page.route('https://origin.example/**', route => route.fulfill({ body: '<h1>Returned</h1>', contentType: 'text/html' }));
  await page.route('https://challenges.cloudflare.com/**', async route => {
    if (mode === 'blocked') return route.abort();
    await route.fulfill({ contentType: 'application/javascript', body:
      `window.__render = 0; window.__execute = 0; let options;
       window.turnstile = {
         ready: fn => fn(),
         render: (_element, opts) => { window.__render++; options = opts; return 'test-id'; },
         execute: async () => {
           window.__execute++;
           const observed = { action: options.action, cData: options.cData };
           ${mutate}
           await window.captureOptions(observed);
           if ('${mode}' === 'error') return options['error-callback']('test-error');
           if ('${mode}' === 'expired') return options['expired-callback']();
           options.callback(btoa(JSON.stringify(observed)).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,''));
         }
       };
       new URL(document.currentScript.src).searchParams.get('onload') &&
         window[new URL(document.currentScript.src).searchParams.get('onload')]();` });
  });
  return observations;
}
for (const style of ['auto', 'checkbox']) {
  test('turnstile_options_match_ticket ' + style, async ({ page }) => {
    const observed = await widget(page);
    const completed = page.waitForResponse(r => new URL(r.url()).pathname === '/verify');
    await page.goto(server.url + '/?style=' + style + '&t=' + server.ticket());
    if (style === 'checkbox') {
      await expect(page.getByRole('checkbox')).toBeVisible();
      expect(await page.evaluate(() => [window.__render, window.__execute])).toEqual([0, 0]);
      expect(observed).toHaveLength(0);
      await page.getByRole('checkbox').focus();
      await page.keyboard.press('Space');
    }
    const response = await completed;
    expect(response.status()).toBe(303);
    expect(response.headers()['location']).toMatch(/^https:\/\/origin\.example\/\.well-known\/nwu-challenge\/callback\?token=/);
    expect(observed).toEqual([{ action: 'nwu_challenge', cData: server.nonce }]);
    // Stop the cross-origin redirect; actual callback and cookie are exercised against Lua in Task 4.
    await page.goto('about:blank');
  });
}
for (const mutate of ["delete observed.action;", "observed.action = 'login';", "delete observed.cData;", "observed.cData = 'bad';"]) {
  test('rejects captured render options: ' + mutate, async ({ page }) => {
    await widget(page, { mutate });
    const completed = page.waitForResponse(r => new URL(r.url()).pathname === '/verify');
    await page.goto(server.url + '/?t=' + server.ticket());
    expect((await completed).status()).toBe(429);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Try again' })).toBeVisible();
    expect(page.url()).not.toContain('origin.example');
  });
}
for (const mode of ['error', 'expired', 'blocked']) {
  test('offers retry without success on widget ' + mode, async ({ page }) => {
    await widget(page, { mode });
    await page.goto(server.url + '/?t=' + server.ticket());
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 15000 });
    const button = page.getByRole('button', { name: 'Try again' });
    await expect(button).toBeEnabled();
    await button.click();
    expect(page.url()).not.toContain('origin.example');
  });
}
for (const [language, expected, heading] of [
  ['zh-CN', 'zh-CN', '正在确认您不是机器人'], ['zh-TW', 'zh-TW', '正在確認您不是機器人'],
  ['en-US', 'en', 'Checking that you are not a robot'], ['fr-FR', 'en', 'Checking that you are not a robot'],
]) {
  test('uses language ' + language + ' and signed IP/time', async ({ browser }) => {
    const context = await browser.newContext({ locale: language });
    const page = await context.newPage();
    try {
    await widget(page);

    await page.goto(server.url + '/?style=checkbox&t=' + server.ticket({ path: '/<script>window.pwned=1</script>' }));
    await expect(page.locator('html')).toHaveAttribute('lang', expected);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(heading);
    await expect(page.getByRole('checkbox')).toHaveAccessibleName(/.+/);
    await expect(page.locator('footer')).toContainText('203.0.113.7');
    await expect(page.locator('footer')).toContainText('2030-01-01T00:00:00.000Z');
    expect(await page.evaluate(() => window.pwned)).toBeUndefined();
    } finally { await context.close(); }
  });
}
test('preview never renders or submits and has no secret', async ({ page }) => {
  const observed = await widget(page);
  const before = server.calls();
  await page.goto(server.url + '/?style=checkbox');
  await expect(page.getByText('Preview only: no verification will be submitted.')).toBeVisible();
  await expect(page.getByRole('checkbox')).toBeDisabled();
  expect(observed).toHaveLength(0);
  expect(server.calls()).toBe(before);
  expect(await page.content()).not.toContain('local-browser-test-ticket-key');
});
test('noscript is readable with JavaScript disabled', async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false, locale: 'en-US' });
  try {
    const page = await context.newPage();
    await page.goto(server.url + '/?t=' + server.ticket());
    await expect(page.locator('noscript')).toBeVisible();
    expect(await page.locator('noscript').textContent()).toContain('Please enable JavaScript');
  } finally { await context.close(); }
});

test('review: expired ticket retry returns to original query for a fresh challenge', async ({ page }) => {
  const expiredServer = await startServer();
  try {
    await widget(page);
    const completed = page.waitForResponse(r => new URL(r.url()).pathname === '/verify');
    await page.goto(expiredServer.url + '/?style=checkbox&t=' + expiredServer.ticket({ path: '/cart?q=one%20two%26three' }));
    expiredServer.advance(301);
    await page.getByRole('checkbox').check();
    expect((await completed).status()).toBe(429);
    expect(expiredServer.calls()).toBe(0);
    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByRole('heading', { name: 'Returned' })).toBeVisible();
    expect(page.url()).toBe('https://origin.example/cart?q=one%20two%26three');
  } finally { await expiredServer.close(); }
});
