import { Router } from 'express';
import { validationError } from '../errors.js';
import { CURRENCIES } from '../money.js';
import {
  findAccountById,
  getOwnedAccount,
  listAccounts,
  listTransactions,
  openAccount,
  setAccountStatus,
  toAccountJson,
} from '../services/accounts.js';
import { cashAccountId, executeTransfer } from '../services/transfers.js';
import { check } from '../validate.js';
import { idempotencyKey, sendTransfer } from './transfers.js';

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;
const MAX_PAGE = 100_000;

export const accountsRouter = Router();

accountsRouter.post('/accounts', async (req, res) => {
  const v = check(req.body);
  const currency = v.oneOf('currency', CURRENCIES);
  v.done();
  res.status(201).json(toAccountJson(await openAccount(req.customerId, currency)));
});

accountsRouter.get('/accounts', async (req, res) => {
  res.json({ items: (await listAccounts(req.customerId)).map(toAccountJson) });
});

accountsRouter.get('/accounts/:accountId', async (req, res) => {
  res.json(toAccountJson(await getOwnedAccount(req.customerId, req.params.accountId, { forRead: true })));
});

accountsRouter.post('/accounts/:accountId/freeze', async (req, res) => {
  res.json(toAccountJson(await setAccountStatus(req.customerId, req.params.accountId, 'FROZEN')));
});

accountsRouter.post('/accounts/:accountId/unfreeze', async (req, res) => {
  res.json(toAccountJson(await setAccountStatus(req.customerId, req.params.accountId, 'ACTIVE')));
});

accountsRouter.get('/accounts/:accountId/transactions', async (req, res) => {
  const problems = [];
  const page = wholeNumber(req.query.page, 0, { field: 'page', min: 0, max: MAX_PAGE }, problems);
  const size = wholeNumber(req.query.size, DEFAULT_PAGE_SIZE, { field: 'size', min: 1, max: MAX_PAGE_SIZE }, problems);
  if (problems.length > 0) {
    throw validationError(problems);
  }
  const account = await getOwnedAccount(req.customerId, req.params.accountId, { forRead: true });
  res.json(await listTransactions(account.id, page, size));
});

accountsRouter.post('/accounts/:accountId/deposits', async (req, res) => {
  const v = check(req.body);
  const key = idempotencyKey(req, v);
  const amount = v.amount('amount');
  const description = v.optionalText('description', { max: 140 });
  v.done();
  const account = await getOwnedAccount(req.customerId, req.params.accountId);
  const cash = await findAccountById(await cashAccountId(account.currency));
  const outcome = await executeTransfer({
    kind: 'DEPOSIT',
    customerId: req.customerId,
    idempotencyKey: key,
    source: cash,
    target: account,
    amount,
    description,
  });
  sendTransfer(res, outcome);
});

function wholeNumber(raw, fallback, { field, min, max }, problems) {
  if (raw === undefined) {
    return fallback;
  }
  const value = typeof raw === 'string' && /^\d{1,9}$/.test(raw) ? Number(raw) : NaN;
  if (Number.isNaN(value) || value < min || value > max) {
    problems.push({ field, issue: `must be a whole number from ${min} to ${max}` });
    return fallback;
  }
  return value;
}
