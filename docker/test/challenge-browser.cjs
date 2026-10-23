// Real Chromium through the challenge page (E-387), used by crowdsec-challenge-e2e.sh --browser.
// Only requests to the test host are allowed; any other request is aborted and fails the check (offline proof).
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
  await page.goto(`${base}/?from=browser`);
  if (!page.url().includes('/.well-known/nwu-challenge/')) {
    throw new Error(`not sent to the challenge page: ${page.url()}`);
  }
  const shown = await page.locator('body').getAttribute('data-style');
  if (shown !== style) {
    throw new Error(`style ${shown}, want ${style}`);
  }
  if (style === 'checkbox') {
    await page.locator('altcha-widget input[type=checkbox]').click();
  }
  await page.waitForURL(`${base}/?from=browser`, { timeout: 30000 });
  const cookie = (await context.cookies()).find((c) => c.name === 'nwu_pass');
  if (!cookie || !cookie.httpOnly || !cookie.secure) {
    throw new Error(`nwu_pass cookie missing or without HttpOnly/Secure: ${JSON.stringify(cookie)}`);
  }
  await page.goto(`${base}/second`);
  if (page.url().includes('/.well-known/nwu-challenge/')) {
    throw new Error('challenged again within the cookie lifetime');
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
