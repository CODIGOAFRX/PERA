CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    location VARCHAR(500),
    default_warehouse BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_warehouse_company_code UNIQUE (company_id, code),
    CONSTRAINT uk_warehouse_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_warehouse_default_active CHECK (active OR NOT default_warehouse)
);

CREATE UNIQUE INDEX uk_warehouse_company_default ON warehouses(company_id) WHERE default_warehouse;

-- Existencia actual por almacén y producto. Se mantiene en la misma transacción que el movimiento
-- que la modifica; nunca se edita directamente.
CREATE TABLE stock_levels (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_code_snapshot VARCHAR(100) NOT NULL,
    product_name_snapshot VARCHAR(300) NOT NULL,
    unit_of_measure_snapshot VARCHAR(30) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_stock_level_warehouse_product UNIQUE (company_id, warehouse_id, product_id),
    CONSTRAINT fk_stock_level_company_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES warehouses(company_id, id),
    CONSTRAINT ck_stock_level_quantity CHECK (quantity >= 0)
);

-- Diario de almacén: solo se añaden filas. Una corrección es otro movimiento de signo contrario.
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_code_snapshot VARCHAR(100) NOT NULL,
    product_name_snapshot VARCHAR(300) NOT NULL,
    unit_of_measure_snapshot VARCHAR(30) NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    balance_after NUMERIC(19, 6) NOT NULL,
    unit_cost NUMERIC(19, 6),
    cost_currency_code VARCHAR(3),
    occurred_at TIMESTAMPTZ NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID,
    source_number_snapshot VARCHAR(100),
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stock_movement_company_warehouse FOREIGN KEY (company_id, warehouse_id)
        REFERENCES warehouses(company_id, id),
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN (
        'PURCHASE_RECEIPT', 'PURCHASE_REVERSAL', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT ck_stock_movement_source CHECK (source_type IN ('MANUAL', 'TRANSFER', 'PURCHASE_DOCUMENT')),
    CONSTRAINT ck_stock_movement_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_movement_balance CHECK (balance_after >= 0),
    CONSTRAINT ck_stock_movement_cost CHECK (
        (unit_cost IS NULL AND cost_currency_code IS NULL)
        OR (unit_cost IS NOT NULL AND unit_cost >= 0 AND cost_currency_code ~ '^[A-Z]{3}$'))
);

CREATE INDEX idx_warehouses_company_active ON warehouses(company_id, active, code);
CREATE INDEX idx_stock_levels_company_product ON stock_levels(company_id, product_id);
CREATE INDEX idx_stock_movements_company_time ON stock_movements(company_id, occurred_at DESC);
CREATE INDEX idx_stock_movements_company_warehouse ON stock_movements(company_id, warehouse_id, occurred_at DESC);
CREATE INDEX idx_stock_movements_company_product ON stock_movements(company_id, product_id, occurred_at DESC);
CREATE INDEX idx_stock_movements_company_source ON stock_movements(company_id, source_type, source_id);
