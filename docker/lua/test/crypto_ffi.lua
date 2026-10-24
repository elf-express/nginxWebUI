-- libcrypto helpers for tests and test tools that run under plain luajit (outside nginx).
local ffi = require "ffi"

ffi.cdef [[
typedef struct evp_md_st EVP_MD;
const EVP_MD *EVP_sha1(void);
unsigned char *HMAC(const EVP_MD *evp_md, const void *key, int key_len,
                    const unsigned char *d, size_t n, unsigned char *md, unsigned int *md_len);
unsigned char *SHA256(const unsigned char *d, size_t n, unsigned char *md);
]]

local ok, C = pcall(ffi.load, "libcrypto.so.3")
if not ok then
  C = ffi.load("crypto")
end

local _M = {}

local function hex(s)
  return (s:gsub(".", function(c)
    return string.format("%02x", c:byte())
  end))
end

function _M.hmac_sha1(key, msg)
  local md = ffi.new("unsigned char[20]")
  local len = ffi.new("unsigned int[1]")
  C.HMAC(C.EVP_sha1(), key, #key, msg, #msg, md, len)
  return ffi.string(md, len[0])
end

function _M.sha256_hex(s)
  local md = ffi.new("unsigned char[32]")
  C.SHA256(s, #s, md)
  return hex(ffi.string(md, 32))
end

_M.hex = hex

return _M
