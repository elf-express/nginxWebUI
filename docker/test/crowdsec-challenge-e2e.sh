#!/bin/sh
# End-to-end check of the self-hosted challenge (E-387) on a real compose stack with the security profile.
# The whole stack runs on an internal network (no internet), creates an https site (self-signed) and a plain http
# site through the admin API, applies the config and checks from client containers with fixed IPs:
#   1  captcha decision: page request -> 302 to the reserved path; the page is solved like a browser (auto style)
#      -> 303 back with the nwu_pass cookie -> 200; the cookie keeps working
#   2  checkbox style (saved through the admin API, applied): page carries data-style="checkbox", solved -> 200
#   3  the cookie of another captcha IP and a tampered cookie -> challenged again (302)
#   4  POST and JSON requests with a captcha decision -> 429, no redirect
#   5  11 challenge page views -> nginxwebui/challenge-fail -> ban -> 444
#   6  6 wrong answers -> nginxwebui/challenge-fail-answer -> ban -> 444
#   7  an IP that already had a decision and is flagged again (bad user agent) -> ban, not captcha
#   8  a first bad user agent -> captcha only
#   9  allowlisted IP with a captcha decision -> 200 without challenge; banned IP on the reserved path -> 444
#   10 plain http site: 429 page with the client IP and no challenge; 15 reloads -> still only the captcha decision
#   11 offline: no internet in the stack, the challenge page references no outside URL, altcha.js comes from nginx
#   12 files: challenge.conf is 600, keys are not in nginx.conf / crowdsec_http.conf, BOUNCING_ON_TYPE=all,
#      the generated cookie key shows source GENERATED in the admin API
#   13 external mode with a fake external site: request ticket valid; signed response ticket -> cookie -> 200;
#      replayed / tampered / expired / other-IP tickets -> 429
#   B  with --browser: the network is not internal and https is published on 127.0.0.1:18443; real Chromium from
#      the host (docker/test/challenge-browser.cjs) passes the auto and the checkbox style, loads nothing from outside
#      and is not challenged again with the cookie. Check 11 then only checks the page and altcha.js.
# Also runs nginx -t against the generated config after every apply.
#
# Nothing of an existing deployment is touched: project nwu-e387-e2e, own container names, volumes and network,
# no published ports (except 127.0.0.1:18443 with --browser). Everything is removed on exit. Takes about 3 minutes.
#
# Usage: sh docker/test/crowdsec-challenge-e2e.sh <nginxwebui image> <nginxwebui-crowdsec image> [--browser]
set -u
export MSYS_NO_PATHCONV=1
unset COMPOSE_PROFILES COMPOSE_FILE COMPOSE_PROJECT_NAME

NWU_IMAGE="${1:?usage: crowdsec-challenge-e2e.sh <nginxwebui image> <crowdsec image>}"
CS_IMAGE="${2:?usage: crowdsec-challenge-e2e.sh <nginxwebui image> <crowdsec image>}"
BROWSER=0
[ "${3:-}" = "--browser" ] && BROWSER=1
if [ "$BROWSER" = 1 ]; then
  PORTS='["127.0.0.1:18443:443"]'
  INTERNAL=false
else
  PORTS='[]'
  INTERNAL=true
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$HERE/.." && pwd)"
TOOLS_DIR="$(cd "$HERE/../lua" && pwd)"
PROJECT=nwu-e387-e2e
NET="${PROJECT}_default"
NWU="${PROJECT}-nginxwebui"
CS="${PROJECT}-crowdsec"
# TEST-NET-1, not RFC 1918: CrowdSec's default whitelist drops events from private IPs.
SUBNET=192.0.2
IP_ALLOW=$SUBNET.50
IP_CAP=$SUBNET.60      # 1, 3, 4
IP_BOX=$SUBNET.61      # 2
IP_PAGES=$SUBNET.62    # 5
IP_ANSWERS=$SUBNET.63  # 6
IP_REPEAT=$SUBNET.64   # 7
IP_FIRST=$SUBNET.65    # 8
IP_OTHER=$SUBNET.66    # 3
IP_BAN=$SUBNET.67      # 9
IP_HTTP=$SUBNET.68     # 10
IP_EXT=$SUBNET.69      # 13
ADMIN=admin
CAPTCHA=1234
HOME_DIR=/home/nginxWebUI
NGINX_CONF=$HOME_DIR/nginx.conf
P=/.well-known/nwu-challenge/
NIKTO="Mozilla/5.00 (Nikto/2.1.6) (Evasions:None) (Test:000001)"

