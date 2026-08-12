# page

> Source: https://nginx.org/en/docs/sys_errlist.html

---

## 目錄

- [A message “ ‘sys\_errlist’ is deprecated; use ‘strerror’ or ‘strerror\_r’ instead ”](#a-message-syserrlist-is-deprecated-use-strerror-or-strerrorr-instead)

---

## A message “ ‘sys\_errlist’ is deprecated; use ‘strerror’ or ‘strerror\_r’ instead ”

**Q:** While building nginx version 0.7.66, 0.8.35 or higher on Linux the following warning messages are issued:

> warning: \`sys\_errlist' is deprecated;
>     use \`strerror' or \`strerror\_r' instead
> warning: \`sys\_nerr' is deprecated;
>     use \`strerror' or \`strerror\_r' instead

**A:** This is normal: nginx has to use the deprecated sys\_errlist\[\] and sys\_nerr in signal handlers because strerror() and strerror\_r() functions are not Async-Signal-Safe.