const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const {
  splitBlocks, isCodeBlock, detectLanguage, toFence, stripQuote, unescapeMd, convertFile,
  parseArgs, selectFiles,
} = require('../../scripts/fence-code-blocks.js');

// 測試自己再寫一份剝除規則，跟 unescapeMd／FP_ESCAPE_RE 一樣刻意不共用：
// 三份都得一起改錯，錯誤才有辦法溜過去。
const UNESCAPE_RE = /\\([!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~])/g;

const CLI = path.join(__dirname, '..', '..', 'scripts', 'fence-code-blocks.js');

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

test('detectLanguage 不把路徑裡的 /var/ 當成 JS 宣告', () => {
  // 010page.md:17 形態——設定檔提到 /var/run/nginx.pid，不是 JavaScript
  assert.strictEqual(
    detectLanguage(['> user  www www;', '> worker\\_processes  2;', '> pid /var/run/nginx.pid;']),
    'nginx',
  );
  assert.strictEqual(detectLanguage(['> listen unix:/var/run/nginx.sock;']), 'nginx');
});

test('detectLanguage 不把 SSI 註解結尾的 --> 當成 C 的箭號', () => {
  // 075page.md:175 形態——SSI 標記沒有任何語言標註才是對的
  assert.strictEqual(detectLanguage(['> <!--# include virtual="/remote/body.php?argument=value" -->']), '');
  assert.strictEqual(detectLanguage(['> <!--# block name="one" -->', '> stub', '> <!--# endblock -->']), '');
});

test('detectLanguage 收緊後仍認得真的 var 宣告與真的 C 箭號', () => {
  // 守門：收緊不得矯枉過正，這兩種形態必須還在
  assert.strictEqual(detectLanguage(['> var pb = require(\'./static.js\');']), 'javascript');
  assert.strictEqual(detectLanguage(['> log->action = "sending mp4 to client";']), 'c');
});

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

test('unescapeMd 還原整套 CommonMark ASCII 標點', () => {
  // fence 內沒有 markdown 語法，引用塊裡被吃掉的每一個反斜線都得還原；
  // 少還原一個，它就會變成畫面上的字元。
  const punct = '!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~';
  assert.strictEqual(punct.length, 32);
  for (const c of punct) {
    assert.strictEqual(unescapeMd('\\' + c), c, `\\${c} 沒有被還原`);
  }
});

test('unescapeMd 不碰非標點的反斜線序列', () => {
  // \d \w \n 是 regex 與 C 的跳脫，markdown 從來沒吃掉它們——
  // 一起還原的話會把 nginx regex 改壞，那才是真的動到程式碼字元。
  assert.strictEqual(unescapeMd('\\d\\w\\s\\n\\t'), '\\d\\w\\s\\n\\t');
  assert.strictEqual(unescapeMd('location ~ \\d+'), 'location ~ \\d+');
});

test('unescapeMd 還原語料裡真實出現的四種形態', () => {
  // 這四條是 fence 轉換留下的渲染退步，逐條對回原本引用塊的渲染結果。
  // 068page.md:25 —— nginx regex，最痛的一種：\\. 會渲染成 \\.
  assert.strictEqual(unescapeMd('~\\\\.google\\\\.;'), '~\\.google\\.;');
  // 007page.md:310 —— C 字串裡的 regex，單趟替換所以 \\\\d 收斂到 \\d 而不是 \d
  assert.strictEqual(unescapeMd('ngx_string("message (\\\\\\\\d)")'), 'ngx_string("message (\\\\d)")');
  // 082page.md:204 —— 註解的 #
  assert.strictEqual(unescapeMd('\\# status is 200'), '# status is 200');
  // 115page.md:1689 —— njs REPL 的提示符
  assert.strictEqual(unescapeMd("\\>> 'x'.toUTF8()"), ">> 'x'.toUTF8()");
});

test('unescapeMd 單趟替換：還原出來的反斜線不再被吃第二次', () => {
  // \\_ 在 markdown 是「跳脫的反斜線」加上一個底線，渲染成 \_；
  // 若做兩趟（或先窄後寬地疊加），會再吃掉一次變成 _，那是實打實的內容損壞。
  assert.strictEqual(unescapeMd('\\\\_foo'), '\\_foo');
  assert.strictEqual(unescapeMd('\\\\*'), '\\*');
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
  const src = [
    '> server {',
    '>     grpc\\_pass 127.0.0.1:9000;',
    '>     server\\_name ~\\\\.example\\\\.com;',
    '> }',
  ];
  const out = toFence(src, 'nginx');
  const body = out.slice(1, -1).join('\n');
  const expected = src.map((l) => l.replace(/^\s*>\s?/, '').replace(UNESCAPE_RE, '$1')).join('\n');
  assert.strictEqual(body, expected);
  // 兩邊都由 src 推導，所以順便釘住結果長什麼樣：nginx regex 要留住單一反斜線
  assert.ok(body.includes('server_name ~\\.example\\.com;'));
});

