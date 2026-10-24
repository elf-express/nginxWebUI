#!/bin/sh
# End-to-end check of the WAF events (E-394, acceptance of E-384) on a real compose stack.
# Starts a throwaway compose project with the security profile, creates a test site and checks:
#   1. the crowdsec image keeps up to 50000 alerts for 7 days (db_config.flush.max_items from config.yaml.local, max_age unchanged)
#   2. GET /.env (403) and a SQL injection from the client container show up in /adminPage/crowdsecGuard/waf/events
#      within 100 s without a manual sync: 2 events, host e2e.test, the client's User-Agent, the /.env event blocked
#      with the vpatch rule and CRS 930130 merged into it
#   3. the category filter crs-sqli lists only the SQL injection
#   4. ipLookup of the client lists its recent WAF events
#   5. backfill: with the events deleted and the sync point moved 30 minutes back, the next sync restores both
#      events and marks a gap (CrowdSec has no alert older than the sync point)
#   6. crowdsec stopped: sync fails with crowdsec_lapi_unavailable and the stored events stay listed;
#      crowdsec started again: sync succeeds (new machine JWT) and the error is cleared
# Nothing of an existing deployment is touched: project nwu-e394-e2e, own container names, own volumes, own network,
# no published ports. Everything is removed on exit (also on failure). Takes about 5 minutes.
# The subnet (198.51.100.0/24) is the same as in crowdsec-guard-e2e.sh: do not run the two scripts at the same time.
# Usage: sh docker/test/crowdsec-waf-events-e2e.sh <nginxwebui image> <nginxwebui-crowdsec image>
#   e.g. sh docker/test/crowdsec-waf-events-e2e.sh nginxwebui:e394 nginxwebui-crowdsec:e394
set -u
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1
unset COMPOSE_PROFILES COMPOSE_FILE COMPOSE_PROJECT_NAME

NWU_IMAGE="${1:?usage: crowdsec-waf-events-e2e.sh <nginxwebui image> <crowdsec image>}"
CS_IMAGE="${2:?usage: crowdsec-waf-events-e2e.sh <nginxwebui image> <crowdsec image>}"

HERE="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$HERE/.." && pwd)"
PROJECT=nwu-e394-e2e
NET="${PROJECT}_default"
NWU="${PROJECT}-nginxwebui"
CS="${PROJECT}-crowdsec"
PG="${PROJECT}-postgres"
CLIENT="${PROJECT}-client"
# A documentation range (TEST-NET-2), not RFC 1918: CrowdSec's default whitelist drops events from private IPs.
SUBNET=198.51.100
IP_CLIENT=$SUBNET.60
HOST=e2e.test
UA="e394-e2e/1.0"
SQLI_PATH="/search?q=1%27%20UNION%20SELECT%20password%20FROM%20users--"
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
    container_name: $PG
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
cscli() { docker exec "$CS" cscli "$@"; }
ok() { case "$1" in *'"success":true'*) return 0 ;; esac; return 1; }

# get <path> : prints the HTTP code of a request from the client container (fixed IP) to the test site
get() {
  docker exec "$CLIENT" curl -g -s -o /dev/null -m 10 -A "$UA" -H "Host: $HOST" -w '%{http_code}' "http://nginxwebui$1"
}

# wait_count <query> <count> <seconds> : polls waf/events until "count":<count>; prints the last response
wait_count() {
  t0=$(now)
  out=""
  while [ $(($(now) - t0)) -lt "$3" ]; do
    out="$(api "/adminPage/crowdsecGuard/waf/events?$1")"
    case "$out" in *"\"count\":$2"[,}]*) break ;; esac
    sleep 5
  done
  echo "$out"
}

# sync_now : POST waf/sync, retried while a scheduled sync is running (outcome busy); prints the last response
sync_now() {
  i=0
  s=""
  while [ "$i" -lt 10 ]; do
    s="$(api /adminPage/crowdsecGuard/waf/sync -X POST -d '')"
    case "$s" in
    *'"outcome":"busy"'*)
      i=$((i + 1))
      sleep 3
      ;;
    *) break ;;
    esac
  done
  echo "$s"
}

