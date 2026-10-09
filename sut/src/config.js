import { readFileSync } from 'node:fs';
import { parseFaults } from './faults.js';

const pkg = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
const env = process.env;

export const config = Object.freeze({
  version: pkg.version,
  port: Number(env.PORT ?? 8080),
  databaseUrl: env.DATABASE_URL ?? 'postgres://sandbox:sandbox@localhost:5432/sandbox_bank',
  jwtSecret: env.JWT_SECRET ?? 'local-sandbox-secret',
  jwtTtlSeconds: Number(env.JWT_TTL_SECONDS ?? 1800),
  seedDemoCustomer: (env.SEED_DEMO_CUSTOMER ?? 'true') === 'true',
  logRequests: (env.LOG_REQUESTS ?? 'true') === 'true',
  faults: parseFaults(env.SANDBOX_FAULTS),
});
