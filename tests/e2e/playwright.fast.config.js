// 快速 / CI 回歸用設定：headless + 關閉 slowMo（繼承 base config 的其餘設定）。
// 背景：package.json 的 test:fast 原本用無效旗標 --headed=false（Playwright 不認，
//       導致套件根本跑不起來）。改用此 config 即可 headless 跑全套。
const base = require('./playwright.config.js');

module.exports = {
  ...base,
  // 09-realip 檢查的是部署容器，另由 test:realip（playwright.realip.config.js）跑
  testIgnore: ['09-realip.spec.js'],
  use: {
    ...base.use,
    headless: true,
    launchOptions: { ...(base.use && base.use.launchOptions ? base.use.launchOptions : {}), slowMo: 0 },
  },
};
