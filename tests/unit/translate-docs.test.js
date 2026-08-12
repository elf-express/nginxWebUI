const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  collectTargets, isTranslatable, stripNavText, stripNav, navBackupPath, Engine,
} = require('../../scripts/translate-docs.js');

// ---- fence 保護（F-1）----

test('collectTargets 不把四反引號 fence 裡的內容送去翻', () => {
  // 131page.md:25 形態：抓取工具把一段內含 ``` 的樣本原樣塞進區塊，轉成 fence 時
  // 升級成四個反引號。「看到反引號就翻轉狀態」的掃描器會被內層的 ``` 提早關掉 fence，
  // 於是 `location / {`、`root html;` 變成「fence 外的散文」被送去翻——
  // 那正是 001~006 被機器翻譯毀掉的形態（root html; → 根html;）。
  const lines = [
    'The following configuration is used:',   // 0  fence 外散文，該翻
    '',
    '````nginx',                              // 2  fence 開頭（四個反引號）
    '```',                                    // 3  內容自帶的三反引號，不是收尾
    '    location / {',                       // 4
    '        root html;',                     // 5  ← 天真掃描器會把這行送去翻
    '    }',
    '}',
    '```',
    '',
    '    ssl_certificate     domain.crt;',    // 10 ← 同上
    '````',                                   // 11 真正的收尾
    '',
    'In the example the connection is passed.',  // 13 fence 外散文，該翻
  ];

  assert.deepStrictEqual(collectTargets(lines), [0, 13]);

  // 講白：fence 內的設定行一行都不准進候選
  for (const i of [4, 5, 10]) {
    assert.ok(!collectTargets(lines).includes(i), `第 ${i} 行在 fence 內，不該送翻`);
  }
});

test('collectTargets 一般 fence 照樣擋，fence 之間的散文照樣翻', () => {
  const lines = [
    'Enables AIO:',            // 0
    '',
    '```nginx',
    'location /video/ {',      // 3
    '    aio on;',             // 4
    '}',
    '```',
    '',
    'On FreeBSD this also requires:',  // 8
    '',
    '```bash',
    'kldload aio',             // 11
    '```',
  ];
  assert.deepStrictEqual(collectTargets(lines), [0, 8]);
});

test('collectTargets 未閉合的 fence 之後一律不翻', () => {
  // fenceScan 對沒閉合的 fence 是「一路吃到檔尾」。寧可少翻幾行散文，
  // 也不要在結構壞掉的檔案上猜哪裡是程式碼。
  const lines = ['```nginx', 'server {', '}', '', 'This text follows an unclosed fence.'];
  assert.deepStrictEqual(collectTargets(lines), []);
});

test('isTranslatable 仍擋下引用塊裡的設定行與 metadata', () => {
  // 這批語料轉成 fence 之前靠的就是這一層；轉換後仍有 442 個區塊維持引用塊形式
  assert.strictEqual(isTranslatable('> location / {'), false);
  assert.strictEqual(isTranslatable('> Source: https://nginx.org/en/docs/'), false);
  assert.strictEqual(isTranslatable('```nginx'), false);
  assert.strictEqual(isTranslatable('| Syntax: | proxy_pass |'), false);
  assert.strictEqual(isTranslatable('Sets the address of a proxied server.'), true);
});

// ---- 批次結果對位（F-3）----

// 引擎的 HTTP 端點換成假的。順帶把 console.warn 靜音——引擎失敗時會印警告，
// 那是預期行為，不該變成測試輸出的噪音。
async function withStubbedFetch(reply, fn) {
  const realFetch = globalThis.fetch;
  const realWarn = console.warn;
  let call = 0;
  globalThis.fetch = async (url) => {
    if (String(url).includes('/translate/auth')) return { ok: true, text: async () => 'stub-token' };
    const body = typeof reply === 'function' ? reply(call++) : reply;
    return { ok: true, json: async () => body };
  };
  console.warn = () => {};
  try {
    return await fn();
  } finally {
    globalThis.fetch = realFetch;
    console.warn = realWarn;
    Engine.microsoft._token = null;   // 別把假 token 留給下一條測試
  }
}

