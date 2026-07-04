# Java 8 → 17 地基升級(配合 dependabot maxmind-db 4.1.0)

**日期:** 2026-07-05
**狀態:** 執行中
**觸發:** GitHub 合併 dependabot PR(#20 maxmind-db 2.1.0→4.1.0、#21 solon 3.10.7 等)進 master/dev 後,`mvn package` 實測失敗;user 決定升 Java 17 修地基,再進 phase 2。

---

## 背景 / 鐵證

`mvn clean package` 在本地(maven 走 JAVA_HOME=jdk-17)實測失敗:

```
GeoipService.java:[141,62] cannot find symbol
  symbol:   method getBuildDate()
  location: class com.maxmind.db.Metadata
```

根因(javap 反編譯 maxmind-db 4.1.0 確認):
- `com.maxmind.db.Metadata` 在 4.x 改寫成 **Java Record**(需 Java 16+),`getBuildDate()` 被移除。
- 替代 API:`public java.time.Instant buildTime()`(或 `java.math.BigInteger buildEpoch()`)。
- `new Reader(File, FileMode)` 建構子、`getMetadata()`、`FileMode.MEMORY` 皆仍在。

結論:**升 Java 是必要(record 需 16+)但不充分,還要改 GeoipService 一行 API**。全專案僅 GeoipService 一處用 maxmind-db。

## Goal
把 dev 地基從 Java 8 升到 **Java 17 LTS**,並適配 maxmind-db 4.1.0 API,讓 `mvn package` + 全套 E2E 綠,為 phase 2 提供健康地基。

## 變更清單(檔案級)

1. **[GeoipService.java](../../../src/main/java/com/cym/service/GeoipService.java)** L16 / L141 — API 適配
   - `Date buildDate = reader.getMetadata().getBuildDate();`
   - → `Instant buildTime = reader.getMetadata().buildTime();`(null-safe)→ `Date.from(buildTime)` 交給 `DateUtil.format(..., "yyyy.MM.dd")`
   - import `java.time.Instant`(`java.util.Date` 保留供 `Date.from`)
   - 行為等價:2.x getBuildDate 與 4.x buildTime 同源自 MMDB build_epoch,系統時區格式化一致。

2. **[pom.xml](../../../pom.xml)** — `<java.version>1.8</java.version>` → `17`;maven-compiler-plugin `<source>1.8</source>`/`<target>1.8</target>` → `17`(或整併為 `<release>17</release>`)。

3. **[.github/workflows/build.yml](../../../.github/workflows/build.yml)** — 兩個 job 的 `Set up JDK 8` / `java-version: '8'` → `17`。

4. **[Dockerfile](../../../Dockerfile)** L34 — runtime `openjdk8-jre` → `openjdk17-jre`(alpine:3.22 community repo,需確認套件名存在)。

5. **[CLAUDE.md](../../../CLAUDE.md)** — Java 8→17 記載;maxmind-db「2.1.0 勿升級」註記改寫為「已升 4.1.0,需 Java 17+,Metadata 為 record」。

## 驗證

1. `mvn clean package`(本地 JAVA_HOME=jdk-17)→ BUILD SUCCESS。
2. `npm run test:fast` 全套 E2E 綠(特別是 23-geoip-version:GeoIP build date badge,直接覆蓋改動的 readBuildDate)。
3. (可選)`docker build` 確認 openjdk17-jre 可裝、容器起得來。

## 風險

- **Alpine openjdk17-jre 套件名**:需確認 alpine:3.22 有 `openjdk17-jre`(高機率有;若無則 `openjdk17-jre-headless`)。Docker 驗證前不影響 jar build。
- **其他 Java 8-only API**:167 檔編譯先前只 1 錯(getBuildDate);升 17 後全量重編可能再現隱藏錯,build 會抓。
- **Solon 3.10.7 / 其他 dependabot 升級行為變更**:靠全套 E2E 回歸覆蓋。
- **buildTime 時區**:Instant→Date.from 用系統時區,與原 getBuildDate 一致,version badge 顯示不變。

## Out of scope
- phase 2 feature 本身(save 接後端 / 三態 mode / nginx -t)—— 地基修好後另開 worktree 進行。
- 升 Java 21(本輪選 17;未來要再升 21 另議)。
