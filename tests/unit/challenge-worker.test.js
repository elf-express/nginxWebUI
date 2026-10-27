const test = require('node:test');
const assert = require('node:assert/strict');
const { createHmac } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const vectors = JSON.parse(fs.readFileSync(path.join(root, 'docker/lua/test/ticket-vectors.json'), 'utf8'));
const secret = vectors.secret;
const NOW = 1893456000;
const req = { kind: 'req', exp: NOW + 300, ip: '203.0.113.7', host: 'Shop.Example', path: '/cart?id=1', nonce: '0123456789abcdef0123456789abcdef' };
const payload = f => ['nwu1', f.kind, f.exp, f.ip, f.host, f.path, f.nonce].join('\n');
const signRaw = text => Buffer.from(text).toString('base64url') + '.' + createHmac('sha1', secret).update(text).digest('base64url');
const signReq = fields => signRaw(payload({ ...req, ...fields }));
const api = () => import('../../cloudflare/challenge-worker/src/tickets.js');

for (const v of vectors.vectors) {
  test('vector: ' + v.name, async () => {
    const t = await api();
    assert.equal(payload(v.fields), v.payload);
    if (v.fields.kind === 'req') {
      assert.deepEqual(await t.verifyRequestTicket(signRaw(v.payload), secret, v.fields.exp - 300), v.fields);
    } else {
      assert.equal(await t.signResponseTicket({ ...v.fields, kind: 'req' }, secret, v.fields.exp - 60), signRaw(v.payload));
      await assert.rejects(t.verifyRequestTicket(signRaw(v.payload), secret, v.fields.exp - 60));
    }
  });
}
test('ticket: exact expiry boundary and response fields stay compatible', async () => {
  const t = await api();
  assert.equal((await t.verifyRequestTicket(signReq({ exp: NOW }), secret, NOW)).exp, NOW);
  const token = await t.signResponseTicket(req, secret, NOW);
  assert.equal(token, signRaw(payload({ ...req, kind: 'resp', exp: NOW + 60 })));
  assert.deepEqual(await t.verifyRequestTicket(signReq({}), secret, NOW), req);
});
for (const [name, fields] of [
  ['expired', { exp: NOW - 1 }], ['too far ahead', { exp: NOW + 301 }],
  ['response kind', { kind: 'resp' }], ['fractional expiry', { exp: NOW + 0.5 }],
  ['empty IP', { ip: '' }], ['invalid IP', { ip: 'not-an-ip' }],
  ['newline', { host: 'shop.example\nevil' }], ['empty nonce', { nonce: '' }],
  ['nonhex nonce', { nonce: 'z'.repeat(32) }], ['unsafe host', { host: 'shop.example@evil.example' }],
]) test('ticket rejects ' + name, async () => {
  await assert.rejects((await api()).verifyRequestTicket(signReq(fields), secret, NOW));
});
test('ticket rejects malformed, oversized, wrong version and tampered signatures', async () => {
  const t = await api();
  const good = signReq({});
  for (const bad of ['', 'a'.repeat(4097), good + '=', good + '.x', 'x.' + good.split('.')[1],
    signRaw(payload(req).replace('nwu1', 'nwu2')), signRaw(payload(req) + '\nextra'),
    signRaw(payload(req).split('\n').slice(0, 6).join('\n')), good.slice(0, -4) + 'AAAA']) {
    await assert.rejects(t.verifyRequestTicket(bad, secret, NOW), { name: 'TicketError' });
  }
  await assert.rejects(t.verifyRequestTicket(good, 'different-secret', NOW));
});
test('ticket safeReturn matches Lua and preserves UTF8/encoded queries', async () => {
  const { safeReturn } = await api();
  assert.equal(safeReturn('/%E4%B8%AD?q=a%20b'), '/%E4%B8%AD?q=a%20b');
  for (const bad of [null, '', '//evil', 'https://evil', '/\\evil', '/a\r\nb', '/.well-known/nwu-challenge/', '/' + 'a'.repeat(2048)])
    assert.equal(safeReturn(bad), '/');
  const token = await (await api()).signResponseTicket({ ...req, path: '//evil' }, secret, NOW);
  assert.equal(Buffer.from(token.split('.')[0], 'base64url').toString().split('\n')[5], '/');
});
test('callback URL uses only a validated host and fixed HTTPS path', async () => {
  const { callbackUrl } = await api();
  assert.equal(callbackUrl('Shop.Example', 'a.b'), 'https://Shop.Example/.well-known/nwu-challenge/callback?token=a.b');
  assert.equal(callbackUrl('[2001:db8::7]', 'a.b'), 'https://[2001:db8::7]/.well-known/nwu-challenge/callback?token=a.b');
  for (const host of ['a:8443', 'user@host', 'evil/path', 'evil\\path', 'evil?x', 'a\r\nb', '', 'a b', '-bad.test'])
    assert.throws(() => callbackUrl(host, 'a.b'));
});

