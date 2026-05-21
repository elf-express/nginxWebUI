# 额外参数批量输入 - 实现方案

## 修改文件清单

仅修改前端，共 2 个文件：

| 文件 | 改动 |
|------|------|
| `src/main/resources/WEB-INF/view/adminPage/server/index.html` | 新增批量输入弹窗 HTML |
| `src/main/resources/static/js/adminPage/server/index.js` | 新增批量输入 JS 函数 |

**后端零改动。**

---

## 1. HTML 改动 (index.html)

### 1.1 在 paramJsonDiv 弹窗内新增「批量输入」按钮

位置：在现有的三个按钮（添加参数 / 添加模板 / 添加模板作为参数）旁边，
新增第四个按钮「批量输入」。

```html
<button class="btn btn-success" onclick="showBatchInput()">
    <span class="icon-edit"></span> ${m.get("batchInput")}
</button>
```

> 注意：需要在 messages.properties / messages_en_US.properties / messages_zh_TW.properties
> 中添加对应的国际化 key，或者直接硬编码文字（视项目风格而定）。

### 1.2 新增批量输入弹窗

在 paramJsonDiv 之后添加一个新的 modal div：

```html
<div id="batchInputDiv" class="modal" style="display:none;">
    <div class="modal-header">
        <span>批量输入参数</span>
        <span class="close" onclick="closeBatchInput()">×</span>
    </div>
    <div class="modal-body">
        <p class="hint-text">每行一条 nginx 指令，按第一个空格自动拆分名称和值，行末分号会自动去除。</p>
        <p class="hint-text">示例：</p>
        <pre>proxy_set_header X-Real-IP $remote_addr
client_max_body_size 100m</pre>
        <textarea id="batchInputText" rows="10" style="width:100%;"></textarea>
    </div>
    <div class="modal-footer">
        <button class="btn btn-primary" onclick="parseBatchInput()">确认添加</button>
        <button class="btn btn-danger" onclick="closeBatchInput()">关闭</button>
    </div>
</div>
```

---

## 2. JS 改动 (index.js)

### 2.1 showBatchInput()

```javascript
function showBatchInput() {
    $("#batchInputText").val("");
    $("#batchInputDiv").show();
}
```

### 2.2 closeBatchInput()

```javascript
function closeBatchInput() {
    $("#batchInputDiv").hide();
}
```

### 2.3 parseBatchInput() - 核心解析函数

```javascript
function parseBatchInput() {
    var text = $("#batchInputText").val();
    if (!text || text.trim() === "") {
        closeBatchInput();
        return;
    }

    var lines = text.split("\n");
    for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (line === "") continue;

        // 去除行末分号
        if (line.endsWith(";")) {
            line = line.substring(0, line.length - 1).trim();
        }

        // 按第一个空格拆分 name 和 value
        var name = "";
        var value = "";
        var spaceIndex = line.indexOf(" ");
        if (spaceIndex > 0) {
            name = line.substring(0, spaceIndex);
            value = line.substring(spaceIndex + 1).trim();
        } else {
            name = line;
            value = "";
        }

        // 复用现有的 addParam 逻辑，动态插入一行到 paramList 表格
        // 但填入解析好的 name 和 value
        addParamWithValue(name, value);
    }

    closeBatchInput();
}
```

### 2.4 addParamWithValue(name, value) - 带值添加参数行

基于现有的 `addParam()` 函数改造，增加 name/value 参数：

```javascript
function addParamWithValue(name, value) {
    // 复用 addParam 的 HTML 结构
    // 生成一行新 row，但 textarea 预填入 name 和 value
    var uuid = guid();
    var html = '<tr id="paramTr_' + uuid + '">';
    html += '<td><textarea class="form-control param_name">' + escapeHtml(name) + '</textarea></td>';
    html += '<td><textarea class="form-control param_value">' + escapeHtml(value) + '</textarea></td>';
    html += '<td><select class="form-control param_position">';
    html += '<option value="0" selected>' + addToLastStr + '</option>';
    html += '<option value="1">' + addToFirstStr + '</option>';
    html += '</select></td>';
    html += '<td>';
    html += '<button class="btn btn-danger btn-sm" onclick="deleteParam(\'' + uuid + '\')">删除</button> ';
    html += '<button class="btn btn-info btn-sm" onclick="upParam(\'' + uuid + '\')">上移</button> ';
    html += '<button class="btn btn-success btn-sm" onclick="downParam(\'' + uuid + '\')">下移</button>';
    html += '</td>';
    html += '</tr>';

    $("#paramList").append(html);
}
```

> **重要：** 上面的 HTML 结构必须与现有 `addParam()` / `fillTable()` 生成的行结构
> 完全一致（class 名、DOM 层级），这样 `addParamOver()` 收集数据时才能正确读取。
> 实际实现时需要对照 `fillTable()` 的代码精确复制行结构。

### 2.5 escapeHtml() 辅助函数

防止 XSS：

```javascript
function escapeHtml(text) {
    if (!text) return "";
    return text.replace(/&/g, "&amp;")
               .replace(/</g, "&lt;")
               .replace(/>/g, "&gt;")
               .replace(/"/g, "&quot;")
               .replace(/'/g, "&#039;");
}
```

---

## 3. 国际化 (如需要)

| Key | 简体中文 | 繁体中文 | English |
|-----|---------|---------|---------|
| batchInput | 批量输入 | 批量輸入 | Batch Input |

文件：
- `src/main/resources/messages.properties`
- `src/main/resources/messages_zh_TW.properties`
- `src/main/resources/messages_en_US.properties`

---

## 4. 注意事项

1. **不改后端** — Controller / Service / Model / ConfService 完全不动
2. **数据格式不变** — 批量输入最终仍然是往 paramList 表格插入行，
   addParamOver() 收集时格式与手动添加完全一致
3. **行结构一致** — addParamWithValue 生成的 tr 必须与 fillTable/addParam 的结构一致
4. **分号处理** — 自动去除行末 `;`，因为系统生成 conf 时由 nginxparser 库自动添加
5. **弹窗层级** — batchInputDiv 需要在 paramJsonDiv 之上显示（z-index）
6. **XSS 防护** — 用户输入的文本需要 escapeHtml 后再插入 DOM

## 5. 验证方式

1. 打开反向代理 → 添加/编辑 → 设置额外参数（Server 层级）→ 批量输入 → 确认多行被正确解析
2. 打开反向代理 → 添加/编辑 → Location 行 → 设置额外参数（Location 层级）→ 批量输入 → 同上
3. 批量输入后可以继续手动添加/删除/编辑参数
4. 提交后重新编辑，确认参数正确保存和回显
5. 启用配置，确认生成的 nginx.conf 正确
