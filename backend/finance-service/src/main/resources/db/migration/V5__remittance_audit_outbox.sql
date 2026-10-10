-- El cambio de remesa y su auditoría se confirman en la misma transacción local.
CREATE TABLE remittance_audit_outbox (
    event_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_remittance_audit_pending ON remittance_audit_outbox(next_attempt_at, created_at)
    WHERE published_at IS NULL;
