-- ACC-03 ISO 13616 in SQL: move "GE" and the check digits behind the rest, replace letters with
-- numbers (G=16, E=14, Z=35) and the result must leave a remainder of 1 when divided by 97.
SELECT number
FROM accounts
WHERE number !~ '^GE[0-9]{2}ZZ[0-9]{16}$'
   OR ('3535' || substr(number, 7) || '1614' || substr(number, 3, 2))::numeric % 97 <> 1
ORDER BY number
