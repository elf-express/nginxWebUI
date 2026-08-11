#!/usr/bin/env node
/**
 * translate-docs.js — 把 docs/nginxdocumentation 的英文頁面補上繁中翻譯。
 *
 * 翻譯引擎沿用 scripts/auto-translate.js（Via UserScript）的三個端點，改寫成 Node 版。
 * 真正的重點不是呼叫翻譯 API，而是**不可譯區段保護**：
 * 001~006 頁就是被無保護的機器翻譯毀掉的 —— `server {` 變「伺服器{」、
 * `root /data/www;` 變「根/數據/www；」、`kill -s QUIT` 變「殺死 -s 退出」。
 * 這裡把程式碼、表格、URL、指令名換成佔位符再送出，翻完還原；
 * 任何一道還原或檢查失敗，就退回原文，寧可不翻也不產出壞設定。
 *
 * 用法：
 *   node scripts/translate-docs.js --dir <docs 目錄> [選項]
 *
 *   --dir <path>        必填。docs/nginxdocumentation 的路徑
 *   --files 7-149       只處理這個編號範圍（預設 all）
 *   --engine <name>     tencent(預設) | google | microsoft
 *   --lang zh-TW        目標語言（預設 zh-TW）
 *   --dry-run           只報告會翻幾行，不呼叫 API、不寫檔
 *   --limit <n>         最多處理 n 個檔案（試跑用）
 *   --concurrency <n>   同時處理幾個檔案（預設 2）
 */

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const USAGE = `
translate-docs.js — 保護程式碼的 markdown 批次翻譯工具

  node scripts/translate-docs.js --dir <目錄> [選項]

  --dir <path>       必填。要翻譯的 markdown 目錄（預設遞迴）
  --no-recursive     只翻這一層，不進子目錄
  --match <regex>    只處理檔名符合此正則的檔案，例如 "^ngx_"
  --files 7-149      檔名開頭數字落在此範圍才處理（例如 007page.md）
  --engine <name>    tencent(預設，原生批次最快) | google（逐行、慢但穩）
                     microsoft 的 edge auth 端點目前回 404，留著備查
  --lang <code>      目標語言，預設 zh-TW（zh-CN／ja／en… 皆可）
  --profile <name>   nginx(預設) 會額外檢查 nginx 專屬的譯壞特徵；none 關閉
  --limit <n>        最多處理 n 個檔案（試跑用）
  --concurrency <n>  同時處理幾個檔案，預設 2
  --force            連手動校對過的檔案也重翻（預設會自動跳過保護）
  --dry-run          只統計不呼叫 API、不寫檔
  --print <n>        搭配 --dry-run，印出前 n 行實際送翻的遮罩結果
  -h, --help         顯示這份說明

程式碼一律不翻：fenced code、<table>/<pre>/<code> 區塊、反引號行內程式碼、
URL 與連結目標、含底線的指令名（proxy_pass、ngx\\_http\\_core\\_module）都會
換成佔位符再送出，翻完還原。還原失敗或安全檢查不過就退回原文。

原文備份在各來源目錄的 .translate-backup/，重跑會從備份讀，因此可重複執行。
`.trim();

// ── 參數 ──────────────────────────────────────────────────────────────
function parseArgs(argv) {
  const opt = {
    dir: null, files: 'all', engine: 'tencent', lang: 'zh-TW',
    dryRun: false, limit: Infinity, concurrency: 2, print: 0,
    match: null, recursive: true, profile: 'nginx', force: false,
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--dry-run') opt.dryRun = true;
    else if (a === '--help' || a === '-h') { console.log(USAGE); process.exit(0); }
    else if (a === '--no-recursive') opt.recursive = false;
    else if (a === '--force') opt.force = true;
    else if (a === '--print') opt.print = parseInt(argv[++i], 10);
    else if (a === '--dir') opt.dir = argv[++i];
    else if (a === '--files') opt.files = argv[++i];
    else if (a === '--match') opt.match = argv[++i];
    else if (a === '--profile') opt.profile = argv[++i];
    else if (a === '--engine') opt.engine = argv[++i];
    else if (a === '--lang') opt.lang = argv[++i];
    else if (a === '--limit') opt.limit = parseInt(argv[++i], 10);
    else if (a === '--concurrency') opt.concurrency = parseInt(argv[++i], 10);
    else throw new Error(`未知參數：${a}（--help 看用法）`);
  }
  if (!opt.dir) throw new Error('--dir 是必填的（--help 看用法）');
  if (!fs.existsSync(opt.dir)) throw new Error(`目錄不存在：${opt.dir}`);
  return opt;
}

