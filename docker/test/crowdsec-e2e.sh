#!/bin/sh
# End-to-end check of the CrowdSec Lua bouncer + AppSec WAF on a real compose stack (E-376, acceptance of E-375).
# Starts docker/docker-compose.yml with the security profile as a throwaway project, creates a test site through
# the nginxWebUI admin API, applies the config and checks from client containers with fixed IPs:
#   1. cscli decisions add -i <ip>     -> requests from that IP are cut off (444, curl exit 52)
#   2. GET /.env                        -> 403 (AppSec virtual patching)
#   3. SQLi / XSS strings               -> visible in cscli alerts list; repeated from the same IP -> decision
#   4. allowlisted IP                   -> 200 with a decision, with attack strings and while its country is blocked
#   5. CrowdSec stopped                 -> 200 within the grace period (120s), then 503 for others, 200 for allowlisted,
#                                          crowdsec_state.json lapiOk=false
#   6. no security profile, empty key   -> without re-applying the config nginx keeps serving (also after the
#                                          grace period); after applying, nginx.conf has no crowdsec_http.conf
#   7. generated nginx.conf             -> no country code list and no allowlist IPs (they live in conf.d include files)
#   8. docker compose ps                -> no traefik bouncer container
# Also runs nginx -t against the real generated config, whose allowlist holds 10.0.0.1, 10.0.0.1/32 and 198.51.100.5/24.
#
# Nothing of an existing deployment is touched: project nwu-e376-e2e, own container names, own volumes, own network,
# no published ports. Everything is removed on exit. Checks 5 and 6 each wait for the default grace period
# (about 10 minutes in total).
#
# Usage: sh docker/test/crowdsec-e2e.sh <nginxwebui image> <nginxwebui-crowdsec image>
#   e.g. sh docker/test/crowdsec-e2e.sh nginxwebui:e376 nginxwebui-crowdsec:e376
set -u
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1
unset COMPOSE_PROFILES COMPOSE_FILE COMPOSE_PROJECT_NAME

NWU_IMAGE="${1:?usage: crowdsec-e2e.sh <nginxwebui image> <crowdsec image>}"
CS_IMAGE="${2:?usage: crowdsec-e2e.sh <nginxwebui image> <crowdsec image>}"

HERE="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$HERE/.." && pwd)"
PROJECT=nwu-e376-e2e
NET="${PROJECT}_default"
NWU="${PROJECT}-nginxwebui"
CS="${PROJECT}-crowdsec"
# A documentation range (TEST-NET-3), not RFC 1918: CrowdSec's default whitelist drops events from private IPs,
# so scenarios would never put a decision on a 10.x / 172.16-31.x / 192.168.x client.
SUBNET=203.0.113
IP_ALLOW=$SUBNET.50   # allowlisted
IP_BAN=$SUBNET.60     # check 1
IP_ENV=$SUBNET.61     # check 2
IP_WAF=$SUBNET.62     # check 3
IP_OTHER=$SUBNET.63   # check 4 control, check 5, check 6
ADMIN=admin
ADMIN_PASS=E2eAdmin1234
CAPTCHA=1234
HOME_DIR=/home/nginxWebUI
NGINX_CONF=$HOME_DIR/nginx.conf
STATE_FILE=$HOME_DIR/crowdsec/state/crowdsec_state.json
GRACE=120

WORK="$(mktemp -d)"
native() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}
BASE="$(native "$DOCKER_DIR/docker-compose.yml")"
OVR="$(native "$WORK/override.yml")"
ENVF="$(native "$WORK/e2e.env")"

PASS_N=0
FAIL_N=0
pass() { PASS_N=$((PASS_N + 1)); echo "PASS [$1] $2"; }
fail() { FAIL_N=$((FAIL_N + 1)); echo "FAIL [$1] $2"; }
info() { echo "  .. $*"; }
now() { date +%s; }

dc() { docker compose -p "$PROJECT" --env-file "$ENVF" -f "$BASE" -f "$OVR" "$@"; }

