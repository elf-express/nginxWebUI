#!/bin/bash
# nginxWebUI 的 crowdsec 啟動包裝：把 machine 密碼交給官方 /docker_start.sh 的 AGENT_USERNAME / AGENT_PASSWORD。
# 官方腳本在本機 LAPI 模式下看到兩者都有值時會執行
#   cscli machines add "$AGENT_USERNAME" --password "$AGENT_PASSWORD" -f /dev/null --force
# 每次啟動都重設密碼，可重複執行。
# 密碼來源：secrets 檔 /run/secrets/crowdsec_machine_password（去掉空白後非空才採用）→ 環境變數 AGENT_PASSWORD。
set -e

secret=/run/secrets/crowdsec_machine_password
if [ -f "$secret" ]; then
    from_file="$(tr -d ' \t\r\n' < "$secret")"
    if [ -n "$from_file" ]; then
        AGENT_PASSWORD="$from_file"
        export AGENT_PASSWORD
    fi
fi

if [ -n "${AGENT_PASSWORD:-}" ]; then
    export AGENT_USERNAME="${AGENT_USERNAME:-nginxwebui}"
else
    unset AGENT_USERNAME AGENT_PASSWORD
fi

exec /bin/bash /docker_start.sh "$@"
