#!/bin/sh
# Offline test: the CrowdSec image must start with no network and already carry the AppSec WAF.
# Starts the container with --network none and checks that
#   - LAPI comes up (cscli lapi status succeeds, up to 90s)
#   - all required collections are installed (nginx, http-cve, base-http-scenarios, appsec-virtual-patching, appsec-crs)
#   - the AppSec listener is on 7422
#   - the startup log has no hub download / install errors
# and, statically, that /etc/crowdsec/profiles.yaml in the image keeps repeat_offender_ban first and
# challenge_fail_ban (nginxwebui/challenge-fail, exactly one 4h ban, no notifications, on_success: break) before
# default_ip_remediation.
# Usage: sh docker/test/crowdsec-offline.sh <image>
set -eu
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1

IMAGE="${1:?usage: crowdsec-offline.sh <image>}"
NAME="crowdsec-offline-test-$$"
FAILED=0

cleanup() {
  docker rm -f -v "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

fail() {
  echo "FAIL: $1"
  FAILED=1
}

# ---------- profiles.yaml order and the challenge-fail profile (static) ----------
profiles="$(docker run --rm --entrypoint cat "$IMAGE" /etc/crowdsec/profiles.yaml 2>&1)" || profiles=""
names="$(echo "$profiles" | sed -n 's/^name: *//p' | tr '\n' ' ')"
pos() { echo "$names" | tr ' ' '\n' | grep -n -x "$1" | cut -d: -f1 | head -1; }
first="$(echo "$names" | cut -d' ' -f1)"
p_cf="$(pos challenge_fail_ban)"
p_def="$(pos default_ip_remediation)"
# the whole document (between --- lines) that is named challenge_fail_ban
cf_block="$(echo "$profiles" | awk '
  function flush() { if (b ~ /\nname: challenge_fail_ban\n/) printf "%s", b; b = "\n" }
  BEGIN { b = "\n" }
  /^---/ { flush(); next }
  { b = b $0 "\n" }
  END { flush() }')"
if [ "$first" != "repeat_offender_ban" ]; then
  fail "profiles.yaml: first profile is [$first], want repeat_offender_ban (profiles: $names)"
elif [ -z "$p_cf" ] || [ -z "$p_def" ] || [ "$p_cf" -ge "$p_def" ]; then
  fail "profiles.yaml: challenge_fail_ban must come before default_ip_remediation (profiles: $names)"
elif ! echo "$cf_block" | grep -q '^ - .*Alert.GetScenario() startsWith "nginxwebui/challenge-fail"'; then
  fail "profiles.yaml: challenge_fail_ban filter does not match nginxwebui/challenge-fail"
elif [ "$(echo "$cf_block" | grep -c '^ - type:')" != "1" ] || ! echo "$cf_block" | grep -q '^ - type: ban$' \
  || [ "$(echo "$cf_block" | grep -c '^   duration:')" != "1" ] || ! echo "$cf_block" | grep -q '^   duration: 4h$'; then
  fail "profiles.yaml: challenge_fail_ban decisions must be exactly one ban for 4h: $(echo "$cf_block" | grep -E '^ - type:|^   duration:' | tr '\n' ' ')"
elif echo "$cf_block" | grep -q '^notifications:'; then
  fail "profiles.yaml: challenge_fail_ban must not have notifications"
elif ! echo "$cf_block" | grep -q '^on_success: break$'; then
  fail "profiles.yaml: challenge_fail_ban must end with on_success: break"
else
  echo "ok: profiles order ($names), challenge_fail_ban: only a 4h ban, no notifications (no AbuseIPDB report), break"
fi

# An anonymous volume on the data dir: the official start script refuses to run without one,
# and it is also what turns on the (online) hub upgrade path unless NO_HUB_UPGRADE is set.
# DISABLE_ONLINE_API: the start script registers with the CrowdSec central API (api.crowdsec.net) on first start,
# which cannot work without a network; that is a runtime step, unrelated to the hub content being baked in.
docker run -d --name "$NAME" --network none -e DISABLE_ONLINE_API=true -v /var/lib/crowdsec/data "$IMAGE" >/dev/null

ready=0
i=0
while [ "$i" -lt 45 ]; do
  if docker exec "$NAME" cscli lapi status >/dev/null 2>&1; then
    ready=1
    break
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]; then
    break
  fi
  i=$((i + 1))
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  fail "cscli lapi status did not succeed within 90s (or container exited)"
  docker logs "$NAME" 2>&1 | tail -40
  exit 1
fi
echo "ok: LAPI is up"

collections="$(docker exec "$NAME" cscli collections list -o raw 2>&1 || true)"
for c in crowdsecurity/nginx crowdsecurity/http-cve crowdsecurity/base-http-scenarios \
         crowdsecurity/appsec-virtual-patching crowdsecurity/appsec-crs; do
  if echo "$collections" | grep -q "^$c,"; then
    echo "ok: collection $c"
  else
    fail "collection $c is not installed"
  fi
done

listening=0
i=0
while [ "$i" -lt 15 ]; do
  if docker exec "$NAME" sh -c 'netstat -ln 2>/dev/null | grep -q ":7422 "'; then
    listening=1
    break
  fi
  i=$((i + 1))
  sleep 2
done
if [ "$listening" -eq 1 ]; then
  echo "ok: AppSec listens on 7422"
else
  fail "nothing is listening on 7422 inside the container"
fi

logs="$(docker logs "$NAME" 2>&1)"
if echo "$logs" | grep -Eiq 'Running hub (update|upgrade)|Failed to (install|upgrade|update|download)|unable to retrieve latest|cannot download|could not download|failed to download'; then
  fail "startup log contains hub download errors or tries to update the hub:"
  echo "$logs" | grep -Ei 'Running hub (update|upgrade)|Failed to (install|upgrade|update|download)|unable to retrieve latest|cannot download|could not download|failed to download' | head -20
else
  echo "ok: no hub download errors in startup log"
fi

if [ "$FAILED" -ne 0 ]; then
  echo "crowdsec-offline: FAILED"
  exit 1
fi
echo "crowdsec-offline: PASSED"
