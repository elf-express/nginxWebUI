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
// 檔頭 metadata：抓取工具留下的 Source 行，以及本專案翻譯版加的翻譯標注。
// 兩者都是檔案開頭的註記而非內容，永遠不該變成程式碼。
const META_RE = /^\s*>\s*(?:Source:|翻譯\s*[:：])/;
const NESTED_RE = /^\s*>\s*>/;

// 連續引用行構成一個區塊，由任何非引用行（含空行）分隔。
// 檔頭 metadata 讓整段退出（Source 與翻譯標注是同一組註記）。巢狀行不在這一層處理：
// `> >` 不一定是 note box，也可能是內容本身就有 >，得看內容才知道，交給 isCodeBlock。
function splitBlocks(lines) {
  const blocks = [];
  let cur = null;
  const flush = () => {
    if (cur && !cur.excluded) blocks.push({ start: cur.start, end: cur.end, lines: cur.lines });
    cur = null;
  };
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (!QUOTE_RE.test(l)) { flush(); continue; }
    if (!cur) cur = { start: i, end: i, lines: [], excluded: false };
    cur.end = i;
    cur.lines.push(l);
    if (META_RE.test(l)) cur.excluded = true;
  }
  flush();
  return blocks;
}

// 剝掉 "> " 前綴，還原 markdown 跳脫，方便後續比對
function stripQuote(line) {
  return line.replace(/^\s*>\s?/, '');
}
// CommonMark 的可跳脫字元集＝全部 ASCII 標點。fence 內沒有 markdown 語法，
// 引用塊裡被 markdown 吃掉的每一個反斜線，進了 fence 都會變成畫面上的字元——
// 只還原 \_ \* \[ \] \` 的話，nginx regex 的 \\. 會渲染成 \\.（068page.md:25 形態）。
// 逐字左到右單趟替換，語意與 markdown 一致：\\\\d → \\d，不是 \d。
function unescapeMd(s) {
  return s.replace(/\\([!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~])/g, '$1');
}

