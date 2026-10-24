-- Unit tests for nwu_challenge_policy (pure Lua, no ngx needed).
local p = require "nwu_challenge_policy"
local n = 0
local function check(cond, msg)
  n = n + 1
  assert(cond, msg or ("assertion " .. n .. " failed"))
end

-- base64url round trip for every length remainder, no padding, url-safe alphabet
for _, s in ipairs({ "", "f", "fo", "foo", "foob", "fooba", "foobar", "\255\254\253" }) do
  local e = p.b64url_encode(s)
  check(p.b64url_decode(e) == s, "round trip " .. s)
  check(not e:find("[=+/]"), "url-safe " .. e)
end
check(p.b64url_encode("foobar") == "Zm9vYmFy")
check(p.b64url_encode("\255\254\253") == "__79")
check(p.b64url_decode("Zm9v!") == nil, "bad char")
check(p.b64url_decode("Zm9vY") == nil, "impossible length")

-- equals
check(p.equals("abc", "abc") and not p.equals("abc", "abd") and not p.equals("abc", "ab"))
check(not p.equals(nil, "a") and not p.equals("a", nil))

-- reserved path and return target
check(p.is_reserved("/.well-known/nwu-challenge/") and p.is_reserved("/.well-known/nwu-challenge/verify"))
check(not p.is_reserved("/.well-known/nwu-challenge") and not p.is_reserved("/.well-known/acme-challenge/x"))
check(p.safe_return("/shop?id=1") == "/shop?id=1")
check(p.safe_return("//evil.example/") == "/", "protocol-relative")
check(p.safe_return("/\\evil.example/") == "/", "backslash")
check(p.safe_return("https://evil.example/") == "/", "absolute url")
check(p.safe_return("/a\r\nSet-Cookie: x") == "/", "CRLF")
check(p.safe_return("/.well-known/nwu-challenge/?r=/") == "/", "no loop into the reserved path")
check(p.safe_return(nil) == "/" and p.safe_return("") == "/" and p.safe_return("/" .. string.rep("a", 2048)) == "/")

-- reserved path routes
local R = p.PREFIX
for _, name in ipairs({ "altcha.js", "altcha.css", "altcha-worker.js", "page.js", "page.css" }) do
  local route, asset = p.reserved_route("GET", R .. name)
  check(route == "asset" and asset == name, "asset " .. name)
  check(p.reserved_route("POST", R .. name) == "asset", "asset " .. name .. " for any method")
end
check(p.reserved_route("GET", R .. "page.html") == nil and p.reserved_route("GET", R .. "altcha.LICENSE.txt") == nil)
check(p.reserved_route("GET", R .. "x/page.js") == nil and p.reserved_route("GET", "/page.js") == nil)
check(p.ASSETS["page.js"]:find("javascript", 1, true) and p.ASSETS["altcha-worker.js"]:find("javascript", 1, true))
check(p.ASSETS["page.css"]:find("text/css", 1, true) and p.ASSETS["altcha.css"]:find("text/css", 1, true))
check(p.reserved_route("GET", R) == "page" and p.reserved_route("HEAD", R) == "page")
check(p.reserved_route("POST", R) == nil, "POST to the page")
check(p.reserved_route("POST", R .. "verify") == "verify" and p.reserved_route("GET", R .. "verify") == nil)
check(p.reserved_route("GET", R .. "callback") == "callback" and p.reserved_route("HEAD", R .. "callback") == "callback")
check(p.reserved_route("POST", R .. "callback") == nil and p.reserved_route("GET", R .. "x") == nil)

-- where a client without a captcha decision is sent from the reserved path
check(p.bounce_target("page", "/cart?id=1") == "/cart?id=1")
check(p.bounce_target("page", "//evil.example/") == "/" and p.bounce_target("page", nil) == "/")
check(p.bounce_target("verify", "/cart") == "/cart" and p.bounce_target("verify", nil) == "/")
check(p.bounce_target("verify", R .. "?r=/") == "/", "no loop into the reserved path")
local bt = p.make_ticket(p.ticket_payload({ kind = "resp", exp = 1, ip = "x", host = "h", path = "/back", nonce = "n" }), "sig")
check(p.bounce_target("callback", nil, bt) == "/back", "ticket path, signature not needed")
local evil = p.make_ticket(p.ticket_payload({ kind = "resp", exp = 1, ip = "x", host = "h", path = "https://evil.example/", nonce = "n" }), "s")
check(p.bounce_target("callback", nil, evil) == "/", "foreign ticket path")
check(p.bounce_target("callback", nil, "garbage") == "/" and p.bounce_target("callback", nil, nil) == "/")
check(p.bounce_target(nil, "/cart") == "/", "unknown reserved path")

