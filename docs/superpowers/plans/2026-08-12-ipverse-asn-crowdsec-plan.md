# ipverse ASN Catalog + CrowdSec Block Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a full-volume ipverse ASN metadata catalog, mutual-exclusive protection profiles (light/manual/strict), and CrowdSec-first ASN range bans (ipverse/tools path), with intentional small-IP auto-ops and safe defaults.

**Architecture:** `AsMeta` holds full as-metadata (normalized columns only). `AsBlockIntent` holds rare deliberate big-block intents with a status machine. Sync never touches intents. Enforcement is CrowdSec LAPI (`scope=ip` auto, `scope=range` for ASN). nginx is enforced by bouncer; WebUI reads LAPI + DB. Profile setting `protection.profile` is a single radio of three values. Prefixes are fetched on push only, never full-catalog stored.

**Tech Stack:** Java 17 + Solon 3.x, SqlHelper ORM (SQLite/PostgreSQL), Hutool HTTP, Freemarker + Layui + jQuery, Playwright E2E, existing CrowdSec LAPI integration.

**Spec:** `docs/superpowers/specs/2026-08-12-ipverse-asn-crowdsec-design.md`

## Implementation status (Phase 1 closed 2026-08-12)

| Scope | Status |
|-------|--------|
| Light + AsMeta catalog/API + profiles + Z + CS client + UI + E2E smoke | **Done — mergeable** |
| Manual single-ASN push | Experimental (warn banner) |
| Strict bulk / production-grade range push | **Post-merge** — see `docs/superpowers/specs/2026-08-12-ipverse-asn-phase1-closure.md` |

## Global Constraints

- **Do not** implement dual primary engines (`$blocked_asn` map + CrowdSec range) as the product default.
- **Do not** store all as-ip-blocks CIDRs for ~120k ASNs in DB.
- **Do not** dual-write metadata as columns + JSON blob.
- **Do not** auto-ban all `category=hosting` without user confirmation.
- Default profile is **`light`**. Code for manual/strict must ship ready but not force enable.
- User-facing strings: update **all three** `messages.properties` / `messages_zh_TW.properties` / `messages_en_US.properties` (ISO-8859-1 `\uXXXX` for CJK).
- Leave unrelated uncommitted work alone; do not touch `UpdateUtils.java` or `select_template.js` unless a task explicitly requires it.
- Branch: `feat/fixbug` (or current feature branch).
- Reason tag format is exact: `nginxwebui:as-ban:AS{asn}` where `{asn}` is digits only (no spaces).
- **All external source URLs must come from `AsnSourceUrls` (Task 0).** No hardcoded ad-hoc GitHub paths; do **not** use legacy `ipverse/asn-ip` paths.

## Source URLs (mandatory — copy into code, do not omit)

Canonical reference also in spec §11. Runtime constants class: `com.cym.utils.AsnSourceUrls`.

| Constant | Exact URL / template | Used by |
|----------|----------------------|---------|
| `LENS_HOME` | `https://lens.ipverse.net/` | UI help link only |
| `REPO_AS_METADATA` | `https://github.com/ipverse/as-metadata` | UI/docs link |
| `REPO_AS_IP_BLOCKS` | `https://github.com/ipverse/as-ip-blocks` | UI/docs link |
| `REPO_TOOLS` | `https://github.com/ipverse/tools` | UI/docs link |
| `REPO_TOOLS_CROWDSEC_README` | `https://github.com/ipverse/tools/blob/main/crowdsec/README.md` | UI/docs link |
| `REPO_COUNTRY_IP_BLOCKS` | `https://github.com/ipverse/country-ip-blocks` | docs only (not v1 runtime) |
| `REPO_GEO_IP_BLOCKS` | `https://github.com/ipverse/geo-ip-blocks` | docs only (not v1 runtime) |
| `REPO_AS_OVERLAY` | `https://github.com/ipverse/as-overlay` | docs only |
| **`META_JSON_URL`** | `https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json` | **AsMeta full sync (primary)** |
| **`META_CSV_URL`** | `https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv` | AsMeta fallback / optional light sync |
| **`prefixIpv4Url(asn)`** | `https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/ipv4-aggregated.txt` | push big-block |
| **`prefixIpv6Url(asn)`** | `https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/ipv6-aggregated.txt` | push big-block |
| `prefixAggregatedJsonUrl(asn)` | `https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/{asn}/aggregated.json` | optional |
| `AS_IP_BLOCKS_TARBALL` | `https://github.com/ipverse/as-ip-blocks/releases/latest/download/as-ip-blocks.tar.gz` | not default ingest; disaster/docs only |

**Legacy (FORBIDDEN in new code):**  
`https://raw.githubusercontent.com/ipverse/asn-ip/master/as/...` — old repo name; tools scripts may still show it; **use `as-ip-blocks` only**.

**HTTP:** UA `nginxWebUI/AsnMeta-sync` / `nginxWebUI/AsnBlock`; redirects ≤5; JSON sync timeout ≥120s; prefix timeout ≥30s.

## File map

