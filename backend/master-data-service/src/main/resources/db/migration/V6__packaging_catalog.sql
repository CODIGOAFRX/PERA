CREATE TABLE packaging_types (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    description TEXT,
    internal_length NUMERIC(15,4),
    internal_width NUMERIC(15,4),
    internal_height NUMERIC(15,4),
    external_length NUMERIC(15,4),
    external_width NUMERIC(15,4),
    external_height NUMERIC(15,4),
    tare_weight NUMERIC(15,4),
    maximum_weight NUMERIC(15,4),
    maximum_volume NUMERIC(19,6),
    returnable BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_packaging_type_code UNIQUE (company_id, code),
    CONSTRAINT uk_packaging_type_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_packaging_type_internal_dimensions CHECK (
        (internal_length IS NULL AND internal_width IS NULL AND internal_height IS NULL)
        OR (internal_length > 0 AND internal_width > 0 AND internal_height > 0)),
    CONSTRAINT ck_packaging_type_external_dimensions CHECK (
        (external_length IS NULL AND external_width IS NULL AND external_height IS NULL)
        OR (external_length > 0 AND external_width > 0 AND external_height > 0)),
    CONSTRAINT ck_packaging_type_dimension_fit CHECK (
        internal_length IS NULL OR external_length IS NULL OR
        (external_length >= internal_length AND external_width >= internal_width AND
            external_height >= internal_height)),
    CONSTRAINT ck_packaging_type_tare CHECK (tare_weight IS NULL OR tare_weight > 0),
    CONSTRAINT ck_packaging_type_maximum_weight CHECK (maximum_weight IS NULL OR maximum_weight > 0),
    CONSTRAINT ck_packaging_type_maximum_volume CHECK (maximum_volume IS NULL OR maximum_volume > 0),
    CONSTRAINT ck_packaging_type_weight_capacity CHECK (
        tare_weight IS NULL OR maximum_weight IS NULL OR maximum_weight >= tare_weight)
);

CREATE TABLE product_packaging (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    product_id UUID NOT NULL,
    packaging_type_id UUID NOT NULL,
    code VARCHAR(80),
    units_per_package NUMERIC(19,6) NOT NULL,
    levels INTEGER,
    units_per_level NUMERIC(19,6),
    length NUMERIC(15,4),
    width NUMERIC(15,4),
    height NUMERIC(15,4),
    gross_weight NUMERIC(15,4),
    default_packaging BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_packaging_product_company
        FOREIGN KEY (company_id, product_id) REFERENCES products(company_id, id),
    CONSTRAINT fk_product_packaging_type_company
        FOREIGN KEY (company_id, packaging_type_id) REFERENCES packaging_types(company_id, id),
    CONSTRAINT ck_product_packaging_units CHECK (units_per_package > 0),
    CONSTRAINT ck_product_packaging_levels CHECK (levels IS NULL OR levels > 0),
    CONSTRAINT ck_product_packaging_units_level CHECK (units_per_level IS NULL OR units_per_level > 0),
    CONSTRAINT ck_product_packaging_level_pair CHECK (
        (levels IS NULL AND units_per_level IS NULL) OR
        (levels IS NOT NULL AND units_per_level IS NOT NULL AND
            units_per_package = levels * units_per_level)),
    CONSTRAINT ck_product_packaging_dimensions CHECK (
        (length IS NULL AND width IS NULL AND height IS NULL)
        OR (length > 0 AND width > 0 AND height > 0)),
    CONSTRAINT ck_product_packaging_gross_weight CHECK (gross_weight IS NULL OR gross_weight > 0),
    CONSTRAINT ck_product_packaging_default_active CHECK (NOT default_packaging OR active)
);

CREATE UNIQUE INDEX uk_product_packaging_code
    ON product_packaging(company_id, code)
    WHERE code IS NOT NULL;
CREATE UNIQUE INDEX uk_product_packaging_active_default
    ON product_packaging(company_id, product_id)
    WHERE default_packaging AND active;
CREATE INDEX idx_packaging_types_search
    ON packaging_types(company_id, active, returnable, name);
CREATE INDEX idx_product_packaging_search
    ON product_packaging(company_id, product_id, packaging_type_id, active);
