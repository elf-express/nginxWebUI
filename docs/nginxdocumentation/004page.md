# page

> Source: https://nginx.org/en/docs/contributing_changes.html

---

## 目錄

- [貢獻變更](#貢獻變更)
    - [取得來源](#取得來源)
    - [格式更改](#格式更改)
    - [提交之前](#提交之前)
    - [提交更改](#提交更改)

---

## 貢獻變更

#### 取得來源

[GitHub](https://github.com/)用於儲存原始碼。可以使用以下命令克隆[存儲庫](https://github.com/nginx/nginx)：

```
git clonehttps://github.com/nginx/nginx.git
```

#### 格式更改

更改的格式應根據 nginx 使用的[程式碼風格](https://nginx.org/en/docs/dev/development_guide.html#code_style)。有時，沒有明確的規則；在這種情況下，請檢查現有 nginx 來源的格式並模仿這種風格。如果樣式與周圍的程式碼相對應，則變更將更有可能被接受。

[提交](https://docs.github.com/en/pull-requests/committing-changes-to-your-project/creating-and-editing-commits/about-commits) nginx GitHub 分支中的變更。請確保指定的[電子郵件](https://docs.github.com/en/get-started/getting-started-with-git/setting-your-username-in-git)地址和作者真實姓名正確。

提交訊息應該有一個單行概要，空行後面跟著詳細描述。將主題和提交訊息正文行限制為 72 個字元。可以使用 `git show` 指令取得結果提交：

```diff
提交 067d766f210ee914b750d79d9284cbf8801058f3
作者：Zoey <用户名@example.com>
日期：2026 年 4 月 5 日星期日 11:31:15 +0200

修復子請求中的 $request_port 和 $is_request_port

關閉#1247。

diff --git a/src/http/ngx_http_core_module.c B/src/http/ngx_http_core_module.c
索引 0c46106db..53ddf39bb 100644
>- a/src/http/ngx_http_core_module.c
>+ B/src/http/ngx_http_core_module.c
@@ -2453,6 +2453,8 @@ ngx_http_subrequest(ngx_http_request_t *r,
sr->方法 = NGX_HTTP_GET;
sr->http_version = r->http_version;

+ sr->端口 = r->端口;
+
sr->请求_line = r->请求_line;
sr->uri = *uri;
```

#### 提交之前

在提交更改之前，有幾點值得考慮：

- 提议的更改应该在广泛的[支持的平台](https://nginx.org/en/index.html#tested_os_and_platforms)上正常工作。
- 嘗試闡明為什麼需要建議的更改，並提供用例（如果可能）。
- 通过测试套件传递您的更改是确保它们不会导致回归的好方法。可以使用以下命令克隆帶有測試的[存儲庫](https://github.com/nginx/nginx-tests)：
    
    ```
    git clonehttps://github.com/nginx/nginx-tests.git
    ```

#### 提交更改

建議的變更應作為 [pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork) 從您的 fork 提交到 [nginx 儲存庫](https://github.com/nginx/nginx)。

#### 網站

GitHub 用於儲存本網站的原始碼。可以使用以下命令克隆[存儲庫](https://www.github.com/nginx/nginx.org)：

```
git clonehttps://github.com/nginx/nginx.org.git
```

文件變更應作為拉取請求從您的分叉提交。

#### 授權

提交更改意味著授予項目在適當的[許可證](https://nginx.org/LICENSE)下使用它的權限。