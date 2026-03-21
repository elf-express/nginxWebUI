# CrowdSec + AbuseIPDB — 實作計畫（開發用）

## Step 1：建立 CrowdSec 配置目錄和檔案

### acquis.yml（日誌來源）
```yaml
filenames:
  - /var/log/nginx/**/*.log
labels:
  type: nginx
```

## Step 2：更新 docker-compose.yml

### 新增 crowdsec 服務
```yaml
crowdsec:
  image: crowdsecurity/crowdsec:v1.6
  container_name: nginxwebui-{版本}-crowdsec
  restart: unless-stopped
  volumes:
    - nginxwebui_data:/var/log/nginx:ro          # 讀取 nginx 日誌
    - crowdsec_data:/var/lib/crowdsec/data       # 決策資料
    - crowdsec_config:/etc/crowdsec              # 配置
    - ./crowdsec/acquis.yml:/etc/crowdsec/acquis.yaml:ro
  environment:
    - COLLECTIONS=crowdsecurity/nginx crowdsecurity/http-cve crowdsecurity/base-http-scenarios
    - ENROLL_KEY=（CrowdSec Console 的 key，可選）
  healthcheck:
    test: ["CMD", "cscli", "version"]
    interval: 15s
    timeout: 5s
    start_period: 30s
    retries: 3
```

### 新增 bouncer 服務
```yaml
crowdsec-bouncer:
  image: fbonalair/traefik-crowdsec-bouncer:0.6
  container_name: nginxwebui-{版本}-bouncer
  restart: unless-stopped
  environment:
    - CROWDSEC_BOUNCER_API_KEY=（需從 crowdsec 容器取得）
    - CROWDSEC_AGENT_HOST=crowdsec:8080
    - PORT=8181
  depends_on:
    crowdsec:
      condition: service_healthy
```

### 新增 volumes
```yaml
volumes:
  crowdsec_data:
    name: nginxwebui_crowdsec_data
  crowdsec_config:
    name: nginxwebui_crowdsec_config
```

## Step 3：取得 Bouncer API Key

容器首次啟動後執行：
```bash
docker exec nginxwebui-{版本}-crowdsec cscli bouncers add nginx-bouncer
```
把產生的 key 填入 bouncer 的 `CROWDSEC_BOUNCER_API_KEY`。

## Step 4：整合 AbuseIPDB

```bash
# 進入 crowdsec 容器
docker exec -it nginxwebui-{版本}-crowdsec sh

# 安裝 AbuseIPDB blocklist
cscli hub update
cscli collections install crowdsecurity/abuseipdb

# 設定 API Key（免費帳號每天 1000 次查詢）
cscli console enroll （你的 key）
```

或透過環境變數：
```yaml
environment:
  - BOUNCER_KEY_nginx=auto-generated
```

## Step 5：nginx 整合（auth_request 模式）

在 nginxWebUI 的 http 參數或 server 額外參數中加入：
```nginx
# 每個請求先查詢 bouncer
auth_request /crowdsec-check;
auth_request_set $auth_status $upstream_status;
```

加一個 location：
```nginx
location = /crowdsec-check {
    internal;
    proxy_pass http://crowdsec-bouncer:8181/api/v1/forwardAuth;
    proxy_set_header X-Forwarded-For $remote_addr;
}
```

## Step 6：Playwright 測試

### 08-crowdsec.spec.js
```javascript
test('CrowdSec 容器應健康運行', async () => {
  // 透過 Docker API 或 curl 檢查
});

test('正常請求應正常回應', async ({ page }) => {
  const response = await page.goto('/adminPage/login');
  expect(response.status()).toBe(200);
});

test('大量 404 請求後應被限制', async ({ page }) => {
  // 連續發送不存在路徑的請求
  // 驗證最終被 403 或 429
});
```

## Step 7：驗證清單

- [ ] CrowdSec 容器健康
- [ ] Bouncer 容器健康
- [ ] CrowdSec 可讀取 nginx 日誌
- [ ] 正常存取不受影響
- [ ] 模擬攻擊後 IP 被封鎖
- [ ] AbuseIPDB 查詢正常
- [ ] Grafana 可查看 CrowdSec 決策日誌
