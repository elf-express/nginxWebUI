# ASN / CrowdSec Security Harden Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden admin ASN→CrowdSec paths with CIDR/duration/reason validation, ASN digit normalization, and non-leaking API errors.

**Architecture:** Pure static `NetGuard` validators; hard gates inside `CrowdSecClient`; `AsnBlockService` normalizes ASN via `AsnSourceUrls.digits` and drops non-CIDR feed lines before ban. Controllers map fixed error codes to i18n only.

**Tech Stack:** Java 17, Solon, Hutool, JUnit 5, existing Asn/CrowdSec Phase 1 code on `dev`.

**Spec:** `docs/superpowers/specs/2026-08-13-asn-security-harden-design.md`

## Global Constraints

- Branch: `feat/asn-security-harden` only; do not touch MCP worktree or unrelated files.
- Do not expand SSRF lock on `crowdsecUrl` this round.
- Do not change product profiles (light/manual/strict) behavior beyond validation.
- User-facing strings: all three `messages.properties` / `messages_zh_TW.properties` / `messages_en_US.properties` (ISO-8859-1 `\uXXXX` for CJK).
- Reason prefix for deletes: must start with `nginxwebui:`.
- Push reason format unchanged: `nginxwebui:as-ban:AS{digits}`.
- Duration pattern exact: `^\d+[smhdwMy]?$` when non-blank.
- Leave untracked `docs/nginxdocumentation/*.mp4` alone.

## File map

| File | Responsibility |
|------|----------------|
| `src/main/java/com/cym/utils/NetGuard.java` | Pure validators |
| `src/test/java/com/cym/utils/NetGuardTest.java` | Unit tests for validators |
| `src/main/java/com/cym/service/CrowdSecClient.java` | Hard gates on ban/delete |
| `src/main/java/com/cym/service/AsnBlockService.java` | ASN digits; filter CIDRs; duration |
| `src/main/java/com/cym/controller/adminPage/CrowdSecController.java` | No raw exception messages |
| `src/main/java/com/cym/controller/adminPage/AsnController.java` | Error code map + i18n keys |
| `src/test/java/com/cym/service/AsnBlockServiceTest.java` | ASN normalize cases if pure helpers |
| `src/test/java/com/cym/service/CrowdSecClientTest.java` | Gate rejection if pure helpers |
| `messages*.properties` ×3 | Error strings |

---

### Task 1: `NetGuard` pure validators + tests

**Files:**
- Create: `src/main/java/com/cym/utils/NetGuard.java`
- Create: `src/test/java/com/cym/utils/NetGuardTest.java`

**Interfaces:**
- Produces:
  - `public static boolean isValidCidr(String s)`
  - `public static boolean isValidDuration(String s)` — true if blank? **No**: blank is caller's default; method returns true only for non-blank valid, or add `isValidDurationOrBlank`
  - Prefer: `isValidDuration(String s)` true iff non-null and matches `^\d+[smhdwMy]?$`
  - `public static boolean isBlankOrValidDuration(String s)` true if blank OR valid
  - `public static boolean isAllowedWebuiReason(String reason)` true if non-blank and `reason.startsWith("nginxwebui:")`
  - Error codes as public constants: `invalid_cidr`, `invalid_duration`, `invalid_reason`

- [ ] **Step 1: Write failing tests**

```java
package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class NetGuardTest {
	@Test
	public void cidr_ipv4_ok() {
		assertTrue(NetGuard.isValidCidr("1.2.3.0/24"));
		assertTrue(NetGuard.isValidCidr("8.8.8.8"));
		assertFalse(NetGuard.isValidCidr("all"));
		assertFalse(NetGuard.isValidCidr("1.2.3.0/33"));
		assertFalse(NetGuard.isValidCidr("not-a-cidr"));
		assertFalse(NetGuard.isValidCidr("1.2.3.0/24 evil"));
	}

	@Test
	public void cidr_ipv6_ok() {
		assertTrue(NetGuard.isValidCidr("2001:db8::/32"));
		assertTrue(NetGuard.isValidCidr("::1"));
		assertFalse(NetGuard.isValidCidr("gggg::/32"));
	}

	@Test
	public void duration_ok() {
		assertTrue(NetGuard.isValidDuration("24h"));
		assertTrue(NetGuard.isValidDuration("30m"));
		assertTrue(NetGuard.isValidDuration("7d"));
		assertTrue(NetGuard.isValidDuration("60"));
		assertFalse(NetGuard.isValidDuration(""));
		assertFalse(NetGuard.isValidDuration("24 hours"));
		assertFalse(NetGuard.isValidDuration("-1h"));
		assertTrue(NetGuard.isBlankOrValidDuration(""));
		assertTrue(NetGuard.isBlankOrValidDuration(null));
		assertFalse(NetGuard.isBlankOrValidDuration("xx"));
	}

	@Test
	public void reason_prefix() {
		assertTrue(NetGuard.isAllowedWebuiReason("nginxwebui:as-ban:AS1"));
		assertTrue(NetGuard.isAllowedWebuiReason("nginxwebui:fp-whitelist"));
		assertFalse(NetGuard.isAllowedWebuiReason("cscli manual"));
		assertFalse(NetGuard.isAllowedWebuiReason(""));
		assertFalse(NetGuard.isAllowedWebuiReason(null));
	}
}
```

