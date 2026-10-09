-- AUD-02 The audit table has no foreign keys by design, so orphans have to be looked for.
SELECT a.id AS audit_event_id, a.event_type, a.entity_type, a.entity_id
FROM audit_events a
WHERE CASE a.entity_type
          WHEN 'TRANSFER' THEN NOT EXISTS (SELECT 1 FROM transfers t WHERE t.id = a.entity_id)
          WHEN 'ACCOUNT' THEN NOT EXISTS (SELECT 1 FROM accounts x WHERE x.id = a.entity_id)
          WHEN 'CUSTOMER' THEN NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = a.entity_id)
          ELSE true
    END
ORDER BY a.id
