# page

> Source: https://nginx.org/en/docs/faq/variables_in_config.html

---

## 目錄

- [Is there a proper way to use nginx variables to make sections of the configuration shorter, using them as macros for making parts of configuration work as templates?](#is-there-a-proper-way-to-use-nginx-variables-to-make-sections-of-the-configuration-shorter-using-them-as-macros-for-making-parts-of-configuration-work-as-templates)

---

## 是否有一種正確的方法可以使用nginx變量來縮短配置的部分，將它們用作宏，使配置的部分作為模板工作？

**Q：** 是否有一種正確的方法可以使用nginx變量來縮短配置的部分，使用它們作為宏來使配置的部分作為模板工作？

**A：** 不應該使用變量作為模板宏。變量是在處理每個請求的運行時進行評估的，因此與普通的靜態配置相比，它們的成本相當高。使用變量存儲靜態字符串也是一個壞主意。相反，應該使用宏擴展和「include」指令來更容易地生成模板宏，可以使用外部工具來完成，例如sed + make或任何其他常見的模板機制。