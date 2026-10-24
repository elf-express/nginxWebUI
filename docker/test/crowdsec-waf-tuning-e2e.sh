#!/bin/sh
# End-to-end check of the WAF rule tuning (E-395, acceptance of E-384) on a real compose stack.
# Starts a throwaway compose project with the security profile, creates a test site and changes WAF rules through
# /adminPage/crowdsecGuard/wafRules/*:
#   1. the rule catalog (exported by the crowdsec image into the shared volume) is visible through the API
#   2. baseline: GET /.env is blocked (403, vpatch); a SQL injection passes (CRS is out-of-band) and raises a 942100 alert
#   3. vpatch rules for /.env and /.git/config set to "log only": the requests are served and, repeated, do not ban the client
#   4. the synced WAF event of the /.env request has the result "log" (E-394 sync + E-395 rule settings)
#   5. CRS 942100 excluded for the test site: the same SQL injection no longer raises a 942100 alert
#   6. a broken config written to the shared volume is rejected: crowdsec keeps running on the last good config
#   7. after a crowdsec restart the last good config is applied again
#   8. the operation log has the WAF apply
#   9. the same SQL injection with an upper-case Host header (E2E.TEST) is excluded as well
#  10. vpatch-env-access set to "disable": /.env is served and raises no vpatch-env-access alert
#  11. a vpatch rule for a known vulnerability (CVE-2017-9841) excluded for the test site: the site answers 404, the same
#      request with Host other.test is still blocked (403)
#  12. CRS category crs-sqli set to "log only": 7 SQL injections from a fresh client raise alerts but no decision
# Checks 3, 5, 9, 10 and 12 are negative ("no ban" / "no alert"), so each is paired with a positive control:
#   3 -> 2b: before any rule is changed, a fresh client that hits two different vpatch rules within 60 s is banned.
#  12 -> 2c: with the default settings, another fresh client sending the same 7 SQL injections is banned.
#   5, 9: the same SQL injection with a Host outside the exclusion (other.test, answered by the default server) must still
#         raise a 942100 alert.
#  11: the request with Host other.test is still blocked.
# Nothing of an existing deployment is touched: project nwu-e395-e2e, own container names, own volumes (including the
# shared WAF volume), own network, no published ports. Everything is removed on exit (also on failure).
# The subnet (198.51.100.0/24) is the same as in the other crowdsec-*-e2e.sh scripts: do not run them at the same time.
# Usage: sh docker/test/crowdsec-waf-tuning-e2e.sh <nginxwebui image> <nginxwebui-crowdsec image>
#   e.g. sh docker/test/crowdsec-waf-tuning-e2e.sh nginxwebui:e395 nwu-crowdsec:e395
set -u
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1
unset COMPOSE_PROFILES COMPOSE_FILE COMPOSE_PROJECT_NAME

NWU_IMAGE="${1:?usage: crowdsec-waf-tuning-e2e.sh <nginxwebui image> <crowdsec image>}"
CS_IMAGE="${2:?usage: crowdsec-waf-tuning-e2e.sh <nginxwebui image> <crowdsec image>}"

HERE="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$HERE/.." && pwd)"
PROJECT=nwu-e395-e2e
NET="${PROJECT}_default"
NWU="${PROJECT}-nginxwebui"
CS="${PROJECT}-crowdsec"
CLIENT="${PROJECT}-client"
# A documentation range (TEST-NET-2), not RFC 1918: CrowdSec's default whitelist drops events from private IPs.
SUBNET=198.51.100
IP_CLIENT=$SUBNET.60    # the client container
CLIENT_VP="${PROJECT}-client-vp"   # fresh client of the vpatch control (2b)
CLIENT_B="${PROJECT}-client-b"     # fresh client of the CRS control (2c)
CLIENT_C="${PROJECT}-client-c"     # fresh client of the CRS log-only check (12)
CLIENT_D="${PROJECT}-client-d"     # fresh client of the baseline request of check 11 (a CVE rule bans its client at once)
CLIENT_E="${PROJECT}-client-e"     # fresh client of the exclusion request of check 11
IP_VP=$SUBNET.61
IP_B=$SUBNET.62
IP_C=$SUBNET.63
IP_D=$SUBNET.64
IP_E=$SUBNET.65
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
  docker rm -f "$CLIENT" "$CLIENT_VP" "$CLIENT_B" "$CLIENT_C" "$CLIENT_D" "$CLIENT_E" >/dev/null 2>&1 || true
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