test('stripQuote 只剝一層引用前綴', () => {
  // 巢狀的內層 > 屬於內容（curl -v 的輸出前綴、diff 的 ---），剝兩層就吃掉程式碼字元。
  // 沒有這條，把 stripQuote 改成 /^(\s*>)+\s?/ 也不會有測試變紅。
  assert.strictEqual(stripQuote('> > HTTP/1.1 200 OK'), '> HTTP/1.1 200 OK');
  assert.strictEqual(stripQuote('> >note'), '>note');
});

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
  assert.strictEqual(r.skipped, 1);
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
  assert.deepStrictEqual(r.problems, []);
});

test('convertFile 內容守恆：所有非空白字元不增不減', () => {
  // 這裡的 strip 兩邊都套一次還原，所以樣本不能留下殘餘的 \<標點>——
  // 輸出那邊會被吃第二次。真正涵蓋殘餘跳脫的是下面那條指紋測試。
  const src = ['> server {', '>     grpc\\_pass 127.0.0.1:9000;', '> }'].join('\n');
  const r = convertFile(src);
  const strip = (s) => s.replace(/```[a-z]*/g, '').replace(/^\s*>\s?/gm, '').replace(UNESCAPE_RE, '$1').replace(/\s+/g, '');
  assert.strictEqual(strip(r.text), strip(src));
});

test('convertFile 指紋跟得上加寬後的跳脫集合', () => {
  // contentFingerprint 的 FP_ESCAPE_RE 是 unescapeMd 的刻意複本：兩邊必須同步。
  // 只改一邊的話，輸入端的 \\. 不會被還原、輸出端 fence 內的 \. 原樣保留，
  // 指紋當場對不起來 —— 整檔被判成「轉換改動了程式碼字元」而退回。
  // 這條測試就是那個對照組的守門人。
  const src = [
    '> server {',
    '>     valid\\_referers ~\\\\.google\\\\.;',
    '>     # exit code is 200 or 204',
    '> }',
  ].join('\n');
  const r = convertFile(src);
  assert.deepStrictEqual(r.problems, []);
  assert.strictEqual(r.converted, 1);
  assert.ok(r.text.includes('valid_referers ~\\.google\\.;'));
  assert.ok(!r.text.includes('\\\\.'));
});

test('convertFile 指紋不誤判巢狀引用：fence 內的 > 是內容不是引用標記', () => {
  // curl -v 的輸出前綴。轉換後它落在 fence 裡，指紋若對 fence 內也剝一層 >，
  // 前後就對不起來，好好的區塊會被誤判成「改動了程式碼字元」而整檔退回。
  const src = [
    '> curl -v http://example.com/',
    '> > GET / HTTP/1.1',
    '> > Host: example.com',
  ].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.deepStrictEqual(r.problems, []);
  // 內層 > 必須原封不動留在 fence 裡
  assert.ok(r.text.includes('> GET / HTTP/1.1'));
  assert.ok(r.text.includes('> Host: example.com'));
});

test('convertFile 不把全空白的引用塊變成空 fence', () => {
  // toFence 沒有空區塊守衛，只有 isCodeBlock 點頭的區塊才准進去
  const r = convertFile(['> ', '>  '].join('\n'));
  assert.strictEqual(r.converted, 0);
  assert.strictEqual(r.skipped, 1);
  assert.ok(!r.text.includes('```'));
  assert.deepStrictEqual(r.problems, []);
});

test('convertFile 撐得住內容自帶 ``` 的區塊', () => {
  // 131page.md:25 形態：抓取工具把巢狀的程式碼樣本原樣塞進引用塊，內容裡就帶著 ```。
  // toFence 會升級成四反引號；掃描器若「看到反引號就翻轉狀態」，內層的 ``` 會把 fence
  // 提早關掉，後半份檔案整個被當成 fence 內，指紋隨即對不起來 —— 整檔被誤判退回。
  const src = [
    '> stream {',
    '> ```',
    '>     ssl\\_certificate domain.crt;',
    '> ```',
    '> }',
  ].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.deepStrictEqual(r.problems, []);
  assert.deepStrictEqual(r.text.split('\n'), [
    '````nginx',
    'stream {',
    '```',
    '    ssl_certificate domain.crt;',
    '```',
    '}',
    '````',
  ]);
});

test('convertFile 縮排不一致時回報問題，不猜著轉', () => {
  // reindent 拿第一行的縮排套到每一行，指紋又把所有空白壓掉——縮排不一致的區塊
  // 會被無聲改壞而 problems 仍是空的。這條是那個猜測的唯一機器檢查。
  const src = [
    '-   範例：',
    '',
    '    > location / {',
    '  >     aio on;',
    '    > }',
    '',
  ].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 0);
  assert.strictEqual(r.problems.length, 1);
  assert.match(r.problems[0], /縮排不一致/);
  assert.match(r.problems[0], /第 3 行/);
  assert.strictEqual(r.text, src);  // 有疑慮就整段不動
});

