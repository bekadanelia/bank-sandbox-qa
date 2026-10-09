-- LED-05 A customer account is never overdrawn, now or at any point of its history.
SELECT a.number, e.id AS entry_id, e.balance_after AS balance
FROM ledger_entries e
         JOIN accounts a ON a.id = e.account_id
WHERE a.kind = 'CUSTOMER'
  AND e.balance_after < 0
UNION ALL
SELECT number, NULL, balance
FROM accounts
WHERE kind = 'CUSTOMER'
  AND balance < 0
