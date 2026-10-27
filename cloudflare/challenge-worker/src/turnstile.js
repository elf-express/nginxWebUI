export class ServiceError extends Error {
  constructor() { super('Verification service unavailable'); this.name = 'ServiceError'; }
}
export async function validateTurnstile(token, nonce, env, fetchImpl) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const response = await fetchImpl('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
      method: 'POST', body: new URLSearchParams({ secret: env.TURNSTILE_SECRET_KEY, response: token }),
      signal: controller.signal,
    });
    if (response.status !== 200) throw new ServiceError();
    const result = await response.json();
    return result?.success === true && typeof result.hostname === 'string' &&
      result.hostname.toLowerCase() === env.TURNSTILE_HOSTNAME.toLowerCase() &&
      result.action === 'nwu_challenge' && result.cdata === nonce;
  } catch { throw new ServiceError(); }
  finally { clearTimeout(timer); }
}
