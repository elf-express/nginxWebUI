# 额外参数批量输入 - 需求计划

## 背景

「设置额外参数」功能目前需要逐行添加，每行分成「名称」和「值」两个格子。
当用户有多行 nginx 指令时，需要反复点击「添加参数」再逐个填写，操作繁琐。

## 目标

在不改动现有业务逻辑的前提下，增加一个「批量输入」的入口，
让用户可以一次性贴上多行 nginx 指令，系统自动解析为多条参数。

## 影响范围

额外参数弹窗共用于两个地方（共享同一个 dialog）：

| 层级 | 触发方式 | 数据存储 |
|------|---------|---------|
| Server 层级 | 点击 Location 表格上方的「设置额外参数」 | `#serverParamJson` 隐藏 textarea |
| Location 层级 | 点击每个 Location 行的「设置额外参数」 | `#locationParamJson_${uuid}` 隐藏 textarea |

两处共用同一个弹窗 `#paramJsonDiv`，通过 `targertId` 变量区分回写目标。

## 改动内容

### 新增：「批量输入」按钮 + textarea 弹窗

1. 在现有的「添加参数 / 添加模板 / 添加模板作为参数」按钮旁，新增一个「批量输入」按钮
2. 点击后显示一个大文本框（textarea），用户可以贴入多行 nginx 指令
3. 点击「确认」后，系统自动解析每一行，拆分为 name/value，追加到现有参数表格中

### 解析规则

输入格式示例：
```
proxy_set_header X-Real-IP $remote_addr
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for
client_max_body_size 100m
proxy_read_timeout 600s
```

解析逻辑：
- 按行分割（忽略空行）
- 每行按**第一个空格**分割：空格前 = name，空格后 = value
- 如果行末有 `;` 则自动去除（因为系统不需要分号）
- 如果整行没有空格，则整行作为 name，value 为空
- 新增的参数 position 默认为 0（追加到末尾）

### 不改动的部分

- 后端 Controller / Service / Model 完全不动
- 数据提交格式不变（仍然是 JSON 数组）
- 现有的逐行添加功能保留
- 参数的编辑、删除、上移、下移功能保留
- 模板功能保留

## 预期效果

用户可以从 nginx.conf 或网上直接复制多行指令，一次贴入，省去反复添加的操作。
批量输入后，参数仍然以逐行形式显示在表格中，可以继续编辑调整。
