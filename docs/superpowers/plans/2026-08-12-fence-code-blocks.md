# nginx 文件程式碼區塊修正計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `docs/nginxdocumentation/` 的檔案中以 markdown 引用塊（`> `）呈現的程式碼，轉成帶語言標註的 code fence，且不動任何一個字元的程式碼內容。

**實測基準（Task 2 全語料掃描，取代本計畫初稿的估算值）：** 152 檔共 7350 行引用，
扣掉 152 行 `> Source:` 檔頭與 256 行 `> >` 巢狀引用後，6942 行進入分類器 —— 其中
程式碼 889 塊／6650 行，散文 249 塊／292 行。計畫初稿寫的「4080 行程式碼、3263 行散文」
是語料進版控前用行級啟發式估的，把 C 原始碼與單行命令誤算成散文，以此處數字為準。

**Architecture:** 新增獨立腳本 `scripts/fence-code-blocks.js`，管線為「切分區塊 → 分類 → 推斷語言 → 轉換 → 驗證」。分類與轉換以**區塊**為單位（連續 `>` 行，由空行分隔），不逐行判斷——逐行會把 C 函式的續行 `void ngx_str_rbtree_insert_value(ngx_rbtree_node_t *temp,` 誤判成散文。核心安全機制是一條可自動驗證的不變量：轉換前後，剝除標記後的文字內容必須完全相同。

**Tech Stack:** Node 24（CommonJS，原生 `fetch` 不需要）、`node:test` 內建測試框架（零新依賴，專案現有的 Playwright 只跑 E2E，兩者不衝突）。

## Global Constraints

- **不得修改任何程式碼字元。** 唯一允許的變動是剝除 `> ` 前綴、還原 markdown 跳脫（`\_` → `_`、`\*` → `*`），以及加上 fence 標記。
- **`> Source: https://...` 是檔頭 metadata，永不轉換。**
- **`> >` 巢狀引用是 nginx.org 的 note box（commercial subscription 提示），維持引用塊。**
- **散文引用塊維持引用塊。** 全庫有 3263 行散文引用，誤轉會讓說明文字變成程式碼。
- **已含 code fence 的檔案要跳過該區塊**：002page.md 與 006page.md 已人工校對完成，全庫已有 136 行 fence。
- **原文備份到 `.translate-backup/<name>.pre-fence`**，與既有的翻譯備份分開，避免互相覆蓋。
- **並發警告：** 目前有另一個代理在同一份文件上工作。執行前必須先確認目標檔案的 mtime 未在計畫產出後變動，否則先停下確認。
- 縮排必須原樣保留（原始 `<pre>` 的縮排在引用塊裡是完整的：`>     aio            on;`）。

---

## File Structure

- **Create `scripts/fence-code-blocks.js`** — 唯一的實作檔。責任：把引用塊形式的程式碼轉成 code fence。不與 `scripts/translate-docs.js` 共用程式碼；後者負責翻譯，兩者職責不同、變動原因不同。
- **Create `tests/unit/fence-code-blocks.test.js`** — 單元測試。專案目前沒有單元測試目錄，`tests/` 下只有 `e2e/`；新增 `tests/unit/` 不影響 `npm test`（它指向 `tests/e2e/playwright.config.js`）。
- **Modify `package.json`** — 新增 `"test:unit": "node --test tests/unit/"`。
- **Modify `docs/superpowers/plans/2026-08-12-nginx-docs-translation.md`** — 完工後補記本次轉換結果。

匯出介面（供測試使用）：腳本尾端 `module.exports = { splitBlocks, isCodeBlock, detectLanguage, toFence, convertFile }`，並用 `if (require.main === module) main()` 包住 CLI 進入點。

---

### Task 1: 區塊切分器

**Files:**
- Create: `scripts/fence-code-blocks.js`
- Create: `tests/unit/fence-code-blocks.test.js`
- Modify: `package.json`

**Interfaces:**
- Produces: `splitBlocks(lines: string[]) => Array<{start: number, end: number, lines: string[]}>`
  回傳所有「連續引用行」區塊，`start`/`end` 為 0-based 行索引（含頭含尾）。
  不含 `> Source:` 開頭的行，也不含 `> >` 巢狀引用行——這兩者在切分階段就排除，後續任務不必再處理。

