# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- SPECKIT START -->
For additional context about the active work, see plans under [docs/superpowers/plans/](docs/superpowers/plans/) — the most recent dated file is usually the current focus.
<!-- SPECKIT END -->

> 本檔以英文為主、關鍵處附中文註解。**本檔只放「每次都要知道」的東西；細節在 `docs/memory/`。**

## 專案記憶 / Project memory — 動手前先讀對應那一頁

這份檔案刻意保持精簡（150 行內）。下表每一頁都是這裡的延伸，**要動到那個領域就去讀那一頁**，
不要憑印象作業 —— 這些檔案裡的每一條都是踩過才寫下來的。

| 讀這頁 | 什麼時候 |
|---|---|
| [docs/memory/stack-and-layout.md](docs/memory/stack-and-layout.md) | 要找程式放哪裡、要寫 DB 存取、要加 CRUD 頁 |
| [docs/memory/conventions.md](docs/memory/conventions.md) | 要改前端／後端／測試／Docker／參數模板 |
| [docs/memory/dev-run-deploy.md](docs/memory/dev-run-deploy.md) | 要跑起來、要改啟動參數、要動 compose |
| [docs/memory/release-flow.md](docs/memory/release-flow.md) | **要發版**（誰是觸發器很容易記錯，先看） |
| [docs/memory/mcp.md](docs/memory/mcp.md) | 要動 nginx 文件 MCP（`/mcp` 端點、索引、設定檢查器） |
| [docs/memory/feature-inventory.md](docs/memory/feature-inventory.md) | 想知道這個 fork 相對上游多了什麼 |

> **Code navigation:** this repo is indexed by CodeGraph (`.codegraph/codegraph.db`).
> Reach for `codegraph_explore` (MCP) or `codegraph explore "<question>"` (shell) BEFORE grep/find/Read —
> one call returns verbatim source + call paths in far fewer tokens than a grep/read loop.

## Overview

nginxWebUI is a web tool that simplifies NGINX configuration — users fill in UI forms instead of
hand-writing `nginx.conf` (reverse proxy, SSL, load balancing, security hardening).

Entry point: [com.cym.NginxWebUI](src/main/java/com/cym/NginxWebUI.java) — `@SolonMain` + `@EnableScheduling`.

> 注意：啟動時會先殺掉同名舊 jar process 再 `Solon.start()`。

**Stack in one line:** Java 17 + **Solon 3.10.7（不是 Spring Boot）** · Layui + jQuery + Freemarker
（伺服器端渲染，不是 SPA）· SQLite／PostgreSQL／MySQL 走自寫的 `SqlHelper`（不是 JPA）·
Maven fat jar · Playwright E2E + JUnit 5 單元測試 · Docker Compose（PG + 可選 CrowdSec）。
細節見 [stack-and-layout.md](docs/memory/stack-and-layout.md)。

## 核心原則 / Core principles

這六條沒有例外，其餘慣例在 [conventions.md](docs/memory/conventions.md)。

1. **不要破壞既有商業邏輯** —— 改動以 UI 打磨或純疊加的功能為主。
2. **多語言** —— 每個新的使用者可見字串都要同步改三份 `messages*.properties`
   （簡／繁／英；CJK 值用 `\uXXXX` escape，檔案是 ISO-8859-1）。
3. **自動化測試** —— 每個新增／變更的功能都要附一支 Playwright 測試。
4. **零風險優先** —— 能純前端或純疊加就不要動既有路徑。
5. **A11y 底線** —— 不要用 `<a href="javascript:...">` 當動作連結，用 `<button type="button">`。
   header／sidebar／表格操作／modal／captcha 都已遷移，由
   [tests/e2e/27-a11y-buttons.spec.js](tests/e2e/27-a11y-buttons.spec.js) 把關。
   只有圖示的控制項需要 `aria-label`；新頁面需要 `<h1>` 地標。
