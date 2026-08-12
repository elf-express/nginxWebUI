# page

> Source: https://nginx.org/en/docs/ngx_google_perftools_module.html

---

## 目錄

- [Module ngx\_google\_perftools\_module](#module-ngxgoogleperftoolsmodule)
    - [Example Configuration](#example-configuration)
    - [Directives](#directives)

---

## Module ngx\_google\_perftools\_module

`ngx_google_perftools_module`模塊（0.6.29）支持使用[Google Performance Tools](https://github.com/gperftools/gperftools)對nginx工作進程進行分析。該模塊面向nginx開發人員。

默認情況下不構建此模塊，應使用`--with-google_perftools_module`配置參數啟用。

> >此模塊需要[gperftools](https://github.com/gperftools/gperftools)庫。

#### 配置示例

```nginx
google_perftools_profiles /path/to/profile;
```

配置文件將存儲為`/path/to/profile.<worker_pid>`。

#### Directives

<table cellspacing="0"><tbody><tr><th>Syntax:</th><td><code><strong>google_perftools_profiles</strong> <code><i>file</i></code>;</code><br></td></tr><tr><th>Default:</th><td>—</td></tr><tr><th>Context:</th><td><code>main</code><br></td></tr></tbody></table>

設置一個文件名，用於保存nginx工作進程的分析信息。工作進程的ID始終是文件名的一部分，並被追加到文件名的末尾，在一個點之後。