- [ ] **Step 2: Run fail**

```bash
mvn -q -Dtest=NetGuardTest test
```

Expected: FAIL (class missing)

- [ ] **Step 3: Implement `NetGuard.java`**

```java
package com.cym.utils;

import java.util.regex.Pattern;

import cn.hutool.core.util.StrUtil;

/**
 * Input guards for CrowdSec / ASN ban paths (pure static).
 * Spec: docs/superpowers/specs/2026-08-13-asn-security-harden-design.md
 */
public final class NetGuard {
	private NetGuard() {}

	public static final String ERR_INVALID_CIDR = "invalid_cidr";
	public static final String ERR_INVALID_DURATION = "invalid_duration";
	public static final String ERR_INVALID_REASON = "invalid_reason";

	private static final Pattern DURATION = Pattern.compile("^\\d+[smhdwMy]?$");
	// IPv4 with optional /0-32
	private static final Pattern IPV4 = Pattern.compile(
			"^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(/([0-9]|[12]\\d|3[0-2]))?$");
	// Practical IPv6 (hex groups + ::) optional /0-128 — use java.net.InetAddress for host part if easier

	public static boolean isValidCidr(String s) {
		if (StrUtil.isBlank(s)) return false;
		String t = s.trim();
		if (t.indexOf(' ') >= 0 || t.indexOf('\t') >= 0) return false;
		if ("all".equalsIgnoreCase(t)) return false;
		int slash = t.indexOf('/');
		String host = slash >= 0 ? t.substring(0, slash) : t;
		String pref = slash >= 0 ? t.substring(slash + 1) : null;
		if (pref != null) {
			try {
				int p = Integer.parseInt(pref);
				// validated after address family known
				if (p < 0) return false;
			} catch (NumberFormatException e) {
				return false;
			}
		}
		if (IPV4.matcher(t).matches()) return true;
		// IPv6: try InetAddress
		try {
			if (host.contains(":")) {
				java.net.InetAddress addr = java.net.InetAddress.getByName(host);
				if (!(addr instanceof java.net.Inet6Address)) return false;
				if (pref == null) return true;
				int p = Integer.parseInt(pref);
				return p >= 0 && p <= 128;
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	public static boolean isValidDuration(String s) {
		if (s == null) return false;
		return DURATION.matcher(s.trim()).matches();
	}

	public static boolean isBlankOrValidDuration(String s) {
		return StrUtil.isBlank(s) || isValidDuration(s);
	}

	public static boolean isAllowedWebuiReason(String reason) {
		return StrUtil.isNotBlank(reason) && reason.startsWith("nginxwebui:");
	}
}
```

Note: Prefer `InetAddress.getByName` only for IPv6 host part; do **not** use it in a way that triggers DNS for hostnames — reject if host contains letters that are not hex/colon (IPv6 only). Safer: if host matches `[0-9a-fA-F:]+` or contains `::` then parse; else false. Avoid DNS lookup:

```java
// better IPv6 path:
if (!host.matches("(?i)[0-9a-f:]+")) return false;
byte[] raw = java.net.InetAddress.getByName(host).getAddress(); // may still resolve? 
// Actually getByName on pure hex:IPv6 literal does not DNS in practice for valid IPv6.
// Or use Apache/commons — not available. Stick to careful regex or Guava-free check.
```

Implementer: use hutool `NetUtil.isInnerIP` is wrong tool. Recommended: IPv4 regex above; for IPv6 use:

```java
try {
  if (!host.contains(":")) return false;
  // reject hostnames: only hex and colon
  if (!host.matches("(?i)^[0-9a-f:]+$")) return false;
  java.net.InetAddress.getByName(host); // literal
  ...
} catch ...
```

- [ ] **Step 4: Tests pass**

