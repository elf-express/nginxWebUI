#!/bin/sh
# End-to-end check of the "CrowdSec Guard" page backend (E-385, acceptance of E-384) on a real compose stack.
# Starts a throwaway compose project with the security profile, creates a test site through the nginxWebUI admin API
# and checks, through /adminPage/crowdsecGuard/*:
#   1. ipLookup, country layer (GeoLite2 baked into the image): 5.62.0.1 is blocked by the seeded global rule,
#      8.8.8.8 is allowed
#   2. cscli decisions add -i <ip>  -> ipLookup crowdsec layer = blocked, blockedBy = crowdsec
#   3. bans?scope=local lists the decision
#   4. a request from that IP is cut off (444); "blocked" lists the IP with the site's host and status 444
#      (all sites, and filtered by the site)
#   5. whitelist -> crowdsec ok, the decision is gone from cscli decisions list, ipLookup shows the allowlist layer,
#      the operation log has a "[CrowdSec] whitelist" entry
#   6. after applying the config the whitelisted client is served again (200)
#   7. ban / unban from the page API: scenario starts with nginxwebui:manual, gone after unban
#   8. two restarts on the same database: the log_format "$host" migration and the legacy whitelist decision cleanup
#      run at most once, and "$host" is in the log_format only once
# Nothing of an existing deployment is touched: project nwu-e385-e2e, own container names, own volumes, own network,
# no published ports. Everything is removed on exit (also on failure). Takes about 4 minutes.
# Usage: sh docker/test/crowdsec-guard-e2e.sh <nginxwebui image> <nginxwebui-crowdsec image>
#   e.g. sh docker/test/crowdsec-guard-e2e.sh nginxwebui:e385 nginxwebui-crowdsec:e390
set -u
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1
unset COMPOSE_PROFILES COMPOSE_FILE COMPOSE_PROJECT_NAME

NWU_IMAGE="${1:?usage: crowdsec-guard-e2e.sh <nginxwebui image> <crowdsec image>}"
CS_IMAGE="${2:?usage: crowdsec-guard-e2e.sh <nginxwebui image> <crowdsec image>}"

HERE="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$HERE/.." && pwd)"
PROJECT=nwu-e385-e2e
NET="${PROJECT}_default"
NWU="${PROJECT}-nginxwebui"
CS="${PROJECT}-crowdsec"
CLIENT="${PROJECT}-client"
# A documentation range (TEST-NET-2), not RFC 1918: CrowdSec's default whitelist drops events from private IPs.
SUBNET=198.51.100
IP_CLIENT=$SUBNET.60    # the client container; banned, then whitelisted
IP_MANUAL=$SUBNET.71    # ban / unban through the page API
IP_GEO_BLOCKED=5.62.0.1 # a country outside the seeded 17-country allow list
IP_GEO_ALLOWED=8.8.8.8  # US
HOST=e2e.test
ADMIN=admin
CAPTCHA=1234
HOME_DIR=/home/nginxWebUI

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
  docker rm -f "$CLIENT" >/dev/null 2>&1 || true
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

# Throwaway credentials, generated per run so no password lives in the repository.
ADMIN_PASS="Aa1$(random_key | cut -c1-24)"
PG_PASS="$(random_key)"
KEY="$(random_key)"
MACHINE_PASS="$(random_key)"

cat > "$WORK/e2e.env" <<EOF
NGINX_WEBUI_VERSION=e2e
E2E_NWU_IMAGE=$NWU_IMAGE
E2E_CS_IMAGE=$CS_IMAGE
CROWDSEC_BOUNCER_KEY=$KEY
CROWDSEC_MACHINE_PASSWORD=$MACHINE_PASS
ABUSEIPDB_API_KEY=
MCP_TOKEN=
EOF

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
  nginxwebui_waf:
    name: ${PROJECT}_waf
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
# layer_state <layer> : state of one layer in an ipLookup response
layer_state() { sed -n "s/.*\"layer\":\"$1\",\"state\":\"\([a-z]*\)\".*/\1/p"; }

