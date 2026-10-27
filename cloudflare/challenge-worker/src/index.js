import { verifyRequestTicket, signResponseTicket, callbackUrl, TicketError } from './tickets.js';
import { validateTurnstile, ServiceError } from './turnstile.js';

const required = ['NWU_CHALLENGE_TICKET_SECRET', 'TURNSTILE_SECRET_KEY', 'TURNSTILE_SITE_KEY', 'TURNSTILE_HOSTNAME'];
const headers = { 'cache-control': 'no-store', 'referrer-policy': 'no-referrer', 'content-type': 'text/html; charset=utf-8' };
const escape = text => String(text).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function reply(status, text) { return new Response(text, { status, headers }); }
async function readForm(request) {
  const reader = request.body?.getReader();
  if (!reader) return new URLSearchParams();
  let length = 0;
  const chunks = [];
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > 16384) { await reader.cancel(); throw new RangeError('Body too large'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return new URLSearchParams(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
}
export function createHandler(deps = {}) {
  const fetchImpl = deps.fetch ?? globalThis.fetch;
  const now = deps.now ?? (() => Math.floor(Date.now() / 1000));
  return async (request, env) => {
    const url = new URL(request.url);
    if (!['/', '/verify'].includes(url.pathname)) return reply(404, 'Not found');
    if ((url.pathname === '/' && request.method !== 'GET') ||
        (url.pathname === '/verify' && request.method !== 'POST')) return reply(405, 'Method not allowed');
    if (!required.every(key => typeof env[key] === 'string' && env[key].trim())) return reply(503, 'Service unavailable');
    try {
      if (request.method === 'GET') {
        const tokens = url.searchParams.getAll('t');
        if (!tokens.length) return reply(200, '<h1>Challenge preview</h1><button disabled>Verify</button>');
        if (tokens.length !== 1) throw new TicketError();
        await verifyRequestTicket(tokens[0], env.NWU_CHALLENGE_TICKET_SECRET, now());
        return reply(200, '<h1>Challenge</h1><form method="post" action="/verify"><input type="hidden" name="t" value="' +
          escape(tokens[0]) + '"><button type="submit">Verify</button></form>');
      }
      if (request.headers.get('origin') !== url.origin) throw new TicketError();
      if (request.headers.get('content-type')?.split(';')[0].trim().toLowerCase() !== 'application/x-www-form-urlencoded')
        return reply(415, 'Unsupported media type');
      const form = await readForm(request);
      for (const key of new Set(form.keys())) if (form.getAll(key).length !== 1) throw new TicketError();
      const token = form.get('t');
      const challenge = form.get('cf-turnstile-response');
      if (!challenge || challenge.length > 2048) throw new TicketError();
      const ticket = await verifyRequestTicket(token, env.NWU_CHALLENGE_TICKET_SECRET, now());
      if (!await validateTurnstile(challenge, ticket.nonce, env, fetchImpl)) throw new TicketError();
      const finalNow = now();
      await verifyRequestTicket(token, env.NWU_CHALLENGE_TICKET_SECRET, finalNow);
      const responseToken = await signResponseTicket(ticket, env.NWU_CHALLENGE_TICKET_SECRET, finalNow);
      return new Response(null, { status: 303, headers: { ...headers, location: callbackUrl(ticket.host, responseToken) } });
    } catch (error) {
      if (error instanceof RangeError) return reply(413, 'Body too large');
      if (error instanceof ServiceError) return reply(503, 'Service unavailable');
      return reply(429, 'Verification failed');
    }
  };
}
export default { fetch: createHandler() };
