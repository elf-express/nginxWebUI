# page

> Source: https://nginx.org/en/docs/nginx_dtrace_pid_provider.html

---

## 目錄

- [Debugging nginx with DTrace pid provider](#debugging-nginx-with-dtrace-pid-provider)
    - [See also](#see-also)

---

## 使用DTrace pid提供程式調試nginx

本文假設讀者對nginx內部和[DTrace](https://nginx.org/en/docs/nginx_dtrace_pid_provider.html#see_also)有一般的了解。

儘管使用[\--with-debug](https://nginx.org/en/docs/debugging_log.html)選項構建的nginx已經提供了大量有關請求處理的信息，但有時需要更徹底地跟蹤代碼路徑的特定部分，同時省略其餘的調試輸出。DTrace pid提供器（在Solaris、macOS上可用）是一個有用工具，可用於探索userland編程的內部結構，因為它不需要任何代碼更改，並且可以幫助完成任務。一個用於跟蹤和列印nginx函數調用的簡單DTrace腳本可能如下所示：

```nginx
#pragma D選項流程縮進

pid$target:nginx::entry {
}

pid$target:nginx::return {
}
```

不過，DTrace得函數調用跟蹤功能只提供了有限得有用信息.實時檢查函數參數通常更有趣，但也更複雜一些.下面得示例旨在幫助讀者更熟悉DTrace以及使用DTrace分析nginx行為得過程.

將DTrace與nginx一起使用的常見方案之一如下：附加到nginx工作進程以記錄請求行和請求開始時間。要附加的相應函數是`ngx_http_process_request()`，而所涉及的參數是指向`ngx_http_request_t`結構的指針。用於此類請求記錄的DTrace腳本可以如下所示簡單：

```c
pid$target：：*ngx_http_process_request：條目
{
    this->request = (ngx_http_request_t *)copyin(arg0, sizeof(ngx_http_request_t));
    request_line =字符串of（copyin（（uintptr_t）this->request->request_line. data，
                                         this->request->request_line.len));
    printf("request line = %s\n", this->request_line);
    printf("request start sec = %d\n", this->request->start_sec);
}
```

應該注意的是，在上面的示例中，DTrace需要一些關於`ngx_http_request_t`結構的知識。遺憾的是，雖然可以在DTrace腳本中使用特定的`#include`指令，然後將其傳遞給C預處理器（帶有`-C`標誌），但實際上並不起作用。由於存在大量的交叉依賴，幾乎所有的nginx頭文件都必須包含在內。反過來，根據`configure`腳本設置，nginx頭文件將包括PCRE、OpenSSL和各種系統頭文件。雖然理論上，DTrace腳本預處理和編譯中可能包括與特定nginx版本相關的所有頭文件，但實際上，由於某些頭文件中的語法未知，DTrace腳本很可能無法編譯。

上述問題可以通過在DTrace腳本中僅包含相關且必要得結構與類型定義來解決. DTrace必須知道結構，類型與欄位偏移量得大小.因此，可以通過手動優化結構定義以便與DTrace一起使用來進一步減少依賴性.

讓我們使用上面的DTrace腳本示例，看看它需要哪些結構定義才能正常工作。

首先，應該包括configure生成的`objs/ngx_auto_config.h`文件，因為它定義了許多影響各種`#ifdef`的常量。然後，應該將一些基本類型和定義（如`ngx_str_t`、`ngx_table_elt_t`、`ngx_uint_t`等）放在DTrace腳本的開頭。這些定義緊湊、常用，不太可能頻繁更改。

還有一個`ngx_http_request_t`結構，它包含了很多指向其他結構的指針。因為這些指針實際上與這個腳本無關，並且因為它們的大小相同，所以可以用void指針來替換它們。不過，與其更改定義，最好添加適當的typedef：

```c
typedef ngx_http_upstream_t     void;
typedef ngx_http_request_body_t void;
```

最後但並非最不重要的是，必須加入兩個成員結構（`ngx_http_headers_in_t`、`ngx_http_headers_out_t`）的定義、回呼函式的宣告和常數的定義。

可從[here](http://nginx.org/download/trace_process_request.d)下載最終的DTrace腳本。

下面的示例顯示了運行此腳本的輸出：

> \# dtrace -C -I ./對象-strace\_process\_request.d-p 4848（在此示例中，您可以使用以下命令）
> dtrace：腳本「trace\_process\_request.d」與1個探測器匹配
> CPU     ID                    功能：名稱
>   1      4 .XAbmO.ngx\_http\_process\_request：進入請求行= GET / HTTP/1.1
> >請求開始秒= 1349162898
> 
>   0      4. XAbmO.ngx\_http\_process\_request：entry request line = GET /en/docs/nginx\_dtrace\_pid\_provider.html HTTP/1.1
> >請求開始秒= 1349162899

使用類似的技術，讀者應該能夠跟蹤其他nginx函數調用。

#### 參見

-   [Solaris Dynamic Tracing Guide](http://docs.oracle.com/cd/E19253-01/817-6223/index.html)
-   [Introduction article on DTrace pid provider](http://dtrace.org/blogs/brendan/2011/02/09/dtrace-pid-provider/)