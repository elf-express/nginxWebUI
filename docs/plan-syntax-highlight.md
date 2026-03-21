# Conf 語法高亮 — 計畫書

## 目標
將啟用配置頁面的 nginx.conf 預覽從純文字 textarea 改為帶語法高亮的程式碼編輯器，類似終端機中的效果。

## 現狀
- 左側 `#nginxContent`：顯示系統生成的 conf（可編輯）
- 右側 `#org`：顯示目標 conf 檔案（唯讀）
- 使用普通 `<textarea>` + `auto-line-number.js` 自定義行號
- 無語法高亮，全部同色文字

## 改進效果

改前：
```
server {
    listen 443 ssl;
    server_name example.com;
    location / {
        proxy_pass http://127.0.0.1:3000;
    }
}
```
（全部白色文字，無法區分）

改後（預期配色）：
- **關鍵字**（server, location, if）→ 紫/藍色
- **指令名**（listen, proxy_pass）→ 青色
- **數字**（443, 1024）→ 綠色
- **字串**（路徑、域名）→ 黃/橙色
- **變數**（$host, $remote_addr）→ 紅色
- **註釋**（# ...）→ 灰色
- **大括號** → 白色加粗
- **背景** → 深色（與現有深色主題一致）

## 風險評估
- **後端** → 零改動
- **業務邏輯** → 零影響
- **相容性** → CodeMirror 5 支援 IE11+，不影響現有瀏覽器支援
- **影響範圍** → 僅啟用配置頁面（conf/index.html）

## 技術方案
引入 CodeMirror 5（輕量、穩定），替換現有 textarea。

### 需要的檔案
```
static/lib/codemirror/
├── codemirror.min.js        # 核心（~140KB gzip 後 ~45KB）
├── codemirror.min.css       # 核心樣式
├── mode/
│   └── nginx/nginx.min.js   # nginx 語法模式
└── theme/
    └── monokai.min.css      # 深色主題（與現有 UI 搭配）
```

### 修改檔案
| 檔案 | 改動 |
|------|------|
| `conf/index.html` | 引入 CodeMirror CSS/JS，移除 auto-line-number |
| `conf/index.js` | textarea → CodeMirror 初始化，getValue/setValue 適配 |

### 不改動的檔案
- `ConfService.java` — 不碰
- `ConfController.java` — 不碰
- `auto-line-number.js` — 保留（其他頁面可能用到）
- diff 功能 — 保留原有 jsdifflib

## 時間估計
約 1-2 小時（含下載 CodeMirror、整合、測試）
