CREATE TABLE parties (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    legal_name VARCHAR(180) NOT NULL,
    trade_name VARCHAR(180),
    tax_id VARCHAR(30),
    phone VARCHAR(40),
    email VARCHAR(180),
    website VARCHAR(240),
    observations TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_party_company_code UNIQUE (company_id, code)
);

CREATE TABLE product_types (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(140) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_product_type_code UNIQUE (company_id, code)
);

CREATE TABLE product_families (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(140) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_product_family_code UNIQUE (company_id, code)
);

CREATE TABLE product_categories (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(140) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_product_category_code UNIQUE (company_id, code)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    product_type_id UUID REFERENCES product_types(id),
    family_id UUID REFERENCES product_families(id),
    category_id UUID REFERENCES product_categories(id),
    unit_of_measure VARCHAR(30) NOT NULL,
    base_price NUMERIC(19,4) NOT NULL,
    tax_rate NUMERIC(7,4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_company_code UNIQUE (company_id, code)
);

CREATE TABLE price_lists (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, code VARCHAR(40) NOT NULL, name VARCHAR(140) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR', active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_price_list_code UNIQUE (company_id, code)
);

CREATE TABLE price_list_items (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, price_list_id UUID NOT NULL REFERENCES price_lists(id),
    product_id UUID NOT NULL REFERENCES products(id), price NUMERIC(19,4) NOT NULL, valid_from DATE NOT NULL,
    valid_until DATE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_price_list_product_date UNIQUE (company_id, price_list_id, product_id, valid_from)
);

CREATE TABLE customer_profiles (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    party_id UUID NOT NULL REFERENCES parties(id),
    price_list_id UUID REFERENCES price_lists(id),
    default_payment_method_id UUID,
    supplier_code VARCHAR(60),
    calculation_multiplier NUMERIC(15,6) NOT NULL DEFAULT 1,
    credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
    risk_warning_threshold NUMERIC(19,4) NOT NULL DEFAULT 0,
    risk_policy VARCHAR(30) NOT NULL DEFAULT 'WARN',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_party UNIQUE (party_id)
);

CREATE TABLE supplier_profiles (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    party_id UUID NOT NULL REFERENCES parties(id),
    carrier VARCHAR(160),
    route VARCHAR(160),
    default_payment_method_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_supplier_party UNIQUE (party_id)
);

CREATE TABLE party_addresses (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, party_id UUID NOT NULL REFERENCES parties(id),
    type VARCHAR(30) NOT NULL, line1 VARCHAR(200) NOT NULL, line2 VARCHAR(200), postal_code VARCHAR(20),
    city VARCHAR(100), province VARCHAR(100), country VARCHAR(100), primary_address BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE party_contacts (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, party_id UUID NOT NULL REFERENCES parties(id),
    name VARCHAR(160) NOT NULL, position VARCHAR(120), phone VARCHAR(40), email VARCHAR(180),
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE customer_notes (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    title VARCHAR(180) NOT NULL, message TEXT NOT NULL, show_on_documents BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE customer_special_rates (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    product_id UUID REFERENCES products(id), adjustment_type VARCHAR(30) NOT NULL,
    percentage NUMERIC(9,4) NOT NULL, valid_from DATE, valid_until DATE, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE customer_specific_prices (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    product_id UUID NOT NULL REFERENCES products(id), price NUMERIC(19,4) NOT NULL,
    discount_percentage NUMERIC(9,4) NOT NULL DEFAULT 0, valid_from DATE NOT NULL, valid_until DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_product_price UNIQUE (company_id, customer_id, product_id, valid_from)
);

CREATE TABLE work_sites (
    id UUID PRIMARY KEY, company_id UUID NOT NULL, customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    code VARCHAR(40) NOT NULL, name VARCHAR(180) NOT NULL, builder VARCHAR(180), address TEXT, description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_work_site_code UNIQUE (company_id, code)
);

CREATE INDEX idx_parties_search ON parties(company_id, code, legal_name, tax_id);
CREATE INDEX idx_products_search ON products(company_id, code, name);
CREATE INDEX idx_customer_profiles_company ON customer_profiles(company_id);
CREATE INDEX idx_supplier_profiles_company ON supplier_profiles(company_id);
