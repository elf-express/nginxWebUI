#!/bin/sh
# Smoke test of the nwu challenge inside the nginxwebui image (no CrowdSec: stub LAPI, live mode, own test pages).
#   builtin mode, https, captcha IP 127.0.0.3:
#     page request -> 302 to the reserved path; challenge page -> solved like a browser -> POST verify -> 303 back to
#     the page with a Secure nwu_pass cookie -> 200 with the cookie; replayed answer, garbage answer -> 429;
#     tampered cookie, the cookie from another captcha IP (127.0.0.6) -> challenged again; POST / -> 429, no redirect
#     redirects to the challenge carry Cache-Control: no-store; an answer too big for the body buffer -> 429
#   plain http: captcha IP -> 429 page with the client IP, no challenge
#   banned IP 127.0.0.2 -> 444 on the reserved path too; CrowdSec whitelist decision 127.0.0.4 -> 200, no challenge
#   IP without a decision 127.0.0.5 on the reserved path: altcha.js -> 200; page, POST verify, callback and unknown
#     paths -> 303 back to a same-site path (no challenge, no check, no cookie)
#   external mode: page request -> 302 to the external URL with a valid request ticket; callback with a signed response
#     ticket -> cookie; replayed / tampered / expired / other-IP / other-host tickets -> 429; POST verify -> 404;
#     HTTP/1.0 without Host on a server without server_name (empty $host) -> 429
#   bouncer loaded without the challenge, both the old conf before apply (no challenge in init) and fallback mode (the
#     challenge init fails): reserved path page, POST verify, callback and unknown paths -> 303 back, altcha.js -> 404,
#     the site never sees the reserved path; ban still 444, whitelist still reaches the site
#   no redirect loop: return targets inside the reserved path (r=/.well-known/nwu-challenge/..., ticket path) -> "/"
#   stream mode (stub /v1/decisions/stream, IP 127.0.0.3): captcha -> covering /24 ban still blocks with a pass cookie ->
#     the captcha is deleted -> still banned (the image patches deletion by decision type) -> the ban is deleted -> 200
#   resty.sha256 inside nginx gives the known SHA-256 vector; no [error] lines besides the bouncer's captcha plugin
# Usage: sh docker/test/challenge-smoke.sh <image>
set -eu
export MSYS_NO_PATHCONV=1

IMAGE="${1:?usage: challenge-smoke.sh <image>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$(cd "$HERE/../lua/test" && pwd)"
if command -v cygpath >/dev/null 2>&1; then
  TOOLS="$(cygpath -m "$TOOLS")"
fi

# Runs inside the container (single-quoted: expanded by the container shell). Test tools are mounted at /t.
INNER='
set -u
W=/tmp/cs
mkdir -p $W/logs $W/pages && chmod 777 $W $W/logs
for f in /usr/local/lib/nginxwebui/lua/nwu_challenge.lua /usr/local/lib/nginxwebui/lua/nwu_challenge_policy.lua \
         /usr/share/lua/common/resty/sha.lua /usr/share/lua/common/resty/sha256.lua \
         /usr/local/lib/nginxwebui/html/altcha.js; do
  [ -f "$f" ] || { echo "FAIL: missing $f"; exit 1; }
done
LUA="luajit -e package.path=\"/t/?.lua;/usr/local/lib/nginxwebui/lua/?.lua;\"..package.path;package.cpath=\"/usr/lib/lua/5.1/?.so;\"..package.cpath"
COOKIE_SECRET=smoke-cookie-secret
TICKET_SECRET=smoke-ticket-secret

openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=shop.test \
  -keyout $W/key.pem -out $W/cert.pem >/dev/null 2>&1
