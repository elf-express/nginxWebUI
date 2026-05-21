# Nginx Web UI v5.0.1 結案報告

## 專案資訊
- **日期：** 2026-03-21
- **原始版本：** 4.3.8（基於 cym1102/nginxWebUI 開源專案）
- **發佈版本：** 5.0.1
- **倉庫：** https://github.com/elf-express/nginxWebUI

---

## 一、改進總覽

### 從 4.3.8 到 5.0.1 的變化
| 指標 | 4.3.8 | 5.0.1 |
|------|-------|-------|
| 預設 http 參數 | 2 個 | 16 個（含 gzip、brotli、GeoIP2、安全 headers） |
| 預設模板 | 0 個 | 15 個（代理、緩存、跨域、限流、安全、GeoIP） |
| 自動化測試 | 無 | 34 個 Playwright E2E 測試 |
| Docker 服務 | 1 個 | 7 個（完整 Stack） |
| Nginx 模組 | 部分載入 | 8 個模組預設載入 |
| Conf 顯示 | 純文字 | CodeMirror 語法高亮（monokai） |
| Conf 縮進 | 無縮進 | 4 格自動縮進 |
| CI/CD | 無 | GitHub Actions（自動構建 + 推送 Docker Hub） |

---

## 二、功能改進明細

### 2.1 UI/UX 優化
| 項目 | 說明 |
|------|------|
| 批量輸入參數 | http / server / location 三處，一次貼入多行 nginx 指令 |
| TLS 版本預設值 | TLSv1/1.1 預設不勾 + 標註「已棄用」 |
| Conf 縮進美化 | 按 nginx 規範每層 4 格縮進 |
| Conf 語法高亮 | CodeMirror + monokai 深色主題 |
| 登入密碼可見 | 眼睛圖標切換顯示/隱藏 |
| 登入背景美化 | 更新 SVG 背景 |

### 2.2 預設配置
| 類別 | 內容 |
|------|------|
| 基本參數 | 載入 geoip2、brotli、headers-more、cache_purge、auth-jwt、stream 模組 |
| Real IP | 自動更新 Cloudflare IPv4/IPv6 段 + CF-Connecting-IP |
| GeoIP2 | Country + City + ASN 三個資料庫（每週自動更新） |
| Gzip | on, level 5, 常用 MIME types |
| Brotli | on, level 6, 常用 MIME types |
| 安全 Headers | X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, Referrer-Policy |
| 日誌格式 | 含真實 IP + 國家代碼 + 城市名 |

### 2.3 預設模板（15 個）
| 類別 | 模板名稱 | 配置到 |
|------|---------|--------|
| 代理 | WebSocket Proxy | location |
| 代理 | Proxy Headers | location |
| 代理 | Large File Upload | server |
| 緩存 | Static File Cache | location |
| 緩存 | Proxy Cache | location |
| 跨域 | CORS Allow All | location |
| 跨域 | CORS Specific Origin | server |
| 限流 | Rate Limit | server |
| 限流 | Connection Limit | server |
| 安全 | Security Headers (HSTS) | server |
| 安全 | Hide Server Info | server |
| 安全 | Block Sensitive Paths | location |
| GeoIP | GeoIP Allow TW Only | server |
| GeoIP | GeoIP Log Country | server |
| CrowdSec | CrowdSec Auth Request | server |

### 2.4 後端改動
| 檔案 | 改動 |
|------|------|
| `InitConfig.java` | 預設參數 + 模板初始化 + 模組載入 |
| `LoginController.java` | 測試用驗證碼支援（--project.testCaptcha） |
| `ToolUtils.java` | formatConf() 縮進方法 |
| `ConfService.java` | 調用 formatConf |
| `MainController.java` | 調用 formatConf |
| `app.yml` | 還原為原始配置 |

### 2.5 前端改動
| 檔案 | 改動 |
|------|------|
| server/index.html + js | 批量輸入（server + location）、TLS 棄用標註 |
| http/index.html + js | 批量輸入 |
| login/index.html + js | 密碼可見切換 |
| conf/index.html + js | CodeMirror 語法高亮 |
| messages*.properties | i18n（簡/繁/英） |
| background.svg | 登入背景 |

---

## 三、Docker Stack 架構

```
┌─────────────────────────────────────────────────────────────┐
│                  Nginx Web UI v5.0.1 Stack                    │
├──────────────┬──────────┬──────────────┬────────────────────┤
│  nginxwebui  │ postgres │  日誌監控     │  安全防護           │
│  :8080       │ PG 18    │              │                    │
│  :80 / :443  │          │  promtail    │  crowdsec          │
│              │          │     ↓        │     ↓              │
│  nginx 1.28  │          │  loki        │  bouncer           │
│  + geoip2    │          │     ↓        │                    │
│  + brotli    │          │  grafana     │                    │
│  + realip    │          │  :3000       │                    │
└──────────────┴──────────┴──────────────┴────────────────────┘
```

