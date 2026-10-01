# 20 — Compras e inventario básico

Fecha de corte: 1 de octubre de 2026

Primer bloque del [mapa de migración](19-mapa-migracion-dimprocristalwin.md). Cubre el ciclo de compra (pedido, albarán de entrada, factura de proveedor) y un inventario por almacén con diario de movimientos. Es una capacidad horizontal: no contiene reglas de ningún sector.

## Dónde vive

Ambos módulos están en `operations-service`, en los paquetes `purchasing` e `inventory`, con las migraciones `V7__inventory.sql` y `V8__purchasing.sql`. Comparten servicio y base de datos a propósito: confirmar un albarán de entrada y dar de alta sus existencias ocurre en una sola transacción, sin eventos ni llamadas entre servicios.

Las pantallas son `/compras` y `/almacen`. Las ven los perfiles propietario, administrador y logística.

## Permisos

| Permiso | Alcance |
|---|---|
| `purchases:read` / `purchases:write` | Consultar y gestionar documentos de compra. |
| `inventory:read` / `inventory:write` | Consultar existencias y diario; gestionar almacenes, ajustes y traspasos. |

Los cuatro se asignan a propietario, administrador y logística al arrancar `identity-service`. Un usuario que ya tuviera sesión abierta debe volver a entrar para recibirlos en su token.

## Inventario

- **Almacenes** (`/api/v1/warehouses`). Código inmutable, un único almacén predeterminado por empresa y siempre activo. Un almacén con movimientos no se borra: se desactiva.
- **Existencias** (`GET /api/v1/stock-levels`). Una fila por almacén y producto. Nunca se editan directamente y nunca quedan en negativo.
- **Diario de almacén** (`GET /api/v1/stock-movements`). Solo se añaden apuntes; una corrección es otro apunte de signo contrario. Cada apunte guarda la existencia resultante, el coste unitario cuando procede de una compra y el documento de origen.
- **Ajustes** (`POST /api/v1/stock-movements/adjustments`). Entrada o salida manual con motivo obligatorio.
- **Traspasos** (`POST /api/v1/stock-movements/transfers`). Salida de un almacén y entrada en otro, enlazadas por el mismo identificador.

La fecha de un apunte es siempre la del servidor. No se admiten apuntes con fecha pasada porque dejarían incoherente el saldo de los posteriores.

Las existencias de un producto se llevan en una sola unidad de medida por almacén. Un movimiento en otra unidad se rechaza.

## Compras

`/api/v1/purchase-documents` gestiona tres tipos con numeración propia por empresa, tipo y año:

| Tipo | Serie | Mueve existencias |
|---|---|---|
| `PURCHASE_ORDER` | `PC-AAAA-NNNNNN` | Nunca. |
| `GOODS_RECEIPT` | `AC-AAAA-NNNNNN` | Al confirmar. Exige almacén. |
| `SUPPLIER_INVOICE` | `FC-AAAA-NNNNNN` | Solo si no procede de un albarán y tiene almacén. |

Estados: borrador, confirmado, convertido y anulado, igual que en ventas.

- Un documento solo se edita o elimina en borrador.
- `POST /{id}/confirm` confirma. `POST /{id}/convert` crea el documento siguiente en borrador y marca el origen como convertido: un pedido pasa a albarán o directamente a factura; un albarán pasa a factura.
- `POST /{id}/cancel` anula un borrador o un confirmado. Si el documento había dado entrada en almacén, la deshace con apuntes de anulación; si no quedan existencias suficientes para deshacerla, la anulación se rechaza.
- Al anular o eliminar un documento que procedía de otro, el origen vuelve a confirmado y puede convertirse de nuevo.
- Una factura de proveedor necesita el número de factura del proveedor para confirmarse, y un mismo proveedor no puede tener dos facturas en vigor con el mismo número.
- Las líneas sin producto (portes, servicios) no mueven existencias.

### Importes

La base de cada línea se redondea al céntimo. La cuota se calcula sobre la suma de bases de cada tipo impositivo, no línea a línea, para que el total coincida con el de la factura recibida. El coste unitario que se anota en el diario es la base de la línea, con su descuento, entre la cantidad.

## Límites conocidos

1. **Las ventas no descuentan existencias.** Qué documento de venta rebaja el stock (albarán, factura o expedición) sigue siendo una decisión de producto abierta. Hasta entonces las salidas se registran con ajustes.
2. **Sin valoración de inventario.** El diario guarda el coste de cada entrada, pero no hay precio medio ni FIFO ni informe de valor de almacén.
3. **Sin pagos a proveedores.** La factura de proveedor no genera vencimientos ni llega a finanzas o contabilidad.
4. **Conversiones completas.** No hay recepciones parciales ni agrupación de varios albaranes en una factura.
5. **Snapshots suministrados por el cliente de la API.** Como en expediciones, `operations-service` no valida proveedor ni producto contra maestros; guarda el código y el nombre que recibe.
6. **Sin stock mínimo, lotes, números de serie ni ubicaciones dentro del almacén.**
7. **Impuestos.** La línea lleva un porcentaje; no hay recargo de equivalencia, retenciones ni IGIC diferenciado.

## Verificación

- 27 pruebas nuevas en `operations-service` (importes, existencias, almacenes, ciclo documental y permisos).
- 11 pruebas nuevas en el frontend (cálculo de totales, compras, almacén y rutas).
- Migraciones V1 a V8 aplicadas sobre PostgreSQL 18 vacío y servicio arrancado con validación de esquema.
- Recorrido HTTP de 44 comprobaciones contra el servicio real: pedido, albarán, factura, duplicados, ajustes, traspasos, anulaciones con reversión, filtros, permisos y aislamiento entre dos empresas.
