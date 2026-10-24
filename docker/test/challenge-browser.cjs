// Real Chromium through the challenge page (E-387), used by crowdsec-challenge-e2e.sh --browser.
// Only requests to the test host are allowed; any other request is aborted and fails the check (offline proof).
// The test site sends Content-Security-Policy "default-src 'self'": the challenge page must carry it and pass without
// any CSP violation in the console.
// With a preview directory (the admin preview page and the files it loads, fetched by the shell script) it also opens that
// preview from a fake https host (ALTCHA needs a secure context): the widget must not end in the error state, nothing may be logged as an ALTCHA error, the
// preview note must be shown, and nothing may be posted to the verify path.
// Usage: node docker/test/challenge-browser.cjs <base url, e.g. https://localhost:18443> <auto|checkbox> [<preview dir>]
const { chromium } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const [base, style, previewDir] = process.argv.slice(2);

const TYPES = { '.js': 'application/javascript', '.css': 'text/css' };

async function checkPreview(browser, dir, style) {
  const context = await browser.newContext({ locale: 'zh-TW' });
  const page = await context.newPage();
  const posted = [];
  const errors = [];
  await page.route('https://preview.test/**', (route) => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'POST') {
      posted.push(url.pathname);
      return route.abort();
    }
    if (url.pathname === '/adminPage/crowdsec/challengePreview') {
      return route.fulfill({ contentType: 'text/html', body: fs.readFileSync(path.join(dir, 'preview.html')) });
    }
    const name = url.searchParams.get('name') || '';
    const file = path.join(dir, path.basename(name));
    if (url.pathname === '/adminPage/crowdsec/challengeAsset' && name && fs.existsSync(file)) {
      return route.fulfill({ contentType: TYPES[path.extname(name)] || 'text/plain', body: fs.readFileSync(file) });
    }
    return route.fulfill({ status: 404, body: '' });
  });
  await page.addInitScript(() => {
    window.__errors = [];
    const original = console.error;
    console.error = (...args) => {
      window.__errors.push(args.map((a) => (a instanceof Error ? a.message : String(a))).join(' '));
      original.apply(console, args);
    };
    window.addEventListener('error', (e) => window.__errors.push(String(e.message)));
  });
  page.on('pageerror', (err) => errors.push(err.message));
  await page.goto(`https://preview.test/adminPage/crowdsec/challengePreview?style=${style}`);
  await page.locator('altcha-widget').waitFor({ state: 'attached' });
  if (style === 'checkbox') {
    await page.locator('altcha-widget input[type=checkbox]').click({ timeout: 10000 });
  }
  // auto verifies on load, the checkbox right after the click: give the proof of work time to finish
  await page.waitForTimeout(8000);
  const state = await page.evaluate(() => ({
    widget: document.querySelector('altcha-widget').getState(),
    errors: window.__errors,
    note: (document.querySelector('.nwu-preview') || {}).textContent || '',
    text: document.body.innerText,
  }));
  await context.close();
  if (state.widget === 'error' || state.errors.length > 0 || errors.length > 0) {
    throw new Error(`preview ${style}: widget ${state.widget}, console errors ${JSON.stringify(state.errors.concat(errors))}`);
  }
  if (!/預覽模式|Preview only/.test(state.note) || /驗證失敗|Verification failed/.test(state.text)) {
    throw new Error(`preview ${style}: note [${state.note}], text [${state.text}]`);
  }
  if (posted.length > 0) {
    throw new Error(`preview ${style} posted ${posted.join(', ')}`);
  }
}

(async () => {
  const host = new URL(base).hostname;
  const browser = await chromium.launch();
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-TW' });
  const page = await context.newPage();
  const outside = [];
  await page.route('**/*', (route) => {
    const url = new URL(route.request().url());
    if (url.hostname === host) {
      return route.continue();
    }
    outside.push(url.href);
    return route.abort();
  });
  const violations = [];
  page.on('console', (msg) => {
    if (/Content Security Policy|Refused to/i.test(msg.text())) {
      violations.push(msg.text());
    }
  });
  let csp = null;
  page.on('response', (res) => {
    if (new URL(res.url()).pathname === '/.well-known/nwu-challenge/' && res.request().method() === 'GET') {
      csp = res.headers()['content-security-policy'] || '';
    }
  });
  await page.goto(`${base}/?from=browser`);
  if (!page.url().includes('/.well-known/nwu-challenge/')) {
    throw new Error(`not sent to the challenge page: ${page.url()}`);
  }
  const shown = await page.locator('body').getAttribute('data-style');
  if (shown !== style) {
    throw new Error(`style ${shown}, want ${style}`);
  }
  if (!csp || !csp.includes("default-src 'self'")) {
    throw new Error(`challenge page without the site CSP: ${csp}`);
  }
  if (style === 'checkbox') {
    await page.locator('altcha-widget input[type=checkbox]').click();
  }
  try {
    await page.waitForURL(`${base}/?from=browser`, { timeout: 30000 });
  } catch (err) {
    throw new Error(`not passed: ${String(err).split('\n')[0]}; CSP violations: ${violations.join(' | ')}`);
  }
  const cookie = (await context.cookies()).find((c) => c.name === 'nwu_pass');
  if (!cookie || !cookie.httpOnly || !cookie.secure) {
    throw new Error(`nwu_pass cookie missing or without HttpOnly/Secure: ${JSON.stringify(cookie)}`);
  }
  await page.goto(`${base}/second`);
  if (page.url().includes('/.well-known/nwu-challenge/')) {
    throw new Error('challenged again within the cookie lifetime');
  }
  if (violations.length > 0) {
    throw new Error(`CSP violations: ${violations.join(' | ')}`);
  }
  if (outside.length > 0) {
    throw new Error(`requests outside ${host}: ${outside.join(', ')}`);
  }
  if (previewDir) {
    await checkPreview(browser, previewDir, style);
  }
  await browser.close();
  console.log(`ok ${style}`);
})().catch((err) => {
  console.error(String(err));
  process.exit(1);
});