cleanup() {
  for c in allow ban env waf other; do
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

write_env() {
  cat > "$WORK/e2e.env" <<EOF
NGINX_WEBUI_VERSION=e2e
E2E_NWU_IMAGE=$NWU_IMAGE
E2E_CS_IMAGE=$CS_IMAGE
CROWDSEC_BOUNCER_KEY=$1
ABUSEIPDB_API_KEY=
MCP_TOKEN=
EOF
}

# Test-only override: local images, own names, own volumes and subnet, no published ports, a preset admin and a
# fixed captcha for the API login, no outbound feeds (seed fetch / CrowdSec central API).
cat > "$WORK/override.yml" <<EOF
services:
  nginxwebui:
    image: \${E2E_NWU_IMAGE}
    container_name: $NWU
    ports: !reset []
    environment:
      BOOT_OPTIONS: >-
        --spring.database.type=postgresql
        --spring.datasource.url=jdbc:postgresql://postgres:5432/nginxwebui
        --spring.datasource.username=nginxwebui
        --spring.datasource.password=nginxwebui123
        --init.admin=$ADMIN
        --init.pass=$ADMIN_PASS
        --project.testCaptcha=$CAPTCHA
        --project.skipSeedFetch=true
  postgres:
    container_name: $PROJECT-postgres
  crowdsec:
    image: \${E2E_CS_IMAGE}
    container_name: $CS
    environment:
      - DISABLE_ONLINE_API=true
networks:
  default:
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

# api <path> [curl args...] : admin API call from inside the nginxwebui container (session kept in a cookie jar)
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
  echo "nginxWebUI did not come up within 180s"
  docker logs "$NWU" 2>&1 | tail -40
  return 1
}

login() {
  api /adminPage/login/getCode -o /dev/null
  r="$(api /adminPage/login/login --data-urlencode "name=$(b64twice "$ADMIN")" \
    --data-urlencode "pass=$(b64twice "$ADMIN_PASS")" --data-urlencode "code=$(b64twice "$CAPTCHA")")"
  case "$r" in
  *'"success":true'*) return 0 ;;
  esac
  echo "login failed: $r"
  return 1
}

# apply: build the config from the database, write it (replace) and reload nginx, like the UI does
apply() {
  r="$(api /adminPage/conf/replace -X POST -d '')"
  case "$r" in
  *'"success":true'*) ;;
  *)
    echo "replace failed: $r"
    return 1
    ;;
  esac
  r="$(api /adminPage/conf/reload -X POST -d '')"
  case "$r" in
  *'[emerg]'* | *'[error]'*)
    echo "reload reported errors: $r"
    return 1
    ;;
  esac
  sleep 3
  return 0
}

json_str() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }

GEO_ID=""
GEO_COUNTRIES=""
geo_set() { # geo_set true|false
  r="$(api /adminPage/geo/addOver --data-urlencode "id=$GEO_ID" -d mode=0 \
    --data-urlencode "countries=$GEO_COUNTRIES" -d "enable=$1")"
  case "$r" in
  *'"success":true'*) return 0 ;;
  esac
  echo "geo addOver failed: $r"
  return 1
}

start_client() { # start_client <name> <ip>
  docker rm -f "$PROJECT-client-$1" >/dev/null 2>&1 || true
  docker run -d --name "$PROJECT-client-$1" --network "$NET" --ip "$2" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
}

# req <client> <path> : prints "<curl exit> <http code>"
req() {
  out="$(docker exec "$PROJECT-client-$1" curl -g -s -o /dev/null -m 10 -w '%{http_code}' "http://nginxwebui$2")"
  rc=$?
  echo "$rc $out"
}

cscli() { docker exec "$CS" cscli "$@"; }

# nginx -t against the generated config, with its directory as prefix (never a bare nginx -t). The prefix also
# decides where the default error log goes, so logs/ has to exist under it.
nginx_t() {
  docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/logs && nginx -t -c $NGINX_CONF -p $HOME_DIR/"
}

SQLI1='/?id=1%27%20OR%20%271%27=%271'
SQLI2='/?id=1%20UNION%20SELECT%20username,password%20FROM%20users--'
XSS1='/?q=%3Cscript%3Ealert(document.cookie)%3C/script%3E'
XSS2='/?q=%3Cimg%20src=x%20onerror=alert(1)%3E'

# ---------- setup ----------

KEY="$(random_key)"
write_env "$KEY"
echo "== starting stack (project $PROJECT, $NWU_IMAGE + $CS_IMAGE, security profile)"
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
if ! cscli bouncers list -o raw 2>/dev/null | grep -q '^nginx,'; then
  docker logs "$CS" 2>&1 | tail -30
  fail setup "bouncer nginx not registered in crowdsec"
  exit 1
fi
info "stack up after $(($(now) - T_START))s; bouncer nginx registered"

login || { fail setup "admin login"; exit 1; }