- [ ] **Step 1: 寫失敗測試**

```javascript
// tests/unit/fence-code-blocks.test.js
const test = require('node:test');
const assert = require('node:assert');
const { splitBlocks } = require('../../scripts/fence-code-blocks.js');

test('splitBlocks 以空行切開連續引用行', () => {
  const lines = [
    'Enables AIO:',
    '',
    '> location /video/ {',
    '>     aio            on;',
    '> }',
    '',
    'On FreeBSD:',
    '',
    '> options VFS_AIO',
  ];
  const blocks = splitBlocks(lines);
  assert.strictEqual(blocks.length, 2);
  assert.deepStrictEqual(blocks[0], {
    start: 2, end: 4,
    lines: ['> location /video/ {', '>     aio            on;', '> }'],
  });
  assert.strictEqual(blocks[1].start, 8);
});

test('splitBlocks 排除 Source metadata 與巢狀引用', () => {
  const lines = [
    '> Source: https://nginx.org/en/docs/',
    '',
    '> >此模塊是商業訂閱的一部分。',
    '',
    '> server {',
  ];
  const blocks = splitBlocks(lines);
  assert.strictEqual(blocks.length, 1);
  assert.strictEqual(blocks[0].start, 4);
});

test('splitBlocks 保留區塊內的空引用行', () => {
  const lines = ['> server {', '> ', '>     listen 9000;', '> }'];
  const blocks = splitBlocks(lines);
  assert.strictEqual(blocks.length, 1);
  assert.strictEqual(blocks[0].lines.length, 4);
});
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: FAIL — `Cannot find module '../../scripts/fence-code-blocks.js'`

- [ ] **Step 3: 寫最小實作**

```javascript
#!/usr/bin/env node
/**
 * fence-code-blocks.js — 把引用塊形式的程式碼轉成 markdown code fence。
 *
 * nginx.org 的 <pre> 被抓取工具轉成了 markdown 引用塊（> ），於是設定範例
 * 在渲染時跟說明文字長得一樣，也無法語法highlight。本腳本只改呈現形式，
 * 不動任何程式碼字元。
 */
'use strict';

const QUOTE_RE = /^\s*>/;
const SOURCE_RE = /^\s*>\s*Source:/;
const NESTED_RE = /^\s*>\s*>/;

// 連續引用行構成一個區塊，由任何非引用行（含空行）分隔。
// Source metadata 與巢狀引用（nginx.org 的 note box）在這一層就排除，
// 它們永遠不該變成程式碼。
function splitBlocks(lines) {
  const blocks = [];
  let cur = null;
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    const isQuote = QUOTE_RE.test(l) && !SOURCE_RE.test(l) && !NESTED_RE.test(l);
    if (isQuote) {
      if (!cur) cur = { start: i, end: i, lines: [] };
      cur.end = i;
      cur.lines.push(l);
    } else if (cur) {
      blocks.push(cur);
      cur = null;
    }
  }
  if (cur) blocks.push(cur);
  return blocks;
}

module.exports = { splitBlocks };
```

- [ ] **Step 4: 執行測試確認通過**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: PASS，3 個測試全綠

- [ ] **Step 5: 加上 test:unit script**

在 `package.json` 的 `scripts` 區塊加入（放在 `test:pg` 之後）：

```json
"test:unit": "node --test tests/unit/"
```

- [ ] **Step 6: Commit**

```bash
git add scripts/fence-code-blocks.js tests/unit/fence-code-blocks.test.js package.json
git commit -m "feat(scripts): add quote-block splitter for fence conversion"
```

---

### Task 2: 程式碼／散文分類器

**Files:**
- Modify: `scripts/fence-code-blocks.js`
- Modify: `tests/unit/fence-code-blocks.test.js`

**Interfaces:**
- Consumes: `splitBlocks` 產出的 `{lines}`
- Produces: `isCodeBlock(blockLines: string[]) => boolean`
  以**整個區塊**為判斷單位。區塊內任一行命中程式碼特徵，整塊即為程式碼——
  這樣 C 函式的續行才不會被誤判。

- [ ] **Step 1: 寫失敗測試**

```javascript
const { isCodeBlock } = require('../../scripts/fence-code-blocks.js');