WORK="$(mktemp -d)"
native() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}
BASE="$(native "$DOCKER_DIR/docker-compose.yml")"
OVR="$(native "$WORK/override.yml")"
ENVF="$(native "$WORK/e2e.env")"
TOOLS="$(native "$TOOLS_DIR")"

PASS_N=0
FAIL_N=0
pass() { PASS_N=$((PASS_N + 1)); echo "PASS [$1] $2"; }
fail() { FAIL_N=$((FAIL_N + 1)); echo "FAIL [$1] $2"; }
info() { echo "  .. $*"; }
now() { date +%s; }

dc() { docker compose -p "$PROJECT" --env-file "$ENVF" -f "$BASE" -f "$OVR" "$@"; }

CLIENTS="allow cap box pages answers repeat first other ban http ext"
cleanup() {
  for c in $CLIENTS; do
    docker rm -f "$PROJECT-client-$c" >/dev/null 2>&1 || true
  done
  dc --profile security down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

random_key() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    od -An -tx1 -N32 /dev/urandom | tr -d ' \n'
  fi
}

ADMIN_PASS="Aa1$(random_key | cut -c1-24)"
PG_PASS="$(random_key)"
KEY="$(random_key)"
TICKET="$(random_key)"

cat > "$WORK/e2e.env" <<EOF
NGINX_WEBUI_VERSION=e2e
E2E_NWU_IMAGE=$NWU_IMAGE
E2E_CS_IMAGE=$CS_IMAGE
CROWDSEC_BOUNCER_KEY=$KEY
NWU_CHALLENGE_TICKET_SECRET=$TICKET
ABUSEIPDB_API_KEY=
MCP_TOKEN=
EOF

# Test-only override: local images, own names, volumes and subnet, an internal network (no internet), no published
# ports, a preset admin and a fixed captcha for the API login, no outbound feeds.
cat > "$WORK/override.yml" <<EOF
services:
  nginxwebui:
    image: \${E2E_NWU_IMAGE}
    container_name: $NWU
    ports: !override $PORTS
    environment:
      BOOT_OPTIONS: >-
        --spring.database.type=postgresql
        --spring.datasource.url=jdbc:postgresql://postgres:5432/nginxwebui
        --spring.datasource.username=nginxwebui
        --spring.datasource.password=$PG_PASS
        --init.admin=$ADMIN
        --init.pass=$ADMIN_PASS
        --project.testCaptcha=$CAPTCHA
        --project.skipSeedFetch=true
  postgres:
    container_name: $PROJECT-postgres
    environment:
      - POSTGRES_PASSWORD=$PG_PASS
  crowdsec:
    image: \${E2E_CS_IMAGE}
    container_name: $CS
    environment:
      - DISABLE_ONLINE_API=true
networks:
  default:
    internal: $INTERNAL
    ipam:
      config:
        - subnet: $SUBNET.0/24
volumes:
  nginxwebui_data:
    name: ${PROJECT}_data
  nginxwebui_log:
    name: ${PROJECT}_log
  postgres_data:
    name: ${PROJECT}_postgres_data
  crowdsec_data:
    name: ${PROJECT}_crowdsec_data
  nginxwebui_geoip:
    name: ${PROJECT}_geoip
EOF

# ---------- helpers ----------

api() {
  p="$1"
  shift
  docker exec "$NWU" curl -s -m 60 -b /tmp/e2e.cookie -c /tmp/e2e.cookie "$@" "http://127.0.0.1:8080$p"
}