modules="$(api /adminPage/basic/pageData)"
for m in ndk_http_module.so ngx_http_lua_module.so; do
  if echo "$modules" | grep -o "{[^{}]*\"name\":\"$m\"[^{}]*}" | grep -q '"enable":true'; then
    info "module $m enabled"
  else
    info "module $m not enabled"
  fi
done

# web root with a static page: the request must reach the access phase (a plain "return 200" would skip it)
docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/e2e-www && echo e2e-ok > $HOME_DIR/e2e-www/index.html \
  && chmod o+x $HOME_DIR && chmod -R a+rX $HOME_DIR/e2e-www"

SERVER_JSON='{"proxyType":"0","listen":"80","def":"1","ipv6":"0","serverName":"e2e.test","ssl":"0","rewrite":"0","http2":"0","proxyProtocol":"0","protocols":"TLSv1.2 TLSv1.3","enable":true}'
LOC_JSON="[{\"enable\":1,\"path\":\"/\",\"type\":1,\"rootType\":\"root\",\"rootPath\":\"$HOME_DIR/e2e-www\",\"rootPage\":\"index.html\",\"header\":0,\"websocket\":0,\"cros\":0,\"returnPath\":0,\"locationParamJson\":\"\"}]"
r="$(api /adminPage/server/addOver --data-urlencode "serverJson=$SERVER_JSON" \
  --data-urlencode "serverParamJson=" --data-urlencode "locationJson=$LOC_JSON")"
case "$r" in *'"success":true'*) ;; *) fail setup "server addOver: $r"; exit 1 ;; esac

ALLOW_LIST="$IP_ALLOW
10.0.0.1
10.0.0.1/32
198.51.100.5/24"
r="$(api /adminPage/denyAllow/addOver --data-urlencode "id=" --data-urlencode "name=E2E-allowlist" \
  --data-urlencode "type=allow" --data-urlencode "ip=$ALLOW_LIST")"
case "$r" in *'"success":true'*) ;; *) fail setup "allowlist addOver: $r"; exit 1 ;; esac

# The seeded global GeoRule (allow mode, 17 countries) blocks every private IP (no country). Keep it off for
# checks 1-3 and 5 so 444/403/503 come from CrowdSec; check 4 turns it on on purpose.
geo="$(api /adminPage/geo/detail)"
GEO_ID="$(echo "$geo" | json_str id)"
GEO_COUNTRIES="$(echo "$geo" | json_str countries)"
[ -n "$GEO_COUNTRIES" ] || GEO_COUNTRIES="CN,JP,HK,KR,SG,TH,MY,TW,VN,GB,FR,DE,GR,CA,US,MO,LA"
info "global GeoRule id=${GEO_ID:-none} countries=$GEO_COUNTRIES"
geo_set false || { fail setup "disable GeoRule"; exit 1; }

apply || { fail setup "apply config"; exit 1; }
if docker exec "$NWU" grep -q 'crowdsec_http.conf' "$NGINX_CONF"; then
  info "nginx.conf includes conf.d/crowdsec_http.conf"
else
  info "nginx.conf does NOT include crowdsec_http.conf"
  docker exec "$NWU" cat "$NGINX_CONF" | head -60
fi
info "bouncer conf: $(docker exec "$NWU" ls -l $HOME_DIR/crowdsec/crowdsec-nginx-bouncer.conf 2>&1)"
info "bouncer conf MODE: $(docker exec "$NWU" grep '^MODE=' $HOME_DIR/crowdsec/crowdsec-nginx-bouncer.conf 2>&1)"

start_client allow "$IP_ALLOW"
start_client ban "$IP_BAN"
start_client env "$IP_ENV"
start_client waf "$IP_WAF"
start_client other "$IP_OTHER"
# stream mode: first decision pull plus the first health probe
sleep 12
info "state file: $(docker exec "$NWU" cat "$STATE_FILE" 2>&1)"

# ---------- 8. no traefik bouncer ----------
services="$(dc --profile security ps -a --format '{{.Service}} {{.Image}}')"
declared="$(docker compose -p "$PROJECT" --env-file "$ENVF" -f "$BASE" --profile security config --services 2>&1 | tr '\n' ' ')"
info "running: $(echo "$services" | tr '\n' ';')"
info "services declared in docker-compose.yml (security profile): $declared"
if echo "$services $declared" | grep -Eiq 'bouncer|traefik'; then
  fail 8 "a bouncer / traefik service is still present"
else
  pass 8 "no traefik bouncer container (services: $declared)"
