const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const repoRoot = path.resolve(__dirname, '../..');
const releaseScript = path.join(repoRoot, 'scripts/release.sh');
const bash = process.env.BASH || (process.platform === 'win32'
  ? 'C:/Program Files/Git/bin/bash.exe' : 'bash');
const originalPom = `<project xmlns="http://maven.apache.org/POM/4.0.0">
  <parent>
    <artifactId>solon-parent</artifactId>
    <version>3.10.7</version>
  </parent>
  <artifactId>nginxWebUI</artifactId>
  <version>1.2.3</version>
</project>
`;

function fixture(t, pom = originalPom) {
  const tempRoot = fs.mkdtempSync(path.join(path.resolve(os.tmpdir()), 'nginx-release-'));
  const cwd = path.join(tempRoot, 'repo');
  fs.mkdirSync(cwd);
  t.after(() => {
    assert.equal(path.dirname(tempRoot), path.resolve(os.tmpdir()));
    fs.rmSync(tempRoot, { recursive: true, force: true });
  });
  const env = { ...process.env, GIT_AUTHOR_NAME: 'Release fixture',
    GIT_AUTHOR_EMAIL: 'fixture@example.invalid', GIT_COMMITTER_NAME: 'Release fixture',
    GIT_COMMITTER_EMAIL: 'fixture@example.invalid' };
  if (process.platform === 'win32') {
    const bin = path.join(tempRoot, 'bin');
    fs.mkdirSync(bin);
    fs.writeFileSync(path.join(bin, 'python3'), '#!/bin/sh\nexec "$RELEASE_TEST_PYTHON" "$@"\n');
    env.RELEASE_TEST_PYTHON = process.env.PYTHON || 'python';
    env.PATH = `${bin}${path.delimiter}${env.PATH}`;
  }
  function run(command, args, extraEnv = {}) {
    const result = spawnSync(command, args, { cwd, env: { ...env, ...extraEnv },
      encoding: 'utf8', timeout: 30000 });
    if (result.error) throw result.error;
    return result;
  }
  function git(...args) {
    const result = run('git', args);
    assert.equal(result.status, 0, result.stderr);
    return result.stdout.trim();
  }
  git('init', '-b', 'chore/release-fixture');
  fs.writeFileSync(path.join(cwd, '.gitattributes'), '* text=auto eol=lf\n');
  fs.writeFileSync(path.join(cwd, 'pom.xml'), pom);
  git('add', '.gitattributes', 'pom.xml');
  git('commit', '-m', 'fixture baseline');
  const before = git('rev-parse', 'HEAD');
  return { cwd, tempRoot, git, before, run,
    release: (version = '1.2.4') => run(bash, [releaseScript, version]),
    pom: () => fs.readFileSync(path.join(cwd, 'pom.xml'), 'utf8') };
}

for (const branch of ['chore/release-fixture', 'hotfix/release-fixture']) {
  test(`release.sh commits only the product version on a new ${branch} without a remote`, t => {
    const f = fixture(t);
    f.git('branch', '-m', branch);
    const result = f.release();
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.equal(f.pom(), originalPom.replace('<version>1.2.3</version>', '<version>1.2.4</version>'));
    assert.equal(f.git('diff-tree', '--no-commit-id', '--name-only', '-r', 'HEAD'), 'pom.xml');
    assert.equal(f.git('log', '-1', '--format=%s'), 'chore(release): v1.2.4');
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '2');
    assert.equal(f.git('status', '--porcelain'), '');
    assert.equal(f.git('tag', '--list'), '');
    assert.equal(f.git('remote'), '');
    assert.equal(f.git('branch', '--show-current'), branch);
  });
}

for (const branch of ['dev', 'release', 'main', 'master']) {
  test(`release.sh rejects ${branch} before changing or committing the version`, t => {
    const f = fixture(t);
    f.git('branch', '-m', branch);
    const result = f.release();
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /分支/);
    assert.equal(f.pom(), originalPom);
    assert.equal(f.git('rev-parse', 'HEAD'), f.before);
  });
}

test('release.sh rejects detached HEAD', t => {
  const f = fixture(t);
  f.git('checkout', '--detach');
  assert.notEqual(f.release().status, 0);
  assert.equal(f.pom(), originalPom);
  assert.equal(f.git('rev-parse', 'HEAD'), f.before);
});

for (const staged of [false, true]) {
  test(`release.sh preserves ${staged ? 'staged' : 'unstaged'} user changes`, t => {
    const f = fixture(t);
    const dirty = originalPom + '<!-- user change -->\n';
    fs.writeFileSync(path.join(f.cwd, 'pom.xml'), dirty);
    if (staged) f.git('add', 'pom.xml');
    assert.notEqual(f.release().status, 0);
    assert.equal(f.pom(), dirty);
    assert.equal(f.git('rev-parse', 'HEAD'), f.before);
  });
}

for (const version of ['1.2.3', 'v1.2.4', '1.2.4-beta.1', '']) {
  test(`release.sh refuses unchanged or invalid version ${JSON.stringify(version)}`, t => {
    const f = fixture(t);
    assert.notEqual(f.release(version).status, 0);
    assert.equal(f.pom(), originalPom);
    assert.equal(f.git('rev-parse', 'HEAD'), f.before);
  });
}

test('release.sh restores pom and refuses a mismatched Solon parent', t => {
  const pom = originalPom.replace('3.10.7', '9.9.9');
  const f = fixture(t, pom);
  assert.notEqual(f.release().status, 0);
  assert.equal(f.pom(), pom);
  assert.equal(f.git('rev-parse', 'HEAD'), f.before);
});

function verify(f, tag) {
  const workflow = fs.readFileSync(path.join(repoRoot, '.github/workflows/build.yml'), 'utf8');
  const match = workflow.match(/      - name: Verify tag version and release ancestry[\s\S]*?        run: \|\n((?:          [^\n]*\n|\n)+)/);
  assert.ok(match, 'release workflow must provide an executable tag verification step');
  const script = match[1].replace(/^          /gm, '');
  return f.run(bash, ['-c', script], { TAG: tag,
    GITHUB_OUTPUT: path.join(f.tempRoot, 'verify-output') });
}

test('workflow verify accepts an existing tag matching the direct project version on release', t => {
  const f = fixture(t);
  f.git('remote', 'add', 'origin', '.');
  f.git('branch', 'release');
  f.git('tag', '-a', 'v1.2.3', '-m', 'fixture');
  const result = verify(f, 'v1.2.3');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  const outputs = Object.fromEntries(fs.readFileSync(path.join(f.tempRoot, 'verify-output'), 'utf8')
    .trim().split(/\r?\n/).map(line => line.split('=')));
  assert.equal(outputs.version, '1.2.3');
  assert.equal(outputs.commit, f.before);
});

for (const scenario of ['wrong-version', 'off-release', 'missing-tag', 'branch-name']) {
  test(`workflow verify rejects ${scenario}`, t => {
    const f = fixture(t);
    f.git('remote', 'add', 'origin', '.');
    f.git('branch', 'release');
    if (scenario === 'off-release') {
      fs.writeFileSync(path.join(f.cwd, 'extra.txt'), 'untested commit\n');
      f.git('add', 'extra.txt');
      f.git('commit', '-m', 'not on release');
    }
    const tag = scenario === 'wrong-version' ? 'v9.9.9' : 'v1.2.3';
    if (scenario !== 'missing-tag') f.git('tag', '-a', tag, '-m', 'fixture');
    assert.notEqual(verify(f, scenario === 'branch-name' ? 'chore/release-fixture' : tag).status, 0);
  });
}