| File | Responsibility |
|------|----------------|
| `src/main/java/com/cym/utils/AsnSourceUrls.java` | **All** ipverse URL constants + prefix URL builders |
| `src/main/java/com/cym/model/AsMeta.java` | Full catalog entity |
| `src/main/java/com/cym/model/AsBlockIntent.java` | Intent / lifecycle entity |
| `src/main/java/com/cym/service/AsnMetaService.java` | Sync + search page |
| `src/main/java/com/cym/service/AsnBlockService.java` | Profile gates, push, revoke |
| `src/main/java/com/cym/service/CrowdSecClient.java` (new or extract) | LAPI HTTP helpers shared by controller/service |
| `src/main/java/com/cym/controller/adminPage/AsnController.java` | Extend: catalog search, profile, intent CRUD, push, sync |
| `src/main/java/com/cym/controller/adminPage/CrowdSecController.java` | Range ban, list/delete by reason, whitelist |
| `src/main/java/com/cym/task/ScheduleTask.java` | Daily AsMeta sync |
| `src/main/java/com/cym/config/InitConfig.java` | AsnRule → intent migration flag; default settings |
| `src/main/java/com/cym/service/ConfService.java` | Stop treating AsnRule map as primary big-block path (see Task 8) |
| `src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html` | ASN tab UI: profile + catalog + intents |
| `src/main/resources/static/js/adminPage/denyAllow/asn.js` and/or `protectionCert/asn*.js` | Front-end |
| `src/main/resources/static/js/adminPage/protectionCert/crowdsec.js` | Unban / whitelist UX |
| `messages*.properties` ×3 | i18n |
| `src/test/java/com/cym/service/AsnMetaParseTest.java` | Fixture parse unit tests |
| `src/test/java/com/cym/service/AsnBlockServiceTest.java` | Profile gate + reasonTag unit tests |
| `tests/e2e/35-asn-catalog-profile.spec.js` | E2E UI |

---

### Task 0: `AsnSourceUrls` — all source websites (do not omit)

**Files:**
- Create: `src/main/java/com/cym/utils/AsnSourceUrls.java`
- Create: `src/test/java/com/cym/utils/AsnSourceUrlsTest.java`

**Interfaces:**
- Produces: public constants + `prefixIpv4Url(String asn)` / `prefixIpv6Url(String asn)` / `prefixAggregatedJsonUrl(String asn)`
- Consumes: none

- [ ] **Step 1: Write failing test for exact URL strings**

```java
package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class AsnSourceUrlsTest {
	@Test
	public void metaAndPrefixUrls_exact() {
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json",
			AsnSourceUrls.META_JSON_URL);
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv",
			AsnSourceUrls.META_CSV_URL);
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/51167/ipv4-aggregated.txt",
			AsnSourceUrls.prefixIpv4Url("51167"));
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/51167/ipv6-aggregated.txt",
			AsnSourceUrls.prefixIpv6Url("51167"));
		assertEquals(
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/51167/aggregated.json",
			AsnSourceUrls.prefixAggregatedJsonUrl("51167"));
		assertEquals("https://lens.ipverse.net/", AsnSourceUrls.LENS_HOME);
		assertEquals("https://github.com/ipverse/as-metadata", AsnSourceUrls.REPO_AS_METADATA);
		assertEquals("https://github.com/ipverse/as-ip-blocks", AsnSourceUrls.REPO_AS_IP_BLOCKS);
		assertEquals("https://github.com/ipverse/tools", AsnSourceUrls.REPO_TOOLS);
		assertEquals(
			"https://github.com/ipverse/tools/blob/main/crowdsec/README.md",
			AsnSourceUrls.REPO_TOOLS_CROWDSEC_README);
		assertEquals(
			"https://github.com/ipverse/as-ip-blocks/releases/latest/download/as-ip-blocks.tar.gz",
			AsnSourceUrls.AS_IP_BLOCKS_TARBALL);
		// forbidden legacy fragment must not appear in builders
		assertFalse(AsnSourceUrls.prefixIpv4Url("1").contains("asn-ip"));
	}
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn -q -Dtest=AsnSourceUrlsTest test
```

- [ ] **Step 3: Implement full class (every URL from Source URLs table)**

