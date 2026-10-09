-- LED-06 A completed transfer has two ledger entries, or four when it converts currency.
-- A rejected transfer has none.
SELECT t.id AS transfer_id, t.status, t.source_currency, t.target_currency, count(e.id) AS entries
FROM transfers t
         LEFT JOIN ledger_entries e ON e.transfer_id = t.id
GROUP BY t.id
HAVING count(e.id) <> CASE
                          WHEN t.status = 'COMPLETED' AND t.source_currency = t.target_currency THEN 2
                          WHEN t.status = 'COMPLETED' THEN 4
                          ELSE 0
    END
ORDER BY t.created_at
