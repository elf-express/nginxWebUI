const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const scripts = path.resolve(__dirname, '../../scripts');
const NAV = '<table width="100%">nav</table>\nBody\n';
const modes = {
  fence: { script: 'fence-code-blocks.js', args: ['--apply'], source: '> aio on;\n', suffix: '.pre-fence' },
  translate: { script: 'translate-docs.js', args: [], source: 'Original English sentence\n', suffix: '' },
  nav: { script: 'translate-docs.js', args: ['--strip-nav'], source: NAV, suffix: '.pre-nav' },
  repair: { script: 'translate-docs.js', args: ['--repair'], source: '目前標題\n', output: '# 目前標題\n' },
};

function fixture(t, mode) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'docs-security-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const config = modes[mode];
  const file = path.join(dir, '001page.md');
  const backupDir = path.join(dir, '.translate-backup');
  fs.mkdirSync(backupDir);
  fs.writeFileSync(file, config.source);
  if (mode === 'repair') fs.writeFileSync(path.join(backupDir, '001page.md'), '# Original heading\n');
  const stamp = path.join(backupDir, '001page.md.out');
  const backup = path.join(backupDir, '001page.md' + (config.suffix || ''));
  function run(target, action, extra = []) {
    const preload = path.join(dir, 'race.cjs');
    // Real files and real CLI processes; intercept only the race scheduling point.
    fs.writeFileSync(preload, `
      const fs = require('node:fs');
      const target = ${JSON.stringify(target)};
      const action = ${JSON.stringify(action)};
      const victim = ${JSON.stringify(path.join(dir, 'victim'))};
      const originalWrite = fs.writeFileSync;
      let fired = false;
      let translated = ${JSON.stringify(mode !== 'translate')};
      function race() {
        if (fired) return;
        if (action === 'competing-backup' && !translated) return;
        fired = true;
        if (action === 'competing-backup') originalWrite(target, 'competitor backup');
        if (action === 'disappear') fs.unlinkSync(target);
        if (action === 'replace') {
          fs.renameSync(target, target + '.detached');
          fs.linkSync(victim, target);
        }
        if (action === 'replace-symbolic') {
          fs.renameSync(target, target + '.detached');
          fs.symlinkSync(victim, target, 'file');
        }
        if (action === 'replace-regular' || action === 'replace-after-open') {
          fs.renameSync(target, target + '.detached');
          fs.renameSync(victim, target);
        }
      }
      const exists = fs.existsSync;
      fs.existsSync = function(p) { const result = exists(p); if (p === target && action !== 'replace-after-open') race(); return result; };
      const lstat = fs.lstatSync;
      fs.lstatSync = function(p, ...rest) { const result = lstat(p, ...rest); if (p === target && action !== 'replace-after-open') race(); return result; };
      const open = fs.openSync;
      // Node can bypass its JS openSync in the writeFileSync UTF-8 fast path.
      fs.writeFileSync = function(p, ...rest) {
        if (p === target && action === 'competing-backup') race();
        return originalWrite(p, ...rest);
      };
      fs.openSync = function(p, flags, ...rest) {
        if (p === target && action === 'competing-backup') race();
        const result = open(p, flags, ...rest);
        if (p === target && action === 'replace-after-open') race();
        return result;
      };
      global.fetch = async () => { translated = true; return { ok: true, json: async () => ({ auto_translation: ['譯文內容'] }) }; };
    `);
    return spawnSync(process.execPath, ['--require', preload, path.join(scripts, config.script),
      '--dir', dir, ...config.args, ...extra], { encoding: 'utf8', timeout: 3000 });
  }
  return { dir, file, backup, stamp, run, config, victim: path.join(dir, 'victim') };
}

for (const mode of ['fence', 'translate', 'nav']) {
  test(`${mode}: a competing first backup survives unchanged`, t => {
    const f = fixture(t, mode);
    const result = f.run(f.backup, 'competing-backup');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(f.backup, 'utf8'), 'competitor backup');
  });

  test(`${mode}: an existing backup symlink never overwrites its target`, t => {
    const f = fixture(t, mode);
    fs.writeFileSync(f.victim, f.config.source);
    try { fs.symlinkSync(f.victim, f.backup, 'file'); }
    catch (e) { if (e.code === 'EPERM') { t.skip('Windows symlink privilege unavailable'); return; } throw e; }
    const result = f.run(null, null);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(f.victim, 'utf8'), f.config.source);
    assert.equal(fs.lstatSync(f.backup).isSymbolicLink(), true);
  });

  test(`${mode}: a dangling backup symlink never creates its target`, t => {
    const f = fixture(t, mode);
    try { fs.symlinkSync(f.victim, f.backup, 'file'); }
    catch (e) { if (e.code === 'EPERM') { t.skip('Windows symlink privilege unavailable'); return; } throw e; }
    const result = f.run(null, null);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.existsSync(f.victim), false);
    assert.equal(fs.lstatSync(f.backup).isSymbolicLink(), true);
  });
}

