# 自架驗證頁：CrowdSec 可疑 IP 先驗證

CrowdSec 判定為「可疑」的 IP 不直接封鎖，而是先送到驗證頁；通過的人回到原頁，持續不通過的判定為程式並封鎖（連線直接中斷，444）。
只有啟用 CrowdSec（`security` profile、設了 bouncer 金鑰與 LAPI 位址）時才有作用，只涵蓋 nginx `http` 區塊（不含 `stream`）。

## 判定規則

`docker/crowdsec/profiles.yaml` 依序比對，第一個符合的生效：

| 順序 | 條件 | 結果 |
|---|---|---|
| 1 | 同一 IP 24 小時內已有決策，又被判可疑 | 封鎖 4 小時（不給驗證機會） |
| 2 | 爬取非靜態頁（`crowdsecurity/http-crawl-non_statics`）、惡意 User-Agent（`crowdsecurity/http-bad-user-agent`）、掃描（`crowdsecurity/http-probing`） | 送驗證（captcha 決策）4 小時，不通報 AbuseIPDB |
| 3 | 其他情境 | 封鎖 4 小時並通報 AbuseIPDB |

驗證不通過的計數由 `docker/crowdsec/nwu-challenge-fail.yaml` 從 nginx access log 統計：同一 IP 約 10 分鐘內開驗證頁超過 10 次，或送出答案超過 5 次，就封鎖。

白名單（站點白名單與後台「加入白名單」）永遠不進驗證頁；被封鎖的 IP 連驗證頁也是 444。

## 兩種驗證方式

在「防護與憑證」→ CrowdSec 分頁的「驗證頁」區塊設定，儲存後要**套用設定**才生效。

- **內建（預設，離線可用）**：nginx 在每個網域的保留路徑 `/.well-known/nwu-challenge/` 直接出題（ALTCHA 工作量證明，一般手機約 1 秒）。
  頁面需要的 JavaScript 在 image 內，不從外網載入。
- **外部驗證站**：轉到你設定的網址（例如 Cloudflare Worker），外部站驗證後帶簽章的交接票回 `https://<原網域>/.well-known/nwu-challenge/callback`，
  nginx 驗票後發 cookie。外部站連不到時，有驗證決策的訪客在決策到期（4 小時）前都無法通過，不會自動退回內建。

樣式可選「自動」（打開即驗證，預設）或「勾選『我不是機器人』」。通過後的有效時間預設 60 分鐘（5–1440），cookie 綁訪客 IP，換 IP 要重新驗證。

## 金鑰

| 金鑰 | 用途 | 來源（依序） |
|---|---|---|
| Cookie 金鑰 | 簽「通過驗證」cookie 與內建題目，只在本機 | secrets 檔 `/run/secrets/nwu_challenge_secret` → 環境變數 `NWU_CHALLENGE_SECRET` → 套用設定時自動產生並存到 `crowdsec/challenge_cookie_secret`（權限 600） |
| 交接票金鑰 | 與外部驗證站共用，簽交接票 | secrets 檔 `/run/secrets/nwu_challenge_ticket_secret` → 環境變數 `NWU_CHALLENGE_TICKET_SECRET`；不自動產生 |

後台只顯示遮罩值與來源。選「外部驗證站」但沒有交接票金鑰時，後台不讓你儲存；已儲存後才拿掉金鑰，套用設定時驗證頁不載入（後台狀態欄會說明原因），CrowdSec 照常封鎖。

產生交接票金鑰並交給 Worker：

```bash
openssl rand -hex 32            # 填到 docker/.env 的 NWU_CHALLENGE_TICKET_SECRET，docker compose up -d
npx wrangler secret put NWU_CHALLENGE_TICKET_SECRET   # 在 Worker 專案貼上同一把
```

交接票金鑰外洩時，拿到它的人能替自己的 IP 簽出交接票、換到 cookie，等同繞過驗證，請立即更換。

## 部署前要注意的三件事

1. **純 HTTP 站台不出題**：瀏覽器只在 HTTPS 提供驗證需要的 `crypto.subtle`，所以 `http://` 站台遇到有驗證決策的訪客一律回 429 說明頁（附訪客 IP 與時間，請真人聯絡管理者加白名單）。
   前面有 TLS 終結代理（反向代理把 HTTPS 轉成 HTTP 給 nginxWebUI）的站台，nginx 看到的也是 HTTP，會走這條規則。
2. **站台開 Cloudflare 代理時要先設 `real_ip`**：否則 nginx 看到的是 Cloudflare 的 IP，cookie 與交接票的 IP 比對會失敗、無法通過。
   在 http 參數加 `include /etc/nginx/geoip/realip.conf;`（Cloudflare IP 清單由「GeoIP」頁的 Cloudflare 下載與每週排程維護，
   內含 `set_real_ip_from` 與 `real_ip_header CF-Connecting-IP`）。外部驗證站（Worker）本身不需要這個設定。
3. **外部驗證站先預覽再實測**：後台「預覽驗證頁」會直接開外部網址；確認頁面正常後，用 `cscli decisions add -i <測試 IP> -t captcha -d 10m` 對自己的 IP 實際走一次。

## 交接票格式（給外部驗證站實作）

```
token   = base64url(payload) + "." + base64url(HMAC-SHA1(交接票金鑰, payload))     # base64url 不補 =
payload = "nwu1" \n kind \n exp \n ip \n host \n path \n nonce                     # 以換行 \n 連接
```

| 欄位 | 請求票（nginx → 外部站，`?t=`） | 回應票（外部站 → nginx，`callback?token=`） |
|---|---|---|
| kind | `req` | `resp` |
| exp | 現在 + 300 秒（epoch 秒） | 現在 + 60 秒；nginx 不接受超過 300 秒後到期的票 |
| ip | 訪客 IP（nginx 看到的） | 照抄請求票（外部站不比對訪客 IP：瀏覽器連兩邊可能分走 IPv4／IPv6） |
| host | 原網域（`$host`） | 照抄請求票 |
| path | 要導回的同網域路徑 | 照抄請求票 |
| nonce | 隨機 32 位 hex | 外部站新產生的隨機值，nginx 記錄後不接受重送 |

外部站驗證請求票（簽章、`kind=req`、未過期）並完成人機驗證後，轉址到 `https://<host>/.well-known/nwu-challenge/callback?token=<回應票>`。
兩端共用的測試資料在 `docker/lua/test/ticket-vectors.json`。

## 測試

```bash
sh docker/test/lua-unit.sh <nginxwebui image>          # Lua 純函式、交接票測試資料
sh docker/test/challenge-smoke.sh <nginxwebui image>   # image 內 nginx：出題、驗答、cookie、交接票（stub LAPI）
sh docker/test/crowdsec-challenge-e2e.sh <nginxwebui image> <crowdsec image>   # 整個 stack、無網路，約 4 分鐘
```
