# Docker 容器構建與部署方案

> 本方案為**強制性**標準，所有容器構建必須遵守。

## 一、Stack 架構

```
┌──────────────────────────────────────────────────────────────┐
│                   Nginx Web UI Docker Stack                    │
├──────────────┬──────────┬─────────────┬─────────────────────┤
│  nginxwebui  │ postgres │  日誌監控    │  安全防護            │
│  :8080       │ PG 18    │             │                     │
│  :80 / :443  │ (內部)   │ promtail    │ crowdsec            │
│              │          │   ↓         │   ↓                 │
│              │          │ loki        │ crowdsec-bouncer    │
│              │          │   ↓         │   → AbuseIPDB 回報  │
│              │          │ grafana     │                     │
│              │          │ :3000       │                     │
└──────────────┴──────────┴─────────────┴─────────────────────┘
```

### 安全防護鏈路（請求處理順序）
```
外部請求 → Nginx
  → 第一層：GeoIP 國家封鎖（map + if，O(1) hash，最快）
  → 第二層：IP 黑白名單（deny/allow）
  → 第三層：CrowdSec Bouncer（auth_request，行為分析）
  → 通過 → 代理到後端服務
```

### 日誌鏈路
```
Nginx access_log → nginxwebui_log volume
  → Promtail 收集 → Loki 儲存 → Grafana 顯示
  → CrowdSec 讀取 → 分析攻擊模式 → 自動封鎖 + 回報 AbuseIPDB
```

| 服務 | 鏡像 | 端口 | 用途 |
|------|------|------|------|
| nginxwebui | ghcr.io/elf-express/nginxwebui | 8080, 80, 443 | Nginx 管理 UI |
| postgres | postgres:18-alpine | 內部 5432 | 資料庫 |
| loki | grafana/loki:3.5.0 | 內部 3100 | 日誌存儲 |
| promtail | grafana/promtail:3.5.0 | 內部 | 日誌收集 |
| grafana | grafana/grafana:11.6.0 | 3000 | 日誌圖表 |
| crowdsec | crowdsecurity/crowdsec | 內部 8080 | 威脅偵測 |
| crowdsec-bouncer | fbonalair/traefik-crowdsec-bouncer | 內部 8181 | IP 攔截 |

## 二、部署指南

### 部署檔案清單

所有部署檔案已打包在 `deploy/` 目錄，部署到任何環境（LXC / VM / 實體機）只需要以下 **9 個檔案**：

```
deploy/
├── docker-compose.yml              # Stack 編排
├── .env.example                    # 敏感設定範本（複製為 .env）
├── promtail-config.yml             # Promtail 日誌收集配置
├── grafana-datasources.yml         # Grafana 數據源自動配置
├── grafana-dashboards.yml          # Grafana Dashboard 自動載入
├── grafana-nginx-dashboard.json    # Nginx 監控儀表板
└── crowdsec/
    ├── acquis.yml                  # CrowdSec 日誌來源設定
    ├── abuseipdb.yaml              # AbuseIPDB 回報設定
    └── profiles.yaml               # CrowdSec 告警處理設定
```

**不需要**原始碼、Dockerfile、JAR 檔 — Image 從 ghcr.io 拉取。

### 方式 A：從 GitHub 拉取（推薦）

在目標機器上直接從 GitHub 拉取 `deploy/` 目錄，不會下載原始碼：

```bash
# 1. 用 sparse-checkout 只拉 deploy/ 目錄
cd /opt
git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/elf-express/nginxWebUI.git nginxwebui
cd nginxwebui
git sparse-checkout set deploy

# 2. 把 deploy/ 內容搬到當前目錄，清除 git
cp -r deploy/* . && cp -r deploy/crowdsec . && rm -rf deploy .git

# 3. 設定敏感資訊
cp .env.example .env
nano .env   # 填入 CROWDSEC_BOUNCER_KEY 和 ABUSEIPDB_API_KEY

# 4. 啟動
docker compose up -d

# 5. 確認所有服務健康
docker compose ps
```

