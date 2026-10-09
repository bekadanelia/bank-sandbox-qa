-- LED-01 Double entry: within one transfer, debits and credits are equal in every currency.
-- A conversion touches two currencies, and each of them has to balance on its own.
SELECT transfer_id,
       currency,
       coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
       coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
FROM ledger_entries
GROUP BY transfer_id, currency
HAVING coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT'), 0)
    <> coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT'), 0)
ORDER BY transfer_id, currency
