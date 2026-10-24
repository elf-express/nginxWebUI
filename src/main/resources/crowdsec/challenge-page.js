// 驗證頁與訊息頁共用（E-387）：依瀏覽器語言換上三語字串；驗證頁另把 ALTCHA 字串交給元件，驗證完成後送出表單。
// 以 defer 排在 altcha.js 之前執行，元件定義前就掛好監聽。
(function () {
  var S = {};
  try {
    S = JSON.parse(document.getElementById("nwu-strings").textContent);
  } catch (e) {
    S = {};
  }
  var lang = (navigator.language || "en").toLowerCase();
  var code = lang.indexOf("zh") === 0 ? (/tw|hk|mo|hant/.test(lang) ? "zh-TW" : "zh-CN") : "en";
  var s = S[code] || S.en || {};
  document.documentElement.lang = code;
  var nodes = document.querySelectorAll("[data-i18n]");
  for (var i = 0; i < nodes.length; i++) {
    var v = s[nodes[i].getAttribute("data-i18n")];
    if (v) { nodes[i].textContent = v; }
  }
  var w = document.querySelector("altcha-widget");
  if (w) {
    w.setAttribute("strings", JSON.stringify({
      label: s["challengeStr.altchaLabel"], verifying: s["challengeStr.altchaVerifying"],
      verified: s["challengeStr.altchaVerified"], error: s["challengeStr.altchaError"],
      expired: s["challengeStr.altchaExpired"]
    }));
    w.addEventListener("verified", function () { document.getElementById("nwu-form").submit(); });
  }
})();
