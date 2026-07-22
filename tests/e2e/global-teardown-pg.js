const { execSync } = require('child_process');
const { stopApp } = require('./helpers');

const CONTAINER = 'nginxwebui-e2e-pg';

module.exports = async function globalTeardownPg() {
  console.log('Stopping test server...');
  stopApp();
  console.log('Removing PostgreSQL container...');
  try { execSync(`docker rm -f -v ${CONTAINER}`, { stdio: 'ignore' }); } catch (e) { /* ignore */ }
  console.log('PG smoke teardown done.');
};