const handlerApi = () => import('../../cloudflare/challenge-worker/src/index.js');
const turnstileApi = () => import('../../cloudflare/challenge-worker/src/turnstile.js');
const env = { NWU_CHALLENGE_TICKET_SECRET: secret, TURNSTILE_SECRET_KEY: 'test-siteverify-secret', TURNSTILE_SITE_KEY: 'test-sitekey', TURNSTILE_HOSTNAME: 'worker.example' };
const goodValidation = overrides => ({ success: true, hostname: 'worker.example', action: 'nwu_challenge', cdata: req.nonce, ...overrides });
function post(fields = {}, options = {}) {
  return new Request('https://worker.example/verify', { method: 'POST', headers: { origin: 'https://worker.example', 'content-type': 'application/x-www-form-urlencoded', ...options.headers },
    body: new URLSearchParams({ t: signReq({}), 'cf-turnstile-response': 'test-token', ...fields }) });
}
test('handler success signs only the original request IP/host/path and no cookie', async () => {
  let calls = 0;
  const handle = (await handlerApi()).createHandler({ now: () => NOW, fetch: async (url, options) => {
    calls++; assert.equal(url, 'https://challenges.cloudflare.com/turnstile/v0/siteverify');
    const sent = new URLSearchParams(options.body);
    assert.equal(sent.get('secret'), env.TURNSTILE_SECRET_KEY); assert.equal(sent.get('response'), 'test-token');
    assert.equal(sent.has('remoteip'), false);
    return Response.json(goodValidation());
  } });
  const response = await handle(post({ host: 'evil.example', callback: 'https://evil.example' }, { headers: { 'CF-Connecting-IP': '2001:db8::2' } }), env);
  assert.equal(response.status, 303); assert.equal(calls, 1);
  assert.equal(response.headers.get('set-cookie'), null);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(response.headers.get('referrer-policy'), 'no-referrer');
  const url = new URL(response.headers.get('location'));
  assert.equal(url.origin, 'https://shop.example');
  assert.equal(url.pathname, '/.well-known/nwu-challenge/callback');
  const token = url.searchParams.get('token');
  assert.equal(token, await (await api()).signResponseTicket(req, secret, NOW));
});
test('handler rejects malformed/expired tickets before any Siteverify call', async () => {
  let calls = 0;
  const handle = (await handlerApi()).createHandler({ now: () => NOW, fetch: async () => { calls++; return Response.json(goodValidation()); } });
  for (const token of ['', 'bad', signReq({ exp: NOW - 1 })]) {
    const response = await handle(post({ t: token }), env);
    assert.equal(response.status, 429); assert.equal(response.headers.has('location'), false);
  }
  assert.equal(calls, 0);
  assert.equal((await handle(new Request('https://worker.example/?t=bad'), env)).status, 429);
  assert.equal((await handle(new Request('https://worker.example/'), env)).status, 200);
  assert.equal(calls, 0);
});
for (const [name, overrides] of [
  ['false', { success: false }], ['duplicate', { success: false, 'error-codes': ['timeout-or-duplicate'] }],
  ['hostname', { hostname: 'evil.example' }], ['action', { action: 'login' }],
  ['cdata', { cdata: 'other-nonce' }], ['missing action', { action: undefined }], ['missing cdata', { cdata: undefined }],
]) test('siteverify rejects ' + name, async () => {
  const handle = (await handlerApi()).createHandler({ now: () => NOW, fetch: async () => Response.json(goodValidation(overrides)) });
  const response = await handle(post(), env);
  assert.equal(response.status, 429); assert.equal(response.headers.has('location'), false); assert.equal(response.headers.has('set-cookie'), false);
});
test('handler fails closed on missing config, network errors, HTTP failures and bad JSON', async () => {
  const { createHandler } = await handlerApi();
  for (const name of Object.keys(env)) {
    const response = await createHandler({ now: () => NOW })(post(), { ...env, [name]: '' });
    assert.equal(response.status, 503); assert.equal(response.headers.has('location'), false);
  }
  for (const fetch of [async () => { throw new Error('network'); }, async () => new Response('bad gateway', { status: 500 }), async () => new Response('not json')]) {
    const response = await createHandler({ now: () => NOW, fetch })(post(), env);
    assert.equal(response.status, 503); assert.equal(response.headers.has('location'), false);
  }
});
test('siteverify timeout aborts after five seconds', async t => {
  const { validateTurnstile } = await turnstileApi();
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const result = validateTurnstile('test-token', req.nonce, env, async (_url, options) =>
    new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new Error('aborted')))));
  const check = assert.rejects(result, { name: 'ServiceError' });
  t.mock.timers.tick(5000);
  await check;
});
test('handler rejects wrong Origin, duplicated fields and oversized streamed body', async () => {
  let calls = 0;
  const handle = (await handlerApi()).createHandler({ now: () => NOW, fetch: async () => { calls++; return Response.json(goodValidation()); } });
  assert.equal((await handle(post({}, { headers: { origin: 'https://evil.example' } }), env)).status, 429);
  assert.equal((await handle(post({}, { headers: { origin: '' } }), env)).status, 429);
  assert.equal((await handle(post({}, { headers: { 'content-type': 'text/plain' } }), env)).status, 415);
  const duplicate = post(); const body = await duplicate.text();
  assert.equal((await handle(new Request(duplicate.url, { method: 'POST', headers: duplicate.headers, body: body + '&t=other' }), env)).status, 429);
  assert.equal((await handle(post({ 'cf-turnstile-response': 'a'.repeat(2049) }), env)).status, 429);
  const stream = new ReadableStream({ start(c) { c.enqueue(new Uint8Array(16385)); c.close(); } });
  assert.equal((await handle(new Request(duplicate.url, { method: 'POST', headers: duplicate.headers, body: stream, duplex: 'half' }), env)).status, 413);
  assert.equal(calls, 0);
});
test('handler checks request expiry again after Siteverify completes', async () => {
  let now = NOW;
  const handle = (await handlerApi()).createHandler({ now: () => now, fetch: async () => { now += 301; return Response.json(goodValidation()); } });
  assert.equal((await handle(post(), env)).status, 429);
});
test('handler routes and fixed worker identity are explicit', async () => {
  const handle = (await handlerApi()).createHandler();
  assert.equal((await handle(new Request('https://worker.example/missing'), env)).status, 404);
  assert.equal((await handle(new Request('https://worker.example/', { method: 'PUT' }), env)).status, 405);
});
test('handler_worker_config_requires_fixed_identity', () => {
  const config = fs.readFileSync(path.join(root, 'cloudflare/challenge-worker/wrangler.toml'), 'utf8');
  assert.match(config, /^name\s*=\s*"nginxwebui-challenge"$/m);
  assert.match(config, /^main\s*=\s*"src\/index.js"$/m);
  assert.match(config, /^compatibility_date\s*=\s*"2026-10-24"$/m);
});

