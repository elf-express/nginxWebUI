# 自架驗證頁：CrowdSec 可疑 IP 先驗證

CrowdSec 判定為「可疑」的 IP 不直接封鎖，而是先送到驗證頁；通過的人回到原頁，持續不通過的判定為程式並封鎖（連線直接中斷，444）。
只有啟用 CrowdSec（`security` profile、設了 bouncer 金鑰與 LAPI 位址）時才有作用，只涵蓋 nginx `http` 區塊（不含 `stream`）。

## 判定規則

`docker/crowdsec/profiles.yaml` 依序比對，第一個符合的生效：

| 順序 | 條件 | 結果 |
|---|---|---|
| 1 | 同一 IP 24 小時內已有決策，又被判可疑 | 封鎖 4 小時（不給驗證機會） |
| 2 | 爬取非靜態頁（`crowdsecurity/http-crawl-non_statics`）、惡意 User-Agent（`crowdsecurity/http-bad-user-agent`）、掃描（`crowdsecurity/http-probing`） | 送驗證（captcha 決策）4 小時，不通報 AbuseIPDB |
| 3 | 驗證不通過（`nginxwebui/challenge-fail`、`nginxwebui/challenge-fail-answer`），且 24 小時內沒有其他決策 | 封鎖 4 小時，不通報 AbuseIPDB |
| 4 | 其他情境 | 封鎖 4 小時並通報 AbuseIPDB |

驗證不通過的計數由 `docker/crowdsec/nwu-challenge-fail.yaml` 從 nginx access log 依狀態碼統計：同一 IP 約 10 分鐘內看到驗證頁（回應 200）超過 10 次，
或答案、交接票被拒（回應 429）超過 5 次（POST `verify`、GET/HEAD `callback` 都算），就封鎖。通過驗證（303）不計數。
正常情況下驗證不通過的 IP 已經有驗證決策，會由第 1 條封鎖；第 3 條只接住其餘情況，兩者都不通報 AbuseIPDB。

沒有驗證決策的 IP 不出題：在保留路徑上除了頁面用的靜態檔（`altcha.js`、`altcha.css`、`altcha-worker.js`、`page.js`、`page.css`，不計數）照常提供之外，一律 303 導回同網域路徑（頁面與 `verify` 導回表單的 `r`，`callback` 導回交接票內的路徑，其他導回 `/`），
不出題、不驗答、不發 cookie，也不計數。這樣第三方網頁就算讓訪客的瀏覽器反覆開驗證頁或送答案，也無法讓訪客被封鎖或被通報。
驗證頁沒有載入時（升級後還沒套用設定，或下面說的退回模式），只要 bouncer 有載入，保留路徑一樣由 nginx 直接 303 導回（靜態檔回 404），不交給網站本身。
nginxWebUI 沒有設定 CrowdSec（bouncer 沒有載入）時，保留路徑會交給網站本身；如果網站對這個路徑回 200（例如單頁應用把所有路徑都導到首頁），
第三方網頁仍可能觸發 `nginxwebui/challenge-fail` 封鎖（第 3 條，不通報 AbuseIPDB）。

白名單（站點白名單與後台「白名單 IP」）永遠不進驗證頁；被封鎖的 IP 連驗證頁也是 444。
IP 本身有驗證決策、但所在網段另有決策時（例如 ASN 封鎖推送的網段決策、CrowdSec 白名單網段），由網段決策決定：
bouncer（stream 模式）查決策時由最精確的網段往外找、找到第一筆就停，IP 本身的驗證決策會蓋住外圍網段的決策，所以 nginx 在 bouncer 回「驗證」時
會照同樣的順序再找一次、跳過驗證決策，由下一筆決定：CrowdSec 白名單就放行（和白名單 IP 一樣，不出題），封鎖就 444（帶著已通過驗證的 cookie 也一樣），
沒有其他決策才照常驗證。所以除了驗證決策之外，封鎖與白名單誰優先都和沒有驗證頁時一樣，由最精確的網段決定。

## bouncer 補丁

