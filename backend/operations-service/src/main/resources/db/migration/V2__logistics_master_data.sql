CREATE TABLE carriers (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    ownership VARCHAR(20) NOT NULL,
    tax_identifier VARCHAR(40),
    external_identifier VARCHAR(100),
    contact_name VARCHAR(180),
    contact_email VARCHAR(254),
    contact_phone VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_carrier_company_code UNIQUE (company_id, code),
    CONSTRAINT uk_carrier_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_carrier_ownership CHECK (ownership IN ('OWN', 'THIRD_PARTY'))
);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    registration_plate VARCHAR(30),
    vehicle_type VARCHAR(80) NOT NULL,
    carrier_id UUID,
    capacity_weight_kg NUMERIC(19, 3),
    capacity_volume_m3 NUMERIC(19, 6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_vehicle_company_code UNIQUE (company_id, code),
    CONSTRAINT uk_vehicle_company_plate UNIQUE (company_id, registration_plate),
    CONSTRAINT uk_vehicle_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_vehicle_company_carrier FOREIGN KEY (company_id, carrier_id)
        REFERENCES carriers(company_id, id),
    CONSTRAINT ck_vehicle_weight_capacity CHECK (capacity_weight_kg IS NULL OR capacity_weight_kg >= 0),
    CONSTRAINT ck_vehicle_volume_capacity CHECK (capacity_volume_m3 IS NULL OR capacity_volume_m3 >= 0)
);

CREATE TABLE delivery_routes (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    origin_snapshot VARCHAR(500) NOT NULL,
    destination_snapshot VARCHAR(500) NOT NULL,
    carrier_id UUID,
    vehicle_id UUID,
    planned_departure_at TIMESTAMPTZ,
    planned_arrival_at TIMESTAMPTZ,
    delivery_window_start TIMESTAMPTZ,
    delivery_window_end TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_delivery_route_company_code UNIQUE (company_id, code),
    CONSTRAINT uk_delivery_route_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_route_company_carrier FOREIGN KEY (company_id, carrier_id)
        REFERENCES carriers(company_id, id),
    CONSTRAINT fk_route_company_vehicle FOREIGN KEY (company_id, vehicle_id)
        REFERENCES vehicles(company_id, id),
    CONSTRAINT ck_route_planned_times CHECK (
        planned_departure_at IS NULL OR planned_arrival_at IS NULL OR planned_arrival_at >= planned_departure_at),
    CONSTRAINT ck_route_delivery_window CHECK (
        delivery_window_start IS NULL OR delivery_window_end IS NULL OR delivery_window_end >= delivery_window_start)
);

CREATE TABLE delivery_route_stops (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    route_id UUID NOT NULL,
    stop_sequence INTEGER NOT NULL,
    name VARCHAR(180) NOT NULL,
    location_snapshot VARCHAR(500) NOT NULL,
    window_start TIMESTAMPTZ,
    window_end TIMESTAMPTZ,
    instructions VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_delivery_route_stop_sequence UNIQUE (company_id, route_id, stop_sequence),
    CONSTRAINT fk_route_stop_company_route FOREIGN KEY (company_id, route_id)
        REFERENCES delivery_routes(company_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_route_stop_sequence CHECK (stop_sequence > 0),
    CONSTRAINT ck_route_stop_window CHECK (
        window_start IS NULL OR window_end IS NULL OR window_end >= window_start)
);

CREATE INDEX idx_carriers_company_active ON carriers(company_id, active, name);
CREATE INDEX idx_vehicles_company_active ON vehicles(company_id, active, vehicle_type);
CREATE INDEX idx_vehicles_company_carrier ON vehicles(company_id, carrier_id);
CREATE INDEX idx_delivery_routes_company_active ON delivery_routes(company_id, active, name);
CREATE INDEX idx_route_stops_company_route ON delivery_route_stops(company_id, route_id, stop_sequence);