test('page dictionary stays reproducible and refuses missing translations', async () => {
  const { spawnSync } = require('node:child_process');
  const builder = path.join(root, 'cloudflare/challenge-worker/scripts/build-messages.cjs');
  const checked = spawnSync(process.execPath, [builder, '--check'], { encoding: 'utf8' });
  assert.equal(checked.status, 0, checked.stderr);
  const { buildMessages, parseProperties } = require(builder);
  assert.equal(parseProperties('challengeStr.test=\\u4F60\\u597D\\nworld')['challengeStr.test'], '你好\nworld');
  assert.throws(() => buildMessages({ en: { 'challengeStr.x': 'x' }, 'zh-CN': {}, 'zh-TW': {} }), /Missing/);
});
test('page escapes all untrusted model values and carries a strict CSP', async () => {
  const { renderPage } = await import('../../cloudflare/challenge-worker/src/page.js');
  const evil = '"><script>window.pwned=1</script>';
  const html = renderPage({ ticket: evil, nonce: evil, sitekey: evil, style: 'checkbox', locale: 'en', ip: evil, time: evil, error: null });
  assert.equal(html.includes('<script>window.pwned=1</script>'), false);
  assert.equal(html.includes('&lt;script&gt;'), true);
  const response = await (await handlerApi()).createHandler({ now: () => NOW })(new Request('https://worker.example/?t=' + signReq({})), env);
  assert.match(response.headers.get('content-security-policy'), /script-src 'self' https:\/\/challenges.cloudflare.com/);
  assert.equal(response.headers.get('content-security-policy').includes('unsafe-inline'), false);
});

