const { stopApp } = require('./helpers');

module.exports = async function globalTeardown() {
  console.log('Stopping test server...');
  stopApp();
  console.log('Test server stopped.');
};
