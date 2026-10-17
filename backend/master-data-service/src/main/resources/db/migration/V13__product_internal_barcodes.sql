-- Artículos que no tienen código de barras: se les asigna uno interno, como hace PERA desde ahora
-- al crear un artículo (InternalBarcode). EAN-13 con prefijo 20 (uso interno de la empresa), diez
-- cifras correlativas por empresa a partir del mayor ya usado y dígito de control. Así todo
-- artículo se puede escanear e imprimir en etiqueta sin pasos previos.
DO $$
DECLARE
    item RECORD;
    sequence_number BIGINT;
    body TEXT;
    total INT;
    position INT;
    candidate TEXT;
BEGIN
    FOR item IN SELECT id, company_id FROM products WHERE barcode IS NULL ORDER BY company_id, created_at, code LOOP
        SELECT COALESCE(MAX(CAST(SUBSTRING(barcode FROM 3 FOR 10) AS BIGINT)), 0) INTO sequence_number
          FROM products WHERE company_id = item.company_id AND barcode ~ '^20[0-9]{11}$';
        LOOP
            sequence_number := sequence_number + 1;
            body := '20' || LPAD(sequence_number::TEXT, 10, '0');
            total := 0;
            FOR position IN 1..12 LOOP
                total := total + SUBSTRING(body FROM position FOR 1)::INT * CASE WHEN position % 2 = 0 THEN 3 ELSE 1 END;
            END LOOP;
            candidate := body || ((10 - total % 10) % 10)::TEXT;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM products WHERE company_id = item.company_id AND LOWER(barcode) = candidate);
        END LOOP;
        UPDATE products SET barcode = candidate WHERE id = item.id;
    END LOOP;
END $$;
