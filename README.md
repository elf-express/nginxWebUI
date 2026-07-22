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
| **[v5.2.5](https://github.com/elf-express/nginxWebUI/releases/tag/v5.2.5)** | Security fixes (CodeQL: zip-slip path traversal / DOM XSS / sensitive-log) + dependency upgrades |
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
