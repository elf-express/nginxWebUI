# 5.2.3 發版 + image pipeline 重新設計(design spec)

> 日期:2026-07-05 · 分支:`worktree-release-5-2-3`(從 dev `4307a642`) · 目標版本:**5.2.3**
> 狀態:**design approved**(使用者 2026-07-05 全數同意:「全部都要」)
> 後續:writing-plans → subagent-driven-development

## Context / 動機

- **發版模型**:目前是 tag 觸發(`scripts/release.sh` 打 tag → CI docker job `if refs/tags/v` → build image,版本從 tag 抓)。使用者/組織的習慣是「**主線(master)觸發發版**」,不是手動打 tag。
- **Image 集**:單一 image 重構(`5c8a927`)後只自建 `nginxwebui`,crowdsec 改用官方 image + bind-mount config。使用者要**恢復自建 `nginxwebui-crowdsec`**,並確定放棄 grafana/promtail(ghcr 上只留 nginxwebui + nginxwebui-crowdsec 兩個 package)。
- **geoip**:image 不 bake MMDB;`entrypoint.sh:4` 首啟才跑 `update-geoip-cf.sh` 下載(P3TERX 鏡像)。**離線/air-gapped 就抓不到** → 與本 fork「離線優先」矛盾(vendored CDN lib 卻讓 geoip 資料靠 runtime 連 github)。
- **CI 架構**:build `linux/amd64,linux/arm64`。arm64 走 QEMU 模擬、慢又脆,而部署目標是 x86,arm64 非必要。

## Goals

1. 發版改**主線觸發、版本閘控、CI 自動打 tag**。
2. 自建 **2 個 image**:`nginxwebui` + `nginxwebui-crowdsec`。
3. geoip **build-time bake**(離線開箱可用)+ 保留 runtime 刷新。
4. build **只 amd64**。
5. 版本 **5.2.3**。

## Non-goals(YAGNI)

- 不做 grafana/promtail image(已放棄;package 由使用者手動刪)。
- 不改 module-filter 功能(已在 dev)。
- 不做 arm64。
- 不動 nginx 業務邏輯。

## 設計

### A. 發版模型:主線觸發 + 版本閘控 + 自動 tag

- **觸發**:`build.yml` docker job 改成 `push` 到 `master` 觸發(取代「僅 `startsWith(github.ref, 'refs/tags/v')`」)。
- **版本來源**:從 `pom.xml` 抓 nginxWebUI 版本(沿用既有 `grep -A1 'artifactId>nginxWebUI' | grep version` 邏輯),不再從 tag。
- **版本閘控**:新增 step 查該版本是否已在 ghcr(`docker manifest inspect ghcr.io/elf-express/<image>:<version>`)。
  - 不存在 → build + push(`:<version>` + `:latest`)。
  - 已存在 → 只跑 build/test,**不重發**(避免非發版的 master merge 重複覆蓋同版本)。
  - 查詢失敗(網路等)→ **視為不存在**(寧可重發;push 同 tag 冪等、無害)。
- **自動 tag**:image push 成功且該版本為新 → CI `git tag v<version> && git push origin v<version>`(用 `GITHUB_TOKEN`,對本 repo 有 write)。打前先確認 tag 不存在。
- **日常流程**:
  ```
  dev 開發 → scripts/release.sh 5.2.3 (只 bump pom + commit)
    → git push origin dev:master (master 是 dev 祖先,可 ff)
    → master push 觸發 build.yml → 偵測新版本 → build+push image + 自動 tag v5.2.3
  ```

### B. Image 集:nginxwebui + nginxwebui-crowdsec

- `build.yml` matrix 兩個 image:
  | name | dockerfile | context | needs_mvn |
  |---|---|---|---|
  | nginxwebui | Dockerfile | . | true |
  | nginxwebui-crowdsec | docker/crowdsec/Dockerfile | docker/crowdsec | false |
- **還原 `docker/crowdsec/Dockerfile`**(內容 = `5c8a927` 刪除前那份):
  ```dockerfile
  FROM crowdsecurity/crowdsec:v1.7.8
  COPY acquis.yml     /etc/crowdsec/acquis.yaml
  COPY profiles.yaml  /etc/crowdsec/profiles.yaml
  COPY abuseipdb.yaml /etc/crowdsec/notifications/abuseipdb.yaml
  ```
  (config 檔目前仍在 `docker/crowdsec/`,直接沿用。)