cat > $W/bouncer.conf <<CONF
ENABLED=true
API_URL=http://127.0.0.1:18082
API_KEY=smoke
MODE=live
BOUNCING_ON_TYPE=all
BAN_TEMPLATE_PATH=/var/lib/crowdsec/lua/templates/ban.html
CONF
cat > $W/builtin.conf <<CONF
MODE=builtin
TTL_SECONDS=3600
COOKIE_SECRET=$COOKIE_SECRET
TICKET_SECRET=$TICKET_SECRET
EXTERNAL_URL=
CONF
cat > $W/external.conf <<CONF
MODE=external
TTL_SECONDS=3600
COOKIE_SECRET=$COOKIE_SECRET
TICKET_SECRET=$TICKET_SECRET
EXTERNAL_URL=https://verify.example/check
CONF
echo "<altcha-widget challengejson=\"{{CHALLENGE}}\"></altcha-widget><input name=\"r\" value=\"{{RETURN}}\"><p>{{IP}} {{TIME}}</p>" > $W/pages/challenge.html
echo "<h1>http blocked</h1><p>{{IP}} {{TIME}}</p>" > $W/pages/http-blocked.html
echo "<h1>verify failed</h1><p>{{IP}}</p>" > $W/pages/verify-failed.html
echo "<h1>callback failed</h1><p>{{IP}}</p>" > $W/pages/callback-failed.html

cat > $W/stream.conf <<CONF
ENABLED=true
API_URL=http://127.0.0.1:18082
API_KEY=smoke
MODE=stream
UPDATE_FREQUENCY=1
BOUNCING_ON_TYPE=all
BAN_TEMPLATE_PATH=/var/lib/crowdsec/lua/templates/ban.html
CONF

write_conf() { # write_conf <challenge conf, or - for none> [bouncer conf]
if [ "$1" = "-" ]; then
  CHALLENGE="nil"
else
  CHALLENGE="{ conf = \"$1\", pages = \"$W/pages/\", altcha = \"/usr/local/lib/nginxwebui/html/altcha.js\" }"
fi
cat > $W/nginx.conf <<CONF
load_module /usr/lib/nginx/modules/ndk_http_module.so;
load_module /usr/lib/nginx/modules/ngx_http_lua_module.so;
pid $W/nginx.pid;
error_log $W/logs/error.log info;
events {}
http {
    include /etc/nginx/nwu/crowdsec_lua_paths.conf;
    lua_shared_dict crowdsec_cache 1m;
    lua_shared_dict nwu_crowdsec 1m;
    lua_shared_dict nwu_challenge 1m;
    init_by_lua_block {
        assert(require("nwu_crowdsec").init({ conf = "${2:-$W/bouncer.conf}", grace = 60, state = "$W/state.json",
            page = "/usr/local/lib/nginxwebui/html/protection-paused.html",
            challenge = $CHALLENGE }))
    }
    init_worker_by_lua_block { require("nwu_crowdsec").init_worker() }
    server {
        listen 127.0.0.1:18082;
        access_log off;
        location = /v1/decisions/stream {
            access_log $W/logs/lapi.log;
            default_type application/json;
            alias $W/stream.json;
        }
        location /v1/decisions {
            if (\$arg_ip = "127.0.0.2") { return 200 "[{\"id\":1,\"origin\":\"cscli\",\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"127.0.0.2\",\"duration\":\"1h\",\"scenario\":\"smoke\"}]"; }
            if (\$arg_ip = "127.0.0.3") { return 200 "[{\"id\":2,\"origin\":\"cscli\",\"type\":\"captcha\",\"scope\":\"Ip\",\"value\":\"127.0.0.3\",\"duration\":\"1h\",\"scenario\":\"smoke\"}]"; }
            if (\$arg_ip = "127.0.0.6") { return 200 "[{\"id\":3,\"origin\":\"cscli\",\"type\":\"captcha\",\"scope\":\"Ip\",\"value\":\"127.0.0.6\",\"duration\":\"1h\",\"scenario\":\"smoke\"}]"; }
            if (\$arg_ip = "127.0.0.4") { return 200 "[{\"id\":4,\"origin\":\"cscli\",\"type\":\"whitelist\",\"scope\":\"Ip\",\"value\":\"127.0.0.4\",\"duration\":\"1h\",\"scenario\":\"smoke\"}]"; }
            return 200 "null";
        }
    }
    server {
        listen 127.0.0.1:18443 ssl;
        listen 127.0.0.1:18080;
        server_name shop.test;
        ssl_certificate $W/cert.pem;
        ssl_certificate_key $W/key.pem;
        access_log off;
        location /sha {
            set \$nwu_allowlist 1;
            content_by_lua_block {
                local h = require("resty.sha256"):new(); h:update("abc")
                ngx.print(require("resty.string").to_hex(h:final()))
            }
        }
        location / {
            set \$nwu_allowlist 0;
            access_by_lua_block { require("nwu_crowdsec").access() }
            content_by_lua_block { ngx.log(ngx.NOTICE, "smoke backend got ", ngx.var.uri); ngx.print("ok") }
        }
    }
    server {
        listen 127.0.0.1:18444 ssl;
        ssl_certificate $W/cert.pem;
        ssl_certificate_key $W/key.pem;
        access_log off;
        location / {
            set \$nwu_allowlist 0;
            access_by_lua_block { require("nwu_crowdsec").access() }
            content_by_lua_block { ngx.print("ok") }
        }
    }
}
CONF
}

