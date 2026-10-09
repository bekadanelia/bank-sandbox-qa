-- LED-08 The source account is debited and the target account credited with exactly the amounts
-- on the transfer, and every entry is in the currency of the account it is posted to.
SELECT t.id AS transfer_id, 'source debit or target credit is missing or differs' AS problem
FROM transfers t
WHERE t.status = 'COMPLETED'
  AND (NOT EXISTS (SELECT 1
                   FROM ledger_entries e
                   WHERE e.transfer_id = t.id
                     AND e.account_id = t.source_account_id
                     AND e.direction = 'DEBIT'
                     AND e.amount = t.source_amount
                     AND e.currency = t.source_currency)
    OR NOT EXISTS (SELECT 1
                   FROM ledger_entries e
                   WHERE e.transfer_id = t.id
                     AND e.account_id = t.target_account_id
                     AND e.direction = 'CREDIT'
                     AND e.amount = t.target_amount
                     AND e.currency = t.target_currency))
UNION ALL
SELECT e.transfer_id, 'entry currency differs from account currency'
FROM ledger_entries e
         JOIN accounts a ON a.id = e.account_id
WHERE e.currency <> a.currency
