# API REST inicial

Todas las rutas pasan por el gateway `http://localhost:8080`. Salvo el login y los health checks, requieren `Authorization: Bearer <token>`.

## Identidad

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/v1/auth/login` | Autenticar y seleccionar empresa |
| GET/POST | `/api/v1/companies` | Listar/crear empresas |
| PUT | `/api/v1/companies/{id}` | Actualizar empresa |
| POST | `/api/v1/users` | Crear usuario y pertenencia |

Si un usuario tiene varias empresas y no envía `companyId`, el login responde `companySelectionRequired=true` con opciones, sin emitir token.

## Maestros

| Método | Ruta | Uso |
|---|---|---|
| GET/POST | `/api/v1/customers` | Buscar/crear clientes |
| GET | `/api/v1/customers/{id}` | Ficha de cliente |
| GET/POST | `/api/v1/suppliers` | Buscar/crear proveedores |
| GET | `/api/v1/suppliers/{id}` | Ficha de proveedor |
| GET/POST | `/api/v1/products` | Buscar/crear artículos |
| GET/PUT | `/api/v1/products/{id}` | Consultar/actualizar artículo |

Los listados paginados aceptan los parámetros estándar `page`, `size` y `sort`.

## Ventas

| Método | Ruta | Uso |
|---|---|---|
| GET/POST | `/api/v1/documents` | Filtrar/crear documentos |
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
