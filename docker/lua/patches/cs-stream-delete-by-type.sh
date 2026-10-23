#!/bin/sh
# Patch for lua-cs-bouncer v1.2.3 plugins/crowdsec/stream.lua (cs-nginx-bouncer release tarball), run at image build.
#
# In stream mode the bouncer keeps one decision per IP (decision_cache/<key> = "<type>/<origin>/<ip type>") and,
# for every entry in "deleted" of /v1/decisions/stream, deletes decision_cache/<key> by IP without looking at the
# type. LAPI lists expired decisions in "deleted", so when the captcha of an IP expires, the ban the IP got later
# (challenge-fail, repeat offender) is deleted with it and the ban ends early. After this patch the cached decision
# is only deleted when its type is the type of the deleted decision (or the deleted decision has no type).
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

# The original block in stream_query_process, deleted decisions:
#       local key, _ = utils.item_to_string(decision.value, decision.scope)
#       if key ~= nil then
#         self:delete(key)
KEY_LINE='^      local key, _ = utils\.item_to_string(decision\.value, decision\.scope)$'
OLD_LINE='^        self:delete(key)$'
NEW_LINE='if cached == nil or type(decision.type) ~= "string" or utils.starts_with(cached, decision.type .. "/") then'

count() { grep -c -e "$1" "$FILE" || true; }
[ "$(count "$KEY_LINE")" = "1" ] || die "expected exactly one '$KEY_LINE' in $FILE, found $(count "$KEY_LINE")"
[ "$(count "$OLD_LINE")" = "1" ] || die "expected exactly one '$OLD_LINE' in $FILE, found $(count "$OLD_LINE")"
grep -A2 -e "$KEY_LINE" "$FILE" | grep -q -e "$OLD_LINE" \
  || die "'self:delete(key)' is not right after the item_to_string line of the deleted decisions in $FILE"

awk -v new="$NEW_LINE" '
  $0 == "        self:delete(key)" {
    print "        local cached = self:get(key)"
    print "        " new
    print "          self:delete(key)"
    print "        end"
    next
  }
  { print }
' "$FILE" > "$FILE.nwu" || die "awk failed on $FILE"
cat "$FILE.nwu" > "$FILE" && rm -f "$FILE.nwu"

[ "$(grep -c -F -e "$NEW_LINE" "$FILE" || true)" = "1" ] || die "patched line not found in $FILE after the patch"
[ "$(count '^        local cached = self:get(key)$')" = "1" ] || die "patched lookup not found in $FILE after the patch"
[ "$(count "$OLD_LINE")" = "0" ] || die "the unconditional delete is still in $FILE after the patch"
echo "cs-stream-delete-by-type: patched $FILE"