test('handler permits no-referrer form navigation only with same-origin Fetch Metadata', async () => {
  const handle = (await handlerApi()).createHandler({ now: () => NOW, fetch: async () => Response.json(goodValidation()) });
  const headers = { origin: 'null', 'sec-fetch-site': 'same-origin', 'sec-fetch-mode': 'navigate' };
  assert.equal((await handle(post({}, { headers }), env)).status, 303);
  for (const changed of [{ 'sec-fetch-site': 'cross-site' }, { 'sec-fetch-site': 'same-site' }, { 'sec-fetch-site': '' }, { 'sec-fetch-mode': '' }, { origin: '' }, { origin: 'https://evil.example' }])
    assert.equal((await handle(post({}, { headers: { ...headers, ...changed } }), env)).status, 429);
});

test('deployment docs and config keep the same fixed Worker identity', () => {
  const worker = path.join(root, 'cloudflare/challenge-worker');
  const config = fs.readFileSync(path.join(worker, 'wrangler.toml'), 'utf8');
  assert.match(config, /^name = "nginxwebui-challenge"$/m);
  const commands = [
    'cd cloudflare/challenge-worker', 'npm ci', 'npm test', 'npm run build', 'npx wrangler login',
    'npx wrangler secret put NWU_CHALLENGE_TICKET_SECRET', 'npx wrangler secret put TURNSTILE_SECRET_KEY', 'npx wrangler deploy',
  ];
  for (const file of ['README.md', 'README_TW.md']) {
    const doc = fs.readFileSync(path.join(worker, file), 'utf8');
    assert.match(doc, /nginxwebui-challenge/);
    assert.match(doc, /main = "src\/index.js"/);
    assert.match(doc, /compatibility_date = "2026-10-24"/);
    assert.doesNotMatch(doc, /--name\b/);
    let last = -1;
    for (const command of commands) {
      const position = doc.indexOf(command);
      assert.ok(position > last, file + ': command order ' + command);
      last = position;
    }
    for (const value of ['TURNSTILE_SITE_KEY', 'TURNSTILE_HOSTNAME', '?style=auto', '?style=checkbox', 'realip.conf', 'ngx.var.scheme', 'NWU_CHALLENGE_SECRET'])
      assert.ok(doc.includes(value), file + ': missing ' + value);
  }
});