test('isCodeBlock 認得 nginx 設定', () => {
  assert.strictEqual(isCodeBlock(['> location /video/ {', '>     aio on;', '> }']), true);
});

test('isCodeBlock 認得沒有大括號的單行命令', () => {
  assert.strictEqual(isCodeBlock(['> kldload aio']), true);
  assert.strictEqual(isCodeBlock(['> ./configure --with-debug ...']), true);
  assert.strictEqual(isCodeBlock(['> options VFS\\_AIO']), true);
});

test('isCodeBlock 認得 C 原始碼與其續行', () => {
  const c = [
    '> void ngx\\_str\\_rbtree\\_insert\\_value(ngx\\_rbtree\\_node\\_t \\*temp,',
    '>     ngx\\_rbtree\\_node\\_t \\*node)',
    '> {',
    '> }',
  ];
  assert.strictEqual(isCodeBlock(c), true);
});

test('isCodeBlock 不把散文當程式碼', () => {
  assert.strictEqual(isCodeBlock(['> 此命令應以啟動 nginx 的同一使用者執行。']), false);
  assert.strictEqual(isCodeBlock(['> ACME質詢是版本化的。如果指定了非版本化名稱，則模塊會自動選擇最新版本。']), false);
  assert.strictEqual(
    isCodeBlock(['> The module supersedes the [ngx\\_http\\_status\\_module](https://nginx.org/x) module.']),
    false,
  );
});
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: FAIL — `isCodeBlock is not a function`

- [ ] **Step 3: 寫最小實作**

```javascript
// 剝掉 "> " 前綴，還原 markdown 跳脫，方便後續比對
function stripQuote(line) {
  return line.replace(/^\s*>\s?/, '');
}
function unescapeMd(s) {
  return s.replace(/\\([_*[\]`])/g, '$1');
}

// 明確的程式碼訊號。命中任一條，整個區塊就是程式碼。
const CODE_SIGNALS = [
  /[{}]\s*$/,                       // 大括號結尾：nginx 區塊、C 函式
  /;\s*$/,                          // 分號結尾：nginx 指令、C 敘述
  /^#include\b/,                    // C 前置處理器
  /^@@ -\d+/,                       // patch diff
  /^(\.\/configure|nginx|kill|service|systemctl|ps|curl|sudo|make|kldload|options|expr|set|memory|dump|while|end)\b/,
  /^[a-z_][a-z0-9_]*\s+[a-z0-9_$/.:*-]+\s*$/i,  // 「指令 參數」形式，例如 kldload aio
];

// 散文訊號：句末標點（中英文皆算）且不含程式碼訊號時，判為散文。
const PROSE_END_RE = /[.。！!？?]\s*$/;

function isCodeBlock(blockLines) {
  const bodies = blockLines.map((l) => unescapeMd(stripQuote(l))).filter((s) => s.trim());
  if (bodies.length === 0) return false;

  const hasCodeSignal = bodies.some((b) => CODE_SIGNALS.some((re) => re.test(b.trim())));
  if (hasCodeSignal) return true;

  // 沒有任何程式碼訊號：只要有一行以句末標點收尾就當散文
  return !bodies.some(PROSE_END_RE.test.bind(PROSE_END_RE));
}

module.exports = { splitBlocks, isCodeBlock, stripQuote, unescapeMd };
```

- [ ] **Step 4: 執行測試確認通過**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: PASS，7 個測試全綠

- [ ] **Step 5: Commit**

```bash
git add scripts/fence-code-blocks.js tests/unit/fence-code-blocks.test.js
git commit -m "feat(scripts): classify quote blocks as code or prose"
```

---

### Task 3: 語言推斷

**Files:**
- Modify: `scripts/fence-code-blocks.js`
- Modify: `tests/unit/fence-code-blocks.test.js`

**Interfaces:**
- Consumes: 已判定為程式碼的區塊
- Produces: `detectLanguage(blockLines: string[]) => string`
  回傳 fence 的語言標註；無法判斷時回傳空字串（產生無標註的 ```` ``` ````，
  仍比引用塊正確）。標錯語言比不標更糟，因此規則保守。

- [ ] **Step 1: 寫失敗測試**

