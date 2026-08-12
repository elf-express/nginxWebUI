# 安裝 nginx

> Source: https://nginx.org/en/docs/install.html  
> 翻譯：zh-TW（人工校對）

---

## 安裝 nginx

安裝方式依作業系統而異。

### Linux

可使用 [nginx.org 提供的套件](https://nginx.org/en/linux_packages.html)。

### FreeBSD

可從 [packages](https://docs.freebsd.org/en/books/handbook/ports/#pkgng-intro) 或 [ports](https://docs.freebsd.org/en/books/handbook/ports/#ports-using) 安裝。ports 彈性較高，可編譯時選擇選項。

### 從原始碼編譯

若套件／ports 無法提供所需功能，可從原始碼編譯。彈性最大，但對新手較複雜。詳見 [從原始碼建構 nginx](https://nginx.org/en/docs/configure.html)（本目錄 `003page.md`）。

### 本專案（Docker）

本 repo 的容器映像以 **Alpine + 系統 nginx 套件 + 精選動態模組** 為主，**不必**在容器內自行 `./configure && make`。模組清單與載入順序見：

- 根目錄 `Dockerfile`（`apk add nginx-mod-*`）
- `NginxService.MODULE_CATALOG`（`load_module` 順序）
- [`docs/nginx結構.md`](../nginx結構.md)（區塊樹與模組對應）
