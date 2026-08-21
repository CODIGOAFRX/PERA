-- Calificación fiscal del código de impuesto.
--
-- Hasta ahora un código fiscal solo decía su porcentaje y si estaba exento. Un booleano no basta
-- para construir el desglose de un registro de facturación: la AEAT necesita saber si una línea al
-- 0 % lo está por exención, por no sujeción o por inversión del sujeto pasivo, y en las exentas
-- cuál de las seis causas aplica.
--
-- Migración aditiva.

ALTER TABLE tax_codes
    ADD COLUMN operation_qualification VARCHAR(24),
    ADD COLUMN exemption_cause VARCHAR(24),
    ADD COLUMN regime_key VARCHAR(2);

-- Backfill con el supuesto explícito y conservador: lo no exento es régimen general sujeto y no
-- exento; lo exento queda como «otras causas», que es la única que no afirma nada que no sepamos.
-- Quien tenga exportaciones o entregas intracomunitarias tendrá que precisar la causa a mano: no
-- se puede deducir del dato guardado.
UPDATE tax_codes
SET operation_qualification = CASE WHEN exempt THEN 'EXEMPT' ELSE 'SUBJECT_NOT_EXEMPT' END,
    exemption_cause = CASE WHEN exempt THEN 'OTHER' ELSE NULL END,
    regime_key = '01';

ALTER TABLE tax_codes
    ALTER COLUMN operation_qualification SET NOT NULL,
    ALTER COLUMN regime_key SET NOT NULL,
    ADD CONSTRAINT ck_tax_code_qualification CHECK (
        operation_qualification IN ('SUBJECT_NOT_EXEMPT', 'REVERSE_CHARGE', 'NOT_SUBJECT',
                                    'NOT_SUBJECT_LOCATION', 'EXEMPT')
    ),
    ADD CONSTRAINT ck_tax_code_exemption_cause CHECK (
        (operation_qualification = 'EXEMPT' AND exemption_cause IS NOT NULL)
        OR (operation_qualification <> 'EXEMPT' AND exemption_cause IS NULL)
    ),
    ADD CONSTRAINT ck_tax_code_exemption_cause_values CHECK (
        exemption_cause IS NULL OR exemption_cause IN ('ARTICLE_20', 'ARTICLE_21', 'ARTICLE_22',
                                                       'ARTICLES_23_AND_24', 'ARTICLE_25', 'OTHER')
    ),
    ADD CONSTRAINT ck_tax_code_regime_key CHECK (regime_key ~ '^[0-9]{2}$'),
    -- El booleano heredado y la calificación no pueden contradecirse.
    ADD CONSTRAINT ck_tax_code_exempt_matches_qualification CHECK (
        exempt = (operation_qualification = 'EXEMPT')
    );

COMMENT ON COLUMN tax_codes.operation_qualification IS
    'CalificacionOperacion de Veri*Factu: S1, S2, N1, N2, o EXEMPT cuando la causa viaja en exemption_cause.';
COMMENT ON COLUMN tax_codes.exemption_cause IS
    'OperacionExenta de Veri*Factu (E1-E6). Obligatoria si la operación es exenta, nula en caso contrario.';
COMMENT ON COLUMN tax_codes.regime_key IS
    'ClaveRegimen de Veri*Factu. 01 es el régimen general.';
