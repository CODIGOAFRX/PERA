-- Núcleo de Veri*Factu: configuración por empresa, cadena de registros y registros de facturación.
--
-- Tres tablas con tres responsabilidades distintas:
--   * verifactu_settings    -> cómo opera cada empresa (modalidad, entorno, datos del obligado).
--   * verifactu_chain_head  -> puntero de la cadena. Es la fila que se bloquea al encadenar.
--   * verifactu_records     -> los registros en sí, inmutables una vez creados.
--
-- Migración aditiva.

CREATE TABLE verifactu_settings (
    id                              UUID PRIMARY KEY,
    company_id                      UUID         NOT NULL,
    enabled                         BOOLEAN      NOT NULL DEFAULT FALSE,
    mode                            VARCHAR(20)  NOT NULL DEFAULT 'VERIFACTU',
    environment                     VARCHAR(20)  NOT NULL DEFAULT 'TEST',
    issuer_tax_id                   VARCHAR(20)  NOT NULL,
    issuer_legal_name               VARCHAR(180) NOT NULL,
    default_regime_key              VARCHAR(2)   NOT NULL DEFAULT '01',
    default_operation_qualification VARCHAR(2)   NOT NULL DEFAULT 'S1',
    time_zone                       VARCHAR(64)  NOT NULL DEFAULT 'Europe/Madrid',
    software_name                   VARCHAR(120) NOT NULL,
    software_id                     VARCHAR(2)   NOT NULL,
    software_version                VARCHAR(50)  NOT NULL,
    developer_tax_id                VARCHAR(20)  NOT NULL,
    created_at                      TIMESTAMPTZ  NOT NULL,
    updated_at                      TIMESTAMPTZ  NOT NULL,
    version                         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_verifactu_settings_company UNIQUE (company_id),
    CONSTRAINT ck_verifactu_mode CHECK (mode IN ('VERIFACTU', 'NO_VERIFACTU')),
    CONSTRAINT ck_verifactu_environment CHECK (environment IN ('TEST', 'PRODUCTION'))
);

-- El emisor y su razón social se copian aquí desde identity al activar el módulo. Es deliberado:
-- si la empresa cambia de denominación, los registros ya remitidos deben seguir siendo
-- reproducibles con los datos que llevaban.
COMMENT ON COLUMN verifactu_settings.issuer_tax_id IS
    'NIF del obligado a expedir, copiado de identity al activar el módulo.';

CREATE TABLE verifactu_chain_head (
    id                UUID PRIMARY KEY,
    company_id        UUID        NOT NULL,
    last_record_id    UUID,
    last_fingerprint  VARCHAR(64),
    last_generated_at TIMESTAMPTZ,
    next_sequence     BIGINT      NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_verifactu_chain_company UNIQUE (company_id),
    CONSTRAINT ck_verifactu_chain_consistency CHECK (
        (last_record_id IS NULL AND last_fingerprint IS NULL AND last_generated_at IS NULL AND next_sequence = 1)
        OR
        (last_record_id IS NOT NULL AND last_fingerprint IS NOT NULL AND last_generated_at IS NOT NULL AND next_sequence > 1)
    )
);

COMMENT ON TABLE verifactu_chain_head IS
    'Una fila por empresa. Se bloquea con SELECT ... FOR UPDATE al encadenar un registro, de modo que dos facturas simultáneas de la misma empresa no puedan tomar la misma huella anterior. Serializa las emisiones de UNA empresa, no las de todas.';

CREATE TABLE verifactu_records (
    id                   UUID PRIMARY KEY,
    company_id           UUID          NOT NULL,
    document_id          UUID          NOT NULL REFERENCES commercial_documents(id),
    record_type          VARCHAR(20)   NOT NULL,
    sequence_number      BIGINT        NOT NULL,
    issuer_tax_id        VARCHAR(20)   NOT NULL,
    invoice_number       VARCHAR(80)   NOT NULL,
    invoice_date         DATE          NOT NULL,
    invoice_kind         VARCHAR(2),
    rectification_type   VARCHAR(12),
    total_tax_amount     NUMERIC(19,4) NOT NULL,
    total_amount         NUMERIC(19,4) NOT NULL,
    previous_fingerprint VARCHAR(64),
    fingerprint          VARCHAR(64)   NOT NULL,
    generated_at         TIMESTAMPTZ   NOT NULL,
    payload_xml          TEXT,
    state                VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    aeat_csv             VARCHAR(50),
    aeat_response        TEXT,
    last_attempt_at      TIMESTAMPTZ,
    attempt_count        INTEGER       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_verifactu_record_sequence UNIQUE (company_id, sequence_number),
    CONSTRAINT uk_verifactu_record_fingerprint UNIQUE (company_id, fingerprint),
    CONSTRAINT ck_verifactu_record_type CHECK (record_type IN ('ALTA', 'ANULACION')),
    CONSTRAINT ck_verifactu_record_state CHECK (
        state IN ('PENDING', 'SENT', 'ACCEPTED', 'ACCEPTED_WITH_ERRORS', 'REJECTED')
    ),
    CONSTRAINT ck_verifactu_record_alta_kind CHECK (
        record_type <> 'ALTA' OR invoice_kind IS NOT NULL
    ),
    CONSTRAINT ck_verifactu_record_sequence_positive CHECK (sequence_number > 0)
);

CREATE INDEX idx_verifactu_records_pending
    ON verifactu_records(company_id, state, generated_at)
    WHERE state IN ('PENDING', 'SENT');

CREATE INDEX idx_verifactu_records_document
    ON verifactu_records(company_id, document_id);

COMMENT ON COLUMN verifactu_records.payload_xml IS
    'Registro serializado en el momento de expedir. No se regenera al enviar: si mañana cambia el mapeo, los registros antiguos siguen siendo exactamente los que se firmaron.';
COMMENT ON COLUMN verifactu_records.previous_fingerprint IS
    'Huella del registro anterior de la cadena de la empresa. NULL solo en el primero.';
