#!/bin/sh
# End-to-end check of managing bans from the admin backend with the CrowdSec machine account (E-390, acceptance of E-389).
# Starts a throwaway compose project with the security profile and checks through the nginxWebUI admin API:
#   1. getConfig has no plaintext bouncer key or machine password; both sources are ENV
#   2. status: LAPI, bouncer key and machine login are all ok
#   3. addDecision (IP) shows up in cscli decisions list
#   3b. bans added from the backend carry a scenario starting with "nginxwebui:" (custom reason -> nginxwebui:manual:<reason>,
#       blank reason -> nginxwebui:manual), which profiles.yaml excludes from AbuseIPDB reporting
#   4. deleteDecision (by id) is gone from cscli decisions list
#   5. addRangeDecision shows up; deleteByReason (prefix; listed with the bouncer key, deleted with the JWT) removes it
#   6. ASN revoke path: revokeIntent (reasonTag) deletes by scenario and the decision is gone
#   7. nginxWebUI with a wrong machine password: status machine=invalid, addDecision returns an error and writes nothing
# Nothing of an existing deployment is touched: project nwu-e390-e2e, own container names and volumes, no published
# ports; everything is removed on exit. Takes about 3 minutes.
# Usage: sh docker/test/crowdsec-admin-e2e.sh <nginxwebui image> <nginxwebui-crowdsec image>
set -u
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1
unset COMPOSE_PROFILES COMPOSE_FILE COMPOSE_PROJECT_NAME

NWU_IMAGE="${1:?usage: crowdsec-admin-e2e.sh <nginxwebui image> <crowdsec image>}"
CS_IMAGE="${2:?usage: crowdsec-admin-e2e.sh <nginxwebui image> <crowdsec image>}"

HERE="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$HERE/.." && pwd)"
PROJECT=nwu-e390-e2e
NWU="${PROJECT}-nginxwebui"
CS="${PROJECT}-crowdsec"
ADMIN=admin
CAPTCHA=1234
IP_BAN=192.0.2.10
IP_CUSTOM=192.0.2.11
IP_BLANK=192.0.2.12
RANGE_BAN=198.51.100.0/24
ASN_RANGE=203.0.113.0/24
ASN_TAG=nginxwebui:as-ban:AS64500

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

dc() { docker compose -p "$PROJECT" --env-file "$ENVF" -f "$BASE" -f "$OVR" "$@"; }

cleanup() {
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

# write_env <machine password seen by nginxwebui> - crowdsec always registers MACHINE_PASS
write_env() {
  cat > "$WORK/e2e.env" <<EOF
NGINX_WEBUI_VERSION=e2e
E2E_NWU_IMAGE=$NWU_IMAGE
E2E_CS_IMAGE=$CS_IMAGE
CROWDSEC_BOUNCER_KEY=$KEY
CROWDSEC_MACHINE_PASSWORD=$MACHINE_PASS
E2E_NWU_MACHINE_PASSWORD=$1
ABUSEIPDB_API_KEY=
MCP_TOKEN=
EOF
}

cat > "$WORK/override.yml" <<EOF
services:
  nginxwebui:
    image: \${E2E_NWU_IMAGE}
    container_name: $NWU
    ports: !reset []
    environment:
      CROWDSEC_MACHINE_PASSWORD: \${E2E_NWU_MACHINE_PASSWORD}
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

cscli() { docker exec "$CS" cscli "$@"; }
decision_lines() { cscli decisions list -o raw 2>/dev/null | grep -F "$1"; }
ok() { case "$1" in *'"success":true'*) return 0 ;; esac; return 1; }

# ---------- setup ----------
write_env "$MACHINE_PASS"
echo "== starting stack (project $PROJECT, $NWU_IMAGE + $CS_IMAGE, security profile)"
if ! dc --profile security up -d >"$WORK/up.log" 2>&1; then
  cat "$WORK/up.log"
  fail setup "docker compose up failed"
  exit 1
fi
wait_nwu || { fail setup "nginxwebui not ready"; exit 1; }
i=0
while [ "$i" -lt 60 ]; do
  cscli machines list -o raw 2>/dev/null | grep -q '^nginxwebui,' && break
  i=$((i + 1))
  sleep 2
done
if ! cscli machines list -o raw 2>/dev/null | grep -q '^nginxwebui,'; then
  docker logs "$CS" 2>&1 | tail -30
  fail setup "machine nginxwebui not registered in crowdsec"
  exit 1
fi
login || { fail setup "admin login"; exit 1; }

# ---------- 1. getConfig ----------
cfg="$(api /adminPage/crowdsec/getConfig)"
if ok "$cfg" && ! echo "$cfg" | grep -qF "$KEY" && ! echo "$cfg" | grep -qF "$MACHINE_PASS" \
  && [ "$(echo "$cfg" | grep -o '"source":"ENV"' | wc -l)" -eq 2 ]; then
  pass 1 "getConfig has no plaintext key or password; both sources are ENV"
else
  fail 1 "getConfig: success=$(ok "$cfg" && echo true || echo false), plaintext key=$(echo "$cfg" | grep -cF "$KEY"), plaintext password=$(echo "$cfg" | grep -cF "$MACHINE_PASS")"
fi