```javascript
const { detectLanguage } = require('../../scripts/fence-code-blocks.js');

test('detectLanguage 分辨 nginx / c / diff / bash', () => {
  assert.strictEqual(detectLanguage(['> location /video/ {', '>     aio on;', '> }']), 'nginx');
  assert.strictEqual(detectLanguage(['> #include <ngx\\_config.h>']), 'c');
  assert.strictEqual(detectLanguage(['> @@ -2453,6 +2453,8 @@ ngx\\_http\\_subrequest(']), 'diff');
  assert.strictEqual(detectLanguage(['> ./configure --with-debug ...']), 'bash');
  assert.strictEqual(detectLanguage(['> nginx -s reload']), 'bash');
});

test('detectLanguage 無法判斷時回傳空字串', () => {
  assert.strictEqual(detectLanguage(['> configure arguments: --with-debug ...']), '');
});
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: FAIL — `detectLanguage is not a function`

- [ ] **Step 3: 寫最小實作**

```javascript
// 順序有意義：diff 與 C 的訊號最明確，先判；nginx 指令範圍最廣，最後判。
const NGINX_DIRECTIVES = /^(location|server|http|events|stream|upstream|mail|types|map|geo|split_clients|limit_req_zone|limit_conn_zone|proxy_pass|listen|root|index|error_log|access_log|include|ssl_certificate|add_header|rewrite|return|aio|sendfile|directio|output_buffers|resolver|acme_issuer|debug_connection)\b/;

