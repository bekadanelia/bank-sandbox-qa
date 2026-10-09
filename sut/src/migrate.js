import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { pool } from './db.js';

const MIGRATIONS_DIR = fileURLToPath(new URL('../db/migrations', import.meta.url));
const MIGRATION_LOCK = 4_815_162_342;

/** Applies every SQL file in db/migrations that has not been applied yet, in file name order. */
export async function migrate() {
  const client = await pool.connect();
  try {
    await client.query('SELECT pg_advisory_lock($1)', [MIGRATION_LOCK]);
    await client.query(
      `CREATE TABLE IF NOT EXISTS schema_migrations (
         version    text PRIMARY KEY,
         applied_at timestamptz NOT NULL DEFAULT now()
       )`,
    );
    const applied = new Set((await client.query('SELECT version FROM schema_migrations')).rows.map((row) => row.version));
    const files = readdirSync(MIGRATIONS_DIR).filter((file) => file.endsWith('.sql')).sort();
    for (const file of files) {
      if (applied.has(file)) {
        continue;
      }
      await client.query('BEGIN');
      try {
        await client.query(readFileSync(path.join(MIGRATIONS_DIR, file), 'utf8'));
        await client.query('INSERT INTO schema_migrations (version) VALUES ($1)', [file]);
        await client.query('COMMIT');
        console.log(JSON.stringify({ level: 'info', message: `applied migration ${file}` }));
      } catch (error) {
        await client.query('ROLLBACK');
        throw new Error(`Migration ${file} failed: ${error.message}`, { cause: error });
      }
    }
  } finally {
    await client.query('SELECT pg_advisory_unlock($1)', [MIGRATION_LOCK]).catch(() => {});
    client.release();
  }
}
