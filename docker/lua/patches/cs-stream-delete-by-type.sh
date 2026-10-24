#!/bin/sh
# Patch for lua-cs-bouncer v1.2.3 plugins/crowdsec/stream.lua (cs-nginx-bouncer release tarball), run at image build.
#
# In stream mode the bouncer keeps one decision per IP (decision_cache/<key> = "<type>/<origin>/<ip type>"):
#   1. "deleted" of /v1/decisions/stream (LAPI lists expired decisions there) deletes decision_cache/<key> by IP only.
#      After the patch the cached decision is only deleted when its type is the type of the deleted decision (or the
#      deleted decision has no type), so the expiry of a captcha does not end a later ban of the same IP.
#   2. "new" overwrites decision_cache/<key>. After the patch a new captcha does not overwrite a cached ban, so a
#      captcha issued while a long ban is still active (e.g. older than the 24h lookback of the profiles) keeps the ban.
# Both are whole-line replacements of exact source lines; anything else in stream.lua stays as it is.
#
# Upgrading the bouncer: the build stops here until this patch is checked against the new stream.lua and the
# expected version below is updated.
# Usage: sh cs-stream-delete-by-type.sh <bouncer version> <path to stream.lua>
set -eu

EXPECTED_VERSION=v1.2.3
VERSION="${1:?usage: cs-stream-delete-by-type.sh <bouncer version> <stream.lua>}"
FILE="${2:?usage: cs-stream-delete-by-type.sh <bouncer version> <stream.lua>}"

die() {
  echo "cs-stream-delete-by-type: $*" >&2
  exit 1
}

[ "$VERSION" = "$EXPECTED_VERSION" ] \
  || die "written for cs-nginx-bouncer $EXPECTED_VERSION, got $VERSION; check stream.lua and update this patch"
[ -f "$FILE" ] || die "$FILE not found"
command -v luajit >/dev/null 2>&1 || die "luajit not found, cannot check $FILE after the patch"

# Original lines (whole lines, indentation included). DEL_LINE is in the loop over deleted decisions, right after
# KEY_LINE; SET_LINE is the cache write in the loop over new decisions.
KEY_LINE='      local key, _ = utils.item_to_string(decision.value, decision.scope)'
DEL_LINE='        self:delete(key)'
SET_LINE="          local succ, err, forcible = self:set(key, decision.type .. \"/\" .. decision.origin .. \"/\" .. ip_type, ttl, 0) -- 0 means the it's a true remediation decision"

# Replacements, one line per element separated by newlines.
DEL_NEW='        local cached = self:get(key)
        if cached == nil or type(decision.type) ~= "string" or utils.starts_with(cached, decision.type .. "/") then
          self:delete(key)
        end'
SET_NEW="          local cached = self:get(key)
          local succ, err, forcible = true, nil, false
          if decision.type ~= \"captcha\" or cached == nil or not utils.starts_with(cached, \"ban/\") then
            succ, err, forcible = self:set(key, decision.type .. \"/\" .. decision.origin .. \"/\" .. ip_type, ttl, 0)
          end"

count() { grep -c -x -F -e "$1" "$FILE" || true; }
for line in "$KEY_LINE" "$DEL_LINE" "$SET_LINE"; do
  [ "$(count "$line")" = "1" ] || die "expected exactly one line '$line' in $FILE, found $(count "$line")"
done
grep -x -F -A2 -e "$KEY_LINE" "$FILE" | grep -q -x -F -e "$DEL_LINE" \
  || die "'$DEL_LINE' is not right after '$KEY_LINE' in $FILE"
before=$(wc -l < "$FILE")

DEL_LINE="$DEL_LINE" DEL_NEW="$DEL_NEW" SET_LINE="$SET_LINE" SET_NEW="$SET_NEW" awk '
  $0 == ENVIRON["DEL_LINE"] { print ENVIRON["DEL_NEW"]; del++; next }
  $0 == ENVIRON["SET_LINE"] { print ENVIRON["SET_NEW"]; set++; next }
  { print }
  END { if (del != 1 || set != 1) exit 1 }
' "$FILE" > "$FILE.nwu" || { rm -f "$FILE.nwu"; die "awk did not replace exactly one delete and one set line in $FILE"; }
cat "$FILE.nwu" > "$FILE" && rm -f "$FILE.nwu"

# After the patch: the original lines are gone, each replacement block is there exactly once (checked by its distinct
# lines), the file grew by exactly the added lines and still compiles.
[ "$(count "$DEL_LINE")" = "0" ] || die "the unconditional delete is still in $FILE after the patch"
[ "$(count "$SET_LINE")" = "0" ] || die "the unconditional set is still in $FILE after the patch"
for line in \
  '        local cached = self:get(key)' \
  '        if cached == nil or type(decision.type) ~= "string" or utils.starts_with(cached, decision.type .. "/") then' \
  '          self:delete(key)' \
  '          local cached = self:get(key)' \
  '          local succ, err, forcible = true, nil, false' \
  '          if decision.type ~= "captcha" or cached == nil or not utils.starts_with(cached, "ban/") then' \
  '            succ, err, forcible = self:set(key, decision.type .. "/" .. decision.origin .. "/" .. ip_type, ttl, 0)'; do
  [ "$(count "$line")" = "1" ] || die "patched line '$line' found $(count "$line") times in $FILE after the patch"
done
[ "$(wc -l < "$FILE")" = "$((before + 7))" ] || die "unexpected line count in $FILE after the patch"
luajit -b "$FILE" /dev/null || die "$FILE does not compile after the patch"
echo "cs-stream-delete-by-type: patched $FILE"