# ---------- 2. status ----------
st="$(api /adminPage/crowdsec/status)"
if echo "$st" | grep -q '"lapi":"ok"' && echo "$st" | grep -q '"bouncerKey":"ok"' && echo "$st" | grep -q '"machine":"ok"'; then
  pass 2 "status: lapi, bouncer key and machine login ok"
else
  fail 2 "status: $st"
fi

# ---------- 3. add an IP ban ----------
r="$(api /adminPage/crowdsec/addDecision --data-urlencode "ip=$IP_BAN" -d duration=1h --data-urlencode "reason=nginxwebui:e2e-ip")"
line="$(decision_lines "nginxwebui:e2e-ip")"
if ok "$r" && echo "$line" | grep -qF "$IP_BAN"; then
  pass 3 "addDecision -> cscli decisions list shows $IP_BAN"
else
  fail 3 "addDecision: $r; cscli: $line"
fi

# ---------- 3b. scenario prefix of backend bans (excluded from AbuseIPDB by profiles.yaml) ----------
r1="$(api /adminPage/crowdsec/addDecision --data-urlencode "ip=$IP_CUSTOM" -d duration=1h --data-urlencode "reason=e2e-custom")"
r2="$(api /adminPage/crowdsec/addDecision --data-urlencode "ip=$IP_BLANK" -d duration=1h)"
raw="$(cscli decisions list -o raw 2>/dev/null)"
l1="$(echo "$raw" | grep -F "$IP_CUSTOM")"
l2="$(echo "$raw" | grep -F "$IP_BLANK")"
if ok "$r1" && ok "$r2" && echo "$l1" | grep -qF ",nginxwebui:manual:e2e-custom," && echo "$l2" | grep -qF ",nginxwebui:manual,"; then
  pass 3b "backend bans carry scenario nginxwebui:* (custom reason -> nginxwebui:manual:e2e-custom, blank -> nginxwebui:manual)"
else
  fail 3b "custom: $r1 / $l1; blank: $r2 / $l2"
fi
api /adminPage/crowdsec/deleteByReason --data-urlencode "reasonPrefix=nginxwebui:manual" >/dev/null

# ---------- 4. delete it by id ----------
id="$(echo "$line" | head -1 | cut -d, -f1)"
r="$(api /adminPage/crowdsec/deleteDecision -d "decisionId=$id")"
if ok "$r" && [ -z "$(decision_lines "nginxwebui:e2e-ip")" ]; then
  pass 4 "deleteDecision $id -> gone from cscli decisions list"
else
  fail 4 "deleteDecision $id: $r; cscli: $(decision_lines "nginxwebui:e2e-ip")"
fi

# ---------- 5. range ban, delete by reason prefix ----------
r="$(api /adminPage/crowdsec/addRangeDecision --data-urlencode "range=$RANGE_BAN" -d duration=1h --data-urlencode "reason=nginxwebui:e2e-range")"
before="$(decision_lines "nginxwebui:e2e-range")"
d="$(api /adminPage/crowdsec/deleteByReason --data-urlencode "reasonPrefix=nginxwebui:e2e-range")"
after="$(decision_lines "nginxwebui:e2e-range")"
if ok "$r" && echo "$before" | grep -qF "$RANGE_BAN" && echo "$d" | grep -q '"obj":1' && [ -z "$after" ]; then
  pass 5 "addRangeDecision $RANGE_BAN visible; deleteByReason removed 1"
else
  fail 5 "add: $r; before: $before; delete: $d; after: $after"
fi

# ---------- 6. ASN revoke path (DELETE /v1/decisions?scenario=) ----------
r="$(api /adminPage/crowdsec/addRangeDecision --data-urlencode "range=$ASN_RANGE" -d duration=1h --data-urlencode "reason=$ASN_TAG")"
before="$(decision_lines "$ASN_TAG")"
d="$(api /adminPage/asn/revokeIntent --data-urlencode "reasonTag=$ASN_TAG")"
after="$(decision_lines "$ASN_TAG")"
if ok "$r" && echo "$before" | grep -qF "$ASN_RANGE" && ok "$d" && [ -z "$after" ]; then
  pass 6 "ASN reason tag $ASN_TAG revoked through the scenario filter"
else
  fail 6 "add: $r; before: $before; revoke: $d; after: $after"
fi

# ---------- 7. wrong machine password ----------
echo "== recreating nginxwebui with a wrong machine password (crowdsec untouched)"
write_env "wrong-$MACHINE_PASS"
dc up -d --no-deps nginxwebui >"$WORK/up2.log" 2>&1 || cat "$WORK/up2.log"
wait_nwu && login || fail 7 "nginxwebui not ready / login after recreate"
st="$(api /adminPage/crowdsec/status)"
r="$(api /adminPage/crowdsec/addDecision --data-urlencode "ip=$IP_BAN" -d duration=1h --data-urlencode "reason=nginxwebui:e2e-bad")"
if echo "$st" | grep -q '"machine":"invalid"' && echo "$st" | grep -q '"lapi":"ok"' \
  && echo "$r" | grep -q '"success":false' && [ -z "$(decision_lines "nginxwebui:e2e-bad")" ]; then
  pass 7 "wrong password: status machine=invalid, addDecision rejected, nothing written"
else
  fail 7 "status: $st; addDecision: $r"
fi

echo "crowdsec-admin-e2e: $PASS_N PASS, $FAIL_N FAIL"
[ "$FAIL_N" -eq 0 ]
