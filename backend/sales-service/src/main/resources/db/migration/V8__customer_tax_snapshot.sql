-- Identificación fiscal del destinatario congelada en el documento.
--
-- El registro de facturación remitido a la AEAT lleva el NIF del destinatario tal y como estaba en
-- el momento de expedir la factura. Si el cliente cambia de NIF o se da de baja, el registro ya
-- enviado no puede cambiar con él: es el mismo criterio de snapshot que PERA ya aplica al código,
-- al nombre, al precio y al impuesto.
--
-- Migración aditiva. No se rellenan las facturas anteriores: no existe el dato y no se puede
-- inventar. Quedan en NULL y no serán remitibles a Veri*Factu, cosa que tampoco procede porque son
-- anteriores a la implantación.

ALTER TABLE commercial_documents
    ADD COLUMN customer_tax_id_snapshot VARCHAR(30),
    ADD COLUMN customer_tax_identification_type_snapshot VARCHAR(20),
    ADD COLUMN customer_tax_country_snapshot VARCHAR(2);

ALTER TABLE commercial_documents
    ADD CONSTRAINT ck_document_customer_tax_snapshot CHECK (
        customer_tax_id_snapshot IS NULL OR customer_tax_identification_type_snapshot IS NOT NULL
    ),
    ADD CONSTRAINT ck_document_customer_tax_country CHECK (
        customer_tax_country_snapshot IS NULL OR customer_tax_country_snapshot ~ '^[A-Z]{2}$'
    );

COMMENT ON COLUMN commercial_documents.customer_tax_id_snapshot IS
    'NIF o identificador fiscal del destinatario en el momento de expedir. Nulo en documentos anteriores a la implantación de Veri*Factu.';
