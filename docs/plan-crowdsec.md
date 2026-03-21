# CrowdSec + AbuseIPDB 安全防護 — 計畫書

## 目標
自動偵測 nginx 日誌中的惡意行為（暴力破解、掃描、爬蟲），自動封鎖惡意 IP，並整合 AbuseIPDB 社區情報。

## 架構

```
                    ┌─────────────────┐
                    │   AbuseIPDB     │
                    │  （社區情報）     │
                    └────────┬────────┘
                             │ API
┌──────────┐    日誌    ┌────▼────┐    封鎖指令    ┌──────────┐
│  nginx   │ ────────→ │CrowdSec │ ────────────→ │ Bouncer  │
│ (日誌產生)│           │ (分析)   │               │(nginx 層) │
└──────────┘           └─────────┘               └──────────┘
                             │
                    ┌────────▼────────┐
                    │  CrowdSec Hub   │
                    │ （共享黑名單）    │
                    └─────────────────┘
```

### 運作流程
1. nginx 產生 access.log / error.log
2. CrowdSec 即時讀取日誌，用 scenarios 規則分析
3. 偵測到惡意行為 → 建立封鎖決策（ban IP）
4. 同時查詢 AbuseIPDB，高風險 IP 直接封鎖
5. Bouncer 從 CrowdSec 取得封鎖名單
6. nginx 請求進來時，Bouncer 檢查 IP → 惡意 IP 直接返回 403

### 可偵測的攻擊類型
- HTTP 暴力破解（短時間大量 401/403）
- 路徑掃描（大量 404）
- 敏感路徑存取（wp-admin、.env、.git 等）
- SQL 注入嘗試
- 惡意爬蟲
- DDoS（高頻請求）

## 新增服務

| 服務 | 鏡像 | 用途 |
|------|------|------|
| crowdsec | crowdsecurity/crowdsec:v1.6 | 日誌分析引擎 |
| bouncer | fbonalair/traefik-crowdsec-bouncer:0.6 | 封鎖執行（HTTP API 模式） |

> 注：nginx 原生 bouncer 需要編譯 nginx 模組，太複雜。
> 改用 HTTP API 模式：nginx 透過 `auth_request` 指令查詢 bouncer，無需重編 nginx。

## 風險評估
- **後端代碼** → 零改動
- **業務邏輯** → 零影響
- **nginx 配置** → 需加一行 `auth_request`（通過 nginxWebUI 的額外參數功能加入）
- **誤封風險** → CrowdSec 預設規則經過大量社區驗證，誤封率極低
- **可隨時關閉** → 停止 bouncer 容器即可，不影響 nginx 運行

## 需要的配置檔案

```
專案根目錄/
├── crowdsec/
│   ├── acquis.yml          # 日誌來源配置
│   └── profiles.yml        # 封鎖策略（可選，用預設即可）
├── docker-compose.yml      # 加入 crowdsec + bouncer
└── docs/plan-crowdsec.md   # 本計畫
```

## AbuseIPDB 整合
1. 到 https://www.abuseipdb.com/account/api 註冊免費帳號
2. 取得 API Key
3. 在 CrowdSec 容器設定環境變數 `ABUSEIPDB_API_KEY`
4. CrowdSec 會自動：
   - 查詢可疑 IP 的信譽評分
   - 回報偵測到的惡意 IP 給社區

## 自動化測試
新增 `tests/e2e/08-crowdsec.spec.js`：
1. 驗證 CrowdSec 容器健康
2. 驗證 Bouncer 容器健康
3. 模擬惡意請求（連續 404）→ 確認被封鎖
4. 驗證正常請求不受影響

## 時間估計
約 1 小時（配置 + 測試）
