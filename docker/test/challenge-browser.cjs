// Real Chromium through the challenge page (E-387), used by crowdsec-challenge-e2e.sh --browser.
// Only requests to the test host are allowed; any other request is aborted and fails the check (offline proof).
// The test site sends Content-Security-Policy "default-src 'self'": the challenge page must carry it and pass without
// any CSP violation in the console.
// Usage: node docker/test/challenge-browser.cjs <base url, e.g. https://localhost:18443> <auto|checkbox>
const { chromium } = require('@playwright/test');

const [base, style] = process.argv.slice(2);

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
  await browser.close();
  console.log(`ok ${style}`);
})().catch((err) => {
  console.error(String(err));
  process.exit(1);
});
