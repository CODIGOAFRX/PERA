CREATE TABLE accounting_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(180) NOT NULL,
    account_kind VARCHAR(30) NOT NULL,
    system_defined BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accounting_account_code UNIQUE (company_id, code)
);

CREATE TABLE accounting_journal_entries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    entry_date DATE NOT NULL,
    description VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_type VARCHAR(40),
    source_id UUID,
    source_number VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE accounting_journal_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    entry_id UUID NOT NULL REFERENCES accounting_journal_entries(id) ON DELETE CASCADE,
    line_order INTEGER NOT NULL,
    account_id UUID NOT NULL REFERENCES accounting_accounts(id),
    description VARCHAR(300),
    debit NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_accounting_line_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    ),
    CONSTRAINT uk_accounting_line_order UNIQUE (entry_id, line_order)
);

CREATE TABLE accounting_inbox_items (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    source_number VARCHAR(80) NOT NULL,
    source_date DATE NOT NULL,
    source_status VARCHAR(30) NOT NULL,
    counterparty_code VARCHAR(60),
    counterparty_name VARCHAR(180) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    inbox_status VARCHAR(20) NOT NULL,
    journal_entry_id UUID REFERENCES accounting_journal_entries(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accounting_inbox_source UNIQUE (company_id, source_type, source_id)
);

CREATE INDEX idx_accounting_accounts_search ON accounting_accounts(company_id, active, code);
CREATE INDEX idx_accounting_entries_date ON accounting_journal_entries(company_id, entry_date DESC);
CREATE INDEX idx_accounting_inbox_pending ON accounting_inbox_items(company_id, inbox_status, source_date DESC);
