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
  const bodies = blockLines.map((l) => unescapeMd(stripQuote(l))).filter((s) => s.trim());
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

module.exports = { splitBlocks, isCodeBlock, stripQuote, unescapeMd };