cscli() { docker exec "$CS" cscli "$@"; }
decision_lines() { cscli decisions list -o raw 2>/dev/null | grep -F "$1"; }
ok() { case "$1" in *'"success":true'*) return 0 ;; esac; return 1; }

# oplog_has <text> : the operation log has an entry containing <text> (the list page only shows buttons, so read
# each entry through its detail endpoint)
oplog_has() {
  for oid in $(api /adminPage/operateLog -d curr=1 -d limit=100 | grep -o "detail('[0-9]*')" | grep -o '[0-9][0-9]*'); do
    api "/adminPage/operateLog/detail?id=$oid" | grep -qF "$1" && return 0
  done
  return 1
}

# req : prints "<curl exit> <http code>" for a request from the client container (fixed IP) to the test site
req() {
  out="$(docker exec "$CLIENT" curl -g -s -o /dev/null -m 10 -H "Host: $HOST" -w '%{http_code}' "http://nginxwebui/")"
  rc=$?
  echo "$rc $out"
}

# ---------- setup ----------
echo "== starting stack (project $PROJECT, $NWU_IMAGE + $CS_IMAGE, security profile)"
if ! dc --profile security up -d >"$WORK/up.log" 2>&1; then
  cat "$WORK/up.log"
  fail setup "docker compose up failed"
  exit 1
fi
wait_nwu || { fail setup "nginxwebui not ready"; exit 1; }
i=0
while [ "$i" -lt 60 ]; do
  cscli machines list -o raw 2>/dev/null | grep -q '^nginxwebui,' && cscli bouncers list -o raw 2>/dev/null | grep -q '^nginx,' && break
  i=$((i + 1))
  sleep 2
done
if ! cscli machines list -o raw 2>/dev/null | grep -q '^nginxwebui,'; then
  docker logs "$CS" 2>&1 | tail -30
  fail setup "machine nginxwebui not registered in crowdsec"
  exit 1
fi
login || { fail setup "admin login"; exit 1; }
st="$(api /adminPage/crowdsec/status)"
if echo "$st" | grep -q '"lapi":"ok"' && echo "$st" | grep -q '"bouncerKey":"ok"' && echo "$st" | grep -q '"machine":"ok"'; then
  info "crowdsec status: lapi, bouncer key and machine login ok"
else
  fail setup "crowdsec status: $st"
  exit 1
fi

# ---------- web root, test site, allowlist-free GeoRule ----------
docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/e2e-www && echo e2e-ok > $HOME_DIR/e2e-www/index.html \
  && chmod o+x $HOME_DIR && chmod -R a+rX $HOME_DIR/e2e-www"
SERVER_JSON="{\"proxyType\":\"0\",\"listen\":\"80\",\"def\":\"1\",\"ipv6\":\"0\",\"serverName\":\"$HOST\",\"ssl\":\"0\",\"rewrite\":\"0\",\"http2\":\"0\",\"proxyProtocol\":\"0\",\"protocols\":\"TLSv1.2 TLSv1.3\",\"enable\":true}"
LOC_JSON="[{\"enable\":1,\"path\":\"/\",\"type\":1,\"rootType\":\"root\",\"rootPath\":\"$HOME_DIR/e2e-www\",\"rootPage\":\"index.html\",\"header\":0,\"websocket\":0,\"cros\":0,\"returnPath\":0,\"locationParamJson\":\"\"}]"
r="$(api /adminPage/server/addOver --data-urlencode "serverJson=$SERVER_JSON" \
  --data-urlencode "serverParamJson=" --data-urlencode "locationJson=$LOC_JSON")"
ok "$r" || { fail setup "server addOver: $r"; exit 1; }

# ---------- 1. country layer ----------
# After the test site exists (the country layer is evaluated per enabled HTTP site) and before the global GeoRule is
# turned off for the 444 checks below.
r1="$(api "/adminPage/crowdsecGuard/ipLookup?ip=$IP_GEO_BLOCKED")"
r2="$(api "/adminPage/crowdsecGuard/ipLookup?ip=$IP_GEO_ALLOWED")"
s1="$(echo "$r1" | layer_state geo)"
s2="$(echo "$r2" | layer_state geo)"
c1="$(echo "$r1" | json_str country)"
c2="$(echo "$r2" | json_str country)"
if ok "$r1" && ok "$r2" && [ "$s1" = "blocked" ] && [ "$s2" = "allowed" ] && [ -n "$c1" ] && [ "$c2" = "US" ]; then
  pass 1 "country layer: $IP_GEO_BLOCKED ($c1) blocked, $IP_GEO_ALLOWED ($c2) allowed"
