const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const IMG_DIR = path.resolve(__dirname, '../../src/main/resources/static/img');

test('tw.svg 含青天白日結構（紅底 + 藍底 + 12 道光芒）', () => {
  const svg = fs.readFileSync(path.join(IMG_DIR, 'tw.svg'), 'utf8');
  expect(svg).toContain('#FE0000');
  expect(svg).toContain('#000095');
  expect(svg).toMatch(/rotate\(30\)[\s\S]*rotate\(330\)/);
  expect(svg).toContain('<circle r="50"');
});

test('cn.svg 含五星紅旗結構（紅底 + 大星 + 4 顆小星）', () => {
  const svg = fs.readFileSync(path.join(IMG_DIR, 'cn.svg'), 'utf8');
  expect(svg).toContain('#DE2910');
  expect(svg).toContain('#FFDE00');
  const useStars = (svg.match(/<use href="#star"/g) || []).length;
  expect(useStars).toBe(5);
});

test('gb.svg 含米字旗結構（藍底 + 紅白十字）', () => {
  const svg = fs.readFileSync(path.join(IMG_DIR, 'gb.svg'), 'utf8');
  expect(svg).toContain('#012169');           // 藍底
  expect(svg).toContain('#C8102E');           // 紅
  expect(svg).toMatch(/#FFFFFF|#fff(?![0-9a-fA-F])|"white"/);  // 白（接受三種寫法）
  expect(svg).toMatch(/clip-path/);           // 對角線階梯效果靠 clipPath
  expect(svg).toMatch(/stroke-width/);        // 線寬定義
});
