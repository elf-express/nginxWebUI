const { startApp } = require('./helpers');

module.exports = async function globalSetup() {
  console.log('Starting test server...');
  await startApp();
  console.log('Test server ready.');
};
