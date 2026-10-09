import { pool } from './db.js';
import { findAccountById, openAccount } from './services/accounts.js';
import { registerCustomer } from './services/customers.js';
import { cashAccountId, executeTransfer } from './services/transfers.js';

export const DEMO_CUSTOMER = Object.freeze({
  email: 'demo@sandbox.bank',
  fullName: 'Demo Customer',
  password: 'Sandbox#2026',
});

/** Creates one customer with some history so the web client has something to show on first start. */
export async function seedDemoCustomer() {
  const existing = await pool.query('SELECT 1 FROM customers WHERE lower(email) = lower($1)', [DEMO_CUSTOMER.email]);
  if (existing.rowCount > 0) {
    return;
  }
  const customer = await registerCustomer(DEMO_CUSTOMER);
  const gel = await openAccount(customer.id, 'GEL');
  const usd = await openAccount(customer.id, 'USD');
  const move = (kind, key, source, target, amount, description) =>
    executeTransfer({ kind, customerId: customer.id, idempotencyKey: key, source, target, amount, description });

  await move('DEPOSIT', 'seed-salary', await findAccountById(await cashAccountId('GEL')), gel, 250_000n, 'Salary');
  await move('TRANSFER', 'seed-savings', gel, usd, 50_000n, 'Savings');
  await move('TRANSFER', 'seed-top-up', gel, usd, 12_550n, 'Top up before the trip');
}