function detectLanguage(blockLines) {
  const bodies = blockLines.map((l) => unescapeMd(stripQuote(l))).filter((s) => s.trim());
  const joined = bodies.join('\n');

  if (bodies.some((b) => /^@@ -\d+/.test(b.trim()))) return 'diff';
  if (bodies.some((b) => /^#include\b/.test(b.trim()))) return 'c';
  if (/\bngx_[a-z_]+_t\b|\bstatic\s+ngx_|\bu_char\b/.test(joined)) return 'c';
  if (bodies.some((b) => /^(\.\/configure|nginx\s|kill\s|service\s|systemctl\s|ps\s|curl\s|sudo\s|make\b|kldload\s|apt\s|yum\s)/.test(b.trim()))) return 'bash';
  if (bodies.some((b) => NGINX_DIRECTIVES.test(b.trim()))) return 'nginx';
  if (bodies.some((b) => /[{};]\s*$/.test(b.trim()))) return 'nginx';
  return '';
}

module.exports = { splitBlocks, isCodeBlock, detectLanguage, stripQuote, unescapeMd };
```

- [ ] **Step 4: 執行測試確認通過**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: PASS，9 個測試全綠

- [ ] **Step 5: Commit**

```bash
git add scripts/fence-code-blocks.js tests/unit/fence-code-blocks.test.js
git commit -m "feat(scripts): infer fence language conservatively"
```

---

### Task 4: 區塊轉換與內容不變量

**Files:**
- Modify: `scripts/fence-code-blocks.js`
- Modify: `tests/unit/fence-code-blocks.test.js`

**Interfaces:**
- Consumes: `isCodeBlock` 為 true 的區塊、`detectLanguage` 的結果
- Produces: `toFence(blockLines: string[], lang: string) => string[]`
  回傳替換用的行陣列（含頭尾 fence 標記）。這是整個計畫的核心安全點：
  剝除標記後的內容必須與原文逐字相同（除了 markdown 反跳脫）。

- [ ] **Step 1: 寫失敗測試**

```javascript
const { toFence } = require('../../scripts/fence-code-blocks.js');

test('toFence 產生帶語言標註的 fence 並保留縮排', () => {
  const out = toFence(['> location /video/ {', '>     aio            on;', '> }'], 'nginx');
  assert.deepStrictEqual(out, [
    '```nginx',
    'location /video/ {',
    '    aio            on;',
    '}',
    '```',
  ]);
});

test('toFence 還原 markdown 跳脫', () => {
  const out = toFence(['> output\\_buffers 1 64k;'], 'nginx');
  assert.strictEqual(out[1], 'output_buffers 1 64k;');
});

test('toFence 無語言標註時不留多餘字元', () => {
  assert.strictEqual(toFence(['> plain text'], '')[0], '```');
});

test('toFence 保留區塊內的空行', () => {
  const out = toFence(['> server {', '> ', '>     listen 9000;', '> }'], 'nginx');
  assert.strictEqual(out.length, 6);
  assert.strictEqual(out[2], '');
});

test('toFence 內容不變量：剝除標記後與原文逐字相同', () => {
  const src = ['> server {', '>     grpc\\_pass 127.0.0.1:9000;', '> }'];
  const out = toFence(src, 'nginx');
  const body = out.slice(1, -1).join('\n');
  const expected = src.map((l) => l.replace(/^\s*>\s?/, '').replace(/\\([_*[\]`])/g, '$1')).join('\n');
  assert.strictEqual(body, expected);
});
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: FAIL — `toFence is not a function`

- [ ] **Step 3: 寫最小實作**

```javascript
// 內容若本身含有 ``` 就升級成四個反引號，避免 fence 提早結束
function fenceMarker(bodies) {
  return bodies.some((b) => b.includes('```')) ? '````' : '```';
}

function toFence(blockLines, lang) {
  const bodies = blockLines.map((l) => unescapeMd(stripQuote(l)));
  // 去掉區塊尾端的空行，但保留中間的
  while (bodies.length && !bodies[bodies.length - 1].trim()) bodies.pop();
  const marker = fenceMarker(bodies);
  return [marker + (lang || ''), ...bodies, marker];
}
```

同時把 `toFence` 加進 `module.exports`。

- [ ] **Step 4: 執行測試確認通過**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: PASS，14 個測試全綠

- [ ] **Step 5: Commit**

```bash
git add scripts/fence-code-blocks.js tests/unit/fence-code-blocks.test.js
git commit -m "feat(scripts): convert quote blocks to fences with content invariant"
```

---

### Task 5: 檔案級轉換與安全驗證

**Files:**
- Modify: `scripts/fence-code-blocks.js`
- Modify: `tests/unit/fence-code-blocks.test.js`

**Interfaces:**
- Consumes: 前四個任務的全部函式
- Produces: `convertFile(text: string) => {text: string, converted: number, skipped: number, problems: string[]}`
  純函式，不碰檔案系統（方便測試）。`problems` 非空時呼叫端必須丟棄結果。

- [ ] **Step 1: 寫失敗測試**

```javascript
const { convertFile } = require('../../scripts/fence-code-blocks.js');

test('convertFile 轉程式碼、留散文', () => {
  const src = [
    '> Source: https://nginx.org/en/docs/x.html',
    '',
    'Enables AIO:',
    '',
    '> location /video/ {',
    '>     aio on;',
    '> }',
    '',
    '> 此命令應以啟動 nginx 的同一使用者執行。',
    '',
  ].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.ok(r.text.includes('```nginx'));
  assert.ok(r.text.includes('> 此命令應以啟動 nginx 的同一使用者執行。'));
  assert.ok(r.text.includes('> Source: https://nginx.org/en/docs/x.html'));
  assert.deepStrictEqual(r.problems, []);
});

test('convertFile 跳過已有 fence 的區域', () => {
  const src = ['```nginx', 'server {', '}', '```', '', '> server {', '> }'].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.strictEqual((r.text.match(/```/g) || []).length, 4);
});

test('convertFile 內容守恆：所有非空白字元不增不減', () => {
  const src = ['> server {', '>     grpc\\_pass 127.0.0.1:9000;', '> }'].join('\n');
  const r = convertFile(src);
  const strip = (s) => s.replace(/```[a-z]*/g, '').replace(/^\s*>\s?/gm, '').replace(/\\([_*[\]`])/g, '$1').replace(/\s+/g, '');
  assert.strictEqual(strip(r.text), strip(src));
});
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: FAIL — `convertFile is not a function`

- [ ] **Step 3: 寫最小實作**

```javascript
// 標記出已經在 fence 內的行，那些區域一律不碰
function fenceMask(lines) {
  const mask = new Array(lines.length).fill(false);
  let inside = false;
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*`{3,}/.test(lines[i])) { mask[i] = true; inside = !inside; continue; }
    mask[i] = inside;
  }
  return mask;
}

// 去掉所有標記與空白後的字元序列。轉換前後必須相同，
// 這是「不動任何程式碼字元」這條約束的機器可驗形式。
function contentFingerprint(text) {
  return text
    .replace(/^\s*`{3,}[a-z]*\s*$/gm, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/\\([_*[\]`])/g, '$1')
    .replace(/\s+/g, '');
}

function convertFile(text) {
  const lines = text.split('\n');
  const mask = fenceMask(lines);
  const blocks = splitBlocks(lines).filter((b) => !mask[b.start]);

  let converted = 0;
  let skipped = 0;
  const replacements = [];
  for (const b of blocks) {
    if (!isCodeBlock(b.lines)) { skipped++; continue; }
    replacements.push({ b, out: toFence(b.lines, detectLanguage(b.lines)) });
    converted++;
  }

  // 由後往前替換，前面的行索引才不會位移
  const out = lines.slice();
  for (const { b, out: rep } of replacements.reverse()) {
    out.splice(b.start, b.end - b.start + 1, ...rep);
  }
  const result = out.join('\n');

  const problems = [];
  if (contentFingerprint(result) !== contentFingerprint(text)) {
    problems.push('內容指紋不符，轉換改動了程式碼字元');
  }
  if ((result.match(/^\s*`{3,}/gm) || []).length % 2 !== 0) {
    problems.push('fence 標記數量為奇數，區塊未正確閉合');
  }
  return { text: result, converted, skipped, problems };
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `node --test tests/unit/fence-code-blocks.test.js`
Expected: PASS，17 個測試全綠

- [ ] **Step 5: Commit**

```bash
git add scripts/fence-code-blocks.js tests/unit/fence-code-blocks.test.js
git commit -m "feat(scripts): file-level conversion guarded by content fingerprint"
```

---

### Task 6: CLI 與乾跑報告

**Files:**
- Modify: `scripts/fence-code-blocks.js`

**Interfaces:**
- Consumes: `convertFile`
- Produces: CLI —
  `node scripts/fence-code-blocks.js --dir <path> [--dry-run] [--files N-M] [--limit N] [--sample N]`
  預設 `--dry-run`；要真的寫檔必須明確加 `--apply`。

- [ ] **Step 1: 實作 CLI**

```javascript
function parseArgs(argv) {
  const opt = { dir: null, apply: false, files: 'all', limit: Infinity, sample: 0 };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--apply') opt.apply = true;
    else if (a === '--dry-run') opt.apply = false;
    else if (a === '--dir') opt.dir = argv[++i];
    else if (a === '--files') opt.files = argv[++i];
    else if (a === '--limit') opt.limit = parseInt(argv[++i], 10);
    else if (a === '--sample') opt.sample = parseInt(argv[++i], 10);
    else throw new Error(`未知參數：${a}`);
  }
  if (!opt.dir) throw new Error('--dir 是必填的');
  return opt;
}

function main() {
  const fs = require('fs');
  const path = require('path');
  const opt = parseArgs(process.argv);

  let files = fs.readdirSync(opt.dir).filter((f) => /\.md$/i.test(f)).sort()
    .map((f) => path.join(opt.dir, f));
  if (opt.files !== 'all') {
    const m = /^(\d+)-(\d+)$/.exec(opt.files);
    if (!m) throw new Error('--files 格式應為 all 或 7-149');
    files = files.filter((f) => {
      const n = /^(\d+)/.exec(path.basename(f));
      return n && +n[1] >= +m[1] && +n[1] <= +m[2];
    });
  }
  files = files.slice(0, opt.limit);

  let totalConv = 0, totalSkip = 0, bad = 0, shown = 0;
  for (const file of files) {
    const src = fs.readFileSync(file, 'utf8');
    const r = convertFile(src);
    if (r.problems.length) {
      bad++;
      console.log(`  x ${path.basename(file)} 放棄：${r.problems.join('；')}`);
      continue;
    }
    if (r.converted === 0) continue;
    totalConv += r.converted;
    totalSkip += r.skipped;

    if (opt.sample && shown < opt.sample) {
      shown++;
      const first = r.text.split('\n').findIndex((l) => /^```/.test(l));
      console.log(`\n--- ${path.basename(file)} 轉換樣本 ---`);
      console.log(r.text.split('\n').slice(first, first + 8).join('\n'));
    }

    if (opt.apply) {
      const bk = path.join(opt.dir, '.translate-backup');
      if (!fs.existsSync(bk)) fs.mkdirSync(bk, { recursive: true });
      const pre = path.join(bk, `${path.basename(file)}.pre-fence`);
      if (!fs.existsSync(pre)) fs.writeFileSync(pre, src, 'utf8');
      fs.writeFileSync(file, r.text, 'utf8');
    }
  }

  console.log(`\n${opt.apply ? '已套用' : 'DRY RUN'}：轉換 ${totalConv} 個區塊 / 保留散文 ${totalSkip} 個 / 放棄 ${bad} 檔`);
  if (!opt.apply) console.log('確認無誤後加 --apply 才會寫檔，原文備份為 .translate-backup/<name>.pre-fence');
}

if (require.main === module) {
  try { main(); } catch (e) { console.error(`錯誤：${e.message}`); process.exit(1); }
}
```

- [ ] **Step 2: 乾跑並人工檢視樣本**

Run:
```bash
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --dry-run --sample 5
```
Expected: 報出轉換／保留數量，並印出 5 個轉換樣本。逐一確認樣本裡沒有散文被包進 fence。

- [ ] **Step 3: 針對三個代表性檔案乾跑**

Run:
```bash
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --files 7-7 --dry-run --sample 3
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --files 35-35 --dry-run --sample 3
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --files 43-43 --dry-run --sample 3
```
Expected: 007 出現 ```` ```c ````、035 出現 ```` ```nginx ````、043 出現 ```` ```nginx ````，且無 `x` 放棄訊息。

- [ ] **Step 4: Commit**

```bash
git add scripts/fence-code-blocks.js
git commit -m "feat(scripts): add dry-run CLI for fence conversion"
```

---

### Task 7: 全量套用與驗收

**Files:**
- Modify: `docs/nginxdocumentation/*.md`（109 檔）
- Modify: `docs/superpowers/plans/2026-08-12-nginx-docs-translation.md`

- [ ] **Step 1: 確認沒有並發衝突**

目前有另一個代理在同一批文件上工作。執行前先確認工作區乾淨：

```bash
git -C E:/nginxWebUI status --short docs/nginxdocumentation
```
Expected: 無輸出。**若有任何 M 或 ?? 項目就停下來，先與另一個代理確認**，否則會覆蓋對方尚未 commit 的成果。

- [ ] **Step 2: 全量乾跑，記錄基準數字**

```bash
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --dry-run
```
Expected: 放棄 0 檔。若有放棄，先修分類器再繼續——放棄代表內容指紋不符，那是真正的資料損毀風險。

- [ ] **Step 3: 套用**

```bash
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --apply
```

- [ ] **Step 4: 機器驗收**

```bash
npm run test:unit
git -C E:/nginxWebUI diff --stat docs/nginxdocumentation | tail -1
```
再跑一次乾跑，應該幾乎沒有可轉換的區塊了（冪等）：
```bash
node scripts/fence-code-blocks.js --dir E:/nginxWebUI/docs/nginxdocumentation --dry-run
```
Expected: 轉換 0 個區塊。

- [ ] **Step 5: 人工抽驗三檔**

開啟 `007page.md`、`035page.md`、`043page.md`，確認：
fence 語言標註正確、縮排完整、`\_` 已還原成 `_`、散文仍是引用塊、`> Source:` 未動、
`> >` note box 未動。

- [ ] **Step 6: 補記成果並 commit**

在 `docs/superpowers/plans/2026-08-12-nginx-docs-translation.md` 的「處理結果」表格加一列
「引用塊轉 code fence」與實際數字，然後：

```bash
git add docs/nginxdocumentation docs/superpowers/plans/2026-08-12-nginx-docs-translation.md
git commit -m "docs(nginx): render code as fenced blocks instead of quote blocks"
```

---

## 風險與退路

- **散文被誤判成程式碼**：`--dry-run --sample` 是主要防線；套用後仍可用
  `.translate-backup/<name>.pre-fence` 逐檔還原。
- **語言標錯**：不影響內容正確性，只影響 highlight。無法判斷時刻意留空標註。
- **內容指紋不符**：該檔直接放棄不寫入，屬設計內行為，不是失敗。
- **並發覆蓋**：Task 7 Step 1 的 git status 檢查是硬性關卡。
- **002／006 人工版**：兩者已是 code fence，`fenceMask` 會讓它們的 fence 區域完全不被觸碰。
