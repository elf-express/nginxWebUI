/**
 * Protect the real log-list -> iframe entry, browser selection/Clipboard and
 * responsive layout against a packaged server. Unit tests cannot exercise the
 * iframe navigation, native text selection or the real 20-second Tailer lease.
 * Remote compatibility uses a loopback HTTP stub, never a deployed node.
 */
const { test: base, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const http = require('http');
const { randomUUID } = require('crypto');
const { TEST_DATA_DIR, BASE_URL, login } = require('./helpers');

const labels = {
  zh_TW: { title: '日誌檢視器', copy: '複製顯示內容', pause: '暫停顯示', resume: '恢復顯示', follow: '跟隨最新內容', wrap: '自動換行', region: '日誌內容', toolbar: '日誌操作', manual: '手動複製' },
  zh: { title: '日志查看器', copy: '复制显示内容', pause: '暂停显示', resume: '恢复显示', follow: '跟随最新内容', wrap: '自动换行', region: '日志内容', toolbar: '日志操作', manual: '手动复制' },
  en_US: { title: 'Log viewer', copy: 'Copy visible lines', pause: 'Pause display', resume: 'Resume display', follow: 'Follow latest', wrap: 'Wrap lines', region: 'Log content', toolbar: 'Log controls', manual: 'Copy manually' },
};
const lines = (start, end, prefix = 'line') => Array.from({ length: end - start + 1 }, (_, i) => `${prefix}-${start + i}`);
const textOf = (viewer) => viewer.locator('pre code');
const regionOf = (viewer, lang = 'zh_TW') => viewer.getByRole('region', { name: labels[lang].region, exact: true });
const button = (viewer, key, lang = 'zh_TW') => viewer.getByRole('button', { name: labels[lang][key], exact: true });
const fixtureRoots = new Set();

async function waitForBackgroundLine(page, line) {
  const containsLine = async (res) => res.url().endsWith('/log/tailData') && (await res.json()).obj?.lines.includes(line);
  await page.waitForResponse(containsLine);
  // The next poll is scheduled only after the first response is processed by
  // the app, so frozen DOM assertions cannot race that first publication.
  await page.waitForResponse(containsLine);
}

function removeOwnFixture(root) {
  if (!fs.existsSync(root)) return;
  const resolved = fs.realpathSync(root);
  const allowed = fs.realpathSync(TEST_DATA_DIR) + path.sep;
  if (!resolved.startsWith(allowed) || !path.basename(resolved).startsWith('e478-viewer-')) {
    throw new Error(`Refusing fixture cleanup outside test-data: ${resolved}`);
  }
  fs.rmSync(resolved, { recursive: true, force: true });
}

const test = base.extend({
  logApp: async ({ page, context }, use) => {
    // Every test explicitly selects the server's actual locale, not browser-only locale.
    await login(page);
    await page.request.post('/adminPage/login/changeLang', { form: { lang: 'zh_TW' } });
    const root = fs.mkdtempSync(path.join(TEST_DATA_DIR, 'e478-viewer-'));
    fixtureRoots.add(root);
    const records = [];
    const extraPages = [];
    const app = {
      page, context, root, records,
      async create(content = 'fixture\n', name = `${randomUUID()}.log`) {
        const file = path.join(root, name).replace(/\\/g, '/');
        if (content !== null) fs.writeFileSync(file, content, 'utf8');
        const result = await (await page.request.post('/adminPage/log/addOver', { form: { path: file } })).json();
        expect(result.success, 'register own server-side log fixture').toBe(true);
        await page.goto('/adminPage/log?limit=100');
        const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: file, exact: true }) });
        await expect(row).toBeVisible();
        const id = await row.locator('input[name="ids"]').getAttribute('value');
        expect(id).toBeTruthy();
        const record = { file, id };
        records.push(record);
        return record;
      },
      async open(record, target = page, lang = 'zh_TW', requireVue = true) {
        await target.goto('/adminPage/monitor');
        // The isolated Windows server has no nginx binary; dismiss its existing
        // monitor warning using the repository's supported Layui test driver.
        await target.evaluate(() => window.layer?.closeAll());
        await target.getByRole('link', { name: /Nginx.*(日誌|日志)|Nginx log/i }).click({ timeout: 5000 });
        await expect(target).toHaveURL(/\/adminPage\/log(?:\?|$)/);
        const row = target.getByRole('row').filter({ has: target.getByRole('cell', { name: record.file, exact: true }) });
        await row.getByRole('button', { name: /^(追蹤|追踪|Tail)$/ }).click({ timeout: 5000 });
        const iframe = target.locator('iframe[src*="/adminPage/log/tail?"]');
        await expect(iframe).toHaveCount(1);
        // FrameLocator is retained so navigation/fallback still targets this iframe.
        const viewer = target.frameLocator('iframe[src*="/adminPage/log/tail?"]');
        if (requireVue) await expect(viewer.getByRole('heading', { name: labels[lang].title })).toBeVisible({ timeout: 5000 });
        return { viewer, iframe };
      },
      async newPage() {
        const other = await context.newPage();
        extraPages.push(other);
        return other;
      },
    };
    try {
      await use(app);
    } finally {
      for (const other of extraPages) await other.close();
      // Restore only session/locale state that this spec changes, then delete our own IDs.
      await page.unrouteAll({ behavior: 'wait' });
      await page.request.post('/adminPage/remote/change', { form: { id: 'local' } });
      await page.request.post('/adminPage/login/changeLang', { form: { lang: 'zh_TW' } });
      await login(page);
      for (const record of records) {
        const result = await (await page.request.post('/adminPage/log/del', { form: { id: record.id } })).json();
        expect(result.success, `delete own fixture ${record.id}`).toBe(true);
      }
      // Windows holds the reader handle until the existing 20-second idle lease
      // expires. Remove our files once at suite teardown, after viewers close.
    }
  },
});