b64twice() { printf '%s' "$1" | base64 | tr -d '\n' | base64 | tr -d '\n'; }

wait_nwu() {
  i=0
  while [ "$i" -lt 90 ]; do
    code="$(docker exec "$NWU" curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/adminPage/login 2>/dev/null)"
    [ "$code" = "200" ] && return 0
    i=$((i + 1))
    sleep 2
  done
  docker logs "$NWU" 2>&1 | tail -40
  return 1
}

login() {
  api /adminPage/login/getCode -o /dev/null
  r="$(api /adminPage/login/login --data-urlencode "name=$(b64twice "$ADMIN")" \
    --data-urlencode "pass=$(b64twice "$ADMIN_PASS")" --data-urlencode "code=$(b64twice "$CAPTCHA")")"
  case "$r" in *'"success":true'*) return 0 ;; esac
  echo "login failed: $r"
  return 1
}

nginx_t() {
  docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/logs && nginx -t -c $NGINX_CONF -p $HOME_DIR/" 2>&1 | tail -1
}

# apply: build the config from the database, write it (replace), reload nginx and check it with nginx -t
apply() {
  r="$(api /adminPage/conf/replace -X POST -d '')"
  case "$r" in *'"success":true'*) ;; *) echo "replace failed: $r"; return 1 ;; esac
  r="$(api /adminPage/conf/reload -X POST -d '')"
  case "$r" in *'[emerg]'* | *'[error]'*) echo "reload reported errors: $r"; return 1 ;; esac
  t="$(nginx_t)"
  case "$t" in *successful*) ;; *) echo "nginx -t: $t"; return 1 ;; esac
  sleep 3
  return 0
}

save_challenge() { # save_challenge <mode> <style> <externalUrl>
  r="$(api /adminPage/crowdsec/saveChallenge --data-urlencode "mode=$1" --data-urlencode "style=$2" \
    --data-urlencode "externalUrl=$3" -d ttlMinutes=60)"
  case "$r" in *'"success":true'*) return 0 ;; esac
  echo "saveChallenge failed: $r"
  return 1
}

start_client() { # start_client <name> <ip>
  docker rm -f "$PROJECT-client-$1" >/dev/null 2>&1 || true
  docker run -d --name "$PROJECT-client-$1" --network "$NET" --ip "$2" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
  docker cp "$TOOLS/." "$PROJECT-client-$1:/t" >/dev/null
}

# c <client> <curl args...> : curl inside a client container (https certificate is self-signed)
c() { n="$1"; shift; docker exec "$PROJECT-client-$n" curl -gsk -m 10 "$@"; }
# code <client> <curl args...> : "<curl exit> <status> <redirect target>"
code() {
  n="$1"; shift
  out="$(docker exec "$PROJECT-client-$n" curl -gsk -m 10 -o /dev/null -w '%{http_code} %{redirect_url}' "$@")"
  rc=$?
  echo "$rc $out"
}
lua() { n="$1"; shift; docker exec "$PROJECT-client-$n" luajit -e 'package.path="/t/?.lua;/t/test/?.lua;"..package.path; package.cpath="/usr/lib/lua/5.1/?.so;"..package.cpath' "$@"; }

cscli() { docker exec "$CS" cscli "$@"; }
# decisions <ip> : the decision types for one IP, sorted, comma separated
decisions() { cscli decisions list -i "$1" -o raw 2>/dev/null | awk -F, 'NR > 1 { print $5 }' | sort | tr '\n' ',' ; }
# wait_status <client> <want "rc status"> <curl args...> : poll until the bouncer pulled the decision (stream, 10s)
wait_status() {
  n="$1"; want="$2"; shift 2
  i=0
  while [ "$i" -lt 20 ]; do
    got="$(code "$n" "$@" | cut -d' ' -f1-2)"
    [ "$got" = "$want" ] && return 0
    i=$((i + 1))
    sleep 3
  done
  echo "$got"
  return 1
}
wait_decision() { # wait_decision <ip> <type>
  i=0
  while [ "$i" -lt 30 ]; do
    decisions "$1" | grep -q "$2" && return 0
    i=$((i + 1))
    sleep 3
  done
  return 1
}

