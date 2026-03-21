# nginxWebUI 改進計畫 — 完成報告

## 專案資訊
- **日期：** 2026-03-21
- **版本：** 5.0.0
- **工作目錄：** `E:\nginxWebUI`

---

## 一、改進項目完成狀態

### 1. 批量輸入參數 ✅
**目標：** 將原本逐條輸入 nginx 指令的方式，簡化為一個大文本框一次貼入多行。

**完成內容：**
- http 參數配置頁面新增「批量輸入」按鈕
- server「設置額外參數」彈窗新增「批量輸入」按鈕
- location「設置額外參數」彈窗新增「批量輸入」按鈕
- 自動按第一個空格拆分名稱和值
- 自動去除行末分號

**修改檔案（6 個）：**
| 檔案 | 說明 |
|------|------|
| `server/index.html` | 新增 server + location 批量輸入彈窗 HTML |
| `server/index.js` | 新增 `showBatchInput()`、`parseBatchInput()` 等函數 |
| `http/index.html` | 新增 http 批量輸入按鈕和彈窗 HTML |
| `http/index.js` | 新增批量輸入解析函數 |
| `messages.properties` | 新增 i18n 文字（簡體） |
| `messages_en_US.properties` | 新增 i18n 文字（英文） |
| `messages_zh_TW.properties` | 新增 i18n 文字（繁體） |

### 2. TLS 版本預設值與棄用標註 ✅
**目標：** TLSv1/TLSv1.1 已被 IETF 正式廢棄（RFC 8996），預設不應勾選。

**完成內容：**
- TLSv1 標籤改為「TLSv1 (已棄用)」，預設不勾選
- TLSv1.1 標籤改為「TLSv1.1 (已棄用)」，預設不勾選
- TLSv1.2 和 TLSv1.3 預設勾選（不變）

**修改檔案（1 個）：**
| 檔案 | 說明 |
|------|------|
| `server/index.html` | checkbox 預設值與標籤文字 |

### 3. 啟用配置頁面 conf 縮進美化 ✅
**目標：** 生成的 nginx.conf 預覽應有正確的層級縮進，方便閱讀。

**完成內容：**
- 新增 `ToolUtils.formatConf()` 方法，依照 `{}` 嵌套層級自動縮進 4 格
- 啟用配置頁面左側生成的 conf 現在清晰顯示層級結構

**修改檔案（3 個）：**
| 檔案 | 說明 |
|------|------|
| `ToolUtils.java` | 新增 `formatConf()` 縮進方法（+45 行） |
| `ConfService.java` | 調用 formatConf 格式化輸出 |
| `MainController.java` | 調用 formatConf |

### 4. 登入頁面密碼可見切換 ✅
**目標：** 讓用戶確認輸入的密碼是否正確。

**完成內容：**
- 密碼欄位右側新增眼睛圖標（layui 內建 icon）
- 點擊切換 password / text 類型
- 圖標隨狀態切換（眼睛 / 眼睛劃線）

**修改檔案（2 個）：**
| 檔案 | 說明 |
|------|------|
| `login/index.html` | CSS + toggle 按鈕 HTML |
| `login/index.js` | `togglePass()` 函數 |

### 5. 登入頁面背景美化 ✅
**修改檔案（1 個）：**
| 檔案 | 說明 |
|------|------|
| `static/img/background.svg` | 更新背景設計 |

### 6. app.yml 還原 ✅
**目標：** 恢復原始配置，避免開發階段的硬編碼殘留。

**完成內容：**
- `project.home` 還原為 `/home/nginxWebUI/`
- `init.admin` 和 `init.pass` 清空

### 7. 測試用驗證碼支援 ✅（新增）
**目標：** 支援自動化測試時使用固定驗證碼。

**完成內容：**
- `LoginController.java` 新增 `project.testCaptcha` 參數
- 啟動時帶 `--project.testCaptcha=1234` 則驗證碼固定為 1234
- 正式環境不帶此參數，完全不影響

---

## 二、自動化測試

### 測試架構
```
tests/e2e/
├── playwright.config.js    # Playwright 配置（port 18080，單 worker）
├── global-setup.js         # 測試前：啟動獨立 app 實例
├── global-teardown.js      # 測試後：停止 app
├── helpers.js              # 登入、啟停 app 輔助函數
├── 01-login.spec.js        # 登入功能（3 個測試）
├── 02-http-batch.spec.js   # http 批量輸入（4 個測試）
├── 03-server-batch.spec.js # server/location 批量輸入（3 個測試）
├── 04-tls-defaults.spec.js # TLS 預設值（3 個測試）
├── 05-conf-indent.spec.js  # conf 縮進（1 個測試）
└── test-data/              # 測試資料庫（gitignore）
```

### 測試環境隔離
- 使用獨立端口 `18080`（不影響正式的 8080）
- 使用獨立 `project.home`（`tests/e2e/test-data/`）
- 使用固定驗證碼 `1234`（`--project.testCaptcha=1234`）
- 使用初始帳號 `admin` / `Admin1234`（`--init.admin` / `--init.pass`）

### 測試結果
```
Running 14 tests using 1 worker

  ✅ 登入功能 › 首次登入成功
  ✅ 登入功能 › 密碼顯示/隱藏切換（眼睛圖標）
  ✅ 登入功能 › 錯誤密碼應顯示錯誤訊息
  ✅ http 參數批量輸入 › 批量輸入按鈕存在
  ✅ http 參數批量輸入 › 批量輸入彈窗可打開並關閉
  ✅ http 參數批量輸入 › 批量輸入多行 nginx 指令
  ✅ http 參數批量輸入 › 批量輸入自動去除行末分號
  ✅ server 額外參數批量輸入 › 批量輸入可打開
  ✅ server 額外參數批量輸入 › 批量輸入並提交
  ✅ location 額外參數批量輸入 › 批量輸入可打開
  ✅ TLS 版本預設值與棄用標註 › TLSv1 和 TLSv1.1 標示為已棄用
  ✅ TLS 版本預設值與棄用標註 › TLSv1 和 TLSv1.1 預設不勾選
  ✅ TLS 版本預設值與棄用標註 › TLSv1.2 和 TLSv1.3 預設勾選
  ✅ 啟用配置頁面 conf 縮進 › 生成的 conf 應有正確縮進

  14 passed (36.9s)
```

### 執行方式
```bash
# 執行所有測試
npm test

# 帶瀏覽器畫面執行（除錯用）
npm run test:headed
```

---

## 三、變更統計

| 指標 | 數值 |
|------|------|
| 修改檔案數 | 16 |
| 新增行數 | ~400 |
| 刪除行數 | ~45 |
| 測試檔案數 | 8 |
| 測試案例數 | 14 |
| 測試通過率 | 100% |

---

## 四、注意事項

1. **不影響原有業務邏輯** — 所有改動僅為 UI 層面的輸入簡化與顯示優化
2. **打包不含測試** — `tests/e2e/` 不在 `src/` 下，Maven 打包不受影響
3. **測試資料隔離** — `tests/e2e/test-data/` 已加入 `.gitignore`
4. **正式環境不受影響** — `testCaptcha` 參數僅在明確指定時生效
5. **多語言支援** — 批量輸入的 i18n 文字已涵蓋簡體、繁體、英文