```java
package com.cym.utils;

/**
 * Canonical external sources for ASN catalog / big-block prefixes.
 * Spec: docs/superpowers/specs/2026-08-12-ipverse-asn-crowdsec-design.md §11
 * Do not use legacy repo path ipverse/asn-ip.
 */
public final class AsnSourceUrls {
	private AsnSourceUrls() {}

	public static final String LENS_HOME = "https://lens.ipverse.net/";
	public static final String REPO_AS_METADATA = "https://github.com/ipverse/as-metadata";
	public static final String REPO_AS_IP_BLOCKS = "https://github.com/ipverse/as-ip-blocks";
	public static final String REPO_TOOLS = "https://github.com/ipverse/tools";
	public static final String REPO_TOOLS_CROWDSEC_README =
			"https://github.com/ipverse/tools/blob/main/crowdsec/README.md";
	public static final String REPO_COUNTRY_IP_BLOCKS = "https://github.com/ipverse/country-ip-blocks";
	public static final String REPO_GEO_IP_BLOCKS = "https://github.com/ipverse/geo-ip-blocks";
	public static final String REPO_AS_OVERLAY = "https://github.com/ipverse/as-overlay";

	/** Primary full catalog (includes category / networkRole). */
	public static final String META_JSON_URL =
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.json";
	/** Lightweight 4-column catalog (no category). */
	public static final String META_CSV_URL =
			"https://raw.githubusercontent.com/ipverse/as-metadata/master/as.csv";

	public static final String AS_IP_BLOCKS_TARBALL =
			"https://github.com/ipverse/as-ip-blocks/releases/latest/download/as-ip-blocks.tar.gz";

	private static final String AS_IP_BLOCKS_AS_BASE =
			"https://raw.githubusercontent.com/ipverse/as-ip-blocks/master/as/";

	public static String prefixIpv4Url(String asn) {
		return AS_IP_BLOCKS_AS_BASE + digits(asn) + "/ipv4-aggregated.txt";
	}

	public static String prefixIpv6Url(String asn) {
		return AS_IP_BLOCKS_AS_BASE + digits(asn) + "/ipv6-aggregated.txt";
	}

	public static String prefixAggregatedJsonUrl(String asn) {
		return AS_IP_BLOCKS_AS_BASE + digits(asn) + "/aggregated.json";
	}

	private static String digits(String asn) {
		if (asn == null) {
			throw new IllegalArgumentException("asn null");
		}
		String a = asn.trim();
		if (a.regionMatches(true, 0, "AS", 0, 2)) {
			a = a.substring(2).trim();
		}
		if (!a.matches("\\d+")) {
			throw new IllegalArgumentException("invalid asn: " + asn);
		}
		return a;
	}
}
```

- [ ] **Step 4: Run test PASS**

```bash
mvn -q -Dtest=AsnSourceUrlsTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cym/utils/AsnSourceUrls.java src/test/java/com/cym/utils/AsnSourceUrlsTest.java
git commit -m "feat(asn): centralize ipverse source URLs in AsnSourceUrls"
```

---

### Task 1: Models `AsMeta` + `AsBlockIntent`

**Files:**
- Create: `src/main/java/com/cym/model/AsMeta.java`
- Create: `src/main/java/com/cym/model/AsBlockIntent.java`
- Test: compile only this task (`mvn -q -DskipTests compile`)

**Interfaces:**
- Produces: `@Table` entities extending `com.cym.sqlhelper.bean.BaseModel`
- Status constants on intent (public static final String): `candidate`, `pending`, `active`, `failed`, `revoked`

- [ ] **Step 1: Create `AsMeta.java`**

```java
package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

@Table
public class AsMeta extends BaseModel {
	/** ASN number digits, e.g. "51167" */
	String asn;
	String handle;
	String description;
	/** ISO 3166-1 alpha-2 */
	String countryCode;
	/** hosting | isp | business | education_research | government_admin | null */
	String category;
	String networkRole;
	String origin;
	/** yyyy-MM-dd or null */
	String lastAnnounced;
	Long syncedAt;

	// getters/setters for all fields
}
```

- [ ] **Step 2: Create `AsBlockIntent.java`**

```java
package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;

@Table
public class AsBlockIntent extends BaseModel {
	public static final String STATUS_CANDIDATE = "candidate";
	public static final String STATUS_PENDING = "pending";
	public static final String STATUS_ACTIVE = "active";
	public static final String STATUS_FAILED = "failed";
	public static final String STATUS_REVOKED = "revoked";

	String asn;
	@InitValue("pending")
	String status;
	@InitValue("24h")
	String duration;
	/** nginxwebui:as-ban:AS{asn} */
	String reasonTag;
	String pushBatchId;
	Long lastPushAt;
	String lastError;
	/** manual | strict */
	String createdByProfile;
	String note;

	// getters/setters
}
```

- [ ] **Step 3: Compile**

```bash
mvn -q -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cym/model/AsMeta.java src/main/java/com/cym/model/AsBlockIntent.java
git commit -m "feat(asn): add AsMeta and AsBlockIntent models"
```

---

### Task 2: `AsnMetaService` — parse + sync + search

**Files:**
- Create: `src/main/java/com/cym/service/AsnMetaService.java`
- Create: `src/test/java/com/cym/service/AsnMetaParseTest.java`
- Create fixture: `src/test/resources/asn/as-meta-sample.json` (3–5 AS objects, include one `hosting`)

**Interfaces:**
- Produces:
  - `public static String reasonTagForAsn(String asn)` — also used by block service; **put on AsnBlockService** in Task 3; here only meta.
  - `public int syncFromRemote()` — download JSON via **`AsnSourceUrls.META_JSON_URL`**, upsert, return row count; sets `asn.meta.lastSyncAt`
  - `public Page search(Page page, String q, String category, String countryCode)`
  - **Do not** redeclare meta URLs; import `com.cym.utils.AsnSourceUrls`
- Consumes: `AsnSourceUrls`, `SqlHelper`, `SettingService`, Hutool `HttpRequest`, Jackson or Hutool JSON

- [ ] **Step 1: Write failing unit test for JSON parse (fixture only, no network)**

```java
package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.hutool.core.io.IoUtil;

public class AsnMetaParseTest {
	@Test
	public void parseSample_hasHostingAndFields() {
		InputStream in = getClass().getResourceAsStream("/asn/as-meta-sample.json");
		assertNotNull(in);
		String json = IoUtil.read(in, StandardCharsets.UTF_8);
		List<AsMetaRow> rows = AsnMetaService.parseAsJson(json);
		assertTrue(rows.size() >= 2);
		AsMetaRow contabo = rows.stream().filter(r -> "51167".equals(r.asn)).findFirst().orElse(null);
		assertNotNull(contabo);
		assertEquals("hosting", contabo.category);
		assertEquals("DE", contabo.countryCode);
	}
}
```

