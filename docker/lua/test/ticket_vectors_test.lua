-- The ticket format must match docker/lua/test/ticket-vectors.json byte for byte (shared with the E-396 Worker tests).
-- The file holds fields, payload and secret only; the signed token is built here at run time.
-- Runs under plain luajit; HMAC-SHA1 comes from libcrypto through FFI (inside nginx it is ngx.hmac_sha1).
local p = require "nwu_challenge_policy"
local crypto = require "crypto_ffi"
local cjson = require "cjson"

-- RFC 2202 test case 2 pins the HMAC-SHA1 used below to the standard.
assert(crypto.hex(crypto.hmac_sha1("Jefe", "what do ya want for nothing?"))
  == "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79", "HMAC-SHA1 (RFC 2202 case 2)")

local here = debug.getinfo(1, "S").source:match("^@(.*/)") or "./"
local f = assert(io.open(here .. "ticket-vectors.json", "rb"))
local data = cjson.decode(f:read("*a"))
f:close()

local n = 0
for _, v in ipairs(data.vectors) do
  local payload = p.ticket_payload(v.fields)
  assert(payload == v.payload, v.name .. ": payload")
  local expected = p.b64url_encode(v.payload) .. "." .. p.b64url_encode(crypto.hmac_sha1(data.secret, v.payload))
  local token = p.make_ticket(payload, p.b64url_encode(crypto.hmac_sha1(data.secret, payload)))
  assert(token == expected, v.name .. ": token " .. token)
  local fields, parsed, sig = p.parse_ticket(expected)
  assert(fields and parsed == v.payload, v.name .. ": parse")
  for k, value in pairs(v.fields) do
    assert(tostring(fields[k]) == tostring(value), v.name .. ": field " .. k)
  end
  assert(p.equals(sig, p.b64url_encode(crypto.hmac_sha1(data.secret, parsed))), v.name .. ": signature")
  n = n + 1
end
print("ticket vector tests: " .. n .. " passed")
