# SSL 憑證申請指南

nginxWebUI 在「防護與憑證 → 證書管理」可申請 Let's Encrypt 免費憑證(ECC / RSA),並每 2 小時自動檢查、超過 60 天自動續簽。本指南說明三種申請方式、推薦做法、以及常見錯誤排查。

---

## 三種「獲取方式」一覽

新增/編輯憑證時的「獲取方式」下拉有三個選項,名稱容易混淆,先看清楚:

| 選單顯示 | 實際機制 | 何時用 | 是否需要手動加 DNS 記錄 |
|---|---|---|---|
| DNS 驗證(推薦) | AcmeDNS — 透過第三方 acme-dns server(`auth.nginxwebui.cn`)放 TXT | 你的 DNS 沒有 API、或不想把 DNS 憑證交給程式 | 要(加一筆 CNAME,且依賴第三方 server) |
| 申請獲得 | DNS API — 直接呼叫你的 DNS 服務商 API 放 TXT | 你的網域在 Cloudflare / 阿里 / DNSPod… 等有 API 的服務商 | 不用(全自動) |
| 手動上傳 | 匯入你已經有的憑證檔 | 已在別處簽好憑證、只想匯入管理 | 不適用 |

注意:「DNS 驗證(推薦)」不是「用你自己的 DNS」,而是 AcmeDNS(靠 `auth.nginxwebui.cn`)。若你網域有 API(如 Cloudflare),請選「申請獲得」,更穩、全自動。

---

## 方法 A:DNS API(推薦,以 Cloudflare 為例)

最穩、全自動、不依賴任何第三方 server。前提是網域託管在有 API 的 DNS 服務商。

步驟 1 — 建立 Cloudflare API Token:

1. 登入 Cloudflare → 右上角頭像 → My Profile(我的個人資料)
2. 左側 API Tokens(API 權杖) → Create Token(建立權杖)
3. 選範本「Edit zone DNS(編輯區域 DNS)」,並確認權限包含 `Zone → DNS → Edit` 與 `Zone → Zone → Read`(acme.sh 需要它來找到 zone)
4. Zone Resources(區域資源):Include → Specific zone → 選你的網域(例 `example.com`)
5. Continue → Create Token → 複製 Token(只會出現一次)

Token 只能改這個 zone 的 DNS,是最小權限、很安全;不是帳號的全域金鑰。

步驟 2 — 在 nginxWebUI 設定:

1. 證書管理 → 添加證書(或對現有憑證點 編輯)
2. 域名:填要簽的網域(多個用逗號分隔,可含 `*.example.com` 萬用憑證)
3. 獲取方式:選「申請獲得」
4. 加密方式:ECC(推薦,檔案小)或 RSA
5. DNS服務商:選「Cloudflare Token」
6. CF_Token:貼上步驟 1 的 Token
7. CF_Account_ID / CF_Zone_ID:可留空(acme.sh 會自動找;若申請時報找不到 zone,再回 Cloudflare 複製 Zone ID 填入)
8. 儲存 → 對該列點 申請

acme.sh 會以 `--dns dns_cf` 直接呼叫 Cloudflare API 加 TXT、驗證、簽發,全程不需你手動加記錄。

其他 DNS 服務商對照(獲取方式同為「申請獲得」):

| DNS服務商 | 需要填的欄位 |
|---|---|
| Cloudflare Token | `CF_Token`(+ 選填 Account ID / Zone ID) |
| Cloudflare Global | `CF_Email` + `CF_Key`(Global API Key) |
| 阿里雲 | `Ali_Key` + `Ali_Secret` |
| DNSPod | `DP_Id` + `DP_Key` |
| 騰訊雲 | `Tencent_SecretId` + `Tencent_SecretKey` |
| 華為雲 | `HUAWEICLOUD_Username` + `HUAWEICLOUD_Password` |
| AWS Route53 | `aws_access_key_id` + `aws_secret_access_key` |
| GoDaddy | `GD_Key` + `GD_Secret` |
| IPv64 | `IPv64_Token` |

---

## 萬用憑證(wildcard)

可以申請,例如 `*.example.com`(涵蓋 `app.example.com`、`api.example.com` 等下一層子網域;同理 `*.sub.example.com` 可涵蓋更深一層)。重點:

