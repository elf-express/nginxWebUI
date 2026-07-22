// PG smoke 專用 global setup:起 postgres:18-alpine 容器(port 15432) → 等 ready → 起 app 連 PG。
// 用固定容器名 + 先 rm -f,避免上次殘留造成 port 衝突。
const { execSync } = require('child_process');
const { startApp } = require('./helpers');

const CONTAINER = 'nginxwebui-e2e-pg';

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

module.exports = async function globalSetupPg() {
  process.env.E2E_DB = 'postgresql';

  console.log('Starting PostgreSQL container...');
  try { execSync(`docker rm -f -v ${CONTAINER}`, { stdio: 'ignore' }); } catch (e) { /* not exists */ }
  execSync(
    `docker run -d --name ${CONTAINER} -p 15432:5432 ` +
    '-e POSTGRES_DB=nginxwebui -e POSTGRES_USER=nginxwebui -e POSTGRES_PASSWORD=nginxwebui123 ' +
    'postgres:18-alpine',
    { stdio: 'inherit' },
  );

  const start = Date.now();
  for (;;) {
    try {
      execSync(`docker exec ${CONTAINER} pg_isready -U nginxwebui`, { stdio: 'ignore' });
      break;
    } catch (e) {
      if (Date.now() - start > 60000) {
        throw new Error('PostgreSQL container not ready within 60s');
      }
      await sleep(1000);
    }
  }

  console.log('Starting test server (PostgreSQL)...');
  await startApp();
  console.log('Test server ready (PostgreSQL).');
};