```bash
mvn -q -Dtest=NetGuardTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cym/utils/NetGuard.java src/test/java/com/cym/utils/NetGuardTest.java
git commit -m "feat(security): add NetGuard CIDR duration reason validators"
```

---

### Task 2: Wire hard gates into `CrowdSecClient`

**Files:**
- Modify: `src/main/java/com/cym/service/CrowdSecClient.java`
- Modify: `src/test/java/com/cym/service/CrowdSecClientTest.java`

**Interfaces:**
- Consumes: `NetGuard.*`
- `postDecision`: if scope is `range` or `ip`, value must pass `isValidCidr` for range; for ip allow single IP (same CIDR checker accepts host without slash)
- duration: if blank throw `invalid_duration` OR allow blank only when caller always fills — **require non-blank valid duration at client** after callers default
- `deleteDecisionsByReasonPrefix` / `Equals`: reason must `isAllowedWebuiReason`

- [ ] **Step 1: Add tests for rejection (pure path if you extract validate methods)**

```java
@Test
public void postDecision_rejectsBadCidr() {
  assertThrows(IllegalArgumentException.class, () -> {
    // if validate exposed:
    if (!NetGuard.isValidCidr("not-cidr")) throw new IllegalArgumentException(NetGuard.ERR_INVALID_CIDR);
  });
  assertEquals(NetGuard.ERR_INVALID_CIDR, NetGuard.ERR_INVALID_CIDR);
}
```

Better: package-private `void validateDecisionInputs(duration, reason optional, scope, value)` tested via public banRange throwing:

Without live HTTP, test only pure validation helpers called before HTTP — extract:

```java
static void requireCidr(String value) {
  if (!NetGuard.isValidCidr(value)) throw new IllegalArgumentException(NetGuard.ERR_INVALID_CIDR);
}
static void requireDuration(String duration) {
  if (!NetGuard.isValidDuration(duration)) throw new IllegalArgumentException(NetGuard.ERR_INVALID_DURATION);
}
static void requireWebuiReason(String reason) {
  if (!NetGuard.isAllowedWebuiReason(reason)) throw new IllegalArgumentException(NetGuard.ERR_INVALID_REASON);
}
```

- [ ] **Step 2: In `postDecision` before HTTP**

```java
requireDuration(duration);
if ("range".equals(scope) || "ip".equals(scope)) {
  requireCidr(value);
}
// reason for ban may be free text from admin on addDecision — only constrain delete paths
// Spec: push uses webui reason from service; manual addDecision reason can stay free OR also require non-blank
// Spec §5.3: delete only. Ban reason: allow blank→"nginxwebui:manual" or keep existing free reason for addDecision.
```

Spec: duration always validated. Value for ip/range always CIDR/IP validated.

- [ ] **Step 3: In `deleteMatching` / prefix / equals entry**

```java
if (equalsOnly) {
  requireWebuiReason(reasonEquals);
} else {
  requireWebuiReason(reasonPrefix);
}
```

- [ ] **Step 4: Compile + test**

```bash
mvn -q -Dtest=NetGuardTest,CrowdSecClientTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cym/service/CrowdSecClient.java src/test/java/com/cym/service/CrowdSecClientTest.java
git commit -m "fix(security): CrowdSecClient hard-gate CIDR duration and webui reasons"
```

---

### Task 3: `AsnBlockService` ASN normalize + filter feed lines

**Files:**
- Modify: `src/main/java/com/cym/service/AsnBlockService.java`
- Modify: `src/test/java/com/cym/service/AsnBlockServiceTest.java`

**Interfaces:**
- Consumes: `AsnSourceUrls.digits`, `NetGuard`
- `reasonTagForAsn`: use digits first:

```java
public static String reasonTagForAsn(String asn) {
  String a = AsnSourceUrls.digits(asn); // throws if invalid
  return WEBUI_ASN_REASON_PREFIX + a;
}
```

- `addIntent`: `asn = AsnSourceUrls.digits(asn)` then store; duration: if blank use `24h`; if non-blank must `NetGuard.isValidDuration` else throw `invalid_duration`
- `fetchAggregatedCidrs`: after trim, only `NetGuard.isValidCidr(s)` lines added
- `pushIntent`: before banRange, duration already on intent — re-validate; skip invalid (already filtered)

- [ ] **Step 1: Unit tests**

```java
@Test
public void reasonTag_acceptsAsPrefix() {
  assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("AS51167"));
  assertEquals("nginxwebui:as-ban:AS51167", AsnBlockService.reasonTagForAsn("51167"));
}
```

If `digits` throws IllegalArgumentException with message containing invalid — map to `invalid_asn` in service.

- [ ] **Step 2: Implement + test**

