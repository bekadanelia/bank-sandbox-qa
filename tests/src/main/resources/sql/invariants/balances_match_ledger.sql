-- LED-02 Reconciliation: the stored balance of an account equals its credits minus its debits.
-- A lost update under concurrency shows up here.
SELECT a.number,
       a.kind,
       a.currency,
       a.balance AS stored_balance,
       coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END), 0) AS ledger_balance
FROM accounts a
         LEFT JOIN ledger_entries e ON e.account_id = a.id
GROUP BY a.id
HAVING a.balance <> coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
ORDER BY a.number
