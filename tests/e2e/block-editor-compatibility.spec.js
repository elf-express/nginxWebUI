const { test, expect } = require('@playwright/test');
const { login, csrfPost } = require('./helpers');

test('block editor records persist across navigation and survive the legacy Stream guide', async ({ page }) => {
  await login(page);
  const marker = 'e287_compat_' + Date.now();
  const body = '# ' + marker + '\nlisten 19411;\nproxy_pass 127.0.0.1:9000;';
  const created = await csrfPost(page, '/adminPage/stream/saveBlock', {
    form: { header: 'server', rawContent: body },
  });
  const saved = await created.json();
  expect(saved.success).toBeTruthy();
  const id = saved.obj.id;

  try {
    await page.goto('/adminPage/stream');
    await expect(page.locator('tr', { hasText: marker })).toHaveCount(1);
    await page.reload();
    await expect(page.locator('tr', { hasText: marker })).toHaveCount(1);

    const guide = await csrfPost(page, '/adminPage/stream/addGiudeOver', { form: { logStatus: 'true' } });
    expect((await guide.json()).success).toBeTruthy();
    await page.goto('/adminPage/stream');
    await expect(page.locator('tr', { hasText: marker })).toHaveCount(1);
    await page.evaluate(id => edit(id), id);
    await expect(page.locator('#blockBody')).toHaveValue(body);
  } finally {
    await csrfPost(page, '/adminPage/stream/del', { form: { id } });
  }
});
