-- LED-04 The bank is a closed system. Deposits come out of the bank's own cash account and
-- conversions pass through its FX position accounts, so every currency sums to zero.
SELECT currency, sum(balance) AS drift
FROM accounts
GROUP BY currency
HAVING sum(balance) <> 0
ORDER BY currency