PASS_N=0
FAIL_N=0
pass() { PASS_N=$((PASS_N + 1)); echo "PASS $1"; }
fail() { FAIL_N=$((FAIL_N + 1)); echo "FAIL $1"; }
expect() { # expect <name> <actual> <wanted>
  if [ "$2" = "$3" ]; then pass "$1: $2"; else fail "$1: got [$2] want [$3]"; fi
}
S=https://shop.test:18443
H=http://shop.test:18080
C="curl -sk --resolve shop.test:18443:127.0.0.1 --resolve shop.test:18080:127.0.0.1 -m 10"
# code <ip> <curl args...> : prints the status code and the redirect target
code() { ip=$1; shift; $C --interface $ip -o /dev/null -w "%{http_code} %{redirect_url}" "$@"; }

start() { # start <challenge conf> [bouncer conf]
  write_conf "$@"
  nginx -t -c $W/nginx.conf -p $W/ >/dev/null 2>&1 || { nginx -t -c $W/nginx.conf -p $W/; exit 1; }
  nginx -c $W/nginx.conf -p $W/
  sleep 1
}
stop() {
  nginx -c $W/nginx.conf -p $W/ -s stop || true
  i=0; while [ -f $W/nginx.pid ] && [ "$i" -lt 20 ]; do sleep 0.5; i=$((i + 1)); done
}

NOW=$(date +%s)
sign() { $LUA /t/ticket_sign.lua "$@"; }
# no_loop <label> <ip> : return targets inside the reserved path are never used (no redirect loop), always "/"
no_loop() {
  expect "$1: page r=reserved" "$(code $2 "$S/.well-known/nwu-challenge/?r=%2F.well-known%2Fnwu-challenge%2F")" "303 $S/"
  expect "$1: page r=reserved?r=" "$(code $2 "$S/.well-known/nwu-challenge/?r=%2F.well-known%2Fnwu-challenge%2F%3Fr%3D%252Fa")" "303 $S/"
  expect "$1: verify r=reserved" "$(code $2 -d altcha=Zm9v --data-urlencode "r=/.well-known/nwu-challenge/?r=/a" $S/.well-known/nwu-challenge/verify)" "303 $S/"
  expect "$1: callback path=reserved" "$(code $2 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $(($(date +%s) + 60)) $2 shop.test "/.well-known/nwu-challenge/?r=/a" "nl$1")")" "303 $S/"
}

# ---------- builtin mode ----------
start $W/builtin.conf
expect "sha256 in nginx" "$($C $S/sha)" "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
expect "captcha page request" "$(code 127.0.0.3 -H "Accept: text/html" "$S/cart?id=1")" \
  "302 $S/.well-known/nwu-challenge/?r=%2Fcart%3Fid%3D1"