else
  fail 1 "geo states: '$s1' / '$s2'; response: $r1"
fi

# The seeded global GeoRule (allow mode, 17 countries) blocks every IP without a country, so it would answer 444
# before CrowdSec does. Turn it off so the 444 below comes from the CrowdSec decision.
geo="$(api /adminPage/geo/detail)"
GEO_ID="$(echo "$geo" | json_str id)"
GEO_COUNTRIES="$(echo "$geo" | json_str countries)"
[ -n "$GEO_COUNTRIES" ] || GEO_COUNTRIES="CN,JP,HK,KR,SG,TH,MY,TW,VN,GB,FR,DE,GR,CA,US,MO,LA"
r="$(api /adminPage/geo/addOver --data-urlencode "id=$GEO_ID" -d mode=0 \
  --data-urlencode "countries=$GEO_COUNTRIES" -d enable=false)"
ok "$r" || { fail setup "disable GeoRule: $r"; exit 1; }

apply || { fail setup "apply config"; exit 1; }
docker rm -f "$CLIENT" >/dev/null 2>&1 || true
docker run -d --name "$CLIENT" --network "$NET" --ip "$IP_CLIENT" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
# stream mode: first decision pull plus the first health probe
sleep 12
base="$(req)"
info "before the decision: $IP_CLIENT GET $HOST -> $base"

# ---------- 2. cscli decision -> ipLookup ----------
cscli decisions add -i "$IP_CLIENT" -d 1h -R "e385 guard test" >/dev/null 2>&1
r="$(api "/adminPage/crowdsecGuard/ipLookup?ip=$IP_CLIENT")"
cs="$(echo "$r" | layer_state crowdsec)"
if ok "$r" && [ "$cs" = "blocked" ] && echo "$r" | grep -q '"blockedBy":"crowdsec"'; then
  pass 2 "ipLookup $IP_CLIENT: crowdsec layer blocked, blockedBy crowdsec"
else
  fail 2 "crowdsec layer '$cs'; response: $r"
fi

# ---------- 3. ban list ----------
r="$(api "/adminPage/crowdsecGuard/bans?scope=local&q=$IP_CLIENT")"
if ok "$r" && echo "$r" | grep -q "\"value\":\"$IP_CLIENT\""; then
  pass 3 "bans?scope=local lists $IP_CLIENT"
else
  fail 3 "bans: $r"
fi

# ---------- 4. 444 and the recently blocked list ----------
T4=$(now)
got=""
while [ $(($(now) - T4)) -lt 45 ]; do
  got="$(req)"
  [ "$got" = "52 000" ] && break
  sleep 3
done
SERVER_ID="$(api /adminPage/crowdsecGuard/servers | json_str id)"
sleep 2
all="$(api "/adminPage/crowdsecGuard/blocked?minutes=60")"
one="$(api "/adminPage/crowdsecGuard/blocked?minutes=60&serverId=$SERVER_ID")"
if [ "$base" = "0 200" ] && [ "$got" = "52 000" ] && echo "$all" | grep -q "\"ip\":\"$IP_CLIENT\"" \
  && echo "$all" | grep -q "\"host\":\"$HOST\"" && echo "$all" | grep -q '"444":' \
  && echo "$all" | grep -q '"hostSupported":true' && echo "$one" | grep -q "\"ip\":\"$IP_CLIENT\""; then
  pass 4 "$IP_CLIENT cut off (curl exit 52, before: 200); blocked lists it with host $HOST and status 444, also filtered by site"
else
  fail 4 "before=$base after=$got; all: $all; site($SERVER_ID): $one"
  docker exec "$NWU" tail -5 "$HOME_DIR/log/access.log" 2>&1 | sed 's/^/     access.log: /'