// 引用行 → 內容行。只剝一層 "> " 是刻意的：巢狀 `> >` 的內層 > 屬於內容本身
// （curl -v 的輸出前綴；diff 的 `--- a/...` 被抓取工具存成 `> >- a/...`，那個內層 >
// 是三個連字號的殘骸，不是引用標記），剝兩層會吃掉程式碼字元。
// 三個呼叫端共用這一層；要不要濾掉空行由呼叫端自己決定（toFence 必須留住中間空行）。
function bodyLines(blockLines) {
  return blockLines.map((l) => unescapeMd(stripQuote(l)));
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

// 散文訊號：句末標點（中英文皆算，含引出下方範例的冒號）且不含程式碼訊號時，判為散文。
const PROSE_END_RE = /[.。！!？?:：]\s*$/;

function isCodeBlock(blockLines) {
  const hasNested = blockLines.some((l) => NESTED_RE.test(l));
  const bodies = bodyLines(blockLines).filter((s) => s.trim());  // 判斷不看空行
  if (bodies.length === 0) return false;

  const hasCodeSignal = bodies.some((b) => CODE_SIGNALS.some((re) => re.test(b.trim())));

  // 含巢狀引用的區塊語意模糊：可能是 note box，也可能是內容本身就有 >
  // （curl verbose 的 HTTP header、diff 的 ---/+++、njs REPL）。只在命中
  // 明確程式碼訊號時才轉換 —— 不讓 fallback 推定把 note box 包進 code block。
  if (hasNested) return hasCodeSignal;

  if (hasCodeSignal) return true;

  // 沒有任何程式碼訊號：只要有一行以句末標點收尾就當散文
  return !bodies.some(PROSE_END_RE.test.bind(PROSE_END_RE));
}

// 順序有意義：diff 與 C 的訊號最明確，先判；nginx 指令範圍最廣，最後判。
const NGINX_DIRECTIVES = /^(location|server|http|events|stream|upstream|mail|types|map|geo|split_clients|limit_req_zone|limit_conn_zone|proxy_pass|listen|root|index|error_log|access_log|include|ssl_certificate|add_header|rewrite|return|aio|sendfile|directio|output_buffers|resolver|acme_issuer|debug_connection)\b/;

function detectLanguage(blockLines) {
  const bodies = bodyLines(blockLines).filter((s) => s.trim());  // 推斷不看空行
  const joined = bodies.join('\n');

  if (bodies.some((b) => /^@@ -\d+/.test(b.trim()))) return 'diff';
  if (bodies.some((b) => /^#include\b/.test(b.trim()))) return 'c';
  // -> 但不是 SSI 註解的 -->，否則 <!--# include ... --> 會被當成 C 的箭號
  if (/\bngx_[a-z_]+_t\b|\bstatic\s+ngx_|\bu_char\b|(?<!-)->|\bngx_[a-z_]+\s*\(/.test(joined)) return 'c';

  // njs（nginx 內嵌的 JavaScript）。全語料實測 40 塊命中這條規則；拿掉它的話
  // 其中 37 塊會掉進 rule 6 的 [{};]$ 兜底被誤標成 nginx，另外 3 塊會變成沒有標註。
  // 排在 C 之後：njs 用 r.foo 而非 r->foo，C 規則搶不走它。
  // var 後面必須跟識別字：放寬回 \bvar\b 的話 /var/run/nginx.sock 這類路徑會被當成 JS
  // （實測誤標 18 塊）。
  if (/\b(?:function|import|export|await|async|const|let)\b|\bvar\s+[A-Za-z_$]|=>/.test(joined)) return 'javascript';

  // JSON API 回應（status API）。整塊以 { 或 [ 起頭，且含 "key": 形式。
  // 排在 JS 之後：JS 區塊可能內含 JSON 字面量，反之不然。
  if (/^\s*[[{]/.test(bodies[0] || '') && /"[^"]+"\s*:/.test(joined)) return 'json';

  if (bodies.some((b) => /^(\.\/configure|nginx\s|kill\s|service\s|systemctl\s|ps\s|curl\s|sudo\s|make\b|kldload\s|apt\s|yum\s)/.test(b.trim()))) return 'bash';
  if (bodies.some((b) => NGINX_DIRECTIVES.test(b.trim()))) return 'nginx';
  if (bodies.some((b) => /[{};]\s*$/.test(b.trim()))) return 'nginx';
  return '';
}

// 內容若本身含有 ``` 就升級成四個反引號，避免 fence 提早結束
function fenceMarker(bodies) {
  return bodies.some((b) => b.includes('```')) ? '````' : '```';
}

// 把一個區塊換成 code fence。這是整個腳本唯一產出檔案內容的地方，
// 也是內容不變量的落點：除了剝一層引用前綴、還原 markdown 跳脫，以及丟掉區塊尾端的空行，
// 不動任何字元。尾端空行是唯一的例外，而且指紋把所有空白壓掉、驗不出這個差異——
// 它只靠這行註解與下面那行程式碼記著。
function toFence(blockLines, lang) {
  const bodies = bodyLines(blockLines);  // 不濾空行：區塊中間的空行是內容
  // 去掉區塊尾端的空行，但保留中間的
  while (bodies.length && !bodies[bodies.length - 1].trim()) bodies.pop();
  const marker = fenceMarker(bodies);
  return [marker + (lang || ''), ...bodies, marker];
}

// ---- 檔案級轉換 ----

const FENCE_RE = /^\s*(`{3,})(.*)$/;

// 掃過整份檔案，標出每一行跟 fence 的關係：
//   ''       fence 外
//   'marker' fence 的起訖標記行
//   'in'     fence 內的內容
// 收尾標記至少要跟開頭一樣長且不帶其他字元（CommonMark 規則）——單純看到反引號就
// 翻轉狀態的話，四反引號 fence 內含三反引號內容時會把後半份檔案整個誤判成 fence 內。
function fenceScan(lines) {
  const states = new Array(lines.length).fill('');
  let open = 0;  // 目前 fence 開頭的反引號數量，0 表示不在 fence 內
  for (let i = 0; i < lines.length; i++) {
    const m = FENCE_RE.exec(lines[i]);
    if (!open) {
      if (m) { states[i] = 'marker'; open = m[1].length; }
      continue;
    }
    if (m && m[1].length >= open && !m[2].trim()) { states[i] = 'marker'; open = 0; continue; }
    states[i] = 'in';
  }
  return { states, unclosed: open > 0 };
}

// 指紋刻意自己寫一份剝除規則，不共用 stripQuote / unescapeMd：
// 它是轉換的獨立對照組，共用同一份程式碼的話，剝除規則一起走錯也照樣對得起來。
// 這兩份 pattern 必須與 stripQuote / unescapeMd 逐字元相同，但刻意各寫一份：
// 改了一邊沒改另一邊，指紋當場對不起來、整批檔案退回，比兩邊一起走錯安全。
const FP_QUOTE_RE = /^\s*>\s?/;
const FP_ESCAPE_RE = /\\([!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~])/g;

// 去掉所有標記與空白後的字元序列。轉換前後必須相同，
// 這是「不動任何程式碼字元」這條約束的機器可驗形式。
//
// 只有 fence 外的行才剝引用前綴與還原跳脫——那正是轉換對它們做的事。fence 內的行
// 是轉換的成品（或本來就在 fence 裡的既有程式碼），一個字元都不能再動：巢狀引用
// `> > HTTP/1.1 200 OK` 的內層 > 是 curl 輸出的一部分，進了 fence 就是內容，
// 若在這裡也剝一層，轉換前後必然對不起來，好好的檔案會被誤判退回。
function contentFingerprint(text) {
  const lines = text.split('\n');
  const { states } = fenceScan(lines);
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    if (states[i] === 'marker') continue;  // fence 標記本身不是內容
    out.push(states[i] === 'in' ? lines[i] : lines[i].replace(FP_QUOTE_RE, '').replace(FP_ESCAPE_RE, '$1'));
  }
  return out.join('\n').replace(/\s+/g, '');
}

// 區塊落在清單項目或巢狀容器裡時，> 前面那段空白是容器縮排而非內容。
// stripQuote 會連它一起吃掉，toFence 也不管縮排，得在這一層補回去，
// 否則縮排區塊會整個掉出所屬的清單項目（115page.md:967 形態，全庫 49 塊）。
const INDENT_RE = /^(\s*)>/;

function reindent(lines, indent) {
  if (!indent) return lines;
  return lines.map((l) => (l.trim() ? indent + l : l));  // 空行不補，免得留下行尾空白
}

// 整個區塊共用的容器縮排；縮排不一致時回 null（null 只代表這一件事）。
//
// 只看有內容的行：空引用行不會被 reindent 碰（見上），它的縮排差異影響不到任何字元，
// 算進來只會平白退掉好檔案。反過來說，有內容的行縮排不一致就沒有正確答案可挑——
// reindent 是把「某一行的縮排」套到每一行，而指紋把所有空白壓掉、驗不出縮排錯位，
// 猜錯了會是一次無聲的破壞。這是全腳本唯一沒有機器檢查兜底的推定，所以寧可退回。
function blockIndent(blockLines) {
  const indents = blockLines
    .filter((l) => stripQuote(l).trim())
    .map((l) => INDENT_RE.exec(l)[1]);  // 區塊每一行都是引用行，必定匹配
  if (!indents.length) return '';  // 整塊都是空行；isCodeBlock 早就擋掉了，這裡只是不讓它變成 null
  return indents.every((s) => s === indents[0]) ? indents[0] : null;
}

function convertFile(text) {
  const lines = text.split('\n');
  const inputScan = fenceScan(lines);
  const seen = splitBlocks(lines);
  const blocks = seen.filter((b) => !inputScan.states[b.start]);

  let converted = 0;
  let skipped = 0;
  // 落在既有 fence 裡而被濾掉的區塊。converted + skipped 不等於區塊總數，
  // 沒有這個數字，讀報告的人分不出「這裡沒東西要轉」跟「有 N 塊被默默丟掉」。
  // 注意它只涵蓋這一層濾掉的：檔頭 Source／翻譯標注在 splitBlocks 裡就排除了，
  // 根本不會回傳到這裡，任何計數都看不到它們。
  const excluded = seen.length - blocks.length;
  const problems = [];
  const replacements = [];
  for (const b of blocks) {
    if (!isCodeBlock(b.lines)) { skipped++; continue; }
    const indent = blockIndent(b.lines);
    if (indent === null) {
      problems.push(`第 ${b.start + 1} 行起的區塊縮排不一致，無法判斷容器縮排`);
      continue;
    }
    replacements.push({ b, out: reindent(toFence(b.lines, detectLanguage(b.lines)), indent) });
    converted++;
  }

  // 由後往前替換，前面的行索引才不會位移
  const out = lines.slice();
  for (const { b, out: rep } of replacements.reverse()) {
    out.splice(b.start, b.end - b.start + 1, ...rep);
  }
  const result = out.join('\n');

  if (contentFingerprint(result) !== contentFingerprint(text)) {
    problems.push('內容指紋不符，轉換改動了程式碼字元');
  }
  // 跟輸入比對過才知道該怪誰：原檔本來就帶著沒閉合的 fence 標記時，
  // 只看輸出會把它寫成轉換的錯，讀報告的人就無從判斷這檔能不能套用。
  if (fenceScan(result.split('\n')).unclosed) {
    problems.push(inputScan.unclosed
      ? '原檔的 fence 標記本來就沒閉合，不是這次轉換造成的'
      : '轉換後 fence 標記未正確閉合');
  }
  return { text: result, converted, skipped, excluded, problems };
}

// ---- CLI ----

const USAGE = 'node scripts/fence-code-blocks.js --dir <path> [--dry-run|--apply] [--files N-M] [--limit N] [--sample N]';

function intArg(name, raw) {
  const n = Number(raw);
  // NaN 一路傳下去只會讓 slice(0, NaN) 靜靜地變成「零個檔案」——
  // 看起來跑完了，其實一個檔都沒掃。寧可當場丟錯。
  if (!Number.isInteger(n) || n < 0) throw new Error(`${name} 應為非負整數，收到：${raw}`);
  return n;
}

// 預設乾跑。要寫檔必須明確打 --apply，而且後面再出現的 --dry-run 收得回來——
// 這條旗標背後是 152 個檔案，安全的那一邊必須是不用特別做對就會發生的那一邊。
function parseArgs(argv) {
  const opt = { dir: null, apply: false, files: 'all', limit: Infinity, sample: 0 };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--apply') opt.apply = true;
    else if (a === '--dry-run') opt.apply = false;
    else if (a === '--dir') opt.dir = argv[++i];
    else if (a === '--files') opt.files = argv[++i];
    else if (a === '--limit') opt.limit = intArg('--limit', argv[++i]);
    else if (a === '--sample') opt.sample = intArg('--sample', argv[++i]);
    else throw new Error(`未知參數：${a}`);
  }
  if (!opt.dir) throw new Error('--dir 是必填的');
  return opt;
}

// 依 --files 編號範圍與 --limit 篩檔名。抽成純函式是因為範圍邊界錯了不會有任何
// 錯誤訊息——少掃一個檔的乾跑報告，長得跟掃完的一模一樣。
function selectFiles(names, files, limit) {
  let out = names.slice().sort();
  if (files !== 'all') {
    const m = /^(\d+)-(\d+)$/.exec(files);
    if (!m) throw new Error('--files 格式應為 all 或 7-149');
    out = out.filter((n) => {
      const d = /^(\d+)/.exec(n);
      return d && +d[1] >= +m[1] && +d[1] <= +m[2];
    });
  }
  return out.slice(0, limit);
}

// 第一個相異行的索引，沒有差異回 -1。
// 樣本要照出「轉換真的改了什麼」，所以直接比對輸入與輸出；找「檔案裡第一個 ```」
// 會挑到本來就存在的 fence，那種樣本什麼都證明不了。
function firstDiff(before, after) {
  const n = Math.min(before.length, after.length);
  for (let i = 0; i < n; i++) if (before[i] !== after[i]) return i;
  return before.length === after.length ? -1 : n;
}

const CLOSING_RE = /^\s*`{3,}\s*$/;

// 從 start 取一段輸出，收在 fence 的收尾標記或 max 行。
function sampleAt(lines, start, max) {
  const out = [];
  for (let i = start; i < lines.length && out.length < max; i++) {
    out.push(lines[i]);
    if (out.length > 1 && CLOSING_RE.test(lines[i])) break;
  }
  return out;
}

function main() {
  const fs = require('fs');
  const path = require('path');
  const opt = parseArgs(process.argv);

  const names = selectFiles(
    fs.readdirSync(opt.dir).filter((f) => /\.md$/i.test(f)),
    opt.files,
    opt.limit,
  );

  let totalConv = 0;
  let totalSkip = 0;
  let totalExcl = 0;
  let changed = 0;
  let bad = 0;
  let shown = 0;
  for (const name of names) {
    const file = path.join(opt.dir, name);
    const src = fs.readFileSync(file, 'utf8');
    const r = convertFile(src);

    if (r.problems.length) {
      bad++;
      console.log(`  x ${name} 放棄：${r.problems.join('；')}`);
      continue;
    }

    // 計數擺在「這檔有沒有要轉」之前：零轉換的檔案裡的散文區塊照樣是語料的一部分，
    // 漏掉它們，報告的總數就跟實際掃到的東西對不起來。
    totalConv += r.converted;
    totalSkip += r.skipped;
    totalExcl += r.excluded;
    if (r.converted === 0) continue;
    changed++;

    const after = r.text.split('\n');
    if (opt.sample && shown < opt.sample) {
      const at = firstDiff(src.split('\n'), after);
      if (at >= 0) {
        shown++;
        // 第一個相異行之前兩邊逐行相同，所以這個行號在原檔與輸出是同一個位置，
        // 讀報告的人可以直接拿去對原檔。
        const win = sampleAt(after, at, 12);
        console.log(`\n--- ${name} 轉換樣本（第 ${at + 1} 行）---`);
        console.log(win.join('\n'));
        if (!CLOSING_RE.test(win[win.length - 1])) console.log('…（樣本截斷，區塊未完）');
      }
    }

    if (opt.apply) {
      const bk = path.join(opt.dir, '.translate-backup');
      fs.mkdirSync(bk, { recursive: true });
      const pre = path.join(bk, `${name}.pre-fence`);
      // 備份只寫第一次：重跑時手上的「原文」已經是轉換後的內容，
      // 覆蓋下去就再也回不到真正的原文了。
      if (!fs.existsSync(pre)) fs.writeFileSync(pre, src, 'utf8');
      fs.writeFileSync(file, r.text, 'utf8');
    }
  }

  console.log(`\n${opt.apply ? '已套用' : 'DRY RUN'}：掃描 ${names.length} 檔，其中 ${changed} 檔有變動，放棄 ${bad} 檔`);
  console.log(`區塊：轉換 ${totalConv} 個 / 保留散文 ${totalSkip} 個 / 既有 fence 內濾除 ${totalExcl} 個`);
  console.log('（「濾除」只含落在既有 fence 裡的引用塊；檔頭 Source／翻譯標注在切分階段就排除了，不進任何計數。放棄的檔案不計入區塊數。）');
  if (!opt.apply) console.log('確認無誤後加 --apply 才會寫檔，原文備份為 .translate-backup/<name>.pre-fence（該目錄未進版控、也不會跟著 worktree 走，真正能還原的是 git）');
}

if (require.main === module) {
  try {
    main();
  } catch (e) {
    console.error(`錯誤：${e.message}\n用法：${USAGE}`);
    process.exit(1);
  }
}

module.exports = {
  splitBlocks, isCodeBlock, detectLanguage, toFence, stripQuote, unescapeMd, convertFile,
  parseArgs, selectFiles,
  // 匯出給 translate-docs.js 用：那邊原本自己用「看到反引號就翻轉」的掃描器，
  // 正是這裡刻意不用的那一種。fence 語意只准有一份。
  fenceScan,
};
