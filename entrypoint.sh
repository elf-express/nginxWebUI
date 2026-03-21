#!/bin/sh

# 啟動前更新 GeoIP + Cloudflare Real IP
/usr/local/bin/update-geoip-cf.sh || true

# 啟動 cron（背景，每週三六自動更新）
crond

cd /home
exec java -Xmx${JVM_XMX:-256m} -jar -Dfile.encoding=UTF-8 nginxWebUI.jar ${BOOT_OPTIONS}
