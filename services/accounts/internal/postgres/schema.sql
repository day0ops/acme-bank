CREATE TABLE IF NOT EXISTS accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL,
    account_type    SMALLINT NOT NULL,
    status          SMALLINT NOT NULL DEFAULT 0,
    currency_code   TEXT NOT NULL,
    balance_minor   BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_accounts_customer_id ON accounts (customer_id);

CREATE TABLE IF NOT EXISTS balance_operations (
    idempotency_key TEXT PRIMARY KEY,
    account_id      UUID NOT NULL REFERENCES accounts (id),
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
