# nginxWebUI · elf-express fork

> Web UI for managing nginx — security-hardened fork with GeoIP / CrowdSec

**繁體中文** · [README_TW.md](./README_TW.md)　**English** · this file

---

## What is this?

[nginxWebUI](https://github.com/cym1102/nginxWebUI) is an open-source nginx configuration UI by [cym1102](https://gitee.com/cym1102). The upstream version is feature-complete but **runs as a single container, has no observability, no security hardening, and ships dated UI**.

**This fork ([elf-express/nginxWebUI](https://github.com/elf-express/nginxWebUI)) evolves it into a production-grade deployment:**

| Aspect | Upstream cym1102 | elf-express fork |
|---|---|---|
| **Database** | SQLite (single file) | **PostgreSQL 18-alpine** (multi-container, backup-ready) |
| **Security** | IP allow/deny lists only | + **CrowdSec** IDS, + **GeoIP2** country blocking, + **ASN** blocking, + **auto-fetch from URL** for multiple lists |
| **Locale** | Simplified Chinese primary | **Traditional Chinese primary** (zh-CN / zh-TW / en-US) |
| **Frontend** | Pure Layui + jQuery | + **Vue 3 partial mount** (template picker / **Vue Dashboard**) |
| **CI/Release** | Manual jar build | **GitHub Actions** push-to-master triggered, version-gated image build (linux/amd64) → ghcr.io |
| **Dev workflow** | Direct push to master | **dev/master branch model**, master-triggered release (CI auto-tags), `scripts/release.sh` automation |

> Not a replacement — **complementary**: use upstream for minimal single-host deployment; use this fork for enterprise observability and security hardening.

---

## Quick start

### Docker Compose full stack (recommended)

```bash
git clone https://github.com/elf-express/nginxWebUI.git
cd nginxWebUI/docker          # default branch master = latest release snapshot
docker compose up -d          # image defaults to :latest, always tracks latest release
```

Open browser → **http://localhost:12300** → first launch walks you through the admin-setup wizard (no built-in default password)

Only the core two services start by default; CrowdSec is opt-in via the compose `security` profile:

| Service | Port | Purpose | Default |
|---|---|---|---|
| **nginxwebui** | 12300:8080 / 80 / 443 | Main app | ✅ |
| postgres | 5432 | Database | ✅ |
| crowdsec | — | Intrusion detection (v1.7.8) | profile `security` |
| crowdsec-bouncer | — | nginx traffic filtering (0.5.0) | profile `security` |

### Stack architecture

```
┌─ nginxwebui (Solon 3.10.7 + Java 17) ────────────────┐
│                                                       │
│  ┌─ Web UI (Layui + Vue 3 partial mount) ───────────┐ │
│  │  Protection / Reverse Proxy / Stream / Upstream  │ │
│  └──────────────────────────────────────────────────┘ │
│                       ↓ SqlHelper (custom ORM)        │
│            PostgreSQL ← cert / server / denyAllow     │
│                       ↓ ConfService generates         │
│            nginx.conf + reverse proxy + GeoIP/ASN     │
└───────────────────────────────────────────────────────┘
            ↓ access / error log
┌─ CrowdSec (IDS) ──→ Bouncer ──→ nginx auth_request   │
└───────────────────────────────────────────────────────┘
```

> For log inspection, read nginx's built-in access/error log directly (in the `nginxwebui_log` volume, also where CrowdSec reads from). Loki / Promtail / Grafana have been removed.

---

## Key features

### 🛡 Security

- **IP allow/deny lists** — managed centrally on the Protection page and **applied site-wide automatically** (whitelist overrides blacklist, zero binding steps); ships with 6 default malicious-IP feed rules, **auto-fetched daily from URL** (Spamhaus DROP / Blocklist.de / Emerging Threats / CINS Army / Feodo Tracker / GreenSnow)
- **GeoIP2 country blocking** — default whitelist of 17 countries (CN/JP/HK/KR/SG/TH/MY/TW/VN/GB/FR/DE/GR/CA/US/MO/LA), user-customizable
- **ASN blocking** — block whole network segments by Autonomous System Number
- **CrowdSec integration** — containerized deployment, bouncer intercepts attacker IPs
- **Anti-bot certificate** — centrally managed in the "Protection" page

### 🌐 Reverse Proxy / Load Balancing

- HTTP / HTTPS / TCP / UDP fully supported, auto-generated `nginx.conf`
- TLS 1.2 / 1.3 support, Let's Encrypt auto-renewal (acme.sh DNS mode)
- Upstream load balancing with weight / backup / down settings
- **19 built-in parameter templates** (with Chinese annotations): WebSocket Proxy / Proxy Headers / Large File Upload / CORS / Rate Limit / Security Headers / GeoIP / CrowdSec auth

### 📊 Observability

- nginx built-in access log / error log (in the `nginxwebui_log` volume, or under `--project.home`/`log/` in jar mode)
- System metrics page (CPU / Mem / Disk / Net, via OSHI)
- CrowdSec cscli / decisions API for the blocked list

> This fork previously shipped a full Loki + Promtail + Grafana pipeline; it was removed 2026-06-30 — nginx's built-in logs are sufficient for day-to-day inspection.

### 🎨 UI

- Traditional Chinese primary, Simplified / English tri-lingual i18n (flag icon switcher)
- Reverse proxy modal single-column left-aligned, doesn't cover top header
- **shadcn-vue style** template picker (Vue 3 + custom Combobox)

### AI assistant integration

- **nginx docs MCP server** — 969 official directive definitions served over MCP: exact lookup, full-text search, context reverse-lookup, and checking the directives inside a config draft against their documented contexts. Off by default; opt in with `--mcp.token` (see [nginx docs MCP server](#nginx-docs-mcp-server))

### 🚀 Development

- **dev / master dual-branch model**: daily dev on dev, master = last release snapshot (releases go via `release/*` branch PR → master)
- **`scripts/release.sh`** automates pom bump + commit (CI auto-tags on master push)
- **GitHub Actions** push to master → version-gated image build (linux/amd64) → ghcr.io, auto-tags `v*` + creates Release
- **Dependabot** weekly scans Maven + Docker + Actions dependencies

---

## Deployment options

### A. Docker Compose (recommended, production)

**Both images are self-built**: `nginxwebui` and `nginxwebui-crowdsec` (official CrowdSec base with config baked in — no bind-mounts). Only the core two services start by default; the IDS is opt-in via the compose `security` **profile**.

**Core only (nginxwebui + postgres) — only two files on the server:**

```bash
mkdir nginxwebui && cd nginxwebui
curl -O https://raw.githubusercontent.com/elf-express/nginxWebUI/master/docker/docker-compose.yml
curl -o .env https://raw.githubusercontent.com/elf-express/nginxWebUI/master/docker/.env.example
# Edit .env: image defaults to :latest, pin via NGINX_WEBUI_VERSION=x.y.z
docker compose up -d                      # starts only nginxwebui + postgres
```

**With CrowdSec IDS — add the compose `security` profile (config is baked into the `nginxwebui-crowdsec` image, no bind-mount needed):**

```bash
git clone https://github.com/elf-express/nginxWebUI.git && cd nginxWebUI/docker
cp .env.example .env                       # set CROWDSEC_BOUNCER_KEY (any value on first boot)
docker compose --profile security up -d
# or set COMPOSE_PROFILES=security in .env, then docker compose up -d
```

> To build the nginxwebui image from source: after clone, inside `docker/` (run `mvn clean package -DskipTests` first):
> `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build`

### B. Pure jar (minimal, development)

```bash
mvn clean package -DskipTests
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-*.jar \
     --server.port=8080 \
     --project.home=./dev-home/
```

Launch parameters:

| Parameter | Default | Description |
|---|---|---|
| `--server.port` | 8080 | Listen port |
| `--project.home` | `/home/nginxWebUI/` | Data directory (DB / cert / log) |
| `--spring.database.type` | sqlite | sqlite / postgresql / mysql |
| `--init.admin` | (empty, set via UI) | Initial admin name |
| `--init.pass` | (empty, set via UI) | Initial admin password |
| `--project.findPass` | false | true prints password and exits (rescue) |

Full parameters: [CLAUDE.md](./CLAUDE.md).

### C. Pull Docker image directly

```bash
docker pull ghcr.io/elf-express/nginxwebui:latest
# or pin a version: ghcr.io/elf-express/nginxwebui:x.y.z (:latest always equals latest tag build)
```

Platform: linux/amd64 (single-arch, not multi-platform)

---

## nginx docs MCP server

A read-only [MCP](https://modelcontextprotocol.io/) endpoint that serves the official nginx directive
reference to an AI assistant, so it answers from the documentation instead of from memory. The index is
built at startup from the 150 documentation pages bundled inside the jar — **969 directive definitions /
803 distinct names / 99 modules / 15 contexts** — with no network access at any point.

Five read-only tools: `nginx_directive` (exact lookup), `nginx_search` (full-text search),
`nginx_module` (list one module's directives), `nginx_context` (reverse lookup — what may legally appear
inside `location`, `server`, `upstream`, …), and `nginx_check_config` (check the directives *inside* a
config draft against their documented contexts).

**What `nginx_check_config` does and does not check.** It reads the config line by line, tracking braces to
know which block each line sits in, and then checks the **directive lines** — is this a real directive, and
is this context one the documentation allows it in. The lines that *open* a block are only used to track
nesting; their own legality is never judged. So a block opened in the wrong place — `if { }` written
directly under `http`, or `server { }` at the top level — is not reported, even though nginx refuses to
start on it. The brace tracking is also the reason a finding can be confident and still be wrong.
Unbalanced braces misattribute every line after the mismatch — and so does any closing brace that does not
have a line to itself, even when the braces balance. `} }`, `}}` and `} location /b {` are tracked correctly;
a trailing `}` (`listen 80; }`) is not. The caveat on findings therefore names the whole category — a closing
brace sharing a line with anything else — instead of listing particular spellings, because a list that misses
one ends up endorsing it. It also carries a check that covers the category: put every `}` on its own line, run
it again, and trust the finding only if both runs agree. And nothing reported never means the config is
correct — the tool reports only what it is certain of, and deliberately stays silent everywhere else.

### Turning it on

**The endpoint is off by default.** It exists only when the app is started with `--mcp.token=<token>`, and
that same token is the credential clients must present. Without the flag, `/mcp` returns `404` and the
documentation index is never even parsed — an existing deployment that upgrades sees no change whatsoever.

Choose any hard-to-guess string as the token (`openssl rand -hex 16` produces a suitable one).

**Running the jar:**

```bash
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-*.jar \
     --server.port=8080 \
     --project.home=./dev-home/ \
     --mcp.token=REPLACE_WITH_YOUR_TOKEN
```

**Docker Compose:** append the same flag to `BOOT_OPTIONS` in `docker/docker-compose.yml`, then
`docker compose up -d`:

```yaml
    environment:
      # keep the flags already on this line, just add --mcp.token at the end
      - BOOT_OPTIONS=--spring.database.type=postgresql ... --mcp.token=REPLACE_WITH_YOUR_TOKEN
```

> **Why the launch flag rather than an environment variable.** The setting key is `mcp.token`, and the dot
> makes it an invalid POSIX shell identifier: `export mcp.token=...` is a *syntax error* in sh/bash. The key
> can still be supplied as an environment variable wherever no shell parses it — a Compose `environment:`
> entry, or `docker run -e mcp.token=...` — but the launch flag works everywhere, so prefer it.

Once enabled, `/mcp` requires the header `Authorization: Bearer <token>`; a missing or wrong token gets
`401`. The transport is streamable-stateless HTTP, so a plain `POST` returns plain JSON — there is no
session handshake and no SSE framing.

To confirm it came up, ask it for its tool list:

```bash
curl -s -X POST http://localhost:8080/mcp \
     -H 'Accept: application/json, text/event-stream' \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer REPLACE_WITH_YOUR_TOKEN' \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

A working endpoint answers with a JSON object listing the five tools.

> **The `Accept` header is mandatory and must name both types.** The MCP streamable spec requires a client
> to accept `application/json` *and* `text/event-stream`; omit the header, or send only `application/json`,
> and the request is rejected with **`400` and a completely empty body** — no message explaining why. An MCP
> client sends the right header on its own, so this only bites when you are testing by hand with curl.

### Pointing a client at it

[`.mcp.json`](./.mcp.json) in the repository root registers the server for Claude Code. It reads two
environment variables, so no token is ever committed:

| Variable | Default | Notes |
|---|---|---|
| `NGINX_WEBUI_MCP_TOKEN` | none — **required** | Must match the value passed to `--mcp.token` |
| `NGINX_WEBUI_MCP_URL` | `http://localhost:12300/mcp` | `12300` is only the port Docker Compose publishes. Running the jar directly, use whatever you passed to `--server.port` — typically `http://localhost:8080/mcp` |

Both names are ordinary shell identifiers, so export them the usual way before starting the client:

```bash
export NGINX_WEBUI_MCP_TOKEN=REPLACE_WITH_YOUR_TOKEN
export NGINX_WEBUI_MCP_URL=http://localhost:8080/mcp   # only if you are not on the Compose port
```

---

## Upgrade

```bash
git pull origin master
cd docker
docker compose pull
docker compose up -d
```

> **2026-06-30 behavior change (monitoring removed):** Loki / Promtail / Grafana have been completely removed from the project (nginx's built-in access/error log is sufficient; CrowdSec reads the nginx log volume directly, no Loki intermediary). If you were running the monitoring profile: after upgrade `docker compose up -d` no longer starts these three services, and the now-orphaned `nginxwebui_loki_data` / `nginxwebui_grafana_data` volumes can be removed with `docker volume rm`. CrowdSec activation via `--profile security` is unchanged.

PostgreSQL schema is **CodeFirst auto-ALTER TABLE** by SqlHelper (custom ORM) — **no manual migration required**.

---

## Development guide

- [`CLAUDE.md`](./CLAUDE.md) — Complete dev environment setup, tech stack, directory structure, SqlHelper cheatsheet, Solon DI annotations, release flow
- [`docs/superpowers/plans/`](./docs/superpowers/plans/) — All design docs + implementation reports
- [`tests/e2e/`](./tests/e2e/) — Playwright E2E tests (31 specs)

```bash
npm install && npx playwright install --with-deps chromium
mvn clean package -DskipTests
npm test                      # E2E (headed)
npm run test:fast             # E2E (headless / CI)
```

---

## Recent releases

| Tag | Highlight |
|---|---|
| **[v5.2.8](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.8)** | nginx documentation localised with code-safe tooling: 822 code samples converted from quote blocks to fenced blocks, guarded by a character-level content fingerprint; 138 machine-translation defects fixed |
| [v5.2.7](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.7) | Global http-param panel moved to the http config page; header GeoIP status laid out as a 2x2 grid |
| [v5.2.6](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.6) | Site-wide save deadlock resolved (`nginx -t` precheck timeout) + DenyAllow redesigned to apply globally, no per-server binding |
| [v5.2.5](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.5) | Security fixes (CodeQL: zip-slip path traversal / DOM XSS / sensitive-log) + dependency upgrades |
| [v5.2.4](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.4) | CI auto-creates GitHub Release (no more manual/stale releases) |
| [v5.2.0](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.0) | GeoIP DB module: header shows Country/City/ASN MMDB build dates + manual download |
| [v5.1.1](https://github.com/elf-express/nginxWebUI/releases/tag/v5.1.1) | Self-built sidecar baked images (config baked in) + CI matrix build; now **2 self-built images** (nginxwebui + nginxwebui-crowdsec) |
| [v5.1.0](https://github.com/elf-express/nginxWebUI/releases/tag/v5.1.0) | Self-contained sidecar baked images (config baked in) + `deploy/` renamed `docker/` + compose drops init.* defaults + brand logo upload |
| [v5.0.13](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.13) | UI major overhaul: modal layout + template picker + DenyAllow CSV multi-select |
| [v5.0.12](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.12) | DenyAllow URL fetch redirect-follow + last-update column |
| [v5.0.11](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.11) | URL fetch IP auto-deduplication |
| [v5.0.10](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.10) | DenyAllow JS validation relaxed: empty IP allowed when URL set |
| [v5.0.7](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.7) | DenyAllow URL daily auto-fetch + default country whitelist + Grafana menu link |
| [v5.0.6](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.6) | ASN tab into Protection page + SpecSnap inspector + port 12300 |
| [v5.0.4](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.4) | dev/release pipeline established |

Full changelog: https://github.com/elf-express/nginxWebUI/releases

---

## License & credits

**License:** MIT

**Original author:** [cym1102](https://gitee.com/cym1102) ([gitee.com/cym1102/nginxWebUI](https://gitee.com/cym1102/nginxWebUI)) — all core features in this fork (nginx config generation, reverse proxy, acme.sh certificate, SqlHelper ORM, etc.) come from upstream.

**Fork maintainer:** [elf-express](https://github.com/elf-express) (ELF International Express)

**Issues / PRs:** https://github.com/elf-express/nginxWebUI/issues

**Upstream issues:** QQ group 560797506 (maintained by cym1102)
