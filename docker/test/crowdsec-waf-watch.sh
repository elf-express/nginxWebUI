#!/bin/sh
# WAF rule tuning watcher check (E-395): one throwaway crowdsec container with its own volume for /nwu-waf.
#   1. the rule catalog is copied to /nwu-waf/catalog (vpatch.json >= 100 rules, crs.tsv, meta.json)
#   2. the active config equals docker/crowdsec/nwu-custom.yaml and appsec.yaml loads nginxwebui/custom
#   3. a valid custom.yaml + request -> result ok, last-good.yaml written, active config replaced
#   4. a broken custom.yaml + request -> result failed, crowdsec still running, active config = last good
#   5. container restart -> starts with the default, then the watcher applies last-good.yaml again
#   6. custom.yaml replaced by a symlink to /etc/passwd + a new request -> result failed (symlink), nothing from /etc/passwd
#      in the message, the active config unchanged
#   7. result.json replaced by a symlink to /tmp/nwu-victim, then a valid apply -> /tmp/nwu-victim untouched, result.json is
#      a regular file with status ok
# Usage: sh docker/test/crowdsec-waf-watch.sh <nginxwebui-crowdsec image>
set -u
export MSYS_NO_PATHCONV=1
CS_IMAGE="${1:?usage: crowdsec-waf-watch.sh <crowdsec image>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
NAME=nwu-e395-watch
VOL=nwu-e395-watch-waf
PASS_N=0
FAIL_N=0
pass() { PASS_N=$((PASS_N + 1)); echo "PASS [$1] $2"; }
fail() { FAIL_N=$((FAIL_N + 1)); echo "FAIL [$1] $2"; }
cleanup() { docker rm -f -v "$NAME" >/dev/null 2>&1 || true; docker volume rm "$VOL" >/dev/null 2>&1 || true; }
trap cleanup EXIT
trap 'exit 130' INT TERM
x() { docker exec "$NAME" sh -c "$1"; }
# wait_result <id> : waits up to 40s for result.json with that id, prints its status
wait_result() {
  i=0
  while [ "$i" -lt 20 ]; do
    r="$(x 'cat /nwu-waf/result.json 2>/dev/null' || true)"
    case "$r" in *"\"id\":\"$1\""*) echo "$r" | sed -n 's/.*"status":"\([a-z]*\)".*/\1/p'; return 0 ;; esac
    i=$((i + 1)); sleep 2
  done
  echo timeout
}
wait_ready() {
  i=0
  while [ "$i" -lt 60 ]; do x 'cscli lapi status' >/dev/null 2>&1 && return 0; i=$((i + 1)); sleep 2; done
  docker logs "$NAME" 2>&1 | tail -30; return 1
}

cleanup
docker volume create "$VOL" >/dev/null
docker run -d --name "$NAME" -e DISABLE_ONLINE_API=true -e NWU_WAF_POLL=1 -v "$VOL:/nwu-waf" -v /var/lib/crowdsec/data "$CS_IMAGE" >/dev/null
wait_ready || { fail setup "crowdsec not ready"; exit 1; }

n="$(x 'yq -p=json ". | length" /nwu-waf/catalog/vpatch.json' 2>/dev/null || echo 0)"
if [ "${n:-0}" -ge 100 ] && x 'test -s /nwu-waf/catalog/crs.tsv && test -s /nwu-waf/catalog/meta.json'; then
  pass 1 "catalog copied ($n vpatch rules)"
else
  fail 1 "catalog: vpatch rules '$n'"
fi

if docker exec "$NAME" cat /etc/crowdsec/appsec-configs/nwu-custom.yaml | tr -d '\r' | cmp -s - "$HERE/../crowdsec/nwu-custom.yaml" \
  && x 'grep -q "nginxwebui/custom" /etc/crowdsec/acquis.d/appsec.yaml'; then
  pass 2 "default active config and appsec.yaml"
else
  fail 2 "active config differs from docker/crowdsec/nwu-custom.yaml or appsec.yaml does not load it"
fi

