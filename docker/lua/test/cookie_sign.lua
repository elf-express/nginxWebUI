-- Test tool: sign an nwu_pass cookie like the builtin challenge does.
-- Usage: luajit cookie_sign.lua <secret> <exp> <ip>   -> prints the cookie value
local crypto = require "crypto_ffi"
local p = require "nwu_challenge_policy"

local secret, exp, ip = arg[1], arg[2], arg[3]
assert(secret and exp and ip, "usage: cookie_sign.lua <secret> <exp> <ip>")
io.write(p.make_cookie(exp, p.b64url_encode(crypto.hmac_sha1(secret, p.cookie_payload(exp, ip)))))
