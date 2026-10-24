-- Unit tests for nwu_crowdsec_policy (pure Lua, no ngx / cjson needed).
local p = require "nwu_crowdsec_policy"
local n = 0
local function check(cond, msg)
  n = n + 1
  assert(cond, msg or ("assertion " .. n .. " failed"))
end

-- parse_conf: KEY=VALUE, skip blank lines and # comments, trim whitespace
local c = p.parse_conf("API_URL=http://crowdsec:8080\n# x\n API_KEY = abc \n\n")
check(c.API_URL == "http://crowdsec:8080" and c.API_KEY == "abc")
check(c["# x"] == nil)
local c2 = p.parse_conf("A=b=c\r\nEMPTY=\n  # indented comment\nnoequals\n")
check(c2.A == "b=c", "value may contain '='")
check(c2.EMPTY == "", "empty value kept as empty string")
check(next(p.parse_conf("")) == nil)
check(next(p.parse_conf(nil)) == nil)

-- decide(allowlisted, now, last_ok, grace)
check(p.decide(true, 1000, 0, 120) == "pass")      -- allowlist always passes, even past grace
check(p.decide(false, 1000, 900, 120) == "check")   -- within grace
check(p.decide(false, 1000, 879, 120) == "paused")  -- beyond grace
check(p.decide(false, 1000, 880, 120) == "check")   -- exactly at grace boundary
check(p.decide(false, 1000, nil, 120) == "check")   -- graceStartsAtBoot: no record yet, do not block

-- remediation(ok, remediation)
check(p.remediation(true, nil) == "pass")
check(p.remediation(nil, nil) == "pass", "lookup error is not a ban")
check(p.remediation(false, "ban") == "ban")
check(p.remediation(false, "captcha") == "captcha")
check(p.remediation(false, "whitelist") == "allow")
check(p.remediation(false, "throttle") == "ban", "unknown decision types are bans")
check(p.remediation(false, nil) == "ban")

-- state_json(last_ok, lapi_ok, appsec_ok, checked_at)
check(p.state_json(1, true, false, 2) == '{"lastOk":1,"lapiOk":true,"appsecOk":false,"checkedAt":2}')
check(p.state_json(1.9, false, true, 2.2) == '{"lastOk":1,"lapiOk":false,"appsecOk":true,"checkedAt":2}',
  "epoch seconds are floored to integers")
check(p.state_json(nil, false, false, 5) == '{"lastOk":0,"lapiOk":false,"appsecOk":false,"checkedAt":5}')

-- conf_missing(text, errno): only a file that does not exist (ENOENT = 2) means "not configured";
-- unreadable or empty-but-present files are real failures and stay fail-closed.
check(p.conf_missing(nil, 2) == true, "absent conf = not configured")
check(p.conf_missing(nil, 13) == false, "permission denied is not a missing file")
check(p.conf_missing(nil, nil) == false, "unknown open failure is not a missing file")
check(p.conf_missing("API_KEY=x", nil) == false, "present conf is configured")
check(p.conf_missing("", nil) == false, "empty present conf is not missing")

print("policy tests: " .. n .. " passed")