cscli() { docker exec "$CS" cscli "$@"; }
ok() { case "$1" in *'"success":true'*) return 0 ;; esac; return 1; }

# oplog_has <text> : the operation log has an entry containing <text> (the list page only shows buttons, so read
# each entry through its detail endpoint)
oplog_has() {
  for oid in $(api /adminPage/operateLog -d curr=1 -d limit=100 | grep -o "detail('[0-9]*')" | grep -o '[0-9][0-9]*'); do
    api "/adminPage/operateLog/detail?id=$oid" | grep -qF "$1" && return 0
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

# ---------- web root, test site, GeoRule off ----------
docker exec "$NWU" sh -c "mkdir -p $HOME_DIR/e2e-www && echo e2e-ok > $HOME_DIR/e2e-www/index.html \
  && chmod o+x $HOME_DIR && chmod -R a+rX $HOME_DIR/e2e-www"
SERVER_JSON="{\"proxyType\":\"0\",\"listen\":\"80\",\"def\":\"1\",\"ipv6\":\"0\",\"serverName\":\"$HOST\",\"ssl\":\"0\",\"rewrite\":\"0\",\"http2\":\"0\",\"proxyProtocol\":\"0\",\"protocols\":\"TLSv1.2 TLSv1.3\",\"enable\":true}"
LOC_JSON="[{\"enable\":1,\"path\":\"/\",\"type\":1,\"rootType\":\"root\",\"rootPath\":\"$HOME_DIR/e2e-www\",\"rootPage\":\"index.html\",\"header\":0,\"websocket\":0,\"cros\":0,\"returnPath\":0,\"locationParamJson\":\"\"}]"
r="$(api /adminPage/server/addOver --data-urlencode "serverJson=$SERVER_JSON" \
  --data-urlencode "serverParamJson=" --data-urlencode "locationJson=$LOC_JSON")"
ok "$r" || { fail setup "server addOver: $r"; exit 1; }

# The seeded global GeoRule (allow mode, 17 countries) blocks every IP without a country (444)
# before the WAF is reached. Turn it off.
geo="$(api /adminPage/geo/detail)"
GEO_ID="$(echo "$geo" | json_str id)"
GEO_COUNTRIES="$(echo "$geo" | json_str countries)"
[ -n "$GEO_COUNTRIES" ] || GEO_COUNTRIES="CN,JP,HK,KR,SG,TH,MY,TW,VN,GB,FR,DE,GR,CA,US,MO,LA"
r="$(api /adminPage/geo/addOver --data-urlencode "id=$GEO_ID" -d mode=0 \
  --data-urlencode "countries=$GEO_COUNTRIES" -d enable=false)"
ok "$r" || { fail setup "disable GeoRule: $r"; exit 1; }

apply || { fail setup "apply config"; exit 1; }
docker rm -f "$CLIENT" "$CLIENT_VP" "$CLIENT_B" "$CLIENT_C" "$CLIENT_D" "$CLIENT_E" >/dev/null 2>&1 || true
docker run -d --name "$CLIENT" --network "$NET" --ip "$IP_CLIENT" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
docker run -d --name "$CLIENT_VP" --network "$NET" --ip "$IP_VP" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
docker run -d --name "$CLIENT_B" --network "$NET" --ip "$IP_B" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
docker run -d --name "$CLIENT_C" --network "$NET" --ip "$IP_C" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
docker run -d --name "$CLIENT_D" --network "$NET" --ip "$IP_D" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
docker run -d --name "$CLIENT_E" --network "$NET" --ip "$IP_E" --entrypoint sleep "$NWU_IMAGE" 3600 >/dev/null
# stream mode: the WAF only sees requests after the first decision pull plus the first health probe of the bouncer
sleep 12

# req_from <client container> <path> : "<curl exit> <http code>" for GET <path> on the test site from that container
req_from() {
  out="$(docker exec "$1" curl -g -s -o /dev/null -m 10 -H "Host: ${REQ_HOST:-$HOST}" -w '%{http_code}' "http://nginxwebui$2")"
  echo "$? $out"
}
req_path() { req_from "$CLIENT" "$1"; }
# decisions_of <ip> : number of decisions for <ip>
decisions_of() { cscli decisions list -i "$1" -o raw 2>/dev/null | tail -n +2 | wc -l | tr -d ' '; }
# waf_apply <changes json> : posts the changes, waits up to 40 s for the result, prints its state
waf_apply() {
  r="$(api /adminPage/crowdsecGuard/wafRules/apply --data-urlencode "changes=$1")"
  id="$(echo "$r" | json_str requestId)"
  [ -n "$id" ] || { echo "apply rejected: $r"; return 1; }
  i=0
  while [ "$i" -lt 20 ]; do
    s="$(api "/adminPage/crowdsecGuard/wafRules/status?requestId=$id" | json_str state)"
    [ "$s" = ok ] || [ "$s" = failed ] && { echo "$s"; return 0; }
    i=$((i + 1)); sleep 2
  done
  echo timeout
}
alerts_since() { cscli alerts list --since "$1" -o json 2>/dev/null; }
# count_rule <text> : how many times <text> occurs in all alerts; compared before and after a request, so a request is
# attributed exactly (no time window)
count_rule() { cscli alerts list --limit 0 -o json 2>/dev/null | grep -o "$1" | wc -l | tr -d ' '; }
SQLI='/?id=1%27%20OR%20%271%27%3D%271'

# 1. catalog visible to nginxWebUI
r="$(api /adminPage/crowdsecGuard/wafRules/list)"
if ok "$r" && echo "$r" | grep -q '"available":true' && echo "$r" | grep -q 'crowdsecurity/vpatch-env-access' && echo "$r" | grep -q '"942100"'; then
  pass 1 "rule catalog available through the API"
else
  fail 1 "wafRules/list: $(echo "$r" | head -c 300)"
fi

# 2. baseline: /.env blocked (403); SQLi passes (out-of-band) and raises a 942100 alert
b1="$(req_path /.env)"
req_path "$SQLI" >/dev/null
sleep 8
if [ "$b1" = "0 403" ] && alerts_since 1m | grep -q 'native_rule:942100'; then
  pass 2 "baseline: /.env -> 403, SQLi alert with 942100"
else
  fail 2 "/.env -> $b1; 942100 alert missing"
fi

# 2b. positive control for check 3 (still the default settings): a fresh client that hits two different vpatch rules
# within 60 s is banned
req_from "$CLIENT_VP" /.env >/dev/null
sleep 2
req_from "$CLIENT_VP" /.git/config >/dev/null
sleep 12
d_vp="$(decisions_of "$IP_VP")"
if [ "$d_vp" -ge 1 ]; then
  pass 2b "control: two vpatch rules from $IP_VP within 60 s -> banned ($d_vp decision)"
else
  fail 2b "control: $IP_VP hit /.env and /.git/config but has no decision; alerts: $(cscli alerts list -i "$IP_VP" -o raw 2>&1 | head -c 600)"
fi

# 2c. positive control for check 12 (still the default settings): another fresh client sending 7 SQL injections in a row
# is banned. Host other.test (default server), the same as in check 12, because 942100 is excluded for the test site later.
REQ_HOST=other.test
j=0
while [ "$j" -lt 7 ]; do req_from "$CLIENT_B" "$SQLI" >/dev/null; j=$((j + 1)); sleep 1; done
unset REQ_HOST
sleep 12
d_b="$(decisions_of "$IP_B")"
if [ "$d_b" -ge 1 ]; then
  pass 2c "control: 7 SQL injections from $IP_B with the default settings -> banned ($d_b decision)"
else
  fail 2c "control: $IP_B sent 7 SQL injections but has no decision; alerts: $(cscli alerts list -i "$IP_B" -o raw 2>&1 | head -c 800)"
fi

# 3. vpatch log only: /.env and /.git/config set to log only -> served (the site has only index.html, so 404), repeated
# hits do not ban the client
s="$(waf_apply '[{"scope":"rule","ruleKey":"crowdsecurity/vpatch-env-access","action":"log","excludedServerIds":""},{"scope":"rule","ruleKey":"crowdsecurity/vpatch-git-config","action":"log","excludedServerIds":""}]')"
sleep 3
codes=""
for p in /.env /.git/config /.env /.git/config /.env; do codes="$codes $(req_path "$p" | cut -d' ' -f2)"; sleep 1; done
sleep 10
bans="$(cscli decisions list -i "$IP_CLIENT" -o raw 2>/dev/null | tail -n +2 | wc -l | tr -d ' ')"
if [ "$s" = ok ] && [ "$codes" = " 404 404 404 404 404" ] && [ "$bans" = 0 ]; then
  pass 3 "vpatch log only: codes$codes, no ban for $IP_CLIENT"
else
  fail 3 "apply '$s', codes$codes, decisions $bans"
fi

# 4. the synced WAF event of a /.env request says log only (E-394 sync + E-395 rule settings). The baseline /.env
# request of check 2 is a block event, so look for a /.env event whose action is log.
ev=""
i=0
while [ "$i" -lt 14 ]; do
  ev="$(api "/adminPage/crowdsecGuard/waf/events?ip=$IP_CLIENT&days=1")"
  echo "$ev" | grep -q '"uri":"/\.env"[^}]*"action":"log"' && break
  i=$((i + 1)); sleep 10
done
if echo "$ev" | grep -q '"uri":"/\.env"[^}]*"action":"log"'; then
  pass 4 "synced /.env event has action log"
else
  fail 4 "events: $(echo "$ev" | head -c 600)"
fi

# 5. CRS 942100 excluded for the test site -> the SQLi request no longer raises a 942100 alert (and 9: upper-case Host).
# Positive control: the same SQLi with Host other.test (not excluded, default server) must raise one.
SERVER_ID="$(api /adminPage/crowdsecGuard/servers | json_str id)"
s="$(waf_apply "[{\"scope\":\"rule\",\"ruleKey\":\"942100\",\"action\":\"inherit\",\"excludedServerIds\":\"$SERVER_ID\"}]")"
sleep 3
n0="$(count_rule 'native_rule:942100')"
req_path "$SQLI" >/dev/null
sleep 8
n1="$(count_rule 'native_rule:942100')"
REQ_HOST=E2E.TEST
req_path "$SQLI" >/dev/null
unset REQ_HOST
sleep 8
n2="$(count_rule 'native_rule:942100')"
REQ_HOST=other.test
req_path "$SQLI" >/dev/null
unset REQ_HOST
sleep 8
n3="$(count_rule 'native_rule:942100')"
if [ "$n3" -gt "$n2" ]; then CONTROL=ok; else CONTROL=fail; fi
if [ "$s" = ok ] && [ "$n1" = "$n0" ] && [ "$CONTROL" = ok ]; then
  pass 5 "942100 excluded for $HOST (alerts $n0 -> $n1); control other.test still alerts ($n2 -> $n3)"
else
  fail 5 "apply '$s'; 942100 alerts before $n0, after $HOST $n1, after E2E.TEST $n2, after other.test $n3 (control $CONTROL)"
fi

# 6. a broken config written to the shared volume is rejected with a reason; crowdsec keeps running on the last good
# config (the active file still has the log-only rule) and /.env is served
restarts="$(docker inspect -f '{{.RestartCount}}' "$CS")"
docker exec "$NWU" sh -c "printf 'name: [\n' > $HOME_DIR/crowdsec-waf/custom.yaml && echo 9001 > $HOME_DIR/crowdsec-waf/request"
st=""
i=0
while [ "$i" -lt 20 ]; do
  st="$(api '/adminPage/crowdsecGuard/wafRules/status?requestId=9001' | json_str state)"
  [ "$st" = failed ] && break
  i=$((i + 1)); sleep 2
done
reason="$(api '/adminPage/crowdsecGuard/wafRules/status?requestId=9001' | sed -n 's/.*"message":"\(.*\)","at".*/\1/p')"
if [ "$st" = failed ] && [ -n "$reason" ] && ! echo "$reason" | grep -q 'level=info' && [ "$(docker inspect -f '{{.State.Running}}' "$CS")" = true ] \
  && [ "$(docker inspect -f '{{.RestartCount}}' "$CS")" = "$restarts" ] \
  && docker exec "$CS" grep -q 'vpatch-env-access' /etc/crowdsec/appsec-configs/nwu-custom.yaml \
  && [ "$(req_path /.env)" = "0 404" ]; then
  pass 6 "broken config rejected ($(echo "$reason" | head -c 200)), crowdsec running on the last good config"
else
  fail 6 "state '$st', reason '$reason', /.env $(req_path /.env)"
fi

# 7. restart crowdsec: last good config is applied again. The startup script first resets the active config to the
# default, so wait for all of: LAPI answering, the log-only rule in the active config, /.env served (404).
docker restart "$CS" >/dev/null
i=0
while [ "$i" -lt 60 ]; do
  docker exec "$CS" cscli lapi status >/dev/null 2>&1 \
    && docker exec "$CS" grep -q 'vpatch-env-access' /etc/crowdsec/appsec-configs/nwu-custom.yaml 2>/dev/null \
    && [ "$(req_path /.env)" = "0 404" ] && break
  i=$((i + 1)); sleep 2
done
if [ "$i" -lt 60 ]; then
  pass 7 "last good config back after crowdsec restart (/.env -> 404 after $((i * 2))s of polling)"
else
  fail 7 "config after restart: $(docker exec "$CS" head -20 /etc/crowdsec/appsec-configs/nwu-custom.yaml); /.env $(req_path /.env)"
fi

# 8. operation log
if oplog_has "[CrowdSec WAF] apply"; then
  pass 8 "operation log has the WAF apply"
else
  fail 8 "no [CrowdSec WAF] apply in the operation log"
fi

# 9. the exclusion also applies to a request with an upper-case Host header (measured right after check 5, with the
# positive control of check 5)
if [ "$n2" = "$n1" ] && [ "$CONTROL" = ok ]; then
  pass 9 "942100 excluded for E2E.TEST (upper-case Host) as well (alerts $n1 -> $n2)"
else
  fail 9 "upper-case Host E2E.TEST: 942100 alerts $n1 -> $n2 (control $CONTROL)"
fi

# 10. vpatch disable: vpatch-env-access off -> /.env is served and no vpatch-env-access alert is raised. The positive
# counterpart is check 4: with the rule on "log only" the same request still produces an event.
s="$(waf_apply '[{"scope":"rule","ruleKey":"crowdsecurity/vpatch-env-access","action":"off","excludedServerIds":""}]')"
sleep 3
v0="$(count_rule 'vpatch-env-access')"
code10="$(req_path /.env)"
sleep 8
v1="$(count_rule 'vpatch-env-access')"
if [ "$s" = ok ] && [ "$code10" = "0 404" ] && [ "$v1" = "$v0" ] \
  && docker exec "$CS" grep -q 'RemoveInBandRuleByName("crowdsecurity/vpatch-env-access")' /etc/crowdsec/appsec-configs/nwu-custom.yaml; then
  pass 10 "vpatch-env-access disabled: /.env -> 404, no new vpatch-env-access alert ($v0 -> $v1)"
else
  fail 10 "apply '$s', /.env $code10, vpatch-env-access alerts $v0 -> $v1"
fi

# 11. a vpatch rule of a known vulnerability (CVE-2017-9841, not touched by any check above) excluded for the test site.
# Baseline: the request is blocked. After the exclusion the test site serves it (404, the file does not exist); the same
# request with Host other.test (not excluded) is still blocked. A CVE rule bans its client at once, so the baseline comes
# from its own fresh client and the exclusion request (and its control, in that order) from another one.
PHP_PATH=/vendor/phpunit/phpunit/src/Util/PHP/eval-stdin.php
b11="$(req_from "$CLIENT_D" "$PHP_PATH")"
s="$(waf_apply "[{\"scope\":\"rule\",\"ruleKey\":\"crowdsecurity/vpatch-CVE-2017-9841\",\"action\":\"inherit\",\"excludedServerIds\":\"$SERVER_ID\"}]")"
sleep 3
e11="$(req_from "$CLIENT_E" "$PHP_PATH")"
REQ_HOST=other.test
o11="$(req_from "$CLIENT_E" "$PHP_PATH")"
unset REQ_HOST
if [ "$b11" = "0 403" ] && [ "$s" = ok ] && [ "$e11" = "0 404" ] && [ "$o11" = "0 403" ]; then
  pass 11 "vpatch-CVE-2017-9841 excluded for $HOST: baseline $b11, $HOST $e11, control other.test $o11"
else
  fail 11 "baseline '$b11', apply '$s', $HOST '$e11', other.test '$o11'"
  info "active config:"; docker exec "$CS" cat /etc/crowdsec/appsec-configs/nwu-custom.yaml
  info "crowdsec log:"; docker logs --tail 40 "$CS" 2>&1
  info "nginx error log:"; docker exec "$NWU" sh -c 'tail -20 /var/log/nginx/error.log 2>/dev/null'
fi

# 12. CRS category crs-sqli on "log only": the alerts are still raised, but 7 SQL injections from a fresh client do not
# lead to a decision (control: check 2c, same requests with the default settings)
s="$(waf_apply '[{"scope":"category","ruleKey":"crs-sqli","action":"log","excludedServerIds":""}]')"
sleep 3
a0="$(count_rule 'native_rule:942100')"
REQ_HOST=other.test
j=0
codes12=""
while [ "$j" -lt 7 ]; do codes12="$codes12 $(req_from "$CLIENT_C" "$SQLI" | tr ' ' :)"; j=$((j + 1)); sleep 1; done
unset REQ_HOST
sleep 12
a1="$(count_rule 'native_rule:942100')"
d_c="$(decisions_of "$IP_C")"
if [ "$s" = ok ] && [ "$a1" -gt "$a0" ] && [ "$d_c" = 0 ]; then
  pass 12 "crs-sqli log only: 7 SQL injections from $IP_C raise alerts (942100 occurrences $a0 -> $a1) but no decision"
else
  fail 12 "apply '$s', codes$codes12, alerts $a0 -> $a1, decisions $d_c; alerts: $(cscli alerts list -i "$IP_C" -o raw 2>&1 | head -c 800); decisions: $(cscli decisions list -i "$IP_C" -o raw 2>&1 | head -c 400)"
fi

echo "crowdsec-waf-tuning-e2e: $PASS_N PASS, $FAIL_N FAIL"
[ "$FAIL_N" -eq 0 ]
