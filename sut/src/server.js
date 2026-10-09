import { setTimeout as sleep } from 'node:timers/promises';
import { createApp } from './app.js';
import { config } from './config.js';
import { pool } from './db.js';
import { migrate } from './migrate.js';
import { seedDemoCustomer } from './seed.js';

const STARTUP_ATTEMPTS = 30;

const log = (level, message, extra = {}) => console.log(JSON.stringify({ level, message, ...extra }));

/** The database container may still be starting, so the first connection is retried. */
async function prepareDatabase() {
  for (let attempt = 1; ; attempt += 1) {
    try {
      await migrate();
      return;
    } catch (error) {
      const waitingForDatabase = ['ECONNREFUSED', 'ENOTFOUND', '57P03'].includes(error.code);
      if (!waitingForDatabase || attempt === STARTUP_ATTEMPTS) {
        throw error;
      }
      log('warn', `database is not ready yet (attempt ${attempt} of ${STARTUP_ATTEMPTS})`);
      await sleep(1000);
    }
  }
}

await prepareDatabase();
if (config.seedDemoCustomer) {
  await seedDemoCustomer();
}

const server = createApp().listen(config.port, () => {
  log('info', `Sandbox Bank ${config.version} is listening on port ${config.port}`, { faults: config.faults.active });
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    log('info', `${signal} received, shutting down`);
    server.close(() => pool.end().finally(() => process.exit(0)));
  });
}
