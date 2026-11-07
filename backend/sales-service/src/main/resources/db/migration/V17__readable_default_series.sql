-- Las series que PERA creaba solas se llamaban «AUTO-QUOTE · Numeración QUOTE»: códigos internos en inglés que el
-- usuario veía en Configuración y al crear documentos. Pasan a «PRE · Presupuesto», «FAC · Factura», etc. Solo se
-- renombran las que siguen con el nombre automático y si el código nuevo está libre en la empresa.
UPDATE numbering_schemes s SET
    code = s.series,
    name = CASE s.document_type
        WHEN 'QUOTE' THEN 'Presupuesto'
        WHEN 'SALES_ORDER' THEN 'Pedido'
        WHEN 'DELIVERY_NOTE' THEN 'Albarán'
        WHEN 'INVOICE' THEN 'Factura'
        WHEN 'RECTIFYING_INVOICE' THEN 'Factura rectificativa'
        WHEN 'WORK_ORDER' THEN 'Parte de trabajo'
        ELSE s.name END
WHERE s.code = 'AUTO-' || s.document_type
  AND s.name = 'Numeración ' || s.document_type
  AND NOT EXISTS (SELECT 1 FROM numbering_schemes o WHERE o.company_id = s.company_id AND o.code = s.series AND o.id <> s.id);

-- Si el código estaba ocupado, al menos el nombre en español.
UPDATE numbering_schemes s SET name = CASE s.document_type
        WHEN 'QUOTE' THEN 'Presupuesto'
        WHEN 'SALES_ORDER' THEN 'Pedido'
        WHEN 'DELIVERY_NOTE' THEN 'Albarán'
        WHEN 'INVOICE' THEN 'Factura'
        WHEN 'RECTIFYING_INVOICE' THEN 'Factura rectificativa'
        WHEN 'WORK_ORDER' THEN 'Parte de trabajo'
        ELSE s.name END
WHERE s.name = 'Numeración ' || s.document_type;