6. **離線優先的前端** —— 第三方 lib 要 vendor 進 `static/lib/`，不要從公開 CDN 載
   （這是自架的管理工具，經常部署在無外網環境）。由
   [tests/e2e/26-offline-no-cdn.spec.js](tests/e2e/26-offline-no-cdn.spec.js) 把關。

## 發版：**push 到 master 才發版，push 到 dev 不會**

這一點最常被記錯，所以放在主檔。完整流程與依據見 [release-flow.md](docs/memory/release-flow.md)。

`.github/workflows/build.yml` 的 push 觸發分支是 `[master, dev]`，兩者都跑 CI，
但 `Release image` 與 `Auto-tag + GitHub Release` 兩個 job 被閘在 `github.ref == 'refs/heads/master'`
（`:41` 與 `:121`）。所以 **dev 的 push 只有 build & test**。
`release/*` 分支**不是**發版觸發器，它只是「通往 master 的一條路」，好讓 claude-code-review 有機會審。

```bash
git checkout dev && git pull origin dev
scripts/release.sh 5.2.9        # 只改 pom 的 nginxWebUI <version> + commit;不打 tag、不 push
git push origin dev             # 同步 origin/dev（此步不發版）
git push origin dev:master      # ← 這一步才發版：build+push 2 images + auto-tag + GitHub Release
```

> **不要開 dev → master 的 PR** —— GitHub merge 後的「Delete branch」會刪掉常駐 `dev`。

## 每次都會用到的指令

```bash
mvn clean package -DskipTests                 # build（跑測試前必須先有 jar）
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-<version>.jar --server.port=8080   # run
npm test            # E2E (headed)            #   npm run test:fast (headless/CI)
npm run report      # test report (port 9400)
cd docker && docker compose up -d --build     # docker build+run
codegraph explore "<question or symbol>"      # 1-call code lookup（優先於 grep/find）
```

> 注意：E2E 會自動起獨立 server（port 18080）+ 獨立 SQLite，不碰 `./dev-home/`。
> `tests/e2e/helpers.js` 動態解析 `target/nginxWebUI-*.jar` —— 所以跑過 `mvn clean` 之後
> 一定要先 `mvn -o package -DskipTests` 把 jar 建回來，否則測試會起不來。

## 幾條「做了會出事」的紅線

- **不要在 image 裡裸跑 `nginx -t`**（不帶 `-c`）—— Alpine 的 `/etc/nginx/nginx.conf` 會 include
  順序壞掉的套件設定。要測 UI 產生的設定：
  `nginx -t -c /home/nginxWebUI/temp/nginx.conf -p /home/nginxWebUI/temp/`。
- **不要**把 HTTP 的 `if` / `$request` log_format 放進 stream 模板（見 [docs/nginx結構.md](docs/nginx結構.md)）。
- **不要**把已剔除的高風險 nginx 模組加回 Dockerfile（`upstream_fair`、舊 `geoip`、`perl`、`upload*`…）。
- **不要**用 `scripts/auto-translate.js` 批次改 `docs/nginxdocumentation/` 的 md —— 會誤翻
  `nginx -s quit` 這類字面值。翻譯規範見 [TRANSLATION.md](docs/nginxdocumentation/TRANSLATION.md)。
- 改 `AppFilter` 要格外小心：它在每個 request 的必經路徑上，守著整個後台。

## Docs

- **README：** `README.md`=英文（主）· `README_TW.md`=繁中；語言切換連結雙向，改內容須同步兩版。
- [nginx 設定結構](docs/nginx結構.md) — 區塊樹、http vs stream 差異、zone 命名、宣告/使用配對。
- [nginx 官方文檔校對索引](docs/nginxdocumentation/README.md) · [翻譯規範](docs/nginxdocumentation/TRANSLATION.md)
- [Improvement plans & reports](docs/superpowers/plans/) ·
  [Playwright guide](docs/superpowers/plans/playwright-guide.md) ·
  [Docker guide](docs/superpowers/plans/docker-guide.md) ·
  [Dev/release workflow](docs/superpowers/plans/2026-05-21-dev-release-workflow.md)