fi

# ---------- 1. IP decision -> 444 ----------
base="$(req ban /)"
info "before decision: $IP_BAN GET / -> $base"
cscli decisions add -i "$IP_BAN" -d 1h -R "e2e ban test" >/dev/null 2>&1
T1=$(now)
got=""
while [ $(($(now) - T1)) -lt 45 ]; do
  got="$(req ban /)"
  [ "$got" = "52 000" ] && break
  sleep 3
done
if [ "$base" = "0 200" ] && [ "$got" = "52 000" ]; then
  pass 1 "after cscli decisions add -i $IP_BAN: curl exit 52 (empty reply) after $(($(now) - T1))s; before: 200"
else
  fail 1 "before=$base after=$got (want 0 200 then 52 000)"
fi

# ---------- 2. /.env -> 403 ----------
base="$(req env /)"
envr="$(req env /.env)"
if [ "$base" = "0 200" ] && [ "$envr" = "0 403" ]; then
  pass 2 "$IP_ENV GET /.env -> 403 (GET / -> 200)"
else
  fail 2 "GET / -> $base, GET /.env -> $envr (want 200 / 403)"
fi

# ---------- 3. SQLi / XSS -> alerts, then a decision ----------
T3=$(now)
codes=""
round=0
alerts_n=0
decided=0
while [ "$round" -lt 6 ] && [ "$decided" -eq 0 ]; do
  round=$((round + 1))
  for n in 1 2 3; do
    for p in "$SQLI1" "$SQLI2" "$XSS1" "$XSS2"; do
      codes="$codes $(req waf "$p" | cut -d' ' -f2)"
    done
  done
  sleep 10
  # cscli alerts list -i does not match WAF (kind waf) alerts, so filter the full list instead
  alerts_n="$(cscli alerts list -o raw 2>/dev/null | grep -Ec "Ip[:,]$IP_WAF,")"
  if cscli decisions list -o raw 2>/dev/null | grep -q "Ip:$IP_WAF,"; then
    decided=1
  fi
done
info "SQLi/XSS response codes: $(echo "$codes" | tr ' ' '\n' | sort | uniq -c | tr '\n' ' ')"
info "alerts for $IP_WAF (cscli alerts list):"
cscli alerts list 2>/dev/null | grep -E "^\+|ID |Ip:$IP_WAF " | sed 's/^/     /' | head -16
info "decisions for $IP_WAF (cscli decisions list):"
cscli decisions list 2>/dev/null | grep -E "^\+|ID |Ip:$IP_WAF " | sed 's/^/     /' | head -8
# the decision reaches nginx with the next stream pull
banned=""
T3b=$(now)
while [ "$decided" -eq 1 ] && [ $(($(now) - T3b)) -lt 30 ]; do
  banned="$(req waf /)"
  [ "$banned" = "52 000" ] && break
  sleep 3
done
info "after the decision: $IP_WAF GET / -> ${banned:-not tried}"
if [ "$alerts_n" -gt 0 ] && [ "$decided" -eq 1 ] && [ "$banned" = "52 000" ]; then
  pass 3 "$alerts_n alert(s) for $IP_WAF, decision after $round round(s) / $(($(now) - T3))s, then cut off (curl exit 52)"
else
  fail 3 "alerts=$alerts_n decision=$decided after $round round(s), GET / afterwards: ${banned:-none}"
fi

# ---------- 4. allowlist wins over decision, WAF and country block ----------
cscli decisions add -i "$IP_ALLOW" -d 1h -R "e2e allowlist test" >/dev/null 2>&1
geo_set true && apply || fail 4 "could not enable the country rule"
# decision must reach the bouncer (stream refresh every 10s)
sleep 25
dec="$(cscli decisions list -o raw 2>/dev/null | grep -c "Ip:$IP_ALLOW,")"
a_root="$(req allow /)"
a_sqli="$(req allow "$SQLI1")"
a_xss="$(req allow "$XSS1")"
a_env="$(req allow /.env)"
o_root="$(req other /)"
info "decisions on $IP_ALLOW: $dec; country rule on (allow mode; the test IPs have no country)"
info "allowlisted: / -> $a_root, SQLi -> $a_sqli, XSS -> $a_xss, /.env -> $a_env; control $IP_OTHER / -> $o_root"
if [ "$dec" -gt 0 ] && [ "$a_root" = "0 200" ] && [ "$a_sqli" = "0 200" ] && [ "$a_xss" = "0 200" ] \
  && [ "$a_env" = "0 404" ] && [ "$o_root" = "0 403" ]; then
  pass 4 "allowlisted IP gets 200 with a decision, attack strings and country block (/.env 404 = not stopped by WAF); control IP 403"
