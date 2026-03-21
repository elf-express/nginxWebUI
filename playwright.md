# Playwright 自動化測試

## 指令

```bash
# 執行測試（帶瀏覽器 + 錄影 + 報告，一次全搞定）
npm test

# 快速測試（無瀏覽器，CI/CD 用）
npm run test:fast

# 查看報告（測試跑完後執行）
npm run report
```

## 輸出位置

```
tests/
└── playwright-report/
    ├── html/               ← HTML 報告（npm run report 開啟）
    └── test-results/       ← 影片(.webm)、截圖(.png)、trace(.zip)
```

## 看影片

測試跑完後，每個測試都有錄影：
```
tests/playwright-report/test-results/
  01-login-登入功能-首次登入成功/video.webm
  02-http-batch-...-批量輸入多行-nginx-指令/video.webm
  ...
```
直接雙擊 `.webm` 檔案即可播放。

## 看互動式 Trace

比影片更強大，可看 DOM 快照、網路請求、console：
```bash
npx playwright show-trace tests/playwright-report/test-results/01-login-登入功能-首次登入成功/trace.zip
```

## 固定端口

| 用途 | 端口 |
|------|------|
| 正式 app | 8080 |
| 測試 app | 18080 |
| HTML 報告 | 9400 |

## 速度調整

編輯 `tests/e2e/playwright.config.js` 的 `slowMo`：
- `0` → 全速（CI/CD）
- `500` → 正常觀看
- `1200` → 慢速展示（目前設定）
- `2000` → 教學演示

## 測試清單（23 個）

| 編號 | 測試項目 |
|------|---------|
| 01 | 首次登入成功 |
| 01 | 密碼顯示/隱藏切換（眼睛圖標） |
| 01 | 錯誤密碼應顯示錯誤訊息 |
| 02 | http 批量輸入按鈕存在 |
| 02 | http 批量輸入彈窗可打開並關閉 |
| 02 | http 批量輸入多行 nginx 指令 |
| 02 | http 批量輸入自動去除行末分號 |
| 03 | server 額外參數批量輸入可打開 |
| 03 | server 額外參數批量輸入並提交 |
| 03 | location 額外參數批量輸入可打開 |
| 04 | TLSv1 和 TLSv1.1 標示為已棄用 |
| 04 | TLSv1 和 TLSv1.1 預設不勾選 |
| 04 | TLSv1.2 和 TLSv1.3 預設勾選 |
| 05 | conf 應有正確縮進 |
| 06 | conf 頁面應使用 CodeMirror |
| 06 | 應使用 monokai 深色主題 |
| 06 | 應有語法高亮 token |
| 06 | 左右兩側都應有 CodeMirror |
| 07 | 應包含 gzip 相關預設參數 |
| 07 | 應包含安全 Headers 預設參數 |
| 07 | 應有 4 個預設模板 |
| 07 | WebSocket Proxy 模板應配置到 location |
| 07 | Large File Upload 模板應配置到 server |

## 詳細規範

見 [docs/playwright-guide.md](docs/playwright-guide.md)
