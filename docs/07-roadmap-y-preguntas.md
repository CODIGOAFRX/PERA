# Roadmap y preguntas abiertas

## Siguiente incremento recomendado

1. Pruebas de integración PostgreSQL con Testcontainers y pruebas de aislamiento multiempresa.
2. CRUD completo de direcciones, contactos, notas, tarifas, precios particulares y obras.
3. Validación de cliente/artículo desde ventas mediante puertos y contratos internos.
4. Confirmación, cancelación y rectificación documental con permisos y auditoría.
5. Publicador de outbox y consumidor financiero idempotente.
6. Riesgo calculado a partir de facturas/vencimientos y política warn/confirm/block.
7. Dashboard REST con métricas diarias; refresco en frontend cada 5–10 segundos.

## Preguntas de dominio prioritarias

- ¿Puede una misma entidad ser cliente y proveedor con el mismo NIF y código distinto?
- ¿La numeración es por empresa, delegación, serie, ejercicio y tipo? ¿Se permiten huecos?
- ¿Qué documento reserva o descuenta stock?
- ¿Una conversión puede ser parcial o agrupar varios documentos?
- ¿Se permite modificar una factura confirmada o siempre se rectifica?
- ¿El IVA se configura por artículo, cliente, línea, país o combinación?
- ¿Cómo se cuentan días de vencimiento y qué calendarios bancarios se aplican?
- ¿Qué compone exactamente el riesgo actual y quién puede saltarse un bloqueo?
- ¿Qué formatos de remesa y factura electrónica serán obligatorios?

## Criterio para ampliar servicios

Solo se extraerá un nuevo servicio cuando haya propiedad de datos clara y una razón operativa medible: equipo independiente, escala distinta, aislamiento regulatorio o ciclo de despliegue separado. Una entidad adicional no es por sí sola un microservicio.
