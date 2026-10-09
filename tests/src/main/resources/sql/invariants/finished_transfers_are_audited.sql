-- AUD-01 Every completed or rejected transfer left exactly one audit event of the matching type.
SELECT t.id AS transfer_id, t.status, count(a.id) AS audit_events
FROM transfers t
         LEFT JOIN audit_events a
                   ON a.entity_type = 'TRANSFER'
                       AND a.entity_id = t.id
                       AND a.event_type = 'TRANSFER_' || t.status
WHERE t.status <> 'PENDING'
GROUP BY t.id
HAVING count(a.id) <> 1
ORDER BY t.created_at
