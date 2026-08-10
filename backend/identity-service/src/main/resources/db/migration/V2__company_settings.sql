CREATE TABLE company_settings (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    country_code VARCHAR(2) NOT NULL DEFAULT 'ES',
    locale VARCHAR(35) NOT NULL DEFAULT 'es-ES',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Madrid',
    base_currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    display_name VARCHAR(180) NOT NULL,
    logo_storage_key VARCHAR(500),
    logo_content_type VARCHAR(40),
    logo_sha256 VARCHAR(64),
    contact_email VARCHAR(180),
    invoice_email VARCHAR(180),
    reply_to_email VARCHAR(180),
    phone VARCHAR(40),
    website VARCHAR(240),
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    postal_code VARCHAR(20),
    city VARCHAR(100),
    region VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_settings_company UNIQUE (company_id),
    CONSTRAINT ck_company_settings_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_company_settings_currency CHECK (base_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_company_settings_logo_metadata CHECK (
        (logo_storage_key IS NULL AND logo_content_type IS NULL AND logo_sha256 IS NULL)
        OR (logo_storage_key IS NOT NULL AND logo_content_type IS NOT NULL AND logo_sha256 IS NOT NULL)
    )
);

INSERT INTO company_settings (
    id, company_id, country_code, locale, timezone, base_currency, display_name,
    created_at, updated_at, version
)
SELECT id, id, 'ES', 'es-ES', 'Europe/Madrid', 'EUR', name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM companies;