$C --interface 127.0.0.3 -D $W/r302.h -o /dev/null -H "Accept: text/html" $S/cart
grep -qi "^cache-control: no-store" $W/r302.h && pass "redirect to the challenge: no-store" \
  || fail "redirect to the challenge: no-store: $(cat $W/r302.h)"
expect "captcha POST" "$(code 127.0.0.3 -X POST -H "Accept: text/html" -d a=1 $S/cart)" "429 "
expect "captcha json request" "$(code 127.0.0.3 -H "Accept: application/json" $S/api)" "429 "
expect "challenge page" "$($C --interface 127.0.0.3 -o $W/page.html -w "%{http_code}" "$S/.well-known/nwu-challenge/?r=%2Fcart%3Fid%3D1")" "200"
grep -q "value=\"/cart?id=1\"" $W/page.html && pass "return path in page" || fail "return path in page: $(cat $W/page.html)"
grep -q "127.0.0.3" $W/page.html && pass "client IP in page" || fail "client IP in page"
PAYLOAD="$($LUA /t/altcha_solve.lua $W/page.html)" || fail "solve"
$C --interface 127.0.0.3 -D $W/verify.h -o /dev/null --data-urlencode "altcha=$PAYLOAD" --data-urlencode "r=/cart?id=1" \
  $S/.well-known/nwu-challenge/verify
expect "verify status" "$(head -1 $W/verify.h | cut -d" " -f2)" "303"
grep -qi "^location: /cart?id=1" $W/verify.h && pass "verify returns to the page" || fail "verify location: $(cat $W/verify.h)"
COOKIE="$(sed -n "s/^[Ss]et-[Cc]ookie: nwu_pass=\([^;]*\);.*/\1/p" $W/verify.h | tr -d "\r")"
grep -i "^set-cookie: nwu_pass=" $W/verify.h | grep -q "HttpOnly; SameSite=Lax; Secure" && pass "cookie flags" \
  || fail "cookie flags: $(grep -i set-cookie $W/verify.h)"
expect "with cookie" "$(code 127.0.0.3 -H "Accept: text/html" -b nwu_pass=$COOKIE "$S/cart?id=1")" "200 "
expect "replayed answer" "$(code 127.0.0.3 --data-urlencode "altcha=$PAYLOAD" $S/.well-known/nwu-challenge/verify)" "429 "
expect "garbage answer" "$(code 127.0.0.3 -d altcha=Zm9v $S/.well-known/nwu-challenge/verify)" "429 "
head -c 65536 /dev/zero | tr "\0" a > $W/big.txt
expect "answer bigger than the body buffer" "$(code 127.0.0.3 --data-binary @$W/big.txt $S/.well-known/nwu-challenge/verify)" "429 "
BAD="$(echo "$COOKIE" | sed "s/.$/x/")"; [ "$BAD" = "$COOKIE" ] && BAD="$(echo "$COOKIE" | sed "s/.$/y/")"
expect "tampered cookie" "$(code 127.0.0.3 -H "Accept: text/html" -b nwu_pass=$BAD $S/ | cut -d" " -f1)" "302"
expect "cookie of another IP" "$(code 127.0.0.6 -H "Accept: text/html" -b nwu_pass=$COOKIE $S/ | cut -d" " -f1)" "302"
http_body="$($C --interface 127.0.0.3 -H "Accept: text/html" -w " %{http_code}" $H/)"
case "$http_body" in *"127.0.0.3"*" 429") pass "plain http: 429 page with IP" ;; *) fail "plain http: $http_body" ;; esac
expect "plain http reserved path" "$(code 127.0.0.3 "$H/.well-known/nwu-challenge/")" "429 "
rc=0; $C --interface 127.0.0.2 -o /dev/null "$S/.well-known/nwu-challenge/" || rc=$?
case "$rc" in 52|56) pass "banned IP on reserved path: connection closed (curl exit $rc)" ;; *) fail "banned IP on reserved path: curl exit $rc" ;; esac
expect "whitelist decision" "$(code 127.0.0.4 -H "Accept: text/html" $S/)" "200 "
expect "clean IP" "$(code 127.0.0.5 -H "Accept: text/html" $S/)" "200 "
expect "altcha.js" "$(code 127.0.0.5 $S/.well-known/nwu-challenge/altcha.js)" "200 "
# no decision on the reserved path: back to a same-site path without a challenge, a check or a cookie
expect "no decision: page" "$(code 127.0.0.5 "$S/.well-known/nwu-challenge/?r=%2Fcart%3Fid%3D1")" "303 $S/cart?id=1"
expect "no decision: page, foreign return" "$(code 127.0.0.5 "$S/.well-known/nwu-challenge/?r=%2F%2Fevil.example%2F")" "303 $S/"
expect "no decision: verify" "$(code 127.0.0.5 -d altcha=Zm9v --data-urlencode "r=/cart?id=1" $S/.well-known/nwu-challenge/verify)" \
  "303 $S/cart?id=1"
