# Docker 容器構建通用規範

> 適用於任何專案的 Docker 容器化標準。所有條目為**強制性**，無選擇性。

## 一、Dockerfile 規範

### 基礎結構（必須包含）
```dockerfile
# 1. 基礎鏡像（指定版本，禁止用 latest）
FROM node:22-alpine

# 2. 環境變數（時區、語言、應用參數）
ENV TZ=Asia/Taipei \
    LANG=zh_TW.UTF-8

# 3. 系統依賴安裝（單一 RUN 層，最後清快取）
RUN apk add --no-cache curl tzdata \
    && ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && rm -rf /var/cache/apk/* /tmp/*

# 4. 應用檔案複製
COPY target/app.jar /app/app.jar

# 5. 資料卷宣告
VOLUME ["/app/data"]

# 6. 健康檢查（必須配置）
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8080/health || exit 1

# 7. 入口點
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 強制要求
| 項目 | 規則 |
|------|------|
| 基礎鏡像 | 指定版本號，禁止 `latest` |
| HEALTHCHECK | 必須配置，不可省略 |
| VOLUME | 資料目錄必須掛載 |
| 時區 | 必須設定，預設 `Asia/Taipei` |
| 清快取 | 安裝完套件後必須清除 |
| Shell 腳本 | 換行符必須為 **LF**，禁止 CRLF |

### 禁止包含
- 測試檔案、開發工具、IDE 配置
- 密碼、金鑰、證書等敏感資料
- `.git/`、`node_modules/`、`__pycache__/`

### .dockerignore（必須建立）
```
.git
.idea
.vscode
node_modules
tests/
docs/
*.md
__pycache__
.env
```

---

## 二、docker-compose.yml 規範

### 命名規範（強制）

#### container_name
```
{專案名}-{版本號}              ← 主服務
{專案名}-{版本號}-{服務名}     ← 附屬服務
```
例：
```yaml
container_name: myapp-1.0.0
container_name: myapp-1.0.0-postgres
container_name: myapp-1.0.0-redis
```

#### volume name
```yaml
volumes:
  app_data:
    name: {專案名}_data            # 主服務資料
  db_data:
    name: {專案名}_{服務名}_data   # 附屬服務資料
```
- 升版時 container_name 改版本號，volume name **不變**（資料保留）

#### 網路（可選）
```yaml
networks:
  default:
    name: {專案名}_network
```

### 啟動順序（強制）

#### depends_on 必須帶健康檢查
```yaml
# ✅ 正確：等資料庫健康後才啟動
depends_on:
  postgres:
    condition: service_healthy

# ❌ 錯誤：只等容器啟動，不等服務就緒
depends_on:
  - postgres
```

#### 典型啟動順序
```
資料庫 ──healthy──→ 應用服務
快取   ──healthy──→ 應用服務
日誌存儲 ──healthy──→ 日誌收集
日誌存儲 ──healthy──→ 日誌圖表
```

### 健康檢查模板

| 服務類型 | 檢查指令 | 間隔 | 啟動等待 | 重試 |
|---------|---------|------|---------|------|
| Web 應用 | `curl -sf http://localhost:PORT` | 30s | 30s | 3 |
| PostgreSQL | `pg_isready -U USER` | 10s | 10s | 5 |
| MySQL | `mysqladmin ping -h localhost` | 10s | 20s | 5 |
| Redis | `redis-cli ping` | 10s | 5s | 3 |
| MongoDB | `mongosh --eval "db.runCommand('ping')"` | 10s | 10s | 5 |
| Elasticsearch | `curl -sf http://localhost:9200` | 15s | 30s | 5 |
| Loki | `wget -qO- http://localhost:3100/ready` | 15s | 20s | 3 |
| RabbitMQ | `rabbitmq-diagnostics -q ping` | 15s | 30s | 5 |

```yaml
# 範例：PostgreSQL
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U myuser"]
  interval: 10s
  timeout: 5s
  start_period: 10s
  retries: 5
```

### 必要欄位
每個服務**必須**包含：
```yaml
services:
  myservice:
    image: xxx:版本號        # 指定版本，禁止 latest
    container_name: xxx      # 明確命名
    restart: unless-stopped  # 自動重啟策略
    volumes: []              # 資料持久化
    healthcheck: {}          # 健康檢查
```

---

## 三、環境變數管理

### 開發環境
直接寫在 `docker-compose.yml`：
```yaml
environment:
  - DB_HOST=postgres
  - DB_PORT=5432
```

### 正式環境
使用 `.env` 檔案（加入 .gitignore）：
```bash
# .env
DB_PASSWORD=強密碼
ADMIN_PASSWORD=強密碼
```

```yaml
# docker-compose.yml
environment:
  - DB_PASSWORD=${DB_PASSWORD}
```

