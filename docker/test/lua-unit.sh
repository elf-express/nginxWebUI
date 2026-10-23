#!/bin/sh
# Runs the pure Lua unit tests (docker/lua/test/*_test.lua) with the image's luajit (no nginx).
# Usage: sh docker/test/lua-unit.sh <image>
set -eu
export MSYS_NO_PATHCONV=1
IMAGE="${1:?usage: lua-unit.sh <image>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
LUA_DIR="$(cd "$HERE/../lua" && pwd)"
if command -v cygpath >/dev/null 2>&1; then
  LUA_DIR="$(cygpath -m "$LUA_DIR")"
fi
docker run --rm -v "$LUA_DIR:/l:ro" --entrypoint sh "$IMAGE" -c '
set -eu
for t in /l/test/*_test.lua; do
  luajit -e "package.path=\"/l/?.lua;/l/test/?.lua;\"..package.path; package.cpath=\"/usr/lib/lua/5.1/?.so;\"..package.cpath" "$t"
done'
