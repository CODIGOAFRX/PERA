ALTER TABLE document_lines
    ADD COLUMN tax_code_id UUID,
    ADD COLUMN tax_code_snapshot VARCHAR(40),
    ADD COLUMN tax_country_code_snapshot VARCHAR(2),
    ADD COLUMN tax_name_snapshot VARCHAR(140),
    ADD COLUMN tax_exempt_snapshot BOOLEAN;

ALTER TABLE document_lines
    ADD CONSTRAINT ck_document_line_tax_snapshot CHECK (
        (tax_code_id IS NULL
            AND tax_code_snapshot IS NULL
            AND tax_country_code_snapshot IS NULL
            AND tax_name_snapshot IS NULL
            AND tax_exempt_snapshot IS NULL)
        OR
        (tax_code_id IS NOT NULL
            AND tax_code_snapshot IS NOT NULL
            AND tax_country_code_snapshot IS NOT NULL
            AND tax_name_snapshot IS NOT NULL
            AND tax_exempt_snapshot IS NOT NULL)
    );

COMMENT ON COLUMN document_lines.tax_code_id IS
    'Referencia informativa al código fiscal de maestros; no es una clave foránea entre servicios.';
