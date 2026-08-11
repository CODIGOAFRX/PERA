# Roadmap y preguntas abiertas

## Siguiente incremento recomendado

1. Pruebas de integración PostgreSQL con Testcontainers y pruebas de aislamiento multiempresa.
2. CRUD completo de direcciones, contactos, notas, tarifas y precios particulares.
3. Validación de cliente/artículo desde ventas mediante puertos y contratos internos.
4. Confirmación, cancelación y rectificación documental con permisos y auditoría.
5. Publicador de outbox y consumidor financiero idempotente.
6. Riesgo calculado a partir de facturas/vencimientos y política warn/confirm/block.
7. Objetivos mensuales configurables y refresco periódico sobre el dashboard REST ya disponible.
8. Descubrimiento de inventario y compras como capacidades horizontales; no reutilizar modelos sectoriales sin validarlos.

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
- ¿Qué capacidades necesitan de forma común comercio, distribución y servicios para entrar en el siguiente MVP?
- ¿Proyectos o ubicaciones de servicio aportan valor transversal suficiente para sustituir al modelo de obra congelado?

## Criterio para ampliar servicios

Solo se extraerá un nuevo servicio cuando haya propiedad de datos clara y una razón operativa medible: equipo independiente, escala distinta, aislamiento regulatorio o ciclo de despliegue separado. Una entidad adicional no es por sí sola un microservicio.

Una necesidad de un único sector tampoco se añadirá directamente al núcleo: primero se validará si puede expresarse como capacidad transversal y, si no, se implementará como extensión opcional.
