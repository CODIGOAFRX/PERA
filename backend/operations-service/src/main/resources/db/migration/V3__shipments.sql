CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    shipment_number VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    status_before_exception VARCHAR(20),
    origin_snapshot VARCHAR(500),
    destination_snapshot VARCHAR(500),
    carrier_id UUID,
    vehicle_id UUID,
    route_id UUID,
    planned_departure_at TIMESTAMPTZ,
    planned_arrival_at TIMESTAMPTZ,
    actual_departure_at TIMESTAMPTZ,
    actual_arrival_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    freight_cost NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    total_weight_kg NUMERIC(19, 3),
    total_volume_m3 NUMERIC(19, 6),
    status_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipment_company_number UNIQUE (company_id, shipment_number),
    CONSTRAINT uk_shipment_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_shipment_company_carrier FOREIGN KEY (company_id, carrier_id)
        REFERENCES carriers(company_id, id),
    CONSTRAINT fk_shipment_company_vehicle FOREIGN KEY (company_id, vehicle_id)
        REFERENCES vehicles(company_id, id),
    CONSTRAINT fk_shipment_company_route FOREIGN KEY (company_id, route_id)
        REFERENCES delivery_routes(company_id, id),
    CONSTRAINT ck_shipment_status CHECK (status IN (
        'PLANNED', 'PACKING', 'READY', 'DISPATCHED', 'IN_TRANSIT', 'ARRIVED', 'DELIVERED', 'EXCEPTION', 'CANCELLED')),
    CONSTRAINT ck_shipment_previous_status CHECK (status_before_exception IS NULL OR status_before_exception IN (
        'PLANNED', 'PACKING', 'READY', 'DISPATCHED', 'IN_TRANSIT', 'ARRIVED')),
    CONSTRAINT ck_shipment_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_shipment_freight_cost CHECK (freight_cost >= 0),
    CONSTRAINT ck_shipment_weight CHECK (total_weight_kg IS NULL OR total_weight_kg >= 0),
    CONSTRAINT ck_shipment_volume CHECK (total_volume_m3 IS NULL OR total_volume_m3 >= 0),
    CONSTRAINT ck_shipment_planned_times CHECK (
        planned_departure_at IS NULL OR planned_arrival_at IS NULL OR planned_arrival_at >= planned_departure_at),
    CONSTRAINT ck_shipment_actual_times CHECK (
        actual_departure_at IS NULL OR actual_arrival_at IS NULL OR actual_arrival_at >= actual_departure_at),
    CONSTRAINT ck_shipment_delivery_time CHECK (
        actual_arrival_at IS NULL OR delivered_at IS NULL OR delivered_at >= actual_arrival_at)
);

CREATE TABLE shipment_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    line_sequence INTEGER NOT NULL,
    product_id UUID,
    product_code_snapshot VARCHAR(100),
    product_name_snapshot VARCHAR(300) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit_of_measure_snapshot VARCHAR(30) NOT NULL,
    source_document_id UUID,
    source_document_type VARCHAR(80),
    source_document_number_snapshot VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipment_line_sequence UNIQUE (company_id, shipment_id, line_sequence),
    CONSTRAINT fk_shipment_line_company_shipment FOREIGN KEY (company_id, shipment_id)
        REFERENCES shipments(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_shipment_line_sequence CHECK (line_sequence > 0),
    CONSTRAINT ck_shipment_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_shipment_line_product_snapshot CHECK (product_id IS NULL OR product_code_snapshot IS NOT NULL),
    CONSTRAINT ck_shipment_line_document_snapshot CHECK (
        (source_document_id IS NULL AND source_document_type IS NULL AND source_document_number_snapshot IS NULL)
        OR
        (source_document_id IS NOT NULL AND source_document_type IS NOT NULL AND source_document_number_snapshot IS NOT NULL))
);

CREATE TABLE shipment_documents (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipment_document_storage_key UNIQUE (company_id, shipment_id, storage_key),
    CONSTRAINT fk_shipment_document_company_shipment FOREIGN KEY (company_id, shipment_id)
        REFERENCES shipments(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_shipment_document_sha256 CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_shipment_document_size CHECK (size_bytes >= 0)
);

CREATE INDEX idx_shipments_company_status ON shipments(company_id, status, planned_departure_at);
CREATE INDEX idx_shipments_company_carrier ON shipments(company_id, carrier_id, status);
CREATE INDEX idx_shipments_company_vehicle ON shipments(company_id, vehicle_id, status);
CREATE INDEX idx_shipments_company_route ON shipments(company_id, route_id, status);
CREATE INDEX idx_shipment_lines_company_shipment ON shipment_lines(company_id, shipment_id, line_sequence);
CREATE INDEX idx_shipment_documents_company_shipment ON shipment_documents(company_id, shipment_id, created_at);
