CREATE TABLE currencies (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(3) NOT NULL,
    name VARCHAR(120) NOT NULL,
    symbol VARCHAR(12) NOT NULL,
    decimal_places INTEGER NOT NULL DEFAULT 2,
    base_currency BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_currency_code UNIQUE (company_id, code),
    CONSTRAINT ck_currency_decimal_places CHECK (decimal_places BETWEEN 0 AND 6)
);

CREATE UNIQUE INDEX uk_currency_base
    ON currencies(company_id)
    WHERE base_currency = TRUE;

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    base_code VARCHAR(3) NOT NULL,
    quote_code VARCHAR(3) NOT NULL,
    rate NUMERIC(19,10) NOT NULL,
    rate_date DATE NOT NULL,
    source VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_exchange_rate UNIQUE (company_id, base_code, quote_code, rate_date, source),
    CONSTRAINT ck_exchange_rate_positive CHECK (rate > 0),
    CONSTRAINT ck_exchange_rate_pair CHECK (base_code <> quote_code)
);

CREATE INDEX idx_exchange_rate_resolution
    ON exchange_rates(company_id, base_code, quote_code, rate_date DESC)
    WHERE active = TRUE;
