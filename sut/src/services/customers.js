import { hashPassword, verifyPassword } from '../auth.js';
import { pool, withTransaction } from '../db.js';
import { ApiError, conflict } from '../errors.js';
import { audit } from './audit.js';

const UNIQUE_VIOLATION = '23505';
// Checked when the email is unknown, so both failure paths spend the same time hashing.
const DECOY_HASH = await hashPassword('decoy password that is never accepted');

export function toCustomerJson(row) {
  return {
    id: row.id,
    email: row.email,
    fullName: row.full_name,
    createdAt: row.created_at.toISOString(),
  };
}

export async function registerCustomer({ email, fullName, password }) {
  const passwordHash = await hashPassword(password);
  try {
    return await withTransaction(async (db) => {
      const { rows } = await db.query(
        'INSERT INTO customers (email, full_name, password_hash) VALUES ($1, $2, $3) RETURNING *',
        [email, fullName, passwordHash],
      );
      await audit(db, {
        eventType: 'CUSTOMER_REGISTERED',
        entityType: 'CUSTOMER',
        entityId: rows[0].id,
        actorId: rows[0].id,
      });
      return rows[0];
    });
  } catch (error) {
    if (error.code === UNIQUE_VIOLATION) {
      throw conflict('EMAIL_ALREADY_REGISTERED', 'A customer with this email already exists');
    }
    throw error;
  }
}

/** Returns the customer id, or fails with the same error for an unknown email and a wrong password. */
export async function verifyCredentials(email, password) {
  const { rows } = await pool.query(
    `SELECT id, password_hash FROM customers WHERE lower(email) = lower($1) AND kind = 'RETAIL'`,
    [email],
  );
  const passwordMatches = await verifyPassword(password, rows[0]?.password_hash ?? DECOY_HASH);
  if (rows.length === 0 || !passwordMatches) {
    throw new ApiError(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect');
  }
  return rows[0].id;
}

export async function findCustomer(customerId) {
  const { rows } = await pool.query(`SELECT * FROM customers WHERE id = $1 AND kind = 'RETAIL'`, [customerId]);
  return rows[0];
}