fi

# ---------- 5. whitelist ----------
w="$(api /adminPage/crowdsecGuard/whitelist -d "ip=$IP_CLIENT")"
left="$(cscli decisions list -i "$IP_CLIENT" -o raw 2>/dev/null | tail -n +2)"
r="$(api "/adminPage/crowdsecGuard/ipLookup?ip=$IP_CLIENT")"
oplog_has "[CrowdSec] whitelist $IP_CLIENT"
logged=$?
if ok "$w" && echo "$w" | grep -q '"crowdsec":"ok"' && [ -z "$left" ] \
  && [ "$(echo "$r" | layer_state allowlist)" = "match" ] && [ "$(echo "$r" | layer_state crowdsec)" = "skipped" ] \
  && [ "$logged" -eq 0 ]; then
  pass 5 "whitelist $IP_CLIENT: crowdsec ok, decision removed, allowlist layer matches, operation log entry present"
else
  fail 5 "whitelist: $w; decisions left: '$left'; lookup: $r; operation log entry found: $([ "$logged" -eq 0 ] && echo yes || echo no)"
fi

# ---------- 6. applied allowlist serves the client again ----------
apply || fail 6 "apply config after whitelist"
T6=$(now)
got=""
while [ $(($(now) - T6)) -lt 45 ]; do
  got="$(req)"
  [ "$got" = "0 200" ] && break
  sleep 3
done
if [ "$got" = "0 200" ]; then
  pass 6 "after applying the config $IP_CLIENT is served again (200) after $(($(now) - T6))s"
else
  fail 6 "after whitelist + apply: $got (want 0 200)"
fi

# ---------- 7. ban / unban through the page API ----------
b="$(api /adminPage/crowdsecGuard/ban -d "ip=$IP_MANUAL" -d duration=1h --data-urlencode "reason=e385-guard")"
line="$(decision_lines "$IP_MANUAL")"
id="$(echo "$line" | head -1 | cut -d, -f1)"
u="$(api /adminPage/crowdsecGuard/unban -d "decisionId=$id" -d "value=$IP_MANUAL")"
after="$(decision_lines "$IP_MANUAL")"
if ok "$b" && echo "$line" | grep -qF ",nginxwebui:manual:e385-guard," && ok "$u" && [ -z "$after" ]; then
  pass 7 "ban $IP_MANUAL carries scenario nginxwebui:manual:e385-guard; unban (decision $id) removed it"
else
  fail 7 "ban: $b; cscli: $line; unban: $u; after: $after"
fi

# ---------- 8. startup migrations run once ----------
# Two restarts on the same database: the log_format "$host" migration and the legacy whitelist decision cleanup
# are gated by setting flags, so each logs at most once over all three starts and "$host" is appended only once.
docker restart "$NWU" >/dev/null && wait_nwu && login || fail 8 "nginxwebui not ready after the first restart"
docker restart "$NWU" >/dev/null && wait_nwu && login || fail 8 "nginxwebui not ready after the second restart"
nwu_log="$(docker logs "$NWU" 2>&1)"
n_host="$(echo "$nwu_log" | grep -c 'Migration: appended \$host')"
n_legacy="$(echo "$nwu_log" | grep -c 'Migration: deleted .* legacy nginxwebui whitelist')"
apply >/dev/null 2>&1
n_fmt="$(docker exec "$NWU" sh -c "sed -n '/log_format/,/;/p' $HOME_DIR/nginx.conf | grep -o '\"\\\$host\"' | wc -l" | tr -d ' \r')"
if [ "$n_host" -le 1 ] && [ "$n_legacy" -le 1 ] && [ "$n_fmt" = "1" ]; then
  pass 8 "after 2 restarts: host migration logged $n_host time(s), legacy cleanup $n_legacy time(s), log_format has \"\$host\" $n_fmt time(s)"
else
  fail 8 "host migration logged $n_host, legacy cleanup logged $n_legacy, \"\$host\" in log_format $n_fmt"
fi

echo "crowdsec-guard-e2e: $PASS_N PASS, $FAIL_N FAIL"
[ "$FAIL_N" -eq 0 ]
