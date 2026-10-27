// Wire-compatible with docker/lua/nwu_challenge_policy.lua (nwu1, HMAC-SHA1).
const encoder = new TextEncoder();
const decoder = new TextDecoder('utf-8', { fatal: true });
const PREFIX = '/.well-known/nwu-challenge/';
export class TicketError extends Error {
  constructor() { super('Invalid handoff ticket'); this.name = 'TicketError'; }
}
const invalid = () => { throw new TicketError(); };
function b64(bytes) {
  return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}
function unb64(text) {
  if (!/^[A-Za-z0-9_-]+$/.test(text) || text.length % 4 === 1) invalid();
  try {
    const bytes = Uint8Array.from(atob(text.replaceAll('-', '+').replaceAll('_', '/')), c => c.charCodeAt(0));
    if (b64(bytes) !== text) invalid();
    return bytes;
  } catch { invalid(); }
}
function validHost(host) {
  if (typeof host !== 'string' || !host || host.length > 253) return false;
  if (/^\[[0-9a-fA-F:]+\]$/.test(host)) {
    try { return new URL('https://' + host + '/').hostname === host.toLowerCase(); } catch { return false; }
  }
  return /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$/i.test(host);
}
function validIp(ip) {
  if (/^(?:\d{1,3}\.){3}\d{1,3}$/.test(ip)) return ip.split('.').every(n => Number(n) <= 255);
  if (!/^[a-fA-F0-9:]+$/.test(ip) || !ip.includes(':')) return false;
  try { new URL('https://[' + ip + ']/'); return true; } catch { return false; }
}
async function key(secret) {
  if (typeof secret !== 'string' || !secret) invalid();
  return crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-1' }, false, ['sign', 'verify']);
}
export function safeReturn(path) {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') ||
      encoder.encode(path).length > 2048 || /[\x00-\x1f\x7f\\]/.test(path) || path.startsWith(PREFIX)) return '/';
  return path;
}
export async function verifyRequestTicket(token, secret, now) {
  try {
    if (typeof token !== 'string' || token.length > 4096 || !Number.isSafeInteger(now)) invalid();
    const parts = token.split('.');
    if (parts.length !== 2) invalid();
    const bytes = unb64(parts[0]), sig = unb64(parts[1]);
    if (sig.length !== 20 || !await crypto.subtle.verify('HMAC', await key(secret), sig, bytes)) invalid();
    const fields = decoder.decode(bytes).split('\n');
    if (fields.length !== 7 || fields[0] !== 'nwu1' || fields[1] !== 'req' ||
        !/^\d+$/.test(fields[2]) || fields.some(v => !v || /[\r\x00]/.test(v))) invalid();
    const [, kind, expiry, ip, host, path, nonce] = fields;
    const exp = Number(expiry);
    if (!Number.isSafeInteger(exp) || exp < now || exp > now + 300 ||
        !validIp(ip) || !validHost(host) || !/^[a-fA-F0-9]{32}$/.test(nonce)) invalid();
    return { kind, exp, ip, host, path, nonce };
  } catch { invalid(); }
}
export async function signResponseTicket(req, secret, now) {
  if (!Number.isSafeInteger(now) || !validHost(req.host) || !validIp(req.ip) ||
      !/^[a-fA-F0-9]{32}$/.test(req.nonce)) invalid();
  const payload = ['nwu1', 'resp', now + 60, req.ip, req.host, safeReturn(req.path), req.nonce].join('\n');
  const bytes = encoder.encode(payload);
  const sig = new Uint8Array(await crypto.subtle.sign('HMAC', await key(secret), bytes));
  return b64(bytes) + '.' + b64(sig);
}
export function callbackUrl(host, token) {
  if (!validHost(host)) invalid();
  return 'https://' + host + PREFIX + 'callback?token=' + encodeURIComponent(token);
}
