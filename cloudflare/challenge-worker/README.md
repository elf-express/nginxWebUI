# External challenge Worker
[繁體中文](README_TW.md)

Worker checks Turnstile and signs a 60-second response ticket. The original nginx callback issues the IP-bound nwu_pass cookie and returns to the signed path. No cookie key, database, KV or runtime dependency is used.

## Configure and deploy
Use Node 24 and the committed lockfile (Wrangler **4.152.0**). All commands start at the repository root, then stay in the Worker directory. Dry-run, secrets and deployment share the same wrangler.toml and fixed identity:
```toml
name = "nginxwebui-challenge"
main = "src/index.js"
compatibility_date = "2026-10-24"
```

In Cloudflare create a **Managed** Turnstile widget restricted to your actual Worker hostname (custom domain or workers.dev). In [vars], set TURNSTILE_SITE_KEY to its public site key and TURNSTILE_HOSTNAME to that hostname without scheme/path/port. Use the Worker hostname, not the origin hostname. Cloudflare chooses Managed interaction.

Eddie or an explicitly authorized deployment session performs login, secret configuration and deployment. Local build is only a dry-run:
```sh
cd cloudflare/challenge-worker
npm ci
npm test
npm run build
npx wrangler login
npx wrangler secret put NWU_CHALLENGE_TICKET_SECRET
npx wrangler secret put TURNSTILE_SECRET_KEY
npx wrangler deploy
```

At the prompts enter the existing origin ticket secret, then the Turnstile widget secret. Keep both out of source, command arguments, logs and screenshots. NWU_CHALLENGE_SECRET is the origin cookie key and must stay at the origin; this Worker never uses it. Copy .dev.vars.example to ignored .dev.vars only for local development. Empty required values fail closed with 503.

## Connect and accept
Set the origin's external URL to https://YOUR_WORKER_HOST/?style=auto or https://YOUR_WORKER_HOST/?style=checkbox. Auto executes immediately; checkbox waits for the page's labeled checkbox before executing the Managed widget. Both send action: "nwu_challenge" and cData: nonce. Siteverify must return the matching action/cdata and hostname.

First open both URLs without a ticket: preview cannot submit and does not run Turnstile. Then use a real HTTPS origin with a captcha decision and follow: original page with query → Worker → real Turnstile → origin callback → nwu_pass cookie → original page. Record deployment version/time, both styles and masked evidence. Account access, actual hostname, matching ticket secret and real widget are prerequisites. Mocks, test keys and dry-run are **not cloud acceptance**.

The origin nginx must see HTTPS itself. TLS termination forwarding HTTP leaves ngx.var.scheme=http and the origin still returns 429. For a Cloudflare-proxied origin, the administrator must verify the existing /etc/nginx/geoip/realip.conf include trusts only Cloudflare ranges and uses real_ip_header CF-Connecting-IP. Do not duplicate that include or trust arbitrary proxies. See [origin operations](../../docs/crowdsec-challenge.md). The Worker preserves the signed origin IP even if its connection uses a different IPv4/IPv6 address.

## Tests and recovery
Run from the repository root:
```sh
node cloudflare/challenge-worker/scripts/build-messages.cjs --check
node cloudflare/challenge-worker/test/nginx-roundtrip.cjs --image nginxwebui:e387
npm run test:fast -- challenge-worker.spec.js
```

The Lua test uses an image containing merged E-387 Lua, creates only its own temporary Docker container, exposes no host ports, uses test secrets and removes the container on completion. Browser tests stub official API requests and inject fake Siteverify only in the test adapter; production has no test bypass. The existing Playwright harness requires a built Java jar.

Translations come from the three src/main/resources/messages*.properties files. After editing keys, run the dictionary builder without --check and commit src/messages.generated.js. Check mode never writes and rejects missing keys.

429 means invalid/expired/tampered ticket, failed/duplicate Turnstile, hostname/action/cdata mismatch or rejected form submission. Exact Origin is required; no-referrer's Origin: null is accepted only with Sec-Fetch-Site: same-origin AND Sec-Fetch-Mode: navigate. Unsupported browsers fail closed. 503 means missing config or Siteverify transport/HTTP/JSON failure (5-second timeout, no automatic retry). Return to the origin for a fresh expired ticket; widget failures offer retry. There is no automatic fallback to builtin mode.

Coordinate ticket-secret rotation across origin and Worker; outstanding old tickets fail immediately. Rotate Turnstile's secret with its widget and update the matching Worker secret. Do not reset the origin cookie key. Eddie can recover by rolling back to a known Worker version in Cloudflare or explicitly switching the origin to builtin mode and applying configuration. Verify secret compatibility after rollback: code rollback does not replace coordinated key rotation.

[Wrangler commands](https://developers.cloudflare.com/workers/wrangler/commands/), [Turnstile setup](https://developers.cloudflare.com/turnstile/get-started/widget-management/dashboard/), [Worker rollbacks](https://developers.cloudflare.com/workers/versions-and-deployments/rollbacks/).