# solve <client> : opens the challenge page for "/" and posts the solved answer, leaves headers in /tmp/verify.h
solve() {
  n="$1"
  c "$n" -o /tmp/page.html "https://nginxwebui${P}?r=%2F"
  payload="$(lua "$n" /t/test/altcha_solve.lua /tmp/page.html)"
  c "$n" -D /tmp/verify.h -o /dev/null --data-urlencode "altcha=$payload" --data-urlencode "r=/" "https://nginxwebui${P}verify"
  docker exec "$PROJECT-client-$n" sh -c "sed -n 's/^[Ss]et-[Cc]ookie: nwu_pass=\([^;]*\);.*/\1/p' /tmp/verify.h | tr -d '\r'"
}

# ---------- setup ----------

echo "== starting stack (project $PROJECT, $NWU_IMAGE + $CS_IMAGE, security profile, internal network)"
T_START=$(now)
if ! dc --profile security up -d >"$WORK/up.log" 2>&1; then
  cat "$WORK/up.log"
  fail setup "docker compose up failed"
  exit 1
fi
wait_nwu || { fail setup "nginxwebui not ready"; exit 1; }
i=0
while [ "$i" -lt 60 ]; do
  cscli bouncers list -o raw 2>/dev/null | grep -q '^nginx,' && break
  i=$((i + 1))
  sleep 2
done
cscli bouncers list -o raw 2>/dev/null | grep -q '^nginx,' || { fail setup "bouncer nginx not registered"; exit 1; }
login || { fail setup "admin login"; exit 1; }

docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/e2e-www $HOME_DIR/e2e-cert && echo e2e-ok > $HOME_DIR/e2e-www/index.html \
  && chmod o+x $HOME_DIR && chmod -R a+rX $HOME_DIR/e2e-www \
  && openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=nginxwebui \
     -keyout $HOME_DIR/e2e-cert/key.pem -out $HOME_DIR/e2e-cert/cert.pem >/dev/null 2>&1"
LOC_JSON="[{\"enable\":1,\"path\":\"/\",\"type\":1,\"rootType\":\"root\",\"rootPath\":\"$HOME_DIR/e2e-www\",\"rootPage\":\"index.html\",\"header\":0,\"websocket\":0,\"cros\":0,\"returnPath\":0,\"locationParamJson\":\"\"}]"
for site in \
  '{"proxyType":"0","listen":"80","def":"1","ipv6":"0","serverName":"e2e.test","ssl":"0","rewrite":"0","http2":"0","proxyProtocol":"0","protocols":"TLSv1.2 TLSv1.3","enable":true}' \
  "{\"proxyType\":\"0\",\"listen\":\"443\",\"def\":\"1\",\"ipv6\":\"0\",\"serverName\":\"e2e.test\",\"ssl\":\"1\",\"pem\":\"$HOME_DIR/e2e-cert/cert.pem\",\"key\":\"$HOME_DIR/e2e-cert/key.pem\",\"rewrite\":\"0\",\"http2\":\"0\",\"proxyProtocol\":\"0\",\"protocols\":\"TLSv1.2 TLSv1.3\",\"enable\":true}"; do
  r="$(api /adminPage/server/addOver --data-urlencode "serverJson=$site" --data-urlencode "serverParamJson=" \
    --data-urlencode "locationJson=$LOC_JSON")"
  case "$r" in *'"success":true'*) ;; *) fail setup "server addOver: $r"; exit 1 ;; esac
done
r="$(api /adminPage/denyAllow/addOver --data-urlencode "id=" --data-urlencode "name=E2E-allowlist" \
  --data-urlencode "type=allow" --data-urlencode "ip=$IP_ALLOW")"
