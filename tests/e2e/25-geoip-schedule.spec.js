const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// 驗證 GeoIP 排程「誠實顯示」:
// 修復前 — 防護頁 GeoIP 表格「排程」欄寫死 "Wed & Sat 03:00 (UTC)"(其實只有 Docker cron
//          在跑;JAR 模式根本不會自動下載 → 使用者「根本要手動」)。
// 修復後 — 後端新增 ScheduleTask.fetchGeoip()(Java @Scheduled,每日 geoip.fetchTime 自動下載
//          + 每小時補抓缺/過期,JAR/Docker 通用),顯示改為「每日 03:00 自動更新 / Daily 03:00」。
//
// 此測試驗證「顯示已誠實」(非靠外網、可靠);實際 75MB 下載走既有且已驗證的 download() 路徑
// (網路相依,不放進自動化套件以免 flaky)。
//
// 依使用者要求:登入只做一次(beforeAll),連續測試共用 session。
test.describe.serial('GeoIP 排程顯示誠實 (Java @Scheduled, 非 Docker-cron 寫死)', () => {
  let context;
  let page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
    await login(page); // 只登入這一次
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('防護頁 GeoIP 表格「排程」欄顯示每日自動更新,不再是舊 Docker-cron 字串', async () => {
    await page.goto('/adminPage/protectionCert');
    // GeoIP 表格(Country/City/ASN)會渲染,排程欄顯示 geoipStr.scheduleValue
    await expect(page.getByText('GeoIP').first()).toBeVisible({ timeout: 10000 });

    const bodyText = await page.locator('body').innerText();
    // 新值(任一語系)應出現
    expect(bodyText).toMatch(/自動更新|自动更新|Daily 03:00/);
    // 舊的寫死 Docker-cron 描述不應再出現
    expect(bodyText).not.toMatch(/Wed & Sat/);
    expect(bodyText).not.toMatch(/03:00 \(UTC\)/);
  });
});