wait_crowdsec() {
  i=0
  while [ "$i" -lt 60 ]; do
    cscli machines list -o raw 2>/dev/null | grep -q '^nginxwebui,' && return 0
    i=$((i + 1))
    sleep 2
  done
  return 1
}

# ---------- setup ----------
echo "== starting stack (project $PROJECT, $NWU_IMAGE + $CS_IMAGE, security profile)"
if ! dc --profile security up -d >"$WORK/up.log" 2>&1; then
  cat "$WORK/up.log"
  fail setup "docker compose up failed"
  exit 1
fi
wait_nwu || { fail setup "nginxwebui not ready"; exit 1; }
wait_crowdsec || { docker logs "$CS" 2>&1 | tail -30; fail setup "machine nginxwebui not registered in crowdsec"; exit 1; }
login || { fail setup "admin login"; exit 1; }

docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/e2e-www && echo e2e-ok > $HOME_DIR/e2e-www/index.html \
  && chmod o+x $HOME_DIR && chmod -R a+rX $HOME_DIR/e2e-www"
SERVER_JSON="{\"proxyType\":\"0\",\"listen\":\"80\",\"def\":\"1\",\"ipv6\":\"0\",\"serverName\":\"$HOST\",\"ssl\":\"0\",\"rewrite\":\"0\",\"http2\":\"0\",\"proxyProtocol\":\"0\",\"protocols\":\"TLSv1.2 TLSv1.3\",\"enable\":true}"
LOC_JSON="[{\"enable\":1,\"path\":\"/\",\"type\":1,\"rootType\":\"root\",\"rootPath\":\"$HOME_DIR/e2e-www\",\"rootPage\":\"index.html\",\"header\":0,\"websocket\":0,\"cros\":0,\"returnPath\":0,\"locationParamJson\":\"\"}]"
r="$(api /adminPage/server/addOver --data-urlencode "serverJson=$SERVER_JSON" \
  --data-urlencode "serverParamJson=" --data-urlencode "locationJson=$LOC_JSON")"
ok "$r" || { fail setup "server addOver: $r"; exit 1; }

# The seeded global GeoRule (allow mode) answers 403 to IPs without a country before the WAF sees them; turn it off.
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
# first decision pull plus the first health probe of the bouncer
sleep 12

# ---------- 1. alert cap ----------
flush="$(cscli config show 2>/dev/null | grep -i 'Flush size')"
flush_age="$(cscli config show 2>/dev/null | grep -i 'Flush age')"
case "$flush" in
*50000*)
  case "$flush_age" in
  *168h*) pass 1 "crowdsec keeps up to 50000 alerts for 7 days ($(echo "$flush" | tr -s ' '), $(echo "$flush_age" | tr -s ' '))" ;;
  *) fail 1 "flush age: '$flush_age'" ;;
  esac
  ;;
*) fail 1 "flush size: '$flush'" ;;
esac

# ---------- 2. events arrive through the scheduled sync ----------
c_env="$(get /.env)"
c_sqli="$(get "$SQLI_PATH")"
info "GET /.env -> $c_env, SQL injection -> $c_sqli"
ev="$(wait_count "ip=$IP_CLIENT&days=0" 2 100)"
if [ "$c_env" = "403" ] && [ "$c_sqli" != "403" ] && echo "$ev" | grep -qE '"count":2[,}]' \
  && echo "$ev" | grep -q "\"host\":\"$HOST\"" && echo "$ev" | grep -qF "\"userAgent\":\"$UA\"" \
  && echo "$ev" | grep -q '"action":"block"' && echo "$ev" | grep -q '"ruleName":"crowdsecurity/vpatch-env-access"' \
  && echo "$ev" | grep -q '"ruleName":"930130"'; then
  pass 2 "2 events within 100 s (host $HOST, User-Agent kept, /.env blocked with vpatch + CRS 930130 merged)"
else
  fail 2 "codes /.env=$c_env sqli=$c_sqli; events: $ev"
fi

