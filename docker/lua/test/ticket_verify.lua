-- Test tool: check a request ticket like the external verification site does and print its fields.
-- Usage: luajit ticket_verify.lua <secret> <token>   -> prints "<ip> <host> <path>" or exits 1
local crypto = require "crypto_ffi"
local p = require "nwu_challenge_policy"

local f, payload, sig = p.parse_ticket(arg[2])
if not f or f.kind ~= "req" or f.exp < os.time()
    or not p.equals(sig, p.b64url_encode(crypto.hmac_sha1(arg[1], payload))) then
  io.stderr:write("invalid request ticket\n")
  os.exit(1)
end
io.write(f.ip .. " " .. f.host .. " " .. f.path)
