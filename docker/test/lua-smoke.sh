#!/bin/sh
# Smoke test: the image can load lua-cs-bouncer and the nwu wrapper under nginx.
# `nginx -t` does not run init_by_lua, so after the syntax check nginx is really started
# with a stub LAPI (127.0.0.1:18082, live mode) and the whole access path is exercised:
#   LAPI up   : probe succeeds (state lapiOk:true), allowlisted and clean IPs get 200,
#               a banned IP (127.0.0.2) is cut off with 444 (curl exit 52)
#   LAPI down : after the grace period a non-allowlisted request gets the 503 page
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
API_URL=http://127.0.0.1:18082
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
        assert(w.init({ conf = "/tmp/smoke/bouncer.conf", grace = 8,
                        state = "/tmp/smoke/state.json",
                        page = "/usr/local/lib/nginxwebui/html/protection-paused.html" }))
    }
    init_worker_by_lua_block { require("nwu_crowdsec").init_worker() }
    # stub LAPI: 127.0.0.2 is banned, everything else has no decision; /tmp/smoke/lapi_down turns it into a 500
    server {
        listen 127.0.0.1:18082;
        access_log off;
        location /v1/decisions {
            if (-f /tmp/smoke/lapi_down) { return 500 "down"; }
            if (\$arg_ip = "127.0.0.2") {
                return 200 "[{\"id\":1,\"origin\":\"cscli\",\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"127.0.0.2\",\"duration\":\"1h\",\"scenario\":\"smoke\"}]";
            }
            return 200 "null";
        }
    }
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
fail() { echo "FAIL: $*"; cat /tmp/smoke/logs/error.log | grep -v "no file"; exit 1; }
nginx -t -c /tmp/smoke/nginx.conf -p /tmp/smoke/
nginx -c /tmp/smoke/nginx.conf -p /tmp/smoke/

# Phase 1: LAPI up. First probe runs after 5s.
sleep 8
state=$(cat /tmp/smoke/state.json 2>/dev/null || echo missing)
echo "$state" | grep -q "\"lapiOk\":true" || fail "state file after successful probe: $state"
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/allow)
[ "$code" = "200" ] || fail "allowlisted request returned $code"
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/guard)
[ "$code" = "200" ] || fail "clean IP on guarded path returned $code"
rc=0; curl -s -o /dev/null --interface 127.0.0.2 http://127.0.0.1:18081/guard || rc=$?
[ "$rc" = "52" ] || fail "banned IP: curl exit $rc, expected 52 (444 connection close)"

# Phase 2: LAPI down. Grace is 8s (probe interval is 5s); wait until the last success is older than grace.
touch /tmp/smoke/lapi_down
sleep 10
paused=$(curl -s -D - -o /tmp/smoke/paused.html http://127.0.0.1:18081/guard)
down_state=$(cat /tmp/smoke/state.json)
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/allow)

nginx -c /tmp/smoke/nginx.conf -p /tmp/smoke/ -s stop || true
cat /tmp/smoke/logs/error.log | grep -v "no file"
echo "$paused" | head -1 | grep -q " 503" || fail "LAPI down: guarded request was not 503: $paused"
echo "$paused" | grep -qi "^Retry-After: 30" || fail "no Retry-After header"
grep -q "<h1>" /tmp/smoke/paused.html || fail "503 page has no h1"
echo "$down_state" | grep -q "\"lapiOk\":false" || fail "state after LAPI down: $down_state"
[ "$code" = "200" ] || fail "allowlisted request while LAPI down returned $code"
# Expected [error] lines: lua-cs-bouncer captcha-not-configured at init; LAPI 500 from the stub is not logged as [error].
if grep -E "\[(error|emerg|alert|crit)\]" /tmp/smoke/logs/error.log | grep -v -e "error loading captcha plugin"; then
  fail "errors in nginx error.log"
fi
# Phase 3: the include is still in nginx.conf but the bouncer conf file is gone (key removed, stale file cleaned up).
# The wrapper must be inert: guarded paths pass immediately and still pass after the grace period.
i=0; while [ -f /tmp/smoke/nginx.pid ] && [ "$i" -lt 20 ]; do sleep 0.5; i=$((i + 1)); done
sed -e 's#/tmp/smoke/bouncer.conf#/tmp/smoke/absent.conf#' \
    -e 's#/tmp/smoke/nginx.pid#/tmp/smoke/nginx3.pid#' \
    -e 's#logs/error.log#logs/error3.log#' /tmp/smoke/nginx.conf > /tmp/smoke/nginx3.conf
[ ! -e /tmp/smoke/absent.conf ] || fail "absent.conf must not exist"
nginx -t -c /tmp/smoke/nginx3.conf -p /tmp/smoke/
nginx -c /tmp/smoke/nginx3.conf -p /tmp/smoke/
sleep 2
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/guard)
[ "$code" = "200" ] || fail "missing bouncer conf: guarded request returned $code right after start"
sleep 12
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/guard)
[ "$code" = "200" ] || fail "missing bouncer conf: guarded request returned $code after the grace period"
code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18081/allow)
[ "$code" = "200" ] || fail "missing bouncer conf: allowlisted request returned $code"
nginx -c /tmp/smoke/nginx3.conf -p /tmp/smoke/ -s stop || true
cat /tmp/smoke/logs/error3.log | grep -v "no file" || true
grep -q "bouncer conf not found" /tmp/smoke/logs/error3.log || fail "no not-configured notice in error3.log"
if grep -E "\[(error|emerg|alert|crit)\]|cannot read bouncer conf|crowdsec init failed" /tmp/smoke/logs/error3.log | grep -v -e "no file"; then
  fail "init errors with missing bouncer conf"
fi

echo "lua smoke: ok (up: state lapiOk:true, clean 200, banned curl exit 52; down: guarded 503, allowlisted 200, state $down_state; conf missing: guarded 200 before and after grace)"
'

docker run --rm --entrypoint sh "$IMAGE" -c "$INNER"