升版時重新拉取即可（volume 資料會保留）：

```bash
cd /tmp
git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/elf-express/nginxWebUI.git nginxwebui-update
cd nginxwebui-update && git sparse-checkout set deploy
cp -r deploy/* /opt/nginxwebui/ && cp -r deploy/crowdsec /opt/nginxwebui/
rm -rf /tmp/nginxwebui-update
cd /opt/nginxwebui && docker compose up -d
```

### 方式 B：從開發機 SCP 傳送

```bash
# 1. 從開發機一條指令傳送整個 deploy 目錄
scp -r deploy/* root@目標IP:/opt/nginxwebui/
# 2. 在目標機器設定敏感資訊
cd /opt/nginxwebui
cp .env.example .env
nano .env   # 填入 CROWDSEC_BOUNCER_KEY 和 ABUSEIPDB_API_KEY

# 3. 啟動
docker compose up -d

# 4. 確認所有服務健康
docker compose ps
```

### .env 檔案內容

```bash
# CrowdSec Bouncer 金鑰（首次部署後從 CrowdSec 取得）
CROWDSEC_BOUNCER_KEY=your-bouncer-key-here

# AbuseIPDB API 金鑰（從 abuseipdb.com 取得）
ABUSEIPDB_API_KEY=your-api-key-here
```

**安全提醒：** `.env` 包含敏感金鑰，不要上傳到 Git。

### 首次部署後設定

1. **登入 Nginx Web UI** — `http://目標IP:8080`
2. **設定管理員帳號密碼**
3. **啟用配置** — 校驗 → 替換 → 重新裝載
4. **登入 Grafana** — `http://目標IP:3000`（admin/admin），修改密碼
5. **確認 CrowdSec**：
   ```bash
   docker exec nginx-webui-5.0.1-crowdsec cscli bouncers list
   docker exec nginx-webui-5.0.1-crowdsec cscli collections list
   ```

## 三、命名規範（強制）

### container_name
```
{專案名}-{版本號}             ← 主服務
{專案名}-{版本號}-{服務名}    ← 其他服務
```
例：
```
nginx-webui-5.0.1
nginx-webui-5.0.1-postgres
nginx-webui-5.0.1-loki
nginx-webui-5.0.1-promtail
nginx-webui-5.0.1-grafana
nginx-webui-5.0.1-crowdsec
nginx-webui-5.0.1-bouncer
```

### volume name
```
{專案名}_{用途}_data
```
例：
```
nginxwebui_data
nginxwebui_log
nginxwebui_postgres_data
nginxwebui_loki_data
nginxwebui_grafana_data
nginxwebui_crowdsec_data
nginxwebui_crowdsec_config
```

### 升版時
只改 container_name 的版本號，volume 名稱**不變**（資料保留）。

## 四、啟動順序（強制）

```
1. postgres    ──健康檢查通過──→  2. nginxwebui
3. loki        ──健康檢查通過──→  4. grafana
                                  5. promtail
6. crowdsec    ──健康檢查通過──→  7. crowdsec-bouncer
```

### 健康檢查配置
| 服務 | 檢查方式 | 間隔 | 啟動等待 |
|------|---------|------|---------|
| postgres | `pg_isready -U nginxwebui` | 10s | 10s |
| nginxwebui | `curl -sf http://localhost:8080` | 30s | 30s |
| loki | `wget -qO- http://localhost:3100/ready` | 15s | 20s |
| crowdsec | `cscli version` | 15s | 30s |

### depends_on 規則
- **必須**使用 `condition: service_healthy`
- **禁止**只寫 `depends_on: - xxx`（不等健康檢查）

## 五、Volume 架構

