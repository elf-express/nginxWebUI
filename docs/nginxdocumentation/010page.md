# page

> Source: https://nginx.org/en/docs/example.html

---

## 目錄

- [Example nginx configuration](#example-nginx-configuration)

---

## Example nginx configuration

An example site configuration that passes all requests to the backend except images and requests starting with "/download/".

> user  www www;
> 
> worker\_processes  2;
> 
> pid /var/run/nginx.pid;
> 
> #                          \[ debug | info | notice | warn | error | crit \]
> 
> error\_log  /var/log/nginx.error\_log  info;
> 
> events {
>     worker\_connections   2000;
> 
>     # use \[ kqueue | epoll | /dev/poll | select | poll \];
>     use kqueue;
> }
> 
> http {
> 
>     include       conf/mime.types;
>     default\_type  application/octet-stream;
> 
> 
>     log\_format main      '$remote\_addr - $remote\_user \[$time\_local\] '
>                          '"$request" $status $bytes\_sent '
>                          '"$http\_referer" "$http\_user\_agent" '
>                          '"$gzip\_ratio"';
> 
>     log\_format download  '$remote\_addr - $remote\_user \[$time\_local\] '
>                          '"$request" $status $bytes\_sent '
>                          '"$http\_referer" "$http\_user\_agent" '
>                          '"$http\_range" "$sent\_http\_content\_range"';
> 
>     client\_header\_timeout  3m;
>     client\_body\_timeout    3m;
>     send\_timeout           3m;
> 
>     client\_header\_buffer\_size    1k;
>     large\_client\_header\_buffers  4 4k;
> 
>     gzip on;
>     gzip\_min\_length  1100;
>     gzip\_buffers     4 8k;
>     gzip\_types       text/plain;
> 
>     output\_buffers   1 32k;
>     postpone\_output  1460;
> 
>     sendfile         on;
>     tcp\_nopush       on;
>     tcp\_nodelay      on;
>     send\_lowat       12000;
> 
>     keepalive\_timeout  75 20;
> 
>     #lingering\_time     30;
>     #lingering\_timeout  10;
>     #reset\_timedout\_connection  on;
> 
> 
>     server {
>         listen        one.example.com;
>         server\_name   one.example.com  www.one.example.com;
> 
>         access\_log   /var/log/nginx.access\_log  main;
> 
>         location / {
>             proxy\_pass         http://127.0.0.1/;
>             proxy\_redirect     off;
> 
>             proxy\_set\_header   Host             $host;
>             proxy\_set\_header   X-Real-IP        $remote\_addr;
>             #proxy\_set\_header  X-Forwarded-For  $proxy\_add\_x\_forwarded\_for;
> 
>             client\_max\_body\_size       10m;
>             client\_body\_buffer\_size    128k;
> 
>             client\_body\_temp\_path      /var/nginx/client\_body\_temp;
> 
>             proxy\_connect\_timeout      70;
>             proxy\_send\_timeout         90;
>             proxy\_read\_timeout         90;
>             proxy\_send\_lowat           12000;
> 
>             proxy\_buffer\_size          4k;
>             proxy\_buffers              4 32k;
>             proxy\_busy\_buffers\_size    64k;
>             proxy\_temp\_file\_write\_size 64k;
> 
>             proxy\_temp\_path            /var/nginx/proxy\_temp;
> 
>             charset  koi8-r;
>         }
> 
>         error\_page  404  /404.html;
> 
>         location = /404.html {
>             root  /spool/www;
>         }
> 
>         location /old\_stuff/ {
>             rewrite   ^/old\_stuff/(.\*)$  /new\_stuff/$1  permanent;
>         }
> 
>         location /download/ {
> 
>             valid\_referers  none  blocked  server\_names  \*.example.com;
> 
>             if ($invalid\_referer) {
>                 #rewrite   ^/   http://www.example.com/;
>                 return   403;
>             }
> 
>             #rewrite\_log  on;
> 
>             # rewrite /download/\*/mp3/\*.any\_ext to /download/\*/mp3/\*.mp3
>             rewrite ^/(download/.\*)/mp3/(.\*)\\..\*$
>                     /$1/mp3/$2.mp3                   break;
> 
>             root         /spool/www;
>             #autoindex    on;
>             access\_log   /var/log/nginx-download.access\_log  download;
>         }
> 
>         location ~\* \\.(jpg|jpeg|gif)$ {
>             root         /spool/www;
>             access\_log   off;
>             expires      30d;
>         }
>     }
> }