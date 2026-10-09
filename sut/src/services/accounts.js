import { config } from '../config.js';
import { pool, withTransaction } from '../db.js';
import { notFound, ruleViolation } from '../errors.js';
import { isUuid } from '../validate.js';
import { audit } from './audit.js';

export const MAX_ACCOUNTS_PER_CUSTOMER = 5;

const faults = config.faults;
const accountNotFound = () => notFound('ACCOUNT_NOT_FOUND', 'Account not found');

export function toAccountJson(row) {
  return {
    id: row.id,
    number: row.number,
    currency: row.currency,
    balance: row.balance,
    status: row.status,
    createdAt: row.created_at.toISOString(),
  };
}

export async function openAccount(customerId, currency) {
  return withTransaction(async (db) => {
    // Locking the customer row makes the "count, then insert" check safe under concurrency.
    await db.query('SELECT id FROM customers WHERE id = $1 FOR NO KEY UPDATE', [customerId]);
    const { rows: counted } = await db.query('SELECT count(*)::int AS total FROM accounts WHERE customer_id = $1', [
      customerId,
    ]);
    if (counted[0].total >= MAX_ACCOUNTS_PER_CUSTOMER) {
      throw ruleViolation('ACCOUNT_LIMIT_REACHED');
    }
    const { rows } = await db.query('INSERT INTO accounts (customer_id, currency) VALUES ($1, $2) RETURNING *', [
      customerId,
      currency,
    ]);
    await audit(db, {
      eventType: 'ACCOUNT_OPENED',
      entityType: 'ACCOUNT',
      entityId: rows[0].id,
      actorId: customerId,
      payload: { currency },
    });
    return rows[0];
  });
}

export async function listAccounts(customerId) {
  const { rows } = await pool.query(
    `SELECT * FROM accounts WHERE customer_id = $1 AND kind = 'CUSTOMER' ORDER BY created_at, number`,
    [customerId],
  );
  return rows;
}

/**
 * Loads an account the caller owns. Unknown ids, malformed ids and other customers' accounts
 * all produce the same 404 so the response never reveals whether an account exists.
 */
export async function getOwnedAccount(customerId, accountId, { forRead = false } = {}) {
  if (!isUuid(accountId)) {
    throw accountNotFound();
  }
  const skipOwnerCheck = forRead && faults.has('IDOR_ACCOUNT_READ');
  const { rows } = skipOwnerCheck
    ? await pool.query(`SELECT * FROM accounts WHERE id = $1 AND kind = 'CUSTOMER'`, [accountId])
    : await pool.query(`SELECT * FROM accounts WHERE id = $1 AND kind = 'CUSTOMER' AND customer_id = $2`, [
      accountId,
      customerId,
    ]);
  if (rows.length === 0) {
    throw accountNotFound();
  }
  return rows[0];
}

export async function findAccountByNumber(number) {
  const { rows } = await pool.query(`SELECT * FROM accounts WHERE number = $1 AND kind = 'CUSTOMER'`, [number]);
  return rows[0];
}

export async function findAccountById(accountId) {
  const { rows } = await pool.query('SELECT * FROM accounts WHERE id = $1', [accountId]);
  return rows[0];
}

export async function setAccountStatus(customerId, accountId, status) {
  const account = await getOwnedAccount(customerId, accountId);
  if (account.status === status) {
    return account; // freezing a frozen account is not an error and leaves no second audit event
  }
  return withTransaction(async (db) => {
    const { rows } = await db.query('UPDATE accounts SET status = $2 WHERE id = $1 RETURNING *', [accountId, status]);
    await audit(db, {
      eventType: status === 'FROZEN' ? 'ACCOUNT_FROZEN' : 'ACCOUNT_UNFROZEN',
      entityType: 'ACCOUNT',
      entityId: accountId,
      actorId: customerId,
    });
    return rows[0];
  });
}

/** One page of ledger entries for an account, newest first. */
export async function listTransactions(accountId, page, size) {
  const order = faults.has('STATEMENT_ORDER') ? 'ASC' : 'DESC';
  const { rows: counted } = await pool.query('SELECT count(*)::int AS total FROM ledger_entries WHERE account_id = $1', [
    accountId,
  ]);
  const { rows } = await pool.query(
    `SELECT entry.id, entry.transfer_id, entry.direction, entry.amount, entry.currency, entry.balance_after,
            entry.created_at, t.kind, t.description
     FROM ledger_entries entry
            JOIN transfers t ON t.id = entry.transfer_id
     WHERE entry.account_id = $1
     ORDER BY entry.id ${order}
     LIMIT $2 OFFSET $3`,
    [accountId, size, page * size],
  );
  const totalItems = counted[0].total;
  return {
    items: rows.map((row) => ({
      id: row.id,
      transferId: row.transfer_id,
      type: row.kind,
      direction: row.direction,
      amount: row.amount,
      currency: row.currency,
      balanceAfter: row.balance_after,
      description: row.description,
      createdAt: row.created_at.toISOString(),
    })),
    page,
    size,
    totalItems,
    totalPages: Math.ceil(totalItems / size),
  };
}
