# 外部驗證 Worker
[English](README.md)

Worker 驗證 Turnstile 後簽發 60 秒回應票。原站 nginx callback 才發出綁定 IP 的 nwu_pass cookie 並返回票內原路徑；Worker 不持有 cookie 金鑰，不需要資料庫、KV 或執行期套件。

## 設定與部署
使用 Node 24 與已提交 lockfile，Wrangler 精確釘選 **4.152.0**。指令從倉庫根目錄開始，切換後均留在 Worker 目錄。dry-run、secret 與部署使用同一份 wrangler.toml，固定身分如下：
```toml
name = "nginxwebui-challenge"
main = "src/index.js"
compatibility_date = "2026-10-24"
```

在 Cloudflare 建立 **Managed** Turnstile widget，只允許實際 Worker hostname（自訂網域或 workers.dev）。在 [vars] 設定公開 TURNSTILE_SITE_KEY 與不含 scheme／path／port 的 TURNSTILE_HOSTNAME。此處填 Worker hostname，非原站 hostname。Managed 是否需要額外互動由 Cloudflare 決定。

登入、設定 secret、部署由 Eddie 或另經明確授權的 session 執行，本機 build 只是 dry-run：
```sh
cd cloudflare/challenge-worker
npm ci
npm test
npm run build
npx wrangler login
npx wrangler secret put NWU_CHALLENGE_TICKET_SECRET
npx wrangler secret put TURNSTILE_SECRET_KEY
npx wrangler deploy
```

第一個提示輸入原站既有交接票金鑰，第二個輸入 Turnstile widget secret。不得放進程式、指令參數、日誌或截圖。NWU_CHALLENGE_SECRET 是原站 cookie 金鑰，只留原站，Worker 完全不用。本機開發可將 .dev.vars.example 複製為已忽略的 .dev.vars；必填值為空一律 503，不簽票。

## 串接與正式驗收
原站外部驗證網址設定 https://YOUR_WORKER_HOST/?style=auto 或 https://YOUR_WORKER_HOST/?style=checkbox。auto 立即執行，checkbox 等訪客勾選本頁有 label 的核取方塊後才執行 Managed widget。兩者均傳 action: "nwu_challenge" 與 cData: nonce，Siteverify 的 action／cdata／hostname 必須一致。

先開不帶票的兩個網址，確認只顯示預覽、不能提交且不執行 Turnstile。再於真實 HTTPS 原站建立 captcha decision，實測「含 query 原頁 → Worker → 真人 Turnstile → 原站 callback → nwu_pass cookie → 原頁」。記錄部署版本／時間、兩種樣式與遮罩證據。沒有帳號、實際 hostname、匹配金鑰、真實 widget，不能宣稱正式驗收；mock、測試鍵與 dry-run 均不算。

原站 nginx 自己必須看到 HTTPS；TLS 終結代理轉入 HTTP 時 ngx.var.scheme=http，原站仍回 429。Cloudflare 代理原站由管理者確認既有 /etc/nginx/geoip/realip.conf include 生效，只信任 Cloudflare 網段，使用 real_ip_header CF-Connecting-IP。不可重複 include 或信任任意代理，詳見[原站操作說明](../../docs/crowdsec-challenge.md)。Worker 連線即使使用不同 IPv4／IPv6，仍沿用簽票的原站 IP。

## 測試、排障與回復
從倉庫根目錄執行：
```sh
node cloudflare/challenge-worker/scripts/build-messages.cjs --check
node cloudflare/challenge-worker/test/nginx-roundtrip.cjs --image nginxwebui:e387
npm run test:fast -- challenge-worker.spec.js
```

互通測試使用含已合併 E-387 Lua 的 image，只建立自己命名的暫存 Docker 容器，不開 host port，使用測試金鑰並在結束清理。瀏覽器攔截官方 API，僅測試 adapter 注入假 Siteverify；正式程式沒有測試旁路。既有 Playwright harness 仍需先建 Java jar。

翻譯來源是三份 src/main/resources/messages*.properties。修改 key 後執行不帶 --check 的字典建置器，提交 src/messages.generated.js。check 只比對、不寫檔，缺 key 即失敗。

429 代表票無效／過期／竄改、Turnstile 失敗／重複、hostname／action／cdata 不符或非同源提交。要求相同 Origin；no-referrer 造成 Origin: null 時，還須同時有 Sec-Fetch-Site: same-origin 與 Sec-Fetch-Mode: navigate，不支援的瀏覽器仍拒絕。503 代表缺設定或 Siteverify 網路／HTTP／JSON 錯誤，5 秒逾時、不自動重試。票過期請回原站取新票；widget 失敗有重試。外站不可用時不自動回退內建模式。

交接票金鑰輪替須協調原站與 Worker 維護時段，舊票立刻失效；Turnstile secret 隨 widget 輪替並同步 Worker secret。不可重設 cookie 金鑰。回復由 Eddie 在 Cloudflare 回退已知 Worker 版本，或明確切原站至內建模式並套用設定。回退後核對 secret 相容性，程式回退不能取代金鑰協調。

[Wrangler commands](https://developers.cloudflare.com/workers/wrangler/commands/), [Turnstile setup](https://developers.cloudflare.com/turnstile/get-started/widget-management/dashboard/), [Worker rollbacks](https://developers.cloudflare.com/workers/versions-and-deployments/rollbacks/).
