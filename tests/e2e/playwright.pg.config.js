// PostgreSQL smoke 設定:headless 跑「登入 + server 儲存」兩支 spec,
// 資料庫換成 docker 起的 postgres:18-alpine(global-setup-pg.js)。
// 目的:補上「E2E 只跑 SQLite,PG 行為差異測不到」的結構性缺口。
const base = require('./playwright.config.js');

module.exports = {
  ...base,
  testMatch: ['01-login.spec.js', '33-server-save.spec.js'],
  globalSetup: './global-setup-pg.js',
  globalTeardown: './global-teardown-pg.js',
  use: {
    ...base.use,
    headless: true,
    launchOptions: { ...(base.use && base.use.launchOptions ? base.use.launchOptions : {}), slowMo: 0 },
  },
};
