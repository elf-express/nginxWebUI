-- Pure policy helpers for nwu_crowdsec. No ngx / cjson dependency so they
-- can be unit tested with plain luajit.
local _M = {}

local function trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

-- Parse "KEY=VALUE" lines. Blank lines and lines starting with '#' are skipped,
-- keys and values are trimmed. Only the first '=' separates key from value.
function _M.parse_conf(text)
  local out = {}
  if type(text) ~= "string" then
    return out
  end
  for line in (text .. "\n"):gmatch("([^\n]*)\n") do
    line = trim(line)
    if line ~= "" and line:sub(1, 1) ~= "#" then
      local k, v = line:match("^([^=]+)=(.*)$")
      if k then
        out[trim(k)] = trim(v)
      end
    end
  end
  return out
end

-- Decide what the access phase should do.
--   "pass"   : allowlisted client, skip every protection layer
--   "paused" : CrowdSec has been unreachable longer than grace seconds -> 503
--   "check"  : run the bouncer checks
-- last_ok == nil means no probe record yet; grace starts at boot so do not block.
function _M.decide(allowlisted, now, last_ok, grace)
  if allowlisted then
    return "pass"
  end
  if last_ok == nil then
    return "check"
  end
  if now - last_ok > grace then
    return "paused"
  end
  return "check"
end

-- A bouncer conf file that does not exist (open failed with ENOENT = 2) means CrowdSec is not
-- configured at all: the wrapper stays inert and traffic passes. Any other open failure
-- (permission denied, unknown error) or a present file is not "missing" and stays fail-closed.
function _M.conf_missing(text, errno)
  return text == nil and errno == 2
end

-- cs.allowIp returns ok=false when the IP is banned.
function _M.is_ban(ok, remediation)
  return ok == false
end

-- Serialize the probe state (epoch seconds, floored) as JSON.
function _M.state_json(last_ok, lapi_ok, appsec_ok, checked_at)
  return string.format(
    '{"lastOk":%d,"lapiOk":%s,"appsecOk":%s,"checkedAt":%d}',
    math.floor(last_ok or 0),
    lapi_ok and "true" or "false",
    appsec_ok and "true" or "false",
    math.floor(checked_at or 0))
end

return _M
