-- LED-07 A transfer is decided in the transaction that creates it, so a committed row is never pending.
SELECT id AS transfer_id, kind, created_at, now() - created_at AS stuck_for
FROM transfers
WHERE status = 'PENDING'
ORDER BY created_at