case "$r" in *'"success":true'*) ;; *) fail setup "allowlist addOver: $r"; exit 1 ;; esac
# The seeded global GeoRule blocks every IP without a country (all test IPs); turn it off.
geo="$(api /adminPage/geo/detail)"
GEO_ID="$(echo "$geo" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
GEO_COUNTRIES="$(echo "$geo" | sed -n 's/.*"countries":"\([^"]*\)".*/\1/p')"
api /adminPage/geo/addOver --data-urlencode "id=$GEO_ID" -d mode=0 --data-urlencode "countries=${GEO_COUNTRIES:-TW}" \
  -d enable=false >/dev/null
apply || { fail setup "apply config"; exit 1; }

for pair in "allow $IP_ALLOW" "cap $IP_CAP" "box $IP_BOX" "pages $IP_PAGES" "answers $IP_ANSWERS" "repeat $IP_REPEAT" \
  "first $IP_FIRST" "other $IP_OTHER" "ban $IP_BAN" "http $IP_HTTP" "ext $IP_EXT"; do
  start_client $pair
done
for ip in $IP_ALLOW $IP_CAP $IP_BOX $IP_PAGES $IP_ANSWERS $IP_OTHER $IP_HTTP $IP_EXT; do
  cscli decisions add -i "$ip" -t captcha -d 1h -R "e2e captcha" >/dev/null 2>&1
done
cscli decisions add -i "$IP_BAN" -t ban -d 1h -R "e2e ban" >/dev/null 2>&1
cscli decisions add -i "$IP_REPEAT" -t captcha -d 1h -R "e2e earlier decision" >/dev/null 2>&1
info "stack up and configured after $(($(now) - T_START))s"

# ---------- 12. files ----------
conf_mode="$(docker exec "$NWU" stat -c %a $HOME_DIR/crowdsec/challenge.conf 2>&1)"
cookie_key="$(docker exec "$NWU" sed -n 's/^COOKIE_SECRET=//p' $HOME_DIR/crowdsec/challenge.conf)"
type_line="$(docker exec "$NWU" grep '^BOUNCING_ON_TYPE=' $HOME_DIR/crowdsec/crowdsec-nginx-bouncer.conf)"
leak="$(docker exec "$NWU" sh -c "cat $NGINX_CONF $HOME_DIR/conf.d/*.conf 2>/dev/null" | grep -c -e "$cookie_key" -e "$TICKET")"
source="$(api /adminPage/crowdsec/getChallenge | sed -n 's/.*"cookieSecret":{[^}]*"source":"\([A-Z_]*\)".*/\1/p')"
if [ "$conf_mode" = "600" ] && [ -n "$cookie_key" ] && [ "$type_line" = "BOUNCING_ON_TYPE=all" ] && [ "$leak" = "0" ] \
  && [ "$source" = "GENERATED" ]; then
  pass 12 "challenge.conf 600, keys not in nginx config, $type_line, cookie key source GENERATED"
else
  fail 12 "mode=$conf_mode key=${cookie_key:+set} $type_line leak=$leak source=$source"
fi

# ---------- 1. builtin, auto style ----------
r="$(wait_status cap "0 302" -H 'Accept: text/html' https://nginxwebui/)" || true
got="$(code cap -H 'Accept: text/html' https://nginxwebui/)"
style="$(c cap "https://nginxwebui${P}?r=%2F" | grep -o 'data-style="[a-z]*"')"
COOKIE="$(solve cap)"
vstatus="$(docker exec "$PROJECT-client-cap" head -1 /tmp/verify.h | cut -d' ' -f2)"
vloc="$(docker exec "$PROJECT-client-cap" sh -c "grep -i '^location:' /tmp/verify.h | tr -d '\r'")"
after="$(c cap -b "nwu_pass=$COOKIE" -H 'Accept: text/html' https://nginxwebui/)"
again="$(code cap -b "nwu_pass=$COOKIE" -H 'Accept: text/html' https://nginxwebui/other.html | cut -d' ' -f1-2)"
if [ "$got" = "0 302 https://nginxwebui${P}?r=%2F" ] && [ "$style" = 'data-style="auto"' ] && [ "$vstatus" = "303" ] \
  && echo "$vloc" | grep -qi 'location: /$' && [ "$after" = "e2e-ok" ] && [ "$again" = "0 404" ]; then
  pass 1 "captcha -> 302 to the challenge, auto style solved -> 303 / with cookie -> 200 e2e-ok; cookie still valid"
else
  fail 1 "redirect=[$got] $style verify=$vstatus [$vloc] after=[$after] again=[$again]"
fi

# ---------- 3. cookie bound to the IP, tampered cookie ----------
other="$(code other -b "nwu_pass=$COOKIE" -H 'Accept: text/html' https://nginxwebui/ | cut -d' ' -f1-2)"
BAD="$(echo "$COOKIE" | sed 's/.$/A/')"
[ "$BAD" = "$COOKIE" ] && BAD="$(echo "$COOKIE" | sed 's/.$/B/')"
tampered="$(code cap -b "nwu_pass=$BAD" -H 'Accept: text/html' https://nginxwebui/ | cut -d' ' -f1-2)"
if [ "$other" = "0 302" ] && [ "$tampered" = "0 302" ]; then
  pass 3 "cookie used from $IP_OTHER -> 302; tampered cookie -> 302"
else
  fail 3 "other IP=$other tampered=$tampered (want 302 / 302)"
fi

# ---------- 4. non-page requests ----------
post="$(code cap -X POST -H 'Accept: text/html' -d a=1 https://nginxwebui/)"
json="$(code cap -H 'Accept: application/json' https://nginxwebui/api)"
if [ "$post" = "0 429 " ] && [ "$json" = "0 429 " ]; then
  pass 4 "POST and JSON requests with a captcha decision -> 429 without redirect"
else
  fail 4 "POST=[$post] JSON=[$json]"
fi

# ---------- 9. allowlist and ban ----------
allow="$(wait_status allow "0 200" -H 'Accept: text/html' https://nginxwebui/ && echo "0 200")"
ban="$(code ban "http://nginxwebui${P}" | cut -d' ' -f1)"
if [ "$allow" = "0 200" ] && { [ "$ban" = "52" ] || [ "$ban" = "56" ]; }; then
  pass 9 "allowlisted IP with a captcha decision -> 200; banned IP on the reserved path -> connection closed (curl $ban)"
else
  fail 9 "allowlisted=[$allow] banned curl exit=$ban"
fi

# ---------- 10. plain http ----------
body="$(c http -H 'Accept: text/html' -w ' %{http_code}' http://nginxwebui/)"
i=0
while [ "$i" -lt 15 ]; do c http -o /dev/null -H 'Accept: text/html' http://nginxwebui/; i=$((i + 1)); done
sleep 20
last="$(code http -H 'Accept: text/html' http://nginxwebui/ | cut -d' ' -f1-2)"
dec="$(decisions $IP_HTTP)"
case "$body" in
*"$IP_HTTP"*" 429")
  if [ "$last" = "0 429" ] && [ "$dec" = "captcha," ]; then
    pass 10 "plain http: 429 page with the client IP, no challenge; after 15 reloads still 429 and only a captcha decision"
  else
    fail 10 "after reloads: $last, decisions: $dec"
  fi
  ;;
*) fail 10 "plain http first response: $(echo "$body" | tail -c 120)" ;;
esac

