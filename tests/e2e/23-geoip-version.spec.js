const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

/**
 * GeoIP 資料庫版本顯示 + 手動下載。
 *
 * 測試環境（jar，無 Docker / 無 mmdb 檔）下版本會是「尚未下載」，
 * 故只斷言「結構/接線」，不斷言具體版本日期；下載按鈕只驗證確認框彈出，
 * 不實際觸發外網下載（保持 hermetic、避免 CI 連外失敗或建立目錄副作用）。
 */
test.describe('GeoIP 版本顯示與手動下載', () => {

  test('header 應顯示 GeoIP 版本下拉項目', async ({ page }) => {
    await login(page);
    // 登入後落在 /adminPage/monitor，header 應含 GeoIP 項目
    const item = page.locator('#geoipVersionItem');
    await expect(item).toHaveCount(1);
    await expect(item).toContainText('GeoIP');
  });

  test('防護與憑證頁 Tab1 應有 GeoIP 資訊表格（3 庫 + 排程 + 下載鈕）', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    // 四顆手動下載按鈕（Country / City / ASN / Cloudflare 各一；Task 11 新增 Cloudflare 列後改為 4）
    const dlButtons = page.locator('button', { hasText: /手動下載|手动下载|Manual Download/ });
    await expect(dlButtons).toHaveCount(4);

    // 排程欄顯示每日自動更新（Java @Scheduled，含時間 03:00）
    await expect(page.locator('text=03:00').first()).toBeVisible();
  });

  test('點手動下載按鈕應彈出確認框（按鈕已正確接線）', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.locator('button', { hasText: /手動下載|手动下载/ }).first().click();

    // layer.confirm 對話框出現
    const confirmBox = page.locator('.layui-layer-dialog');
    await expect(confirmBox).toBeVisible({ timeout: 5000 });

    // 取消，不實際觸發外網下載
    const cancelBtn = page.locator('.layui-layer-btn1');
    if (await cancelBtn.count() > 0) {
      await cancelBtn.click();
    } else {
      await page.locator('.layui-layer-btn0').click();
    }
  });

  test('GET /adminPage/geoip/versions 回四個資料庫資訊（含 Cloudflare）', async ({ page, baseURL }) => {
    await login(page);

    const res = await page.request.get(baseURL + '/adminPage/geoip/versions');
    const data = await res.json();
    expect(data.success).toBe(true);
    expect(Array.isArray(data.obj)).toBe(true);
    // Task 11 新增 Cloudflare 列後回 4 筆（country / city / asn / cloudflare）
    expect(data.obj.length).toBe(4);

    const keys = data.obj.map((o) => o.key).sort();
    expect(keys).toEqual(['asn', 'city', 'cloudflare', 'country']);

    for (const o of data.obj) {
      expect(o).toHaveProperty('displayName');
      expect(o).toHaveProperty('scheduleStr');
    }
  });
});
