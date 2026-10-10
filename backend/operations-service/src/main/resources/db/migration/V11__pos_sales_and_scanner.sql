-- Almacén conectado al TPV y a la aplicación de almacén del móvil.

-- Quién hizo cada movimiento, con qué motivo y desde qué aplicación. Los movimientos anteriores
-- no tienen autor: no se inventa.
ALTER TABLE stock_movements
    ADD COLUMN performed_by UUID,
    ADD COLUMN performed_by_name VARCHAR(160),
    ADD COLUMN reason_code VARCHAR(30),
    ADD COLUMN client_reference UUID;

ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movement_source;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movement_source CHECK (source_type IN (
    'MANUAL', 'TRANSFER', 'PURCHASE_DOCUMENT', 'SALES_DOCUMENT', 'POS_TICKET', 'SCAN'));
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movement_reason CHECK (reason_code IS NULL OR reason_code IN (
    'RECEIPT', 'CUSTOMER_RETURN', 'COUNT', 'DAMAGED', 'EXPIRED', 'SPOILED', 'LOST', 'INTERNAL_USE', 'OTHER'));

-- Un reintento del mismo escaneo (cobertura mala en el almacén, doble toque) no duplica el movimiento.
CREATE UNIQUE INDEX uk_stock_movement_client_reference ON stock_movements(company_id, client_reference)
    WHERE client_reference IS NOT NULL;
CREATE INDEX idx_stock_movements_company_occurred ON stock_movements(company_id, occurred_at DESC);
CREATE INDEX idx_stock_movements_performed_by ON stock_movements(company_id, performed_by, occurred_at DESC);

-- Marca de lectura de los tickets del TPV (finance-service), aparte de la de Ventas. Igual que con
-- Ventas, solo cuentan los tickets posteriores a la primera lectura.
CREATE TABLE pos_ticket_sync (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    last_source_update TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_ticket_sync_company UNIQUE (company_id)
);
