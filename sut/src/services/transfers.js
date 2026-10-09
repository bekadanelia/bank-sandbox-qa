import { createHash, randomUUID } from 'node:crypto';
import { setTimeout as sleep } from 'node:timers/promises';
import { config } from '../config.js';
import { pool, withTransaction } from '../db.js';
import { conflict } from '../errors.js';
import { convert, formatAmount } from '../money.js';
import { audit } from './audit.js';

/** Largest amount a single customer transfer may move, in minor units of the source currency. */
export const TRANSFER_LIMIT = 1_000_000n;
/** Largest single deposit, in minor units. */
export const DEPOSIT_LIMIT = 10_000_000n;

const faults = config.faults;
let internalAccounts;

/** Ids of the bank's own accounts, keyed as CASH:GEL or FX_POSITION:USD. Loaded once. */
async function internalAccountId(kind, currency) {
  if (!internalAccounts) {
    const { rows } = await pool.query(`SELECT id, kind, currency FROM accounts WHERE kind <> 'CUSTOMER'`);
    internalAccounts = new Map(rows.map((row) => [`${row.kind}:${row.currency}`, row.id]));
  }
  return internalAccounts.get(`${kind}:${currency}`);
}

export const cashAccountId = (currency) => internalAccountId('CASH', currency);

function numericToMinor(text) {
  const negative = text.startsWith('-');
  const [whole, fraction = ''] = (negative ? text.slice(1) : text).split('.');
  const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  return negative ? -minor : minor;
}

const fingerprint = (parts) => createHash('sha256').update(parts.join('|')).digest('hex');

/**
 * Moves money between two accounts in one database transaction.
 *
 * Returns { transfer, replayed }. A transfer that breaks a business rule is still stored,
 * with status REJECTED and the rule code in rejection_reason, and never touches the ledger.
 */
