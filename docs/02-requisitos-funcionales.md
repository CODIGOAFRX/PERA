# Requisitos funcionales

## Confirmados para el esqueleto

### Seguridad y multiempresa

- Login formal con contraseña cifrada.
- Un usuario puede pertenecer a una o varias empresas.
- Roles y permisos se asignan por pertenencia usuario–empresa.
- El JWT transporta la empresa activa; los servicios no aceptan `company_id` del cliente como fuente de autoridad.

### Clientes y proveedores

- Datos fiscales y comerciales separados conceptualmente.
- Búsqueda por código, razón social, nombre comercial o NIF/CIF.
- Direcciones y contactos como colecciones independientes.
- Clientes con tarifa, forma de pago, multiplicador de cálculo, riesgo, notas internas, tarifas especiales, precios particulares y obras.
- Proveedores con transportista, ruta y forma de pago.

### Artículos y precios

- Artículos flexibles por tipo, familia, categoría y unidad de medida.
- Precio base, IVA, tarifas y vigencias.
- Excepciones de precio por cliente y artículo.

### Documentos comerciales

- Tipos iniciales: presupuesto, albarán, factura y parte de trabajo.
- Líneas con snapshots de código/descripción/precio/IVA para preservar el histórico.
- Cálculo monetario con `BigDecimal` y redondeo explícito.
- Estados borrador, confirmado, convertido y cancelado.
- Conversión controlada presupuesto → albarán → factura, manteniendo trazabilidad.
- Filtros por tipo, estado, cliente y fechas.

### Finanzas

- Formas de pago con uno o varios porcentajes y días de vencimiento.
- Generación determinista de vencimientos; el último absorbe el resto de redondeo.
- Modelos iniciales para recibos, remesas, movimientos financieros, riesgo y caja.

## Requisitos no funcionales

- Java 21, Spring Boot, PostgreSQL y API REST versionada.
- Migraciones Flyway; ningún servicio modifica el esquema automáticamente.
- Health checks, métricas, OpenAPI y errores HTTP con `ProblemDetail`.
- Secretos y credenciales configurables por entorno.
- Cada servicio es propietario de sus tablas y no consulta directamente la base de otro.

## Pendientes de validar con Raúl y usuarios

- Significado exacto de porcentajes en tarifas especiales y “múltiplos de cálculo”.
- Cuándo se descuenta stock y si el albarán o la factura es el hecho logístico.
- Reglas de edición/anulación de facturas emitidas.
- Series, numeración por ejercicio y requisitos fiscales definitivos.
- Cálculo de riesgo: documentos incluidos, pagos en tránsito y política de bloqueo.
- Días naturales/hábiles, festivos y ajuste de vencimientos.
- Campos exactos de obras, cartas, vacaciones y partes de trabajo.
- Operativa real de caja frente a Cartera Dimpro.
