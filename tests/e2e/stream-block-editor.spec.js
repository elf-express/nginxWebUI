const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test('Stream preview combines persisted sources with unsaved draft without saving it', async ({ page }) => {
  await login(page);
  const created = await page.request.post('/adminPage/stream/saveBlock', { form: { header: 'server', rawContent: 'listen 19401;\nproxy_pass 127.0.0.1:9000;' } });
  const saved = await created.json();
  expect(saved.success).toBeTruthy();
  const id = saved.obj.id;
  try {
    await page.goto('/adminPage/stream');
    await page.locator('button[onclick="add()"]').click();
    await page.locator('#blockHeader').fill('server');
    const draft = '# <img src=x onerror="window.previewXss=1">\nlisten 19402;\nproxy_pass 127.0.0.1:9000;';
    await page.locator('#blockBody').fill(draft);
    await page.locator('#blockPreview').click();
    await expect(page.locator('#blockOutput')).toContainText('listen 19401;');
    await expect(page.locator('#blockOutput')).toContainText('listen 19402;');
    await expect(page.locator('#blockOutput')).toContainText('stream {');
    await expect(page.locator('#blockOutput')).toContainText('<img src=x');
    expect(await page.evaluate(() => window.previewXss)).toBeUndefined();
    await expect(page.locator('#blockBody')).toHaveValue(draft);
    await page.locator('#blockBody').fill('listen 19402;\n}');
    await page.locator('#blockPreview').click();
    await expect(page.locator('#blockError')).not.toBeEmpty();
    await expect(page.locator('#blockBody')).toHaveValue('listen 19402;\n}');
    await page.locator('#blockClose').click();
    await page.evaluate(id => edit(id), id);
    await expect(page.locator('#blockBody')).toHaveValue('listen 19401;\nproxy_pass 127.0.0.1:9000;');
    await page.locator('#blockBody').fill('listen 19403;\nproxy_pass 127.0.0.1:9000;');
    await page.locator('#blockPreview').click();
    await expect(page.locator('#blockOutput')).toContainText('listen 19403;');
    await expect(page.locator('#blockOutput')).not.toContainText('listen 19401;');
    await page.reload();
    await expect(page.locator('tr', { hasText: 'listen 19402;' })).toHaveCount(0);
    await expect(page.locator('tr', { hasText: 'listen 19403;' })).toHaveCount(0);
    await expect(page.locator('tr', { hasText: 'listen 19401;' })).toHaveCount(1);
  } finally {
    await page.request.post('/adminPage/stream/del', { form: { id } });
  }
});

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
