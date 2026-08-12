# page

> Source: https://nginx.org/en/docs/njs/preload_objects.html

---

## 目錄

- [Understanding preloaded objects](#understanding-preloaded-objects)
    - [Working with preload objects](#working-with-preload-objects)

---

## 了解預加載對象

> Preloaded objects are supported only with the [njs](https://nginx.org/en/docs/njs/engine.html#njs_engine) JavaScript engine and are not available with the [QuickJS](https://nginx.org/en/docs/njs/engine.html#quickjs_engine) engine.

對於每個傳入的請求，njs都創建一個單獨的虛擬機。這帶來了很多好處，例如可預測的內存消耗或請求隔離。但是，由於所有請求都是隔離的，如果請求處理程式需要訪問某些數據，它必須自己讀取。這是效率低下的，特別是當數據量很大時。

為了解決這個問題，我們引入了一個預加載的共享對象。這樣的對象是不可變的，沒有原型鏈：它們的值不能改變，屬性不能添加或刪除。

#### 使用preload對象

下面是一些如何在njs中使用preload對象的示例：

-   按名稱訪問屬性：
    
    > preloaded\_object.prop\_name
    > preloaded\_object\[prop\_name\]
    
-   枚舉屬性：
    
    > for (i in preloaded\_object\_name) {
    >     ...
    > }
    
-   使用`call()`應用非修改內置方法：
    
    > Array.prototype.filter.call（preloaded\_object\_name，...）