test.use({ locale: 'zh-TW' });

test.describe('Vue log viewer through the real list iframe', () => {
  test.afterAll(async () => {
    await expect.poll(() => {
      for (const root of fixtureRoots) {
        try { removeOwnFixture(root); fixtureRoots.delete(root); }
        catch (error) { if (!['EPERM', 'EBUSY'].includes(error.code)) throw error; }
      }
      return fixtureRoots.size;
    }, { timeout: 30000, intervals: [1000] }).toBe(0);
  });
  test('opens Vue and preserves literal whitespace, HTML and the recent 50/500-line bounds', async ({ logApp }) => {
    const initial = lines(1, 80);
    initial[77] = '  tab\t  空白 <img src=x onerror="window.__logExecuted=1"> & <script>window.__logExecuted=2</script>';
    initial[78] = '';
    initial[79] = 'long  ' + 'x'.repeat(1800);
    const record = await logApp.create(initial.join('\r\n') + '\r\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText(initial.slice(-50).join('\n'), { useInnerText: false });
    await expect(viewer.getByTestId('visible-range')).toHaveText('目前顯示 50 行，最多 500 行；僅為日誌片段。');
    expect(await textOf(viewer).evaluate((el) => el.textContent)).toBe(initial.slice(-50).join('\n'));
    expect(await textOf(viewer).locator('img,script').count()).toBe(0);
    expect(await textOf(viewer).evaluate(() => window.__logExecuted)).toBeUndefined();
    expect(await textOf(viewer).evaluate((el) => getComputedStyle(el.parentElement).whiteSpace)).toBe('pre');
    const region = regionOf(viewer);
    expect(await region.evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(true);
    fs.appendFileSync(record.file, lines(81, 680).join('\n') + '\n');
    await expect.poll(async () => (await textOf(viewer).textContent()).split('\n'), { timeout: 12000 }).toEqual(lines(181, 680));
    await expect(viewer.getByTestId('visible-range')).toHaveText('目前顯示 500 行，最多 500 行；僅為日誌片段。');
    expect(await region.evaluate((el) => el.scrollHeight - el.clientHeight - el.scrollTop)).toBeLessThanOrEqual(4);
    await viewer.getByRole('switch', { name: labels.zh_TW.wrap }).click();
    await expect(viewer.getByRole('switch', { name: labels.zh_TW.wrap })).toHaveAttribute('aria-checked', 'true');
    expect(await textOf(viewer).evaluate((el) => getComputedStyle(el.parentElement).whiteSpace)).toBe('pre-wrap');
  });

  test('manual follow off freezes the snapshot and count while polling, and on publishes the latest', async ({ logApp }) => {
    const record = await logApp.create('first\nsecond\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText('first\nsecond');
    await textOf(viewer).evaluate((el) => { window.__snapshotNode = el.firstChild; });
    await button(viewer, 'follow').click();
    await expect(button(viewer, 'follow')).toHaveAttribute('aria-pressed', 'false');
    const response = waitForBackgroundLine(logApp.page, 'after-off');
    fs.appendFileSync(record.file, 'after-off\n');
    await response;
    expect(await textOf(viewer).evaluate((el) => el.firstChild === window.__snapshotNode)).toBe(true);
    await expect(textOf(viewer)).toHaveText('first\nsecond');
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 2 行');
    await button(viewer, 'follow').click();
    await expect(button(viewer, 'follow')).toHaveAttribute('aria-pressed', 'true');
    await expect(textOf(viewer)).toHaveText('first\nsecond\nafter-off');
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 3 行');
  });

  test('scroll and native selection survive updates until explicit follow clears selection', async ({ logApp }) => {
    const record = await logApp.create(lines(1, 50, 'selection').join('\n') + '\n');
    const { viewer } = await logApp.open(record);
    const region = regionOf(viewer);
    await expect(textOf(viewer)).toContainText('selection-50');
    await expect(button(viewer, 'follow')).toHaveAttribute('aria-pressed', 'true');
    const box = await region.boundingBox();
    // Select at the bottom while following is still enabled: selection itself,
    // independently of an earlier upward scroll, must freeze the snapshot.
    await logApp.page.mouse.move(box.x + 18, box.y + box.height - 24);
    await logApp.page.mouse.down();
    await logApp.page.mouse.move(box.x + 125, box.y + box.height - 24, { steps: 10 });
    await logApp.page.mouse.up();
    await expect.poll(() => region.evaluate(() => getSelection().toString().length)).toBeGreaterThan(0);
    await expect(button(viewer, 'follow')).toHaveAttribute('aria-pressed', 'false');
    const selected = await region.evaluate(() => getSelection().toString());
    const scrollTop = await region.evaluate((el) => el.scrollTop);
    await textOf(viewer).evaluate((el) => { window.__selectedNode = el.firstChild; });
    const response = waitForBackgroundLine(logApp.page, 'new-selection-line');
    fs.appendFileSync(record.file, 'new-selection-line\n');
    await response;
    expect(await region.evaluate(() => getSelection().toString())).toBe(selected);
    expect(await textOf(viewer).evaluate((el) => el.firstChild === window.__selectedNode)).toBe(true);
    expect(await region.evaluate((el) => el.scrollTop)).toBe(scrollTop);
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 50 行');
    await button(viewer, 'follow').click();
    await expect(textOf(viewer)).toContainText('new-selection-line');
    expect(await region.evaluate(() => getSelection().toString())).toBe('');
    await expect(button(viewer, 'follow')).toHaveAttribute('aria-pressed', 'true');
    await region.evaluate((el) => { el.scrollTop = 0; });
    await expect(button(viewer, 'follow')).toHaveAttribute('aria-pressed', 'false');
    const afterScroll = waitForBackgroundLine(logApp.page, 'after-scroll-line');
    fs.appendFileSync(record.file, 'after-scroll-line\n');
    await afterScroll;
    expect(await region.evaluate((el) => el.scrollTop)).toBe(0);
    await expect(textOf(viewer)).not.toContainText('after-scroll-line');
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 51 行');
    await button(viewer, 'follow').click();
    await expect(textOf(viewer)).toContainText('after-scroll-line');
  });

  test('pause longer than the 20-second idle lease keeps background history and resumes latest', async ({ logApp }) => {
    const record = await logApp.create(lines(1, 50, 'lease').join('\n') + '\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toContainText('lease-50');
    await button(viewer, 'pause').click();
    const snapshot = await textOf(viewer).textContent();
    const started = Date.now();
    let polls = 0;
    const listener = (req) => { if (req.url().endsWith('/log/tailData')) polls++; };
    logApp.page.on('request', listener);
    fs.appendFileSync(record.file, lines(51, 120, 'lease').join('\n') + '\n');
    await expect.poll(() => Date.now() - started, { intervals: [1000], timeout: 24000 }).toBeGreaterThan(21000);
    expect(polls).toBeGreaterThanOrEqual(15);
    await expect(textOf(viewer)).toHaveText(snapshot);
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 50 行');
    await button(viewer, 'resume').click();
    await expect.poll(async () => (await textOf(viewer).textContent()).split('\n')).toEqual(lines(1, 120, 'lease'));
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 120 行');
    logApp.page.off('request', listener);
  });

  test('copies exactly the visible LF snapshot and reports Clipboard success', async ({ logApp }) => {
    await logApp.context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await logApp.context.addInitScript(() => {
      const writeText = navigator.clipboard.writeText.bind(navigator.clipboard);
      navigator.clipboard.writeText = (value) => { window.__clipboardInput = value; return writeText(value); };
    });
    const record = await logApp.create('  copy\tspace\r\n\r\nlast\r\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText('  copy\tspace\n\nlast');
    await button(viewer, 'copy').click();
    await expect(viewer.getByRole('status')).toHaveText('已複製');
    expect(await textOf(viewer).evaluate(() => window.__clipboardInput)).toBe('  copy\tspace\n\nlast');
    // Windows' native clipboard translates line endings to CRLF on readback.
    const copied = await textOf(viewer).evaluate(() => navigator.clipboard.readText());
    expect(copied.replace(/\r\n/g, '\n')).toBe('  copy\tspace\n\nlast');
  });

  test('Clipboard denial offers a readonly selected textarea containing the frozen click-time snapshot', async ({ logApp }) => {
    await logApp.context.grantPermissions([]);
    // Deny via actual Chromium permission state rather than replacing writeText.
    const cdp = await logApp.context.newCDPSession(logApp.page);
    await cdp.send('Browser.setPermission', { permission: { name: 'clipboard-write' }, setting: 'denied', origin: BASE_URL });
    const record = await logApp.create('  manual\tspace\nlast\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toContainText('last');
    await button(viewer, 'pause').click();
    await button(viewer, 'copy').click();
    const manual = viewer.getByRole('textbox', { name: labels.zh_TW.manual });
    await expect(manual).toHaveValue('  manual\tspace\nlast');
    await expect(manual).toHaveAttribute('readonly', '');
    await expect(viewer.getByRole('alert')).toContainText('無法使用剪貼簿');
    expect(await manual.evaluate((el) => document.activeElement === el && el.selectionStart === 0 && el.selectionEnd === el.value.length)).toBe(true);
    const response = waitForBackgroundLine(logApp.page, 'after-copy');
    fs.appendFileSync(record.file, 'after-copy\n');
    await response;
    await expect(manual).toHaveValue('  manual\tspace\nlast');
    await cdp.detach();
  });

  test('two real viewer windows use distinct guids and never mix log files', async ({ logApp }) => {
    const a = await logApp.create('only-A\n');
    const b = await logApp.create('only-B\n');
    const guids = new Map();
    logApp.context.on('request', (req) => {
      if (req.url().endsWith('/log/tailData')) {
        const form = new URLSearchParams(req.postData());
        guids.set(form.get('id'), form.get('guid'));
      }
    });
    const first = await logApp.open(a);
    const otherPage = await logApp.newPage();
    const second = await logApp.open(b, otherPage);
    await expect(textOf(first.viewer)).toHaveText('only-A');
    await expect(textOf(second.viewer)).toHaveText('only-B');
    expect(guids.get(a.id)).toBeTruthy();
    expect(guids.get(b.id)).toBeTruthy();
    expect(guids.get(a.id)).not.toBe(guids.get(b.id));
    fs.appendFileSync(a.file, 'append-A\n');
    fs.appendFileSync(b.file, 'append-B\n');
    await expect(textOf(first.viewer)).toHaveText('only-A\nappend-A');
    await expect(textOf(second.viewer)).toHaveText('only-B\nappend-B');
  });

  test('empty and missing files recover after append, and a directory reports unreadable', async ({ logApp }) => {
    const empty = await logApp.create('');
    let opened = await logApp.open(empty);
    await expect(opened.viewer.getByText('日誌尚無內容', { exact: true })).toBeVisible();
    fs.appendFileSync(empty.file, 'now-present\n');
    await expect(textOf(opened.viewer)).toHaveText('now-present');
    const missing = await logApp.create(null);
    opened = await logApp.open(missing);
    await expect(opened.viewer.getByRole('status')).toHaveText('日誌檔案不存在');
    fs.writeFileSync(missing.file, 'created-later\n');
    await expect(textOf(opened.viewer)).toHaveText('created-later');
    const unreadable = await logApp.create(null);
    fs.mkdirSync(unreadable.file);
    opened = await logApp.open(unreadable);
    await expect(opened.viewer.getByRole('status')).toHaveText('無法讀取日誌檔案');
    fs.rmdirSync(unreadable.file);
    fs.writeFileSync(unreadable.file, 'readable-again\n');
    await expect(textOf(opened.viewer)).toHaveText('readable-again');
  });

  test('same-size live replacement follows the new generation without reloading the iframe', async ({ logApp }) => {
    test.skip(process.platform === 'win32', 'Windows RAF denies renaming open files; deterministic old-handle/new-path JUnit regression covers this state. This is not a Windows rotation PASS.');
    const record = await logApp.create('old-generation\n');
    const guids = new Set();
    logApp.page.on('request', (req) => {
      if (req.url().endsWith('/log/tailData')) guids.add(new URLSearchParams(req.postData()).get('guid'));
    });
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText('old-generation');
    const originalSize = fs.statSync(record.file).size;
    fs.renameSync(record.file, `${record.file}.1`);
    fs.writeFileSync(record.file, 'new-generation\n');
    expect(fs.statSync(record.file).size).toBe(originalSize);
    await expect(textOf(viewer)).toHaveText('new-generation');
    fs.appendFileSync(record.file, 'after-replacement\n');
    await expect(textOf(viewer)).toHaveText('new-generation\nafter-replacement');
    await expect(viewer.getByTestId('visible-range')).toContainText('顯示 2 行');
    expect(guids.size).toBe(1);
  });

  test('a live missing log recovers within its lease without resurrecting the previous generation', async ({ logApp }) => {
    test.skip(process.platform === 'win32', 'Windows RAF denies renaming open files; deterministic missing/invalidation/recreation JUnit regression covers this state. This is not a Windows rotation PASS.');
    const record = await logApp.create('before-disappearance\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText('before-disappearance');
    const started = Date.now();
    fs.renameSync(record.file, `${record.file}.1`);
    await expect(viewer.getByRole('status')).toHaveText('日誌檔案不存在');
    fs.writeFileSync(record.file, 'after-recreation\n');
    await expect(textOf(viewer)).toHaveText('after-recreation');
    fs.appendFileSync(record.file, 'following-again\n');
    await expect(textOf(viewer)).toHaveText('after-recreation\nfollowing-again');
    expect(Date.now() - started).toBeLessThan(20000);
  });

  test('transport errors retain the last snapshot and retry recovers without legacy fallback', async ({ logApp }) => {
    let fail = false;
    await logApp.page.route('**/adminPage/log/tailData', (route) => fail ? route.fulfill({ status: 503, body: '' }) : route.continue());
    const record = await logApp.create('before-error\n');
    const { viewer } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText('before-error');
    fail = true;
    await expect(viewer.getByRole('alert')).toBeVisible();
    await expect(textOf(viewer)).toHaveText('before-error');
    fs.appendFileSync(record.file, 'after-recovery\n');
    fail = false;
    await viewer.getByRole('button', { name: '重試', exact: true }).click({ timeout: 5000 });
    await expect(textOf(viewer)).toHaveText('before-error\nafter-recovery');
    await expect(viewer.getByRole('alert')).toHaveCount(0);
  });

  test('legacy entry and download remain compatible and escape HTML', async ({ logApp }) => {
    const raw = '<img src=x onerror="window.__legacyExecuted=1"> & <b>literal</b>\n';
    const record = await logApp.create(raw);
    const { iframe, viewer } = await logApp.open(record, logApp.page, 'zh_TW', false);
    const download = await logApp.page.request.get(`/adminPage/log/down?id=${record.id}`);
    expect(download.ok()).toBe(true);
    expect(await download.text()).toBe(raw);
    const original = new URL(await iframe.getAttribute('src'), BASE_URL);
    original.searchParams.set('legacy', '1');
    await iframe.evaluate((el, url) => { el.src = url; }, original.toString());
    await expect(viewer.locator('#log-container')).toHaveText(raw.trimEnd());
    expect(await viewer.locator('#log-container img,#log-container b').count()).toBe(0);
    expect(await viewer.locator('#log-container').evaluate(() => window.__legacyExecuted)).toBeUndefined();
  });

  for (const fault of ['bundle', 'dictionary', 'api404']) {
    test(`${fault} failure falls back once and preserves every query parameter`, async ({ logApp }) => {
      const record = await logApp.create('fallback-literal\n');
      const { iframe, viewer } = await logApp.open(record);
      const query = new URL(await iframe.getAttribute('src'), BASE_URL);
      query.searchParams.set('ctx', Buffer.from('//localhost:18080').toString('base64'));
      query.searchParams.set('protocol', 'http');
      query.searchParams.append('probe', 'a&b=中');
      query.searchParams.append('probe', 'second');
      query.searchParams.set('empty', '');
      const routePattern = fault === 'bundle' ? '**/js/spa/logTail.js*' : fault === 'dictionary' ? '**/adminPage/i18n*' : '**/adminPage/log/tailData';
      await logApp.page.route(routePattern, (route) => route.fulfill({ status: fault === 'dictionary' ? 500 : 404, body: '' }));
      let fallbacks = 0;
      logApp.page.on('framenavigated', (frame) => { if (new URL(frame.url(), BASE_URL).searchParams.get('legacy') === '1') fallbacks++; });
      await iframe.evaluate((el, url) => { el.src = url; }, query.toString());
      await expect(viewer.locator('#log-container')).toHaveText('fallback-literal');
      const final = await iframe.evaluate((el) => new URL(el.contentWindow.location.href).search);
      const params = new URLSearchParams(final);
      for (const key of ['id', 'ctx', 'protocol', 'probe', 'empty']) expect(params.getAll(key)).toEqual(query.searchParams.getAll(key));
      expect(params.get('legacy')).toBe('1');
      const secondPoll = logApp.page.waitForResponse((res) => res.url().includes('/log/tailCmd'));
      await secondPoll;
      expect(fallbacks).toBe(1);
    });
  }

  test('expired login redirects the iframe to the existing login page and stops polling', async ({ logApp }) => {
    const record = await logApp.create('authenticated\n');
    const { viewer, iframe } = await logApp.open(record);
    await expect(textOf(viewer)).toHaveText('authenticated');
    await logApp.context.clearCookies();
    await expect(viewer.locator('#name')).toBeVisible();
    expect(await iframe.evaluate((el) => el.contentWindow.location.pathname)).toBe('/adminPage/login');
    expect(await iframe.evaluate((el) => el.contentWindow.location.search)).not.toContain('legacy=1');
  });

  test('unmount cancels a held request and sends no more polls from the removed iframe', async ({ logApp }) => {
    const record = await logApp.create('unmount\n');
    let held;
    let pendingRequests = 0;
    await logApp.page.route('**/adminPage/log/tailData', async (route) => {
      held = route;
      pendingRequests++;
      await new Promise((resolve) => { logApp.page.once('requestfailed', resolve); });
      await route.abort().catch(() => {});
    });
    const { viewer, iframe } = await logApp.open(record);
    await expect.poll(() => !!held).toBe(true);
    await expect(regionOf(viewer)).toHaveAttribute('aria-busy', 'true');
    await expect(viewer.getByRole('status')).toHaveText('正在載入日誌');
    const pendingSince = Date.now();
    await expect.poll(() => Date.now() - pendingSince, { intervals: [500], timeout: 4000 }).toBeGreaterThan(2100);
    expect(pendingRequests).toBe(1);
    const failed = logApp.page.waitForEvent('requestfailed', (req) => req.url().endsWith('/log/tailData'));
    await iframe.evaluate((el) => el.remove());
    const request = await failed;
    expect(request.failure().errorText).toMatch(/ABORTED|FAILED/);
    let later = 0;
    logApp.page.on('request', (req) => { if (req.url().endsWith('/log/tailData')) later++; });
    const start = Date.now();
    await expect.poll(() => Date.now() - start, { intervals: [500], timeout: 4000 }).toBeGreaterThan(2100);
    expect(later).toBe(0);
  });

  test('loopback remote proxy forwards the viewer and POST identity, then preserves noServer redirect', async ({ logApp }) => {
    const record = await logApp.create('local-only\n');
    const dictionary = await (await logApp.page.request.get('/adminPage/i18n')).text();
    const template = await (await logApp.page.request.get(`/adminPage/log/tail?id=${record.id}`)).text();
    const calls = [];
    let disconnected = false;
    const stub = http.createServer(async (req, res) => {
      let body = '';
      for await (const chunk of req) body += chunk;
      const url = new URL(req.url, 'http://127.0.0.1');
      calls.push({ path: url.pathname, method: req.method, body, query: url.searchParams });
      if (disconnected && url.pathname.endsWith('/tailData')) { req.socket.destroy(); return; }
      res.setHeader('Content-Type', url.pathname.endsWith('/tail') ? 'text/html;charset=utf-8' : 'application/json');
      if (url.pathname.endsWith('/getCredit')) res.end(JSON.stringify({ success: true, obj: { creditKey: 'e478-loopback-only', system: 'Linux' } }));
      else if (url.pathname.endsWith('/version')) res.end(JSON.stringify({ version: '5.4.1', nginx: 0 }));
      else if (url.pathname.endsWith('/i18n')) res.end(dictionary);
      else if (url.pathname.endsWith('/tail')) res.end(template);
      else if (url.pathname.endsWith('/tailData')) res.end(JSON.stringify({ success: true, obj: { state: 'ready', path: 'loopback-remote.log', lines: ['remote-stub-only'], limit: 500 } }));
      else { res.statusCode = 404; res.end('{}'); }
    });
    await new Promise((resolve) => stub.listen(0, '127.0.0.1', resolve));
    let remoteId;
    try {
      const port = stub.address().port;
      const saved = await (await logApp.page.request.post('/adminPage/remote/addOver', { form: { ip: '127.0.0.1', port, protocol: 'http', name: 'stub', pass: 'stub', code: '', auth: '', descr: `e478-${randomUUID()}` } })).json();
      expect(saved.success).toBe(true);
      const remotes = await (await logApp.page.request.get('/adminPage/remote/allTable')).json();
      remoteId = remotes.data.find((remote) => remote.ip === '127.0.0.1' && remote.port === port).id;
      const { viewer, iframe } = await logApp.open(record);
      await expect(textOf(viewer)).toHaveText('local-only');
      await logApp.page.request.post('/adminPage/remote/change', { form: { id: remoteId } });
      await iframe.evaluate((el) => { el.contentWindow.location.reload(); });
      await expect(textOf(viewer)).toHaveText('remote-stub-only');
      const forwarded = calls.filter((call) => call.path.endsWith('/tailData')).at(-1);
      expect(forwarded.method).toBe('POST');
      const form = new URLSearchParams(forwarded.body);
      expect(form.get('id')).toBe(record.id);
      expect(form.get('guid')).toBeTruthy();
      expect(form.get('creditKey')).toBe('e478-loopback-only');
      expect(form.has('path')).toBe(false);
      expect(forwarded.query.get('protocol')).toBe('http');
      expect(forwarded.query.get('ctx')).toBeTruthy();
      disconnected = true;
      await expect.poll(() => iframe.evaluate((el) => el.contentWindow.location.pathname), { timeout: 15000 }).toBe('/adminPage/login/noServer');
      expect(await iframe.evaluate((el) => el.contentWindow.location.search)).not.toContain('legacy=1');
    } finally {
      await logApp.page.request.post('/adminPage/remote/change', { form: { id: 'local' } });
      if (remoteId) await logApp.page.request.post('/adminPage/remote/del', { form: { id: remoteId } });
      await new Promise((resolve) => stub.close(resolve));
    }
  });

  for (const lang of ['zh', 'zh_TW', 'en_US']) {
    for (const [width, height] of [[1440, 900], [1280, 800], [900, 640], [360, 540]]) {
      test(`${lang} toolbar and text remain operable at ${width}x${height} without CDN`, async ({ logApp }, testInfo) => {
        const record = await logApp.create('layout\t  spacing ' + 'long'.repeat(400) + '\nsecond\n');
        await logApp.page.request.post('/adminPage/login/changeLang', { form: { lang } });
        const requests = [];
        logApp.page.on('request', (req) => { if (/https?:\/\/(?!localhost:18080(?:\/|$)|127\.0\.0\.1:18080(?:\/|$))/.test(req.url())) requests.push(req.url()); });
        // Resize before opening the percentage-sized legacy layer, whose live
        // resize handler would otherwise overwrite our fixed iframe height.
        await logApp.page.setViewportSize({ width: width + 80, height: height + 100 });
        const { viewer, iframe } = await logApp.open(record, logApp.page, lang);
        // Size the actual list iframe to the acceptance dimensions after entering through its button.
        await iframe.evaluate((el, size) => { el.style.width = `${size.width}px`; el.style.height = `${size.height}px`; }, { width, height });
        await expect(textOf(viewer)).toContainText('layout\t  spacing');
        await expect(viewer.getByRole('toolbar', { name: labels[lang].toolbar })).toBeVisible();
        const controls = [button(viewer, 'pause', lang), button(viewer, 'follow', lang), button(viewer, 'copy', lang), viewer.getByRole('switch', { name: labels[lang].wrap })];
        const rects = [];
        for (const control of controls) {
          await expect(control).toBeVisible();
          rects.push(await control.evaluate((el) => { const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, right: r.right, bottom: r.bottom }; }));
        }
        for (const r of rects) { expect(r.x).toBeGreaterThanOrEqual(0); expect(r.right).toBeLessThanOrEqual(width); }
        for (let i = 0; i < rects.length; i++) for (let j = i + 1; j < rects.length; j++) {
          const a = rects[i], b = rects[j];
          expect(a.right <= b.x || b.right <= a.x || a.bottom <= b.y || b.bottom <= a.y).toBe(true);
        }
        const geometry = await regionOf(viewer, lang).evaluate((el) => ({ width: innerWidth, height: innerHeight, pageWidth: document.documentElement.scrollWidth, top: el.getBoundingClientRect().top, bottom: el.getBoundingClientRect().bottom, regionHeight: el.clientHeight, contentWidth: el.scrollWidth, regionWidth: el.clientWidth }));
        expect(geometry.width).toBe(width);
        expect(geometry.height).toBe(height);
        expect(geometry.pageWidth).toBe(width);
        expect(geometry.regionHeight).toBeGreaterThan(80);
        expect(geometry.bottom).toBeLessThanOrEqual(height);
        expect(Math.max(...rects.map((r) => r.bottom))).toBeLessThanOrEqual(geometry.top);
        expect(geometry.contentWidth).toBeGreaterThan(geometry.regionWidth);
        await viewer.getByRole('switch', { name: labels[lang].wrap }).focus();
        await logApp.page.keyboard.press('Space');
        await expect(viewer.getByRole('switch', { name: labels[lang].wrap })).toHaveAttribute('aria-checked', 'true');
        await expect(button(viewer, 'copy', lang)).toHaveAttribute('type', 'button');
        expect(await viewer.locator('a[href^="javascript:"],[contenteditable]').count()).toBe(0);
        expect(requests).toEqual([]);
        await testInfo.attach(`layout-${lang}-${width}x${height}`, { body: await iframe.screenshot(), contentType: 'image/png' });
      });
    }
  }
});