// ── 不可譯區段保護 ────────────────────────────────────────────────────
// 佔位符用數學括號 U+27E6/U+27E7 包住編號，機器翻譯引擎通常原樣保留；
// 還原時容忍引擎插入的空白。絕不可用空字串，否則 includes() 恆真、保護會整個失效。
// 實測過的存活率：@0@ / %0% / [[0]] / {0} / #0# 在騰訊與 Google 都能原樣返回；
// <x0/> 會被騰訊吃掉，⟦0⟧ 會被改寫成「0」。選 @ 是因為它不跟 markdown 連結、
// nginx 大括號或標題語法相撞。
const PH_L = '@';
const PH_R = '@';
const ph = (n) => `${PH_L}${n}${PH_R}`;
const PH_RE = new RegExp(`${PH_L}\\s*(\\d+)\\s*${PH_R}`, 'g');

// 順序有意義：先吃掉大結構，再吃行內片段。
const PROTECT_RULES = [
  /<[^>]+>/g,                                   // HTML 標籤
  /`[^`\n]*`/g,                                 // 反引號行內程式碼
  // 整個 markdown 連結一起保護。只遮 ](url) 會留下孤立的 [，
  // 翻譯引擎看到未閉合的括號會自己補一個 ]，還原後就多出一個。
  /\[[^\]\n]*\]\([^)\s]*\)/g,
  /https?:\/\/\S+/g,                            // 裸 URL
  // 指令／模組名：proxy_pass、ngx_http_core_module。
  // 這批 md 的底線是跳脫過的（ngx\_mail\_smtp\_module），所以 \\? 不能省。
  /\b[a-z][a-z0-9]*(?:\\?_[a-z0-9]+)+\b/g,
  /&[a-z]+;|&#\d+;/gi,                          // HTML entity，例如 &nbsp;
];

function protect(line) {
  const slots = [];
  let masked = line;
  for (const re of PROTECT_RULES) {
    masked = masked.replace(re, (m) => {
      if (m.includes(PH_L)) return m;   // 已經是佔位符就別再包一層
      slots.push(m);
      return ph(slots.length - 1);
    });
  }
  return { masked, slots };
}

// 遮罩後剩不到兩個英文單字的行（例如 "Syntax: @0@ @1@;"）不送翻：
// 翻譯價值幾乎是零，而引擎對這種句子很容易整句亂生成。
function hasSubstance(masked) {
  const bare = masked.replace(PH_RE, ' ');
  return (bare.match(/[A-Za-z]{2,}/g) || []).length >= 2;
}

function restore(masked, slots) {
  const seen = new Set();
  let failed = false;
  const text = masked.replace(PH_RE, (_, n) => {
    const i = Number(n);
    if (!(i in slots)) { failed = true; return ''; }
    seen.add(i);
    return slots[i];
  });
  // 佔位符必須全數歸位，且不得殘留
  if (seen.size !== slots.length) failed = true;
  if (text.includes(PH_L) || text.includes(PH_R)) failed = true;
  return { text, ok: !failed };
}

// ── 行層級的可譯性判斷 ────────────────────────────────────────────────
const CJK_RE = /[㐀-鿿]/;
// 引用塊裡看起來像設定／命令的行 —— 這正是 001~006 出事的地方
const CODEISH_RE = /[{};]|^\s*>\s*(?:\.\/|nginx\b|kill\b|service\b|systemctl\b|ps\b|curl\b|sudo\b|make\b|configure\b)/;

function isTranslatable(line, inFence) {
  if (inFence) return false;
  const t = line.trim();
  if (!t) return false;
  if (t.startsWith('```') || t.startsWith('~~~')) return false;
  if (t === '---' || /^[-=*_]{3,}$/.test(t)) return false;
  if (/^#+\s*page$/i.test(t)) return false;                        // 抓取工具的模板標題
  if (t.startsWith('|')) return false;                             // markdown 表格
  if (/<(table|pre|code|tbody|tr|td|th)\b/i.test(t)) return false;  // HTML 區塊
  if (t.startsWith('>')) {
    if (t.startsWith('> Source:')) return false;                   // 抓取工具的 metadata
    if (CODEISH_RE.test(t)) return false;                          // 設定範例／命令
  }
  if (CJK_RE.test(t)) return false;                                // 已翻過 → 冪等
  if (!/[A-Za-z]{2}/.test(t)) return false;                        // 沒有實質文字
  if (/^\[[^\]]*\]\([^)]*\)$/.test(t)) return false;               // 整行只是一個連結
  return true;
}

