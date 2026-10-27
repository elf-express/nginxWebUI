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
