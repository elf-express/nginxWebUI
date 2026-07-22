const { test, expect } = require('@playwright/test');
const { login, BASE_URL } = require('./helpers');

test.describe('黑白名單 Tag 編輯器', () => {

  test('透過 API 新增後列表顯示名稱和 IP 數量', async ({ page, baseURL }) => {
    await login(page);

    // 先透過 API 新增一筆
    const addResp = await page.request.post(baseURL + '/adminPage/denyAllow/addOver', {
      form: { id: '', name: 'TestList', ip: '192.168.1.1\n10.0.0.1\n172.16.0.0/12' }
    });
    const addData = await addResp.json();
    expect(addData.success).toBe(true);

    // 進入合併頁面檢查
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    const content = await page.content();
    expect(content).toContain('TestList');
    // 應顯示 IP 數量 = 3
    expect(content).toContain('3');
  });

  test('新增對話框有 Tag 編輯器（非 textarea）', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    // 打開新增對話框
    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 應有 tag 容器和輸入框
    await expect(page.locator('#daTagContainer')).toBeVisible();
    await expect(page.locator('#daTagInput')).toBeVisible();
    await expect(page.locator('#daTagSearch')).toBeVisible();
  });

  test('輸入 IP 後按 Enter 產生藍色標籤', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 輸入有效 IP 按 Enter
    await page.locator('#daTagInput').fill('192.168.1.100');
    await page.locator('#daTagInput').press('Enter');
    await page.waitForTimeout(300);

    // 應出現藍色標籤（pill 樣式）
    const blueTag = page.locator('#daTagContainer .ip-tag-valid');
    await expect(blueTag).toBeVisible();
    const tagText = await blueTag.textContent();
    expect(tagText).toContain('192.168.1.100');
  });

  test('無效 IP 產生紅色標籤', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 輸入無效 IP
    await page.locator('#daTagInput').fill('not-an-ip');
    await page.locator('#daTagInput').press('Enter');
    await page.waitForTimeout(300);

    // 應出現紅色邊框標籤
    const redTag = page.locator('#daTagContainer .ip-tag-invalid');
    await expect(redTag).toBeVisible();
  });

  test('重複 IP 不會新增第二個標籤', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 輸入同一 IP 兩次
    await page.locator('#daTagInput').fill('10.0.0.1');
    await page.locator('#daTagInput').press('Enter');
    await page.waitForTimeout(200);

    await page.locator('#daTagInput').fill('10.0.0.1');
    await page.locator('#daTagInput').press('Enter');
    await page.waitForTimeout(200);

    // 應只有一個標籤
    const tags = page.locator('#daTagContainer .ip-tag');
    await expect(tags).toHaveCount(1);
  });

  test('Tag 統計顯示正確的 IP 數量', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 新增 3 個 IP
    for (const ip of ['1.1.1.1', '2.2.2.2', '3.3.3.3']) {
      await page.locator('#daTagInput').fill(ip);
      await page.locator('#daTagInput').press('Enter');
      await page.waitForTimeout(200);
    }

    // 統計應顯示 3
    const statsText = await page.locator('#daTagStats').textContent();
    expect(statsText).toContain('3');
  });

  test('CIDR 格式 IP 被識別為有效', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    await page.locator('#daTagInput').fill('192.168.0.0/16');
    await page.locator('#daTagInput').press('Enter');
    await page.waitForTimeout(300);

    // 應為藍色（有效）
    const blueTag = page.locator('#daTagContainer .ip-tag-valid');
    await expect(blueTag).toBeVisible();
  });

  test('搜尋可過濾標籤', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 新增多個不同 IP
    for (const ip of ['192.168.1.1', '10.0.0.1', '192.168.2.2']) {
      await page.locator('#daTagInput').fill(ip);
      await page.locator('#daTagInput').press('Enter');
      await page.waitForTimeout(200);
    }

    // 搜尋 "10.0"
    await page.locator('#daTagSearch').fill('10.0');
    await page.waitForTimeout(300);

    // 只顯示匹配的標籤（可見的 badge 應只有 1 個）
    const visibleTags = page.locator('#daTagContainer .ip-tag');
    await expect(visibleTags).toHaveCount(1);
  });

  test('儲存後 API 收到正確的換行分隔格式', async ({ page, baseURL }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    await page.evaluate(() => { denyAllowNS.add(); });
    await page.waitForTimeout(500);

    // 填寫名稱
    await page.locator('#daName').fill('ApiTestList');

    // 新增 IP
    for (const ip of ['8.8.8.8', '1.1.1.1']) {
      await page.locator('#daTagInput').fill(ip);
      await page.locator('#daTagInput').press('Enter');
      await page.waitForTimeout(200);
    }

    // 檢查隱藏的 textarea 值（syncToTextarea 結果）
    const textareaVal = await page.evaluate(() => {
      return document.getElementById('daIp').value;
    });
    expect(textareaVal).toContain('8.8.8.8');
    expect(textareaVal).toContain('1.1.1.1');
    expect(textareaVal).toContain('\n');
  });

  test('「生效範圍」欄位在列表中顯示全站生效', async ({ page }) => {
    await login(page);
    await page.goto('/adminPage/protectionCert');
    await page.waitForSelector('.layui-tab');

    // 表頭應有生效範圍欄位(規則一律全站自動生效,取代舊的逐項「被引用」)
    const content = await page.content();
    expect(content).toMatch(/生效範圍|生效范围|Scope/);
    expect(content).toMatch(/全站自動生效|全站自动生效|Site-wide/);
  });

});