expect "no decision: big verify" "$(code 127.0.0.5 --data-binary @$W/big.txt $S/.well-known/nwu-challenge/verify)" "303 $S/"
expect "no decision: callback" "$(code 127.0.0.5 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $((NOW + 60)) 127.0.0.5 shop.test "/cart?id=1" nb0)")" \
  "303 $S/cart?id=1"
expect "no decision: bad callback" "$(code 127.0.0.5 "$S/.well-known/nwu-challenge/callback?token=x")" "303 $S/"
expect "no decision: unknown reserved path" "$(code 127.0.0.5 $S/.well-known/nwu-challenge/x)" "303 $S/"
expect "no decision: plain http page" "$(code 127.0.0.5 "$H/.well-known/nwu-challenge/?r=%2Fa")" "303 $H/a"
no_loop "no decision" 127.0.0.5
$C --interface 127.0.0.5 -D $W/bounce.h -o /dev/null "$S/.well-known/nwu-challenge/?r=%2Fa"
head -1 $W/bounce.h | grep -q " 303 " && grep -qi "^cache-control: no-store" $W/bounce.h && ! grep -qi "^set-cookie:" $W/bounce.h \
  && pass "no decision: no-store, no cookie" || fail "no decision headers: $(cat $W/bounce.h)"
expect "callback is external-only" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $((NOW + 60)) 127.0.0.3 shop.test / nb1)")" "404 "
stop

# ---------- external mode ----------
start $W/external.conf
r="$(code 127.0.0.3 -H "Accept: text/html" "$S/cart?id=1")"
case "$r" in "302 https://verify.example/check?t="*) pass "external redirect" ;; *) fail "external redirect: $r" ;; esac
TOKEN="${r#*?t=}"
$C --interface 127.0.0.3 -D $W/r302.h -o /dev/null -H "Accept: text/html" $S/cart
grep -qi "^cache-control: no-store" $W/r302.h && pass "redirect to the external site: no-store" \
  || fail "redirect to the external site: no-store: $(cat $W/r302.h)"
