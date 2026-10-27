// Test-only HTTP adapter; production has no environment-selected verification bypass.
const http = require('node:http');
const { createHmac } = require('node:crypto');
const SECRET = 'local-browser-test-ticket-key';
const NOW = 1893456000;
const NONCE = '0123456789abcdef0123456789abcdef';
async function startServer() {
  const { createHandler } = await import('../src/index.js');
  let calls = 0;
  const env = { NWU_CHALLENGE_TICKET_SECRET: SECRET, TURNSTILE_SECRET_KEY: 'test-only-secret', TURNSTILE_SITE_KEY: 'test-key', TURNSTILE_HOSTNAME: 'worker.example' };
  const handle = createHandler({ now: () => NOW, fetch: async (_url, options) => {
    calls++;
    const token = new URLSearchParams(options.body).get('response');
    const observed = JSON.parse(Buffer.from(token, 'base64url').toString());
    return Response.json({ success: true, hostname: env.TURNSTILE_HOSTNAME, action: observed.action, cdata: observed.cData });
  } });
  const server = http.createServer(async (req, res) => {
    try {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const request = new Request('http://' + req.headers.host + req.url, { method: req.method, headers: req.headers,
        ...(req.method === 'POST' ? { body: Buffer.concat(chunks) } : {}) });
      const response = await handle(request, env);
      res.writeHead(response.status, Object.fromEntries(response.headers));
      res.end(Buffer.from(await response.arrayBuffer()));
    } catch { res.writeHead(500); res.end('Test adapter error'); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return { url: 'http://127.0.0.1:' + server.address().port, nonce: NONCE, now: NOW, calls: () => calls,
    ticket: (overrides = {}) => {
      const fields = { kind: 'req', exp: NOW + 300, ip: '203.0.113.7', host: 'origin.example', path: '/cart?q=one', nonce: NONCE, ...overrides };
      const payload = ['nwu1', fields.kind, fields.exp, fields.ip, fields.host, fields.path, fields.nonce].join('\n');
      return Buffer.from(payload).toString('base64url') + '.' + createHmac('sha1', SECRET).update(payload).digest('base64url');
    },
    close: () => new Promise((resolve, reject) => { server.close(error => error ? reject(error) : resolve()); server.closeAllConnections(); }),
  };
}
module.exports = { startServer };
