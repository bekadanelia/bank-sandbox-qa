-- LED-03 Each ledger entry starts from the balance the previous entry of that account left behind.
WITH ordered AS (
    SELECT id,
           account_id,
           direction,
           amount,
           balance_after,
           lag(balance_after, 1, 0::numeric) OVER (PARTITION BY account_id ORDER BY id) AS balance_before
    FROM ledger_entries
)
SELECT o.id AS entry_id, a.number, o.direction, o.amount, o.balance_before, o.balance_after
FROM ordered o
         JOIN accounts a ON a.id = o.account_id
WHERE o.balance_after <> o.balance_before + CASE o.direction WHEN 'CREDIT' THEN o.amount ELSE -o.amount END
ORDER BY o.id
