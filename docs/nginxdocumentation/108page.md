# page

> Source: https://nginx.org/en/docs/njs/cli.html

---

## 目錄

- [Command-line interface](#command-line-interface)

---

## 命令行界面

njs scripts development and debugging can be performed from the command-line. The command-line utility is available after the installation of the Linux [package](https://nginx.org/en/docs/njs/install.html#install_package) or after building from the [sources](https://nginx.org/en/docs/njs/install.html#install_sources). Compared to njs running inside nginx, nginx objects ([HTTP](https://nginx.org/en/docs/njs/reference.html#http) and [Stream](https://nginx.org/en/docs/njs/reference.html#stream)) are not available in the utility.

```nginx
$ echo「2**3」|njs-q
8

$ njs
>> globalThis
global {
 njs: njs {
  version: '0.3.9'
 },
 global：[Circular]，
 process: process {
  argv: [
   「/usr/bin/njs」
  ],
  env: {
   PATH：'/usr/local/sbin：/usr/local/bin：/usr/sbin：/usr/bin：/sbin：/bin'，
   HOSTNAME: 'f777c149d4f8',
   $TERM：'xterm'，
   Nginx_版本：'1.17.9'，
   NJS_版本：「0.3.9」，
   PKG_RELEASE：'1~ buster'，
   HOME：'/root'
  }
 },
 console: {
  log：[Function：native]，
  dump：[Function：native]，
  time：[Function：native]，
  timeEnd：[Function：native]
 },
 print：[Function：native]
}
>>
```