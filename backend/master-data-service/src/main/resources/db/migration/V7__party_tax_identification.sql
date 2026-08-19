-- Identificación fiscal completa del tercero.
--
-- `parties.tax_id` ya existía, pero por sí solo no basta para construir el bloque IDDestinatario
-- de un registro de facturación: hace falta saber SI ese identificador es un NIF español o un
-- documento extranjero, y de qué país procede.
--
-- Migración aditiva.

ALTER TABLE parties
    ADD COLUMN tax_identification_type VARCHAR(20),
    ADD COLUMN tax_country_code VARCHAR(2);

-- Supuesto explícito del backfill: todos los identificadores fiscales cargados hasta ahora en un
-- ERP español son NIF españoles. Si algún tercero existente es extranjero, hay que corregirlo a
-- mano desde la ficha; no hay forma de deducirlo del dato guardado.
UPDATE parties
SET tax_identification_type = 'NIF',
    tax_country_code = 'ES'
WHERE tax_id IS NOT NULL AND btrim(tax_id) <> '';

ALTER TABLE parties
    ADD CONSTRAINT ck_party_tax_identification CHECK (
        tax_id IS NULL OR btrim(tax_id) = '' OR tax_identification_type IS NOT NULL
    ),
    ADD CONSTRAINT ck_party_tax_country CHECK (
        tax_country_code IS NULL OR tax_country_code ~ '^[A-Z]{2}$'
    );

COMMENT ON COLUMN parties.tax_identification_type IS
    'NIF para residentes en España; VAT_NUMBER, PASSPORT, FOREIGN_OFFICIAL_ID, RESIDENCE_CERTIFICATE, OTHER_DOCUMENT o NOT_REGISTERED para el resto (IDOtro de Veri*Factu).';
COMMENT ON COLUMN parties.tax_country_code IS
    'País de expedición del identificador, ISO 3166-1 alfa-2.';
