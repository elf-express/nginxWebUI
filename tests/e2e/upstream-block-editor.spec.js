const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test('legacy upstream conversion requires a reviewed snapshot and preserves text on failed save', async ({ page }) => {
  await login(page);
  const seeded = await page.request.post('/adminPage/upstream/addOver', { form: {
    upstreamJson: JSON.stringify({ name: 'e287_legacy_ui', proxyType: 0 }),
    upstreamParamJson: '[]',
    upstreamServerJson: JSON.stringify([{ server: '127.0.0.1', port: 8089, enable: 1, status: 'none' }])
  } });
  expect((await seeded.json()).success).toBeTruthy();
  await page.goto('/adminPage/upstream');
  const row = page.locator('tr', { hasText: 'e287_legacy_ui' });
  const id = await row.locator('input[name="ids"]').inputValue();
  await row.locator('button[onclick^="edit("]').click();
  await expect(page.locator('#name')).toBeVisible();
  await page.locator('#convertBlock').click();
  await expect(page.locator('#blockSnapshot')).toBeVisible();
  expect(await page.locator('#blockBody').inputValue()).toContain('127.0.0.1:8089');
  await page.locator('#blockBody').fill('server 127.0.0.1:8089;\n}');
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#blockSave').click();
  await expect(page.locator('#blockError')).not.toBeEmpty();
  await expect(page.locator('#blockBody')).toHaveValue('server 127.0.0.1:8089;\n}');
  await page.locator('#blockBody').fill('server 127.0.0.1:8089;');
  page.once('dialog', dialog => dialog.accept());
  await Promise.all([page.waitForEvent('load'), page.locator('#blockSave').click()]);
  await page.evaluate(id => edit(id), id);
  await expect(page.locator('#blockBody')).toHaveValue('server 127.0.0.1:8089;');
  await expect(page.locator('#blockMonitorNote')).toBeVisible();
  await page.request.post('/adminPage/upstream/del', { form: { id } });
});

test('three upstream drafts persist independently; full paste confirms title and rejects multiple roots', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await login(page);
  await page.goto('/adminPage/upstream');
  for (let i = 0; i < 3; i++) {
    await page.locator('button[onclick="add()"]').click();
    await expect(page.locator('#blockHeader')).toBeVisible();
    await page.locator('#blockHeader').fill('e287_ui_' + i);
    await page.locator('#blockBody').fill('# independent ' + i + '\nserver 127.0.0.1:' + (9100 + i) + ';');
    await Promise.all([page.waitForEvent('load'), page.locator('#blockSave').click()]);
  }
  for (let i = 0; i < 3; i++) {
    await page.locator('tr', { hasText: 'e287_ui_' + i }).locator('button[onclick^="edit("]').click();
    await expect(page.locator('#blockBody')).toHaveValue('# independent ' + i + '\nserver 127.0.0.1:' + (9100 + i) + ';');
    if (i === 1) {
      await page.locator('#blockBody').fill('server 127.0.0.1:9992;');
      await Promise.all([page.waitForEvent('load'), page.locator('#blockSave').click()]);
    } else await page.locator('#blockClose').click();
  }
  await page.locator('tr', { hasText: 'e287_ui_1' }).locator('button[onclick^="edit("]').click();
  await expect(page.locator('#blockBody')).toHaveValue('server 127.0.0.1:9992;');
  await page.locator('#blockClose').click();
  await page.locator('button[onclick="add()"]').click();
  await expect(page.locator('#blockHeader')).toHaveValue('');
  await expect(page.locator('#blockBody')).toHaveValue('');
  await page.locator('#blockHeader').fill('original');
  await page.locator('#blockBody').fill('upstream pasted {\nserver 127.0.0.1:9010;\n}');
  const [rejectedTitle] = await Promise.all([page.waitForEvent('dialog'), page.locator('#blockParse').click()]);
  await rejectedTitle.dismiss();
  await expect(page.locator('#blockHeader')).toHaveValue('original');
  const [acceptedTitle] = await Promise.all([page.waitForEvent('dialog'), page.locator('#blockParse').click()]);
  await acceptedTitle.accept();
  await expect(page.locator('#blockHeader')).toHaveValue('pasted');
  await expect(page.locator('#blockBody')).toHaveValue('\nserver 127.0.0.1:9010;\n');
  const invalid = 'upstream a {} upstream b {}';
  await page.locator('#blockBody').fill(invalid);
  await page.locator('#blockParse').click();
  await expect(page.locator('#blockError')).not.toBeEmpty();
  await expect(page.locator('#blockBody')).toHaveValue(invalid);
  await page.locator('#blockBody').fill('# <img src=x onerror="window.e287xss=1">\nserver 127.0.0.1:9010;');
  await page.locator('#blockPreview').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#blockOutput')).toContainText('<img src=x');
  expect(await page.evaluate(() => window.e287xss)).toBeUndefined();
  await page.locator('#blockSave').scrollIntoViewIfNeeded();
  const saveBox = await page.locator('#blockSave').boundingBox();
  expect(saveBox.x + saveBox.width).toBeLessThanOrEqual(1280);
  expect(saveBox.y + saveBox.height).toBeLessThanOrEqual(800);
  await page.locator('#blockClose').click();
  for (let i = 0; i < 3; i++) {
    const row = page.locator('tr', { hasText: 'e287_ui_' + i });
    const id = await row.locator('input[name="ids"]').inputValue();
    await page.request.post('/adminPage/upstream/del', { form: { id } });
  }
});
