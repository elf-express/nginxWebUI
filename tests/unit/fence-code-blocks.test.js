const test = require('node:test');
const assert = require('node:assert');
const { splitBlocks } = require('../../scripts/fence-code-blocks.js');

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
