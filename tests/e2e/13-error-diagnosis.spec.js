const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('啟用配置 - 錯誤診斷', () => {

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/conf');
    await page.waitForSelector('.CodeMirror');
  });

  test('parseNginxErrors 函數存在', async ({ page }) => {
    const exists = await page.evaluate(() => typeof parseNginxErrors === 'function');
    expect(exists).toBe(true);
  });

  test('成功訊息不產生診斷區', async ({ page }) => {
    const result = await page.evaluate(() => {
      return parseNginxErrors('<pre>nginx: the configuration file /etc/nginx/nginx.conf syntax is ok</pre>');
    });
    // 成功時不應有診斷區（含 #fff3e0 背景色的 div）
    expect(result).not.toContain('#fff3e0');
  });

  test('unknown directive 錯誤產生診斷', async ({ page }) => {
    const result = await page.evaluate(() => {
      return parseNginxErrors('<pre>nginx: [emerg] unknown directive "gzp" in /etc/nginx/nginx.conf:10</pre>');
    });
    // 應有診斷區（橙色背景）和錯誤內容
    expect(result).toContain('#fff3e0');
    expect(result).toContain('gzp');
  });

  test('host not found 錯誤產生診斷', async ({ page }) => {
    const result = await page.evaluate(() => {
      return parseNginxErrors('<pre>nginx: [emerg] host not found in upstream "backend.local" in /etc/nginx/nginx.conf:20</pre>');
    });
    expect(result).toContain('#fff3e0');
    expect(result).toContain('backend.local');
  });

  test('bind() failed 錯誤產生診斷', async ({ page }) => {
    const result = await page.evaluate(() => {
      return parseNginxErrors('<pre>nginx: [emerg] bind() to 0.0.0.0:80 failed (98: Address already in use)</pre>');
    });
    expect(result).toContain('#fff3e0');
    expect(result).toContain('80');
  });

  test('多個錯誤產生多個診斷卡片', async ({ page }) => {
    const result = await page.evaluate(() => {
      return parseNginxErrors(
        '<pre>nginx: [emerg] unknown directive "gzp" in /etc/nginx/nginx.conf:10\n' +
        'nginx: [emerg] host not found in upstream "backend.local"</pre>'
      );
    });
    expect(result).toContain('#fff3e0');
    // 應包含兩個不同的診斷
    expect(result).toContain('gzp');
    expect(result).toContain('backend.local');
  });

});
