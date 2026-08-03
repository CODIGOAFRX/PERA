CREATE TABLE payment_methods (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_payment_method_code UNIQUE(company_id,code)
);
CREATE TABLE payment_schedule_rules (
    id UUID PRIMARY KEY, payment_method_id UUID NOT NULL REFERENCES payment_methods(id) ON DELETE CASCADE,
    installment_number INTEGER NOT NULL, due_days INTEGER NOT NULL, percentage NUMERIC(9,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_rule_order UNIQUE(payment_method_id,installment_number)
);
CREATE TABLE document_due_dates (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, document_id UUID NOT NULL, installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL, amount NUMERIC(19,4) NOT NULL, paid_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_document_installment UNIQUE(company_id,document_id,installment_number)
);
CREATE TABLE receipts (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, receipt_number VARCHAR(50) NOT NULL, customer_id UUID NOT NULL,
    document_id UUID NOT NULL, due_date_id UUID REFERENCES document_due_dates(id), amount NUMERIC(19,4) NOT NULL,
    due_date DATE NOT NULL, collection_date DATE, status VARCHAR(30) NOT NULL, bank_account VARCHAR(80), notes TEXT,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_receipt_number UNIQUE(company_id,receipt_number)
);
CREATE TABLE remittances (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, remittance_number VARCHAR(50) NOT NULL,
    bank_account VARCHAR(80) NOT NULL, creation_date DATE NOT NULL, sent_date DATE, settlement_date DATE,
    status VARCHAR(30) NOT NULL, total_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_remittance_number UNIQUE(company_id,remittance_number)
);
CREATE TABLE remittance_receipts (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, remittance_id UUID NOT NULL REFERENCES remittances(id) ON DELETE CASCADE,
    receipt_id UUID NOT NULL REFERENCES receipts(id), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_remittance_receipt UNIQUE(remittance_id,receipt_id)
);
CREATE TABLE financial_movements (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, movement_date DATE NOT NULL, movement_type VARCHAR(40) NOT NULL,
    customer_id UUID, document_id UUID, receipt_id UUID REFERENCES receipts(id), debit_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(19,4) NOT NULL DEFAULT 0, concept VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE customer_risks (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, customer_id UUID NOT NULL,
    current_exposure NUMERIC(19,4) NOT NULL DEFAULT 0, credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
    warning_threshold NUMERIC(19,4) NOT NULL DEFAULT 0, risk_action VARCHAR(30) NOT NULL DEFAULT 'WARN',
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_risk UNIQUE(company_id,customer_id)
);
CREATE TABLE cash_registers (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL,
    owner_name VARCHAR(160), active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cash_register_code UNIQUE(company_id,code)
);
CREATE TABLE cash_sessions (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, cash_register_id UUID NOT NULL REFERENCES cash_registers(id),
    opened_by UUID NOT NULL, closed_by UUID, opened_at TIMESTAMPTZ NOT NULL, closed_at TIMESTAMPTZ,
    opening_amount NUMERIC(19,4) NOT NULL, expected_closing_amount NUMERIC(19,4), actual_closing_amount NUMERIC(19,4),
    status VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE cash_movements (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    occurred_at TIMESTAMPTZ NOT NULL, movement_type VARCHAR(40) NOT NULL, amount NUMERIC(19,4) NOT NULL,
    document_id UUID, receipt_id UUID REFERENCES receipts(id), concept VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_due_dates_pending ON document_due_dates(company_id,due_date) WHERE status IN ('PENDING','PARTIALLY_PAID');
CREATE INDEX idx_receipts_customer ON receipts(company_id,customer_id,status);
CREATE INDEX idx_financial_movements_date ON financial_movements(company_id,movement_date);
