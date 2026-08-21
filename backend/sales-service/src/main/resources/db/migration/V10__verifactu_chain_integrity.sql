-- Segunda barrera contra la bifurcación de la cadena.
--
-- El bloqueo pesimista del puntero de cadena impide que dos emisiones simultáneas de la misma
-- empresa lean la misma huella anterior. Esta migración añade una barrera independiente, en la
-- base de datos, que hace la bifurcación físicamente imposible aunque el bloqueo desapareciera:
-- dos registros no pueden encadenarse desde el mismo predecesor.
--
-- El primer registro de cada cadena queda fuera del índice porque su previous_fingerprint es NULL.
-- Ese caso ya lo cubre uk_verifactu_record_sequence: dos primeros registros pedirían ambos el
-- número de secuencia 1 y el segundo sería rechazado.

CREATE UNIQUE INDEX uk_verifactu_record_chain_link
    ON verifactu_records (company_id, previous_fingerprint)
    WHERE previous_fingerprint IS NOT NULL;

COMMENT ON INDEX uk_verifactu_record_chain_link IS
    'Impide dos registros encadenados desde la misma huella anterior. Es defensa en profundidad: la primera barrera es el bloqueo pesimista de verifactu_chain_head.';