```bash
mvn -q -Dtest=AsnBlockServiceTest,NetGuardTest test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/service/AsnBlockService.java src/test/java/com/cym/service/AsnBlockServiceTest.java
git commit -m "fix(security): normalize ASN digits and filter non-CIDR prefixes"
```

---

### Task 4: Controllers — no leak + i18n

**Files:**
- Modify: `src/main/java/com/cym/controller/adminPage/AsnController.java`
- Modify: `src/main/java/com/cym/controller/adminPage/CrowdSecController.java`
- Modify: `messages.properties`, `messages_zh_TW.properties`, `messages_en_US.properties`

**Interfaces:**
- Add mapServiceError cases: `invalid_cidr`, `invalid_duration`, `invalid_reason`, `crowdsec_error`
- CrowdSecController: on catch, `logger.error(..., e); return renderError(mapped)` never `e.getMessage()` for unknown
- `revokeIntent`: if reasonTag provided without id, still require `NetGuard.isAllowedWebuiReason(reasonTag)` before service
- `whitelistIp` / `addDecision` / `addRangeDecision`: validate duration blank→default then validate; validate ip/range via NetGuard

i18n en examples:

```
asnStr.invalidCidr=Invalid IP or CIDR
asnStr.invalidDuration=Invalid duration (e.g. 24h, 30m)
asnStr.invalidReason=Reason must start with nginxwebui:
crowdsecStr.invalidCidr=Invalid IP or CIDR
crowdsecStr.invalidDuration=Invalid duration (e.g. 4h)
crowdsecStr.invalidReason=Reason must start with nginxwebui:
crowdsecStr.error=CrowdSec request failed
```

CJK with `\uXXXX` in zh properties.

- [ ] **Step 1: Implement controller mapping**

```java
case "invalid_cidr": return msgOr("asnStr.invalidCidr", code);
case "invalid_duration": return msgOr("asnStr.invalidDuration", code);
case "invalid_reason": return msgOr("asnStr.invalidReason", code);
case "crowdsec_error": return msgOr("crowdsecStr.error", code);
```

CrowdSecController helper:

```java
private JsonResult fail(Exception e) {
  logger.error("crowdsec api failed", e);
  if (e instanceof IllegalArgumentException) {
    String m = e.getMessage();
    if (NetGuard.ERR_INVALID_CIDR.equals(m)) return renderError(...);
    ...
  }
  return renderError(msg crowdsec_error);
}
```

- [ ] **Step 2: Compile**

```bash
mvn -q -DskipTests compile
mvn -q -Dtest=NetGuardTest,AsnBlockServiceTest,CrowdSecClientTest test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cym/controller/adminPage/AsnController.java \
  src/main/java/com/cym/controller/adminPage/CrowdSecController.java \
  src/main/resources/messages.properties \
  src/main/resources/messages_zh_TW.properties \
  src/main/resources/messages_en_US.properties
git commit -m "fix(security): map validation errors to i18n without leaking internals"
```

---

### Task 5: Regression smoke

**Files:** none new

- [ ] **Step 1: Full unit set**

```bash
mvn -q -Dtest=NetGuardTest,AsnSourceUrlsTest,AsnMetaParseTest,AsnBlockServiceTest,CrowdSecClientTest test
```

Expected: all PASS

- [ ] **Step 2: Optional E2E (needs package)**

```bash
mvn -q package -DskipTests
npx playwright test --config=tests/e2e/playwright.fast.config.js tests/e2e/35-asn-catalog-profile.spec.js
```

Expected: 4 passed (profile/catalog still works; no CS required)

- [ ] **Step 3: Commit only if test fixes needed; else done**

---

## Self-review (plan vs spec)

| Spec item | Task |
|-----------|------|
| CIDR whitelist | 1, 2, 3 |
| reason nginxwebui: on delete | 1, 2, 4 |
| duration pattern | 1, 2, 3, 4 |
| ASN digits / AS prefix | 3 |
| No e.getMessage to client | 4 |
| Unit tests | 1–3, 5 |
| No SSRF crowdsecUrl expand | Global — out of scope |

**Placeholder scan:** none intentional.  
**Type consistency:** error codes = NetGuard constants = controller switch cases.

---

## Execution Handoff

Plan complete and saved to:

- **Spec:** `docs/superpowers/specs/2026-08-13-asn-security-harden-design.md`
- **Plan:** `docs/superpowers/plans/2026-08-13-asn-security-harden-plan.md`
- **Branch:** `feat/asn-security-harden`

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task + review  
2. **Inline Execution** — this session with executing-plans  

**Which approach?**
