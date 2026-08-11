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

Filtros iniciales: `type`, `status`, `customerId`, `fromDate`, `toDate`.

## Finanzas

| Método | Ruta | Uso |
|---|---|---|
| GET/POST | `/api/v1/payment-methods` | Consultar/crear formas de pago |
| POST | `/api/v1/due-dates/generate` | Generar vencimientos idempotentes por documento |
| GET | `/api/v1/due-dates?documentId=...` | Consultar vencimientos |

## OpenAPI y salud

Cada servicio expone `/swagger-ui.html`, `/v3/api-docs` y `/actuator/health` en su puerto directo de desarrollo. El gateway no agrega todavía las especificaciones.
