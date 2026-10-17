const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test('three Stream server blocks retain distinct bodies and reject outer stream without losing input', async ({ page }) => {
  await login(page);
  await page.goto('/adminPage/stream');
  const ids = [];
  for (let i = 0; i < 3; i++) {
    await page.locator('button[onclick="add()"]').click();
    await expect(page.locator('#blockHeader')).toBeVisible();
    await page.locator('#blockHeader').fill('server');
    await page.locator('#blockBody').fill('# e287_stream_' + i + '\nlisten ' + (9200 + i) + ';\nproxy_pass 127.0.0.1:9000;');
    await Promise.all([page.waitForEvent('load'), page.locator('#blockSave').click()]);
    const row = page.locator('tr', { hasText: '# e287_stream_' + i });
    ids.push(await row.locator('input[name="ids"]').inputValue());
  }
  for (let i = 0; i < 3; i++) {
    await page.evaluate(id => edit(id), ids[i]);
    await expect(page.locator('#blockHeader')).toHaveValue('server');
    await expect(page.locator('#blockBody')).toHaveValue('# e287_stream_' + i + '\nlisten ' + (9200 + i) + ';\nproxy_pass 127.0.0.1:9000;');
    await page.locator('#blockClose').click();
  }
  await page.locator('button[onclick="add()"]').click();
  await page.locator('#blockBody').fill('stream { server { listen 9300; } }');
  await page.locator('#blockParse').click();
  await expect(page.locator('#blockError')).not.toBeEmpty();
  await expect(page.locator('#blockBody')).toHaveValue('stream { server { listen 9300; } }');
  for (const id of ids) await page.request.post('/adminPage/stream/del', { form: { id } });
});