export async function executeTransfer({ kind, customerId, idempotencyKey, source, target, amount, description }) {
  const requestHash = fingerprint([kind, source.id, target.id, amount, description ?? '']);
  const crossCurrency = source.currency !== target.currency;

  return withTransaction(async (db) => {
    // 1. Claim the idempotency key. A concurrent request with the same key waits here until
    //    the first one commits, then sees the conflict and replays the stored outcome.
    const storedKey = faults.has('IDEMPOTENCY_IGNORED') ? `${idempotencyKey}#${randomUUID()}` : idempotencyKey;
    const claim = await db.query(
      `INSERT INTO transfers (kind, customer_id, idempotency_key, request_hash, source_account_id, target_account_id,
                              source_amount, source_currency, target_currency, description)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
       ON CONFLICT (customer_id, idempotency_key) DO NOTHING
       RETURNING id`,
      [kind, customerId, storedKey, requestHash, source.id, target.id, formatAmount(amount), source.currency,
        target.currency, description],
    );
    if (claim.rowCount === 0) {
      const existing = await findByKey(db, customerId, idempotencyKey);
      if (existing.request_hash !== requestHash) {
        throw conflict('IDEMPOTENCY_KEY_REUSED', 'This Idempotency-Key was already used with a different request');
      }
      return { transfer: existing, replayed: true };
    }
    const transferId = claim.rows[0].id;

    // 2. Lock every account the transfer touches, always in id order so two transfers
    //    in opposite directions cannot deadlock.
    //    The lock is FOR NO KEY UPDATE on purpose. The insert above already holds foreign key
    //    locks on the same account rows, and those conflict with a plain FOR UPDATE: two deposits
    //    out of the same cash account would then wait for each other forever.
    const fxSource = crossCurrency ? await internalAccountId('FX_POSITION', source.currency) : null;
    const fxTarget = crossCurrency ? await internalAccountId('FX_POSITION', target.currency) : null;
    const accountIds = [source.id, target.id, fxSource, fxTarget].filter(Boolean);
    const lock = faults.has('NO_ROW_LOCK')
      ? ''
      : faults.has('DEADLOCK_PRONE_LOCK') ? 'FOR UPDATE' : 'FOR NO KEY UPDATE';
    const { rows: locked } = await db.query(
      `SELECT id, kind, status, balance FROM accounts WHERE id = ANY ($1::uuid[]) ORDER BY id ${lock}`,
      [accountIds],
    );
    if (faults.has('NO_ROW_LOCK')) {
      await sleep(150); // widen the race window so the seeded defect shows up reliably
    }
    const accounts = new Map(locked.map((row) => [row.id, { ...row, balance: numericToMinor(row.balance) }]));

    // 3. Business rules, checked in a fixed order so the reported reason is deterministic.
    const reject = async (reason) => {
      await db.query(`UPDATE transfers SET status = 'REJECTED', rejection_reason = $2 WHERE id = $1`, [transferId, reason]);
      await audit(db, {
        eventType: 'TRANSFER_REJECTED',
        entityType: 'TRANSFER',
        entityId: transferId,
        actorId: customerId,
        payload: { reason, amount: formatAmount(amount), currency: source.currency },
      });
      return { transfer: await findById(db, transferId), replayed: false };
    };

    if (source.id === target.id) {
      return reject('SAME_ACCOUNT');
    }
    if (accounts.get(source.id).status !== 'ACTIVE' || accounts.get(target.id).status !== 'ACTIVE') {
      return reject('ACCOUNT_NOT_ACTIVE');
    }
    const limit = kind === 'DEPOSIT' ? DEPOSIT_LIMIT : TRANSFER_LIMIT;
    const overLimit = faults.has('LIMIT_OFF_BY_ONE') ? amount >= limit : amount > limit;
    if (overLimit) {
      return reject('LIMIT_EXCEEDED');
    }
    let rate = null;
    let targetAmount = amount;
    if (crossCurrency) {
      const found = await db.query('SELECT rate FROM fx_rates WHERE base_currency = $1 AND quote_currency = $2', [
        source.currency,
        target.currency,
      ]);
      rate = found.rows[0].rate;
      targetAmount = convert(amount, rate, { truncate: faults.has('FX_TRUNCATE') });
      if (targetAmount === 0n) {
        return reject('AMOUNT_TOO_SMALL');
      }
    }
    if (accounts.get(source.id).kind === 'CUSTOMER' && accounts.get(source.id).balance < amount) {
      return reject('INSUFFICIENT_FUNDS');
    }

    // 4. Post the ledger entries. Every currency must balance: debits equal credits.
    const post = async (accountId, direction, value, currency) => {
      const account = accounts.get(accountId);
      account.balance += direction === 'CREDIT' ? value : -value;
      await db.query('UPDATE accounts SET balance = $2 WHERE id = $1', [accountId, formatAmount(account.balance)]);
      await db.query(
        `INSERT INTO ledger_entries (transfer_id, account_id, direction, amount, currency, balance_after)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [transferId, accountId, direction, formatAmount(value), currency, formatAmount(account.balance)],
      );
    };

    await post(source.id, 'DEBIT', amount, source.currency);
    if (crossCurrency && !faults.has('FX_POSITION_SKIPPED')) {
      await post(fxSource, 'CREDIT', amount, source.currency);
      await post(fxTarget, 'DEBIT', targetAmount, target.currency);
    }
    await post(target.id, 'CREDIT', targetAmount, target.currency);

    // 5. Mark the transfer as final and leave an audit trail.
    await db.query(
      `UPDATE transfers
       SET status = 'COMPLETED', target_amount = $2, fx_rate = $3, completed_at = now()
       WHERE id = $1`,
      [transferId, formatAmount(targetAmount), rate],
    );
    await audit(db, {
      eventType: 'TRANSFER_COMPLETED',
      entityType: 'TRANSFER',
      entityId: transferId,
      actorId: customerId,
      payload: {
        kind,
        sourceAmount: formatAmount(amount),
        sourceCurrency: source.currency,
        targetAmount: formatAmount(targetAmount),
        targetCurrency: target.currency,
      },
    });
    return { transfer: await findById(db, transferId), replayed: false };
  });
}

const TRANSFER_SELECT = `
  SELECT t.*, target.number AS target_number
  FROM transfers t
         JOIN accounts target ON target.id = t.target_account_id`;

async function findById(db, id) {
  const { rows } = await db.query(`${TRANSFER_SELECT} WHERE t.id = $1`, [id]);
  return rows[0];
}

async function findByKey(db, customerId, idempotencyKey) {
  const { rows } = await db.query(`${TRANSFER_SELECT} WHERE t.customer_id = $1 AND t.idempotency_key = $2`, [
    customerId,
    idempotencyKey,
  ]);
  return rows[0];
}

export async function findTransferForCustomer(customerId, transferId) {
  const { rows } = await pool.query(`${TRANSFER_SELECT} WHERE t.id = $1 AND t.customer_id = $2`, [transferId, customerId]);
  return rows[0];
}

export function toTransferJson(row) {
  return {
    id: row.id,
    type: row.kind,
    status: row.status,
    // The bank's internal cash account is not exposed for deposits.
    sourceAccountId: row.kind === 'DEPOSIT' ? null : row.source_account_id,
    targetAccountNumber: row.target_number,
    sourceAmount: row.source_amount,
    sourceCurrency: row.source_currency,
    targetAmount: row.target_amount,
    targetCurrency: row.target_currency,
    fxRate: row.fx_rate,
    rejectionReason: row.rejection_reason,
    description: row.description,
    createdAt: row.created_at.toISOString(),
    completedAt: row.completed_at ? row.completed_at.toISOString() : null,
  };
}