test('convertFile 空引用行的縮排不算不一致，縮排取自第一行有內容的行', () => {
  // 空行不會被重新縮排（reindent 跳過它），所以它的縮排差異影響不到任何字元。
  // 把空行算進去只會平白退掉好檔案。
  const src = ['  >', '    > server {', '  >', '    > }'].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.deepStrictEqual(r.problems, []);
  assert.deepStrictEqual(r.text.split('\n'), [
    '    ```nginx',
    '',
    '    server {',
    '',
    '    }',
    '    ```',
  ]);
});

test('convertFile 數出被既有 fence 濾掉的區塊', () => {
  // converted + skipped 不等於看到的區塊總數：落在既有 fence 裡的引用行
  // 兩邊都不算。少了這個計數，讀報告的人分不出「這裡沒東西要轉」跟「有 N 塊被默默丟掉」。
  const src = [
    '```nginx',
    'server {',
    '> 這行在 fence 裡，是內容不是引用塊',
    '}',
    '```',
    '',
    '> server {',
    '> }',
  ].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.strictEqual(r.skipped, 0);
  assert.strictEqual(r.excluded, 1);
  assert.deepStrictEqual(r.problems, []);
});

test('convertFile 分辨原檔就沒閉合的 fence', () => {
  // unclosed 只看輸出的話，原檔本來就壞掉的 fence 會被寫成轉換的錯。
  const src = ['```nginx', 'server {', '}', '', '> aio on;'].join('\n');
  const r = convertFile(src);
  assert.deepStrictEqual(r.problems, ['原檔的 fence 標記本來就沒閉合，不是這次轉換造成的']);
});

test('convertFile 分辨轉換自己弄壞的 fence', () => {
  // 內容自帶四個反引號：fenceMarker 只升到四個，收尾標記被內容提早關掉。
  // 這個極端情況會被擋下（整檔退回），重點是訊息要指向轉換、不能誣賴原檔。
  const r = convertFile('> ````');
  assert.ok(r.problems.includes('轉換後 fence 標記未正確閉合'));
  assert.ok(!r.problems.some((p) => p.includes('原檔')));
});

test('convertFile 保留容器縮排，不讓區塊掉出清單項目', () => {
  // 115page.md:967 形態：程式碼區塊縮在清單項目裡，> 前面帶著容器縮排。
  // toFence 產出的是不帶縮排的行，直接貼回第 0 欄會炸掉文件結構。
  const src = [
    '-   `name` is a string:',
    '',
    '    > location / {',
    '    >     aio on;',
    '    > }',
    '',
  ].join('\n');
  const r = convertFile(src);
  assert.strictEqual(r.converted, 1);
  assert.deepStrictEqual(r.problems, []);
  assert.deepStrictEqual(r.text.split('\n'), [
    '-   `name` is a string:',
    '',
    '    ```nginx',
    '    location / {',
    '        aio on;',
    '    }',
    '    ```',
    '',
  ]);
});

// ---- CLI ----

test('parseArgs 預設乾跑，只有 --apply 能開啟寫檔', () => {
  assert.strictEqual(parseArgs(['node', 's', '--dir', 'd']).apply, false);
  assert.strictEqual(parseArgs(['node', 's', '--dir', 'd', '--dry-run']).apply, false);
  assert.strictEqual(parseArgs(['node', 's', '--dir', 'd', '--apply']).apply, true);
  // 後面的 --dry-run 要能收回前面的 --apply：安全的那個選項必須贏得了
  assert.strictEqual(parseArgs(['node', 's', '--dir', 'd', '--apply', '--dry-run']).apply, false);
});

