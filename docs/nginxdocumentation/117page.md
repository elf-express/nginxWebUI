# page

> Source: https://nginx.org/en/docs/njs/typescript.html

---

## 目錄

- [Writing njs code using TypeScript definition files](#writing-njs-code-using-typescript-definition-files)
    - [Compiling TypeScript definition files](#compiling-typescript-definition-files)
    - [API checks and autocompletions](#api-checks-and-autocompletions)
    - [Writing njs type-safe code](#writing-njs-type-safe-code)

---

## 使用TypeScript定義文件編寫njs代碼

[TypeScript](https://www.typescriptlang.org/)是JavaScript的類型化超集，可編譯為純JavaScript。

TypeScript支持包含現有JavaScript庫的類型信息的定義文件。這使得其他程式能夠使用文件中定義的值，就像它們是靜態類型的TypeScript實體一樣。

njs為其[API](https://nginx.org/en/docs/njs/reference.html)提供了TypeScript定義文件，可用於：

-   在編輯器中獲取自動完成和API檢查
-   編寫njs類型安全代碼

#### 編譯TypeScript定義文件

> >$git clone https://github.com/nginx/njs
> >$cd njs &&./configure && make ts
> >$ls build/ts/
> njs\_core.d.ts
> njs\_shell.d.ts
> ngx\_http\_js\_module.d.ts
> ngx\_stream\_js\_module.d.ts

#### API檢查和自動補全

把`*.d.ts`文件放到你的編輯可以找到的地方。

`test.js`:

```javascript
/// <reference path="ngx_http_js_module.d.ts" />
/**
 * @param {NginxHTTPRequest} r
 * */
function content_handler(r) {
    r.headersOut['content-type'] = 'text/plain';
    r.return(200, "Hello");
}
```

#### 編寫njs類型安全代碼

`test.ts`:

```javascript
/// <reference path="ngx_http_js_module.d.ts" />
function content_handler(r: NginxHTTPRequest) {
    r.headersOut['content-type'] = 'text/plain';
    r.return(200, "Hello from TypeScript");
}
```

TypeScript安裝：

> >\#npm install-g typescript

TypeScript編譯：

> >$tsc test. ts
> >$cat test.js

生成的`test.js`文件可以直接與njs一起使用。