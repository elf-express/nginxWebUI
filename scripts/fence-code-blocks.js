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
function unescapeMd(s) {
  return s.replace(/\\([_*[\]`])/g, '$1');
}

// 引用行 → 內容行。只剝一層 "> " 是刻意的：巢狀 `> >` 的內層 > 屬於內容本身
// （curl -v 的輸出前綴、diff 的 ---），剝兩層會吃掉程式碼字元。
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
  // -> 但不是 SSI 註解的 -->；var 後面必須跟識別字，否則 /var/run/nginx.sock 會被當成 JS
  if (/\bngx_[a-z_]+_t\b|\bstatic\s+ngx_|\bu_char\b|(?<!-)->|\bngx_[a-z_]+\s*\(/.test(joined)) return 'c';

  // njs（nginx 內嵌的 JavaScript）。語料有 38 塊，否則會被 rule 6 的 [{};]$ 誤標成 nginx。
  // 排在 C 之後：njs 用 r.foo 而非 r->foo，C 規則搶不走它。
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
// 也是內容不變量的落點：除了剝一層引用前綴與還原 markdown 跳脫，不動任何字元。
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
const FP_QUOTE_RE = /^\s*>\s?/;
const FP_ESCAPE_RE = /\\([_*[\]`])/g;

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

function convertFile(text) {
  const lines = text.split('\n');
  const { states } = fenceScan(lines);
  const blocks = splitBlocks(lines).filter((b) => !states[b.start]);

  let converted = 0;
  let skipped = 0;
  const replacements = [];
  for (const b of blocks) {
    if (!isCodeBlock(b.lines)) { skipped++; continue; }
    const indent = INDENT_RE.exec(b.lines[0])[1];  // 區塊每一行都是引用行，必定匹配
    replacements.push({ b, out: reindent(toFence(b.lines, detectLanguage(b.lines)), indent) });
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
  if (fenceScan(result.split('\n')).unclosed) {
    problems.push('fence 標記未正確閉合');
  }
  return { text: result, converted, skipped, problems };
}

module.exports = { splitBlocks, isCodeBlock, detectLanguage, toFence, stripQuote, unescapeMd, convertFile };
