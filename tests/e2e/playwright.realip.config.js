// 09-realip 專用設定：只跑對 nginxwebui 容器的檢查（docker exec），不起測試用的 app server。
// 容器由呼叫端先啟動（CI 的 image job 用剛建好的映像跑 `docker run --name nginxwebui`）。
const base = require('./playwright.config.js');

module.exports = {
  ...base,
  testMatch: ['09-realip.spec.js'],
  globalSetup: undefined,
  globalTeardown: undefined,
  use: {
    ...base.use,
    headless: true,
    screenshot: 'off',
    trace: 'off',
    video: 'off',
    launchOptions: { ...(base.use && base.use.launchOptions ? base.use.launchOptions : {}), slowMo: 0 },
  },
};
