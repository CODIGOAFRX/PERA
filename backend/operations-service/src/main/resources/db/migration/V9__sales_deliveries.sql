-- Salidas de almacén por ventas. operations-service lee de sales-service los albaranes y las
-- facturas sin albarán, los guarda aquí y les da salida en el diario de almacén.
ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movement_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movement_type CHECK (movement_type IN (
    'PURCHASE_RECEIPT', 'PURCHASE_REVERSAL', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_IN', 'TRANSFER_OUT',
    'SALES_ISSUE', 'SALES_RETURN'));
ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movement_source;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movement_source CHECK (source_type IN (
    'MANUAL', 'TRANSFER', 'PURCHASE_DOCUMENT', 'SALES_DOCUMENT'));

CREATE TABLE sales_deliveries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    source_document_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_number VARCHAR(100) NOT NULL,
    source_date DATE NOT NULL,
    source_status VARCHAR(30) NOT NULL,
    customer_code VARCHAR(60),
    customer_name VARCHAR(180) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    warehouse_id UUID,
    problem VARCHAR(500),
    posted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_delivery_source UNIQUE (company_id, source_document_id),
    CONSTRAINT uk_sales_delivery_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_sales_delivery_company_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES warehouses(company_id, id),
    CONSTRAINT ck_sales_delivery_status CHECK (delivery_status IN (
        'PENDING', 'POSTED', 'NOT_APPLICABLE', 'REVERSED', 'DISMISSED')),
    CONSTRAINT ck_sales_delivery_posted CHECK (
        delivery_status NOT IN ('POSTED', 'REVERSED') OR (warehouse_id IS NOT NULL AND posted_at IS NOT NULL))
);

CREATE TABLE sales_delivery_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    line_sequence INTEGER NOT NULL,
    product_id UUID NOT NULL,
    product_code_snapshot VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_delivery_line_sequence UNIQUE (company_id, delivery_id, line_sequence),
    CONSTRAINT fk_sales_delivery_line_delivery FOREIGN KEY (company_id, delivery_id)
        REFERENCES sales_deliveries(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_sales_delivery_line_quantity CHECK (quantity > 0)
);

-- Hasta qué momento de sales-service se ha leído ya. Solo cuentan las ventas posteriores a la
-- primera sincronización: activar el inventario no descuenta el histórico.
CREATE TABLE sales_delivery_sync (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    last_source_update TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_delivery_sync_company UNIQUE (company_id)
);

CREATE INDEX idx_sales_deliveries_company_status ON sales_deliveries(company_id, delivery_status, source_date DESC);
CREATE INDEX idx_sales_delivery_lines_delivery ON sales_delivery_lines(company_id, delivery_id, line_sequence);
