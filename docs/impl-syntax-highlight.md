# Conf 語法高亮 — 實作計畫（開發用）

## Step 1：下載 CodeMirror 5 資源

從 CDN 下載以下檔案到 `static/lib/codemirror/`：

```
https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.18/codemirror.min.js
https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.18/codemirror.min.css
https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.18/mode/nginx/nginx.min.js
https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.18/theme/monokai.min.css
```

## Step 2：修改 conf/index.html

### 2a. 在 `<head>` 或頁面頂部引入 CSS
```html
<link rel="stylesheet" href="${ctx}/lib/codemirror/codemirror.min.css">
<link rel="stylesheet" href="${ctx}/lib/codemirror/theme/monokai.min.css">
```

### 2b. 加入自定義 CSS 調整高度和邊框
```css
.CodeMirror {
    height: 500px;
    border: 1px solid #ddd;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
}
```

### 2c. 在頁面底部、現有 JS 之前引入
```html
<script src="${ctx}/lib/codemirror/codemirror.min.js"></script>
<script src="${ctx}/lib/codemirror/mode/nginx/nginx.min.js"></script>
```

### 2d. textarea 保持不變
不需要改 textarea 的 HTML，CodeMirror 會自動接管。

## Step 3：修改 conf/index.js

### 3a. 初始化 CodeMirror（取代 setTextareaCount）

找到 `$(".conf").setTextareaCount()` 改為：
```javascript
// 左側（可編輯）
var cmLeft = CodeMirror.fromTextArea(document.getElementById('nginxContent'), {
    mode: 'nginx',
    theme: 'monokai',
    lineNumbers: true,
    readOnly: false,
    lineWrapping: false,
    tabSize: 4,
    indentUnit: 4,
});

// 右側（唯讀）
var cmRight = CodeMirror.fromTextArea(document.getElementById('org'), {
    mode: 'nginx',
    theme: 'monokai',
    lineNumbers: true,
    readOnly: true,
    lineWrapping: false,
    tabSize: 4,
    indentUnit: 4,
});
```

### 3b. 內容讀寫適配

原本用 `$("#nginxContent").val(xxx)` 的地方改為：
```javascript
// 設值
cmLeft.setValue(confExt.conf);
cmRight.setValue(confExt.conf);

// 取值
var content = cmLeft.getValue();
```

### 3c. 需要搜尋替換的模式

| 原始代碼 | 替換為 |
|---------|--------|
| `$("#nginxContent").val(xxx)` | `cmLeft.setValue(xxx)` |
| `$("#nginxContent").val()` | `cmLeft.getValue()` |
| `$("#org").val(xxx)` | `cmRight.setValue(xxx)` |
| `$("#org").val()` | `cmRight.getValue()` |
| `$(".conf").setTextareaCount()` | 刪除（CodeMirror 自帶行號） |
| `$(".org").setTextareaCount()` | 刪除 |

## Step 4：Playwright 測試

新增 `tests/e2e/06-syntax-highlight.spec.js`：
```javascript
test('conf 頁面應使用 CodeMirror 語法高亮', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForTimeout(2000);

    // CodeMirror 應存在
    const cm = page.locator('.CodeMirror');
    await expect(cm.first()).toBeVisible();

    // 應使用 monokai 主題
    const hasMomokai = await page.locator('.cm-s-monokai').count();
    expect(hasMomokai).toBeGreaterThan(0);

    // 應有語法高亮的 span（CodeMirror 會為不同 token 加 class）
    const hasTokens = await page.locator('.cm-keyword, .cm-variable, .cm-number').count();
    expect(hasTokens).toBeGreaterThan(0);
});
```

## Step 5：驗證清單

- [ ] 左側 conf 有語法高亮
- [ ] 右側 conf 有語法高亮（唯讀）
- [ ] 行號正確顯示
- [ ] 深色主題與現有 UI 風格一致
- [ ] 「對比文件」功能正常（jsdifflib 不受影響）
- [ ] 「校驗文件」功能正常
- [ ] 「替換文件」功能正常
- [ ] 「重新裝載」功能正常
- [ ] 內容可正確讀取和提交
- [ ] Playwright 測試通過
