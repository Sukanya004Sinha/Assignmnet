CREATE TABLE wallets (
    id              UUID PRIMARY KEY,
    user_id         TEXT NOT NULL UNIQUE,
    balance_paise   BIGINT NOT NULL DEFAULT 0 CHECK (balance_paise >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transfers (
    id                  UUID PRIMARY KEY,
    from_wallet         UUID NOT NULL REFERENCES wallets(id),
    to_wallet           UUID NOT NULL REFERENCES wallets(id),
    amount_paise        BIGINT NOT NULL CHECK (amount_paise > 0),
    status              TEXT NOT NULL,           -- PENDING | COMPLETED | DECLINED
    idempotency_key     TEXT NOT NULL UNIQUE,
    request_hash        TEXT NOT NULL,
    reversal_of         UUID REFERENCES transfers(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transfers_from_wallet ON transfers(from_wallet);
CREATE INDEX idx_transfers_to_wallet ON transfers(to_wallet);
CREATE INDEX idx_transfers_reversal_of ON transfers(reversal_of);
