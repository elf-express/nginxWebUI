import { verifyRequestTicket, signResponseTicket, callbackUrl, TicketError } from './tickets.js';
import { validateTurnstile, ServiceError } from './turnstile.js';
import { renderPage, chooseLocale, clientScript, styleSheet } from './page.js';

const required = ['NWU_CHALLENGE_TICKET_SECRET', 'TURNSTILE_SECRET_KEY', 'TURNSTILE_SITE_KEY', 'TURNSTILE_HOSTNAME'];
const headers = {
  'cache-control': 'no-store', 'referrer-policy': 'no-referrer', 'content-type': 'text/html; charset=utf-8',
  'x-content-type-options': 'nosniff',
  'content-security-policy': "default-src 'none'; script-src 'self' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; style-src 'self'; connect-src 'self' https://challenges.cloudflare.com; base-uri 'none'; frame-ancestors 'none'; form-action 'self' https:",
};
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
  return async (request, env = {}) => {
    const url = new URL(request.url);
    const model = { ticket: null, nonce: null, sitekey: env.TURNSTILE_SITE_KEY ?? '',
      style: url.searchParams.get('style') === 'checkbox' ? 'checkbox' : 'auto',
      locale: chooseLocale(request.headers.get('accept-language') ?? ''), ip: '', time: new Date(now() * 1000).toISOString(), error: null };
    const reply = (status, error = null) => new Response(renderPage({ ...model, error }), { status, headers });
    if (!['/', '/verify', '/client.js', '/style.css'].includes(url.pathname)) return reply(404, 'invalidTicket');
    if (request.method !== (url.pathname === '/verify' ? 'POST' : 'GET')) return reply(405, 'invalidTicket');
    if (url.pathname === '/client.js' || url.pathname === '/style.css')
      return new Response(url.pathname === '/client.js' ? clientScript : styleSheet, { headers: { ...headers,
        'content-type': url.pathname === '/client.js' ? 'application/javascript; charset=utf-8' : 'text/css; charset=utf-8' } });
    if (!required.every(key => typeof env[key] === 'string' && env[key].trim())) return reply(503, 'serviceUnavailable');
    const retain = (token, ticket) => Object.assign(model, { ticket: token, nonce: ticket.nonce, ip: ticket.ip });
    try {
      if (request.method === 'GET') {
        const tokens = url.searchParams.getAll('t');
        if (!tokens.length) return reply(200);
        if (tokens.length !== 1) throw new TicketError();
        retain(tokens[0], await verifyRequestTicket(tokens[0], env.NWU_CHALLENGE_TICKET_SECRET, now()));
        return reply(200);
      }
      // no-referrer makes a browser form navigation send Origin:null. Fetch Metadata still proves same-origin.
      const origin = request.headers.get('origin');
      const sameOriginForm = origin === 'null' && request.headers.get('sec-fetch-site') === 'same-origin' &&
        request.headers.get('sec-fetch-mode') === 'navigate';
      if (origin !== url.origin && !sameOriginForm) throw new TicketError();
      if (request.headers.get('content-type')?.split(';')[0].trim().toLowerCase() !== 'application/x-www-form-urlencoded')
        return reply(415, 'invalidTicket');
      const form = await readForm(request);
      for (const key of new Set(form.keys())) if (form.getAll(key).length !== 1) throw new TicketError();
      const token = form.get('t');
      const challenge = form.get('cf-turnstile-response');
      if (!challenge || challenge.length > 2048) throw new TicketError();
      const ticket = await verifyRequestTicket(token, env.NWU_CHALLENGE_TICKET_SECRET, now());
      retain(token, ticket);
      if (!await validateTurnstile(challenge, ticket.nonce, env, fetchImpl)) throw new TicketError();
      const finalNow = now();
      await verifyRequestTicket(token, env.NWU_CHALLENGE_TICKET_SECRET, finalNow);
      const responseToken = await signResponseTicket(ticket, env.NWU_CHALLENGE_TICKET_SECRET, finalNow);
      return new Response(null, { status: 303, headers: { ...headers, location: callbackUrl(ticket.host, responseToken) } });
    } catch (error) {
      if (error instanceof RangeError) return reply(413, 'invalidTicket');
      if (error instanceof ServiceError) return reply(503, 'serviceUnavailable');
      return reply(429, 'invalidTicket');
    }
  };
}
export default { fetch: createHandler() };
