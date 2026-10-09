# 25 — Comisiones de los comerciales

Fecha de corte: 9 de octubre de 2026

Segunda parte del bloque 4 del [mapa de migración](19-mapa-migracion-dimprocristalwin.md), sacada del formulario «Comisiones vendedores» de DimproCristalWin y de sus tablas `comisiones_vendedor` y `comisiones_vendedor_documento`.

## Cómo lo hace DimproCristalWin

- Cada albarán y factura lleva su vendedor (`albaran.id_vend`), tomado del cliente.
- Cada vendedor tiene una tabla de comisiones: porcentaje por tramo de importe de la línea, opcionalmente limitado a un artículo, a un grupo o subgrupo de artículos o a una tarifa. Se aplica la regla más concreta.
- «Recalcular» recorre las facturas del vendedor entre dos fechas, calcula la comisión línea a línea y guarda por factura el importe, la comisión y si está cobrada.
- Las comisiones se liquidan: quedan como pagadas con su fecha y ya no se recalculan.

## En PERA

Todo vive en `sales-service`, paquete `commission`, con la migración `V15`. El comercial de cada documento está descrito en el [doc 23](23-ficha-de-cliente.md).

**Reglas** (`/api/v1/commission-rules`): por comercial, un porcentaje (0–100) y, opcionalmente, un artículo o un grupo de artículos (no los dos) y un tramo de importe. Una regla no se pasa a otro comercial; se desactiva en lugar de borrarse.

**Qué regla gana**, línea a línea:

1. Las del artículo de la línea, después las de su grupo y después las generales.
2. Entre las del mismo nivel, la de tramo más estrecho; sin tramo es la más ancha.
3. El tramo se compara con el neto de la línea en valor absoluto, para que un abono caiga en el mismo tramo que la venta.
4. Si ninguna encaja, la comisión por defecto del comercial (su ficha); si no tiene, cero.

La base es el neto de la línea sin IVA, en moneda base. Las rectificativas restan.

**Cálculo** (`POST /api/v1/commissions/calculate`): facturas y rectificativas expedidas del periodo (como mucho un año), de un comercial o de todos. Guarda por factura la base, la comisión y el detalle por línea con la regla aplicada. Las liquidadas no se tocan.

**Consulta**: lista con filtros por comercial, fechas, estado (pendiente o liquidada) y cobrada (factura cobrada en Cartera), totales y detalle por línea.

**Liquidación**: se liquidan las seleccionadas con fecha y nota; ya no se recalculan. Se puede deshacer una liquidación hecha por error. El comercial de una factura con la comisión liquidada no se cambia sin deshacerla antes.

**Pantalla** `/comisiones` (Ventas › Comisiones):

- *Comisiones*: filtros por comercial, fechas, estado y cobro; «Recalcular» el periodo; totales del filtro y de lo seleccionado; detalle por línea con el origen del porcentaje; «Liquidar» lo seleccionado con fecha y nota; «Deshacer la liquidación» desde el detalle.
- *Reglas*: las del comercial elegido, con su comisión por defecto a la vista; alta y edición de reglas por artículo, grupo o generales, con tramo y porcentaje.

**Permisos**: `commissions:read` y `commissions:write`, para propietario, administrador y economía.

## Límites

- No hay reglas por tarifa como en el programa anterior: en PERA las tarifas no clasifican artículos por precio.
- Una rectificativa por sustitución resta su importe entero; no se compensa con la factura que sustituye.
- Sin cobradores ni comisiones de cobro.