# ---------- 11. offline ----------
inet="$(docker exec "$NWU" curl -s -m 5 -o /dev/null -w '%{http_code}' https://registry.npmjs.org/ 2>&1)"
page="$(c cap "https://nginxwebui${P}?r=%2F")"
js="$(code cap "https://nginxwebui${P}altcha.js" | cut -d' ' -f1-2)"
[ "$BROWSER" = 1 ] && inet=000   # --browser: the stack has internet; challenge-browser.cjs checks outside requests
if [ "$inet" = "000" ] && ! echo "$page" | grep -Eq 'https?://' && [ "$js" = "0 200" ]; then
  pass 11 "no internet in the stack; challenge page has no outside URL; altcha.js served by nginx"
else
  fail 11 "internet=$inet outside URL in page: $(echo "$page" | grep -Eo 'https?://[^"]*' | head -1) altcha.js=$js"
fi

# ---------- B. real browser, auto style ----------
# browser <style> : captcha decision for the address the host's requests arrive from, then Chromium through the page
browser() {
  curl -sk -m 10 -o /dev/null https://localhost:18443/ || true
  gw="$(docker exec "$NWU" sh -c "tail -1 $HOME_DIR/log/access.log" | cut -d' ' -f1)"
  cscli decisions add -i "$gw" -t captcha -d 1h -R "e2e browser" >/dev/null 2>&1
  i=0
  while [ "$i" -lt 20 ]; do
    [ "$(curl -sk -m 10 -o /dev/null -w '%{http_code}' -H 'Accept: text/html' https://localhost:18443/)" = "302" ] && break
    i=$((i + 1))
    sleep 3
  done
  out="$(node "$(native "$HERE/challenge-browser.cjs")" https://localhost:18443 "$1" 2>&1)"
  if [ "$out" = "ok $1" ]; then
    pass B "real Chromium from the host ($gw): $1 style passes, cookie keeps working, nothing loaded from outside"
  else
    fail B "$1 style: $out"
  fi
}
[ "$BROWSER" = 1 ] && browser auto

