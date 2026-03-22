# Docker 容器構建與部署方案

> 本方案為**強制性**標準，所有容器構建必須遵守。

## 一、Stack 架構

```
┌──────────────────────────────────────────────────────────────┐
│                   nginxWebUI Docker Stack                     │
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

部署到任何環境（LXC / VM / 實體機）只需要以下 **7 個檔案**：

```
/opt/nginxwebui/
├── docker-compose.yml          # Stack 編排
├── .env                        # 敏感設定（API Key、Bouncer Key）
├── promtail-config.yml         # Promtail 日誌收集配置
├── grafana-datasources.yml     # Grafana 數據源自動配置
└── crowdsec/
    ├── acquis.yml              # CrowdSec 日誌來源設定
    ├── abuseipdb.yaml          # AbuseIPDB 回報設定
    └── profiles.yaml           # CrowdSec 告警處理設定
```

**不需要**原始碼、Dockerfile、JAR 檔 — Image 從 ghcr.io 拉取。

### 部署步驟

```bash
# 1. 在目標機器建立目錄
mkdir -p /opt/nginxwebui/crowdsec

# 2. 從開發機傳送檔案（Windows PowerShell）
scp docker-compose.yml .env promtail-config.yml grafana-datasources.yml root@目標IP:/opt/nginxwebui/
scp crowdsec/acquis.yml crowdsec/abuseipdb.yaml crowdsec/profiles.yaml root@目標IP:/opt/nginxwebui/crowdsec/

# 3. 在目標機器啟動
cd /opt/nginxwebui
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

1. **登入 nginxWebUI** — `http://目標IP:8080`
2. **設定管理員帳號密碼**
3. **啟用配置** — 校驗 → 替換 → 重新裝載
4. **登入 Grafana** — `http://目標IP:3000`（admin/admin），修改密碼
5. **確認 CrowdSec**：
   ```bash
   docker exec nginxwebui-5.0.0-crowdsec cscli bouncers list
   docker exec nginxwebui-5.0.0-crowdsec cscli collections list
   ```

## 三、命名規範（強制）

### container_name
```
{專案名}-{版本號}             ← 主服務
{專案名}-{版本號}-{服務名}    ← 其他服務
```
例：
```
nginxwebui-5.0.0
nginxwebui-5.0.0-postgres
nginxwebui-5.0.0-loki
nginxwebui-5.0.0-promtail
nginxwebui-5.0.0-grafana
nginxwebui-5.0.0-crowdsec
nginxwebui-5.0.0-bouncer
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

**重點：** `nginxwebui_log` 是日誌共享 volume，nginxWebUI 寫入，Promtail 和 CrowdSec 以 `:ro` 唯讀掛載。

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

## 七、日常操作

### CrowdSec 管理
```bash
# 查看警報
docker exec nginxwebui-5.0.0-crowdsec cscli alerts list

# 查看被封鎖的 IP
docker exec nginxwebui-5.0.0-crowdsec cscli decisions list

# 手動封鎖 IP
docker exec nginxwebui-5.0.0-crowdsec cscli decisions add --ip 1.2.3.4

# 解除封鎖
docker exec nginxwebui-5.0.0-crowdsec cscli decisions delete --ip 1.2.3.4

# 查看偵測統計
docker exec nginxwebui-5.0.0-crowdsec cscli metrics
```

### Grafana 查看日誌
1. 開啟 `http://目標IP:3000`
2. Explore → Data source: Loki
3. 查詢：`{job="nginx"}`

### 一般操作
```bash
# 查看日誌
docker logs nginxwebui-5.0.0
docker logs nginxwebui-5.0.0-crowdsec

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

## 八、entrypoint.sh 規範

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

## 九、Dockerfile 規範

### 必要元素
- `HEALTHCHECK` — 必須配置
- `VOLUME` — 數據目錄必須掛載
- `ENTRYPOINT` — 使用 `tini` 作為 init 進程
- `ENV` — 時區、語言、JVM 參數

### 不可包含
- 測試檔案（tests/）
- 開發工具（node_modules/）
- IDE 配置（.idea/, .vscode/）

## 十、.dockerignore

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
