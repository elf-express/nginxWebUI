-- nwu challenge: serves the reserved path /.well-known/nwu-challenge/ and decides what a client with a
-- CrowdSec captcha decision gets. Used by nwu_crowdsec when its init opts carry a challenge table.
--   init(opts)  opts = { conf = <challenge conf>, pages = <dir of generated pages, ends with />, altcha = <altcha.js> }
--   enabled(), has_valid_cookie(ip), challenge(ip), handle(ip)
-- Builtin mode: ALTCHA proof of work (SHA-256) on the reserved path, POST verify issues the nwu_pass cookie.
-- External mode: 302 to the external site with a signed request ticket; its response ticket comes back to callback.
-- Plain http sites never get a challenge: they get the 429 page (ALTCHA needs crypto.subtle, i.e. https).
local policy = require "nwu_challenge_policy"
local conf_policy = require "nwu_crowdsec_policy"

local _M = {}

local DICT_NAME = "nwu_challenge"
local MAXNUMBER = 50000
local CHALLENGE_TTL = 300
local REQUEST_TICKET_TTL = 300
local PAGE_FILES = {
  challenge = "challenge.html",
  http = "http-blocked.html",
  verify = "verify-failed.html",
  callback = "callback-failed.html",
}

local cfg = {}
local pages = {}
local altcha_js
local enabled = false
local sha256, to_hex, cjson

local function read_file(path)
  local f = io.open(path, "rb")
  if not f then
    return nil
  end
  local data = f:read("*a")
  f:close()
  return data
end

local function fail_init(msg, detail)
  ngx.log(ngx.ERR, "[nwu_challenge] ", msg, ": ", tostring(detail))
  return false
end

function _M.init(o)
  enabled = false
  o = o or {}
  local text = read_file(o.conf or "")
  if not text then
    return fail_init("cannot read challenge conf, challenge disabled", o.conf)
  end
  cfg = conf_policy.parse_conf(text)
  cfg.TTL_SECONDS = tonumber(cfg.TTL_SECONDS)
  if (cfg.COOKIE_SECRET or "") == "" or not cfg.TTL_SECONDS then
    return fail_init("challenge conf lacks COOKIE_SECRET or TTL_SECONDS", o.conf)
  end
  if cfg.MODE == "external" then
    if (cfg.TICKET_SECRET or "") == "" or (cfg.EXTERNAL_URL or "") == "" then
      return fail_init("external mode needs TICKET_SECRET and EXTERNAL_URL", o.conf)
    end
  elseif cfg.MODE ~= "builtin" then
    return fail_init("unknown MODE", cfg.MODE)
  end
  for key, name in pairs(PAGE_FILES) do
    pages[key] = read_file((o.pages or "") .. name)
    if not pages[key] then
      return fail_init("cannot read page", (o.pages or "") .. name)
    end
  end
  altcha_js = read_file(o.altcha or "")
  if not altcha_js then
    return fail_init("cannot read altcha.js", o.altcha)
  end
  local ok1, sha = pcall(require, "resty.sha256")
  local ok2, str = pcall(require, "resty.string")
  if not ok1 or not ok2 then
    return fail_init("cannot load resty.sha256 / resty.string", ok1 and str or sha)
  end
  sha256, to_hex = sha, str.to_hex
  cjson = require "cjson.safe"
  enabled = true
  ngx.log(ngx.NOTICE, "[nwu_challenge] initialised, mode ", cfg.MODE)
  return true
end

function _M.enabled()
  return enabled
end

local function random_bytes(n)
  local f = assert(io.open("/dev/urandom", "rb"))
  local b = f:read(n)
  f:close()
  return b
end

local function random_hex(n)
  return to_hex(random_bytes(n))
end

local function random_int(max)
  local b1, b2, b3, b4 = random_bytes(4):byte(1, 4)
  return (((b1 * 256 + b2) * 256 + b3) * 256 + b4) % (max + 1)
end

local function sign(key, msg)
  return policy.b64url_encode(ngx.hmac_sha1(key, msg))
end

local function sha256_hex(s)
  local h = sha256:new()
  h:update(s)
  return to_hex(h:final())
end

local function send_page(status, template, vars)
  vars = vars or {}
  vars.IP = ngx.var.remote_addr
  vars.TIME = os.date("%Y-%m-%d %H:%M:%S %z", ngx.time())
  ngx.status = status
  ngx.header["Content-Type"] = "text/html; charset=utf-8"
  ngx.header["Cache-Control"] = "no-store"
  ngx.print(policy.render(template, vars))
  return ngx.exit(ngx.HTTP_OK)
end

local function send_too_many()
  ngx.status = 429
  ngx.header["Content-Type"] = "text/plain; charset=utf-8"
  ngx.header["Cache-Control"] = "no-store"
  ngx.print("429 Too Many Requests\n")
  return ngx.exit(ngx.HTTP_OK)
end

function _M.has_valid_cookie(ip)
  local exp, sig = policy.parse_cookie(ngx.var["cookie_" .. policy.COOKIE_NAME])
  if not exp or exp < ngx.time() then
    return false
  end
  return policy.equals(sig, sign(cfg.COOKIE_SECRET, policy.cookie_payload(exp, ip)))
end