// ── 翻譯引擎（移植自 auto-translate.js）──────────────────────────────
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 這些端點都是給瀏覽器用的，裸 fetch 不帶 UA 會被擋（Microsoft 那支甚至直接 404）
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

async function httpJson(url, init = {}) {
  const r = await fetch(url, { ...init, headers: { 'User-Agent': UA, ...(init.headers || {}) } });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

const Engine = {
  google: {
    name: 'Google (gtx)',
    async translateBatch(texts, lang) {
      const out = [];
      for (const t of texts) {
        const url = 'https://translate.googleapis.com/translate_a/single'
          + `?client=gtx&dt=t&sl=auto&tl=${encodeURIComponent(lang)}&q=${encodeURIComponent(t)}`;
        try {
          const data = await httpJson(url);
          out.push(data[0].filter((s) => s && s[0]).map((s) => s[0]).join(''));
        } catch (e) {
          console.warn(`    ! google 這行失敗（${e.message}），保留原文`);
          out.push(null);
        }
        await sleep(120); // gtx 一次只吃一條，且沒有官方配額，放慢避免被擋
      }
      return out;
    },
  },

  microsoft: {
    name: 'Microsoft (edge)',
    _token: null,
    _tokenTime: 0,
    langCode(l) {
      const m = { zh: 'zh-Hans', 'zh-CN': 'zh-Hans', 'zh-TW': 'zh-Hant' };
      return m[l] || l;
    },
    async getToken() {
      if (this._token && Date.now() - this._tokenTime < 480000) return this._token;
      const r = await fetch('https://edge.microsoft.com/translate/auth');
      if (!r.ok) throw new Error(`MS auth HTTP ${r.status}`);
      this._token = await r.text();
      this._tokenTime = Date.now();
      return this._token;
    },
    async translateBatch(texts, lang) {
      const to = this.langCode(lang);
      const out = [];
      for (let b = 0; b < texts.length; b += 25) {
        const chunk = texts.slice(b, b + 25);
        try {
          const token = await this.getToken();
          const data = await httpJson(
            `https://api-edge.cognitive.microsofttranslator.com/translate?from=&to=${to}&api-version=3.0`,
            {
              method: 'POST',
              headers: { authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
              body: JSON.stringify(chunk.map((t) => ({ Text: t }))),
            },
          );
          for (const item of data) out.push(item.translations[0].text);
        } catch (e) {
          console.warn(`    ! microsoft 這批 ${chunk.length} 行失敗（${e.message}），保留原文`);
          for (let i = 0; i < chunk.length; i++) out.push(null);
        }
        await sleep(200);
      }
      return out;
    },
  },

  tencent: {
    name: 'Tencent (transmart)',
    _clientKey: null,
    getClientKey() {
      if (!this._clientKey) {
        this._clientKey = `browser-chrome-120.0-Windows_10-${crypto.randomUUID()}-${Date.now()}`;
      }
      return this._clientKey;
    },
    langCode(l) { return l === 'zh-CN' ? 'zh' : l; },
    async translateBatch(texts, lang) {
      const to = this.langCode(lang);
      const out = [];
      for (let b = 0; b < texts.length; b += 20) {
        const chunk = texts.slice(b, b + 20);
        try {
          const data = await httpJson('https://transmart.qq.com/api/imt', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Referer: 'https://transmart.qq.com/zh-CN/index' },
            body: JSON.stringify({
              header: { fn: 'auto_translation', session: '', client_key: this.getClientKey(), user: '' },
              type: 'plain', model_category: 'normal', text_domain: 'general',
              source: { lang: 'auto', text_list: chunk }, target: { lang: to },
            }),
          });
          out.push(...data.auto_translation);
        } catch (e) {
          console.warn(`    ! tencent 這批 ${chunk.length} 行失敗（${e.message}），保留原文`);
          for (let i = 0; i < chunk.length; i++) out.push(null);
        }
        await sleep(200);
      }
      return out;
    },
  },
};