```
nginxwebui_data     → /home/nginxWebUI         # 應用資料（配置、資料庫備份）
nginxwebui_log      → /home/nginxWebUI/log      # Nginx 日誌（共享給 Promtail/CrowdSec）
                    → /var/log/nginx（Promtail/CrowdSec 讀取端）
nginxwebui_postgres_data → /var/lib/postgresql/data
nginxwebui_loki_data     → /loki
nginxwebui_grafana_data  → /var/lib/grafana
nginxwebui_crowdsec_data → /var/lib/crowdsec/data
nginxwebui_crowdsec_config → /etc/crowdsec
```

**重點：** `nginxwebui_log` 是日誌共享 volume，Nginx Web UI 寫入，Promtail 和 CrowdSec 以 `:ro` 唯讀掛載。

## 六、環境變數

### nginxwebui
| 變數 | 默認值 | 說明 |
|------|--------|------|
| `JVM_XMX` | `256m` | JVM 最大堆記憶體 |
| `BOOT_OPTIONS` | （見 compose） | 啟動參數（資料庫連線等） |

### postgres
| 變數 | 值 | 說明 |
|------|------|------|
| `POSTGRES_DB` | `nginxwebui` | 資料庫名 |
| `POSTGRES_USER` | `nginxwebui` | 用戶名 |
| `POSTGRES_PASSWORD` | `nginxwebui123` | 密碼（正式環境請改強密碼） |

### grafana
| 變數 | 值 | 說明 |
|------|------|------|
| `GF_SECURITY_ADMIN_USER` | `admin` | 管理員帳號 |
| `GF_SECURITY_ADMIN_PASSWORD` | `admin` | 管理員密碼（正式環境請修改） |

### crowdsec
| 變數 | 說明 |
|------|------|
| `COLLECTIONS` | 安裝的偵測規則集（nginx, http-cve, base-http-scenarios） |
| `BOUNCER_KEY_nginx` | Bouncer 認證金鑰（從 .env 讀取） |
| `ABUSEIPDB_API_KEY` | AbuseIPDB 回報金鑰（從 .env 讀取） |

## 七、開發者構建流程

### 完整流程圖

```
原始碼修改
  ↓
mvn clean package -DskipTests          # 編譯 JAR
  ↓
┌─ 本機測試 ─────────────────────────┐
│ docker compose -f docker-compose.yml │
│   -f docker-compose.dev.yml        │
│   up -d --build                    │  ← 用 Dockerfile 本機 build image
│ npm test                           │  ← 跑 Playwright 測試
└────────────────────────────────────┘
  ↓
git push origin master                 # 推到 GitHub
  ↓
┌─ CI/CD（GitHub Actions 自動執行）──┐
│ 1. mvn clean package               │
│ 2. docker buildx（amd64 + arm64）  │
│ 3. push → ghcr.io/elf-express/     │
│    nginxwebui:5.0.1                 │
│    nginxwebui:latest                │
└────────────────────────────────────┘
  ↓
部署機 docker compose up -d           # 自動拉新 image
```

### 本機開發（Windows）

```bash
# 1. 編譯
mvn clean package -DskipTests

# 2. 本機 Docker 啟動（使用 dev 覆蓋檔從原始碼 build image）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build

# 3. 確認所有服務健康
docker compose ps

# 4. 跑自動化測試
npm test

# 5. 看測試報告
npm run report
```

### 手動構建 Docker Image

```bash
# 1. 編譯 JAR（必須先做，Dockerfile 會 COPY target/*.jar）
mvn clean package -DskipTests

# 2. 構建 image
docker build -t ghcr.io/elf-express/nginxwebui:5.0.1 .

# 3. 登入 GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u 你的帳號 --password-stdin

# 4. 推送
docker push ghcr.io/elf-express/nginxwebui:5.0.1
docker tag ghcr.io/elf-express/nginxwebui:5.0.1 ghcr.io/elf-express/nginxwebui:latest
docker push ghcr.io/elf-express/nginxwebui:latest
```

### CI/CD 自動構建（GitHub Actions）