nginx image 在安裝官方 lua-cs-bouncer（`CS_NGINX_BOUNCER_VERSION=v1.2.3`）後，會用 `docker/lua/patches/cs-stream-delete-by-type.sh` 修改它的 `plugins/crowdsec/stream.lua`。
原版在 stream 模式處理到期或刪除的決策時只看 IP，不看類型；同一 IP 先拿到驗證決策、之後又被封鎖時，驗證決策到期會連同封鎖一起從 bouncer 快取刪掉，封鎖提早結束（最多少 4 小時）。
補丁後只有快取中的決策類型和被刪的決策相同時才刪除。
原版收到新決策時也直接覆蓋同一 IP 的快取；IP 還在一筆較長的封鎖期間（例如 25 小時前下的 168 小時封鎖，已超出第 1 條的 24 小時回溯）又拿到驗證決策時，
封鎖會被驗證蓋掉。補丁後新的驗證決策不覆蓋快取中的封鎖。

補丁腳本會先檢查 bouncer 版本是 `v1.2.3`、要替換的兩行原始程式碼（整行比對）各恰好出現一次，補丁後再確認新內容各出現一次、檔案行數符合、
LuaJIT 能編譯，任何一項不符就讓 image build 失敗。
升級 bouncer 版本時 build 會在這裡停下，請對照新版 `stream.lua` 重新確認補丁（或上游已修正就移除補丁），再更新腳本裡的版本。

## 兩種驗證方式

在「防護與憑證」→ CrowdSec 分頁的「驗證頁」區塊設定，儲存後要**套用設定**才生效。

- **內建（預設，離線可用）**：nginx 在每個網域的保留路徑 `/.well-known/nwu-challenge/` 直接出題（ALTCHA 工作量證明，一般手機約 1 秒）。
  頁面需要的 JavaScript 在 image 內，不從外網載入。
- **外部驗證站**：轉到你設定的網址（例如 Cloudflare Worker），外部站驗證後帶簽章的交接票回 `https://<原網域>/.well-known/nwu-challenge/callback`，
  nginx 驗票後發 cookie。外部站連不到時，有驗證決策的訪客在決策到期（4 小時）前都無法通過，不會自動退回內建。

樣式可選「自動」（打開即驗證，預設）或「勾選『我不是機器人』」。通過後的有效時間預設 60 分鐘（5–1440），cookie 綁訪客 IP，換 IP 要重新驗證。
後台「預覽驗證頁」的 ALTCHA 與頁面檔改由後台網址（需登入）提供；image 內沒有 ALTCHA 檔時（例如不是用 Docker 執行）預覽只看不到驗證元件。

## 網站送 Content-Security-Policy 時

驗證頁與三個訊息頁不含 inline script 與 style：三語字串放在不會執行的 `<script type="application/json">`，
script 與樣式是保留路徑上的同源檔案（`page.js`、`page.css`）。ALTCHA 用官方給嚴格 CSP 用的 `dist_external` 版本，
樣式（`altcha.css`）與 Web Worker（`altcha-worker.js`，由元件的 `workerurl` 指定）都是同源檔案；預設版本會插入 `<style>`、用 `blob:` 網址啟動 Worker，
在 `default-src 'self'` 下會被擋。
所以網站套用內建的 Security Headers 範本（`Content-Security-Policy "default-src 'self'"`）時驗證頁照常可用，不需要放寬網站的 CSP。

## 金鑰

| 金鑰 | 用途 | 來源（依序） |
|---|---|---|
| Cookie 金鑰 | 簽「通過驗證」cookie 與內建題目，只在本機 | secrets 檔 `/run/secrets/nwu_challenge_secret` → 環境變數 `NWU_CHALLENGE_SECRET` → 套用設定時自動產生並存到 `crowdsec/challenge_cookie_secret`（權限 600） |
| 交接票金鑰 | 與外部驗證站共用，簽交接票 | secrets 檔 `/run/secrets/nwu_challenge_ticket_secret` → 環境變數 `NWU_CHALLENGE_TICKET_SECRET`；不自動產生 |

後台只顯示遮罩值與來源。選「外部驗證站」但沒有交接票金鑰時，後台不讓你儲存；已儲存後才拿掉金鑰，套用設定時驗證頁不載入（後台狀態欄會說明原因），CrowdSec 照常封鎖。

產生交接票金鑰並交給 Worker：

```bash
openssl rand -hex 32            # 隨機字串，填到 docker/.env 的 NWU_CHALLENGE_TICKET_SECRET，docker compose up -d
npx wrangler secret put NWU_CHALLENGE_TICKET_SECRET   # 在 Worker 專案貼上同一把
```

交接票金鑰外洩時，拿到它的人能替自己的 IP 簽出交接票、換到 cookie，等同繞過驗證，請立即更換。

