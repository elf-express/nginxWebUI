const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test.describe('啟用配置頁面 conf 縮進', () => {

  test('生成的 conf 應有正確縮進', async ({ page }) => {
    await login(page);

    // 直接去啟用配置頁面（http 參數已有內容，足以驗證縮進）
    await page.goto('/adminPage/conf');
    await page.waitForTimeout(2000);

    // 讀取左側 conf 內容
    const confContent = await page.evaluate(() => {
      const cms = document.querySelectorAll('.CodeMirror');
      if (cms.length > 0 && cms[0].CodeMirror) {
        return cms[0].CodeMirror.getValue();
      }
      const ta = document.querySelector('textarea');
      if (ta) return ta.value;
      return document.body.innerText;
    });

    // conf 應包含 http 塊（http 參數測試已添加了 gzip 等）
    expect(confContent).toContain('http');

    // 驗證 http 塊內的指令有正確縮進（4 個空格）
    const lines = confContent.split('\n');
    const indentedLines = lines.filter(l => /^\s{4}\S/.test(l));

    // 至少應有幾行縮進的內容（events 和 http 塊內的指令）
    expect(indentedLines.length).toBeGreaterThanOrEqual(1);

    // 驗證 events 塊結構
    expect(confContent).toContain('events');
    expect(confContent).toContain('worker_connections');
  });

});
