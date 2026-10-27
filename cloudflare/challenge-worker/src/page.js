import { messages } from './messages.generated.js';
export const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
export function chooseLocale(header = '') {
  const preferences = header.split(',').map((part, order) => {
    const [tag, ...params] = part.trim().split(';');
    const q = params.find(p => p.trim().startsWith('q='));
    return { tag: tag.toLowerCase(), q: q ? Number(q.trim().slice(2)) : 1, order };
  }).filter(p => p.q > 0 && p.q <= 1).sort((a, b) => b.q - a.q || a.order - b.order);
  for (const { tag } of preferences) {
    if (/^zh-(tw|hk|mo|hant)(-|$)/.test(tag)) return 'zh-TW';
    if (/^zh($|-)/.test(tag)) return 'zh-CN';
    if (/^en($|-)/.test(tag)) return 'en';
  }
  return 'en';
}
export function renderPage(model) {
  const m = messages[model.locale] ?? messages.en;
  const e = escapeHtml;
  const t = key => e(m[key]);
  const active = Boolean(model.ticket && model.nonce);
  const retryUrl = active ? '/?style=' + model.style + '&t=' + encodeURIComponent(model.ticket) : '/';
  return `<!doctype html>
<html lang="${e(model.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${t('challengeStr.pageTitle')}</title><link rel="stylesheet" href="/style.css">
<script src="/client.js" defer></script>
${active && !model.error ? '<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&amp;onload=nwuTurnstileReady" defer></script>' : ''}
</head><body><main><h1>${t('challengeStr.heading')}</h1>
<p>${t(model.style === 'checkbox' ? 'challengeStr.checkboxHint' : 'challengeStr.autoHint')}</p>
${!active && !model.error ? '<p>' + t('challengeWorkerStr.preview') + '</p>' : ''}
<form id="challenge" method="post" action="/verify?style=${e(model.style)}"
 data-nonce="${e(model.nonce)}" data-sitekey="${e(model.sitekey)}" data-style="${e(model.style)}"
 data-active="${active && !model.error}" data-retry="${e(retryUrl)}"
 data-failure="${t('challengeStr.verifyFailedBody')}">
<input type="hidden" name="t" value="${e(model.ticket)}">
<input type="hidden" name="cf-turnstile-response" value="">
${model.style === 'checkbox' ? '<label><input id="consent" type="checkbox"' + (!active || model.error ? ' disabled' : '') + '>' + t('challengeWorkerStr.checkboxLabel') + '</label>' : ''}
<div id="widget"></div>
<p id="error" role="alert"${model.error ? '' : ' hidden'}>${model.error ? t('challengeWorkerStr.' + model.error) : ''}</p>
<button id="retry" type="button"${model.error ? '' : ' hidden'}>${t('challengeWorkerStr.retry')}</button>
</form><noscript>${t('challengeStr.noscript')}</noscript>
<footer>${t('challengeStr.yourIp')}: ${e(model.ip)} · ${t('challengeStr.time')}: <time>${e(model.time)}</time></footer>
</main></body></html>`;
}
// Served locally as /client.js. No code from request fields is evaluated.
export const clientScript = `(() => {
  const form = document.querySelector('#challenge');
  if (!form) return;
  const error = document.querySelector('#error'), retry = document.querySelector('#retry');
  retry.addEventListener('click', () => { location.href = form.dataset.retry; });
  if (form.dataset.active !== 'true') return;
  let ready = false, started = false, timer;
  function failed() {
    clearTimeout(timer);
    error.textContent = form.dataset.failure;
    error.hidden = false;
    retry.hidden = false;
  }
  function start() {
    if (!ready || started) return;
    started = true;
    try {
      const id = turnstile.render(document.querySelector('#widget'), {
        sitekey: form.dataset.sitekey, action: 'nwu_challenge', cData: form.dataset.nonce,
        execution: 'execute', 'response-field': false,
        callback: token => {
          clearTimeout(timer);
          form.elements['cf-turnstile-response'].value = token;
          form.requestSubmit();
        },
        'error-callback': failed, 'expired-callback': failed, 'timeout-callback': failed
      });
      turnstile.execute(id);
    } catch { failed(); }
  }
  window.nwuTurnstileReady = () => {
    ready = true;
    clearTimeout(timer);
    if (form.dataset.style === 'auto' || document.querySelector('#consent')?.checked) start();
  };
  document.querySelector('#consent')?.addEventListener('change', event => { if (event.target.checked) start(); });
  timer = setTimeout(failed, 10000);
})();`;
export const styleSheet = `body{margin:0;background:#f5f6f8;color:#17202a;font:16px/1.6 system-ui,sans-serif}
main{max-width:40rem;margin:8vh auto;padding:2rem;background:white;border-radius:.75rem}
h1{font-size:1.6rem}label,button{cursor:pointer}input[type=checkbox]{width:1.2rem;height:1.2rem;margin-right:.5rem}
button{padding:.6rem 1rem;font:inherit}footer{margin-top:2rem;font-size:.85rem;color:#485360;overflow-wrap:anywhere}
[role=alert]{color:#a11919}noscript{display:block} :focus-visible{outline:3px solid #245fcd;outline-offset:3px}
@media(max-width:44rem){main{margin:1rem;padding:1.2rem}}`;