# ---------- 5. too many challenge page views ----------
i=0
while [ "$i" -lt 11 ]; do c pages -o /dev/null "https://nginxwebui${P}?r=%2F"; i=$((i + 1)); done
if wait_decision $IP_PAGES ban && wait_status pages "52 000" http://nginxwebui/ >/dev/null; then
  pass 5 "11 challenge page views -> $(cscli decisions list -i $IP_PAGES -o raw | grep ban | cut -d, -f4) ban -> 444"
else
  fail 5 "decisions for $IP_PAGES: $(decisions $IP_PAGES)"
fi

# ---------- 6. too many wrong answers ----------
i=0
while [ "$i" -lt 6 ]; do c answers -o /dev/null -d altcha=Zm9v "https://nginxwebui${P}verify"; i=$((i + 1)); done
if wait_decision $IP_ANSWERS ban && wait_status answers "52 000" http://nginxwebui/ >/dev/null; then
  pass 6 "6 wrong answers -> $(cscli decisions list -i $IP_ANSWERS -o raw | grep ban | cut -d, -f4) ban -> 444"
else
  fail 6 "decisions for $IP_ANSWERS: $(decisions $IP_ANSWERS)"
fi

# ---------- 7 / 8. bad user agent: repeat offender -> ban, first time -> captcha ----------
for n in repeat first; do
  i=0
  while [ "$i" -lt 3 ]; do c "$n" -o /dev/null -A "$NIKTO" "http://nginxwebui/p$i"; i=$((i + 1)); done
done
wait_decision $IP_REPEAT ban && r7=ok || r7=no
wait_decision $IP_FIRST captcha && r8=ok || r8=no
d7="$(decisions $IP_REPEAT)"
d8="$(decisions $IP_FIRST)"
[ "$r7" = ok ] && pass 7 "earlier decision + bad user agent -> ban ($d7)" || fail 7 "decisions for $IP_REPEAT: $d7"
if [ "$r8" = ok ] && ! echo "$d8" | grep -q ban; then
  pass 8 "first bad user agent -> captcha only ($d8)"
else
  fail 8 "decisions for $IP_FIRST: $d8"
fi

