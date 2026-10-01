CREATE TABLE purchase_document_sequences (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_sequence UNIQUE (company_id, document_type, fiscal_year),
    CONSTRAINT ck_purchase_sequence_type CHECK (document_type IN (
        'PURCHASE_ORDER', 'GOODS_RECEIPT', 'SUPPLIER_INVOICE')),
    CONSTRAINT ck_purchase_sequence_value CHECK (last_value >= 0)
);

CREATE TABLE purchase_documents (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    document_number VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    supplier_id UUID NOT NULL,
    supplier_code_snapshot VARCHAR(40) NOT NULL,
    supplier_name_snapshot VARCHAR(180) NOT NULL,
    supplier_tax_id_snapshot VARCHAR(30),
    supplier_reference VARCHAR(80),
    issue_date DATE NOT NULL,
    expected_date DATE,
    warehouse_id UUID,
    currency_code VARCHAR(3) NOT NULL,
    source_document_id UUID,
    stock_received_upstream BOOLEAN NOT NULL DEFAULT FALSE,
    stock_posted BOOLEAN NOT NULL DEFAULT FALSE,
    net_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_document_number UNIQUE (company_id, document_type, document_number),
    CONSTRAINT uk_purchase_document_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_purchase_document_company_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES warehouses(company_id, id),
    CONSTRAINT fk_purchase_document_company_source FOREIGN KEY (company_id, source_document_id)
        REFERENCES purchase_documents(company_id, id),
    CONSTRAINT ck_purchase_document_type CHECK (document_type IN (
        'PURCHASE_ORDER', 'GOODS_RECEIPT', 'SUPPLIER_INVOICE')),
    CONSTRAINT ck_purchase_document_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CONVERTED', 'CANCELLED')),
    CONSTRAINT ck_purchase_document_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_purchase_document_amounts CHECK (net_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_purchase_document_stock CHECK (NOT stock_posted OR warehouse_id IS NOT NULL)
);

CREATE TABLE purchase_document_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    document_id UUID NOT NULL,
    line_sequence INTEGER NOT NULL,
    product_id UUID,
    product_code_snapshot VARCHAR(100),
    description VARCHAR(300) NOT NULL,
    unit_of_measure_snapshot VARCHAR(30) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit_price NUMERIC(19, 6) NOT NULL,
    discount_percentage NUMERIC(7, 4) NOT NULL DEFAULT 0,
    tax_percentage NUMERIC(7, 4) NOT NULL DEFAULT 0,
    net_amount NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_purchase_line_sequence UNIQUE (company_id, document_id, line_sequence),
    CONSTRAINT fk_purchase_line_company_document FOREIGN KEY (company_id, document_id)
        REFERENCES purchase_documents(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_purchase_line_sequence CHECK (line_sequence > 0),
    CONSTRAINT ck_purchase_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_line_price CHECK (unit_price >= 0),
    CONSTRAINT ck_purchase_line_discount CHECK (discount_percentage >= 0 AND discount_percentage <= 100),
    CONSTRAINT ck_purchase_line_tax CHECK (tax_percentage >= 0 AND tax_percentage <= 100),
    CONSTRAINT ck_purchase_line_net CHECK (net_amount >= 0),
    CONSTRAINT ck_purchase_line_product_snapshot CHECK (product_id IS NULL OR product_code_snapshot IS NOT NULL)
);

CREATE INDEX idx_purchase_documents_company_date ON purchase_documents(company_id, issue_date DESC);
CREATE INDEX idx_purchase_documents_company_status ON purchase_documents(company_id, document_type, status);
CREATE INDEX idx_purchase_documents_company_supplier ON purchase_documents(company_id, supplier_id, issue_date DESC);
CREATE INDEX idx_purchase_documents_company_source ON purchase_documents(company_id, source_document_id);
CREATE INDEX idx_purchase_lines_company_document ON purchase_document_lines(company_id, document_id, line_sequence);

-- El mismo proveedor no puede tener registrada dos veces la misma factura en vigor.
CREATE UNIQUE INDEX uk_purchase_supplier_invoice_reference
    ON purchase_documents(company_id, supplier_id, lower(supplier_reference))
    WHERE document_type = 'SUPPLIER_INVOICE' AND status IN ('CONFIRMED', 'CONVERTED')
        AND supplier_reference IS NOT NULL;
