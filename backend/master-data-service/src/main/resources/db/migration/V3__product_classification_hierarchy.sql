CREATE TABLE product_natures (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_nature_code UNIQUE (company_id, code),
    CONSTRAINT uk_product_nature_company_id UNIQUE (company_id, id)
);

CREATE TABLE product_supertypes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    nature_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_supertype_code UNIQUE (company_id, code),
    CONSTRAINT uk_product_supertype_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_product_supertype_nature_company
        FOREIGN KEY (company_id, nature_id) REFERENCES product_natures(company_id, id)
);

ALTER TABLE product_types
    ADD COLUMN supertype_id UUID;

ALTER TABLE product_types
    ADD CONSTRAINT uk_product_type_company_id UNIQUE (company_id, id),
    ADD CONSTRAINT fk_product_type_supertype_company
        FOREIGN KEY (company_id, supertype_id) REFERENCES product_supertypes(company_id, id);

CREATE TABLE product_groups (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    product_type_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(140) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_group_code UNIQUE (company_id, code),
    CONSTRAINT uk_product_group_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_product_group_type_company
        FOREIGN KEY (company_id, product_type_id) REFERENCES product_types(company_id, id)
);

ALTER TABLE products
    ADD COLUMN product_group_id UUID,
    ADD CONSTRAINT fk_product_group_company
        FOREIGN KEY (company_id, product_group_id) REFERENCES product_groups(company_id, id);

CREATE INDEX idx_product_natures_company_name
    ON product_natures(company_id, active, name);
CREATE INDEX idx_product_supertypes_company_nature
    ON product_supertypes(company_id, nature_id, active);
CREATE INDEX idx_product_types_company_supertype
    ON product_types(company_id, supertype_id, active);
CREATE INDEX idx_product_groups_company_type
    ON product_groups(company_id, product_type_id, active);
CREATE INDEX idx_products_company_group
    ON products(company_id, product_group_id);
