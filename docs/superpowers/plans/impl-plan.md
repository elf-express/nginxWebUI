# 實作計畫（開發用）

## 代碼變更清單

### 已完成的修改（15 個檔案，+381 -44）

| 檔案 | 變更說明 |
|------|--------|
| `.gitignore` | 新增忽略規則 |
| `app.yml` | 還原 project.home、清空 init.admin/pass |
| `MainController.java` | 調用 formatConf |
| `ConfService.java` | 調用 formatConf 格式化輸出 |
| `ToolUtils.java` | 新增 formatConf() 縮進方法 |
| `server/index.html` | 批量輸入彈窗 + TLS 棄用標註 + 預設值 |
| `server/index.js` | 批量輸入解析（server + location） |
| `http/index.html` | 批量輸入按鈕 + 彈窗 |
| `http/index.js` | 批量輸入解析 |
| `login/index.html` | 密碼 toggle CSS + 按鈕 |
| `login/index.js` | togglePass() 函數 |
| `background.svg` | 背景圖更新 |
| `messages.properties` | i18n（簡體） |
| `messages_en_US.properties` | i18n（英文） |
| `messages_zh_TW.properties` | i18n（繁體） |

## Playwright 測試架構

### 目錄結構
```
tests/
  e2e/
    playwright.config.js    # Playwright 配置
    setup.js                # 啟動/停止 app、初始化測試資料庫
    login.spec.js           # 登入流程測試
    http-batch.spec.js      # http 批量輸入測試
    server-batch.spec.js    # server 額外參數批量輸入測試
    tls-defaults.spec.js    # TLS 預設值測試
    conf-indent.spec.js     # conf 縮進測試
```

### 測試環境隔離
- `project.home` 指向 `tests/e2e/test-data/`（獨立於正式資料）
- 每次測試前清空 test-data 目錄
- 使用 `--server.port=18080` 避免與正式環境衝突
- 測試帳號：`admin`，密碼：`Admin1234`

### 驗證碼處理策略
- 驗證碼為 4 位純數字，存在 session 的 `captcha` key
- 測試中透過截圖 + 人工辨識不可行
- 方案：測試時使用 cookie-based session，先呼叫 `/adminPage/login/getCode` 取得 session，再透過 Playwright 截圖讀取驗證碼圖片

### 打包排除
在 `.gitignore` 中加入：
```
tests/e2e/test-data/
```
Maven 打包不受影響（tests/e2e/ 不在 src/ 下）

## 建置與執行

### 編譯
```bash
mvn clean package -DskipTests
```

### 啟動（正式）
```bash
java -jar -Dfile.encoding=UTF-8 target/nginxWebUI-5.0.1.jar --server.port=8080
```

### 執行測試
```bash
npx playwright test tests/e2e/
```
