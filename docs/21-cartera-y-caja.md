# 21 — Cartera y caja

Fecha de corte: 1 de octubre de 2026

Segundo bloque del [mapa de migración](19-mapa-migracion-dimprocristalwin.md). Cubre lo que en el programa legado hace DimproCartera: recibos de cobro, remesas al banco y diario de caja. Es horizontal: sirve a cualquier empresa que cobre a plazos.

## Dónde vive

En `finance-service`, paquetes `receivable`, `remittance` y `cash`, con la migración `V4__collections.sql`. Las tablas existían desde V1 como esqueleto sin lógica; V4 las completa sin borrar nada.

La pantalla es `/cartera`, con las pestañas Recibos, Remesas y Caja. La ven propietario, administrador y economía, con los permisos `finance:read` y `finance:write` que ya existían.

## Recibos

Un recibo es el documento de cobro de un vencimiento de factura. Numeración `REC-AAAA-NNNNNN` por empresa y año.

- **Emisión** (`POST /api/v1/receipts/issue`). Un recibo por cada vencimiento pendiente de la factura que aún no lo tenga. En `/finanzas`, generar los vencimientos emite también sus recibos.
- **Cobro** (`POST /{id}/collect`). Fecha y forma de cobro. Un cobro en efectivo puede anotarse en una caja con sesión abierta.
- **Devolución** (`POST /{id}/return`). Con fecha y motivo. El vencimiento vuelve a pendiente.
- **Volver al cobro** (`POST /{id}/reopen`). Un recibo devuelto vuelve a pendiente, con nuevo vencimiento si se indica.
- **Anulación** (`POST /{id}/cancel`). Libera el vencimiento para emitir otro recibo.

Los recibos se cobran enteros. No hay cobros parciales de un recibo.

### Efecto en la factura

Cada cambio recalcula el estado de cobro de la factura a partir de sus recibos en vigor (pendiente, parcialmente cobrada, cobrada) y lo traslada a `sales-service`, que es quien calcula el riesgo del cliente. La llamada usa la sesión del usuario que está cobrando.

Si Ventas no responde, el cobro no se pierde: la cartera queda actualizada y la respuesta lleva `invoiceUpdated: false`, que la pantalla muestra como aviso para corregir el estado desde la factura.

## Remesas

Una remesa agrupa recibos pendientes para presentarlos juntos al banco. Numeración `REM-AAAA-NNNN`.

| Estado | Significado | Recibos |
|---|---|---|
| Borrador | Se pueden añadir y quitar recibos. | Siguen pendientes, pero reservados: no se cobran por otra vía ni entran en otra remesa. |
| Enviada | Presentada al banco. | «En el banco». |
| Abonada | El banco ha pagado. | Cobrados por domiciliación, con la fecha de abono. |
| Con devoluciones | Algún recibo vino devuelto. | El devuelto queda como tal; los demás, cobrados. |
| Anulada | Solo desde borrador o enviada. | Vuelven a pendientes. |

Todos los recibos de una remesa deben estar en la misma moneda.

**Esta versión no genera el fichero para el banco.** Lleva la gestión —qué se presenta, cuándo se envía, cuándo se abona—; el fichero SEPA de adeudos directos queda para cuando los clientes tengan mandato registrado.

## Caja

- **Cajas** (`/api/v1/cash-registers`). Código, nombre y responsable.
- **Sesiones** (`/api/v1/cash-sessions`). Una caja tiene como mucho una sesión abierta. Se abre con un fondo inicial y queda constancia de quién la abre y la cierra.
- **Diario**. Cobros de recibos en efectivo, anotados solos, y apuntes manuales de ingreso, gasto y retirada. Una salida no puede dejar la caja en negativo.
- **Arqueo y cierre**. Se anota el efectivo contado. La diferencia con lo esperado no se corrige: queda a la vista.

## Límites conocidos

1. **Sin fichero bancario** ni mandatos SEPA de cliente.
2. **Sin cobros parciales** de un recibo ni agrupación de varios recibos en un cobro.
3. **Sin pagos a proveedores.** La cartera es solo de cobros; las facturas de proveedor del [bloque 1](20-compras-inventario.md) no generan vencimientos.
4. **La caja no guarda moneda.** Los importes se muestran en euros.
5. **Sin extracto de cliente.** La tabla `financial_movements` sigue sin uso.
6. **El aviso a Ventas no se reintenta.** Si falla, hay que corregir el estado de cobro desde la factura. Además se envía antes de confirmar la transacción de finanzas: un fallo posterior, muy improbable, dejaría la factura adelantada respecto a la cartera.
7. **Los datos del cliente y de la factura del recibo los suministra quien lo emite**, como en el resto de servicios: `finance-service` no los valida contra Ventas.
8. **Las devoluciones de un cobro en efectivo no generan apunte de caja**; se anotan a mano.

## Verificación

- 17 pruebas nuevas en `finance-service` (recibos, remesas y caja).
- 7 pruebas nuevas en el frontend.
- Migración V4 aplicada sobre una base con V1 a V3 ya aplicadas, y servicio arrancado con validación de esquema.
- Recorrido real de 40 comprobaciones por el gateway con todos los servicios arrancados: factura a dos plazos, vencimientos, recibos, cobro en efectivo con apunte en caja, remesa enviada y abonada, devolución, vuelta al cobro, arqueo con descuadre, y estado de cobro de la factura actualizado en Ventas en cada paso.
