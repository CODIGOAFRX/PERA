-- Cartera: recibos de cobro, remesas y caja pasan de esqueleto a módulo operativo.
-- Las tablas ya existían desde V1 sin uso; aquí se completan sin borrar nada.

CREATE TABLE collection_sequences (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    sequence_name VARCHAR(30) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_collection_sequence UNIQUE (company_id, sequence_name, fiscal_year),
    CONSTRAINT ck_collection_sequence_name CHECK (sequence_name IN ('RECEIPT', 'REMITTANCE')),
    CONSTRAINT ck_collection_sequence_value CHECK (last_value >= 0)
);

ALTER TABLE receipts
    ADD COLUMN installment_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN document_number_snapshot VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN customer_code_snapshot VARCHAR(60),
    ADD COLUMN customer_name_snapshot VARCHAR(180) NOT NULL DEFAULT '',
    ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN collection_method VARCHAR(30),
    ADD COLUMN return_date DATE,
    ADD COLUMN return_reason VARCHAR(300),
    ADD COLUMN remittance_id UUID REFERENCES remittances(id),
    ADD CONSTRAINT ck_receipt_status CHECK (status IN ('PENDING', 'REMITTED', 'COLLECTED', 'RETURNED', 'CANCELLED')),
    ADD CONSTRAINT ck_receipt_amount CHECK (amount > 0),
    ADD CONSTRAINT ck_receipt_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_receipt_method CHECK (collection_method IS NULL OR collection_method IN (
        'CASH', 'BANK_TRANSFER', 'CARD', 'DIRECT_DEBIT', 'CHEQUE', 'OTHER')),
    ADD CONSTRAINT ck_receipt_collection CHECK (
        status <> 'COLLECTED' OR (collection_date IS NOT NULL AND collection_method IS NOT NULL)),
    ADD CONSTRAINT ck_receipt_return CHECK (status <> 'RETURNED' OR return_date IS NOT NULL);

-- Un vencimiento tiene como mucho un recibo en vigor.
CREATE UNIQUE INDEX uk_receipt_live_due_date ON receipts(company_id, due_date_id)
    WHERE due_date_id IS NOT NULL AND status <> 'CANCELLED';
CREATE INDEX idx_receipts_company_due ON receipts(company_id, status, due_date);
CREATE INDEX idx_receipts_company_document ON receipts(company_id, document_id);
CREATE INDEX idx_receipts_company_remittance ON receipts(company_id, remittance_id);

ALTER TABLE remittances
    ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN notes VARCHAR(500),
    ADD CONSTRAINT ck_remittance_status CHECK (status IN (
        'DRAFT', 'SENT', 'SETTLED', 'PARTIALLY_RETURNED', 'CANCELLED')),
    ADD CONSTRAINT ck_remittance_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_remittance_total CHECK (total_amount >= 0);
CREATE INDEX idx_remittances_company_status ON remittances(company_id, status, creation_date DESC);

ALTER TABLE cash_sessions
    ADD COLUMN closing_note VARCHAR(300),
    ADD CONSTRAINT ck_cash_session_status CHECK (status IN ('OPEN', 'CLOSED')),
    ADD CONSTRAINT ck_cash_session_opening CHECK (opening_amount >= 0),
    ADD CONSTRAINT ck_cash_session_closed CHECK (
        status <> 'CLOSED' OR (closed_at IS NOT NULL AND closed_by IS NOT NULL
            AND expected_closing_amount IS NOT NULL AND actual_closing_amount IS NOT NULL));
-- Una caja solo puede tener una sesión abierta.
CREATE UNIQUE INDEX uk_cash_session_open ON cash_sessions(company_id, cash_register_id) WHERE status = 'OPEN';
CREATE INDEX idx_cash_sessions_company ON cash_sessions(company_id, opened_at DESC);

ALTER TABLE cash_movements
    ADD CONSTRAINT ck_cash_movement_type CHECK (movement_type IN (
        'OPENING', 'SALE_COLLECTION', 'INCOME', 'EXPENSE', 'WITHDRAWAL', 'CLOSING_ADJUSTMENT')),
    ADD CONSTRAINT ck_cash_movement_amount CHECK (amount > 0);
CREATE INDEX idx_cash_movements_session ON cash_movements(company_id, cash_session_id, occurred_at);
