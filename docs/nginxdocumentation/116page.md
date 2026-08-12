# page

> Source: https://nginx.org/en/docs/njs/security.html

---

## 目錄

- [Security](#security)
    - [Special considerations](#special-considerations)
    - [Advisories](#advisories)

---

## Security

所有的nginx安全問題都應該通過列出的方法之一報告[here](https://github.com/nginx/njs/blob/master/SECURITY.md)。

修補程式使用其中一個[PGP public keys](https://nginx.org/en/pgp_keys.html)簽名。

#### 特殊注意事項

njs不以任何方式評估動態代碼，尤其是從網絡接收的代碼。使用njs評估該代碼的唯一方法是在nginx中配置[js\_import](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import)指令。JavaScript代碼在nginx啟動時加載一次。

在nginx/njs威脅模型中，JavaScript代碼被認為是與`nginx.conf`和sites證書相同的可信來源。這在實踐中意味著什麼：

-   由JavaScript代碼修改觸發的內存泄漏和其他安全問題不被視為安全漏洞，而是普通的錯誤
-   應該採取措施保護njs使用的JavaScript代碼
-   如果`nginx.conf`中沒有[js\_import](https://nginx.org/en/docs/http/ngx_http_js_module.html#js_import)指令，則nginx不會受到JavaScript相關漏洞的影響

#### Advisories

-   js\_fetch\_proxy中的堆緩衝區溢出
    嚴重程度：中等
    [Advisory](https://my.f5.com/manage/s/article/K000161307)  
    [CVE-2026-8711](https://www.cve.org/CVERecord?id=CVE-2026-8711)  
    不脆弱：0.9.9+
    Vulnerable: 0.9.4-0.9.8