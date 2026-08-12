# page

> Source: https://nginx.org/en/docs/http/converting_rewrite_rules.html

---

## 目錄

- [Converting rewrite rules](#converting-rewrite-rules)
    - [A redirect to a main site](#a-redirect-to-a-main-site)
    - [Converting Mongrel rules](#converting-mongrel-rules)

---

## 重寫規則轉換

#### 重定向到主站點

那些在共享主機的生活中使用 * 僅 * Apache的.htaccess文件來配置 * 所有內容 * 的人，通常會翻譯以下規則：

```
RewriteCond  %{HTTP_HOST}  example.org
RewriteRule  (.*)          http://www.example.org$1
```

變成了這樣

```nginx
server {
    listen       80;
    server_name  www.example.org  example.org;
    if ($http_host = example.org) {
        rewrite  (.*)  http://www.example.org$1;
    }
    ...
}
```

這是一種錯誤的、繁瑣的、無效的方法。正確的方法是為`example.org`定義一個單獨的伺服器：

```nginx
server {
    listen       80;
    server_name  example.org;
    return       301 http://www.example.org$request_uri;
}

server {
    listen       80;
    server_name  www.example.org;
    ...
}
```

```nginx
>在0.9.1之前的版本中，重定向可以通過以下方式進行：

>     rewrite      ^ http://www.example.org$request_uri?;
```

另一個例子。代替「顛倒」邏輯「所有不是`example.com`和不是`www.example.com`的"：

```
RewriteCond  %{HTTP_HOST}  !example.com
RewriteCond  %{HTTP_HOST}  !www.example.com
RewriteRule  (.*)          http://www.example.com$1
```

應該簡單地定義`example.com`、`www.example.com`和「其他所有內容」：

```nginx
server {
    listen       80;
    server_name  example.com www.example.com;
    ...
}

server {
    listen       80 default_server;
    server_name  _;
    return       301 http://example.com$request_uri;
}
```

```nginx
>在0.9.1之前的版本中，重定向可以通過以下方式進行：

>     rewrite      ^ http://example.com$request_uri?;
```

#### 轉換Mongrel規則

典型的Mongrel規則：

> >文檔根目錄/var/www/myapp.com/current/public
> 
> RewriteCond %{DOCUMENT\_ROOT}/system/maintenance.html -f
> RewriteCond %{SCRIPT\_FILENAME} !maintenance.html
> RewriteRule ^.\*$ %{DOCUMENT\_ROOT}/system/maintenance.html \[L\]
> 
> RewriteCond %{REQUEST\_FILENAME} -f
> RewriteRule ^（.\*）$$1\[QSA，L\]
> 
> RewriteCond %{REQUEST\_FILENAME}/index.html -f
> RewriteRule ^（.\*）$$1/index.html \[QSA，L\]
> 
> RewriteCond %{REQUEST\_FILENAME}.html -f
> RewriteRule ^（.\*）$$1.html\[QSA，L\]
> 
> RewriteRule ^/(.\*)$ balancer://mongrel\_cluster%{REQUEST\_URI} \[P,QSA,L\]

應換算成

```nginx
location / {
    root       /var/www/myapp.com/current/public;

    try_files/system/maintenance. html
               $uri $uri/index.html $uri.html
               @mongrel;
}

location @mongrel {
    proxy_pass  http://mongrel;
}
```