Use a package-visible DTO `AsMetaRow` inside `AsnMetaService` or static nested class with fields: asn, handle, description, countryCode, category, networkRole, origin, lastAnnounced.

- [ ] **Step 2: Run test — expect FAIL (class/method missing)**

```bash
mvn -q -Dtest=AsnMetaParseTest test
```

- [ ] **Step 3: Implement `parseAsJson` + fixture**

Fixture shape (minimal):

```json
[
  {
    "asn": 51167,
    "metadata": {
      "handle": "CONTABO",
      "description": "Contabo GmbH",
      "countryCode": "DE",
      "category": "hosting",
      "networkRole": "stub",
      "origin": "authoritative"
    },
    "lastAnnounced": "2026-08-11"
  },
  {
    "asn": 3462,
    "metadata": {
      "handle": "HINET",
      "description": "Chunghwa Telecom",
      "countryCode": "TW",
      "category": "isp",
      "networkRole": "access_provider",
      "origin": "authoritative"
    },
    "lastAnnounced": "2026-08-11"
  }
]
```

Parse rules:
- `asn` may be number → `String.valueOf`
- null metadata fields → null columns
- skip entries with blank asn

- [ ] **Step 4: Implement `syncFromRemote`**

```java
public int syncFromRemote() {
	// MUST use AsnSourceUrls.META_JSON_URL — never hardcode / never asn-ip legacy
	HttpResponse resp = HttpRequest.get(AsnSourceUrls.META_JSON_URL)
		.timeout(120_000)
		.header("User-Agent", "nginxWebUI/AsnMeta-sync")
		.setMaxRedirectCount(5)
		.execute();
	if (!resp.isOk()) {
		settingService.set("asn.meta.lastSyncError", "HTTP " + resp.getStatus());
		throw new IllegalStateException("AsMeta sync HTTP " + resp.getStatus()
			+ " url=" + AsnSourceUrls.META_JSON_URL);
	}
	List<AsMetaRow> rows = parseAsJson(resp.body());
	long now = System.currentTimeMillis();
	int n = 0;
	for (AsMetaRow row : rows) {
		AsMeta existing = sqlHelper.findOneByQuery(
			new ConditionAndWrapper().eq("asn", row.asn), AsMeta.class);
		AsMeta m = existing != null ? existing : new AsMeta();
		m.setAsn(row.asn);
		m.setHandle(row.handle);
		m.setDescription(row.description);
		m.setCountryCode(row.countryCode);
		m.setCategory(row.category);
		m.setNetworkRole(row.networkRole);
		m.setOrigin(row.origin);
		m.setLastAnnounced(row.lastAnnounced);
		m.setSyncedAt(now);
		sqlHelper.insertOrUpdate(m);
		n++;
	}
	settingService.set("asn.meta.lastSyncAt", String.valueOf(now));
	settingService.set("asn.meta.lastSyncError", "");
	return n;
}
```

**Memory note:** If full JSON OOM on small heaps, implement streaming with Jackson `JsonParser` array iteration in a follow-up commit within this task — do not leave TODO; prefer streaming if heap is an issue in dev.

- [ ] **Step 5: Implement `search`**

```java
public Page search(Page page, String q, String category, String countryCode) {
	ConditionAndWrapper c = new ConditionAndWrapper();
	if (StrUtil.isNotBlank(category)) {
		c = c.eq("category", category);
	}
	if (StrUtil.isNotBlank(countryCode)) {
		c = c.eq("countryCode", countryCode.toUpperCase());
	}
	if (StrUtil.isNotBlank(q)) {
		String qq = q.trim();
		// Prefer: asn exact OR handle/description like — use ConditionOrWrapper if available
		// If SqlHelper like is limited, filter asn eq when qq.matches("\\d+"), else like handle
		if (qq.matches("\\d+")) {
			c = c.eq("asn", qq);
		} else {
			c = c.like("handle", qq); // extend with description if API supports OR
		}
	}
	return sqlHelper.findPage(c, page, AsMeta.class);
}
```

If `like` + OR is awkward in SqlHelper, ship asn-exact + handle-like first; document limitation — still must support category/country filters.

- [ ] **Step 6: Run unit test PASS**

```bash
mvn -q -Dtest=AsnMetaParseTest test
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cym/service/AsnMetaService.java src/test/java/com/cym/service/AsnMetaParseTest.java src/test/resources/asn/
git commit -m "feat(asn): AsMeta sync parse and search service"
```

---

### Task 3: `AsnBlockService` — profile gates + reasonTag + intent CRUD helpers

**Files:**
- Create: `src/main/java/com/cym/service/AsnBlockService.java`
- Create: `src/test/java/com/cym/service/AsnBlockServiceTest.java`