// ── 檔案級安全檢查 ────────────────────────────────────────────────────
// 譯壞的特徵，全部來自 001~006 的實際災情
const DAMAGE_PATTERNS = [
  /伺服器\s*\{/, /地點\s*\//, /位置\s*\/\w/, /根\s*\/[\w數]/,
  /錯誤\\?_log/, /訪問\\?_log/, /服務\s+nginx/, /殺死\s+-s/,
];

const count = (s, re) => (s.match(re) || []).length;

// 行層級守恆檢查。放在這一層，壞的那一行退回原文就好，
// 不必為了一行而放棄整個檔案（007page.md 有 2468 個反引號，
// 只差兩個就整份 5000 行都不翻，太浪費）。
function lineIsSafe(original, out, profile) {
  if (profile === 'nginx' && DAMAGE_PATTERNS.some((re) => re.test(out))) return false;
  for (const re of [/`/g, /\[/g, /\]/g, /\]\(/g, /<[^>]+>/g]) {
    if (count(original, re) !== count(out, re)) return false;
  }
  if (count(out, /;/g) < count(original, /;/g)) return false;
  return true;
}

function sanityCheck(original, translated, profile) {
  const problems = [];

  if (profile === 'nginx') {
    for (const re of DAMAGE_PATTERNS) {
      if (re.test(translated)) problems.push(`出現譯壞特徵 ${re}`);
    }
  }
  for (const [label, re] of [
    ['程式碼圍欄', /```/g],
    ['<table>', /<table/gi],
    ['<pre>', /<pre/gi],
    ['行數', /\n/g],
    ['反引號', /`/g],
    ['連結標記 ](', /\]\(/g],
    ['左方括號', /\[/g],
    ['右方括號', /\]/g],
  ]) {
    if (count(original, re) !== count(translated, re)) {
      problems.push(`${label}數量不符（原 ${count(original, re)} → 譯 ${count(translated, re)}）`);
    }
  }
  if (count(translated, /;/g) < count(original, /;/g)) {
    problems.push('ASCII 分號數量減少，疑似被轉成全形');
  }
  return problems;
}

// ── 單檔處理 ──────────────────────────────────────────────────────────
async function translateFile(file, opt, engine) {
  // 備份跟著檔案所在目錄走，遞迴翻多層目錄時才不會互相蓋掉同名檔
  const backupDir = path.join(path.dirname(file), '.translate-backup');
  const backup = path.join(backupDir, path.basename(file));

  // 上次腳本產出的版本。目前檔案若跟它不一樣，代表有人手動校對過，
  // 這時絕不能從備份重翻覆蓋掉人工成果（--force 才強制蓋）。
  const stamp = `${backup}.out`;
  if (!opt.force && fs.existsSync(stamp)) {
    const current = fs.readFileSync(file, 'utf8');
    if (current !== fs.readFileSync(stamp, 'utf8')) {
      return { file, translated: 0, candidates: 0, manualEdit: true };
    }
  }

  // 有備份就從備份讀，確保重跑冪等（不會拿譯文再翻一次）
  const source = fs.existsSync(backup) ? backup : file;
  const original = fs.readFileSync(source, 'utf8');

  const lines = original.split('\n');
  const targets = [];
  let inFence = false;
  for (let i = 0; i < lines.length; i++) {
    const t = lines[i].trim();
    if (t.startsWith('```') || t.startsWith('~~~')) { inFence = !inFence; continue; }
    if (isTranslatable(lines[i], inFence)) targets.push(i);
  }

  if (opt.dryRun) {
    if (opt.print > 0) {
      console.log(`\n── ${path.basename(file)} 前 ${opt.print} 行送翻內容 ──`);
      for (const i of targets.slice(0, opt.print)) {
        const { masked, slots } = protect(lines[i]);
        console.log(`  L${i + 1} 原文 : ${lines[i].slice(0, 150)}`);
        console.log(`  L${i + 1} 送翻 : ${masked.slice(0, 150)}`);
        console.log(`  L${i + 1} 保護 : ${slots.length} 段 ${JSON.stringify(slots.slice(0, 6))}`);
      }
    }
    return { file, translated: 0, candidates: targets.length, skipped: true };
  }
  if (targets.length === 0) return { file, translated: 0, candidates: 0, skipped: true };

  // 保護 → 篩掉遮罩後沒有實質文字的行 → 送翻 → 還原
  const prepared = targets
    .map((i) => ({ line: i, ...protect(lines[i]) }))
    .filter((p) => hasSubstance(p.masked));
  const results = await engine.translateBatch(prepared.map((p) => p.masked), opt.lang);

  let applied = 0;
  let restoreFailed = 0;
  let unsafe = 0;
  for (let k = 0; k < prepared.length; k++) {
    const out = results[k];
    if (!out) continue;                                   // 引擎失敗 → 留原文
    const { text, ok } = restore(out, prepared[k].slots);
    if (!ok) { restoreFailed++; continue; }               // 佔位符沒歸位 → 留原文
    const before = lines[prepared[k].line];
    if (!lineIsSafe(before, text, opt.profile)) { unsafe++; continue; }
    lines[prepared[k].line] = text;
    applied++;
  }

  const translated = lines.join('\n');
  const problems = sanityCheck(original, translated, opt.profile);
  if (problems.length) {
    return { file, translated: 0, candidates: prepared.length, rolledBack: true, problems };
  }

  if (applied > 0) {
    if (!fs.existsSync(backupDir)) fs.mkdirSync(backupDir, { recursive: true });
    if (!fs.existsSync(backup)) fs.writeFileSync(backup, original, 'utf8');
    fs.writeFileSync(file, translated, 'utf8');
    fs.writeFileSync(stamp, translated, 'utf8');   // 供下次比對，辨識人工校對
  }
  return { file, translated: applied, candidates: prepared.length, restoreFailed, unsafe };
}

// ── 主流程 ────────────────────────────────────────────────────────────
// 遞迴列出所有 markdown。以 . 開頭的目錄一律跳過，備份目錄才不會被回頭再翻一次。
function listMarkdown(dir, recursive) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name.startsWith('.')) continue;
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (recursive) out.push(...listMarkdown(full, recursive));
    } else if (/\.mdx?$/i.test(e.name)) {
      out.push(full);
    }
  }
  return out.sort();
}

