CREATE TABLE document_sequences (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, document_type VARCHAR(30) NOT NULL,
    sequence_year INTEGER NOT NULL, next_value BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_sequence UNIQUE (company_id, document_type, sequence_year)
);

CREATE TABLE commercial_documents (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    document_number VARCHAR(40) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    customer_id UUID NOT NULL,
    customer_code_snapshot VARCHAR(60) NOT NULL,
    customer_name_snapshot VARCHAR(180) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    source_document_id UUID REFERENCES commercial_documents(id),
    payment_method_id UUID,
    payment_status VARCHAR(30) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_number UNIQUE (company_id, document_type, document_number)
);

CREATE TABLE document_lines (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES commercial_documents(id) ON DELETE CASCADE,
    line_order INTEGER NOT NULL,
    product_id UUID,
    product_code_snapshot VARCHAR(60),
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(19,6) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount_percentage NUMERIC(9,4) NOT NULL DEFAULT 0,
    tax_percentage NUMERIC(9,4) NOT NULL DEFAULT 0,
    net_amount NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_line_order UNIQUE (document_id, line_order)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_documents_company_date ON commercial_documents(company_id, issue_date DESC);
CREATE INDEX idx_documents_customer ON commercial_documents(company_id, customer_id);
CREATE INDEX idx_documents_status ON commercial_documents(company_id, document_type, status);
CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;
