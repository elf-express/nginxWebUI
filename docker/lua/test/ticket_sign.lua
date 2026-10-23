-- Test tool: sign a handoff ticket like the external verification site does.
-- Usage: luajit ticket_sign.lua <secret> <kind> <exp> <ip> <host> <path> <nonce>   -> prints the token
local crypto = require "crypto_ffi"
local p = require "nwu_challenge_policy"

local secret = arg[1]
local payload = assert(p.ticket_payload({
  kind = arg[2], exp = arg[3], ip = arg[4], host = arg[5], path = arg[6], nonce = arg[7],
}), "invalid ticket fields")
io.write(p.make_ticket(payload, p.b64url_encode(crypto.hmac_sha1(secret, payload))))
