const test = require('node:test');
const assert = require('node:assert');
const { splitBlocks, isCodeBlock } = require('../../scripts/fence-code-blocks.js');

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

test('splitBlocks 排除翻譯檔頭 metadata', () => {
  // 真實檔頭：Source 與翻譯標注是相鄰兩行，整段都是 metadata
  const lines = [
    '> Source: https://nginx.org/en/docs/beginners\\_guide.html',
    '> 翻譯：zh-TW（人工校對）· 指令／directive／路徑保留原文',
    '',
    '> server {',
  ];
  const blocks = splitBlocks(lines);
  assert.strictEqual(blocks.length, 1);
  assert.strictEqual(blocks[0].start, 3);

  // 半形冒號的寫法同樣要排除
  assert.deepStrictEqual(splitBlocks(['> 翻譯: zh-TW（人工校對）']), []);
});

test('splitBlocks 讓含巢狀行的整段引用退出，不留半截續行', () => {
  assert.deepStrictEqual(splitBlocks(['> server {', '> >note', '> }']), []);

  // 真實形態：巢狀首行 + 一般續行的錯誤訊息，整段維持引用塊
  const errMsg = [
    '> >「/some/movie/file.mp4」mp4 moov原子太大：',
    '> 12583268，您可能需要增加mp4\\_max\\_buffer\\_size',
  ];
  assert.deepStrictEqual(splitBlocks(errMsg), []);
});

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

test('isCodeBlock 不把冒號結尾的導言句當程式碼', () => {
  // 引出下方設定範例的導言句，中英文冒號都算句末
  assert.strictEqual(
    isCodeBlock([
      '> A trailer section should be [explicitly enabled](https://datatracker.ietf.org/doc/html/rfc9110#section-6.5.1):',
    ]),
    false,
  );
  assert.strictEqual(isCodeBlock(['> 否則nginx將返回500，並記錄以下消息：']), false);

  // 程式碼訊號先判定並提前返回，冒號不影響帶大括號／分號的區塊
  assert.strictEqual(isCodeBlock(['> location / {', '>     proxy\\_pass http://backend:8080;', '> }']), true);
});
