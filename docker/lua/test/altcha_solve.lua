-- Test tool: solve the ALTCHA challenge of a challenge page like a browser would.
-- Usage: luajit altcha_solve.lua <challenge page html file>   -> prints the base64 payload for POST verify (field "altcha")
local crypto = require "crypto_ffi"
local cjson = require "cjson"

local f = assert(io.open(arg[1], "rb"))
local html = f:read("*a")
f:close()

local attr = assert(html:match('challengejson="([^"]*)"'), "no challengejson attribute")
local json = attr:gsub("&quot;", '"'):gsub("&#39;", "'"):gsub("&lt;", "<"):gsub("&gt;", ">"):gsub("&amp;", "&")
local c = cjson.decode(json)
assert(c.algorithm == "SHA-256", "algorithm " .. tostring(c.algorithm))

local number
for i = 0, c.maxnumber do
  if crypto.sha256_hex(c.salt .. i) == c.challenge then
    number = i
    break
  end
end
assert(number, "no solution up to maxnumber")

local payload = cjson.encode({
  algorithm = c.algorithm, challenge = c.challenge, number = number, salt = c.salt, signature = c.signature, took = 1,
})
-- standard base64 with padding (what the browser's btoa produces)
local B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
local out = {}
for i = 1, #payload, 3 do
  local a, b, d = payload:byte(i, i + 2)
  local n = a * 65536 + (b or 0) * 256 + (d or 0)
  local c1, c2, c3, c4 = math.floor(n / 262144), math.floor(n / 4096) % 64, math.floor(n / 64) % 64, n % 64
  out[#out + 1] = B64:sub(c1 + 1, c1 + 1) .. B64:sub(c2 + 1, c2 + 1)
      .. (b and B64:sub(c3 + 1, c3 + 1) or "=") .. (d and B64:sub(c4 + 1, c4 + 1) or "=")
end
io.write(table.concat(out))