- **`docker-compose.yml` crowdsec service**:`image` 改 `ghcr.io/elf-express/nginxwebui-crowdsec:${NGINX_WEBUI_VERSION:-latest}`;移除 3 個 config bind-mount(已烤進 image);保留 runtime secret env（`BOUNCER_KEY_nginx`、`ABUSEIPDB_API_KEY`）+ log(ro)/data volume + healthcheck。
- 版本閘控 + 自動 tag 對兩 image 都適用(各查各的 ghcr tag)。
- grafana/promtail 的 ghcr package 由使用者手動刪(非本 repo 範圍)。

### C. geoip build-time bake

- **root `Dockerfile`** 新增 build step:`curl -fL --retry 3` 抓 3 個 MMDB(Country/City/ASN,P3TERX 鏡像,URL 同 `update-geoip-cf.sh`)進 `/etc/nginx/geoip/`。
- **保留** `entrypoint.sh` 首啟 `update-geoip-cf.sh`(7 天 freshness:bake 的 <7 天會跳過、>7 天自動刷新)+ cron(週三六)。
- 效果:離線 → image 自帶 MMDB、開箱可用;線上 → refresh 保新鮮。
- 授權:GeoLite2 經 P3TERX 鏡像(與現行 runtime 下載同源),bake 進 public image = 同樣的再散布,行為一致。

### D. build 只 amd64

- `build.yml` `platforms: linux/amd64,linux/arm64` → `linux/amd64`(兩 image 皆是)。

### E. `scripts/release.sh` 縮成 bump-pom-only

- 移除「打 tag」步驟(tag 改由 CI 自動打)。
- 保留:分支檢查、工作區乾淨檢查、改 pom nginxWebUI 版本、parent 版本不動的安全檢查、commit。
- 輸出提示改為:「下一步 `git push origin dev:master`,CI 會自動 build + tag」。

### F. 連帶更新

- **`.github/dependabot.yml`**:docker ecosystem 現指 `/`(root Dockerfile,已於 `4307a642` 修)。**新增第二個 docker ecosystem 指 `/docker/crowdsec`**(crowdsec Dockerfile),讓 `crowdsecurity/crowdsec` base 版本也被 Dependabot 追。
- **`CLAUDE.md` / 部署文件**:更新為「主線觸發發版、2 image、geoip bake、amd64-only」;release flow 段改寫。

## 資料流(發版)

```
dev: scripts/release.sh 5.2.3   (bump pom 5.2.2 -> 5.2.3 + commit)
  → git push origin dev:master  (ff)
  → master push 觸發 .github/workflows/build.yml
     build job:  mvn compile (JDK17) 驗證能編譯
     docker job (matrix: nginxwebui, nginxwebui-crowdsec):
        version = pom.xml
        if !(ghcr 已有 :version):
           buildx (linux/amd64) → push :version + :latest
     若版本為新: git tag v5.2.3 + push
```

## 測試

- **既有 E2E**:不受影響(release/image 屬 CI/Docker 層,非 app 功能)。跑 `npm run test:fast` 確認零回歸(注意:PATH java 需 17)。
- **版本閘控**:push master(新版本)→ 兩 image build+push+tag;再 push(同版本,無 pom 變更)→ 只 build/test、不重發。
- **geoip bake**:build image 後 `docker run --rm <image> ls -l /etc/nginx/geoip` 應見 3 個 MMDB;斷網起容器仍有 geoip。
- **crowdsec image**:build 後檢查 `/etc/crowdsec/acquis.yaml` 等 config 已烤進。
- **可測性限制**:master-triggered CI 邏輯要真的 push master 才完整驗得到;實作階段先在 worktree 分支審 build.yml 邏輯 + 用一次真實 master push 驗收。

## Risks & Mitigations

| 風險 | 緩解 |
|---|---|
| 版本閘控查 ghcr 失敗誤判 | 查詢失敗 → 視為「不存在」→ 重發(push 同 tag 冪等、無害),不會漏發 |
| 自動 tag 撞既有 tag | 打前先 `git rev-parse` 確認不存在才打 |
| master 未來分岔(非 ff) | 發版前確認 ff;目前 master(`05d98a6`)是 dev 祖先 |
| geoip bake image +~80MB | 可接受(image 已含 nginx + 30 模組) |
| 自建 crowdsec 與官方行為差異 | 自建 = 官方 base + config bake,行為等同官方 + 自訂 config |
| 每次 master push 都跑 docker job(即使不發) | 版本閘控只在「新版本」才 build+push;非新版本只 manifest inspect + 早退,成本低 |

## 版本

5.2.3(pom 由 5.2.2 → 5.2.3;5.2.2 image 為 tag 重打的過渡版,5.2.3 為本次重構正式版)。
