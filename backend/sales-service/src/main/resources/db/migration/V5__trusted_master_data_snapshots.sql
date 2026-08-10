ALTER TABLE document_lines
    ADD COLUMN requested_quantity NUMERIC(19,6),
    ADD COLUMN tariff_id UUID,
    ADD COLUMN tariff_code_snapshot VARCHAR(60),
    ADD COLUMN pricing_resolved_amount NUMERIC(19,4),
    ADD COLUMN pricing_trace_json TEXT;

UPDATE document_lines SET requested_quantity = quantity;

ALTER TABLE document_lines
    ALTER COLUMN requested_quantity SET NOT NULL,
    ADD CONSTRAINT ck_document_line_requested_quantity CHECK (requested_quantity > 0),
    ADD CONSTRAINT ck_document_line_pricing_snapshot CHECK (
        (tariff_id IS NULL AND tariff_code_snapshot IS NULL)
        OR (tariff_id IS NOT NULL AND tariff_code_snapshot IS NOT NULL)
    ),
    ADD CONSTRAINT ck_document_line_resolved_amount CHECK (
        pricing_resolved_amount IS NULL OR pricing_resolved_amount >= 0
    );