### 服務清單
| 服務 | 鏡像 | 端口 | 用途 |
|------|------|------|------|
| nginxwebui | 自建 (Alpine 3.22 + nginx 1.28 + JDK 8) | 8080, 80, 443 | Nginx 管理 UI |
| postgres | postgres:18-alpine | 內部 5432 | 資料庫 |
| loki | grafana/loki:3.5.0 | 內部 3100 | 日誌存儲 |
| promtail | grafana/promtail:3.5.0 | 內部 | 日誌收集 |
| grafana | grafana/grafana:11.6.0 | 3000 | 日誌圖表 |
| crowdsec | crowdsecurity/crowdsec:latest | 內部 | 惡意 IP 偵測 |
| bouncer | fbonalair/traefik-crowdsec-bouncer:latest | 內部 | 惡意 IP 封鎖 |

### 自動化腳本
| 腳本 | 觸發 | 功能 |
|------|------|------|
| update-geoip-cf.sh | 容器啟動 + 每週三/六 03:00 | 更新 GeoLite2 mmdb + Cloudflare Real IP 段 |

---

## 四、自動化測試

### 測試架構
```
tests/e2e/
├── playwright.config.js     # 配置（port 18080, slowMo 1200ms）
├── global-setup.js          # 啟動測試 app
├── global-teardown.js       # 停止測試 app
├── helpers.js               # 登入、啟停輔助
├── 01-login.spec.js         # 登入（3 測試）
├── 02-http-batch.spec.js    # http 批量輸入（4 測試）
├── 03-server-batch.spec.js  # server/location 批量輸入（3 測試）
├── 04-tls-defaults.spec.js  # TLS 預設值（3 測試）
├── 05-conf-indent.spec.js   # Conf 縮進（1 測試）
├── 06-syntax-highlight.spec.js  # 語法高亮（4 測試）
├── 07-default-params.spec.js    # 預設參數+模板（5 測試）
├── 08-crowdsec.spec.js      # 安全防護（3 測試）
└── 09-realip.spec.js        # Real IP + GeoIP（8 測試）
```

### 測試結果
```
34 passed (4.5m)
```

### 執行指令
```bash
npm test          # 執行測試（帶瀏覽器 + 錄影）
npm run test:fast # 快速測試（CI/CD）
npm run report    # 查看 HTML 報告
```

---

## 五、CI/CD

### GitHub Actions
```
push master → Build JAR → Build Docker (amd64 + arm64) → Push Docker Hub
```

### 需要的 Secrets
| Secret | 說明 |
|--------|------|
| DOCKERHUB_USERNAME | Docker Hub 帳號 |
| DOCKERHUB_TOKEN | Docker Hub Access Token |

---

## 六、規範文件

| 文件 | 用途 | 類型 |
|------|------|------|
| [CLAUDE.md](../CLAUDE.md) | AI 專案指南 | 專案專用 |
| [playwright-guide.md](playwright-guide.md) | Playwright 測試規範 | 通用 |
| [docker-standard.md](docker-standard.md) | Docker 容器構建規範 | 通用 |
| [docker-guide.md](docker-guide.md) | Docker Stack 專案指南 | 專案專用 |
| [playwright.md](../playwright.md) | Playwright 快速指令 | 專案專用 |

---

## 七、檔案變更統計

| 指標 | 數值 |
|------|------|
| 修改檔案 | 23 個 |
| 新增檔案 | 28 個 |
| 新增程式碼行數 | ~1,500 行 |
| 測試檔案 | 9 個 |
| 測試案例 | 34 個 |
| 測試通過率 | 100% |
| 文件 | 12 份 |

---

## 八、部署指南

### 本地開發
```bash
mvn clean package -DskipTests
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.1.jar --server.port=8080
```

### Docker 部署
```bash
# 建立 .env 檔案
echo "CROWDSEC_BOUNCER_KEY=你的key" > .env
echo "ABUSEIPDB_API_KEY=你的key" >> .env

# 構建並啟動
docker compose up -d --build

# 確認所有服務健康
docker compose ps
```

### 訪問地址
| 服務 | URL | 預設帳密 |
|------|-----|---------|
| Nginx Web UI | http://localhost:8080 | 首次設定 |
| Grafana | http://localhost:3000 | admin / admin |

---

## 九、注意事項

1. **app.yml 已還原** — project.home、init.admin/pass 恢復原始值
2. **entrypoint.sh 必須 LF** — Windows 修改後需 `sed -i 's/\r$//' entrypoint.sh`
3. **.env 不入版控** — 已加入 .gitignore
4. **測試環境隔離** — port 18080、獨立資料庫、固定驗證碼
5. **CrowdSec Bouncer Key** — 首次需從容器生成或透過 BOUNCER_KEY_nginx 環境變數自動建立
6. **AbuseIPDB** — 需在 CrowdSec Console 整合（非 collection）
7. **GeoIP + Cloudflare IP** — 容器啟動自動更新，每週三/六 cron 自動維護
