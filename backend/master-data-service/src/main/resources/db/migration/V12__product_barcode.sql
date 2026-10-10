-- Código de barras del artículo (EAN-13, EAN-8, UPC o un código interno). Lo leen el TPV y la
-- aplicación de almacén con lector o cámara. Es opcional: un artículo sin él se identifica por su
-- código, que también se imprime en las etiquetas.
ALTER TABLE products ADD COLUMN barcode VARCHAR(64);

-- Un mismo código de barras no puede identificar dos artículos de la misma empresa.
CREATE UNIQUE INDEX uk_product_company_barcode ON products(company_id, lower(barcode)) WHERE barcode IS NOT NULL;
