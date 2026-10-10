-- TPV: tickets de venta y de devolución cobrados en una sesión de caja.
--
-- Cada ticket se cobra dentro de una sesión abierta. Lo cobrado en efectivo entra en el diario
-- de caja en la misma transacción (POS_SALE) y lo devuelto en efectivo sale de él (POS_REFUND),
-- así que el arqueo ya cuenta las ventas. Lo cobrado con tarjeta queda en el ticket.
-- operations-service lee los tickets por su punto interno para descontar existencias.

ALTER TABLE cash_movements DROP CONSTRAINT ck_cash_movement_type;
ALTER TABLE cash_movements ADD CONSTRAINT ck_cash_movement_type CHECK (movement_type IN (
    'OPENING', 'SALE_COLLECTION', 'INCOME', 'EXPENSE', 'WITHDRAWAL', 'CLOSING_ADJUSTMENT', 'POS_SALE', 'POS_REFUND'));

-- Numeración por caja, tipo de ticket y año: T2026-CAJA1-000001 y D2026-CAJA1-000001.
CREATE TABLE pos_ticket_sequences (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    cash_register_id UUID NOT NULL REFERENCES cash_registers(id),
    kind VARCHAR(20) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_ticket_sequence UNIQUE (company_id, cash_register_id, kind, fiscal_year),
    CONSTRAINT ck_pos_ticket_sequence_kind CHECK (kind IN ('SALE', 'REFUND')),
    CONSTRAINT ck_pos_ticket_sequence_value CHECK (last_value >= 0)
);

CREATE TABLE pos_tickets (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    cash_register_id UUID NOT NULL REFERENCES cash_registers(id),
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    ticket_number VARCHAR(60) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    cashier_id UUID NOT NULL,
    cashier_name VARCHAR(160) NOT NULL,
    customer_name VARCHAR(180),
    customer_tax_id VARCHAR(30),
    original_ticket_id UUID REFERENCES pos_tickets(id),
    original_ticket_number VARCHAR(60),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EUR',
    discount_percent NUMERIC(7, 4) NOT NULL DEFAULT 0,
    gross_amount NUMERIC(19, 4) NOT NULL,
    discount_amount NUMERIC(19, 4) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    tax_base_amount NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    cash_amount NUMERIC(19, 4) NOT NULL,
    card_amount NUMERIC(19, 4) NOT NULL,
    cash_tendered NUMERIC(19, 4),
    change_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    note VARCHAR(300),
    client_reference UUID,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_ticket_number UNIQUE (company_id, ticket_number),
    CONSTRAINT ck_pos_ticket_kind CHECK (kind IN ('SALE', 'REFUND')),
    CONSTRAINT ck_pos_ticket_status CHECK (status IN ('COMPLETED', 'VOIDED')),
    CONSTRAINT ck_pos_ticket_payment CHECK (payment_method IN ('CASH', 'CARD', 'MIXED')),
    CONSTRAINT ck_pos_ticket_amounts CHECK (total_amount >= 0 AND cash_amount >= 0 AND card_amount >= 0
        AND change_amount >= 0 AND cash_amount + card_amount = total_amount),
    CONSTRAINT ck_pos_ticket_discount CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT ck_pos_ticket_refund CHECK (kind <> 'REFUND' OR original_ticket_id IS NOT NULL),
    CONSTRAINT ck_pos_ticket_voided CHECK (status <> 'VOIDED' OR (voided_at IS NOT NULL AND voided_by IS NOT NULL)),
    CONSTRAINT ck_pos_ticket_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);
-- Un reintento del mismo cobro (red lenta, doble pulsación) devuelve el ticket ya creado.
CREATE UNIQUE INDEX uk_pos_ticket_client_reference ON pos_tickets(company_id, client_reference)
    WHERE client_reference IS NOT NULL;
CREATE INDEX idx_pos_tickets_session ON pos_tickets(company_id, cash_session_id, issued_at);
CREATE INDEX idx_pos_tickets_issued ON pos_tickets(company_id, issued_at DESC);
CREATE INDEX idx_pos_tickets_updated ON pos_tickets(company_id, updated_at);

CREATE TABLE pos_ticket_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    ticket_id UUID NOT NULL REFERENCES pos_tickets(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    product_code VARCHAR(60) NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    barcode VARCHAR(64),
    unit_of_measure VARCHAR(30) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    price_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    discount_percent NUMERIC(7, 4) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(7, 4) NOT NULL,
    line_total NUMERIC(19, 4) NOT NULL,
    refunded_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    refunded_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    original_line_id UUID REFERENCES pos_ticket_lines(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_ticket_line UNIQUE (company_id, ticket_id, line_number),
    CONSTRAINT ck_pos_ticket_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_pos_ticket_line_price CHECK (unit_price >= 0 AND line_total >= 0),
    CONSTRAINT ck_pos_ticket_line_discount CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT ck_pos_ticket_line_refund CHECK (refunded_quantity >= 0 AND refunded_quantity <= quantity
        AND refunded_amount >= 0 AND refunded_amount <= line_total)
);
CREATE INDEX idx_pos_ticket_lines_ticket ON pos_ticket_lines(company_id, ticket_id, line_number);
