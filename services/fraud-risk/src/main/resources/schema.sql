CREATE TABLE IF NOT EXISTS flagged_transactions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id        UUID NOT NULL,
    reason                TEXT NOT NULL,
    currency_code         TEXT NOT NULL,
    amount_minor          BIGINT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
