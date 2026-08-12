# page

> Source: https://nginx.org/en/docs/faq/chunked_encoding_from_backend.html

---

## 目錄

- [Why nginx doesn’t handle chunked encoding responses from my backend properly?](#why-nginx-doesnt-handle-chunked-encoding-responses-from-my-backend-properly)

---

## 為什麼nginx不能正確處理我後台的分塊編碼響應？

**Q：** 我的後端伺服器似乎使用分塊編碼發送HTTP/1.0響應，但nginx無法正確處理它。例如，我使用nginx作為node.js應用程式的前端，而不是後端的純JSON，nginx返回的是十進位數字，如

> 47
> {"error":"query error","message":"Parameter(s) missing: user,password"}
> 0

**A：** 您的後端違反了HTTP規範（參見[RFC 2616, "3.6 Transfer Codings"](https://datatracker.ietf.org/doc/html/rfc2616#section-3.6)），HTTP/1.0中不能使用「chunked」transfer-coding。您需要修復後端應用程式或升級到nginx 1.1.4及更新版本，其中引入了額外的代碼來處理這種不穩定的後端行為。