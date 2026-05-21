# nginxWebUI 改進計畫

## 概述
本次改進目標是簡化 nginxWebUI 的操作體驗，不改動原有業務邏輯，僅優化 UI 輸入方式與顯示效果。

## 改進項目

### 1. 批量輸入參數（已完成）
**影響範圍：** http 參數配置、反向代理 server 額外參數、反向代理 location 額外參數

**改進內容：**
- 新增「批量輸入」按鈕，點擊後彈出大文本框
- 用戶可一次貼入多行 nginx 指令，例如：
  ```
  gzip on
  keepalive_timeout 65
  client_max_body_size 100m
  ```
- 系統自動按第一個空格拆分名稱和值
- 行末分號自動去除
- 原有逐條添加方式保留不變

**修改檔案：**
- `server/index.html` — server 和 location 額外參數彈窗加入批量輸入
- `server/index.js` — 批量輸入解析邏輯（server + location）
- `http/index.html` — http 參數頁面加入批量輸入按鈕和彈窗
- `http/index.js` — 批量輸入解析邏輯
- `messages.properties` / `messages_en_US.properties` / `messages_zh_TW.properties` — 新增 i18n 文字

### 2. TLS 版本預設值與棄用標註（已完成）
**影響範圍：** 反向代理 server 的 SSL 協議版本選項

**改進內容：**
- TLSv1 和 TLSv1.1 預設**不勾選**（原本預設全勾）
- 標籤後方加上「(已棄用)」提示文字
- TLSv1.2 和 TLSv1.3 預設勾選

**修改檔案：**
- `server/index.html` — checkbox 預設值與標籤文字

### 3. 啟用配置頁面 conf 縮進美化（已完成）
**影響範圍：** 啟用配置頁面左側生成的 nginx.conf 預覽

**改進內容：**
- 依照 nginx 規範，每層嵌套縮進 4 個空格
- `http {}` → `server {}` → `location {}` 層級清晰可辨
- 不影響實際寫入檔案的內容（僅影響顯示）

**修改檔案：**
- `ToolUtils.java` — 新增 `formatConf()` 方法
- `ConfService.java` — 調用 formatConf 格式化輸出

### 4. 登入頁面密碼可見切換（已完成）
**影響範圍：** 登入頁面密碼欄位

**改進內容：**
- 密碼欄位右側加入眼睛圖標（使用 layui 內建圖標）
- 點擊可切換密碼顯示/隱藏

**修改檔案：**
- `login/index.html` — 加入 toggle 按鈕與 CSS
- `login/index.js` — togglePass() 函數

### 5. 登入頁面背景美化（已完成）
**影響範圍：** 登入頁面背景 SVG

**修改檔案：**
- `static/img/background.svg` — 更新背景圖

### 6. app.yml 還原（已完成）
**改進內容：**
- `project.home` 改回 `/home/nginxWebUI/`（原始值）
- `init.admin` 和 `init.pass` 清空（使用原始初始化流程）

## 自動化測試
使用 Playwright 進行端對端模擬測試，測試資料夾獨立於主程式碼：
- 測試目錄：`tests/e2e/`
- 測試資料庫：使用獨立的 `project.home` 路徑，不影響正式資料
- 測試帳號：`admin` / `Admin1234`
- 測試流程涵蓋：初始化設定帳號密碼 → 登入 → 各功能驗證

測試項目：
1. 首次啟動設定管理員帳號密碼
2. 登入驗證（含密碼顯示/隱藏切換）
3. http 批量輸入
4. server 額外參數批量輸入
5. location 額外參數批量輸入
6. TLS 版本預設值與棄用標註
7. 啟用配置頁面 conf 縮進檢查

## 注意事項
- 所有改動不影響原有業務邏輯
- 打包時不包含測試資料與程式
- 測試使用獨立資料夾，保持原本代碼乾淨