推送到 `master` 分支會自動觸發 `.github/workflows/build.yml`：

1. **Build Job** — 編譯 JAR，上傳為 artifact
2. **Docker Job** — Build multi-arch image（amd64 + arm64），push 到 ghcr.io

版本號從 `pom.xml` 的 `<version>` 自動提取。

### 關鍵檔案說明

| 檔案 | 用途 |
|------|------|
| `Dockerfile` | 基於 Alpine 3.22，安裝 Nginx + JRE8 + 所有模組 |
| `entrypoint.sh` | 容器啟動入口，用 tini + exec java |
| `scripts/update-geoip-cf.sh` | 定期更新 GeoIP 資料庫 + Cloudflare IP |
| `.github/workflows/build.yml` | CI/CD 自動構建 + 推送 image |
| `pom.xml` | Maven 配置，版本號在這裡改 |

### 升版 Checklist

1. 修改 `pom.xml` 的 `<version>`（如 `5.0.1` → `5.1.0`）
2. 修改 `docker-compose.yml` 的 `image` 和所有 `container_name` 版本號
3. 修改 `tests/e2e/helpers.js` 的 `JAR_PATH`（如有改 JAR 檔名）
4. 編譯測試：`mvn clean package -DskipTests && npm test`
5. 提交推送：`git push origin master`
6. CI 自動構建並推送 image
7. 部署機：`docker compose pull && docker compose up -d`

## 八、日常操作（部署環境）

### CrowdSec 管理
```bash
# 查看警報
docker exec nginx-webui-5.0.1-crowdsec cscli alerts list

# 查看被封鎖的 IP
docker exec nginx-webui-5.0.1-crowdsec cscli decisions list

# 手動封鎖 IP
docker exec nginx-webui-5.0.1-crowdsec cscli decisions add --ip 1.2.3.4

# 解除封鎖
docker exec nginx-webui-5.0.1-crowdsec cscli decisions delete --ip 1.2.3.4

# 查看偵測統計
docker exec nginx-webui-5.0.1-crowdsec cscli metrics
```

### Grafana 查看日誌
1. 開啟 `http://目標IP:3000`
2. Explore → Data source: Loki
3. 查詢：`{job="nginx"}`

### 一般操作
```bash
# 查看日誌
docker logs nginx-webui-5.0.1
docker logs nginx-webui-5.0.1-crowdsec

# 重啟單一服務
docker compose restart nginxwebui

# 停止全部
docker compose down

# 停止並刪除資料（危險！）
docker compose down -v
```

### 升版流程
```bash
# 1. 修改 docker-compose.yml 的 image 版本號和 container_name
# 2. 重新啟動
docker compose up -d
# 3. 確認健康
docker compose ps
```

## 九、entrypoint.sh 規範

```bash
#!/bin/sh
cd /home
exec java -Xmx${JVM_XMX:-256m} -jar -Dfile.encoding=UTF-8 nginxWebUI.jar ${BOOT_OPTIONS}
```

**強制要求：**
- 換行符必須為 **LF**（不可用 CRLF）
- Windows 環境修改後執行：`sed -i 's/\r$//' entrypoint.sh`
- 使用 `exec` 確保 Java 進程接收信號
- 使用 `${JVM_XMX:-256m}` 支持環境變數覆蓋

## 十、Dockerfile 規範

### 必要元素
- `HEALTHCHECK` — 必須配置
- `VOLUME` — 數據目錄必須掛載
- `ENTRYPOINT` — 使用 `tini` 作為 init 進程
- `ENV` — 時區、語言、JVM 參數

### 不可包含
- 測試檔案（tests/）
- 開發工具（node_modules/）
- IDE 配置（.idea/, .vscode/）

## 十一、.dockerignore

```
.git
.idea
.vscode
node_modules
tests/
docs/
*.md
.claude/
.playwright-mcp/
captcha_code.txt
```