**Interfaces:**
- Produces:
  - `public static String reasonTagForAsn(String asn)` → `nginxwebui:as-ban:AS` + digits
  - `public String getProfile()` → from setting, default `light`
  - `public void setProfile(String profile)` → only `light|manual|strict`
  - `public boolean canCreateIntent()` → manual or strict
  - `public boolean canPush()` → manual or strict **and** CrowdSec configured
  - `public boolean canSuggestCandidates()` → strict only
  - `public AsBlockIntent addIntent(String asn, String duration, String note, String profile)`
  - `public List<AsBlockIntent> listIntents()`
- Consumes: `SettingService`, `SqlHelper`, later `CrowdSecClient`

- [ ] **Step 1: Failing tests**

```java
@Test
public void reasonTag_format() {
	assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("51167"));
}

@Test
public void light_cannotCreateIntent() {
	// inject SettingService mock or test pure helpers:
	assertFalse(AsnBlockService.canCreateIntentForProfile("light"));
	assertTrue(AsnBlockService.canCreateIntentForProfile("manual"));
	assertTrue(AsnBlockService.canSuggestCandidatesForProfile("strict"));
	assertFalse(AsnBlockService.canSuggestCandidatesForProfile("manual"));
}
```

Expose pure static profile helpers to keep unit tests free of DB:

```java
public static boolean canCreateIntentForProfile(String p) {
	return "manual".equals(p) || "strict".equals(p);
}
public static boolean canSuggestCandidatesForProfile(String p) {
	return "strict".equals(p);
}
public static String normalizeProfile(String p) {
	if ("manual".equals(p) || "strict".equals(p) || "light".equals(p)) return p;
	return "light";
}
public static String reasonTagForAsn(String asn) {
	String a = asn == null ? "" : asn.trim();
	if (!a.matches("\\d+")) throw new IllegalArgumentException("invalid asn");
	return "nginxwebui:as-ban:AS" + a;
}
```

- [ ] **Step 2: Run tests FAIL then implement PASS**

- [ ] **Step 3: `addIntent`**

```java
public AsBlockIntent addIntent(String asn, String duration, String note) {
	String profile = getProfile();
	if (!canCreateIntentForProfile(profile)) {
		throw new IllegalStateException("profile_disallows_intent");
	}
	asn = asn.trim();
	if (!asn.matches("\\d+")) throw new IllegalArgumentException("invalid_asn");
	AsBlockIntent i = new AsBlockIntent();
	i.setAsn(asn);
	i.setStatus(AsBlockIntent.STATUS_PENDING);
	i.setDuration(StrUtil.blankToDefault(duration, "24h"));
	i.setReasonTag(reasonTagForAsn(asn));
	i.setCreatedByProfile(profile);
	i.setNote(note);
	sqlHelper.insert(i);
	return i;
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cym/service/AsnBlockService.java src/test/java/com/cym/service/AsnBlockServiceTest.java
git commit -m "feat(asn): AsnBlockService profile gates and intents"
```

---

### Task 4: `CrowdSecClient` — range ban + delete by reason

**Files:**
- Create: `src/main/java/com/cym/service/CrowdSecClient.java`
- Modify: `src/main/java/com/cym/controller/adminPage/CrowdSecController.java` to delegate where practical
- Test: unit test JSON body builder (no live LAPI required)

**Interfaces:**
- Produces:
  - `boolean isConfigured()`
  - `void banRange(String cidr, String duration, String reason)`
  - `void banIp(String ip, String duration, String reason)` — wrap existing
  - `int deleteDecisionsByReasonPrefix(String reasonPrefix)` — returns deleted count
  - `void whitelistIp(String ip, String duration, String reason)` — if LAPI supports type whitelist; else document delete-only
- Consumes: `SettingService` keys `crowdsecUrl`, `crowdsecApiKey`

- [ ] **Step 1: Implement client**

```java
public void banRange(String cidr, String duration, String reason) {
	ensureConfigured();
	// Escape JSON carefully — use JSONObject / hutool JSONUtil
	JSONObject body = new JSONObject();
	body.set("duration", duration);
	body.set("reason", reason);
	body.set("scope", "range");
	body.set("value", cidr);
	body.set("type", "ban");
	HttpResponse resp = HttpRequest.post(base() + "/v1/decisions")
		.header("X-Api-Key", apiKey())
		.header("Content-Type", "application/json")
		.body(body.toString())
		.timeout(15_000)
		.execute();
	if (!resp.isOk()) {
		throw new IllegalStateException("crowdsec banRange HTTP " + resp.getStatus() + " " + resp.body());
	}
}
```

List decisions: `GET /v1/decisions?limit=...` — filter client-side where `reason` equals or starts with prefix; DELETE `/v1/decisions/{id}` each.

**Note:** Some CrowdSec versions use `POST /v1/decisions/stream` or alerts API for bulk. If `/v1/decisions` POST shape differs in your deployed CS version, adjust to match **runtime LAPI** used by docker `nginxwebui-crowdsec` in this repo — inspect existing `addDecision` which already POSTs scope=ip.

- [ ] **Step 2: Extend `CrowdSecController`**

Add mappings:
- `POST /adminPage/crowdsec/addRangeDecision` params: `range`, `duration`, `reason`
- `POST /adminPage/crowdsec/deleteByReason` params: `reasonPrefix`
- `POST /adminPage/crowdsec/whitelistIp` params: `ip`, `duration`, `reason` (optional type=whitelist body)

Keep existing `addDecision` / `deleteDecision`.

- [ ] **Step 3: Compile + commit**