test('tencent 回傳筆數少於送出時整批作廢，不讓譯文錯位', async () => {
  // 結果是照位置對回輸入的。少一筆的話，第 k 行就會拿到第 k+1 行的譯文，
  // 一路歪到檔尾——而 lineIsSafe（數反引號／方括號）與 sanityCheck（數全檔總量）
  // 在散文換散文的情況下完全平衡，兩道都攔不住。
  const out = await withStubbedFetch(
    { auto_translation: ['甲', '乙'] },
    () => Engine.tencent.translateBatch(['alpha', 'bravo', 'charlie'], 'zh-TW'),
  );
  // 整批 null → 呼叫端的 `if (!out) continue;` 會讓這三行都保留原文
  assert.deepStrictEqual(out, [null, null, null]);
});

test('tencent 回傳非陣列時同樣整批作廢', async () => {
  const out = await withStubbedFetch(
    { auto_translation: undefined },
    () => Engine.tencent.translateBatch(['alpha', 'bravo'], 'zh-TW'),
  );
  assert.deepStrictEqual(out, [null, null]);
});

test('tencent 筆數相符時原樣回傳', async () => {
  // 守門：長度檢查不得把好的批次也擋掉
  const out = await withStubbedFetch(
    { auto_translation: ['甲', '乙', '丙'] },
    () => Engine.tencent.translateBatch(['alpha', 'bravo', 'charlie'], 'zh-TW'),
  );
  assert.deepStrictEqual(out, ['甲', '乙', '丙']);
});

test('tencent 壞掉的那一批不會推移後面幾批的位置', async () => {
  // 一個檔通常不只一批（chunk = 20）。第一批作廢之後，第二批的譯文必須還是
  // 落在第 20~24 個位置上，否則「只壞一批」會變成「整份檔案從第 21 行起全錯」。
  const texts = Array.from({ length: 25 }, (_, i) => `line-${i}`);
  const out = await withStubbedFetch(
    (call) => (call === 0
      ? { auto_translation: ['短少的一批'] }                       // 第 1 批：20 送 1 回
      : { auto_translation: texts.slice(20).map((t) => `譯:${t}`) }),  // 第 2 批：5 送 5 回
    () => Engine.tencent.translateBatch(texts, 'zh-TW'),
  );
  assert.strictEqual(out.length, 25);
  assert.deepStrictEqual(out.slice(0, 20), Array(20).fill(null));
  assert.deepStrictEqual(out.slice(20), ['譯:line-20', '譯:line-21', '譯:line-22', '譯:line-23', '譯:line-24']);
});

test('microsoft 回傳筆數對不上時整批作廢', async () => {
  const out = await withStubbedFetch(
    [{ translations: [{ text: '甲' }] }],
    () => Engine.microsoft.translateBatch(['alpha', 'bravo', 'charlie'], 'zh-TW'),
  );
  assert.deepStrictEqual(out, [null, null, null]);
});

test('microsoft 筆數相符時原樣回傳', async () => {
  const out = await withStubbedFetch(
    [{ translations: [{ text: '甲' }] }, { translations: [{ text: '乙' }] }],
    () => Engine.microsoft.translateBatch(['alpha', 'bravo'], 'zh-TW'),
  );
  assert.deepStrictEqual(out, ['甲', '乙']);
});

// ---- 導覽表格移除（F-2）----

const NAV = '<table width="100%"><tr><td>prev</td><td>next</td></tr></table>';
const SYNTAX_TABLE = '<table cellspacing="0"><tbody><tr><th>Syntax:</th><td>pass address;</td></tr></tbody></table>';

