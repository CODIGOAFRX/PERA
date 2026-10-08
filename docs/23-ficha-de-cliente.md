# 23 — Ficha de cliente

Fecha de corte: 8 de octubre de 2026

Primera parte del bloque 4 del [mapa de migración](19-mapa-migracion-dimprocristalwin.md): todo lo que la ficha de cliente de DimproCristalWin guarda y PERA no tenía. Antes de diseñarlo se revisaron sus tablas `clientes`, `grupo_clientes`, `vendedores`, `forma_entrega`, `motivo_inactivo` y `telefonos`. Se ha traído la estructura, no datos de clientes. Las comisiones de los comerciales quedan para la segunda parte, porque necesitan que Ventas guarde el comercial de cada documento.

## Dónde vive

En `master-data-service`, paquete `customer`, con la migración `V11__customer_file.sql`. La pantalla sigue siendo `/clientes`, ahora con tres pestañas: Clientes, Clasificación y Comerciales.

Los permisos son los de siempre: `customers:read` para consultar y `customers:write` para modificar, también para las tablas de clasificación y los comerciales. Quien solo puede leer ve la lista y las fichas sin botones de edición.

## Clasificación

Cuatro tablas que mantiene cada empresa, como en el programa anterior:

| Tabla | En la ficha |
|---|---|
| Grupos | Grupo del cliente; también filtra la lista |
| Tipos de cliente | Tipo |
| Formas de entrega | Cómo se le entrega habitualmente |
| Motivos de baja | Por qué se dio de baja; solo se guarda si el cliente está inactivo |

Los nombres no se repiten dentro de una tabla. Un valor no se borra: se desactiva. Deja de ofrecerse en fichas nuevas, pero el cliente que ya lo tiene lo conserva y se puede seguir guardando. Solo se rechaza elegir un valor desactivado como cambio.

## Comerciales

Código (fijo una vez creado, en mayúsculas), nombre, correo, teléfono, comisión por defecto en porcentaje (0–100) y estado. A un cliente se le puede asignar un comercial activo. La lista de clientes filtra por comercial.

## La ficha

Además de lo que ya había (datos fiscales, dirección fiscal, banco, riesgo), la ficha guarda:

- Móvil y cuenta contable propia. Si la cuenta se deja vacía, la contabilidad usa la general de clientes.
- Tarifa, forma de pago y código con el que el cliente nos tiene como proveedor, ahora editables desde la pantalla.

Al pulsar un cliente de la lista se abre su ficha con cuatro apartados:

- **Ficha**: resumen y botón para editar.
- **Contactos**: personas con cargo, teléfono, móvil, correo y observaciones. Solo uno puede ser el principal: marcar otro le quita la marca al anterior.
- **Direcciones de entrega**: obras, almacenes o tiendas, además de la dirección fiscal. Una sola es la habitual y una dirección dada de baja no puede serlo. Cuelgan del tercero, no del perfil de cliente, para que sirvan también a proveedores.
- **Notas**: título, texto y la marca «mostrar en documentos», que sustituye al texto para documentos del programa anterior. Saldrán impresas cuando lleguen las plantillas de documento (bloque 6). Borrar una nota la desactiva.

## Compatibilidad

- Las peticiones que no envían el bloque `classification` (la importación de clientes, integraciones antiguas) conservan la clasificación guardada.
- **Arreglo**: la pantalla no enviaba tarifa, forma de pago ni código de proveedor, y al guardar un cliente se borraban. Ahora se envían siempre. Si el usuario no puede leer tarifas o formas de pago, el desplegable queda bloqueado con el valor que tenga y se conserva.

## Límites

- Sin comisiones todavía, ni comercial en los documentos de venta.
- Sin cobradores, rutas de reparto, mandatos SEPA, recargo de equivalencia ni retenciones por cliente.
- La dirección de entrega no se elige aún en albaranes ni facturas.
- No se importan los grupos, comerciales y contactos del programa anterior.