```bash
mvn -q -DskipTests compile
git add src/main/java/com/cym/service/CrowdSecClient.java src/main/java/com/cym/controller/adminPage/CrowdSecController.java
git commit -m "feat(crowdsec): range ban and delete-by-reason client"
```

---

### Task 5: Push ASN intent → fetch prefixes → CrowdSec

**Files:**
- Modify: `src/main/java/com/cym/service/AsnBlockService.java`
- Optional helper: prefix fetch methods in same service

**Interfaces:**
- Produces:
  - `public void pushIntent(String intentId)`
  - `public int revokeByReasonTag(String reasonTag)`
  - `public int revokeAllWebuiAsnBans()` — reason starts with `nginxwebui:as-ban:AS`
- Prefix URLs: **only** via `AsnSourceUrls.prefixIpv4Url(asn)` / `prefixIpv6Url(asn)`  
  (full strings listed in **Source URLs** section above — do not invent paths)

- [ ] **Step 1: Implement prefix fetch**

```java
List<String> fetchAggregatedCidrs(String asn) {
	List<String> out = new ArrayList<>();
	// MUST use AsnSourceUrls — exact hosts/paths from plan Source URLs table
	String[] urls = new String[] {
		AsnSourceUrls.prefixIpv4Url(asn),
		AsnSourceUrls.prefixIpv6Url(asn)
	};
	for (String url : urls) {
		HttpResponse resp = HttpRequest.get(url)
			.timeout(30_000)
			.header("User-Agent", "nginxWebUI/AsnBlock")
			.setMaxRedirectCount(5)
			.execute();
		if (!resp.isOk()) continue;
		for (String line : resp.body().split("\r?\n")) {
			String s = line.trim();
			if (s.isEmpty() || s.startsWith("#")) continue;
			out.add(s);
		}
	}
	return out;
}
```

- [ ] **Step 2: `pushIntent`**

```java
public void pushIntent(String intentId) {
	if (!canCreateIntentForProfile(getProfile())) {
		throw new IllegalStateException("profile_disallows_push");
	}
	if (!crowdSecClient.isConfigured()) {
		throw new IllegalStateException("crowdsec_not_configured");
	}
	AsBlockIntent intent = sqlHelper.findById(intentId, AsBlockIntent.class);
	if (intent == null) throw new IllegalArgumentException("intent_not_found");
	intent.setStatus(AsBlockIntent.STATUS_PENDING);
	intent.setLastError(null);
	sqlHelper.updateById(intent);

	String batchId = String.valueOf(System.currentTimeMillis());
	List<String> cidrs = fetchAggregatedCidrs(intent.getAsn());
	if (cidrs.isEmpty()) {
		intent.setStatus(AsBlockIntent.STATUS_FAILED);
		intent.setLastError("no_prefixes");
		sqlHelper.updateById(intent);
		return;
	}
	String reason = intent.getReasonTag();
	int ok = 0;
	String lastErr = null;
	for (String cidr : cidrs) {
		try {
			crowdSecClient.banRange(cidr, intent.getDuration(), reason);
			ok++;
		} catch (Exception e) {
			lastErr = e.getMessage();
		}
	}
	intent.setPushBatchId(batchId);
	intent.setLastPushAt(System.currentTimeMillis());
	if (ok == 0) {
		intent.setStatus(AsBlockIntent.STATUS_FAILED);
		intent.setLastError(lastErr);
	} else {
		intent.setStatus(AsBlockIntent.STATUS_ACTIVE);
		intent.setLastError(ok < cidrs.size() ? "partial:" + ok + "/" + cidrs.size() + " " + lastErr : null);
	}
	sqlHelper.updateById(intent);
}
```

- [ ] **Step 3: Revoke**

```java
public int revokeByReasonTag(String reasonTag) {
	int n = crowdSecClient.deleteDecisionsByReasonEquals(reasonTag);
	// mark intents revoked
	List<AsBlockIntent> list = sqlHelper.findListByQuery(
		new ConditionAndWrapper().eq("reasonTag", reasonTag), AsBlockIntent.class);
	for (AsBlockIntent i : list) {
		i.setStatus(AsBlockIntent.STATUS_REVOKED);
		sqlHelper.updateById(i);
	}
	return n;
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cym/service/AsnBlockService.java
git commit -m "feat(asn): push intent prefixes to CrowdSec range bans"
```

---

### Task 6: Controller APIs + schedule sync

**Files:**
- Modify: `src/main/java/com/cym/controller/adminPage/AsnController.java`
- Modify: `src/main/java/com/cym/task/ScheduleTask.java`
- Modify: `src/main/java/com/cym/config/InitConfig.java` (default settings only)

**Interfaces (HTTP under `/adminPage/asn`):**

| Method | Path | Behavior |
|--------|------|----------|
| GET | `catalog` | page,q,category,countryCode → Page AsMeta |
| POST | `syncMeta` | manual sync (async thread + single-flight flag optional) |
| GET | `profile` | current profile + crowdsecConfigured |
| POST | `setProfile` | profile=light\|manual\|strict; if → light and `revokeMode` = keep\|revoke → Z |
| GET | `intents` | list intents |
| POST | `addIntent` | asn,duration,note |
| POST | `pushIntent` | id |
| POST | `revokeIntent` | id or reasonTag |
| POST | `suggestCandidates` | category=hosting (strict only) → insert candidate rows if missing |

