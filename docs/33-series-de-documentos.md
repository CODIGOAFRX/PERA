# 33 — Series de documentos (parte 1 de 5)

Fecha de corte: 7 de noviembre de 2026

En DimproCristalWin una **serie** (ficha `series_facturas`, tabla `fseries`, 88 campos) es un tipo de documento hecho a medida, con su numeración y su comportamiento: «Factura», «Factura Contado», «Pedido al Taller», «Ped. Clien. CANCELADO»… En PERA las numeraciones solo contaban números. Ahora cada numeración es una **serie** con su comportamiento, y cada documento recuerda con qué serie se hizo (Ventas `V16`, `commercial_documents.numbering_scheme_id`).

## Qué hace cada opción (campo de Dimpro entre paréntesis)

| Opción | Qué hace en PERA |
|---|---|
| Orden en las listas (`orden`), color (`color_grid`), agrupación para listados (`id_agrupa`) | Orden de la lista de series y del selector; color junto al número en Ventas. La agrupación se guarda para los listados de la parte 4. |
| Principal | La que se propone al crear y la que se usa si no se elige ninguna. |
| **Pide el número al crear** (`pide_numdocu`) | El número se escribe a mano al crear y no cambia el contador. Sin número, no deja crear; en una serie que no lo pide, no admite número a mano. Si el número ya existe en ese tipo, lo dice. |
| **Pasa a la serie** (`id_pasar_a`) | Al convertir (presupuesto → albarán → factura), el documento nuevo va a esa serie. Si no hay, o está desactivada, a la principal del tipo siguiente. Solo admite series del tipo al que se convierte. |
| Número igual al documento anterior (`numdocu_igual`) | El documento nuevo lleva el número del anterior en vez de uno del contador. |
| Fecha igual al documento anterior (`ffra_igual_falb`) | El documento nuevo lleva la fecha del anterior en vez de la de hoy. |
| Pasa las notas al documento siguiente (`pasa_nota_docu`) | Si está desmarcado, las notas no pasan al convertir. Por defecto sí, como hasta ahora. |
| **Avisar si no se ha pasado en N días** (`dias_aviso`) | En Ventas, el presupuesto o albarán confirmado que lleva más de N días sin pasarse a otro documento sale con «Sin pasar hace N días». |
| **Texto al pie** (`texto_documento`) | Se imprime al pie del PDF, por ejemplo la cuenta bancaria. |
| **Copias además del original** (`copias_doc`) | El PDF lleva el original y las copias, marcados «ORIGINAL» y «COPIA 1», «COPIA 2»… |
| **Documento valorado** (`documento_valorado`) | Sin valorar, el PDF sale sin precios, descuentos, importes ni totales (el albarán para el transportista). Una factura siempre sale valorada. |
| **Genera riesgo en el cliente** (`es_riesgo`) | Si está desmarcado, sus documentos no se comprueban contra el riesgo del cliente ni cuentan en lo que debe. |
| **Documento interno** (`interno`) | No se envía al cliente por correo; el botón responde «Este documento es de una serie interna». |
| **Reposición** (`es_reposicion`) | El documento lleva el material con todos los importes a cero. |

Además:
- **Albaranes y pedidos se imprimen.** Antes PERA solo imprimía facturas y presupuestos. Están en el detalle del documento («Descargar el PDF») y en Impresión e informes.
- Quien puede hacer documentos puede ver la lista de series para elegir una, aunque no pueda cambiarlas (`documents:read`).
- Las series que ya existían siguen funcionando exactamente igual: los valores por defecto son el comportamiento de antes.

## Pantallas

- **Sin detalles técnicos** (pedido por Raúl al verla):
  - El número no se configura con un patrón como `{series}-{yyyy}-{seq:5}`. Se elige un **formato** (serie, año y número; año y número seguidos «como DimproCristalWin»…) y **cuántas cifras** lleva, y debajo sale **cómo quedará** (p. ej. `AV-2026-00001` o `2600001`). El patrón solo aparece con «Personalizado».
  - En la lista sale un **ejemplo de número** en vez del patrón.
  - Las series que PERA crea sola se llaman «PRE · Presupuesto», «ALB · Albarán», «FAC · Factura»… en vez de «AUTO-QUOTE · Numeración QUOTE». Las que ya existían se renombran (Ventas `V17`) si el código está libre.

- **Configuración › Series de documentos:** la lista va ordenada, con color, tipo, «Sin valorar» o «Interno» y a qué serie pasa. La ficha tiene cuatro apartados: serie y numeración, al pasar a otro documento, impresión y comportamiento.
- **Ventas › Nuevo documento:** selector de **Serie** (propone la principal) y, si la serie lo pide, **Número del documento**. En la lista, el color de la serie y el aviso de «sin pasar».

## Probado

- **Pruebas automáticas:**
  - Convertir va a la serie que dice la de origen, con su número, su fecha y sin notas.
  - Pide número: lo toma de la petición, nunca del contador, y sin número no deja crear.
  - Reposición: importes a cero.
  - Todas las del servidor y las 149 del frontend pasan.
- **Prueba real, con PERA arrancado y series de ejemplo** (AVSV albarán sin valorar, FCPRU factura, AREP reposición, PMAN número a mano, PINT presupuesto interno):
  - El albarán AV-2026-00001 se imprime sin precios, con «ORIGINAL» y «COPIA 1» y el texto al pie.
  - Pasado a factura, sale FP-2026-00001, de la serie a la que pasa, sin notas y con 30,25 €.
  - Reposición: total 0.
  - El presupuesto interno no se envía por correo.
  - Un presupuesto sin número en una serie que lo pide da 422, y con número se crea.
  - «Pasa a» en una serie de factura se rechaza.
  - Pantallas vistas en el navegador.

## Siguientes partes

2. Contado: cobro automático en caja, recibo de caja y entregas a cuenta (`es_contado`, `cobro_automatico`, `recibo_caja`, `apuntes_a_cta`, `importe_a_cta`, `seleccion_caja`).
3. Permisos de cada usuario por serie (`fseries_usuarios`: crear, modificar, borrar, borrar líneas, pasar e imprimir).
4. Formatos de impresión por serie, agrupaciones de listados, PDF de archivo y número máximo de impresiones.
5. Las de vidrio (fabricable, etiquetas de producción, caballetes, autoconsumo de planchas, dividir documentos…) en la extensión de vidrio.

El envío por correo de albaranes no existe todavía en PERA (solo facturas y presupuestos); la opción «interno» se aplica donde hay envío.
