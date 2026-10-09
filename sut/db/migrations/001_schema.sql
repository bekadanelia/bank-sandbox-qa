-- Sandbox Bank schema.
-- Money is stored as NUMERIC(19,2); every balance change is backed by ledger entries.

CREATE TABLE customers (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text        NOT NULL,
    full_name     text        NOT NULL,
    password_hash text        NOT NULL,
    kind          text        NOT NULL DEFAULT 'RETAIL' CHECK (kind IN ('RETAIL', 'SYSTEM')),
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX customers_email_uq ON customers (lower(email));

CREATE SEQUENCE account_number_seq START 1000000001;

-- Builds a Georgian style IBAN (GE + 2 check digits + bank code + 16 digits).
-- Check digits follow ISO 13616: move "GE00" to the end, map letters to numbers
-- (A=10 ... Z=35) and take 98 minus the remainder of division by 97.
CREATE FUNCTION generate_account_number() RETURNS text
    LANGUAGE plpgsql AS
$$
DECLARE
    bban         text    := 'ZZ' || lpad(nextval('account_number_seq')::text, 16, '0');
    numeric_form numeric := ('3535' || substr(bban, 3) || '161400')::numeric;
    check_digits int     := 98 - (numeric_form % 97)::int;
BEGIN
    RETURN 'GE' || lpad(check_digits::text, 2, '0') || bban;
END
$$;

CREATE TABLE accounts (
    id          uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    customer_id uuid        NOT NULL REFERENCES customers (id),
    number      text        NOT NULL DEFAULT generate_account_number(),
    currency    char(3)     NOT NULL CHECK (currency IN ('GEL', 'USD', 'EUR')),
    kind        text        NOT NULL DEFAULT 'CUSTOMER' CHECK (kind IN ('CUSTOMER', 'CASH', 'FX_POSITION')),
    status      text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN')),
    balance     numeric(19, 2) NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT accounts_number_uq UNIQUE (number),
    -- Customers can never be overdrawn. Internal bank accounts may go negative.
    CONSTRAINT accounts_customer_balance_non_negative CHECK (kind <> 'CUSTOMER' OR balance >= 0)
);

CREATE INDEX accounts_customer_idx ON accounts (customer_id);

-- One CASH and one FX_POSITION account per currency.
CREATE UNIQUE INDEX accounts_internal_uq ON accounts (kind, currency) WHERE kind <> 'CUSTOMER';

CREATE TABLE fx_rates (
    base_currency  char(3)        NOT NULL,
    quote_currency char(3)        NOT NULL,
    rate           numeric(12, 6) NOT NULL CHECK (rate > 0),
    updated_at     timestamptz    NOT NULL DEFAULT now(),
    PRIMARY KEY (base_currency, quote_currency),
    CONSTRAINT fx_rates_distinct_currencies CHECK (base_currency <> quote_currency)
);

CREATE TABLE transfers (
    id                uuid PRIMARY KEY        DEFAULT gen_random_uuid(),
    kind              text           NOT NULL CHECK (kind IN ('TRANSFER', 'DEPOSIT')),
    customer_id       uuid           NOT NULL REFERENCES customers (id),
    idempotency_key   text           NOT NULL,
    request_hash      text           NOT NULL,
    source_account_id uuid           NOT NULL REFERENCES accounts (id),
    target_account_id uuid           NOT NULL REFERENCES accounts (id),
    source_amount     numeric(19, 2) NOT NULL CHECK (source_amount > 0),
    source_currency   char(3)        NOT NULL,
    target_amount     numeric(19, 2) CHECK (target_amount > 0),
    target_currency   char(3)        NOT NULL,
    fx_rate           numeric(12, 6),
    status            text           NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    rejection_reason  text,
    description       text,
    created_at        timestamptz    NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    CONSTRAINT transfers_idempotency_uq UNIQUE (customer_id, idempotency_key),
    CONSTRAINT transfers_rejection_has_reason CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT transfers_completed_is_final CHECK (
        (status = 'COMPLETED') = (completed_at IS NOT NULL AND target_amount IS NOT NULL)
    ),
    CONSTRAINT transfers_fx_rate_only_cross_currency CHECK (
        fx_rate IS NULL OR source_currency <> target_currency
    )
);

CREATE INDEX transfers_source_idx ON transfers (source_account_id);
CREATE INDEX transfers_target_idx ON transfers (target_account_id);
CREATE INDEX transfers_pending_idx ON transfers (created_at) WHERE status = 'PENDING';

CREATE TABLE ledger_entries (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_id   uuid           NOT NULL REFERENCES transfers (id),
    account_id    uuid           NOT NULL REFERENCES accounts (id),
    direction     text           NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount        numeric(19, 2) NOT NULL CHECK (amount > 0),
    currency      char(3)        NOT NULL,
    balance_after numeric(19, 2) NOT NULL,
    created_at    timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX ledger_entries_account_idx ON ledger_entries (account_id, id DESC);
CREATE INDEX ledger_entries_transfer_idx ON ledger_entries (transfer_id);

-- The ledger is append only: corrections are made with new entries, never by rewriting history.
CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append only' USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE
    ON ledger_entries
    FOR EACH ROW
EXECUTE FUNCTION forbid_ledger_mutation();

-- Audit trail. Deliberately has no foreign key to the audited entity (it must outlive it),
-- which is why the test suite checks it for orphans.
CREATE TABLE audit_events (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type  text        NOT NULL,
    entity_type text        NOT NULL,
    entity_id   uuid        NOT NULL,
    actor_id    uuid,
    payload     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_entity_idx ON audit_events (entity_type, entity_id);
