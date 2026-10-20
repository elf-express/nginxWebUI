-- nginxWebUI wrapper around the official CrowdSec lua-cs-bouncer.
-- Provides: init(opts), init_worker(), access().
--   opts = { conf = <bouncer conf path>, grace = <seconds>, state = <state file path>, page = <503 page path> }
-- Decisions: IP ban -> ngx.exit(444); AppSec block -> ngx.exit(403);
-- CrowdSec unreachable longer than grace -> 503 page. Allowlisted clients ($nwu_allowlist = "1") bypass everything.
local policy = require "nwu_crowdsec_policy"

local _M = {}

local USER_AGENT = "nginxwebui-bouncer/1.0"
local PROBE_INTERVAL = 5
local PROBE_TIMEOUT_MS = 2000
local DICT_NAME = "nwu_crowdsec"

local cs            -- the official crowdsec module
local cfg = {}      -- parsed bouncer conf
local opts = {}     -- opts given to init()
local page_html     -- content of the 503 page
local ready = false
local configured = true  -- false when the bouncer conf file does not exist: wrapper is inert, traffic passes

local function read_file(path)
  local f, _, errno = io.open(path, "rb")
  if not f then
    return nil, errno
  end
  local data = f:read("*a")
  f:close()
  return data
end

function _M.init(o)
  opts = o or {}
  opts.grace = tonumber(opts.grace) or 120
  ready = false
  configured = true

  local text, errno = read_file(opts.conf or "")
  if policy.conf_missing(text, errno) then
    configured = false
    ngx.log(ngx.NOTICE, "[nwu_crowdsec] bouncer conf not found, CrowdSec protection is not configured: ", tostring(opts.conf))
    return true
  end
  if not text then
    ngx.log(ngx.ERR, "[nwu_crowdsec] cannot read bouncer conf: ", tostring(opts.conf))
    return false
  end
  cfg = policy.parse_conf(text)
  cfg.API_URL = (cfg.API_URL or ""):gsub("/+$", "")
  cfg.APPSEC_URL = (cfg.APPSEC_URL or ""):gsub("/+$", "")
  page_html = read_file(opts.page or "") or "<h1>503 Protection service paused</h1>"

  local ok, mod = pcall(require, "crowdsec")
  if not ok then
    ngx.log(ngx.ERR, "[nwu_crowdsec] cannot load crowdsec module: ", tostring(mod))
    return false
  end
  cs = mod

  local res, err = cs.init(opts.conf, USER_AGENT)
  if res == nil then
    ngx.log(ngx.ERR, "[nwu_crowdsec] crowdsec init failed: ", tostring(err))
    return false
  end
  if res == "Disabled" then
    ngx.log(ngx.WARN, "[nwu_crowdsec] bouncer is disabled in conf (ENABLED=false)")
    return false
  end

  ready = true
  ngx.log(ngx.NOTICE, "[nwu_crowdsec] initialised")
  return true
end

-- Write the state file atomically (tmp file then rename).
local function write_state(last_ok, lapi_ok, appsec_ok)
  if not opts.state or opts.state == "" then
    return
  end
  local tmp = opts.state .. ".tmp"
  local f, err = io.open(tmp, "wb")
  if not f then
    ngx.log(ngx.WARN, "[nwu_crowdsec] cannot write state file: ", tostring(err))
    return
  end
  f:write(policy.state_json(last_ok, lapi_ok, appsec_ok, ngx.now()))
  f:close()
  local ok, rerr = os.rename(tmp, opts.state)
  if not ok then
    ngx.log(ngx.WARN, "[nwu_crowdsec] cannot rename state file: ", tostring(rerr))
  end
end

local function probe_lapi()
  local http = require "resty.http"
  local httpc = http.new()
  httpc:set_timeout(PROBE_TIMEOUT_MS)
  local res = httpc:request_uri(cfg.API_URL .. "/v1/decisions?ip=127.0.0.1", {
    method = "GET",
    headers = { ["X-Api-Key"] = cfg.API_KEY },
    ssl_verify = cfg.SSL_VERIFY ~= "false",
  })
  return res ~= nil and res.status == 200
end

local function probe_appsec()
  local http = require "resty.http"
  local httpc = http.new()
  httpc:set_timeout(PROBE_TIMEOUT_MS)
  local res = httpc:request_uri(cfg.APPSEC_URL .. "/", {
    method = "GET",
    headers = {
      ["X-Crowdsec-Appsec-Ip"] = "127.0.0.1",
      ["X-Crowdsec-Appsec-Uri"] = "/",
      ["X-Crowdsec-Appsec-Host"] = "localhost",
      ["X-Crowdsec-Appsec-Verb"] = "GET",
      ["X-Crowdsec-Appsec-Api-Key"] = cfg.API_KEY,
    },
    ssl_verify = cfg.SSL_VERIFY ~= "false",
  })
  return res ~= nil and (res.status == 200 or res.status == 403)
end

local function probe(premature)
  if premature then
    return
  end
  local dict = ngx.shared[DICT_NAME]
  local lapi_ok = false
  local appsec_ok = true
  local ok, r = pcall(probe_lapi)
  lapi_ok = ok and r or false
  if cfg.APPSEC_URL and cfg.APPSEC_URL ~= "" then
    local ok2, r2 = pcall(probe_appsec)
    appsec_ok = ok2 and r2 or false
  end
  if lapi_ok and appsec_ok then
    dict:set("last_ok", ngx.now())
  end
  write_state(dict:get("last_ok"), lapi_ok, appsec_ok)
end

function _M.init_worker()
  if not configured or not ready then
    return
  end
  if cs.get_mode() ~= "live" then
    cs.SetupStream()
  end
  if ngx.worker.id() == 0 then
    cs.SetupMetrics()
    -- graceStartsAtBoot: the grace window starts when nginx starts, not at the first successful probe.
    ngx.shared[DICT_NAME]:add("last_ok", ngx.now())
    local ok, err = ngx.timer.every(PROBE_INTERVAL, probe)
    if not ok then
      ngx.log(ngx.ERR, "[nwu_crowdsec] cannot start probe timer: ", tostring(err))
    end
  end
end

local function send_paused()
  ngx.status = 503
  ngx.header["Content-Type"] = "text/html; charset=utf-8"
  ngx.header["Retry-After"] = "30"
  ngx.print(page_html)
  return ngx.exit(ngx.HTTP_OK)
end

function _M.access()
  if not configured then
    return
  end
  local allowlisted = ngx.var.nwu_allowlist == "1"
  if allowlisted then
    return
  end
  if not ready then
    return send_paused()
  end
  local last_ok = ngx.shared[DICT_NAME]:get("last_ok")
  if policy.decide(false, ngx.now(), last_ok, opts.grace) == "paused" then
    return send_paused()
  end

  local ip = ngx.var.remote_addr
  local ok, remediation = cs.allowIp(ip)
  if policy.is_ban(ok, remediation) then
    return ngx.exit(444)
  end

  if cfg.APPSEC_URL and cfg.APPSEC_URL ~= "" then
    local allowed = cs.AppSecCheck(ip)
    if allowed == false then
      return ngx.exit(403)
    end
  end
end

return _M