test('stripNavText 只壓接縫的空行，不碰 fence 裡的空行', () => {
  // toFence 刻意保住區塊中間的空行（「區塊中間的空行是內容」）。
  // 舊版的 /\n{3,}/g → '\n\n' 是全檔規則，會把 fence 內的連續空行一起吃掉，
  // 而且當時連備份都沒寫，吃掉就回不來了。
  const src = [
    '# page',
    '',
    NAV,
    '',
    '```nginx',
    'http {',
    '',
    '',
    '    server {}',
    '```',
    '',
  ].join('\n');
  const { text, removed } = stripNavText(src);
  assert.strictEqual(removed, 1);
  assert.deepStrictEqual(text.split('\n'), [
    '# page',
    '',            // 接縫：原本的前後兩個空行併成一個
    '```nginx',
    'http {',
    '',            // fence 內的連續空行原封不動
    '',
    '    server {}',
    '```',
    '',
  ]);
});

test('stripNavText 不動與刪除無關的既有連續空行', () => {
  // 只修自己弄出來的接縫。別處的排版是原作者的事，改了也沒有備份以外的理由。
  const src = ['a', '', '', 'b', '', NAV, '', 'c'].join('\n');
  const { text } = stripNavText(src);
  assert.deepStrictEqual(text.split('\n'), ['a', '', '', 'b', '', 'c']);
});

test('stripNavText 放過語法表格', () => {
  // 導覽表格是 <table width="100%">，指令語法表格是 <table cellspacing="0">，
  // 兩者只差屬性——後者是文件核心，碰不得。
  const src = ['# page', '', SYNTAX_TABLE, ''].join('\n');
  const { text, removed } = stripNavText(src);
  assert.strictEqual(removed, 0);
  assert.strictEqual(text, src);
});

function tmpFile(name, body) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'strip-nav-'));
  fs.writeFileSync(path.join(dir, name), body, 'utf8');
  return dir;
}

const NAV_SRC = ['# page', '', NAV, '', 'Body text.', ''].join('\n');

test('stripNav --dry-run 只回報，不寫檔也不留備份', () => {
  const dir = tmpFile('001page.md', NAV_SRC);
  const file = path.join(dir, '001page.md');
  try {
    const r = stripNav(file, { dryRun: true });
    assert.strictEqual(r.removed, 1);
    assert.strictEqual(fs.readFileSync(file, 'utf8'), NAV_SRC);
    assert.strictEqual(fs.existsSync(path.join(dir, '.translate-backup')), false);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('stripNav 寫檔前先備份原文，重跑不覆蓋既有備份', () => {
  const dir = tmpFile('001page.md', NAV_SRC);
  const file = path.join(dir, '001page.md');
  try {
    const r = stripNav(file, {});
    assert.strictEqual(r.removed, 1);
    assert.strictEqual(fs.readFileSync(navBackupPath(file), 'utf8'), NAV_SRC);
    assert.ok(!fs.readFileSync(file, 'utf8').includes('width="100%"'));

    // 手動再改一次檔，然後重跑：備份必須還是最初的原文，否則就再也回不去了
    fs.writeFileSync(file, `${NAV}\n手動改過\n`, 'utf8');
    stripNav(file, {});
    assert.strictEqual(fs.readFileSync(navBackupPath(file), 'utf8'), NAV_SRC);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('stripNav 的備份不會蓋掉翻譯用的原文備份', () => {
  // translateFile 會從 .translate-backup/<name> 重讀原文以維持冪等。
  // stripNav 若寫同一個檔名，下一次翻譯就會把導覽表格又帶回來。
  const dir = tmpFile('001page.md', NAV_SRC);
  const file = path.join(dir, '001page.md');
  const translateBackup = path.join(dir, '.translate-backup', '001page.md');
  try {
    fs.mkdirSync(path.join(dir, '.translate-backup'), { recursive: true });
    fs.writeFileSync(translateBackup, '翻譯用的原文備份\n', 'utf8');
    stripNav(file, {});
    assert.strictEqual(fs.readFileSync(translateBackup, 'utf8'), '翻譯用的原文備份\n');
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('stripNav 沒有東西可刪時不寫檔、不建備份目錄', () => {
  const src = ['# page', '', SYNTAX_TABLE, ''].join('\n');
  const dir = tmpFile('001page.md', src);
  try {
    assert.strictEqual(stripNav(path.join(dir, '001page.md'), {}).removed, 0);
    assert.strictEqual(fs.existsSync(path.join(dir, '.translate-backup')), false);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
