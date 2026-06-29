#!/bin/bash
# Nginx Web UI 一鍵部署腳本
# 用法：bash deploy.sh

set -e

REPO="https://github.com/elf-express/nginxWebUI.git"
INSTALL_DIR="/opt/nginxwebui"
TMP_DIR="/tmp/nginxwebui-deploy-$$"

echo "=== Nginx Web UI 部署腳本 ==="
echo ""

# 檢查必要工具
for cmd in git docker; do
  if ! command -v $cmd &>/dev/null; then
    echo "[錯誤] 找不到 $cmd，請先安裝"
    exit 1
  fi
done

if ! docker compose version &>/dev/null; then
  echo "[錯誤] 找不到 docker compose，請安裝 Docker Compose V2"
  exit 1
fi

# 判斷新裝還是升版
if [ -f "$INSTALL_DIR/docker-compose.yml" ]; then
  MODE="upgrade"
  echo "[升版模式] 偵測到現有安裝：$INSTALL_DIR"
else
  MODE="install"
  echo "[全新安裝] 目標目錄：$INSTALL_DIR"
fi

# 從 GitHub 拉取 docker/ 目錄
echo ""
echo ">>> 從 GitHub 拉取部署檔案..."
rm -rf "$TMP_DIR"
git clone --depth 1 --filter=blob:none --sparse "$REPO" "$TMP_DIR"
cd "$TMP_DIR"
git sparse-checkout set docker
echo "[完成] 部署檔案已下載"

# 建立安裝目錄
mkdir -p "$INSTALL_DIR/crowdsec"

# 複製檔案（不覆蓋 .env）
echo ""
echo ">>> 複製部署檔案到 $INSTALL_DIR..."
cp docker/docker-compose.yml "$INSTALL_DIR/"
cp docker/crowdsec/acquis.yml "$INSTALL_DIR/crowdsec/"
cp docker/crowdsec/abuseipdb.yaml "$INSTALL_DIR/crowdsec/"
cp docker/crowdsec/profiles.yaml "$INSTALL_DIR/crowdsec/"

# .env 處理
if [ ! -f "$INSTALL_DIR/.env" ]; then
  cp docker/.env.example "$INSTALL_DIR/.env"
  echo "[注意] 已建立 .env，請稍後編輯填入金鑰"
  NEED_ENV=true
else
  echo "[保留] .env 已存在，不覆蓋"
  NEED_ENV=false
fi

# 清理暫存
rm -rf "$TMP_DIR"
echo "[完成] 暫存檔案已清理"

# 顯示結果
echo ""
echo "=== 部署檔案就緒 ==="
ls -la "$INSTALL_DIR/"
echo ""

if [ "$NEED_ENV" = true ]; then
  echo ">>> 請編輯 .env 填入金鑰："
  echo "    nano $INSTALL_DIR/.env"
  echo ""
  read -p "編輯完成後按 Enter 繼續啟動，或按 Ctrl+C 稍後手動啟動..."
fi

# 啟動
echo ""
echo ">>> 啟動 Docker Stack..."
cd "$INSTALL_DIR"
docker compose up -d

echo ""
echo ">>> 等待服務啟動..."
sleep 5
docker compose ps

echo ""
echo "=== 部署完成 ==="
echo "  Nginx Web UI：http://$(hostname -I | awk '{print $1}'):8080"
echo ""
