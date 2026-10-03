-- Reclamaciones de clientes (no conformidades) y agenda de citas y contactos.

-- Tablas de clasificación de reclamaciones: motivos, no conformidades, causas, áreas,
-- responsables, resoluciones y acciones preventivas, en una sola tabla por tipo.
CREATE TABLE claim_catalog_items (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    follow_up_days INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_claim_catalog_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_claim_catalog_kind CHECK (kind IN (
        'REASON', 'NONCONFORMITY', 'CAUSE', 'AREA', 'RESPONSIBLE', 'RESOLUTION', 'PREVENTIVE_ACTION')),
    CONSTRAINT ck_claim_catalog_days CHECK (
        follow_up_days IS NULL OR (kind = 'PREVENTIVE_ACTION' AND follow_up_days BETWEEN 0 AND 3650))
);
CREATE UNIQUE INDEX uk_claim_catalog_name ON claim_catalog_items(company_id, kind, lower(name));

CREATE TABLE claim_sequences (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    fiscal_year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_claim_sequence UNIQUE (company_id, fiscal_year)
);

CREATE TABLE claims (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    claim_number VARCHAR(40) NOT NULL,
    claim_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    customer_id UUID NOT NULL,
    customer_code_snapshot VARCHAR(60),
    customer_name_snapshot VARCHAR(180) NOT NULL,
    source_document_id UUID,
    source_document_number VARCHAR(100),
    source_document_date DATE,
    description VARCHAR(2000) NOT NULL,
    reported_by_user_id UUID NOT NULL,
    reported_by_name VARCHAR(160) NOT NULL,
    reason_id UUID,
    nonconformity_id UUID,
    cause_id UUID,
    area_id UUID,
    responsible_id UUID,
    resolution_id UUID,
    preventive_action_id UUID,
    follow_up_date DATE,
    closed_on DATE,
    closing_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_claim_company_number UNIQUE (company_id, claim_number),
    CONSTRAINT uk_claim_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_claim_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_claim_closed CHECK (
        (status = 'OPEN' AND closed_on IS NULL) OR (status = 'CLOSED' AND closed_on IS NOT NULL)),
    CONSTRAINT ck_claim_closed_after CHECK (closed_on IS NULL OR closed_on >= claim_date),
    CONSTRAINT ck_claim_source CHECK (source_document_id IS NULL OR source_document_number IS NOT NULL),
    CONSTRAINT fk_claim_reason FOREIGN KEY (company_id, reason_id) REFERENCES claim_catalog_items(company_id, id),
    CONSTRAINT fk_claim_nonconformity FOREIGN KEY (company_id, nonconformity_id) REFERENCES claim_catalog_items(company_id, id),
    CONSTRAINT fk_claim_cause FOREIGN KEY (company_id, cause_id) REFERENCES claim_catalog_items(company_id, id),
    CONSTRAINT fk_claim_area FOREIGN KEY (company_id, area_id) REFERENCES claim_catalog_items(company_id, id),
    CONSTRAINT fk_claim_responsible FOREIGN KEY (company_id, responsible_id) REFERENCES claim_catalog_items(company_id, id),
    CONSTRAINT fk_claim_resolution FOREIGN KEY (company_id, resolution_id) REFERENCES claim_catalog_items(company_id, id),
    CONSTRAINT fk_claim_preventive_action FOREIGN KEY (company_id, preventive_action_id) REFERENCES claim_catalog_items(company_id, id)
);

CREATE TABLE claim_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    claim_id UUID NOT NULL,
    line_sequence INTEGER NOT NULL,
    product_id UUID,
    product_code_snapshot VARCHAR(100),
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_claim_line_sequence UNIQUE (company_id, claim_id, line_sequence),
    CONSTRAINT fk_claim_line_claim FOREIGN KEY (company_id, claim_id)
        REFERENCES claims(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_claim_line_quantity CHECK (quantity > 0)
);

-- Seguimiento: solo se añaden comentarios, nunca se editan.
CREATE TABLE claim_comments (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    claim_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    author_name VARCHAR(160) NOT NULL,
    comment_text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_claim_comment_claim FOREIGN KEY (company_id, claim_id)
        REFERENCES claims(company_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_claims_company_date ON claims(company_id, claim_date DESC);
CREATE INDEX idx_claims_company_status ON claims(company_id, status, follow_up_date);
CREATE INDEX idx_claims_company_customer ON claims(company_id, customer_id);
CREATE INDEX idx_claim_lines_claim ON claim_lines(company_id, claim_id, line_sequence);
CREATE INDEX idx_claim_comments_claim ON claim_comments(company_id, claim_id, created_at);

-- Listín: personas y empresas con las que se habla, sean o no clientes.
CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL,
    organization VARCHAR(180),
    phone VARCHAR(40),
    mobile VARCHAR(40),
    email VARCHAR(180),
    address VARCHAR(300),
    postal_code VARCHAR(20),
    city VARCHAR(120),
    region VARCHAR(120),
    customer_id UUID,
    customer_name_snapshot VARCHAR(180),
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_contact_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_contact_customer CHECK (customer_id IS NULL OR customer_name_snapshot IS NOT NULL)
);
CREATE INDEX idx_contacts_company_name ON contacts(company_id, lower(name));

CREATE TABLE agenda_entry_types (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    color VARCHAR(7),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_agenda_type_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_agenda_type_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE UNIQUE INDEX uk_agenda_type_name ON agenda_entry_types(company_id, lower(name));

-- Cita o tarea en la agenda. Sin hora de inicio es de todo el día.
CREATE TABLE agenda_entries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    entry_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    title VARCHAR(200) NOT NULL,
    details VARCHAR(2000),
    type_id UUID,
    assignee_name VARCHAR(160),
    customer_id UUID,
    customer_name_snapshot VARCHAR(180),
    contact_id UUID,
    location VARCHAR(300),
    document_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_on DATE,
    outcome VARCHAR(1000),
    created_by_user_id UUID NOT NULL,
    created_by_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_agenda_entry_type FOREIGN KEY (company_id, type_id) REFERENCES agenda_entry_types(company_id, id),
    CONSTRAINT fk_agenda_entry_contact FOREIGN KEY (company_id, contact_id) REFERENCES contacts(company_id, id),
    CONSTRAINT ck_agenda_entry_status CHECK (status IN ('PENDING', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_agenda_entry_times CHECK (
        (start_time IS NULL AND end_time IS NULL) OR (start_time IS NOT NULL AND (end_time IS NULL OR end_time > start_time))),
    CONSTRAINT ck_agenda_entry_done CHECK ((status = 'DONE') = (completed_on IS NOT NULL)),
    CONSTRAINT ck_agenda_entry_customer CHECK (customer_id IS NULL OR customer_name_snapshot IS NOT NULL)
);
CREATE INDEX idx_agenda_entries_company_date ON agenda_entries(company_id, entry_date, start_time);
CREATE INDEX idx_agenda_entries_company_status ON agenda_entries(company_id, status, entry_date);