-- Issue the pass cookie and send the client back to its page.
local function pass(ip, path)
  local exp = ngx.time() + cfg.TTL_SECONDS
  local value = policy.make_cookie(exp, sign(cfg.COOKIE_SECRET, policy.cookie_payload(exp, ip)))
  ngx.header["Set-Cookie"] = policy.cookie_header(value, cfg.TTL_SECONDS, ngx.var.scheme == "https")
  ngx.header["Cache-Control"] = "no-store"
  return ngx.redirect(policy.safe_return(path), 303)
end

local function to_external(ip, path)
  local payload = policy.ticket_payload({
    kind = "req", exp = ngx.time() + REQUEST_TICKET_TTL, ip = ip, host = ngx.var.host, path = path,
    nonce = random_hex(16),
  })
  local token = policy.make_ticket(payload, sign(cfg.TICKET_SECRET, payload))
  return ngx.redirect(policy.append_query(cfg.EXTERNAL_URL, "t", token), 302)
end

-- A client with a captcha decision and no valid cookie.
function _M.challenge(ip)
  if ngx.var.scheme ~= "https" then
    return send_page(429, pages.http)
  end
  if not policy.is_page_request(ngx.req.get_method(), ngx.var.http_accept) then
    return send_too_many()
  end
  local path = policy.safe_return(ngx.var.request_uri)
  if cfg.MODE == "external" then
    return to_external(ip, path)
  end
  return ngx.redirect(policy.PREFIX .. "?r=" .. ngx.escape_uri(path), 302)
end

local function show_challenge(ip, path)
  local salt = policy.altcha_salt(random_hex(12), ngx.time() + CHALLENGE_TTL, ip)
  local challenge = sha256_hex(salt .. random_int(MAXNUMBER))
  local json = cjson.encode({
    algorithm = "SHA-256", challenge = challenge, maxnumber = MAXNUMBER, salt = salt,
    signature = sign(cfg.COOKIE_SECRET, "altcha\n" .. salt .. "\n" .. challenge),
  })
  return send_page(200, pages.challenge, { CHALLENGE = json, RETURN = path })
end

-- ALTCHA payload = base64(JSON{algorithm, challenge, number, salt, signature, took}); each challenge works once.
local function verify_altcha(b64, ip)
  local raw = type(b64) == "string" and ngx.decode_base64(b64)
  local data = raw and cjson.decode(raw)
  if type(data) ~= "table" or data.algorithm ~= "SHA-256" or type(data.number) ~= "number"
      or type(data.challenge) ~= "string" then
    return false
  end
  local nonce, exp, salt_ip = policy.parse_altcha_salt(data.salt)
  local now = ngx.time()
  if not nonce or exp < now or salt_ip ~= ip then
    return false
  end
  if not policy.equals(data.signature, sign(cfg.COOKIE_SECRET, "altcha\n" .. data.salt .. "\n" .. data.challenge)) then
    return false
  end
  if data.number < 0 or data.number > MAXNUMBER or data.number % 1 ~= 0 then
    return false
  end
  if sha256_hex(data.salt .. string.format("%d", data.number)) ~= data.challenge then
    return false
  end
  return ngx.shared[DICT_NAME]:add("a:" .. nonce, true, exp - now + 5) == true
end

local function callback(ip)
  local f, payload, sig = policy.parse_ticket(ngx.var.arg_token)
  if not f or not policy.equals(sig, sign(cfg.TICKET_SECRET, payload)) then
    return send_page(429, pages.callback)
  end
  local now = ngx.time()
  if not policy.check_ticket(f, now, ip, ngx.var.host) then
    return send_page(429, pages.callback)
  end
  if ngx.shared[DICT_NAME]:add("t:" .. f.nonce, true, f.exp - now + 5) ~= true then
    return send_page(429, pages.callback)
  end
  return pass(ip, f.path)
end

-- Every request on the reserved path from a client that is not allowlisted and not banned.
function _M.handle(ip)
  local uri = ngx.var.uri
  local method = ngx.req.get_method()
  if uri == policy.PREFIX .. "altcha.js" then
    ngx.header["Content-Type"] = "application/javascript; charset=utf-8"
    ngx.header["Cache-Control"] = "public, max-age=86400"
    ngx.print(altcha_js)
    return ngx.exit(ngx.HTTP_OK)
  end
  if ngx.var.scheme ~= "https" then
    return send_page(429, pages.http)
  end
  if uri == policy.PREFIX and (method == "GET" or method == "HEAD") then
    local path = policy.safe_return(ngx.var.arg_r and ngx.unescape_uri(ngx.var.arg_r))
    if cfg.MODE == "external" then
      return to_external(ip, path)
    end
    return show_challenge(ip, path)
  end
  if uri == policy.PREFIX .. "verify" and method == "POST" and cfg.MODE == "builtin" then
    ngx.req.read_body()
    local args = ngx.req.get_post_args(10)
    if not verify_altcha(args.altcha, ip) then
      return send_page(429, pages.verify)
    end
    return pass(ip, args.r)
  end
  if uri == policy.PREFIX .. "callback" and (method == "GET" or method == "HEAD")
      and cfg.MODE == "external" then
    return callback(ip)
  end
  return ngx.exit(ngx.HTTP_NOT_FOUND)
end

return _M
