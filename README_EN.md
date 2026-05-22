# nginxWebUI · elf-express fork

> Web UI for managing nginx — security-hardened fork with GeoIP / CrowdSec / Loki / Grafana / Vue Dashboard

**繁體中文** · [README.md](./README.md)　**English** · this file

---

## What is this?

[nginxWebUI](https://github.com/cym1102/nginxWebUI) is an open-source nginx configuration UI by [cym1102](https://gitee.com/cym1102). The upstream version is feature-complete but **runs as a single container, has no observability, no security hardening, and ships dated UI**.

**This fork ([elf-express/nginxWebUI](https://github.com/elf-express/nginxWebUI)) evolves it into a production-grade deployment:**

| Aspect | Upstream cym1102 | elf-express fork |
|---|---|---|
| **Database** | SQLite (single file) | **PostgreSQL 18-alpine** (multi-container, backup-ready) |
| **Observability** | None | **Loki + Promtail + Grafana** full log/metric pipeline |
| **Security** | IP allow/deny lists only | + **CrowdSec** IDS, + **GeoIP2** country blocking, + **ASN** blocking, + **auto-fetch from URL** for multiple lists |
| **Locale** | Simplified Chinese primary | **Traditional Chinese primary** (zh-CN / zh-TW / en-US) |
| **Frontend** | Pure Layui + jQuery | + **Vue 3 partial mount** (template picker / SpecSnap inspector / **5.1.0 Dashboard**) |
| **CI/Release** | Manual jar build | **GitHub Actions** auto multi-platform image build (linux/amd64 + linux/arm64) → ghcr.io |
| **Dev workflow** | Direct push to master | **dev/master branch model**, git-tag-based release, `scripts/release.sh` automation |

> Not a replacement — **complementary**: use upstream for minimal single-host deployment; use this fork for enterprise observability and security hardening.

---

## Quick start

### Docker Compose full stack (recommended)

```bash
git clone https://github.com/elf-express/nginxWebUI.git
cd nginxWebUI
git checkout v5.0.13          # latest release
cd deploy
docker compose up -d
```

Open browser → **http://localhost:12300** → default `admin` / `Admin123`

Seven services up together:

| Service | Port | Purpose |
|---|---|---|
| **nginxwebui** | 12300:8080 / 80 / 443 | Main app |
| postgres | 5432 | Database |
| loki | 3100 | Log aggregation |
| **grafana** | 3000 | Monitoring dashboards (admin/admin) |
| promtail | — | Push nginx + app log to Loki |
| crowdsec | — | Intrusion detection (v1.7.8) |
| crowdsec-bouncer | — | nginx traffic filtering (0.5.0) |

### Stack architecture

```
┌─ nginxwebui (Solon 3.3.3 + Java 8) ──────────────────┐
│                                                       │
│  ┌─ Web UI (Layui + Vue 3 partial mount) ───────────┐ │
│  │  Protection / Reverse Proxy / Stream / Upstream  │ │
│  └──────────────────────────────────────────────────┘ │
│                       ↓ SqlHelper (custom ORM)        │
│            PostgreSQL ← cert / server / denyAllow     │
│                       ↓ ConfService generates         │
│            nginx.conf + reverse proxy + GeoIP/ASN     │
└───────────────────────────────────────────────────────┘
            ↓ access log                ↑ HTTP query
┌─ Promtail ─→ Loki ←─ Grafana Dashboard ──────────────┐
│                  ←─ nginxwebui Monitor (5.1.0)        │
└───────────────────────────────────────────────────────┘
            ↓ access log              ↑ cscli / API
┌─ CrowdSec (IDS) ──→ Bouncer ──→ nginx auth_request   │
└───────────────────────────────────────────────────────┘
```

---

## Key features

### 🛡 Security

- **IP allow/deny lists** — multiple lists, simultaneously applicable (CSV multi-select), **auto-fetch daily from URL** (SpamHaus DROP / FireHOL / Emerging Threats / IPsum / Binary Defense, etc.)
- **GeoIP2 country blocking** — default whitelist of 17 countries (CN/JP/HK/KR/SG/TH/MY/TW/VN/GB/FR/DE/GR/CA/US/MO/LA), user-customizable
- **ASN blocking** — block whole network segments by Autonomous System Number
- **CrowdSec integration** — containerized deployment, bouncer intercepts attacker IPs
- **Anti-bot certificate** — centrally managed in the "Protection" page

### 🌐 Reverse Proxy / Load Balancing

- HTTP / HTTPS / TCP / UDP fully supported, auto-generated `nginx.conf`
- TLS 1.2 / 1.3 support, Let's Encrypt auto-renewal (acme.sh DNS mode)
- Upstream load balancing with weight / backup / down settings
- **19 built-in parameter templates** (with Chinese annotations): WebSocket Proxy / Proxy Headers / Large File Upload / CORS / Rate Limit / Security Headers / GeoIP / CrowdSec auth

### 📊 Observability (major 5.1.0 enhancement coming)

- Grafana pre-configured dashboards for nginx traffic + system metrics
- Loki collects all nginx access log + nginxwebui app log
- Promtail auto-forwards
- **5.1.0 coming**: Native Vue Dashboard integrating Loki queries, 4 metric categories
  - System (CPU/Mem/Disk/Net)
  - **Security (blocked IP/country/ASN Top N, CrowdSec alerts/decisions)**
  - Traffic (RPS / status code / response time / top paths)
  - TLS (cert expiry warning / TLS version distribution)

→ [Full 5.1.0 design doc](./docs/superpowers/plans/2026-05-22-monitor-dashboard-v2.md)

### 🎨 UI

- Traditional Chinese primary, Simplified / English tri-lingual i18n (flag icon switcher)
- Reverse proxy modal single-column left-aligned, doesn't cover top header
- **shadcn-vue style** template picker (Vue 3 + custom Combobox)
- **SpecSnap Inspector** dev tool (top-right 🔍) — click UI elements to auto-capture structured metadata for AI

### 🚀 Development

- **dev / master dual-branch model**: daily dev on dev, release tags only, master = last release snapshot
- **`scripts/release.sh`** automates pom bump + commit + git tag
- **GitHub Actions** sees `v*` tag → auto multi-platform image build → push to ghcr.io
- **Dependabot** weekly scans Maven + Docker + Actions dependencies

---

## Deployment options

### A. Docker Compose (recommended, production)

See "Quick start" above. Full stack 7 services.

### B. Pure jar (minimal, development)

```bash
mvn clean package -DskipTests
java -jar -Dfile.encoding=UTF-8 \
     target/nginxWebUI-5.0.13.jar \
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
docker pull ghcr.io/elf-express/nginxwebui:5.0.13
# or :latest (always equals latest tag build)
```

Multi-platform: linux/amd64 + linux/arm64

---

## Upgrade

```bash
git pull origin master
cd deploy
docker compose pull
docker compose up -d
```

PostgreSQL schema is **CodeFirst auto-ALTER TABLE** by SqlHelper (custom ORM) — **no manual migration required**.

---

## Development guide

- [`CLAUDE.md`](./CLAUDE.md) — Complete dev environment setup, tech stack, directory structure, SqlHelper cheatsheet, Solon DI annotations, release flow
- [`docs/superpowers/plans/`](./docs/superpowers/plans/) — All design docs + implementation reports
- [`tests/e2e/`](./tests/e2e/) — Playwright E2E tests (24+ scenarios)

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
| **[v5.0.13](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.13)** | UI major overhaul: modal layout + template picker + DenyAllow CSV multi-select |
| [v5.0.12](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.12) | DenyAllow URL fetch redirect-follow + last-update column |
| [v5.0.11](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.11) | URL fetch IP auto-deduplication |
| [v5.0.10](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.10) | DenyAllow JS validation relaxed: empty IP allowed when URL set |
| [v5.0.7](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.7) | DenyAllow URL daily auto-fetch + default country whitelist + Grafana menu link |
| [v5.0.6](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.6) | ASN tab into Protection page + SpecSnap inspector + port 12300 |
| [v5.0.4](https://github.com/elf-express/nginxWebUI/releases/tag/v5.0.4) | dev/release pipeline established |

Full changelog: https://github.com/elf-express/nginxWebUI/releases

---

## Roadmap

- **v5.1.0** (in progress) — Vue Dashboard redesign: 4 metric categories + ECharts + Loki query integration
- v5.2.0 (planned) — Grafana pre-configured dashboard JSON upgrade, add alert rules
- v5.3.0 (planned) — Dashboard widget drag-sort + historical trend page

---

## License & credits

**License:** MIT

**Original author:** [cym1102](https://gitee.com/cym1102) ([gitee.com/cym1102/nginxWebUI](https://gitee.com/cym1102/nginxWebUI)) — all core features in this fork (nginx config generation, reverse proxy, acme.sh certificate, SqlHelper ORM, etc.) come from upstream.

**Fork maintainer:** [elf-express](https://github.com/elf-express) (ELF International Express)

**Issues / PRs:** https://github.com/elf-express/nginxWebUI/issues

**Upstream issues:** QQ group 560797506 (maintained by cym1102)
