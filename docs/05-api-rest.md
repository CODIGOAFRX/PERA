# API REST inicial

Todas las rutas pasan por el gateway `http://localhost:8080`. Salvo el login y los health checks, requieren `Authorization: Bearer <token>`.

## Identidad

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/v1/auth/login` | Autenticar y seleccionar empresa |
| GET/POST | `/api/v1/companies` | Listar/crear empresas |
| PUT | `/api/v1/companies/{id}` | Actualizar empresa |
| GET/POST | `/api/v1/users` | Listar/crear usuarios de la empresa activa |
| PUT | `/api/v1/users/{id}` | Actualizar perfil, contraseña, roles y estado |
| GET | `/api/v1/roles` | Perfiles asignables y sus permisos |

Si un usuario tiene varias empresas y no envía `companyId`, el login responde `companySelectionRequired=true` con opciones, sin emitir token.

## Maestros

| Método | Ruta | Uso |
|---|---|---|
| GET/POST | `/api/v1/customers` | Buscar/crear clientes |
| GET/PUT | `/api/v1/customers/{id}` | Consultar/actualizar cliente |
| GET/POST | `/api/v1/suppliers` | Buscar/crear proveedores |
| GET/PUT | `/api/v1/suppliers/{id}` | Consultar/actualizar proveedor |
| GET/POST | `/api/v1/products` | Buscar/crear artículos |
| GET/PUT | `/api/v1/products/{id}` | Consultar/actualizar artículo |
| GET | `/api/v1/{customers|suppliers|products}/import-template` | Descargar plantilla Excel base |
| POST | `/api/v1/{customers|suppliers|products}/import` | Importar CSV/XLSX con resultado por fila |

En Excel, los importes se leen por su valor numérico real aunque la celda muestre separadores de miles. La importación de clientes también normaliza nombres de país como `España` a ISO `ES` y admite nombres comprensibles para la política de riesgo.

Los identificadores fiscales de clientes y proveedores importados se conservan como datos heredados sin validar el dígito de control ni limitar su longitud. Esa tolerancia es exclusiva de la migración masiva; los flujos fiscales siguen validando los datos cuando se utilizan.

En la importación de artículos, código y nombre son los únicos campos obligatorios. La unidad admite abreviaturas y nombres habituales en español; cualquier valor opcional no reconocido usa un valor seguro y no bloquea la fila.

Los listados paginados aceptan los parámetros estándar `page`, `size` y `sort`.

Las respuestas de clientes, proveedores y productos incluyen `createdAt`. Es un campo aditivo utilizado por el centro de informes para ordenar por fecha de alta; no altera los contratos de escritura.

`calculationMultiplier` puede aparecer todavía en las peticiones y respuestas de cliente para mantener compatibilidad. OpenAPI lo marca como obsoleto; el servidor solo lo conserva y no lo aplica a precios ni documentos. No se publica ninguna ruta de obras.

## Ventas

| Método | Ruta | Uso |
|---|---|---|
| GET/POST | `/api/v1/documents` | Filtrar/crear documentos |
| GET | `/api/v1/sales-dashboard?months=6` | Facturación mensual, acumulados diarios y ritmo comparado |
| GET | `/api/v1/documents/{id}` | Consultar documento y líneas |
| POST | `/api/v1/documents/{id}/convert` | Convertir al siguiente tipo válido |
| PATCH | `/api/v1/documents/{id}/payment-status` | Actualizar estado de cobro de factura |

Filtros: `q` (número, código o nombre de cliente), `type`, `status`, `customerId`, `fromDate`, `toDate`.

## Finanzas

| Método | Ruta | Uso |
|---|---|---|
| GET/POST | `/api/v1/payment-methods` | Consultar/crear formas de pago |
| POST | `/api/v1/due-dates/generate` | Generar vencimientos idempotentes por documento |
| GET | `/api/v1/due-dates?documentId=...` | Consultar vencimientos |
| GET | `/api/v1/accounting/accounts?q=...` | Buscar cuentas por código, nombre o alias conocido |
| GET | `/api/v1/accounting/inbox` | Sincronizar y consultar operaciones pendientes |
| GET | `/api/v1/accounting/inbox/count` | Obtener el contador de pendientes |
| POST | `/api/v1/accounting/inbox/{id}/post` | Contabilizar una operación pendiente con un asiento equilibrado |
| GET/POST | `/api/v1/accounting/entries` | Consultar el libro diario o crear un asiento manual |

Las rutas contables requieren `accounting:read` o `accounting:write`. La empresa se obtiene del JWT. Cada línea tiene importe exclusivamente en Debe o Haber y el servidor rechaza cualquier asiento cuyo total no cuadre.

## OpenAPI y salud

Cada servicio expone `/swagger-ui.html`, `/v3/api-docs` y `/actuator/health` en su puerto directo de desarrollo. El gateway no agrega todavía las especificaciones.