# ---------- 2. checkbox style ----------
save_challenge builtin checkbox "" && apply || fail 2 "save / apply checkbox style"
wait_status box "0 302" -H 'Accept: text/html' https://nginxwebui/ >/dev/null
page="$(c box "https://nginxwebui${P}?r=%2F")"
BOX_COOKIE="$(solve box)"
after="$(c box -b "nwu_pass=$BOX_COOKIE" -H 'Accept: text/html' https://nginxwebui/)"
if echo "$page" | grep -q 'data-style="checkbox"' && ! echo "$page" | grep -q 'auto="onload"' && [ "$after" = "e2e-ok" ]; then
  pass 2 "checkbox style page, solved -> 200 e2e-ok"
else
  fail 2 "style: $(echo "$page" | grep -o 'data-style="[a-z]*"') after=[$after]"
fi
[ "$BROWSER" = 1 ] && browser checkbox

# ---------- 13. external mode with a fake external site ----------
save_challenge external auto "https://verify.example/check" && apply || fail 13 "save / apply external mode"
wait_status ext "0 302" -H 'Accept: text/html' https://nginxwebui/ >/dev/null
r="$(code ext -H 'Accept: text/html' 'https://nginxwebui/cart?id=1')"
TOKEN="${r#*\?t=}"
fields="$(lua ext /t/test/ticket_verify.lua "$TICKET" "$TOKEN" 2>&1)"
NOW=$(now)
sign() { lua ext /t/test/ticket_sign.lua "$TICKET" "$@"; }
T1="$(sign resp $((NOW + 60)) $IP_EXT nginxwebui '/cart?id=1' e2e1)"
c ext -D /tmp/cb.h -o /dev/null "https://nginxwebui${P}callback?token=$T1"
cb="$(docker exec "$PROJECT-client-ext" sh -c "head -1 /tmp/cb.h | cut -d' ' -f2; grep -i '^location:' /tmp/cb.h | tr -d '\r'")"
EXT_COOKIE="$(docker exec "$PROJECT-client-ext" sh -c "sed -n 's/^[Ss]et-[Cc]ookie: nwu_pass=\([^;]*\);.*/\1/p' /tmp/cb.h | tr -d '\r'")"
after="$(c ext -b "nwu_pass=$EXT_COOKIE" -H 'Accept: text/html' https://nginxwebui/)"
replay="$(code ext "https://nginxwebui${P}callback?token=$T1" | cut -d' ' -f2)"
T2="$(sign resp $((NOW + 60)) $IP_EXT nginxwebui / e2e2)"
case "$T2" in *A) T2BAD="${T2%?}B" ;; *) T2BAD="${T2%?}A" ;; esac
tampered="$(code ext "https://nginxwebui${P}callback?token=$T2BAD" | cut -d' ' -f2)"
expired="$(code ext "https://nginxwebui${P}callback?token=$(sign resp $((NOW - 1)) $IP_EXT nginxwebui / e2e3)" | cut -d' ' -f2)"
otherip="$(code ext "https://nginxwebui${P}callback?token=$(sign resp $((NOW + 60)) $IP_OTHER nginxwebui / e2e4)" | cut -d' ' -f2)"
case "$r" in "0 302 https://verify.example/check?t="*) redirect=ok ;; *) redirect="$r" ;; esac
if [ "$redirect" = ok ] && [ "$fields" = "$IP_EXT nginxwebui /cart?id=1" ] \
  && echo "$cb" | head -1 | grep -q '^303$' && echo "$cb" | grep -qi 'location: /cart?id=1' && [ "$after" = "e2e-ok" ] \
  && [ "$replay$tampered$expired$otherip" = "429429429429" ]; then
  pass 13 "external: request ticket valid, response ticket -> cookie -> 200; replayed/tampered/expired/other-IP -> 429"
else
  fail 13 "redirect=[$redirect] ticket=[$fields] callback=[$(echo "$cb" | tr '\n' ' ')] after=[$after] replay=$replay tampered=$tampered expired=$expired otherIP=$otherip"
fi
save_challenge builtin auto "" && apply || info "restoring builtin mode failed"

echo "crowdsec-challenge-e2e: $PASS_N PASS, $FAIL_N FAIL (total $(($(now) - T_START))s)"
[ "$FAIL_N" -eq 0 ]