## 升級後要套用設定一次

升級 nginxwebui 與 crowdsec image 後，請到後台按一次**套用設定**。新的 crowdsec image 一啟動，爬取、惡意 User-Agent、掃描這三類可疑 IP 就改發驗證決策，
但 nginx 在套用設定之前仍沿用舊的 bouncer 設定（只處理封鎖決策），所以這段期間這三類可疑 IP 既不會被封鎖、也不會被送去驗證。
驗證頁無法載入而退回的期間（image 缺 ALTCHA、外部模式缺交接票金鑰，後台狀態欄會說明原因）也一樣，這三類 IP 不會被擋。

## 部署前要注意的三件事

1. **純 HTTP 站台不出題**：瀏覽器只在 HTTPS 提供驗證需要的 `crypto.subtle`，所以 `http://` 站台遇到有驗證決策的訪客一律回 429 說明頁（附訪客 IP 與時間，請真人聯絡管理者加白名單）。
   前面有 TLS 終結代理（反向代理把 HTTPS 轉成 HTTP 給 nginxWebUI）的站台，nginx 看到的也是 HTTP，會走這條規則。
2. **站台開 Cloudflare 代理時，確認 `real_ip` 已生效**：否則 nginx 看到的是 Cloudflare 的 IP，cookie 與交接票的 IP 比對會失敗、無法通過。
   http 參數預設已包含 `include /etc/nginx/geoip/realip.conf;`（鎖定的 `realip` 群組，不用也不能重複加，重複會讓 `real_ip_header` 重複而使 `nginx -t` 失敗）。
   要做的是兩件事：確認 Cloudflare IP 清單已下載（「GeoIP」頁的 Cloudflare 下載；cron 每週三、六也會更新，7 天內已更新則跳過），
   以及自訂模板沒有把 `realip` 群組移掉。`realip.conf` 內含 `set_real_ip_from` 與 `real_ip_header CF-Connecting-IP`。外部驗證站（Worker）本身不需要這個設定。
3. **外部驗證站先預覽再實測**：後台「預覽驗證頁」會直接開外部網址；確認頁面正常後，在 crowdsec 容器內執行 `docker compose exec crowdsec cscli decisions add -i <測試 IP> -t captcha -d 10m`，對自己的 IP 實際走一次。

## 交接票格式（給外部驗證站實作）

```
token   = base64url(payload) + "." + base64url(HMAC-SHA1(交接票金鑰, payload))     # base64url 不補 =
payload = "nwu1" \n kind \n exp \n ip \n host \n path \n nonce                     # 以換行 \n 連接
```

| 欄位 | 請求票（nginx → 外部站，`?t=`） | 回應票（外部站 → nginx，`callback?token=`） |
|---|---|---|
| kind | `req` | `resp` |
| exp | 現在 + 300 秒（epoch 秒） | 建議現在 + 60 秒；nginx 只檢查未過期，且到期時間不晚於現在 + 300 秒 |
| ip | 訪客 IP（nginx 看到的） | 照抄請求票（外部站不比對訪客 IP：瀏覽器連兩邊可能分走 IPv4／IPv6） |
| host | 原網域（`$host`） | 照抄請求票 |
| path | 要導回的同網域路徑 | 照抄請求票 |
| nonce | 隨機 32 位 hex | 外部站新產生的隨機值，nginx 記錄後不接受重送 |

外部站驗證請求票（簽章、`kind=req`、未過期）並完成人機驗證後，轉址到 `https://<host>/.well-known/nwu-challenge/callback?token=<回應票>`。
交接票金鑰的文字本身就是 HMAC 金鑰，兩端都直接取這串文字的位元組，不做 hex 解碼；`openssl rand -hex 32` 只是產生一串安全的隨機字串。
兩端共用的測試資料在 `docker/lua/test/ticket-vectors.json`：只放欄位、payload 與測試金鑰，簽好的 token 由各端測試依上面的公式在執行時產生。

## 測試

```bash
sh docker/test/lua-unit.sh <nginxwebui image>          # Lua 純函式、交接票測試資料
sh docker/test/challenge-smoke.sh <nginxwebui image>   # image 內 nginx：出題、驗答、cookie、交接票（stub LAPI）
sh docker/test/crowdsec-challenge-e2e.sh <nginxwebui image> <crowdsec image>   # 整個 stack、無網路，約 5 分鐘
```
