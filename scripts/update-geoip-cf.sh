#!/bin/sh
set -eu

# === 參數 ===
GEOIP_DIR="/etc/nginx/geoip"
CF_V4_URL="https://www.cloudflare.com/ips-v4"
CF_V6_URL="https://www.cloudflare.com/ips-v6"
COUNTRY_URL="https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb"
CITY_URL="https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb"
ASN_URL="https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-ASN.mmdb"

# 本機/內網信任來源（Docker 內網 + 常見私有網段）
LOCAL_TRUST="
127.0.0.1
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
"

mkdir -p "$GEOIP_DIR"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# === GeoLite2 mmdb 下載（含 freshness check）===
# 已存在且 7 天內更新過則跳過：crontab 每週三六自動跑、entrypoint 啟動時跑一次，
# 不必每次 container restart 都重抓 ~80 MB。
dl_mmdb() {
  local url="$1" name="$2"
  local target="$GEOIP_DIR/$name"

  if [ -f "$target" ] && find "$target" -mtime -7 -print -quit | grep -q .; then
    echo "[$(date)] $name 已是新檔（< 7 天），跳過下載"
    return 0
  fi

  echo "[$(date)] 下載 $name ..."
  if curl -fL --retry 3 -o "$TMP/$name" "$url"; then
    mv "$TMP/$name" "$target"
    chmod 644 "$target"
  else
    echo "[WARN] 無法下載 $name，保留現有檔案" >&2
  fi
}

dl_mmdb "$COUNTRY_URL" "GeoLite2-Country.mmdb"
dl_mmdb "$CITY_URL"    "GeoLite2-City.mmdb"
dl_mmdb "$ASN_URL"     "GeoLite2-ASN.mmdb"

# === Cloudflare Real IP 清單 ===
echo "[$(date)] 更新 Cloudflare IP 清單 ..."

# IPv4
IPV4=$(curl -fsS "$CF_V4_URL")
if [ -z "$IPV4" ]; then
  echo "[ERROR] 無法取得 Cloudflare IPv4 清單" >&2
  exit 1
fi

# IPv6
IPV6=$(curl -fsS "$CF_V6_URL")
if [ -z "$IPV6" ]; then
  echo "[ERROR] 無法取得 Cloudflare IPv6 清單" >&2
  exit 1
fi

# 寫入暫存（原子替換）
{
  echo "# Cloudflare Real IP - Auto Updated $(date)"
  echo ""
  echo "# IPv4"
  echo "$IPV4" | awk '{print "set_real_ip_from " $1 ";"}'
  echo ""
  echo "# IPv6"
  echo "$IPV6" | awk '{print "set_real_ip_from " $1 ";"}'
  echo ""
  echo "# Local / Docker / Private Network Trust"
  for cidr in $LOCAL_TRUST; do
    echo "set_real_ip_from $cidr;"
  done
  echo ""
  echo "real_ip_header CF-Connecting-IP;"
  echo "real_ip_recursive on;"
} > "$TMP/realip.conf"

mv "$TMP/realip.conf" "$GEOIP_DIR/realip.conf"
chmod 644 "$GEOIP_DIR/realip.conf"

# === 測試並重載 nginx ===
if nginx -t 2>/dev/null; then
  nginx -s reload 2>/dev/null || true
  echo "[OK] GeoIP + Cloudflare Real IP 已更新並重新載入 nginx"
else
  echo "[WARN] nginx -t 失敗，未重載" >&2
fi