for (const mode of ['repair', 'nav']) {
  test(`${mode}: an absent stamp remains absent`, t => {
    const f = fixture(t, mode);
    const result = f.run(null, null);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.existsSync(f.stamp), false);
  });

  test(`${mode}: a disappearing stamp is never recreated`, t => {
    const f = fixture(t, mode);
    fs.writeFileSync(f.stamp, 'previous output');
    const result = f.run(f.stamp, 'disappear');
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.existsSync(f.stamp), false);
  });

  test(`${mode}: replacing a stamp with an unrelated hard link cannot change the victim`, t => {
    const f = fixture(t, mode);
    fs.writeFileSync(f.stamp, 'previous output');
    fs.writeFileSync(f.victim, 'unrelated private data');
    f.run(f.stamp, 'replace');
    assert.equal(fs.readFileSync(f.victim, 'utf8'), 'unrelated private data');
  });

  test(`${mode}: replacing a stamp with a symlink preserves the victim and link`, t => {
    const f = fixture(t, mode);
    fs.writeFileSync(f.stamp, 'previous output');
    fs.writeFileSync(f.victim, 'unrelated private data');
    const probe = f.stamp + '.probe';
    try { fs.symlinkSync(f.victim, probe, 'file'); }
    catch (e) { if (e.code === 'EPERM') { t.skip('Windows symlink privilege unavailable'); return; } throw e; }
    fs.unlinkSync(probe);
    const result = f.run(f.stamp, 'replace-symbolic');
    assert.equal(result.status, 1, result.stderr);
    assert.equal(fs.readFileSync(f.victim, 'utf8'), 'unrelated private data');
    assert.equal(fs.lstatSync(f.stamp).isSymbolicLink(), true);
    assert.equal(fs.readlinkSync(f.stamp), f.victim);
  });

  for (const action of ['replace-regular', 'replace-after-open']) {
    test(`${mode}: ${action} leaves the replacement inode unchanged`, t => {
      const f = fixture(t, mode);
      fs.writeFileSync(f.stamp, 'previous output');
      fs.writeFileSync(f.victim, 'unrelated private data');
      const result = f.run(f.stamp, action);
      assert.equal(result.status, action === 'replace-regular' ? 1 : 0, result.stderr);
      assert.equal(fs.readFileSync(f.stamp, 'utf8'), 'unrelated private data');
    });
  }

  for (const link of ['hard', 'symbolic']) {
    test(`${mode}: refuses a preexisting ${link} stamp link`, t => {
      const f = fixture(t, mode);
      fs.writeFileSync(f.victim, 'unrelated private data');
      try {
        if (link === 'hard') fs.linkSync(f.victim, f.stamp);
        else fs.symlinkSync(f.victim, f.stamp, 'file');
      } catch (e) { if (e.code === 'EPERM') { t.skip('Windows symlink privilege unavailable'); return; } throw e; }
      const result = f.run(null, null);
      assert.notEqual(result.status, 0);
      assert.equal(fs.readFileSync(f.victim, 'utf8'), 'unrelated private data');
    });
  }

  test(`${mode}: updating an existing stamp truncates the old UTF-8 suffix`, t => {
    const f = fixture(t, mode);
    fs.writeFileSync(f.stamp, 'old output'.repeat(100));
    const result = f.run(null, null);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(fs.readFileSync(f.stamp, 'utf8'), mode === 'nav' ? 'Body\n' : '# 目前標題\n');
  });
}

test('--match retains regex anchors, alternation, classes, escaping and unanchored search', t => {
  const f = fixture(t, 'nav');
  fs.writeFileSync(path.join(f.dir, 'ngx_http.md'), NAV);
  fs.writeFileSync(path.join(f.dir, 'ngx_mail.mdx'), NAV);
  fs.writeFileSync(path.join(f.dir, 'unrelated.md'), NAV);
  for (const [pattern, count] of [['^ngx_(http|mail)\\.mdx?$', 2], ['[0-9]+page', 1], ['http\\.md$', 1]]) {
    const result = f.run(null, null, ['--dry-run', '--match', pattern]);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, new RegExp(`${count} 檔 / ${count} 行`));
  }
});

for (const pattern of ['[', '(?=page)', '(a)\\1']) {
  test(`--match reports invalid or unsupported RE2 regex ${pattern}`, t => {
    const f = fixture(t, 'nav');
    const result = f.run(null, null, ['--dry-run', '--match', pattern]);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /--match.*RE2/);
    assert.equal(fs.readFileSync(f.file, 'utf8'), NAV);
  });
}

test('--match nested quantifiers finish within the subprocess deadline', t => {
  const f = fixture(t, 'nav');
  fs.writeFileSync(path.join(f.dir, 'a'.repeat(36) + '!.md'), NAV);
  const result = f.run(null, null, ['--dry-run', '--match', '^(a+)+$']);
  assert.equal(result.error, undefined);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /0 檔 \/ 0 行/);
});