-- page request classification
check(p.is_page_request("GET", "text/html,application/xhtml+xml"))
check(p.is_page_request("HEAD", "TEXT/HTML"))
check(not p.is_page_request("POST", "text/html"))
check(not p.is_page_request("GET", "application/json"))
check(not p.is_page_request("GET", nil))

-- cookie
check(p.cookie_payload(100, "203.0.113.5") == "pass\n100\n203.0.113.5")
local exp, sig = p.parse_cookie(p.make_cookie(1700000000, "AbC-_9"))
check(exp == 1700000000 and sig == "AbC-_9")
check(p.parse_cookie("x.abc") == nil and p.parse_cookie("123") == nil and p.parse_cookie(nil) == nil)
check(p.parse_cookie("123.ab;c") == nil, "no extra characters")
check(p.cookie_header("v", 3600, true) == "nwu_pass=v; Path=/; Max-Age=3600; HttpOnly; SameSite=Lax; Secure")
check(p.cookie_header("v", 60, false) == "nwu_pass=v; Path=/; Max-Age=60; HttpOnly; SameSite=Lax")

-- ticket payload, parse and check
local t = { kind = "resp", exp = 1000, ip = "2001:db8::1", host = "shop.example", path = "/a?b=1", nonce = "00ff" }
local payload = p.ticket_payload(t)
check(payload == "nwu1\nresp\n1000\n2001:db8::1\nshop.example\n/a?b=1\n00ff")
check(p.ticket_payload({ kind = "resp", exp = 1, ip = "x", host = "h", path = "/", nonce = "" }) == nil, "empty field")
check(p.ticket_payload({ kind = "resp", exp = 1, ip = "x", host = "h\nx", path = "/", nonce = "a" }) == nil, "newline")
local token = p.make_ticket(payload, "c2ln")
local f, pl, s = p.parse_ticket(token)
check(f and f.kind == "resp" and f.exp == 1000 and f.ip == "2001:db8::1" and f.host == "shop.example")
check(f.path == "/a?b=1" and f.nonce == "00ff" and pl == payload and s == "c2ln")
check(p.parse_ticket("abc") == nil, "no dot")
check(p.parse_ticket(p.b64url_encode("nwu2\nresp\n1\nip\nh\n/\nn") .. ".x") == nil, "version")
check(p.parse_ticket(p.b64url_encode("nwu1\nresp\nx\nip\nh\n/\nn") .. ".x") == nil, "exp not numeric")
check(p.parse_ticket(p.b64url_encode("nwu1\nresp\n1\nip\nh\n/") .. ".x") == nil, "six fields")
check(p.check_ticket(f, 990, "2001:db8::1", "SHOP.example") == true)
check(select(2, p.check_ticket(f, 1001, "2001:db8::1", "shop.example")) == "expired")
check(select(2, p.check_ticket(f, 600, "2001:db8::1", "shop.example")) == "lifetime")
check(select(2, p.check_ticket(f, 990, "2001:db8::2", "shop.example")) == "ip")
check(select(2, p.check_ticket(f, 990, "2001:db8::1", "other.example")) == "host")
f.kind = "req"
check(select(2, p.check_ticket(f, 990, "2001:db8::1", "shop.example")) == "kind")

-- ALTCHA salt
local salt = p.altcha_salt("a1b2", 1234, "203.0.113.5")
check(salt == "a1b2?expires=1234&ip=203.0.113.5")
local nonce, sexp, sip = p.parse_altcha_salt(salt)
check(nonce == "a1b2" and sexp == 1234 and sip == "203.0.113.5")
check(select(3, p.parse_altcha_salt("ab?expires=1&ip=2001:db8::1")) == "2001:db8::1")
check(p.parse_altcha_salt("ab?expires=1&ip=1.2.3.4&x=1") == nil and p.parse_altcha_salt(nil) == nil)

-- render and query
check(p.render("<p>{{IP}} {{MISSING}}</p>", { IP = "<1>" }) == "<p>&lt;1&gt; </p>")
check(p.render('<a v="{{R}}">', { R = '/"x&\'' }) == '<a v="/&quot;x&amp;&#39;">')
check(p.render("{{A}}{{A}}", { A = "%1" }) == "%1%1", "replacement is literal")
check(p.append_query("https://v.example/", "t", "x.y") == "https://v.example/?t=x.y")
check(p.append_query("https://v.example/?a=1", "t", "x") == "https://v.example/?a=1&t=x")

print("challenge policy tests: " .. n .. " passed")