expect "empty host" "$(code 127.0.0.3 --http1.0 -H "Host:" -H "Accept: text/html" https://127.0.0.1:18444/cart)" "429 "
expect "request ticket" "$($LUA /t/ticket_verify.lua $TICKET_SECRET "$TOKEN")" "127.0.0.3 shop.test /cart?id=1"
NOW=$(date +%s)
T1="$(sign $TICKET_SECRET resp $((NOW + 60)) 127.0.0.3 shop.test "/cart?id=1" n1)"
$C --interface 127.0.0.3 -D $W/cb.h -o /dev/null "$S/.well-known/nwu-challenge/callback?token=$T1"
expect "callback status" "$(head -1 $W/cb.h | cut -d" " -f2)" "303"
grep -qi "^location: /cart?id=1" $W/cb.h && pass "callback returns to the page" || fail "callback location: $(cat $W/cb.h)"
grep -qi "^set-cookie: nwu_pass=" $W/cb.h && pass "callback cookie" || fail "callback cookie"
expect "replayed ticket" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$T1")" "429 "
T2="$(sign $TICKET_SECRET resp $((NOW + 60)) 127.0.0.3 shop.test / n2)"
TT="${T2%?}"; case "$T2" in *A) TT="${TT}B" ;; *) TT="${TT}A" ;; esac
expect "tampered ticket" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$TT")" "429 "
expect "wrong secret" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$(sign other resp $((NOW + 60)) 127.0.0.3 shop.test / n3)")" "429 "
expect "expired ticket" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $((NOW - 1)) 127.0.0.3 shop.test / n4)")" "429 "
expect "other IP" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $((NOW + 60)) 127.0.0.9 shop.test / n5)")" "429 "
expect "other host" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $((NOW + 60)) 127.0.0.3 evil.test / n6)")" "429 "
expect "request ticket is not a response" "$(code 127.0.0.3 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET req $((NOW + 60)) 127.0.0.3 shop.test / n7)")" "429 "
expect "verify in external mode" "$(code 127.0.0.3 -d altcha=x $S/.well-known/nwu-challenge/verify)" "404 "
stop

# ---------- bouncer loaded without the challenge ----------
# Two states: the old bouncer conf before apply (init gets no challenge at all) and fallback mode (init gets a challenge
# whose init fails, here external mode without TICKET_SECRET). The reserved path never reaches the site (which answers
# 200 everywhere here): 303 back to a same-site path outside the reserved path, altcha.js 404; ban and allow unchanged.
backend_hits() { grep -c "smoke backend got $1" $W/logs/error.log; }
cat > $W/fallback.conf <<CONF
MODE=external
TTL_SECONDS=3600
COOKIE_SECRET=$COOKIE_SECRET
TICKET_SECRET=
EXTERNAL_URL=https://verify.example/check
CONF
unloaded_checks() { # unloaded_checks <label>
  n="$1"
  B0=$(backend_hits /.well-known/nwu-challenge/); X0=$(backend_hits /x1); Y0=$(backend_hits /x2)
  expect "$n: page" "$(code 127.0.0.5 "$S/.well-known/nwu-challenge/?r=%2Fcart%3Fid%3D1")" "303 $S/cart?id=1"
  expect "$n: page, captcha IP" "$(code 127.0.0.3 -H "Accept: text/html" "$S/.well-known/nwu-challenge/?r=%2Fa")" "303 $S/a"
  expect "$n: verify" "$(code 127.0.0.5 -d altcha=Zm9v --data-urlencode "r=/cart?id=1" $S/.well-known/nwu-challenge/verify)" \
    "303 $S/cart?id=1"
  expect "$n: callback" "$(code 127.0.0.5 "$S/.well-known/nwu-challenge/callback?token=$(sign $TICKET_SECRET resp $(($(date +%s) + 60)) 127.0.0.5 shop.test "/cart?id=1" "nc$n")")" \
    "303 $S/cart?id=1"
  expect "$n: unknown reserved path" "$(code 127.0.0.5 $S/.well-known/nwu-challenge/x)" "303 $S/"
  expect "$n: plain http page" "$(code 127.0.0.5 "$H/.well-known/nwu-challenge/?r=%2Fa")" "303 $H/a"
  expect "$n: altcha.js" "$(code 127.0.0.5 $S/.well-known/nwu-challenge/altcha.js)" "404 "
  no_loop "$n" 127.0.0.5
  $C --interface 127.0.0.5 -D $W/nc.h -o /dev/null "$S/.well-known/nwu-challenge/?r=%2Fa"
  head -1 $W/nc.h | grep -q " 303 " && grep -qi "^cache-control: no-store" $W/nc.h && ! grep -qi "^set-cookie:" $W/nc.h \
    && pass "$n: no-store, no cookie" || fail "$n headers: $(cat $W/nc.h)"
  rc=0; $C --interface 127.0.0.2 -o /dev/null "$S/.well-known/nwu-challenge/" || rc=$?
  case "$rc" in 52|56) pass "$n: banned IP on reserved path closed (curl exit $rc)" ;; *) fail "$n: banned IP curl exit $rc" ;; esac
  expect "$n: whitelist decision passes the site" "$(code 127.0.0.4 $S/x1)" "200 "
  expect "$n: site path" "$(code 127.0.0.5 $S/x2)" "200 "
  expect "$n: site got the site paths" "$(($(backend_hits /x1) - X0))$(($(backend_hits /x2) - Y0))" "11"
  expect "$n: site got no reserved path" "$(($(backend_hits /.well-known/nwu-challenge/) - B0))" "0"
}
start -
unloaded_checks "old conf"
stop
start $W/fallback.conf
grep -q "external mode needs TICKET_SECRET and EXTERNAL_URL" $W/logs/error.log && pass "fallback: challenge init failed" \
  || fail "fallback: challenge init did not fail"
