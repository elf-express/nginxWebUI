# page

> Source: https://nginx.org/en/docs/http/ngx_http_dav_module.html

---

## 目錄

- [Module ngx\_http\_dav\_module](#module-ngxhttpdavmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_http\_dav\_module

`ngx_http_dav_module`模塊用於通過WebDAV協議實現文件管理自動化。該模塊處理HTTP和WebDAV方法PUT、PUT、MKCOL、COPY和MOVE。

默認情況下不構建此模塊，應使用`--with-http_dav_module`配置參數啟用。

> >需要其他WebDAV方法才能運行的WebDAV客戶端將無法使用此模塊。

#### 配置示例

> location / {
>     root                  /data/www;
> 
>     client\_body\_temp\_path /data/client\_temp;
> 
>     dav\_methods PUT DELETE MKCOL COPY MOVE;
> 
>     create\_full\_put\_path  on;
>     dav\_access            group:rw  all:r;
> 
>     limit\_except GET {
>         allow 192.168.1.0/32;
>         deny  all;
>     }
> }

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>create_full_put_path</strong> <code>on</code> | <code>off</code>;</code><br></td></tr><tr><th>Default:</th><td><pre>create_full_put_path off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

WebDAV規範只允許在已經存在的目錄中創建文件。此指令允許創建所有需要的中間目錄。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>dav_access</strong> <code><i>users</i></code>:<code><i>permissions</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>dav_access user:rw;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

為新創建的文件和目錄設置訪問權限，例如：

> dav\_access user:rw group:rw all:r;

如果指定了任何`group`或`all`訪問權限，則可以省略`user`權限：

> dav\_access group:rw all:r;

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>dav_methods</strong> <code>off</code> | <code><i>method</i></code> ...;</code><br></td></tr><tr><th>Default:</th><td><pre>dav_methods off;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

允許指定的HTTP和WebDAV方法。參數`off`拒絕此模塊處理的所有方法。支持以下方法：`PUT`、`DELETE`、`MKCOL`、`COPY`和`MOVE`。

使用PUT方法上傳的文件首先寫入臨時文件，然後重命名文件。從0.8.9版本開始，臨時文件和持久存儲可以放在不同的文件系統上。但是，請注意，在這種情況下，文件是跨兩個文件系統複製的，而不是廉價的重命名操作。因此，建議對於任何給定的位置，保存的文件和目錄由[client\_body\_temp\_path](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_temp_path)指令設置的臨時文件放在同一個文件系統上。

當使用PUT方法創建文件時，可以通過在「Date」頭欄位中傳遞修改日期來指定修改日期。

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>min_delete_depth</strong> <code><i>number</i></code>;</code><br></td></tr><tr><th>Default:</th><td><pre>min_delete_depth 0;</pre></td></tr><tr><th>Context:</th><td><code>http</code>, <code>server</code>, <code>location</code><br></td></tr></tbody></table>

如果請求路徑中的元素數量不小於指定的數量，則允許review方法刪除文件。

> min\_delete\_depth 4;

允許根據請求刪除文件

> /users/00/00/name
> >/users/00/00/name/pic. jpg
> >/users/00/00/page. html

並否認

> /users/00/00