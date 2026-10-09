-- Reference data: the bank's own customer record, its internal accounts and FX rates.

INSERT INTO customers (id, email, full_name, password_hash, kind)
VALUES ('00000000-0000-0000-0000-000000000001', 'treasury@sandbox.bank', 'Sandbox Bank Treasury', 'no-login', 'SYSTEM');

-- CASH is the counterparty for deposits. FX_POSITION holds the bank's side of every conversion.
INSERT INTO accounts (customer_id, currency, kind)
SELECT '00000000-0000-0000-0000-000000000001', currency, kind
FROM unnest(ARRAY ['GEL', 'USD', 'EUR']) AS currency
         CROSS JOIN unnest(ARRAY ['CASH', 'FX_POSITION']) AS kind
ORDER BY kind, currency;

-- Fixed rates keep conversions deterministic. Buy and sell differ on purpose (spread).
INSERT INTO fx_rates (base_currency, quote_currency, rate)
VALUES ('GEL', 'USD', 0.370000),
       ('USD', 'GEL', 2.650000),
       ('GEL', 'EUR', 0.340000),
       ('EUR', 'GEL', 2.890000),
       ('USD', 'EUR', 0.915000),
       ('EUR', 'USD', 1.072500);
