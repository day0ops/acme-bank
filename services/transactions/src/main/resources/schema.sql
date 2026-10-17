CREATE TABLE IF NOT EXISTS transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_account_id   UUID NOT NULL,
    to_account_id     UUID NOT NULL,
    currency_code     TEXT NOT NULL,
    amount_minor      BIGINT NOT NULL,
    status            SMALLINT NOT NULL DEFAULT 0,
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions (from_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions (to_account_id);

CREATE TABLE IF NOT EXISTS outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type TEXT NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     TEXT NOT NULL,
    payload        BYTEA NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
