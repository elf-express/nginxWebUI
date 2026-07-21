# /init：CLAUDE.md 與相鄰文件同步現況（2026-07-21）

## Context

使用者跑 `/init`。repo 已有完整 CLAUDE.md，因此本次任務是「找出文件與現況的落差並修正」，不是重寫。兩個 Explore agent 已逐項核對 CLAUDE.md 對 codebase 的所有可驗證主張，Plan agent 產出逐節修訂文字。核心落差：

1. E2E specs 已到 `32-firewall-ip-tabs`（文件停在 27），28-31 為 http-param phase 2/3 系列。
2. `controller/api/`（11 個 REST API controller）完全未記載，「controllers 全在 adminPage/」說法不完整。
3. ProtectionCert 頁已重構為 6 tab（GeoIP 資料庫／黑名單／白名單／GeoIP 國別／ASN／憑證）。
4. DenyAllow `type`（deny/allow）黑白名單拆分整組功能未入 Feature Inventory。
5. Release Flow 自相矛盾：`scripts/release.sh` 分支閘只准 `dev|hotfix/*`，但文件教在 `release/x.y.z` 上跑（必報錯）；且依使用者既定做法，主要流程應為 `git push origin dev:master` 直推（不走 PR，避免 GitHub 刪 dev）。
6. README.md/README_TW.md line 115、127 仍寫 CrowdSec「官方 image + bind-mount config」——實際是自建 `nginxwebui-crowdsec`（config 烤進 image）。
7. `scripts/update-geoip-cf.sh` 未記載。
8. 殘留物：根目錄 `artifact-34772d20.png` 未被 gitignore；`docker/docker-compose.yml` 有一段未 commit 的壞掉 `networks:` 區塊（縮排錯誤掛在 volumes 下，`driver: bridge` 會讓建 volume 失敗）。

使用者已決定：(a) 還原 docker-compose.yml 那段未 commit 修改；(b) `.gitignore` 加 `/artifact-*.png` 並刪除殘留 PNG。

其餘核對全數相符（package.json scripts、pom 5.2.5/Java 17/maxmind 4.1.0、compose port 12300/PG18/security profile、playwright 18080、helpers.js 動態 jar、build.yml/claude-code-review.yml 描述）——不動。

## 實作步驟

### 1. CLAUDE.md（7 處，就地修改）
- 1a Directory Structure：`controller/` 註解改為 `adminPage/ (28 page controllers) + api/ (11 REST API controllers)`。
- 1b Backend conventions：controllers 主張改為 adminPage/ + api/ 並列（api/ 列出 *Api + Token/Upload），root 仍無 controller。
- 1c Testing：spec 範圍改 `01-login` … `32-firewall-ip-tabs` (contiguous)。
- 1d Docker conventions 末尾加 bullet：`scripts/update-geoip-cf.sh`（GeoLite2 + Cloudflare ips 下載到 /etc/nginx/geoip；entrypoint 啟動跑一次 + crontab 每週三、六；7 天內已更新則跳過）。寫入前先讀 script header 核對措辭。
- 1e Release Flow 整節改寫：主要路徑 = 在 dev 跑 release.sh（分支閘只准 dev|hotfix/*）後 `git push origin dev:master` 直推；PR variant 改為「先 bump 於 dev、再開 release/x.y.z 分支」；hotfix 從 master 開、push hotfix:master。
- 1f Feature Inventory 4 行：UI/UX 追加 http param panel phase 2/3（specs 28-31）；Security 的 DenyAllow 子句擴為 black/white lists（type、reference dropdown 過濾、跨型別 IP 衝突拒絕、reference 遷移）+ firewall 6 tabs；GeoIP DB module 行加 mtime cache、evaluateStatus/reverifyAll、Cloudflare Real-IP auto-download、downloadCloudflare route；Deploy/Test 加 auto GitHub Release 與 claude.yml @claude responder。
- 1g 其餘節不動。

### 2. README.md + README_TW.md（各 2 處，EN/TW 同步）
line 115 與 127 的「CrowdSec 官方 image + bind-mount config」改為「兩個 image 都自建、config 烤進 nginxwebui-crowdsec、不需 bind-mount」。

### 3. .gitignore
`/specsnap-*.png` 後追加 `/artifact-*.png`。

### 4. 清理（使用者已核准）
- `git checkout -- docker/docker-compose.yml`（還原壞掉的 networks 區塊）。
- 刪除 untracked 的 `artifact-34772d20.png`。

### 5. 驗證
重讀修改各節確認 markdown 結構（尤其 Release Flow 巢狀 code fence）；`git status` 只剩預期修改；`git diff --stat` 過目。文件-only，不需 mvn/npm test。

### 6. Commit + Push
單一 commit `docs(init): sync CLAUDE.md/README to current codebase + cleanup`，直接 push origin dev。

## 明確不改
- `scripts/release.sh`、`scripts/update-geoip-cf.sh` 本身（文件向 script 對齊）。
- `messages*.properties`、Java/JS/模板/測試。
- `docs/superpowers/plans/` 既有 dated 檔。
- README 版本歷史表與功能清單（已核對正確）。
