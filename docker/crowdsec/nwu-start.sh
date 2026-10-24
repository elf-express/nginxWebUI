#!/bin/bash
# nginxWebUI 的 crowdsec 啟動包裝：把 machine 密碼交給官方 /docker_start.sh 的 AGENT_USERNAME / AGENT_PASSWORD。
# 官方腳本在本機 LAPI 模式下看到兩者都有值時會執行
#   cscli machines add "$AGENT_USERNAME" --password "$AGENT_PASSWORD" -f /dev/null --force
# 每次啟動都重設密碼，可重複執行。
# 密碼來源：secrets 檔 /run/secrets/crowdsec_machine_password（去掉頭尾空白後非空才採用）→ 環境變數 AGENT_PASSWORD。
set -e

secret=/run/secrets/crowdsec_machine_password
if [ -f "$secret" ]; then
    # 只去頭尾空白（含 CR/LF），與 Java CrowdSecCredentials 的 trim() 一致；密碼中間的空白保留
    from_file="$(cat "$secret")"
    from_file="${from_file#"${from_file%%[![:space:]]*}"}"
    from_file="${from_file%"${from_file##*[![:space:]]}"}"
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

# WAF 規則調整（E-395）：每次啟動都先用預設的自訂 appsec-config，crowdsec 起來後由監看程式套用上一份成功的設定。
# 沒有共用 volume 時不做其他事；複製規則目錄失敗只印警告，不讓 crowdsec 起不來。
cp -f /nwu/nwu-custom.default.yaml /etc/crowdsec/appsec-configs/nwu-custom.yaml
if [ -d /nwu-waf ]; then
    (
        # catalog 若是 symlink 先刪掉；每個檔案先寫到 catalog 內的暫存檔再 mv -f，不跟著 symlink 寫
        if [ -L /nwu-waf/catalog ]; then rm -f /nwu-waf/catalog; fi
        mkdir -p /nwu-waf/catalog
        for f in /nwu/catalog/*; do
            t="$(mktemp -u /nwu-waf/catalog/.nwu.XXXXXX)" || exit 1
            if ( umask 022; /usr/bin/timeout 10 /bin/dd if="$f" of="$t" conv=excl oflag=nofollow status=none ) && [ -f "$t" ] && [ ! -L "$t" ] && mv -fT "$t" "/nwu-waf/catalog/$(basename "$f")"; then :; else rm -f "$t"; exit 1; fi
        done
    ) || echo "nwu-waf: catalog copy failed" >&2
    /bin/bash /nwu/nwu-waf-watch.sh &
fi

exec /bin/bash /docker_start.sh "$@"