# ---------- 3. category filter ----------
r="$(api "/adminPage/crowdsecGuard/waf/events?ip=$IP_CLIENT&days=0&category=crs-sqli")"
if echo "$r" | grep -qE '"count":1[,}]' && echo "$r" | grep -q '"category":"crs-sqli"' && ! echo "$r" | grep -q 'vpatch-env-access'; then
  pass 3 "category crs-sqli lists only the SQL injection"
else
  fail 3 "crs-sqli: $r"
fi

# ---------- 4. ipLookup ----------
r="$(api "/adminPage/crowdsecGuard/ipLookup?ip=$IP_CLIENT")"
if ok "$r" && echo "$r" | grep -q '"wafEvents":\[{' && echo "$r" | grep -q '"ruleName":"930130"'; then
  pass 4 "ipLookup $IP_CLIENT lists its recent WAF events"
else
  fail 4 "ipLookup: $r"
fi

# ---------- 5. backfill and gap ----------
# Right after a finished sync (so a running scheduled sync cannot write its sync point over ours), drop the events and
# move the sync point 30 minutes back. The stack is younger than that, so CrowdSec has no alert older than the sync
# point: the next sync restores both events and records a gap starting at the sync point.
# A scheduled sync can run between the delete and the check and bring the events back; then the reset is repeated (3 tries).
tries=0
gone=""
while [ "$tries" -lt 3 ]; do
  sync_now >/dev/null
  back=$(( $(now) * 1000 - 1800000 ))
  docker exec "$PG" psql -q -U nginxwebui -d nginxwebui -c "DELETE FROM waf_event_rule; DELETE FROM waf_event; \
UPDATE setting SET value='$back' WHERE key='crowdsecWafSyncedUntil'; DELETE FROM setting WHERE key IN ('crowdsecWafGapFrom','crowdsecWafGapTo');" >/dev/null
  gone="$(api "/adminPage/crowdsecGuard/waf/events?ip=$IP_CLIENT&days=0")"
  case "$gone" in *'"count":0'[,}]*) break ;; esac
  tries=$((tries + 1))
done
s5="$(sync_now)"
ev="$(api "/adminPage/crowdsecGuard/waf/events?ip=$IP_CLIENT&days=0")"
st="$(api /adminPage/crowdsecGuard/waf/status)"
if echo "$gone" | grep -qE '"count":0[,}]' && echo "$s5" | grep -q '"outcome":"ok"' && echo "$ev" | grep -qE '"count":2[,}]' \
  && echo "$st" | grep -q "\"gapFrom\":$back"; then
  pass 5 "events restored from the sync point 30 minutes back; gap marked from $back"
else
  fail 5 "after delete: $gone; sync: $s5; after backfill: $ev; status: $st"
fi

# ---------- 6. crowdsec down and up ----------
docker stop "$CS" >/dev/null
s_down="$(sync_now)"
st_down="$(api /adminPage/crowdsecGuard/waf/status)"
kept="$(api "/adminPage/crowdsecGuard/waf/events?ip=$IP_CLIENT&days=0")"
docker start "$CS" >/dev/null
# The machine record lives in the volume, so wait_crowdsec returns at once; poll the sync until the LAPI answers (about 60 s).
i=0
s_up=""
while [ "$i" -lt 12 ]; do
  s_up="$(sync_now)"
  case "$s_up" in *'"outcome":"ok"'*) break ;; esac
  i=$((i + 1))
  sleep 5
done
st_up="$(api /adminPage/crowdsecGuard/waf/status)"
if echo "$s_down" | grep -q '"error":"crowdsec_lapi_unavailable"' \
  && echo "$st_down" | grep -q '"lastError":"crowdsec_lapi_unavailable"' && echo "$kept" | grep -qE '"count":2[,}]' \
  && echo "$s_up" | grep -q '"outcome":"ok"' && ! echo "$st_up" | grep -q '"lastError":"'; then
  pass 6 "crowdsec down: sync failed (lapi unavailable), events kept; up again: sync ok and the error cleared"
else
  fail 6 "down: $s_down / $st_down; events: $kept; up: $s_up / $st_up"
fi

echo "crowdsec-waf-events-e2e: $PASS_N PASS, $FAIL_N FAIL"
[ "$FAIL_N" -eq 0 ]
