import { Router } from 'express';
import { notFound, ruleViolation } from '../errors.js';
import { hasAccountNumberLayout, hasValidCheckDigits } from '../iban.js';
import { findAccountByNumber, getOwnedAccount } from '../services/accounts.js';
import { executeTransfer, findTransferForCustomer, toTransferJson } from '../services/transfers.js';
import { check, isUuid } from '../validate.js';

const IDEMPOTENCY_KEY_PATTERN = /^[A-Za-z0-9_-]{8,64}$/;

export const transfersRouter = Router();

/** Reads the Idempotency-Key header and reports problems through the request validator. */
export function idempotencyKey(req, v) {
  const key = req.get('Idempotency-Key');
  if (key === undefined) {
    return v.fail('Idempotency-Key', 'header is required');
  }
  return IDEMPOTENCY_KEY_PATTERN.test(key)
    ? key
    : v.fail('Idempotency-Key', 'must be 8 to 64 letters, digits, hyphens or underscores');
}

/**
 * A replay answers exactly like the first attempt did, plus a header that marks it as a replay.
 * Rejected transfers are reported as 422 with the rule that was broken.
 */
export function sendTransfer(res, { transfer, replayed }) {
  if (replayed) {
    res.set('Idempotency-Replayed', 'true');
  }
  if (transfer.status === 'REJECTED') {
    throw ruleViolation(transfer.rejection_reason, { transferId: transfer.id });
  }
  res.status(201).json(toTransferJson(transfer));
}

transfersRouter.post('/transfers', async (req, res) => {
  const v = check(req.body);
  const key = idempotencyKey(req, v);
  const sourceAccountId = v.uuid('sourceAccountId');
  const targetAccountNumber = v.text('targetAccountNumber', { max: 34 });
  if (targetAccountNumber !== undefined) {
    if (!hasAccountNumberLayout(targetAccountNumber)) {
      v.fail('targetAccountNumber', 'must look like GE00ZZ0000000000000000');
    } else if (!hasValidCheckDigits(targetAccountNumber)) {
      v.fail('targetAccountNumber', 'has invalid check digits');
    }
  }
  const amount = v.amount('amount');
  const description = v.optionalText('description', { max: 140 });
  v.done();

  const source = await getOwnedAccount(req.customerId, sourceAccountId);
  const target = await findAccountByNumber(targetAccountNumber);
  if (!target) {
    throw ruleViolation('TARGET_ACCOUNT_NOT_FOUND');
  }
  const outcome = await executeTransfer({
    kind: 'TRANSFER',
    customerId: req.customerId,
    idempotencyKey: key,
    source,
    target,
    amount,
    description,
  });
  sendTransfer(res, outcome);
});

transfersRouter.get('/transfers/:transferId', async (req, res) => {
  const transfer = isUuid(req.params.transferId)
    ? await findTransferForCustomer(req.customerId, req.params.transferId)
    : undefined;
  if (!transfer) {
    throw notFound('TRANSFER_NOT_FOUND', 'Transfer not found');
  }
  res.json(toTransferJson(transfer));
});
