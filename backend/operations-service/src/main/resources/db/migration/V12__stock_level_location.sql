-- Ubicación del artículo dentro de cada almacén: pasillo, estantería, zona o cámara
-- («Pasillo 3 · Estante B», «Cámara frigorífica 2»). Texto libre de cada empresa para que
-- quien entra nuevo en el almacén sepa dónde buscar. Es por almacén: el mismo artículo puede
-- estar en sitios distintos en cada uno.
ALTER TABLE stock_levels ADD COLUMN location VARCHAR(120);

CREATE INDEX idx_stock_levels_location ON stock_levels(company_id, warehouse_id, lower(location))
    WHERE location IS NOT NULL;
