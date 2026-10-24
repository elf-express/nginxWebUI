#!/bin/sh
# Exports the installed AppSec rules as the nginxWebUI WAF rule catalog (E-395). Runs at image build time.
#   vpatch.json : [{name, description, label, cves[]}] from /etc/crowdsec/appsec-rules/vpatch-*.yaml
#   crs.tsv     : <id>\t<paranoia level>\t<msg> for CRS rules 911000-944999 (REQUEST-9*.conf, one rule per line)
#   meta.json   : {generatedAt, crowdsec}
set -e
dir=${1:-/nwu/catalog}
crs_dir=${CRS_DIR:-/staging/var/lib/crowdsec/data}
mkdir -p "$dir"
yq ea -o=json -I=0 '[. | select(.name | test("^crowdsecurity/vpatch-")) | {"name": .name, "description": (.description // ""), "label": (.labels.label // ""), "cves": [(.labels.classification // [])[] | select(test("^cve\.")) | sub("^cve\.", "")]}]' /etc/crowdsec/appsec-rules/vpatch-*.yaml > "$dir/vpatch.json.tmp"
awk -v q="'" '
  /id:9(1[1-9]|[2-3][0-9]|4[0-4])[0-9][0-9][0-9]/ {
    if (!match($0, /id:[0-9]+/)) next
    id = substr($0, RSTART + 3, RLENGTH - 3)
    msg = ""; p = index($0, "msg:" q)
    if (p > 0) { rest = substr($0, p + 5); e = index(rest, q); msg = substr(rest, 1, e - 1) }
    pl = ""; if (match($0, /paranoia-level\/[0-9]/)) pl = substr($0, RSTART + 15, 1)
    print id "\t" pl "\t" msg
  }' "$crs_dir"/REQUEST-9*.conf > "$dir/crs.tsv.tmp"
AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)" CS="$(cscli version 2>&1 | sed -n 's/^version: *//p' | head -n 1)" \
  yq -n -o=json -I=0 '.generatedAt = strenv(AT) | .crowdsec = strenv(CS)' > "$dir/meta.json.tmp"
mv "$dir/vpatch.json.tmp" "$dir/vpatch.json"
mv "$dir/crs.tsv.tmp" "$dir/crs.tsv"
mv "$dir/meta.json.tmp" "$dir/meta.json"
