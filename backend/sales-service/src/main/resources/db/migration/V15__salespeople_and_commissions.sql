-- Comercial de cada documento y comisiones de venta, como en DimproCristalWin
-- (albaran.id_vend, comisiones_vendedor y comisiones_vendedor_documento).

-- El comercial se toma del cliente al crear el documento. Se guarda el nombre para no depender de
-- maestros al listar ni si el comercial se da de baja.
ALTER TABLE commercial_documents
    ADD COLUMN salesperson_id UUID,
    ADD COLUMN salesperson_name VARCHAR(160);
CREATE INDEX idx_documents_salesperson ON commercial_documents(company_id, salesperson_id, issue_date);

-- Reglas de comisión de cada comercial. Sin artículo ni grupo valen para cualquier línea; sin tramo,
-- para cualquier importe. Gana la más concreta (ver CommissionCalculator).
CREATE TABLE commission_rules (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    salesperson_id UUID NOT NULL,
    product_id UUID,
    product_label VARCHAR(220),
    product_group_id UUID,
    product_group_label VARCHAR(180),
    amount_from NUMERIC(19, 4),
    amount_to NUMERIC(19, 4),
    percentage NUMERIC(7, 4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_commission_rule_percentage CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT ck_commission_rule_range CHECK (amount_from IS NULL OR amount_to IS NULL OR amount_from <= amount_to),
    CONSTRAINT ck_commission_rule_target CHECK (product_id IS NULL OR product_group_id IS NULL)
);
CREATE INDEX idx_commission_rules_salesperson ON commission_rules(company_id, salesperson_id);

-- Comisión calculada de cada factura. Mientras está pendiente se puede recalcular; liquidada, no.
CREATE TABLE sales_commissions (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    document_id UUID NOT NULL REFERENCES commercial_documents(id),
    salesperson_id UUID NOT NULL,
    salesperson_name VARCHAR(160) NOT NULL,
    base_amount NUMERIC(19, 4) NOT NULL,
    commission_amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    settled_on DATE,
    settlement_note VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_commission_document UNIQUE (company_id, document_id),
    CONSTRAINT ck_sales_commission_status CHECK (status IN ('PENDING', 'SETTLED'))
);
CREATE INDEX idx_sales_commissions_salesperson ON sales_commissions(company_id, salesperson_id, status);

-- Detalle por línea: qué regla y qué porcentaje se aplicó, para poder explicar cada importe.
CREATE TABLE sales_commission_lines (
    id UUID PRIMARY KEY,
    commission_id UUID NOT NULL REFERENCES sales_commissions(id) ON DELETE CASCADE,
    line_order INT NOT NULL,
    description VARCHAR(500) NOT NULL,
    base_amount NUMERIC(19, 4) NOT NULL,
    percentage NUMERIC(7, 4) NOT NULL,
    commission_amount NUMERIC(19, 4) NOT NULL,
    rule_id UUID,
    origin VARCHAR(20) NOT NULL,
    CONSTRAINT ck_sales_commission_line_origin CHECK (origin IN ('RULE', 'DEFAULT', 'NONE'))
);
CREATE INDEX idx_sales_commission_lines ON sales_commission_lines(commission_id);
