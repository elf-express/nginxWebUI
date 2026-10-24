-- Pure helpers for the nwu challenge (no ngx / cjson dependency) so they can be unit tested with plain luajit.
-- Cookie, ticket and ALTCHA salt formats are defined here; docs/crowdsec-challenge.md describes the ticket format.
local bit = require "bit"

local _M = {}

_M.PREFIX = "/.well-known/nwu-challenge/"
_M.COOKIE_NAME = "nwu_pass"
_M.TICKET_VERSION = "nwu1"
-- A response ticket may not claim to live longer than this (seconds from now).
_M.MAX_TICKET_AHEAD = 300

local B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
local B64_INDEX = {}
for i = 1, #B64 do
  B64_INDEX[B64:byte(i)] = i - 1
end

local function char_at(n)
  return B64:sub(n + 1, n + 1)
end

-- base64url without padding
function _M.b64url_encode(s)
  local out = {}
  for i = 1, #s, 3 do
    local a, b, c = s:byte(i, i + 2)
    local n = bit.bor(bit.lshift(a, 16), bit.lshift(b or 0, 8), c or 0)
    out[#out + 1] = char_at(bit.rshift(n, 18))
    out[#out + 1] = char_at(bit.band(bit.rshift(n, 12), 63))
    if b then
      out[#out + 1] = char_at(bit.band(bit.rshift(n, 6), 63))
    end
    if c then
      out[#out + 1] = char_at(bit.band(n, 63))
    end
  end
  return table.concat(out)
end

-- Returns nil for characters outside the base64url alphabet or an impossible length.
function _M.b64url_decode(s)
  if type(s) ~= "string" or #s % 4 == 1 then
    return nil
  end
  local out = {}
  for i = 1, #s, 4 do
    local n, count = 0, 0
    for j = i, math.min(i + 3, #s) do
      local v = B64_INDEX[s:byte(j)]
      if v == nil then
        return nil
      end
      n = bit.bor(bit.lshift(n, 6), v)
      count = count + 1
    end
    n = bit.lshift(n, 6 * (4 - count))
    out[#out + 1] = string.char(bit.band(bit.rshift(n, 16), 255))
    if count > 2 then
      out[#out + 1] = string.char(bit.band(bit.rshift(n, 8), 255))
    end
    if count > 3 then
      out[#out + 1] = string.char(bit.band(n, 255))
    end
  end
  return table.concat(out)
end

-- Constant-time string comparison.
function _M.equals(a, b)
  if type(a) ~= "string" or type(b) ~= "string" or #a ~= #b then
    return false
  end
  local diff = 0
  for i = 1, #a do
    diff = bit.bor(diff, bit.bxor(a:byte(i), b:byte(i)))
  end
  return diff == 0
end

function _M.is_reserved(uri)
  return type(uri) == "string" and uri:sub(1, #_M.PREFIX) == _M.PREFIX
end

-- Only same-site paths are accepted as a return target; anything else becomes "/".
function _M.safe_return(path)
  if type(path) ~= "string" or path == "" or #path > 2048 then
    return "/"
  end
  if path:sub(1, 1) ~= "/" or path:sub(2, 2) == "/" then
    return "/"
  end
  if path:find("[%z\1-\31\127\\]") then
    return "/"
  end
  if _M.is_reserved(path) then
    return "/"
  end
  return path
end

-- Static files on the reserved path (name -> content type): the ALTCHA widget, its styles and its Web Worker from the
-- image, page.js / page.css written with the pages. The pages carry no inline script or style, so the challenge still
-- works on a site that sends Content-Security-Policy "default-src 'self'".
_M.ASSETS = {
  ["altcha.js"] = "application/javascript; charset=utf-8",
  ["altcha.css"] = "text/css; charset=utf-8",
  ["altcha-worker.js"] = "application/javascript; charset=utf-8",
  ["page.js"] = "application/javascript; charset=utf-8",
  ["page.css"] = "text/css; charset=utf-8",
}

-- What a request on the reserved path is: "asset" (second value: the file name), "page", "verify", "callback";
-- nil for anything else.
function _M.reserved_route(method, uri)
  local get = method == "GET" or method == "HEAD"
  if _M.is_reserved(uri) and _M.ASSETS[uri:sub(#_M.PREFIX + 1)] then
    return "asset", uri:sub(#_M.PREFIX + 1)
  end
  if uri == _M.PREFIX and get then
    return "page"
  end
  if uri == _M.PREFIX .. "verify" and method == "POST" then
    return "verify"
  end
  if uri == _M.PREFIX .. "callback" and get then
    return "callback"
  end
  return nil
end

-- Where a client without a captcha decision is sent from the reserved path (route from reserved_route, not asset).
-- r: the return path of the page or the verify form; token: the callback ticket, only its path is used.
function _M.bounce_target(route, r, token)
  if route == "page" or route == "verify" then
    return _M.safe_return(r)
  end
  if route == "callback" then
    local f = _M.parse_ticket(token)
    return _M.safe_return(f and f.path)
  end
  return "/"
end

-- A browser page load: GET/HEAD with an Accept header that asks for HTML.
function _M.is_page_request(method, accept)
  if method ~= "GET" and method ~= "HEAD" then
    return false
  end
  return type(accept) == "string" and accept:lower():find("text/html", 1, true) ~= nil
end

function _M.cookie_payload(exp, ip)
  return "pass\n" .. tostring(exp) .. "\n" .. ip
end

function _M.make_cookie(exp, sig)
  return tostring(exp) .. "." .. sig
end

-- Returns exp (number), sig; or nil.
function _M.parse_cookie(value)
  if type(value) ~= "string" then
    return nil
  end
  local exp, sig = value:match("^(%d+)%.([%w%-_]+)$")
  if not exp then
    return nil
  end
  return tonumber(exp), sig
end

function _M.cookie_header(value, max_age, secure)
  local h = _M.COOKIE_NAME .. "=" .. value .. "; Path=/; Max-Age=" .. max_age .. "; HttpOnly; SameSite=Lax"
  if secure then
    h = h .. "; Secure"
  end
  return h
end

local TICKET_FIELDS = { "kind", "exp", "ip", "host", "path", "nonce" }

-- Canonical payload "nwu1\n<kind>\n<exp>\n<ip>\n<host>\n<path>\n<nonce>".
-- Returns nil when a field is empty or contains a newline.
function _M.ticket_payload(t)
  local parts = { _M.TICKET_VERSION }
  for _, k in ipairs(TICKET_FIELDS) do
    local v = t[k] ~= nil and tostring(t[k]) or ""
    if v == "" or v:find("\n", 1, true) then
      return nil
    end
    parts[#parts + 1] = v
  end
  return table.concat(parts, "\n")
end

function _M.make_ticket(payload, sig)
  return _M.b64url_encode(payload) .. "." .. sig
end

-- Returns fields, payload, sig; or nil, "format".
function _M.parse_ticket(token)
  if type(token) ~= "string" or #token > 4096 then
    return nil, "format"
  end
  local body, sig = token:match("^([%w%-_]+)%.([%w%-_]+)$")
  if not body then
    return nil, "format"
  end
  local payload = _M.b64url_decode(body)
  if not payload then
    return nil, "format"
  end
  local parts = {}
  for p in (payload .. "\n"):gmatch("([^\n]*)\n") do
    parts[#parts + 1] = p
  end
  if #parts ~= 7 or parts[1] ~= _M.TICKET_VERSION or not parts[3]:match("^%d+$") then
    return nil, "format"
  end
  return {
    kind = parts[2], exp = tonumber(parts[3]), ip = parts[4], host = parts[5], path = parts[6], nonce = parts[7],
  }, payload, sig
end

-- Checks a response ticket against the request it arrived on. The signature and the nonce are checked by the caller.
function _M.check_ticket(f, now, ip, host)
  if f.kind ~= "resp" then
    return false, "kind"
  end
  if f.exp < now then
    return false, "expired"
  end
  if f.exp > now + _M.MAX_TICKET_AHEAD then
    return false, "lifetime"
  end
  if f.ip ~= ip then
    return false, "ip"
  end
  if f.host:lower() ~= tostring(host or ""):lower() then
    return false, "host"
  end
  return true
end

-- ALTCHA salt carries the expiry and the client IP so the server can check both without storing the challenge.
function _M.altcha_salt(nonce, exp, ip)
  return nonce .. "?expires=" .. tostring(exp) .. "&ip=" .. ip
end

-- Returns nonce, exp (number), ip; or nil.
function _M.parse_altcha_salt(salt)
  if type(salt) ~= "string" then
    return nil
  end
  local nonce, exp, ip = salt:match("^(%x+)%?expires=(%d+)&ip=([%x%.:]+)$")
  if not nonce then
    return nil
  end
  return nonce, tonumber(exp), ip
end

local ESC = { ["&"] = "&amp;", ["<"] = "&lt;", [">"] = "&gt;", ['"'] = "&quot;", ["'"] = "&#39;" }

function _M.html_escape(s)
  return (tostring(s):gsub("[&<>\"']", ESC))
end

-- Replaces {{KEY}} (upper-case letters) with the HTML-escaped value of vars.KEY; unknown keys become "".
function _M.render(template, vars)
  return (template:gsub("{{(%u+)}}", function(k)
    local v = vars[k]
    if v == nil then
      return ""
    end
    return _M.html_escape(v)
  end))
end

function _M.append_query(url, key, value)
  local sep = url:find("?", 1, true) and "&" or "?"
  return url .. sep .. key .. "=" .. value
end

return _M
