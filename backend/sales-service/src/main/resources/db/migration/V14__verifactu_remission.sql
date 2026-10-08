-- Remisión automática de los registros Veri*Factu a la AEAT, en pruebas y en producción.

-- Lo que la AEAT contestó a cada registro (o por qué no se pudo remitir), para enseñarlo en la factura
-- y en la pantalla de seguimiento sin tener que leer el XML de respuesta.
ALTER TABLE verifactu_records
    ADD COLUMN aeat_error_code VARCHAR(10),
    ADD COLUMN aeat_message VARCHAR(1500);

-- Registros por remitir de cada empresa, en orden de cadena.
CREATE INDEX idx_verifactu_records_remission
    ON verifactu_records(company_id, state, sequence_number)
    WHERE record_type = 'ALTA' AND state IN ('PENDING', 'SENT');

-- Estado del canal con la AEAT: fallos seguidos (para espaciar los reintentos), último error y último envío.
ALTER TABLE fiscal_connections
    ADD COLUMN failures INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error VARCHAR(1500),
    ADD COLUMN last_sent_at TIMESTAMPTZ;
