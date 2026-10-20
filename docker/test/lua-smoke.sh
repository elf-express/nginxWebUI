#!/bin/sh
# Smoke test: the image can load lua-cs-bouncer and the nwu wrapper under nginx.
# `nginx -t` does not run init_by_lua, so after the syntax check nginx is really started
# and error.log must stay free of [error]/[emerg]/[alert] entries.
# Usage: sh docker/test/lua-smoke.sh <image>
set -eu

IMAGE="${1:?usage: lua-smoke.sh <image>}"

# Runs inside the container (single-quoted: expanded by the container shell).
INNER='
set -eu
mkdir -p /tmp/smoke/logs && chmod 777 /tmp/smoke /tmp/smoke/logs
for f in /usr/local/lib/nginxwebui/html/protection-paused.html \
         /var/lib/crowdsec/lua/templates/ban.html \
         /etc/nginx/nwu/crowdsec_lua_paths.conf; do
  [ -f "$f" ] || { echo "FAIL: missing $f"; exit 1; }
done
cat > /tmp/smoke/bouncer.conf <<CONF
ENABLED=true
API_URL=http://127.0.0.1:1
API_KEY=smoke
MODE=live
BAN_TEMPLATE_PATH=/var/lib/crowdsec/lua/templates/ban.html
CONF
cat > /tmp/smoke/nginx.conf <<CONF
load_module /usr/lib/nginx/modules/ndk_http_module.so;
load_module /usr/lib/nginx/modules/ngx_http_lua_module.so;
pid /tmp/smoke/nginx.pid;
error_log /tmp/smoke/logs/error.log info;
events {}
http {
    include /etc/nginx/nwu/crowdsec_lua_paths.conf;
    lua_shared_dict crowdsec_cache 1m;
    lua_shared_dict nwu_crowdsec 1m;
    init_by_lua_block {
        require "resty.http"
        require "cjson"
        require "crowdsec"
        local w = require "nwu_crowdsec"
        assert(w.init({ conf = "/tmp/smoke/bouncer.conf", grace = 2,
                        state = "/tmp/smoke/state.json",
                        page = "/usr/local/lib/nginxwebui/html/protection-paused.html" }))
    }
    init_worker_by_lua_block { require("nwu_crowdsec").init_worker() }
    server {
        listen 127.0.0.1:18081;
        access_log off;
        location /allow {
            set \$nwu_allowlist 1;
            access_by_lua_block { require("nwu_crowdsec").access() }
            content_by_lua_block { ngx.print("ok") }
        }
        location /guard {
            set \$nwu_allowlist 0;
            access_by_lua_block { require("nwu_crowdsec").access() }
            content_by_lua_block { ngx.print("ok") }
        }
    }
}
CONF
nginx -t -c /tmp/smoke/nginx.conf -p /tmp/smoke/
nginx -c /tmp/smoke/nginx.conf -p /tmp/smoke/
# LAPI is unreachable and grace is 2s, so after the first probes the guarded location must answer 503.
sleep 8
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/allow)
paused=$(curl -s -D - -o /tmp/smoke/paused.html http://127.0.0.1:18081/guard)
state=$(cat /tmp/smoke/state.json 2>/dev/null || echo missing)
nginx -c /tmp/smoke/nginx.conf -p /tmp/smoke/ -s stop || true
cat /tmp/smoke/logs/error.log
[ "$code" = "200" ] || { echo "FAIL: allowlisted request returned $code"; exit 1; }
# Expected [error] lines: lua-cs-bouncer captcha-not-configured at init; probe connect() refused (LAPI is deliberately unreachable).
echo "$paused" | head -1 | grep -q " 503" || { echo "FAIL: guarded request was not 503: $paused"; exit 1; }
echo "$paused" | grep -qi "^Retry-After: 30" || { echo "FAIL: no Retry-After header"; exit 1; }
grep -q "<h1>" /tmp/smoke/paused.html || { echo "FAIL: 503 page has no h1"; exit 1; }
echo "$state" | grep -q "\"lapiOk\":false" || { echo "FAIL: unexpected state file: $state"; exit 1; }
if grep -E "\[(error|emerg|alert|crit)\]" /tmp/smoke/logs/error.log | grep -v -e "error loading captcha plugin" -e "connect() failed"; then
  echo "FAIL: errors in nginx error.log"; exit 1
fi
echo "lua smoke: ok (allowlisted -> $code, guarded -> 503, state $state)"
'

docker run --rm --entrypoint sh "$IMAGE" -c "$INNER"
