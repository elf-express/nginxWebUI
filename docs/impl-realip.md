# Real IP + Cloudflare IP 自動更新 — 實作計畫（開發用）

## Step 1：建立更新腳本

### scripts/cf-realip.sh
```bash
#!/bin/sh
CONF="/etc/nginx/conf.d/realip.conf"
TEMP="/tmp/realip.conf.tmp"

echo "# Cloudflare Real IP - Auto Updated $(date)" > $TEMP
echo "" >> $TEMP

# 下載 IPv4
IPV4=$(curl -sf https://www.cloudflare.com/ips-v4)
if [ -z "$IPV4" ]; then
    echo "[ERROR] Failed to fetch Cloudflare IPv4 list"
    exit 1
fi

for ip in $IPV4; do
    echo "set_real_ip_from $ip;" >> $TEMP
done

# 下載 IPv6
IPV6=$(curl -sf https://www.cloudflare.com/ips-v6)
if [ -z "$IPV6" ]; then
    echo "[ERROR] Failed to fetch Cloudflare IPv6 list"
    exit 1
fi

for ip in $IPV6; do
    echo "set_real_ip_from $ip;" >> $TEMP
done

echo "" >> $TEMP
echo "real_ip_header CF-Connecting-IP;" >> $TEMP
echo "real_ip_recursive on;" >> $TEMP

# 驗證 nginx 配置後才替換
mv $TEMP $CONF
nginx -t 2>/dev/null && nginx -s reload 2>/dev/null

echo "[$(date)] Cloudflare Real IP config updated."
```

**注意：**
- 先寫到 temp 檔，下載成功才覆蓋（防止下載失敗清空配置）
- `nginx -t` 先驗證配置正確才 reload

## Step 2：修改 Dockerfile

在 COPY jar 之前加入：
```dockerfile
# Cloudflare Real IP 更新腳本
COPY scripts/cf-realip.sh /usr/local/bin/cf-realip.sh
RUN chmod +x /usr/local/bin/cf-realip.sh \
    && mkdir -p /etc/nginx/conf.d

# cron job：每日凌晨 3 點更新 Cloudflare IP
RUN echo "0 3 * * * /usr/local/bin/cf-realip.sh >> /var/log/cf-realip.log 2>&1" \
    > /etc/crontabs/root
```

## Step 3：修改 entrypoint.sh

```bash
#!/bin/sh

# 啟動前先更新 Cloudflare Real IP
/usr/local/bin/cf-realip.sh

# 啟動 cron（背景）
crond

cd /home
exec java -Xmx${JVM_XMX:-256m} -jar -Dfile.encoding=UTF-8 nginxWebUI.jar ${BOOT_OPTIONS}
```

## Step 4：nginx 載入 conf.d

需確認 nginx.conf 有 include conf.d：
```nginx
http {
    include /etc/nginx/conf.d/*.conf;
    ...
}
```

如果沒有，在 nginxWebUI 的 http 參數中加入：
- 名稱：`include`
- 值：`/etc/nginx/conf.d/*.conf`

## Step 5：Playwright 測試

### 09-realip.spec.js
```javascript
test('應有 Cloudflare Real IP 配置', async () => {
    // 透過 Docker exec 檢查 realip.conf
    const result = execFileSync('docker', [
        'exec', 'nginxwebui-5.0.1',
        'cat', '/etc/nginx/conf.d/realip.conf'
    ]).toString();

    expect(result).toContain('set_real_ip_from');
    expect(result).toContain('real_ip_header CF-Connecting-IP');
    expect(result).toContain('real_ip_recursive on');
});
```

## Step 6：驗證清單

- [ ] cf-realip.sh 可正常執行
- [ ] realip.conf 包含 Cloudflare IPv4 段
- [ ] realip.conf 包含 Cloudflare IPv6 段
- [ ] realip.conf 包含 real_ip_header CF-Connecting-IP
- [ ] nginx -t 驗證通過
- [ ] cron 每日自動執行
- [ ] 容器重啟後自動執行一次
- [ ] 下載失敗不會清空現有配置

## 生成的 realip.conf 範例
```nginx
# Cloudflare Real IP - Auto Updated Fri Mar 21 2026

set_real_ip_from 103.21.244.0/22;
set_real_ip_from 103.22.200.0/22;
set_real_ip_from 103.31.4.0/22;
set_real_ip_from 104.16.0.0/13;
set_real_ip_from 104.24.0.0/14;
set_real_ip_from 108.162.192.0/18;
set_real_ip_from 131.0.72.0/22;
set_real_ip_from 141.101.64.0/18;
set_real_ip_from 162.158.0.0/15;
set_real_ip_from 172.64.0.0/13;
set_real_ip_from 173.245.48.0/20;
set_real_ip_from 188.114.96.0/20;
set_real_ip_from 190.93.240.0/20;
set_real_ip_from 197.234.240.0/22;
set_real_ip_from 198.41.128.0/17;

set_real_ip_from 2400:cb00::/32;
set_real_ip_from 2606:4700::/32;
set_real_ip_from 2803:f800::/32;
set_real_ip_from 2405:b500::/32;
set_real_ip_from 2405:8100::/32;
set_real_ip_from 2a06:98c0::/29;
set_real_ip_from 2c0f:f248::/32;

real_ip_header CF-Connecting-IP;
real_ip_recursive on;
```