test('parseArgs 擋下缺參數與壞參數', () => {
  assert.throws(() => parseArgs(['node', 's']), /--dir/);
  assert.throws(() => parseArgs(['node', 's', '--dir']), /--dir/);
  assert.throws(() => parseArgs(['node', 's', '--dir', 'd', '--wat']), /未知參數/);
  // NaN 會讓 slice(0, NaN) 靜靜地變成「零個檔案」，看起來像跑完了其實什麼都沒掃
  assert.throws(() => parseArgs(['node', 's', '--dir', 'd', '--limit', 'abc']), /--limit/);
  assert.throws(() => parseArgs(['node', 's', '--dir', 'd', '--sample', '-1']), /--sample/);
});

test('selectFiles 依編號範圍與上限篩檔', () => {
  const names = ['035page.md', '001page.md', '115page.md', '007page.md'];
  assert.deepStrictEqual(selectFiles(names, '7-7', Infinity), ['007page.md']);
  assert.deepStrictEqual(selectFiles(names, '7-35', Infinity), ['007page.md', '035page.md']);
  assert.deepStrictEqual(selectFiles(names, 'all', 2), ['001page.md', '007page.md']);
  assert.throws(() => selectFiles(names, '7', Infinity), /--files/);
});

function tmpCorpus(files) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fence-cli-'));
  for (const [name, body] of Object.entries(files)) fs.writeFileSync(path.join(dir, name), body, 'utf8');
  return dir;
}

const SRC = ['Enables AIO:', '', '> location /video/ {', '>     aio on;', '> }', ''].join('\n');

test('CLI 預設乾跑：不寫檔、不留備份', () => {
  // 152 個檔案押在這條上——parseArgs 說 apply=false 還不夠，得看真的沒動到磁碟。
  const dir = tmpCorpus({ '001page.md': SRC });
  try {
    const out = execFileSync(process.execPath, [CLI, '--dir', dir], { encoding: 'utf8' });
    assert.match(out, /DRY RUN/);
    assert.strictEqual(fs.readFileSync(path.join(dir, '001page.md'), 'utf8'), SRC);
    assert.strictEqual(fs.existsSync(path.join(dir, '.translate-backup')), false);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('CLI --apply 寫檔並備份原文，重跑不覆蓋既有備份', () => {
  const dir = tmpCorpus({ '001page.md': SRC });
  const bk = path.join(dir, '.translate-backup', '001page.md.pre-fence');
  try {
    execFileSync(process.execPath, [CLI, '--dir', dir, '--apply'], { encoding: 'utf8' });
    assert.match(fs.readFileSync(path.join(dir, '001page.md'), 'utf8'), /```nginx/);
    assert.strictEqual(fs.readFileSync(bk, 'utf8'), SRC);

    // 第二趟的「原文」已經是轉換後的內容；備份若被覆蓋就再也回不去原文了
    execFileSync(process.execPath, [CLI, '--dir', dir, '--apply'], { encoding: 'utf8' });
    assert.strictEqual(fs.readFileSync(bk, 'utf8'), SRC);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('CLI 樣本印的是轉換真的改掉的地方，不是檔案裡第一個 fence', () => {
  // 找「檔案裡第一個 ```」會挑到本來就存在的 fence，樣本就證明不了任何事
  const src = [
    '```bash',
    'nginx -s reload',
    '```',
    '',
    '> location /video/ {',
    '>     aio on;',
    '> }',
    '',
  ].join('\n');
  const dir = tmpCorpus({ '001page.md': src });
  try {
    const out = execFileSync(process.execPath, [CLI, '--dir', dir, '--sample', '1'], { encoding: 'utf8' });
    assert.match(out, /```nginx/);
    assert.match(out, /aio on;/);
    assert.doesNotMatch(out, /nginx -s reload/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('CLI 報告的散文計數含 0 轉換的檔案', () => {
  // 「這檔沒東西可轉」不等於「這檔沒有引用塊」；漏掉它們總數就對不起來
  const dir = tmpCorpus({
    '001page.md': SRC,
    '002page.md': '> 此命令應以啟動 nginx 的同一使用者執行。\n',
  });
  try {
    const out = execFileSync(process.execPath, [CLI, '--dir', dir], { encoding: 'utf8' });
    assert.match(out, /轉換 1 /);
    assert.match(out, /保留散文 1 /);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
