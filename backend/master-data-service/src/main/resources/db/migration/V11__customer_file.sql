-- Ficha de cliente completa: clasificación, comerciales, contactos, direcciones de entrega y notas.
-- Las tablas de contactos, direcciones y notas existían desde V1 sin uso; aquí se completan.

-- Tablas de clasificación de clientes que mantiene cada empresa.
CREATE TABLE customer_catalog_items (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_catalog_kind CHECK (kind IN ('GROUP', 'TYPE', 'DELIVERY_METHOD', 'INACTIVE_REASON'))
);
CREATE UNIQUE INDEX uk_customer_catalog_name ON customer_catalog_items(company_id, kind, lower(name));

-- Comerciales (vendedores). La comisión por defecto es la base del futuro cálculo de comisiones.
CREATE TABLE salespeople (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(180),
    phone VARCHAR(40),
    commission_percentage NUMERIC(7, 4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_salesperson_company_code UNIQUE (company_id, code),
    CONSTRAINT ck_salesperson_commission CHECK (
        commission_percentage IS NULL OR (commission_percentage >= 0 AND commission_percentage <= 100))
);

ALTER TABLE customer_profiles
    ADD COLUMN group_id UUID REFERENCES customer_catalog_items(id),
    ADD COLUMN type_id UUID REFERENCES customer_catalog_items(id),
    ADD COLUMN salesperson_id UUID REFERENCES salespeople(id),
    ADD COLUMN delivery_method_id UUID REFERENCES customer_catalog_items(id),
    ADD COLUMN inactive_reason_id UUID REFERENCES customer_catalog_items(id),
    ADD COLUMN mobile VARCHAR(40),
    ADD COLUMN accounting_account VARCHAR(20);
CREATE INDEX idx_customer_profiles_group ON customer_profiles(company_id, group_id);
CREATE INDEX idx_customer_profiles_type ON customer_profiles(company_id, type_id);
CREATE INDEX idx_customer_profiles_salesperson ON customer_profiles(company_id, salesperson_id);

-- El texto para documentos va en las notas con «mostrar en documentos»; no se duplica aquí.

-- Personas de contacto del cliente: compras, administración, obra...
ALTER TABLE party_contacts
    ADD COLUMN mobile VARCHAR(40),
    ADD COLUMN notes VARCHAR(300);
CREATE INDEX idx_party_contacts_party ON party_contacts(company_id, party_id);
CREATE UNIQUE INDEX uk_party_contact_primary ON party_contacts(party_id) WHERE primary_contact;

-- Direcciones de entrega (obras, almacenes, tiendas) además de la dirección fiscal de la ficha.
ALTER TABLE party_addresses
    ADD COLUMN label VARCHAR(120),
    ADD COLUMN contact_phone VARCHAR(40),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_party_address_type CHECK (type IN ('DELIVERY', 'BILLING', 'OTHER'));
CREATE INDEX idx_party_addresses_party ON party_addresses(company_id, party_id);
CREATE UNIQUE INDEX uk_party_address_primary ON party_addresses(party_id, type) WHERE primary_address;

CREATE INDEX idx_customer_notes_customer ON customer_notes(company_id, customer_id, created_at DESC);
