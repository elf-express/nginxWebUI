# page

> Source: https://nginx.org/en/docs/njs/install.html

---

## 目錄

- [Download and install](#download-and-install)
    - [Installing as a Linux package](#installing-as-a-linux-package)
    - [Building from the sources](#building-from-the-sources)
    - [Adding QuickJS engine support](#adding-quickjs-engine-support)
    - [Building njs command-line utility](#building-njs-command-line-utility)

---

## Download and install

#### Installing as a Linux package

For Linux, njs modules [packages](https://nginx.org/en/linux_packages.html#dynmodules) can be used:

-   `nginx-module-njs` — njs [dynamic](https://nginx.org/en/docs/ngx_core_module.html#load_module) modules
-   `nginx-module-njs-dbg` — debug symbols for the `nginx-module-njs` package

After package installation, njs dynamic modules need to be loaded with the [`load_module`](https://nginx.org/en/docs/ngx_core_module.html#load_module) directive:

```nginx
load_module modules/ngx_http_js_module.so;
```

or

```nginx
load_module modules/ngx_stream_js_module.so;
```

#### Building from the sources

The [repository](https://github.com/nginx/njs) with njs sources can be cloned with the following command (requires [Git](https://git-scm.com/) client):

```
git clone https://github.com/nginx/njs
```

Then the modules should be compiled from [nginx](https://nginx.org/en/docs/configure.html) root directory using the `--add-module` configuration parameter:

```bash
./configure --add-module=`*path-to-njs*`/nginx
```

The modules can also be built as [dynamic](https://nginx.org/en/docs/ngx_core_module.html#load_module):

```bash
./configure --add-dynamic-module=`*path-to-njs*`/nginx
```

#### Adding QuickJS engine support

Make sure you have built the QuickJS library:

```
git clone https://github.com/bellard/quickjs
cd quickjs
CFLAGS='-fPIC' make libquickjs.a
```

At the module compilation step, also specify the include (`-I`) and library (`-L`) paths with the `--with-cc-opt=` and `--with-ld-opt=` configuration parameters:

```bash
./configure --add-module=`*path-to-njs*`/nginx \\
    --with-cc-opt="-I `*path-to-quickjs*`" \\
    --with-ld-opt="-L `*path-to-quickjs*`"
```

#### Building njs command-line utility

To build only the njs command-line [utility](https://nginx.org/en/docs/njs/cli.html), run `./configure` and `make njs` commands from njs root directory. After building, the utility is available as `./build/njs`.