else
  fail 4 "dec=$dec /=$a_root sqli=$a_sqli xss=$a_xss env=$a_env control=$o_root (want 200/200/200/404, control 403)"
fi

# ---------- 7. nginx.conf has no country list and no allowlist IPs ----------
conf="$(docker exec "$NWU" cat "$NGINX_CONF")"
allow_inc="$(docker exec "$NWU" cat "$HOME_DIR/conf.d/allowlist_http.conf" 2>&1)"
geo_inc="$(docker exec "$NWU" cat "$HOME_DIR/conf.d/geo_http.conf" 2>&1)"
leak=""
for ip in "$IP_ALLOW" 10.0.0.1 198.51.100.5; do
  echo "$conf" | grep -qF "$ip" && leak="$leak $ip"
done
echo "$conf" | grep -Eq '^[[:space:]]*[A-Z]{2}[[:space:]]+[01];' && leak="$leak country-map-lines"
echo "$conf" | grep -qF "$GEO_COUNTRIES" && leak="$leak country-csv"
info "nginx.conf include lines: $(echo "$conf" | grep -E 'include conf\.d/(allowlist|geo|crowdsec)_http\.conf' | tr -s ' ' | tr '\n' ';')"
if [ -z "$leak" ] && echo "$conf" | grep -qF 'include conf.d/allowlist_http.conf;' \
  && echo "$conf" | grep -qF 'include conf.d/geo_http.conf;' \
  && echo "$allow_inc" | grep -qF "$IP_ALLOW 1;" && echo "$geo_inc" | grep -qF 'TW 0;'; then
  pass 7 "nginx.conf has only the include lines; IPs are in allowlist_http.conf, country codes in geo_http.conf"
else
  fail 7 "leaked into nginx.conf:${leak:- none}; allowlist/geo include files present as expected?"
fi

# nginx -t on the real generated config (allowlist has 10.0.0.1, 10.0.0.1/32 and 198.51.100.5/24)
echo "== nginx -t -c $NGINX_CONF -p $HOME_DIR/ (allowlist with duplicate and host-bits entries)"
t_out="$(nginx_t 2>&1)"
t_rc=$?
echo "$t_out" | sed 's/^/     /'
echo "$allow_inc" | sed 's/^/     allowlist_http.conf: /'
if [ "$t_rc" -eq 0 ]; then
  pass nginx-t "nginx -t successful (warnings: $(echo "$t_out" | grep -c '\[warn\]'))"
else
  fail nginx-t "nginx -t failed (exit $t_rc)"
fi

geo_set false && apply || fail 4 "could not disable the country rule again"
sleep 3

# ---------- 5. CrowdSec stopped -> 200 within grace, 503 after ----------
# Times are taken from the nginxwebui container clock and compared with lastOk in the state file
# (the last successful probe, at most 5s before the stop).
pre="$(req other /)"
pre_state="$(docker exec "$NWU" cat "$STATE_FILE" 2>&1)"
info "before stop: $IP_OTHER / -> $pre; state $pre_state"
docker stop "$CS" >/dev/null
T5=$(now)
early_bad=""
first503=""
last200=""
c503=""
c200=""
while [ $(($(now) - T5)) -lt 220 ]; do
  got="$(req other /)"
  ct="$(docker exec "$NWU" date +%s)"
  el=$(($(now) - T5))
  case "$got" in
  "0 503")
    first503=$el
    c503=$ct
    break
    ;;
  "0 200")
    last200=$el
    c200=$ct
    ;;
  *) early_bad="$early_bad ${el}s:$got" ;;
  esac
  sleep 2