function selectFiles(opt) {
  let files = listMarkdown(opt.dir, opt.recursive);

  if (opt.match) {
    const re = new RegExp(opt.match);
    files = files.filter((f) => re.test(path.basename(f)));
  }

  if (opt.files !== 'all') {
    const m = /^(\d+)-(\d+)$/.exec(opt.files);
    if (!m) throw new Error(`--files 格式應為 all 或 7-149，收到：${opt.files}`);
    const [lo, hi] = [Number(m[1]), Number(m[2])];
    files = files.filter((f) => {
      const n = /^(\d+)/.exec(path.basename(f));   // 檔名開頭的數字，例如 007page.md
      return n && Number(n[1]) >= lo && Number(n[1]) <= hi;
    });
  }
  return files;
}

async function main() {
  const opt = parseArgs(process.argv);
  const engine = Engine[opt.engine];
  if (!engine) throw new Error(`未知引擎：${opt.engine}（可用：${Object.keys(Engine).join(', ')}）`);

  const files = selectFiles(opt).slice(0, opt.limit);
  console.log(`引擎 ${engine.name} · 目標 ${opt.lang} · ${files.length} 個檔案${opt.dryRun ? ' · DRY RUN' : ''}`);

  const queue = [...files];
  const summary = { done: 0, lines: 0, rolledBack: [], restoreFailed: 0, candidates: 0, manualEdit: 0, unsafe: 0 };

  async function worker() {
    while (queue.length) {
      const file = queue.shift();
      const r = await translateFile(file, opt, engine);
      summary.done++;
      summary.lines += r.translated;
      summary.candidates += r.candidates;
      summary.restoreFailed += r.restoreFailed || 0;
      summary.unsafe += r.unsafe || 0;
      if (r.manualEdit) {
        summary.manualEdit++;
        console.log(`  - ${path.basename(file)} 已人工校對過，跳過（--force 可覆蓋）`);
      } else if (r.rolledBack) {
        summary.rolledBack.push({ file: path.basename(file), problems: r.problems });
        console.log(`  x ${path.basename(file)} 回滾：${r.problems.join('；')}`);
      } else if (!opt.dryRun) {
        console.log(`  o ${path.basename(file)} 譯了 ${r.translated}/${r.candidates} 行`);
      }
    }
  }

  await Promise.all(Array.from({ length: Math.max(1, opt.concurrency) }, worker));

  console.log('');
  if (opt.dryRun) {
    console.log(`DRY RUN：${summary.done} 個檔案，共 ${summary.candidates} 行可譯。`);
  } else {
    console.log(`完成：${summary.done} 檔 / 譯出 ${summary.lines} 行 / 候選 ${summary.candidates} 行`);
    if (summary.restoreFailed) console.log(`佔位符還原失敗而保留原文：${summary.restoreFailed} 行`);
    if (summary.unsafe) console.log(`行層級檢查不過而保留原文：${summary.unsafe} 行`);
    if (summary.manualEdit) console.log(`已人工校對而跳過：${summary.manualEdit} 檔`);
    if (summary.rolledBack.length) console.log(`安全檢查回滾：${summary.rolledBack.length} 檔`);
    console.log('原文備份在各來源目錄下的 .translate-backup/（重跑會從備份讀，不會二次翻譯）');
  }
}

main().catch((e) => { console.error(`錯誤：${e.message}`); process.exit(1); });
