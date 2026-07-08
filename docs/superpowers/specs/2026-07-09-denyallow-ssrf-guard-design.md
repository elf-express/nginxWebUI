# DenyAllow 黑名單抓取 SSRF 防護 — 設計文件

日期:2026-07-09
範圍:僅 `DenyAllowService.fetchAndUpdate`(本 fork 新增功能)
關聯掃描規則:服务端请求伪造(SSRF),Hash `ee74a397ac0bf6d2a921db423713647a976c2924`

## 1. 背景與威脅模型

DenyAllow 黑名單支援「填一個來源 URL,服務端定期抓取該 URL 的 IP 清單」。抓取邏輯 [DenyAllowService.fetchAndUpdate:71](../../../src/main/java/com/cym/service/DenyAllowService.java#L71) 直接把使用者可控的 `da.getSourceUrl()` 丟給 `HttpRequest.get(...)`,構成 SSRF。

**觸發路徑:**
- 即時:管理員 POST `/adminPage/denyAllow/addOver`(`sourceUrl` 非空)→ `fetchAndUpdate`。
- 定時:`ScheduleTask.fetchDenyAllowLists()` 每日對已存的 `sourceUrl` 抓取。

**威脅特性:**
- **Blind SSRF** — response body 只被當文字 parse IP,不回顯前端。攻擊者拿不到回應內容,主要威脅是內網探測、雲端 metadata 存取(`169.254.169.254`)、DoS。
- **需已登入管理員** — 端點在 `/adminPage/` 下,由 AppFilter 保護。這降低風險等級,但不消除(CSRF 組合、低信任管理員、憑證竊取仍有意義)。
- **現有 `setMaxRedirectCount(5)`** — 自動跟隨 redirect,是繞過 URL 校驗的經典手法(初始給公網、302 轉內網)。

## 2. 目標與非目標

**目標:** 讓 `fetchAndUpdate` 只能對「公網 http/https」目標發請求,堵住協議、內網位址、redirect 三種手法。

**非目標(本次不動,避免破壞正當功能):**
- 遠端節點管理(`RemoteController`/`RemoteService`/`ServerController`)、CrowdSec LAPI、SSO OAuth、ACME DNS —— 這些「使用者設定目標」設計上就要連內網。
- `MainController.autoUpdate`(SSRF+RCE)—— 上游原有自動更新,另案處理。
- DNS rebinding 的徹底防禦(見 §6 殘餘風險)。

## 3. 架構

### 3.1 新增 `SsrfGuard`(`src/main/java/com/cym/utils/SsrfGuard.java`)

純工具類,無 Solon 依賴,便於單元測試與重用。

```
public final class SsrfGuard {
    public static class SsrfBlockedException extends RuntimeException { ... }

    // 校驗單一 URL:協議 + 解析所有 IP 皆須為公網,否則拋 SsrfBlockedException
    public static void validatePublicUrl(String url) throws SsrfBlockedException;

    // 供測試 / 重用:判斷單一 InetAddress 是否禁止
    static boolean isBlockedAddress(java.net.InetAddress addr);
}
```

**`validatePublicUrl` 步驟:**
1. `new URL(url)`(格式不合法 → block)。
2. 協議白名單:`http` / `https`(小寫比對),其餘(`file`/`dict`/`gopher`/`ftp`/...)→ block。
3. 取 host,`InetAddress.getAllByName(host)` 取**所有**解析 IP;**任一**落在禁止範圍 → block(不是只看第一個)。

**`isBlockedAddress` 禁止範圍**(用 `InetAddress` 內建判斷 + 手動補洞):
- `isAnyLocalAddress()` — `0.0.0.0` / `::`
- `isLoopbackAddress()` — `127.0.0.0/8` / `::1`
- `isLinkLocalAddress()` — `169.254.0.0/16`(含 metadata `169.254.169.254`)/ `fe80::/10`
- `isSiteLocalAddress()` — `10/8`、`172.16/12`、`192.168/16`
- `isMulticastAddress()` — `224.0.0.0/4` / `ff00::/8`
- 手動補:CGNAT `100.64.0.0/10`、benchmark `198.18.0.0/15`、reserved `240.0.0.0/4`
- IPv4-mapped IPv6(`::ffff:127.0.0.1`)：Java `getByName` 通常回 `Inet4Address`,內建判斷即涵蓋;測試需明確涵蓋此案例確認不繞過。

### 3.2 改 `DenyAllowService.fetchAndUpdate`

把「一次 `HttpRequest.get` + 自動 5 redirect」改為「禁自動 redirect + 手動逐跳,每跳先校驗」:

```
setMaxRedirectCount(0);
String current = da.getSourceUrl();
for (int hop = 0; hop <= MAX_REDIRECTS(5); hop++) {
    SsrfGuard.validatePublicUrl(current);           // 每一跳都校驗落地
    HttpResponse response = HttpRequest.get(current)
        .setMaxRedirectCount(0).timeout(30000)
        .header("User-Agent", "nginxWebUI/DenyAllow-fetcher").execute();
    int status = response.getStatus();
    if (status >= 300 && status < 400) {            // redirect
        current = 解析 Location(相對→絕對用 new URL(base, location));
        continue;
    }
    ... 原本 isOk / body / parse IP 邏輯不變 ...
    return true / false;
}
// 超過 MAX_REDIRECTS → return false(記 warn)
```

- `SsrfBlockedException` 由既有的 `catch (Exception e)` 捕捉,記為明確 warn(區分於一般 fetch 失敗),`fetchAndUpdate` 回 `false`。
- `Location` 相對路徑用 `new URL(baseUrl, location)` 解析成絕對再校驗。

## 4. 行為與資料流(維持現狀,最小侵入)

- 被擋時 `fetchAndUpdate` 回 `false`,行為與「URL 連不通」一致 —— **`addOver` 仍 `renderSuccess()` 並存 record**(不改 controller,保留「抓失敗仍存、排程重試」的既有語意)。
- 不新增面向使用者字串 → **不動 i18n 三份 properties**(僅 log 訊息,log 不需 i18n)。

## 5. 測試策略

安全關鍵邏輯,雙層覆蓋:

1. **`SsrfGuard` 單元測試**(`src/test/java/com/cym/utils/SsrfGuardTest.java`,JUnit 5 via `solon-test`):
   - block:`file:///etc/passwd`、`http://127.0.0.1`、`http://169.254.169.254`、`http://10.0.0.1`、`http://192.168.1.1`、`http://[::1]`、`http://100.64.0.1`、IPv4-mapped、格式不合法。
   - pass:`https://example.com`、`http://1.1.1.1`(公網 IP)。
   - **前提**:專案目前 `src/test` 為空、build/CI 皆 `-DskipTests`。實作時先本地 `mvn test`(不 skip)驗證 solon-test 能跑純 JUnit;若環境不通,退回純 E2E 涵蓋代表案例並在此註記。
2. **Playwright E2E**(加到 [17-deny-allow-tags.spec.js](../../../tests/e2e/17-deny-allow-tags.spec.js) 或新 spec,進 CI gate):
   - POST `addOver` with `sourceUrl=http://169.254.169.254/...` 及 `file://...` → 之後 `detail` 查該 record,`ip` 應為空(未被抓取更新)。

## 6. 殘餘風險(誠實揭露)

- **DNS rebinding / TOCTOU**:`validatePublicUrl` 解析 IP 校驗後,Hutool `execute()` 會**再解析一次 DNS**,兩次之間 DNS 可能被改指向內網。徹底防禦需 pin IP 連線,但 https 會撞 SNI/憑證問題,對「需管理員登入」的功能不划算。**判定:此情境可接受,列為已知限制。**
- 本設計堵住:非 http(s) 協議、直接給內網位址、redirect 逐跳繞過。

## 7. 變更檔案清單

| 檔案 | 動作 |
|---|---|
| `src/main/java/com/cym/utils/SsrfGuard.java` | 新增 |
| `src/main/java/com/cym/service/DenyAllowService.java` | 改 `fetchAndUpdate`(禁 redirect + 手動逐跳校驗) |
| `src/test/java/com/cym/utils/SsrfGuardTest.java` | 新增(JUnit,前提見 §5) |
| `tests/e2e/17-deny-allow-tags.spec.js` | 新增 SSRF 被擋案例 |

## 8. 回退

- SsrfGuard 為新增、fetchAndUpdate 改動集中在一個 method;回退即還原該 method + 刪 SsrfGuard。
- 風險:若合法來源解析到非預期 IP 被誤擋 → 使用者可見症狀為「抓不到 IP」,log 有明確 SsrfBlocked warn 可診斷。
