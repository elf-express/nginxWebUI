const { defineConfig } = require('@playwright/test');
const path = require('path');

module.exports = defineConfig({
  testDir: '.',
  testMatch: '*.spec.js',
  timeout: 60000,
  retries: 0,
  workers: 1,
  globalSetup: './global-setup.js',
  globalTeardown: './global-teardown.js',
  outputDir: path.resolve(__dirname, '../playwright-report/test-results'),
  reporter: [
    ['list'],
    ['html', { outputFolder: path.resolve(__dirname, '../playwright-report/html'), open: 'never' }],
  ],
  use: {
    baseURL: 'http://localhost:18080',
    headless: false,
    screenshot: 'on',
    trace: 'on',
    video: 'on',
    launchOptions: {
      slowMo: 1200,  // 每個操作間隔 1200ms，方便觀看
    },
  },
});
