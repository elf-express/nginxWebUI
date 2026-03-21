# Docker 容器構建方案

> 本方案為**強制性**標準，所有容器構建必須遵守。

## 一、Stack 架構

```
┌──────────────────────────────────────────────────┐
│              nginxWebUI Docker Stack              │
├──────────────┬──────────┬────────────────────────┤
│  nginxwebui  │ postgres │     日誌監控            │
│  :8080       │ PG 18    │                        │
│  :80 / :443  │ (內部)   │  promtail → loki       │
│              │          │              ↓          │
│              │          │           grafana       │
│              │          │           :3000         │
└──────────────┴──────────┴────────────────────────┘
```

| 服務 | 鏡像 | 端口 | 用途 |
|------|------|------|------|
| nginxwebui | 自建 | 8080, 80, 443 | Nginx 管理 UI |
| postgres | postgres:18-alpine | 內部 5432 | 資料庫 |
| loki | grafana/loki:3.5.0 | 內部 3100 | 日誌存儲 |
| promtail | grafana/promtail:3.5.0 | 內部 | 日誌收集 |
| grafana | grafana/grafana:11.6.0 | 3000 | 日誌圖表 |

## 二、命名規範（強制）

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
```

### volume name
```
{專案名}_{用途}_data
```
例：
```
nginxwebui_data
nginxwebui_postgres_data
nginxwebui_loki_data
nginxwebui_grafana_data
```

### 升版時
只改 container_name 的版本號，volume 名稱**不變**（資料保留）。

## 三、啟動順序（強制）

```
1. postgres    ──健康檢查通過──→  2. nginxwebui

3. loki        ──健康檢查通過──→  4. grafana
                                  5. promtail
```

### 健康檢查配置
| 服務 | 檢查方式 | 間隔 | 啟動等待 |
|------|---------|------|---------|
| postgres | `pg_isready -U nginxwebui` | 10s | 10s |
| nginxwebui | `curl -sf http://localhost:8080` | 30s | 30s |
| loki | `wget -qO- http://localhost:3100/ready` | 15s | 20s |

### depends_on 規則
- **必須**使用 `condition: service_healthy`
- **禁止**只寫 `depends_on: - xxx`（不等健康檢查）

## 四、檔案清單（強制）

```
專案根目錄/
├── Dockerfile              # 主服務鏡像
├── docker-compose.yml      # Stack 編排
├── entrypoint.sh           # 容器入口（必須 LF 換行）
├── promtail-config.yml     # Promtail 日誌收集配置
├── grafana-datasources.yml # Grafana 數據源自動配置
└── .dockerignore           # 構建排除
```

## 五、指令

### 構建與啟動
```bash
# 1. 編譯 jar（必須先做）
mvn clean package -DskipTests

# 2. 構建鏡像 + 啟動全部服務
docker compose up -d --build

# 3. 確認所有服務狀態
docker compose ps
```

### 日常操作
```bash
# 查看日誌
docker logs nginxwebui-5.0.0
docker logs nginxwebui-5.0.0-postgres

# 重啟單一服務
docker compose restart nginxwebui

# 停止全部
docker compose down

# 停止並刪除資料（危險！）
docker compose down -v
```

### 升版流程
```bash
# 1. 拉新代碼 + 編譯
git pull && mvn clean package -DskipTests

# 2. 修改 docker-compose.yml 的 container_name 版本號

# 3. 重新構建並啟動
docker compose up -d --build

# 4. 確認健康
docker compose ps
```

## 六、環境變數

### nginxwebui
| 變數 | 默認值 | 說明 |
|------|--------|------|
| `JVM_XMX` | `256m` | JVM 最大堆記憶體 |
| `BOOT_OPTIONS` | （見 compose） | Spring Boot 啟動參數 |

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

## 七、entrypoint.sh 規範

```bash
#!/bin/sh
cd /home
exec java -Xmx${JVM_XMX:-256m} -jar -Dfile.encoding=UTF-8 nginxWebUI.jar ${BOOT_OPTIONS}
```

**強制要求：**
- 換行符必須為 **LF**（不可用 CRLF）
- Windows 環境修改後執行：`sed -i 's/\r$//' entrypoint.sh`
- 使用 `exec` 確保 Java 進程接收信號（tini 需要）
- 使用 `${JVM_XMX:-256m}` 支持環境變數覆蓋

## 八、Dockerfile 規範

### 必要元素
- `HEALTHCHECK` — 必須配置
- `VOLUME` — 數據目錄必須掛載
- `ENTRYPOINT` — 使用 `tini` 作為 init 進程
- `ENV` — 時區、語言、JVM 參數

### 不可包含
- 測試檔案（tests/）
- 開發工具（node_modules/）
- IDE 配置（.idea/, .vscode/）

## 九、跟 AI 協作的提示詞（直接複製貼上）

### 構建並啟動
```
請幫我構建 Docker 鏡像並啟動整個 stack。
1. mvn clean package -DskipTests
2. docker compose up -d --build
3. docker compose ps 確認所有服務健康
4. 告訴我各服務的訪問地址
```

### 升版
```
版本升級到 X.Y.Z：
1. 修改 docker-compose.yml 的 container_name 版本號
2. 修改 Dockerfile 或 pom.xml 的版本號
3. 重新構建並啟動
4. 確認所有服務健康
```

### 加新服務到 Stack
```
請在 docker-compose.yml 加入 [服務名稱]。
遵守命名規範：
- container_name: nginxwebui-{版本}-{服務名}
- volume name: nginxwebui_{服務名}_data
- 必須配置 healthcheck
- 必須配置 depends_on 和啟動順序
```

### 排查問題
```
[服務名稱] 啟動失敗，請排查：
1. docker compose ps 看狀態
2. docker logs [container_name] 看日誌
3. 找出原因並修復
```

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
