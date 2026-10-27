// Test-only: real image Lua -> actual Worker handler -> real Lua callback.
// Siteverify alone is injected. No cloud resources or production keys are used.
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { randomUUID, createHmac } = require('node:crypto');
const SECRET = 'e396-roundtrip-ticket-test-secret';
const COOKIE_SECRET = 'e396-roundtrip-cookie-test-secret';
const image = process.argv[process.argv.indexOf('--image') + 1];
const docker = (args, input) => execFileSync('docker', args, { input, encoding: 'utf8', timeout: 30000, maxBuffer: 4 * 1024 * 1024 });
const sign = fields => {
  const text = ['nwu1', fields.kind, fields.exp, fields.ip, fields.host, fields.path, fields.nonce].join('\n');
  return Buffer.from(text).toString('base64url') + '.' + createHmac('sha1', SECRET).update(text).digest('base64url');
};

function startTopology(image) {
  const name = 'e396-worker-roundtrip-' + randomUUID();
  const close = () => docker(['rm', '-f', name]);
  try {
    docker(['run', '-d', '--name', name, '--entrypoint', 'sh', image, '-c', 'sleep 240']);
    docker(['exec', '-i', name, 'sh', '-s'], `set -eu
W=/tmp/e396
mkdir -p $W/logs $W/pages
chmod 777 $W $W/logs
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=shop.test -keyout $W/key.pem -out $W/cert.pem >/dev/null 2>&1
cat > $W/challenge.conf <<'CONF'
MODE=external
TTL_SECONDS=3600
COOKIE_SECRET=${COOKIE_SECRET}
TICKET_SECRET=${SECRET}
EXTERNAL_URL=https://worker.example/?style=auto
CONF
cat > $W/bouncer.conf <<'CONF'
ENABLED=true
API_URL=http://127.0.0.1:18082
API_KEY=test-only
MODE=live
BOUNCING_ON_TYPE=all
BAN_TEMPLATE_PATH=/var/lib/crowdsec/lua/templates/ban.html
CONF
for page in challenge http-blocked verify-failed callback-failed; do
  echo '<h1>Test challenge result</h1><p>{{IP}} {{TIME}}</p>' > $W/pages/$page.html
done
echo '/* test */' > $W/pages/page.js
echo '/* test */' > $W/pages/page.css
cat > $W/nginx.conf <<'CONF'
load_module /usr/lib/nginx/modules/ndk_http_module.so;
load_module /usr/lib/nginx/modules/ngx_http_lua_module.so;
pid /tmp/e396/nginx.pid;
error_log /tmp/e396/logs/error.log info;
events {}
http {
  include /etc/nginx/nwu/crowdsec_lua_paths.conf;
  lua_shared_dict crowdsec_cache 1m;
  lua_shared_dict nwu_crowdsec 1m;
  lua_shared_dict nwu_challenge 1m;
  init_by_lua_block {
    assert(require("nwu_crowdsec").init({
      conf="/tmp/e396/bouncer.conf", grace=60, state="/tmp/e396/state.json",
      page="/usr/local/lib/nginxwebui/html/protection-paused.html",
      challenge={conf="/tmp/e396/challenge.conf",pages="/tmp/e396/pages/",altcha="/usr/local/lib/nginxwebui/html/altcha.js"}
    }))
  }
  init_worker_by_lua_block { require("nwu_crowdsec").init_worker() }
  server {
    listen 127.0.0.1:18082;
    access_log off;
    location /v1/decisions {
      default_type application/json;
      return 200 '[{"id":1,"origin":"cscli","type":"captcha","scope":"Ip","value":"$arg_ip","duration":"1h","scenario":"test-only"}]';
    }
  }
  server {
    listen 127.0.0.1:18443 ssl;
    listen [::]:18444 ssl ipv6only=off;
    server_name shop.test;
    ssl_certificate /tmp/e396/cert.pem;
    ssl_certificate_key /tmp/e396/key.pem;
    access_log off;
    location / {
      set $nwu_allowlist 0;
      access_by_lua_block { require("nwu_crowdsec").access() }
      content_by_lua_block { ngx.print("original page") }
    }
  }
}
CONF
nginx -t -c $W/nginx.conf -p $W/
nginx -c $W/nginx.conf -p $W/
`);
    return {
      request(path, ip, cookie) {
        const mapped = ip.includes(':');
        const port = mapped ? 18444 : 18443;
        const resolve = mapped ? 'shop.test:18444:[::ffff:127.0.0.1]' : 'shop.test:18443:127.0.0.1';
        const args = ['exec', name, 'curl', '-sk', '--max-time', '10', '--retry', '3', '--retry-connrefused',
          '--retry-delay', '1', '--resolve', resolve, '--interface', ip,
          '-H', 'Accept: text/html', '-D', '-'];
        if (cookie) args.push('-H', 'Cookie: ' + cookie);
        args.push('https://shop.test:' + port + path);
        const raw = docker(args), [head] = raw.split(/\r?\n\r?\n/);
        const lines = head.split(/\r?\n/), headers = {};
        for (const line of lines.slice(1)) {
          const split = line.indexOf(':');
          if (split > 0) headers[line.slice(0, split).toLowerCase()] = line.slice(split + 1).trim();
        }
        return { status: Number(lines[0].split(' ')[1]), headers };
      },
      close,
    };
  } catch (error) {
    try { close(); } catch { /* container creation may have failed */ }
    throw error;
  }
}
async function main() {
  assert.ok(process.argv.includes('--image') && image && !image.startsWith('--'), 'Specify --image <merged E-387 image>');
  const topology = startTopology(image);
  try {
    const { createHandler } = await import('../src/index.js');
    let now = Math.floor(Date.now() / 1000);
    const env = { NWU_CHALLENGE_TICKET_SECRET: SECRET, TURNSTILE_SECRET_KEY: 'test-only', TURNSTILE_SITE_KEY: 'test-only', TURNSTILE_HOSTNAME: 'worker.example' };
    let nonce;
    const handle = createHandler({ now: () => now, fetch: async () => Response.json({
      success: true, hostname: env.TURNSTILE_HOSTNAME, action: 'nwu_challenge', cdata: nonce,
    }) });
    const source = topology.request('/cart?item=one%20two', '127.0.0.3');
    assert.equal(source.status, 302);
    const requestToken = new URL(source.headers.location).searchParams.get('t');
    const fields = Buffer.from(requestToken.split('.')[0], 'base64url').toString().split('\n');
    assert.equal(fields[3], '127.0.0.3');
    now = Math.floor(Date.now() / 1000);
    nonce = fields[6];
    async function worker(token = requestToken) {
      nonce = Buffer.from(token.split('.')[0], 'base64url').toString().split('\n')[6];
      return handle(new Request('https://worker.example/verify', { method: 'POST',
        headers: { origin: 'https://worker.example', 'content-type': 'application/x-www-form-urlencoded', 'CF-Connecting-IP': '2001:db8::2' },
        body: new URLSearchParams({ t: token, 'cf-turnstile-response': 'test-only-token' }),
      }), env);
    }
    const response = await worker();
    assert.equal(response.status, 303);
    assert.equal(response.headers.has('set-cookie'), false);
    const callback = new URL(response.headers.get('location'));
    assert.equal(callback.origin, 'https://shop.test');
    assert.equal(callback.pathname, '/.well-known/nwu-challenge/callback');
    const callbackPath = callback.pathname + callback.search;
    const passed = topology.request(callbackPath, '127.0.0.3');
    assert.equal(passed.status, 303);
    assert.ok(passed.headers.location.endsWith('/cart?item=one%20two'));
    assert.match(passed.headers['set-cookie'], /^nwu_pass=/);
    assert.match(passed.headers['set-cookie'], /Secure/);
    assert.equal(topology.request('/cart?item=one%20two', '127.0.0.3', passed.headers['set-cookie'].split(';')[0]).status, 200);
    console.log('PASS real Lua request -> Worker (different IPv6 header) -> callback cookie -> original query');
    const rejected = (name, token, ip = '127.0.0.3') => {
      const result = topology.request('/.well-known/nwu-challenge/callback?token=' + encodeURIComponent(token), ip);
      assert.equal(result.status, 429, name);
      assert.equal(result.headers['set-cookie'], undefined, name);
      assert.equal(result.headers.location, undefined, name);
      console.log('PASS ' + name);
    };
    const token = callback.searchParams.get('token');
    rejected('same response replay', token);
    now += 1;
    const again = await worker();
    assert.equal(again.status, 303);
    const againToken = new URL(again.headers.get('location')).searchParams.get('token');
    assert.notEqual(againToken, token);
    rejected('newly signed response with consumed request nonce', againToken);
    // Use an unconsumed response so these tests cannot pass merely because of replay detection.
    const freshSource = topology.request('/fresh?query=two', '127.0.0.3');
    assert.equal(freshSource.status, 302);
    now = Math.floor(Date.now() / 1000);
    const freshResponse = await worker(new URL(freshSource.headers.location).searchParams.get('t'));
    assert.equal(freshResponse.status, 303);
    const freshCallback = new URL(freshResponse.headers.get('location'));
    const freshToken = freshCallback.searchParams.get('token');
    rejected('different origin IP', freshToken, '127.0.0.6');
    rejected('tampered signature', freshToken.slice(0, -5) + 'AAAAA');
    assert.equal(topology.request(freshCallback.pathname + freshCallback.search, '127.0.0.3').status, 303);
    console.log('PASS rejected attempts do not consume a valid unused response');
    const respFields = { kind: 'resp', exp: Math.floor(Date.now() / 1000) - 1, ip: '127.0.0.3', host: 'shop.test', path: '/cart?item=one%20two', nonce: randomUUID().replaceAll('-', '') };
    rejected('expired response', sign(respFields));
    rejected('different host', sign({ ...respFields, exp: Math.floor(Date.now() / 1000) + 60, host: 'other.test' }));
    const mappedIp = '::ffff:127.0.0.3';
    const mappedSource = topology.request('/mapped?q=one%20two', mappedIp);
    assert.equal(mappedSource.status, 302);
    const mappedTicket = new URL(mappedSource.headers.location).searchParams.get('t');
    assert.equal(Buffer.from(mappedTicket.split('.')[0], 'base64url').toString().split('\n')[3], mappedIp);
    now = Math.floor(Date.now() / 1000);
    const mappedResponse = await worker(mappedTicket);
    assert.equal(mappedResponse.status, 303);
    const mappedCallback = new URL(mappedResponse.headers.get('location'));
    assert.equal(Buffer.from(mappedCallback.searchParams.get('token').split('.')[0], 'base64url').toString().split('\n')[3], mappedIp);
    const mappedPassed = topology.request(mappedCallback.pathname + mappedCallback.search, mappedIp);
    assert.equal(mappedPassed.status, 303);
    assert.ok(mappedPassed.headers.location.endsWith('/mapped?q=one%20two'));
    assert.equal(topology.request('/mapped?q=one%20two', mappedIp, mappedPassed.headers['set-cookie'].split(';')[0]).status, 200);
    console.log('PASS real dual-stack IPv4-mapped IPv6 request and callback retain the original IP');
    console.log('8/8 roundtrip scenarios passed; cloud acceptance remains pending.');
  } finally { topology.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