unloaded_checks "fallback"
stop

# ---------- stream mode: overlapping captcha and range ban, and captcha expiry ----------
X=127.0.0.3
decision() { # decision <id> <type>
  echo "{\"id\":$1,\"origin\":\"crowdsec\",\"type\":\"$2\",\"scope\":\"Ip\",\"value\":\"$X\",\"duration\":\"1h\",\"scenario\":\"smoke\"}"
}
range_decision() { # range_decision <id> <type> <range>
  echo "{\"id\":$1,\"origin\":\"crowdsec\",\"type\":\"$2\",\"scope\":\"Range\",\"value\":\"$3\",\"duration\":\"1h\",\"scenario\":\"smoke\"}"
}
# phase <json> : serve the stream answer, then wait until the bouncer fetched it at least twice after the switch
phase() {
  echo "$1" > $W/stream.json
  n0=$(cat $W/logs/lapi.log 2>/dev/null | wc -l)
  i=0
  while [ "$(cat $W/logs/lapi.log 2>/dev/null | wc -l)" -lt $((n0 + 3)) ] && [ "$i" -lt 40 ]; do sleep 0.5; i=$((i + 1)); done
  sleep 0.5
}
st() { code $X -H "Accept: text/html" $S/ | cut -d" " -f1; }
st_cookie() { code $X -H "Accept: text/html" -b "nwu_pass=$COOKIE" $S/ | cut -d" " -f1; }
echo "{\"new\":[],\"deleted\":[]}" > $W/stream.json
start $W/builtin.conf $W/stream.conf
phase "{\"new\":[$(decision 11 captcha)],\"deleted\":[]}"
expect "stream: captcha" "$(st)" "302"
phase "{\"new\":[$(range_decision 12 ban 127.0.0.0/24)],\"deleted\":[]}"
expect "stream: range ban overrides captcha despite pass cookie" "$(st_cookie)" "000"
phase "{\"new\":[],\"deleted\":[$(decision 11 captcha)]}"
expect "stream: expired IP captcha leaves range ban" "$(st_cookie)" "000"
phase "{\"new\":[],\"deleted\":[$(range_decision 12 ban 127.0.0.0/24)]}"
expect "stream: deleted range ban" "$(st_cookie)" "200"
stop

grep -v "no file" $W/logs/error.log | grep -E "\[(error|emerg|alert|crit)\]" | grep -v "error loading captcha plugin" \
  | grep -v "external mode needs TICKET_SECRET and EXTERNAL_URL" \
  && fail "errors in nginx error.log" || pass "no unexpected errors in error.log"
echo "challenge smoke: $PASS_N PASS, $FAIL_N FAIL"
[ "$FAIL_N" = "0" ]
'

docker run --rm -v "$TOOLS:/t:ro" --entrypoint sh "$IMAGE" -c "$INNER"
