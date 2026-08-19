-- Clasificación fiscal de las facturas, previa a Veri*Factu.
--
-- El registro de facturación exige saber de qué tipo es cada factura (TipoFactura F1..R5) y, en
-- las rectificativas, a qué factura sustituyen y con qué criterio. Sin esto no se puede construir
-- un registro de alta válido.
--
-- Migración aditiva: no elimina ni reescribe nada existente.

ALTER TABLE commercial_documents
    ADD COLUMN invoice_kind VARCHAR(2),
    ADD COLUMN rectification_type VARCHAR(12),
    ADD COLUMN rectified_document_id UUID REFERENCES commercial_documents(id),
    ADD COLUMN rectified_number_snapshot VARCHAR(80),
    ADD COLUMN rectified_issue_date_snapshot DATE;

-- Toda factura ya emitida es, por definición, una factura completa ordinaria: no existía forma de
-- crear otra cosa antes de esta migración.
UPDATE commercial_documents
SET invoice_kind = 'F1'
WHERE document_type = 'INVOICE';

ALTER TABLE commercial_documents
    ADD CONSTRAINT ck_document_invoice_kind CHECK (
        (document_type IN ('INVOICE', 'RECTIFYING_INVOICE')
            AND (status = 'DRAFT' OR invoice_kind IS NOT NULL))
        OR
        (document_type NOT IN ('INVOICE', 'RECTIFYING_INVOICE')
            AND invoice_kind IS NULL
            AND rectification_type IS NULL
            AND rectified_document_id IS NULL)
    ),
    ADD CONSTRAINT ck_document_rectification CHECK (
        (rectification_type IS NULL
            AND rectified_document_id IS NULL
            AND rectified_number_snapshot IS NULL
            AND rectified_issue_date_snapshot IS NULL)
        OR
        (document_type = 'RECTIFYING_INVOICE'
            AND rectification_type IN ('SUBSTITUTION', 'DIFFERENCES')
            AND rectified_document_id IS NOT NULL
            AND rectified_number_snapshot IS NOT NULL
            AND rectified_issue_date_snapshot IS NOT NULL)
    );

CREATE INDEX idx_documents_rectified
    ON commercial_documents(company_id, rectified_document_id)
    WHERE rectified_document_id IS NOT NULL;

COMMENT ON COLUMN commercial_documents.invoice_kind IS
    'TipoFactura de Veri*Factu: F1, F2, F3 o R1-R5. Nulo en documentos que no son factura.';
COMMENT ON COLUMN commercial_documents.rectification_type IS
    'TipoRectificativa: SUBSTITUTION (S, por sustitución) o DIFFERENCES (I, por diferencias).';
COMMENT ON COLUMN commercial_documents.rectified_number_snapshot IS
    'Número de la factura rectificada, congelado al clasificar. El registro remitido a la AEAT no puede depender de una lectura posterior de la factura original.';