- 萬用憑證只能用 DNS 驗證(方法 A 或 B),不能用 HTTP 驗證。
- 域名欄直接填 `*.example.com`。
- `*.example.com` 只涵蓋一層:`xxx.example.com` 可以,但不含本級 `example.com`,也不含更深層 `a.b.example.com`。
- 要同時涵蓋本級網域,域名欄用逗號填兩個:`example.com,*.example.com`。
- 用 DNS API(Cloudflare)時全自動:apex 與 wildcard 會在 `_acme-challenge.example.com` 放兩筆同名 TXT,acme.sh 會自動處理(改用 AcmeDNS / 手動就得自己加兩筆,較麻煩)。
- 前提:該網域的 zone 在你的 DNS 服務商、且 Token / 憑證有權限。

---

## 方法 B:AcmeDNS(免交出 DNS 憑證,但依賴第三方 server)

不想把 DNS 憑證交給程式時可用。代價是依賴 `auth.nginxwebui.cn` 這台第三方 server(它掛了就無法簽發)。

流程:

1. 證書管理 → 添加證書 → 域名、加密方式填好 → 獲取方式選「DNS 驗證(推薦)」 → 儲存
2. 對該列點 獲取DNS記錄,會給你一筆 CNAME(紀錄 `_acme-challenge.example.com`、類型 CNAME、值 `<uuid>.acme.nginxwebui.cn`)
3. 到你的 DNS 服務商加上這筆 CNAME。重要:若用 Cloudflare,這筆 CNAME 的 Proxy 狀態必須是「僅 DNS」(灰雲),不能是橘雲(Proxy) —— 被代理會讓 Let's Encrypt 追不到目標、驗證失敗
4. 等約 60 秒讓 DNS 生效 → 回 nginxWebUI 點 申請

提醒:加好的 CNAME 不要刪,續簽時還要用。

---

## 方法 C:手動上傳

已在別處簽好憑證時,獲取方式選「手動上傳」,把 pem / key 貼上或上傳即可,只做管理、不自動續簽。

---

## 常見錯誤排查

**1. `invalid response of acme-dns` / `curl error code: 6`**
意思是 acme.sh 連不到 acme-dns server(couldn't resolve host)。原因:你用了「DNS 驗證(推薦)」(AcmeDNS),但伺服器連不到 `auth.nginxwebui.cn`(網路/DNS 不通,或對方 server 當掉)。
解法:改用方法 A(DNS API)最一勞永逸;或在容器內測試對外連線 `docker exec nginxwebui sh -c "nslookup auth.nginxwebui.cn; curl -sI http://auth.nginxwebui.cn"`。

**2. 用 DNS API 卻一直失敗,且 `_acme-challenge` 已有一筆 CNAME**
原因:DNS 規定同名稱不能同時有 CNAME 和 TXT。之前用 AcmeDNS 留下的 `_acme-challenge` CNAME,會擋掉 DNS API 要加的 TXT。
解法:到 DNS 服務商刪掉那筆 `_acme-challenge` CNAME,再重新申請。

**3. Cloudflare 驗證記錄沒生效**
確認驗證用的記錄是「僅 DNS」(灰雲),不是橘雲 Proxy。主網域的 A 記錄要不要橘雲與簽發無關,可自行決定。

**4. 選錯獲取方式**
看到指令是 `--dns dns_acmedns` 代表你選到 AcmeDNS(「DNS 驗證(推薦)」)。要走 DNS 服務商 API(如 Cloudflare),請選「申請獲得」,指令才會是 `--dns dns_cf` 等。

**5. `too many certificates` / rate limit**
Let's Encrypt 對每個「註冊網域」有速率限制。若用的是免費二級子網域(如某些 `*.xxx.yy` 共享網域),且該後綴不在 Public Suffix List,會與其他人共用額度而被擋。解法:換自有網域,或稍後再試。注意這與上面的 error 6 是不同錯誤。

---

## 自動續簽

- 系統每 2 小時檢查一次,超過 60 天的憑證會自動續簽(Let's Encrypt 有效期 90 天)。
- DNS API 方式續簽全自動;AcmeDNS 方式只要當初的 CNAME 還在、且 `auth.nginxwebui.cn` 可達即可。
- 憑證檔路徑見該列的「證書路徑」(pem / key),可在反向代理的 SSL 設定引用。