### 敏感資訊規則
| 類型 | 開發環境 | 正式環境 |
|------|---------|---------|
| 端口 | 直接寫 | 直接寫 |
| 資料庫帳號 | 直接寫 | `.env` |
| 密碼 | 弱密碼可直接寫 | **必須** `.env` |
| API Key | 禁止寫入 | **必須** `.env` 或 Docker Secrets |

---

## 四、常用 Stack 模板

### 模板 A：Web 應用 + PostgreSQL
```yaml
services:
  app:
    build: .
    container_name: ${PROJECT}-${VERSION}
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - app_data:/app/data
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8080"]
      interval: 30s
      timeout: 5s
      start_period: 30s
      retries: 3

  postgres:
    image: postgres:18-alpine
    container_name: ${PROJECT}-${VERSION}-postgres
    restart: unless-stopped
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      - POSTGRES_DB=${PROJECT}
      - POSTGRES_USER=${PROJECT}
      - POSTGRES_PASSWORD=changeme
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${PROJECT}"]
      interval: 10s
      timeout: 5s
      start_period: 10s
      retries: 5

volumes:
  app_data:
    name: ${PROJECT}_data
  postgres_data:
    name: ${PROJECT}_postgres_data
```

### 模板 B：加入 Redis 快取
```yaml
  redis:
    image: redis:8-alpine
    container_name: ${PROJECT}-${VERSION}-redis
    restart: unless-stopped
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      start_period: 5s
      retries: 3

volumes:
  redis_data:
    name: ${PROJECT}_redis_data
```

### 模板 C：加入日誌監控（Loki + Grafana）
```yaml
  loki:
    image: grafana/loki:3.5.0
    container_name: ${PROJECT}-${VERSION}-loki
    restart: unless-stopped
    volumes:
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:3100/ready || exit 1"]
      interval: 15s
      timeout: 5s
      start_period: 20s
      retries: 3

  promtail:
    image: grafana/promtail:3.5.0
    container_name: ${PROJECT}-${VERSION}-promtail
    restart: unless-stopped
    volumes:
      - app_data:/var/log/app:ro
      - ./promtail-config.yml:/etc/promtail/config.yml:ro
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki

  grafana:
    image: grafana/grafana:11.6.0
    container_name: ${PROJECT}-${VERSION}-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on:
      loki:
        condition: service_healthy

volumes:
  loki_data:
    name: ${PROJECT}_loki_data
  grafana_data:
    name: ${PROJECT}_grafana_data
```

---

## 五、操作指令速查

### 構建與啟動
```bash
docker compose up -d --build       # 構建 + 啟動
docker compose ps                  # 查看狀態
docker compose logs -f [服務名]     # 即時日誌
```

### 日常維護
```bash
docker compose restart [服務名]     # 重啟
docker compose stop                # 停止（保留容器）
docker compose down                # 停止 + 刪除容器
docker compose down -v             # 停止 + 刪除容器和資料（危險！）
```

### 排查問題
```bash
docker compose ps -a               # 看全部容器（含已停止）
docker logs [container_name]       # 看日誌
docker exec -it [container_name] sh # 進入容器
docker inspect [container_name]    # 看詳細配置
```

### 資料備份
```bash
# 備份 PostgreSQL
docker exec [pg_container] pg_dump -U [user] [db] > backup.sql

# 備份 Volume
docker run --rm -v [volume_name]:/data -v $(pwd):/backup \
  alpine tar czf /backup/volume-backup.tar.gz /data
```

---

## 六、跟 AI 協作的提示詞

### 容器化一個新專案
```
請幫我的專案建立 Docker 容器化：
1. 寫 Dockerfile（必須含 HEALTHCHECK）
2. 寫 docker-compose.yml（依照 Docker 通用規範）
3. 命名規範：container_name={專案名}-{版本}-{服務名}
4. 所有服務必須配 healthcheck 和 depends_on condition
5. 資料庫必須先於應用啟動（service_healthy）
6. 建立 .dockerignore
7. 構建並確認所有服務健康
```

### 加新服務到現有 Stack
```
請在 docker-compose.yml 加入 [服務名稱]（如 Redis / Elasticsearch / RabbitMQ）。
依照 Docker 通用規範：
- container_name: {專案名}-{版本}-{服務名}
- volume name: {專案名}_{服務名}_data
- 必須配 healthcheck
- 必須配正確的 depends_on 啟動順序
```

### 排查容器問題
```
[服務名稱] 無法啟動，請排查：
1. docker compose ps -a 看狀態
2. docker logs [container_name] 看錯誤日誌
3. 檢查健康檢查是否正確
4. 檢查 depends_on 啟動順序
5. 檢查端口是否衝突
6. 找出原因並修復
```