Keep legacy `list` / `addOver` temporarily for AsnRule during migration (Task 8).

- [ ] **Step 1: Implement endpoints returning `JsonResult`** with i18n error keys for `profile_disallows_*`, `crowdsec_not_configured`.

- [ ] **Step 2: ScheduleTask**

```java
@Scheduled(cron = "0 * * * * ?")
public void syncAsMeta() {
	String fetchTime = settingService.get("asn.meta.syncTime");
	if (StrUtil.isBlank(fetchTime) || !fetchTime.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
		fetchTime = "04:15";
	}
	String nowHHmm = DateUtil.format(new Date(), "HH:mm");
	if (!nowHHmm.equals(fetchTime)) return;
	if (!asMetaSyncing.compareAndSet(false, true)) return;
	new Thread(() -> {
		try {
			asnMetaService.syncFromRemote();
		} catch (Exception e) {
			logger.error("AsMeta sync failed", e);
		} finally {
			asMetaSyncing.set(false);
		}
	}, "as-meta-sync").start();
}
```

- [ ] **Step 3: InitConfig** once: if `protection.profile` missing → `light`; if `asn.meta.syncTime` missing → `04:15`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cym/controller/adminPage/AsnController.java src/main/java/com/cym/task/ScheduleTask.java src/main/java/com/cym/config/InitConfig.java
git commit -m "feat(asn): catalog/profile/intent APIs and daily AsMeta sync"
```

---

### Task 7: UI — profile + catalog + intents + Z dialog

**Files:**
- Modify: `src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html` (ASN tab section)
- Modify: `src/main/resources/static/js/adminPage/denyAllow/asn.js` (or split `protectionCert/asnCatalog.js`)
- Modify: `messages.properties`, `messages_zh_TW.properties`, `messages_en_US.properties`

**UI requirements:**
1. Profile control: three options Light / Manual / Strict (radio or button group). On change to light → layer.confirm Z: keep vs revoke.
2. Catalog table: search input, category select, country input, page prev/next; columns asn, handle, description, country, category; action「加入封鎖意圖」disabled when light or no CS.
3. Intent table: asn, status, duration, lastPush, error, actions push/revoke.
4. Banner when CrowdSec not configured.
5. Strict-only button「依 category 產生候選」→ confirm → suggestCandidates.

- [ ] **Step 1: Add i18n keys (all 3 files)** — examples (en):

```
asnStr.profile=Protection profile
asnStr.profileLight=Light
asnStr.profileManual=Manual
asnStr.profileStrict=Strict
asnStr.catalog=ASN catalog
asnStr.syncMeta=Sync catalog
asnStr.addIntent=Add block intent
asnStr.push=Push to CrowdSec
asnStr.revoke=Revoke
asnStr.switchLightKeep=Keep bans until expiry
asnStr.switchLightRevoke=Revoke WebUI ASN bans now
asnStr.crowdsecRequired=Configure CrowdSec to push ASN bans
asnStr.suggestHosting=Suggest hosting candidates
asnStr.sourceLens=Lens (ipverse lookup)
asnStr.sourceMeta=ASN metadata (ipverse)
asnStr.sourceBlocks=AS IP blocks (ipverse)
asnStr.sourceTools=ipverse CrowdSec tools
```

CJK: proper Traditional/Simplified with `\uXXXX`.

- [ ] **Step 1b: Help / footer links on ASN tab** — must use server-injected or JS constants matching `AsnSourceUrls` (do not omit):

| Link label | href |
|------------|------|
| Lens | `https://lens.ipverse.net/` |
| as-metadata | `https://github.com/ipverse/as-metadata` |
| as-ip-blocks | `https://github.com/ipverse/as-ip-blocks` |
| tools (CrowdSec ban-as) | `https://github.com/ipverse/tools` |

Prefer injecting from backend once, e.g. controller puts `asnSourceUrls` map into Freemarker from `AsnSourceUrls` fields, so URLs stay single-sourced.

- [ ] **Step 2: Wire JS to new APIs**; keep legacy manual AsnRule UI section labeled deprecated or hide after Task 8.

- [ ] **Step 3: Manual smoke** — login → protectionCert ASN tab → profile shows light → catalog empty until sync → sync button (dev may skip full 60MB once; optional seed few rows via SQL for UI test).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/WEB-INF/view/adminPage/protectionCert/index.html src/main/resources/static/js/adminPage/denyAllow/asn.js src/main/resources/messages*.properties
git commit -m "feat(asn): catalog UI, profile switch, intent actions"
```

---

### Task 8: Deprecate AsnRule map path + migration

**Files:**
- Modify: `src/main/java/com/cym/service/ConfService.java` (ASN map block ~213–232)
- Modify: `src/main/java/com/cym/config/InitConfig.java`
- Modify: `AsnController` legacy endpoints — soft-disable or redirect to intent

**Behavior:**
1. Setting `asn.nginxMapEnabled` default **`false`**. When false, ConfService **skips** building `map $geoip2_data_asn $blocked_asn` from AsnRule.
2. Migration `asnRuleToIntent20260812`: for each AsnRule with enable true, if no intent for asn, insert AsBlockIntent status=`active`, reasonTag set, **do not auto push**.
3. InitConfig seed templates that inject `if ($blocked_asn)` remain for users who re-enable map later; document that primary path is CrowdSec.

- [ ] **Step 1: ConfService guard**

```java
boolean mapEnabled = "true".equals(settingService.get("asn.nginxMapEnabled"));
if (mapEnabled) {
	// existing AsnRule map generation
}
```

- [ ] **Step 2: Migration once** with setting flag `asnRuleToIntent20260812=true` after run.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/service/ConfService.java src/main/java/com/cym/config/InitConfig.java
git commit -m "feat(asn): deprecate AsnRule nginx map as primary path"
```

