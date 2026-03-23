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

### 前置條件

部署機器需要安裝：
- **Docker Engine** 24+（`docker --version` 確認）
- **Docker Compose V2**（`docker compose version` 確認）
- **Git**（方式 A 需要）

如果沒有 Docker，先安裝：
```bash
curl -fsSL https://get.docker.com | sh
```

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

---

### 步驟 0：申請 GitHub Personal Access Token（PAT）

> **為什麼需要？** 本專案的程式碼倉庫和 Docker Image（ghcr.io）都是**私有**的，拉取程式碼和 Image 都需要 Token 認證。

#### 申請步驟：

1. 登入 [GitHub.com](https://github.com)
2. 點右上角**頭像** → **Settings**
3. 左側選單滑到最底 → **Developer settings**
4. 點 **Personal access tokens** → **Tokens (classic)**
5. 點 **Generate new token** → **Generate new token (classic)**
6. 填寫：
   - **Note**：隨便填，例如 `deploy-nginxwebui`
   - **Expiration**：建議選 90 days 或 No expiration
   - **勾選權限**：
     - [x] `repo`（整個區塊打勾，用於 git clone 私有倉庫）
     - [x] `read:packages`（用於 docker pull 私有 Image）
     - [x] `write:packages`（如果需要從開發機 docker push）
7. 點 **Generate token**
8. **立刻複製 Token**（格式：`ghp_xxxxxxxxxxxxxxxx`），離開頁面就看不到了

> **重要：** 把 Token 存到安全的地方（密碼管理器、記事本），遺失只能重新申請。

---

### 步驟 1：在部署機登入 GitHub Container Registry

> **為什麼？** Docker Image 存在 ghcr.io 私有倉庫，不登入會報 `denied` 錯誤。

```bash
# 把 ghp_xxxx 換成你的 Token，elf-express 換成你的 GitHub 帳號
echo "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx" | docker login ghcr.io -u elf-express --password-stdin
```

看到 `Login Succeeded` 就成功了。登入資訊會存在機器上，以後不用重複登入。

**常見錯誤：**
| 錯誤訊息 | 原因 | 解法 |
|----------|------|------|
| `denied` | Token 沒有 `read:packages` 權限 | 重新申請 Token，勾選 `read:packages` |
| `unauthorized` | Token 過期或打錯 | 確認 Token 正確且未過期 |
| `403 Forbidden` | 用了密碼而不是 Token | GitHub 已停用密碼認證，必須用 PAT |

---

### 步驟 2：取得部署檔案

**方式 A：從 GitHub 拉取（推薦）**

```bash
# 1. 建立安裝目錄
mkdir -p /opt/nginxwebui
cd /opt

# 2. 用 sparse-checkout 只拉 deploy/ 目錄（不會下載原始碼）
#    把 ghp_xxxx 換成你的 Token，elf-express 換成你的 GitHub 帳號
git clone --depth 1 --filter=blob:none --sparse \
  https://elf-express:ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx@github.com/elf-express/nginxWebUI.git \
  nginxwebui-tmp

# 3. 進入目錄，設定只拉 deploy/
cd nginxwebui-tmp
git sparse-checkout set deploy

# 4. 把 deploy/ 內容搬到安裝目錄
cp -r deploy/* /opt/nginxwebui/
cp -r deploy/crowdsec /opt/nginxwebui/

# 5. 清理暫存（不需要原始碼）
cd /opt
rm -rf nginxwebui-tmp
```

**方式 B：從開發機 SCP 傳送（不需要 GitHub）**

在**開發機**（Windows / Mac）上執行：
```bash
# 把 deploy/ 目錄整個傳到部署機
scp -r deploy/* root@192.168.25.220:/opt/nginxwebui/
scp -r deploy/crowdsec root@192.168.25.220:/opt/nginxwebui/
```

---

### 步驟 3：設定環境變數

```bash
cd /opt/nginxwebui

# 複製範本
cp .env.example .env

# 編輯（用 nano 或 vi）
nano .env
```

`.env` 內容說明：
```bash
# CrowdSec Bouncer 金鑰
# → 首次部署先填任意值，啟動後再用以下指令取得真正的金鑰：
#   docker exec nginx-webui-5.0.2-crowdsec cscli bouncers add nginx
# → 把輸出的金鑰貼回來，然後 docker compose restart bouncer
CROWDSEC_BOUNCER_KEY=your-bouncer-key-here

# AbuseIPDB API 金鑰
# → 到 https://www.abuseipdb.com 註冊帳號
# → 登入後到 Account → API Keys → Create Key
ABUSEIPDB_API_KEY=your-api-key-here
```

**安全提醒：** `.env` 包含敏感金鑰，不要上傳到 Git。

---

### 步驟 4：啟動 Docker Stack

```bash
cd /opt/nginxwebui

# 啟動所有服務（背景執行）
docker compose up -d

# 等待約 30 秒讓所有服務啟動，然後確認狀態
docker compose ps
```

正常結果：所有服務顯示 `Up` 或 `healthy`。

**如果某個服務一直 restarting：**
```bash
# 看錯誤日誌
docker logs nginx-webui-5.0.2-容器名

# 常見問題：
# postgres restarting → 資料庫初始化失敗，刪 volume 重來：
#   docker compose down && docker volume rm nginxwebui_postgres_data && docker compose up -d
# bouncer restarting → CROWDSEC_BOUNCER_KEY 不正確，見步驟 5
```

---

### 步驟 5：首次部署後設定

#### 5-1. 登入 Nginx Web UI

1. 瀏覽器打開 `http://部署機IP:8080`
2. 第一次進入會要求設定**管理員帳號密碼**
3. 設定完成後登入

#### 5-2. 啟用 Nginx 配置

1. 左側選單 → **啟用配置**
2. 點 **校驗** → 確認沒有錯誤
3. 點 **替換** → **重新裝載**

#### 5-3. 設定 CrowdSec Bouncer 金鑰

```bash
# 1. 取得金鑰（會印出一串亂碼）
docker exec nginx-webui-5.0.2-crowdsec cscli bouncers add nginx

# 2. 複製輸出的金鑰，貼到 .env
nano /opt/nginxwebui/.env
# 把 CROWDSEC_BOUNCER_KEY=your-bouncer-key-here 改成：
# CROWDSEC_BOUNCER_KEY=貼上剛才的金鑰

# 3. 重啟 bouncer 讓新金鑰生效
docker compose restart bouncer

# 4. 確認 bouncer 正常連線
docker exec nginx-webui-5.0.2-crowdsec cscli bouncers list
# 應該看到 nginx 狀態為 validated
```

#### 5-4. 登入 Grafana

1. 瀏覽器打開 `http://部署機IP:3000`
2. 帳號密碼：`admin` / `admin`
3. 第一次登入會要求改密碼
4. 左側選單 → **Dashboards** → **Nginx Monitor** 即可看到日誌儀表板

#### 5-5. 確認所有服務

```bash
# 確認 CrowdSec 偵測規則已安裝
docker exec nginx-webui-5.0.2-crowdsec cscli collections list

# 確認 Promtail 正在收集日誌
docker logs nginx-webui-5.0.2-promtail --tail 5

# 確認所有服務健康
docker compose ps
```

---

### 升版流程

當有新版本發佈時：

```bash
cd /opt/nginxwebui

# 1. 取得新的部署檔案（方式 A 或 B 二選一）

## 方式 A：從 GitHub 拉取
cd /tmp
git clone --depth 1 --filter=blob:none --sparse \
  https://elf-express:ghp_xxxx你的Token@github.com/elf-express/nginxWebUI.git nginxwebui-update
cd nginxwebui-update && git sparse-checkout set deploy
cp -r deploy/* /opt/nginxwebui/ && cp -r deploy/crowdsec /opt/nginxwebui/
rm -rf /tmp/nginxwebui-update

## 方式 B：從開發機 SCP（在開發機執行）
# scp -r deploy/* root@192.168.25.220:/opt/nginxwebui/
# scp -r deploy/crowdsec root@192.168.25.220:/opt/nginxwebui/

# 2. 拉取新 Image 並重啟（.env 不會被覆蓋）
cd /opt/nginxwebui
docker compose pull
docker compose up -d

# 3. 確認
docker compose ps
```

> **注意：** `.env` 不會被覆蓋，你的金鑰設定會保留。Volume 資料（資料庫、日誌）也會保留。

## 三、命名規範（強制）

### container_name
```
{專案名}-{版本號}             ← 主服務
{專案名}-{版本號}-{服務名}    ← 其他服務
```
例：
```
nginx-webui-5.0.2
nginx-webui-5.0.2-postgres
nginx-webui-5.0.2-loki
nginx-webui-5.0.2-promtail
nginx-webui-5.0.2-grafana
nginx-webui-5.0.2-crowdsec
nginx-webui-5.0.2-bouncer
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
│    nginxwebui:5.0.2                 │
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
docker build -t ghcr.io/elf-express/nginxwebui:5.0.2 .

# 3. 登入 GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u 你的帳號 --password-stdin

# 4. 推送
docker push ghcr.io/elf-express/nginxwebui:5.0.2
docker tag ghcr.io/elf-express/nginxwebui:5.0.2 ghcr.io/elf-express/nginxwebui:latest
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
docker exec nginx-webui-5.0.2-crowdsec cscli alerts list

# 查看被封鎖的 IP
docker exec nginx-webui-5.0.2-crowdsec cscli decisions list

# 手動封鎖 IP
docker exec nginx-webui-5.0.2-crowdsec cscli decisions add --ip 1.2.3.4

# 解除封鎖
docker exec nginx-webui-5.0.2-crowdsec cscli decisions delete --ip 1.2.3.4

# 查看偵測統計
docker exec nginx-webui-5.0.2-crowdsec cscli metrics
```

### Grafana 查看日誌
1. 開啟 `http://目標IP:3000`
2. Explore → Data source: Loki
3. 查詢：`{job="nginx"}`

### 一般操作
```bash
# 查看日誌
docker logs nginx-webui-5.0.2
docker logs nginx-webui-5.0.2-crowdsec

# 重啟單一服務
docker compose restart nginxwebui

# 查看即時日誌
docker logs -f nginx-webui-5.0.2 --tail 50

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
