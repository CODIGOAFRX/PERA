CREATE TABLE tax_codes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    percentage NUMERIC(7,4) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    exempt BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tax_code_country UNIQUE (company_id, country_code, code),
    CONSTRAINT uk_tax_code_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_tax_code_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_tax_code_percentage CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT ck_tax_code_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT ck_tax_code_exempt_percentage CHECK (NOT exempt OR percentage = 0)
);

ALTER TABLE products
    ADD COLUMN tax_code_id UUID,
    ADD CONSTRAINT fk_product_tax_code_company
        FOREIGN KEY (company_id, tax_code_id) REFERENCES tax_codes(company_id, id);

CREATE INDEX idx_tax_codes_company_country_active
    ON tax_codes(company_id, country_code, active, valid_from, valid_until);
CREATE INDEX idx_products_company_tax_code
    ON products(company_id, tax_code_id);
