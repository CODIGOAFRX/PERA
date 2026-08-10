CREATE TABLE freight_rates (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(180) NOT NULL,
    route_id UUID,
    carrier_id UUID,
    currency_code VARCHAR(3) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    calculation_method VARCHAR(30) NOT NULL,
    fixed_amount NUMERIC(19, 4),
    unit_amount NUMERIC(19, 6),
    minimum_charge NUMERIC(19, 4),
    maximum_charge NUMERIC(19, 4),
    minimum_weight_kg NUMERIC(19, 3),
    maximum_weight_kg NUMERIC(19, 3),
    minimum_volume_m3 NUMERIC(19, 6),
    maximum_volume_m3 NUMERIC(19, 6),
    minimum_distance_km NUMERIC(19, 3),
    maximum_distance_km NUMERIC(19, 3),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_freight_rate_company_code UNIQUE (company_id, code),
    CONSTRAINT uk_freight_rate_company_id UNIQUE (company_id, id),
    CONSTRAINT fk_freight_rate_company_route FOREIGN KEY (company_id, route_id)
        REFERENCES delivery_routes(company_id, id),
    CONSTRAINT fk_freight_rate_company_carrier FOREIGN KEY (company_id, carrier_id)
        REFERENCES carriers(company_id, id),
    CONSTRAINT ck_freight_rate_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_freight_rate_validity CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_freight_rate_method CHECK (calculation_method IN (
        'FIXED', 'PER_KG', 'PER_M3', 'PER_KM',
        'FIXED_PLUS_PER_KG', 'FIXED_PLUS_PER_M3', 'FIXED_PLUS_PER_KM')),
    CONSTRAINT ck_freight_rate_method_amounts CHECK (
        (calculation_method = 'FIXED' AND fixed_amount IS NOT NULL AND unit_amount IS NULL)
        OR (calculation_method IN ('PER_KG', 'PER_M3', 'PER_KM')
            AND fixed_amount IS NULL AND unit_amount IS NOT NULL)
        OR (calculation_method IN ('FIXED_PLUS_PER_KG', 'FIXED_PLUS_PER_M3', 'FIXED_PLUS_PER_KM')
            AND fixed_amount IS NOT NULL AND unit_amount IS NOT NULL)),
    CONSTRAINT ck_freight_rate_non_negative_amounts CHECK (
        (fixed_amount IS NULL OR fixed_amount >= 0)
        AND (unit_amount IS NULL OR unit_amount >= 0)
        AND (minimum_charge IS NULL OR minimum_charge >= 0)
        AND (maximum_charge IS NULL OR maximum_charge >= 0)),
    CONSTRAINT ck_freight_rate_charge_range CHECK (
        minimum_charge IS NULL OR maximum_charge IS NULL OR maximum_charge >= minimum_charge),
    CONSTRAINT ck_freight_rate_weight_range CHECK (
        (minimum_weight_kg IS NULL OR minimum_weight_kg >= 0)
        AND (maximum_weight_kg IS NULL OR maximum_weight_kg >= 0)
        AND (minimum_weight_kg IS NULL OR maximum_weight_kg IS NULL OR maximum_weight_kg >= minimum_weight_kg)),
    CONSTRAINT ck_freight_rate_volume_range CHECK (
        (minimum_volume_m3 IS NULL OR minimum_volume_m3 >= 0)
        AND (maximum_volume_m3 IS NULL OR maximum_volume_m3 >= 0)
        AND (minimum_volume_m3 IS NULL OR maximum_volume_m3 IS NULL OR maximum_volume_m3 >= minimum_volume_m3)),
    CONSTRAINT ck_freight_rate_distance_range CHECK (
        (minimum_distance_km IS NULL OR minimum_distance_km >= 0)
        AND (maximum_distance_km IS NULL OR maximum_distance_km >= 0)
        AND (minimum_distance_km IS NULL OR maximum_distance_km IS NULL OR maximum_distance_km >= minimum_distance_km))
);

CREATE INDEX idx_freight_rates_resolution
    ON freight_rates(company_id, currency_code, active, valid_from, valid_to, priority);
CREATE INDEX idx_freight_rates_route ON freight_rates(company_id, route_id);
CREATE INDEX idx_freight_rates_carrier ON freight_rates(company_id, carrier_id);

ALTER TABLE shipments
    ADD COLUMN freight_rate_id UUID,
    ADD COLUMN freight_rate_code_snapshot VARCHAR(60),
    ADD COLUMN freight_rate_name_snapshot VARCHAR(180),
    ADD COLUMN freight_method_snapshot VARCHAR(30),
    ADD COLUMN freight_pricing_date_snapshot DATE,
    ADD COLUMN freight_fixed_component_snapshot NUMERIC(19, 4),
    ADD COLUMN freight_variable_component_snapshot NUMERIC(19, 4),
    ADD COLUMN freight_distance_km_snapshot NUMERIC(19, 3),
    ADD COLUMN freight_minimum_applied_snapshot BOOLEAN,
    ADD COLUMN freight_maximum_applied_snapshot BOOLEAN,
    ADD CONSTRAINT fk_shipment_company_freight_rate FOREIGN KEY (company_id, freight_rate_id)
        REFERENCES freight_rates(company_id, id),
    ADD CONSTRAINT ck_shipment_freight_snapshot_method CHECK (
        freight_method_snapshot IS NULL OR freight_method_snapshot IN (
            'FIXED', 'PER_KG', 'PER_M3', 'PER_KM',
            'FIXED_PLUS_PER_KG', 'FIXED_PLUS_PER_M3', 'FIXED_PLUS_PER_KM')),
    ADD CONSTRAINT ck_shipment_freight_snapshot_complete CHECK (
        (freight_rate_id IS NULL
            AND freight_rate_code_snapshot IS NULL
            AND freight_rate_name_snapshot IS NULL
            AND freight_method_snapshot IS NULL
            AND freight_pricing_date_snapshot IS NULL
            AND freight_fixed_component_snapshot IS NULL
            AND freight_variable_component_snapshot IS NULL
            AND freight_minimum_applied_snapshot IS NULL
            AND freight_maximum_applied_snapshot IS NULL)
        OR
        (freight_rate_id IS NOT NULL
            AND freight_rate_code_snapshot IS NOT NULL
            AND freight_rate_name_snapshot IS NOT NULL
            AND freight_method_snapshot IS NOT NULL
            AND freight_pricing_date_snapshot IS NOT NULL
            AND freight_fixed_component_snapshot IS NOT NULL
            AND freight_variable_component_snapshot IS NOT NULL
            AND freight_minimum_applied_snapshot IS NOT NULL
            AND freight_maximum_applied_snapshot IS NOT NULL)),
    ADD CONSTRAINT ck_shipment_freight_distance_snapshot CHECK (
        freight_distance_km_snapshot IS NULL OR freight_distance_km_snapshot >= 0);

CREATE INDEX idx_shipments_company_freight_rate ON shipments(company_id, freight_rate_id);
