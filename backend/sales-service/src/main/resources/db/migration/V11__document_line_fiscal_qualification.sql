-- Calificación fiscal congelada en la línea del documento.
--
-- El desglose del registro de facturación no agrupa por porcentaje de IVA sino por combinación
-- fiscal: régimen, calificación, causa de exención y tipo. Esos tres primeros datos viven en el
-- código de impuesto de maestros, y hay que congelarlos en la línea al emitir por el mismo motivo
-- que ya se congelan el código, el nombre y el porcentaje: un registro remitido debe poder
-- reproducirse aunque el maestro cambie después.
--
-- Migración aditiva. Las líneas anteriores quedan sin calificación; el agregador usa entonces la
-- de la empresa, que es lo que estaban declarando implícitamente.

ALTER TABLE document_lines
    ADD COLUMN tax_qualification_snapshot VARCHAR(24),
    ADD COLUMN tax_exemption_cause_snapshot VARCHAR(24),
    ADD COLUMN tax_regime_key_snapshot VARCHAR(2);

ALTER TABLE document_lines
    ADD CONSTRAINT ck_document_line_tax_qualification CHECK (
        tax_qualification_snapshot IS NULL
        OR tax_qualification_snapshot IN ('SUBJECT_NOT_EXEMPT', 'REVERSE_CHARGE', 'NOT_SUBJECT',
                                          'NOT_SUBJECT_LOCATION', 'EXEMPT')
    ),
    ADD CONSTRAINT ck_document_line_tax_exemption_cause CHECK (
        tax_exemption_cause_snapshot IS NULL
        OR (tax_qualification_snapshot = 'EXEMPT'
            AND tax_exemption_cause_snapshot IN ('ARTICLE_20', 'ARTICLE_21', 'ARTICLE_22',
                                                 'ARTICLES_23_AND_24', 'ARTICLE_25', 'OTHER'))
    ),
    ADD CONSTRAINT ck_document_line_tax_regime_key CHECK (
        tax_regime_key_snapshot IS NULL OR tax_regime_key_snapshot ~ '^[0-9]{2}$'
    );

COMMENT ON COLUMN document_lines.tax_qualification_snapshot IS
    'CalificacionOperacion congelada al emitir. Nula en documentos anteriores a Veri*Factu.';
