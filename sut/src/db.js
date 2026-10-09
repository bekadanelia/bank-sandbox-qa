import pg from 'pg';
import { config } from './config.js';

// NUMERIC columns stay strings (pg's default), so amounts never pass through a float.
// BIGINT identity values are far below 2^53 here and are safe to expose as numbers.
pg.types.setTypeParser(pg.types.builtins.INT8, Number);

export const pool = new pg.Pool({ connectionString: config.databaseUrl, max: 20 });

/** Runs `work` inside one database transaction and rolls back if it throws. */
export async function withTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}