x 'sed "s#^outofband_rules:#on_load:\n - apply:\n   - RemoveInBandRuleByName(\"crowdsecurity/vpatch-git-config\")\noutofband_rules:#" /etc/crowdsec/appsec-configs/nwu-custom.yaml > /nwu-waf/custom.yaml && echo 1001 > /nwu-waf/request'
s3="$(wait_result 1001)"
if [ "$s3" = ok ] && x 'grep -q vpatch-git-config /etc/crowdsec/appsec-configs/nwu-custom.yaml && cmp -s /nwu-waf/last-good.yaml /etc/crowdsec/appsec-configs/nwu-custom.yaml'; then
  pass 3 "valid config applied"
else
  fail 3 "status '$s3'"
fi

restarts0="$(docker inspect -f '{{.RestartCount}}' "$NAME")"
x 'printf "name: nginxwebui/custom\ninband_rules: [\n" > /nwu-waf/custom.yaml && echo 1002 > /nwu-waf/request'
s4="$(wait_result 1002)"
running="$(docker inspect -f '{{.State.Running}}' "$NAME")"
reason4="$(x 'yq -p=json ".message" /nwu-waf/result.json' 2>/dev/null)"
if [ "$s4" = failed ] && [ -n "$reason4" ] && [ "$reason4" != null ] && ! echo "$reason4" | grep -q 'level=info' && [ "$running" = true ] && [ "$(docker inspect -f '{{.RestartCount}}' "$NAME")" = "$restarts0" ] \
  && x 'cmp -s /nwu-waf/last-good.yaml /etc/crowdsec/appsec-configs/nwu-custom.yaml'; then
  pass 4 "broken config rejected ($(echo "$reason4" | head -c 160)), crowdsec kept running on the last good config"
else
  fail 4 "status '$s4', running '$running', message '$reason4'"
fi

docker restart "$NAME" >/dev/null
wait_ready || { fail 5 "crowdsec not ready after restart"; exit 1; }
i=0
while [ "$i" -lt 20 ]; do x 'grep -q vpatch-git-config /etc/crowdsec/appsec-configs/nwu-custom.yaml' && break; i=$((i + 1)); sleep 2; done
if x 'cmp -s /nwu-waf/last-good.yaml /etc/crowdsec/appsec-configs/nwu-custom.yaml'; then
  pass 5 "last good config applied again after restart"
else
  fail 5 "active config after restart is not last-good.yaml"
fi

x 'cp /etc/crowdsec/appsec-configs/nwu-custom.yaml /tmp/nwu-before.yaml; rm -f /nwu-waf/custom.yaml; ln -s /etc/passwd /nwu-waf/custom.yaml; echo 1006 > /nwu-waf/request'
s6="$(wait_result 1006)"
msg6="$(x 'cat /nwu-waf/result.json' 2>/dev/null)"
if [ "$s6" = failed ] && echo "$msg6" | grep -qi symlink && ! echo "$msg6" | grep -q 'root:' \
  && x 'cmp -s /tmp/nwu-before.yaml /etc/crowdsec/appsec-configs/nwu-custom.yaml'; then
  pass 6 "symlinked custom.yaml refused ($(echo "$msg6" | head -c 160))"
else
  fail 6 "status '$s6', result '$msg6'"
fi

x 'echo untouched > /tmp/nwu-victim; rm -f /nwu-waf/result.json /nwu-waf/custom.yaml; ln -s /tmp/nwu-victim /nwu-waf/result.json
sed "s#^outofband_rules:#on_load:\n - apply:\n   - RemoveInBandRuleByName(\"crowdsecurity/vpatch-env-access\")\noutofband_rules:#" /nwu/nwu-custom.default.yaml > /nwu-waf/custom.yaml && echo 1007 > /nwu-waf/request'
s7="$(wait_result 1007)"
victim="$(x 'cat /tmp/nwu-victim')"
if [ "$s7" = ok ] && [ "$victim" = untouched ] && x 'test -f /nwu-waf/result.json && ! test -L /nwu-waf/result.json'; then
  pass 7 "symlinked result.json replaced, not followed (victim '$victim', apply $s7)"
else
  fail 7 "status '$s7', victim '$victim'"
fi

echo "== $PASS_N passed, $FAIL_N failed"
[ "$FAIL_N" -eq 0 ]