---

### Task 9: Unban / whitelist UX (CrowdSec-first)

**Files:**
- Modify: `src/main/resources/static/js/adminPage/protectionCert/crowdsec.js`
- Modify: `CrowdSecController` if needed
- Optional: call DenyAllow allow insert when checkbox set

- [ ] **Step 1: On decisions table, ensure Delete works** (already exists).

- [ ] **Step 2: Add「Whitelist IP」** → `whitelistIp` or ban type whitelist per LAPI; optional POST to create DenyAllow type=allow with single IP if user checks「同步到白名單」.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/adminPage/protectionCert/crowdsec.js src/main/java/com/cym/controller/adminPage/CrowdSecController.java
git commit -m "feat(crowdsec): whitelist/unban UX aligned with false-positive path"
```

---

### Task 10: Playwright E2E `35-asn-catalog-profile.spec.js`

**Files:**
- Create: `tests/e2e/35-asn-catalog-profile.spec.js`
- May update: `tests/e2e/21-asn-block.spec.js` if legacy UI moved

**Tests (no live GitHub full sync, no live CrowdSec required):**

```javascript
const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('ASN catalog + profile', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');
    await page.locator('.layui-tab-title li', { hasText: /ASN/ }).click();
  });

  test('profile control present default light', async ({ page }) => {
    const light = page.locator('[name=protectionProfile], #protectionProfile, .asn-profile');
    // adapt selectors to implementation
    await expect(page.locator('body')).toContainText(/Light|輕量|轻量/i);
  });

  test('add intent disabled or blocked in light', async ({ page }) => {
    // either button disabled or API returns error — assert UI disabled preferred
    const addBtn = page.locator('button', { hasText: /intent|意圖|意图|加入/i }).first();
    // if visible, expect disabled when light
  });

  test('catalog search API reachable', async ({ page }) => {
    const res = await page.evaluate(async () => {
      const r = await fetch(ctx + '/adminPage/asn/catalog?curr=1&limit=10');
      return r.json();
    });
    expect(res.success).toBeTruthy();
  });
});
```

- [ ] **Step 1: Implement selectors matching Task 7 DOM ids exactly** (`#asnProfileLight`, `#asnCatalogBody`, etc. — **define these ids in Task 7 and copy here**).

Recommended stable ids from Task 7:
- `#asnProfile` input radio name `protectionProfile` values light|manual|strict
- `#asnCatalogQ`, `#asnCatalogBody`, `#btnAsnSyncMeta`
- `#asnIntentBody`, `#btnAsnAddIntent`

- [ ] **Step 2: Run**

```bash
mvn -q package -DskipTests
npx playwright test tests/e2e/35-asn-catalog-profile.spec.js
```

- [ ] **Step 3: Commit**

```bash
git add tests/e2e/35-asn-catalog-profile.spec.js
git commit -m "test(e2e): ASN catalog and protection profile"
```

---

### Task 11: Documentation touchpoints

**Files:**
- Modify: `Claude.md` Feature Inventory bullet for ASN / CrowdSec (short)
- Do **not** rewrite README unless user asks

- [ ] **Step 1: Add Feature Inventory line** describing ipverse catalog + profiles + CrowdSec range path.

- [ ] **Step 2: Commit**

```bash
git add Claude.md
git commit -m "docs: ASN ipverse catalog and CrowdSec profiles in inventory"
```

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| Source URLs centralized (spec §11) | **0** + used by 2, 5, 7 help links |
| Full AsMeta sync P columns | 1, 2, 6 |
| S1 AsBlockIntent | 1, 3, 5 |
| Profiles light/manual/strict | 3, 6, 7 |
| Z on switch to light | 6, 7 |
| CrowdSec range + reason tag | 4, 5 |
| No full prefix catalog | 5 (fetch on push via AsnSourceUrls) |
| D1 no CS → catalog ok, push off | 3, 6, 7 |
| False positive CS whitelist | 9 |
| Deprecate AsnRule map primary | 8 |
| i18n ×3 | 7 |
| E2E | 10 |
| Schedule | 6 |

**Placeholder scan:** None intentional; streaming JSON noted as implement-now if OOM.

**Type consistency:** `reasonTag` / `reasonTagForAsn` / status constants shared via `AsBlockIntent` + `AsnBlockService`.

---

## Execution Handoff

Plan complete and saved to:

- **Spec:** `docs/superpowers/specs/2026-08-12-ipverse-asn-crowdsec-design.md`
- **Plan:** `docs/superpowers/plans/2026-08-12-ipverse-asn-crowdsec-plan.md`

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — this session with executing-plans and checkpoints  

**Which approach?**