done
hdr="$(docker exec "$PROJECT-client-other" curl -s -D - -o /dev/null -m 10 http://nginxwebui/ | tr -d '\r')"
a_down="$(req allow /)"
down_state="$(docker exec "$NWU" cat "$STATE_FILE" 2>&1)"
last_ok="$(echo "$down_state" | sed -n 's/.*"lastOk":\([0-9]*\).*/\1/p')"
ok200="" ok503=""
[ -n "$last_ok" ] && [ -n "$c200" ] && ok200=$((c200 - last_ok))
[ -n "$last_ok" ] && [ -n "$c503" ] && ok503=$((c503 - last_ok))
info "after docker stop $CS: last 200 at ${last200:-never}s, first 503 at ${first503:-never}s after the stop"
info "measured from lastOk (last successful probe): last 200 at ${ok200:-?}s, first 503 at ${ok503:-?}s (grace ${GRACE}s)"
info "503 headers: $(echo "$hdr" | grep -Ei '^(HTTP/|Retry-After)' | tr '\n' ' ')"
info "allowlisted while down: $a_down; state $down_state"
[ -n "$early_bad" ] && info "unexpected responses: $early_bad"
if [ "$pre" = "0 200" ] && [ -z "$early_bad" ] && [ -n "$ok200" ] && [ -n "$ok503" ] \
  && [ "$ok200" -ge $((GRACE - 10)) ] && [ "$ok503" -ge "$GRACE" ] && [ "$ok503" -le $((GRACE + 15)) ] \
  && [ "$a_down" = "0 200" ] && echo "$down_state" | grep -q '"lapiOk":false' \
  && echo "$hdr" | grep -qi '^Retry-After: 30'; then
  pass 5 "200 until ${ok200}s, 503 from ${ok503}s after the last successful probe (stop +${last200}s / +${first503}s); allowlisted 200; lapiOk=false"
else
  fail 5 "pre=$pre last200=${ok200:-none} first503=${ok503:-none} (from lastOk) allowlisted=$a_down state=$down_state"
fi

# ---------- 6. no security profile, empty key ----------
# Remove the key and restart without the security profile, WITHOUT applying the configuration again: nginx must keep
# serving, also after the grace period. Afterwards apply once and check that nginx.conf has no crowdsec_http.conf.
echo "== restarting without the security profile and with an empty CROWDSEC_BOUNCER_KEY (config not re-applied)"
write_env ""
dc --profile security rm -sf crowdsec >/dev/null 2>&1
T6=$(now)
dc up -d >"$WORK/up2.log" 2>&1 || cat "$WORK/up2.log"
wait_nwu || fail 6 "nginxwebui not ready after restart"
T6r=$(now)
env_key="$(docker exec "$NWU" sh -c 'printf %s "$CROWDSEC_BOUNCER_KEY"')"
cs_running="$(docker ps --filter "name=^/$CS$" --format '{{.Names}}')"
stale_inc="$(docker exec "$NWU" grep -c 'crowdsec_http.conf' "$NGINX_CONF")"
stale_conf="$(docker exec "$NWU" sh -c "[ -f $HOME_DIR/crowdsec/crowdsec-nginx-bouncer.conf ] && echo present || echo absent")"
r_now="$(req other /)"
info "restart took $((T6r - T6))s; key in container env: '${env_key}'; crowdsec running: '${cs_running}'"
info "without re-applying: nginx.conf crowdsec_http.conf lines = $stale_inc, bouncer conf $stale_conf"
info "right after restart: $IP_OTHER GET / -> $r_now"
while [ $(($(now) - T6r)) -lt $((GRACE + 15)) ]; do
  sleep 5
done
r_late="$(req other /)"
info "$(($(now) - T6r))s after nginxWebUI came up (grace ${GRACE}s + 15s): $IP_OTHER GET / -> $r_late"
login && apply || fail 6 "login / apply after restart"
conf="$(docker exec "$NWU" cat "$NGINX_CONF")"
o_root="$(req other /)"
t6="$(nginx_t 2>&1 | tail -1)"
info "after applying: include lines = $(echo "$conf" | grep -c 'crowdsec_http.conf'), GET / -> $o_root; $t6"
if [ -z "$env_key" ] && [ -z "$cs_running" ] && [ "$r_now" = "0 200" ] && [ "$r_late" = "0 200" ] \
  && [ "$o_root" = "0 200" ] && ! echo "$conf" | grep -q 'crowdsec_http.conf' && echo "$t6" | grep -q 'successful'; then
  pass 6 "no profile, empty key: 200 right after restart and after the grace period; after applying nginx.conf has no crowdsec_http.conf"
else
  fail 6 "key='$env_key' crowdsec='$cs_running' restart: GET /=$r_now, after grace: $r_late (stale include $stale_inc, bouncer conf $stale_conf); after apply: GET /=$o_root include=$(echo "$conf" | grep -c 'crowdsec_http.conf')"
fi

echo "crowdsec-e2e: $PASS_N PASS, $FAIL_N FAIL (total $(($(now) - T_START))s)"
[ "$FAIL_N" -eq 0 ]
