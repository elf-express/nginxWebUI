#!/bin/sh
# crowdsec image 的 machine 帳號註冊檢查（E-390）：
#   1. secrets 檔 /run/secrets/crowdsec_machine_password → 註冊 machine nginxwebui，該密碼可登入 LAPI，錯的密碼不行
#   2. 重啟容器後仍可登入（每次啟動都以 --force 重設，可重複執行）；log 不含密碼
#   3. 只有環境變數 AGENT_PASSWORD（沒有 AGENT_USERNAME）時也會註冊 nginxwebui
#   4. 沒有密碼時不註冊 nginxwebui
#   5. profiles.yaml（Ip、Range 兩個 profile）排除 nginxwebui: 開頭的 scenario，後台封鎖不送 AbuseIPDB
#   6. 以該 profiles 啟動後 LAPI 正常回應、log 沒有 profile 錯誤（profile 的 expr 編譯通過）
# 容器以 --network none 啟動，用 image 內的 busybox wget 在容器內呼叫 LAPI。
# Usage: sh docker/test/crowdsec-machine.sh <crowdsec image>
set -u
# Git Bash on Windows would otherwise rewrite the container paths below.
export MSYS_NO_PATHCONV=1

IMAGE="${1:?usage: crowdsec-machine.sh <crowdsec image>}"
PREFIX="crowdsec-machine-test-$$"
WORK="$(mktemp -d)"
FAILED=0
MACHINE_PASS="e2e-machine-$(od -An -tx1 -N12 /dev/urandom | tr -d ' \n')"

native() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}

cleanup() {
  for n in file env none; do
    docker rm -f -v "$PREFIX-$n" >/dev/null 2>&1 || true
  done
  rm -rf "$WORK"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

pass() { echo "ok: $1"; }
fail() {
  echo "FAIL: $1"
  FAILED=1
}

wait_lapi() {
  i=0
  while [ "$i" -lt 45 ]; do
    docker exec "$1" cscli lapi status >/dev/null 2>&1 && return 0
    [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ] || break
    i=$((i + 1))
    sleep 2
  done
  # Mask the test password in case a failing run echoes it.
  docker logs "$1" 2>&1 | tail -30 | sed "s/$MACHINE_PASS/<masked>/g"
  return 1
}

# start <name> [docker run args...] — an anonymous data volume is required by the official start script
start() {
  name="$PREFIX-$1"
  shift
  docker run -d --name "$name" --network none -e DISABLE_ONLINE_API=true -v /var/lib/crowdsec/data "$@" "$IMAGE" >/dev/null
  wait_lapi "$name"
}

registered() { docker exec "$1" cscli machines list -o raw 2>/dev/null | grep -q '^nginxwebui,'; }

# login <container> <password> : prints the LAPI login response (empty on HTTP error).
# LAPI answers 401 to any request whose User-Agent is not crowdsec/<version>, so the probe has to send one.
login() {
  docker exec "$1" busybox wget -q -O - -U crowdsec/v1.8.1 --header 'Content-Type: application/json' \
    --post-data "{\"machine_id\":\"nginxwebui\",\"password\":\"$2\"}" \
    http://127.0.0.1:8080/v1/watchers/login 2>/dev/null
}

# ---------- 5. profiles baked into the image ----------
EXCLUDE='not (Alert.GetScenario() startsWith "nginxwebui:")'
PROFILES="$(docker run --rm --entrypoint cat "$IMAGE" /etc/crowdsec/profiles.yaml 2>&1)"
for scope in Ip Range; do
  if printf '%s\n' "$PROFILES" | grep -F "Alert.GetScope() == \"$scope\"" | grep -qF "$EXCLUDE"; then
    pass "profiles: the $scope profile filter excludes nginxwebui: scenarios"
  else
    fail "profiles: the $scope profile filter does not exclude nginxwebui: scenarios"
  fi
done

# ---------- 1. secrets file ----------
mkdir -p "$WORK/secrets"
printf '%s\n' "$MACHINE_PASS" > "$WORK/secrets/crowdsec_machine_password"
C="$PREFIX-file"
if ! start file -v "$(native "$WORK/secrets"):/run/secrets:ro"; then
  fail "container with the secrets file did not start"
  exit 1
fi
if registered "$C"; then pass "secrets file: machine nginxwebui registered"; else fail "secrets file: machine nginxwebui not registered"; fi
case "$(login "$C" "$MACHINE_PASS")" in
*'"token"'*) pass "secrets file: login with the password returns a token" ;;
*) fail "secrets file: login with the password failed" ;;
esac
case "$(login "$C" "wrong-$MACHINE_PASS")" in
*'"token"'*) fail "a wrong password still logs in" ;;
*) pass "a wrong password is rejected" ;;
esac

# ---------- 6. LAPI up with the profiles ----------
if docker exec "$C" cscli lapi status >/dev/null 2>&1; then
  pass "profiles: LAPI answers with the image profiles loaded"
else
  fail "profiles: LAPI does not answer"
fi
if docker logs "$C" 2>&1 | grep -iE 'profile.*(error|fatal|invalid|unable|failed)|(error|fatal|invalid|unable|failed).*profile' >/dev/null; then
  fail "profiles: the container log reports a profile error"
else
  pass "profiles: the container log has no profile error"
fi

# ---------- 2. restart ----------
docker restart "$C" >/dev/null
if wait_lapi "$C"; then
  case "$(login "$C" "$MACHINE_PASS")" in
  *'"token"'*) pass "after restart: login still works" ;;
  *) fail "after restart: login failed" ;;
  esac
else
  fail "container did not come back after restart"
fi
if docker logs "$C" 2>&1 | grep -qF "$MACHINE_PASS"; then fail "container log contains the machine password"; else pass "container log has no machine password"; fi

# ---------- 3. env only ----------
C="$PREFIX-env"
if start env -e "AGENT_PASSWORD=$MACHINE_PASS"; then
  if registered "$C"; then pass "env: machine nginxwebui registered"; else fail "env: machine nginxwebui not registered"; fi
  case "$(login "$C" "$MACHINE_PASS")" in
  *'"token"'*) pass "env: login works" ;;
  *) fail "env: login failed" ;;
  esac
else
  fail "container with AGENT_PASSWORD did not start"
fi

# ---------- 4. no password ----------
C="$PREFIX-none"
if start none; then
  if registered "$C"; then fail "no password: machine nginxwebui registered anyway"; else pass "no password: machine nginxwebui not registered"; fi
else
  fail "container without a password did not start"
fi

if [ "$FAILED" -ne 0 ]; then
  echo "crowdsec-machine: FAILED"
  exit 1
fi
echo "crowdsec-machine: PASSED"
