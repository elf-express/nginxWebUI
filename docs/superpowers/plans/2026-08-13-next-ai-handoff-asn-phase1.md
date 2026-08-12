# Handoff — ASN Phase 1 on `dev` (for next AI)

**Date:** 2026-08-13  
**Branch:** `dev` @ `74091b90` (fast-forward from `feat/fixbug`)  
**Status:** Phase 1 **merged locally** into `dev`. Verified unit + package + E2E 35 after merge.

## What shipped (Phase 1)

- ipverse **AsMeta** full catalog + search/sync (`AsnSourceUrls` — all source URLs centralized)
- **AsBlockIntent** + profiles `light|manual|strict` (default light)
- CrowdSec range ban client; Z switch to light (revoke-before-profile)
- Protection UI: catalog, intents, banners (phase1 + bulk warn)
- AsnRule nginx map **off by default** (`asn.nginxMapEnabled=false`)
- Specs/plans under `docs/superpowers/specs/` and `plans/`

## Product boundary

| In scope (ship) | Out of scope (post-merge) |
|-----------------|---------------------------|
| Light + catalog/API | Streaming full JSON sync + batch upsert |
| Manual **single** ASN push (experimental) | Async push job + progress for large ASNs |
| Strict suggest **capped at 50** | Production bulk Strict |
| CS whitelist UX | Revoke residual re-scan hardening |

## Key docs

1. `docs/superpowers/specs/2026-08-12-ipverse-asn-phase1-closure.md` — phase boundary  
2. `docs/superpowers/specs/2026-08-12-ipverse-asn-crowdsec-design.md` — design  
3. `docs/superpowers/plans/2026-08-12-ipverse-asn-crowdsec-plan.md` — implementation plan  

## Suggested next work

1. Push `dev` to `origin/dev` if remote should match (local is ahead 16).  
2. Post-merge hardening list in phase1-closure.  
3. Do **not** market Strict bulk as production-grade yet.

## Verify (already green on this tree)

```bash
mvn -q "-Dtest=AsnSourceUrlsTest,AsnMetaParseTest,AsnBlockServiceTest,CrowdSecClientTest" test
mvn -q package -DskipTests
npx playwright test --config=tests/e2e/playwright.fast.config.js tests/e2e/35-asn-catalog-profile.spec.js
```

## Note

Unrelated untracked `docs/nginxdocumentation/*.mp4` — ignore / do not commit.
