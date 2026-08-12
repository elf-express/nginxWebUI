const test = require('node:test');
const assert = require('node:assert');
const { splitBlocks, isCodeBlock, detectLanguage } = require('../../scripts/fence-code-blocks.js');

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

test('splitBlocks 排除 Source metadata；巢狀引用改由分類器擋下', () => {
  const lines = [
    '> Source: https://nginx.org/en/docs/',
    '',
    '> >此模塊是商業訂閱的一部分。',
    '',
    '> server {',
  ];
  const blocks = splitBlocks(lines);

  // Source 檔頭仍在切分階段排除
  assert.strictEqual(blocks.length, 2);
  assert.strictEqual(blocks[0].start, 2);
  assert.strictEqual(blocks[1].start, 4);

  // note box 留在輸出裡，但不得變成程式碼——這是 Task 1 這條測試真正要守的東西
  assert.strictEqual(isCodeBlock(blocks[0].lines), false);
  assert.strictEqual(isCodeBlock(blocks[1].lines), true);
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

test('splitBlocks 保留含巢狀行的區塊，交給分類器判斷', () => {
  // 巢狀行不再切斷區塊、也不再讓整段退出：`> >` 在這份語料裡不一定是 note box，
  // 也可能是內容本身就有 >（HTTP header、diff 的 ---/+++）。改由 isCodeBlock 看內容決定。
  const blocks = splitBlocks(['> server {', '> >note', '> }']);
  assert.strictEqual(blocks.length, 1);
  assert.deepStrictEqual(blocks[0], {
    start: 0, end: 2,
    lines: ['> server {', '> >note', '> }'],
  });

  // 真實形態：巢狀首行 + 一般續行的錯誤訊息，整段是一個區塊、且判為散文（維持引用塊）
  const errMsg = [
    '> >「/some/movie/file.mp4」mp4 moov原子太大：',
    '> 12583268，您可能需要增加mp4\\_max\\_buffer\\_size',
  ];
  assert.strictEqual(splitBlocks(errMsg).length, 1);
  assert.strictEqual(isCodeBlock(errMsg), false);
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

test('isCodeBlock 用程式碼訊號救回被誤標成巢狀的樣本', () => {
  // 004page.md:33 —— git show 輸出裡的 unified diff，
  // `> >- a/...` 其實是 `--- a/...`，不是 note box
  const patch = [
    '> diff --git a/src/http/ngx\\_http\\_core\\_module.c B/src/http/ngx\\_http\\_core\\_module.c',
    '> >- a/src/http/ngx\\_http\\_core\\_module.c',
    '> >+ B/src/http/ngx\\_http\\_core\\_module.c',
    '> @@ -2453,6 +2453,8 @@ ngx\\_http\\_subrequest(ngx\\_http\\_request\\_t \\*r,',
    '> sr->方法 = NGX\\_HTTP\\_GET;',
  ];
  // 整條路徑都要通：區塊要先活過切分，才輪得到分類器判它是程式碼
  const blocks = splitBlocks(patch);
  assert.strictEqual(blocks.length, 1);
  assert.strictEqual(blocks[0].lines.length, 5);
  assert.strictEqual(isCodeBlock(blocks[0].lines), true);
});

test('isCodeBlock 讓真正的 note box 維持散文', () => {
  assert.strictEqual(isCodeBlock(['> >此模塊是我們的商業訂閱的一部分。']), false);

  // 守門條件：巢狀區塊沒有明確程式碼訊號時判散文，
  // 不走「沒有句末標點就當程式碼」的 fallback 推定（068page.md:22 真實形態）
  assert.strictEqual(isCodeBlock(['> >valid\\_referers沒有阻止server\\_names']), false);
  assert.strictEqual(isCodeBlock(['> >當前監聽隊列大小（qlen/incqlen/maxqlen）']), false);
});

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

test('detectLanguage 認得 njs JavaScript，不讓它掉進 nginx 兜底', () => {
  // 052page.md:70 —— 只靠 [{};]$ 兜底的話會被標成 nginx
  assert.strictEqual(
    detectLanguage(['> function foo(r) {', '>     r.log("hello from foo() handler");', '>     return "foo";', '> }']),
    'javascript',
  );
  // 115page.md:82 —— import 形式，同樣不得落到 nginx
  assert.strictEqual(
    detectLanguage(['> import qs from \'querystring\';', '> ', '> function args(r) {', '>     return qs.parse(r.variables.args);', '> }']),
    'javascript',
  );
});

test('detectLanguage 認得 JSON API 回應', () => {
  // 027page.md:1381 —— status API 的回應，整塊以 { 起頭
  assert.strictEqual(
    detectLanguage(['> {', '>   "nginx" : {', '>     "version" : "1.21.6"', '>   }', '> }']),
    'json',
  );
});

test('detectLanguage 認得沒有 _t 型別線索的 C', () => {
  // 007page.md:434 —— 純函式呼叫，舊規則要 _t 結尾才算 C，會掉進 nginx 兜底
  assert.strictEqual(detectLanguage(['> s = ngx\\_array\\_push(a);']), 'c');
  // 007page.md:942 —— 指標成員存取
  assert.strictEqual(detectLanguage(['> log->action = "sending mp4 to client";']), 'c');
});

test('detectLanguage 新規則沒有搶走一般 nginx 設定', () => {
  // 守門：rule 6 的兜底仍要活著——白名單外的指令區塊依舊是 nginx
  assert.strictEqual(detectLanguage(['> fastcgi\\_param SCRIPT\\_FILENAME /home/www$fastcgi\\_script\\_name;']), 'nginx');
  assert.strictEqual(detectLanguage(['> worker\\_processes    4;', '> worker\\_cpu\\_affinity 0001 0010;']), 'nginx');
});
