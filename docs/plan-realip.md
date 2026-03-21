# Real IP + Cloudflare IP 自動更新 — 計畫書

## 目標
讓 nginx 正確識別真實用戶 IP（而非 CDN/代理 IP），並自動更新 Cloudflare IP 段。

## 為什麼重要
```
用戶 (1.2.3.4) → Cloudflare (103.21.244.x) → nginx → 日誌/CrowdSec

沒有 realip：nginx 看到的是 103.21.244.x（Cloudflare）
有了 realip：nginx 看到的是 1.2.3.4（真實用戶）
```

如果不設 realip：
- CrowdSec 封的是 Cloudflare IP → **整個 CDN 被封** → 全站掛掉
- 日誌裡全是 CDN IP → 看不出誰在攻擊
- 黑白名單 IP 功能失效

## 架構

```
┌──────────────────────────────────────────────────────┐
│                    容器內                              │
│                                                      │
│  ┌────────────┐    定時執行     ┌─────────────────┐   │
│  │ cron job   │ ─────────────→ │ cf-realip.sh    │   │
│  │ (每日一次)  │               │ (更新腳本)       │   │
│  └────────────┘               └────────┬────────┘   │
│                                        │             │
│                    ┌───────────────────▼──────────┐  │
│                    │ /etc/nginx/conf.d/realip.conf │  │
│                    │ set_real_ip_from 103.21.244.0 │  │
│                    │ set_real_ip_from 103.22.200.0 │  │
│                    │ ...                           │  │
│                    │ real_ip_header CF-Connecting-IP│  │
│                    └──────────────────────────────┘  │
│                                                      │
│  nginx reload ← 自動重載配置                          │
└──────────────────────────────────────────────────────┘
         ↑
         │ 下載最新 IP 段
         │
┌────────▼────────┐
│  Cloudflare API  │
│  /ips-v4         │
│  /ips-v6         │
└─────────────────┘
```

## 運作流程
1. 腳本從 Cloudflare 官方 API 下載最新 IPv4 + IPv6 段
2. 生成 `realip.conf`，包含所有 `set_real_ip_from` 指令
3. nginx 載入此 conf → 正確還原真實 IP
4. cron 每日自動執行一次（Cloudflare IP 段很少變動，每日足夠）

## Cloudflare 官方 IP 端點
- IPv4：`https://www.cloudflare.com/ips-v4`
- IPv6：`https://www.cloudflare.com/ips-v6`
- 無需 API Key，公開存取

## 需要的檔案

```
專案根目錄/
├── scripts/
│   └── cf-realip.sh         # 更新腳本
├── Dockerfile               # 加入 cron + 腳本
└── docker-compose.yml       # 不用改
```

## 修改範圍

| 檔案 | 改動 | 風險 |
|------|------|------|
| `scripts/cf-realip.sh` | 新建腳本 | 零（獨立腳本） |
| `Dockerfile` | 複製腳本 + 加 cron | 低（只加安裝步驟） |
| `entrypoint.sh` | 啟動時先執行一次 + 啟動 cron | 低 |

## 腳本內容（cf-realip.sh）
```bash
#!/bin/sh
# 從 Cloudflare 下載最新 IP 段，生成 nginx realip 配置

CONF="/etc/nginx/conf.d/realip.conf"

echo "# Cloudflare Real IP - Auto Updated $(date)" > $CONF
echo "" >> $CONF

# IPv4
for ip in $(curl -sf https://www.cloudflare.com/ips-v4); do
    echo "set_real_ip_from $ip;" >> $CONF
done

# IPv6
for ip in $(curl -sf https://www.cloudflare.com/ips-v6); do
    echo "set_real_ip_from $ip;" >> $CONF
done

echo "" >> $CONF
echo "real_ip_header CF-Connecting-IP;" >> $CONF
echo "real_ip_recursive on;" >> $CONF

# Reload nginx（如果正在運行）
nginx -t 2>/dev/null && nginx -s reload 2>/dev/null

echo "[$(date)] Cloudflare Real IP config updated."
```

## 自動化測試
新增 `tests/e2e/09-realip.spec.js`：
1. 驗證 realip.conf 存在
2. 驗證包含 Cloudflare IP 段
3. 驗證 `real_ip_header CF-Connecting-IP` 設定

## 時間估計
約 30 分鐘
