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
