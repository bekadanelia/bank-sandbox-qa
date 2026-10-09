-- FX-02 Recomputes every conversion. round() on NUMERIC rounds half away from zero,
-- which for positive amounts is the half up rule of the bank.
SELECT id AS transfer_id,
       source_amount,
       source_currency,
       fx_rate,
       target_amount,
       round(source_amount * fx_rate, 2) AS expected_target_amount
FROM transfers
WHERE status = 'COMPLETED'
  AND source_currency <> target_currency
  AND target_amount IS DISTINCT FROM round(source_amount * fx_rate, 2)
ORDER BY created_at
