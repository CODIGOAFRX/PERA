ALTER TABLE commercial_documents
    ALTER COLUMN document_number TYPE VARCHAR(80);

CREATE TABLE numbering_schemes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    series VARCHAR(20) NOT NULL,
    pattern VARCHAR(120) NOT NULL,
    reset_period VARCHAR(20) NOT NULL,
    initial_value BIGINT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    default_scheme BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_numbering_scheme_code UNIQUE (company_id, code),
    CONSTRAINT ck_numbering_initial_value CHECK (initial_value > 0)
);

CREATE UNIQUE INDEX uk_numbering_scheme_default
    ON numbering_schemes(company_id, document_type)
    WHERE default_scheme = TRUE;

CREATE INDEX idx_numbering_scheme_search
    ON numbering_schemes(company_id, document_type, active);

CREATE TABLE numbering_counters (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    scheme_id UUID NOT NULL REFERENCES numbering_schemes(id),
    period_key VARCHAR(20) NOT NULL,
    next_value BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_numbering_counter UNIQUE (company_id, scheme_id, period_key),
    CONSTRAINT ck_numbering_next_value CHECK (next_value > 0)
);
