# 28 — TPV, almacén desde el móvil, etiquetas y listados por periodo

Fecha de corte: 10 de octubre de 2026

Tres aplicaciones de puesto conectadas con la gestión de PERA y un generador de listados por mes, trimestre o año. No son servicios nuevos: el TPV vive en `finance-service` (usa sus cajas y sesiones) y el almacén móvil en `operations-service` (usa su diario de existencias). Así la Cartera, el Almacén y el TPV ven siempre el mismo dinero y la misma mercancía.

## Perfiles y usuarios demo

| Perfil | Código | Permisos | Entra en |
|---|---|---|---|
| Caja (TPV) | `CASHIER` | `pos:sell`, `products:read`, `company-settings:read` | `/tpv` directamente |
| Almacén (operario) | `WAREHOUSE` | `inventory:read`, `inventory:scan`, `products:read`, `packaging:read`, `company-settings:read` | `/almacen-movil` directamente |

- Propietario y administrador tienen todo (`pos:read`, `pos:sell`, `inventory:scan`).
- Economía gana `pos:read` (seguimiento de ventas del TPV). Logística gana `inventory:scan` y `packaging:read`.
- `inventory:scan` solo permite entradas y retiradas desde el móvil; no ajustes, traspasos ni almacenes.
- Usuarios demo (contraseña de desarrollo del script): `cajero1` (Lucía Martín), `cajero2` (Andrés Gil), `almacen1` (Iván Rojas), `almacen2` (Sara Pino).
- Quien solo tiene uno de estos perfiles entra directamente a su aplicación a pantalla completa, sin barra lateral ni resumen. Los permisos nuevos requieren volver a iniciar sesión.

## Código de barras del artículo (master-data `V12`)

- `products.barcode`, opcional y único por empresa (sin distinguir mayúsculas). EAN-13 del fabricante o interno.
- En el catálogo, «Generar» crea un EAN-13 interno con prefijo `20` (reservado para uso en tienda, no choca con códigos de fabricantes).
- Una petición sin `barcode` conserva el guardado (clientes y formularios antiguos); una cadena vacía lo quita.
- `GET /api/v1/products/lookup?code=` busca lo que lee un escáner: primero como código de barras y después como código del artículo. La búsqueda general también busca por código de barras.

## TPV (`/tpv`)

### Servidor (finance-service `V7`, paquete `pos`)

- **Cajas y sesiones**: las de Cartera. Abrir el TPV es abrir una sesión con fondo; cerrar es arquear. `GET /api/v1/pos/registers`, `POST /registers/{id}/open`, `GET /sessions/{id}/summary` (informe X/Z), `POST /sessions/{id}/movements` (entradas, gastos, retiradas) y `POST /sessions/{id}/close`.
- **Venta** `POST /api/v1/pos/tickets`: el servidor consulta cada artículo en master-data con el token del cajero; el navegador no decide nombre, impuesto ni precio de catálogo. PVP = precio base + IVA, redondeado a céntimos. Un precio distinto es «precio manual» y queda marcado.
- **Importes**: precios con IVA; cada línea se redondea tras su descuento y tras el del ticket; base y cuota por tipo a partir del total (factura simplificada), así base + cuota = cobrado.
- **Cobro**: efectivo (con entregado y cambio), tarjeta (importe exacto) o mixto (tarjeta + resto en efectivo). Solo el efectivo neto entra en el diario de caja (`POS_SALE`), en la misma transacción que el ticket; la tarjeta queda en el ticket para cuadrar con el datáfono.
- **Numeración** por caja, tipo y año: `T2026-<caja>-000001` (ventas) y `D2026-<caja>-000001` (devoluciones). La sesión se bloquea mientras se cobra.
- **Reintentos**: `clientReference` lo genera el navegador; repetir el mismo cobro devuelve el ticket ya creado.
- **Anular** (`POST /tickets/{id}/void`, con motivo): solo una venta sin devoluciones y con su sesión abierta. Devuelve el efectivo (`POS_REFUND`) y la mercancía vuelve al almacén.
- **Devolución** (`POST /tickets/{id}/refund`): líneas y unidades de una venta, en efectivo o a la tarjeta, con motivo, desde cualquier caja abierta. Devuelve lo cobrado por unidad; la última devolución de una línea se lleva el resto para que no queden céntimos por redondeo. No se puede devolver más de lo vendido. Una devolución en efectivo no puede dejar la caja en negativo.
- **Consultas**: `GET /tickets` (filtros de sesión, caja, tipo, fechas y texto), `GET /tickets/{id}`, `GET /tickets/by-number/{número}` (lo que lee el escáner en el ticket impreso) y `GET /stats?from&to&timeZone` (hasta un año).
- **Permisos**: `pos:sell` para vender y consultar tickets; `pos:read` para consultar; `/stats` solo con `pos:read`.
- Zona horaria para numerar por año y agrupar por día y hora: `PERA_POS_TIME_ZONE` (por defecto `Europe/Madrid`); las pantallas mandan la del navegador.

### Existencias

- operations-service lee los tickets por `GET /internal/v1/inventory/pos-tickets` (clave interna) con la misma maquinaria que los albaranes de Ventas: un ticket es una salida (`POS_TICKET`), una devolución una entrada (`POS_RETURN`) y una anulación devuelve lo que salió. Solo cuentan los artículos inventariados; con falta de stock la salida queda pendiente en Almacén › Salidas de venta, sin bloquear la venta.
- Tras cada ticket, finance avisa a operations (`POST /internal/v1/inventory/pos-sync`) para que descuente al momento. Si el aviso se pierde, la lectura periódica (cada minuto) lo recoge. Marca de lectura propia: tabla `pos_ticket_sync` (operations `V11`); como con Ventas, cuentan los tickets posteriores a la primera lectura.

### Pantalla

- Selector de cajas con su estado (abierta desde, ventas, tickets, efectivo) y apertura con fondo.
- Venta: buscador que es también el campo del escáner (Intro busca el código exacto; `3*código` añade tres unidades), cámara, familias, rejilla de artículos con PVP, ticket con cantidades, precio manual, descuento por línea y por ticket, IVA desglosado y total grande. Los artículos por kilo piden el peso.
- Cobro con teclado numérico, importes rápidos (exacto y billetes siguientes), cambio a la vista y datos de cliente (nombre y NIF) para la factura simplificada.
- Ticket de 80 mm con logo, NIF, desglose de IVA, forma de pago, cambio y código de barras del número (para devoluciones). Opción «imprimir siempre al cobrar».
- Tickets aparcados (se guardan en el equipo), tickets del turno (reimprimir, anular, devolver), devoluciones escaneando el ticket, entradas y salidas de efectivo, cierre con recuento por billetes y monedas, descuadre visible (obliga a explicar la diferencia) e informes X y Z imprimibles.
- Atajos: F2 buscar, F4 cobrar, F6 tickets, F7 devolución, F8 caja, F9 aparcar, `+`/`−`/Supr sobre la línea elegida.
- En el móvil, pestañas «Artículos» y «Ticket» con el total siempre visible.

### Seguimiento (`/ventas-tpv`)

Para propietario, administrador y economía: hoy, ayer, semana, mes o fechas; ventas netas, ticket medio, efectivo, tarjeta, devoluciones y anulados; ventas por hora (un día) o por día; más vendidos; por caja, por cajero e IVA repercutido; turnos con su descuadre; lista de tickets con búsqueda y reimpresión.

## Almacén desde el móvil (`/almacen-movil`)

- `POST /api/v1/stock-movements/scan` (`inventory:scan` o `inventory:write`): entrada (`ADJUSTMENT_IN`) o retirada (`ADJUSTMENT_OUT`) con motivo obligatorio, en el almacén elegido o el predeterminado, origen `SCAN`. Motivos de entrada: recepción, devolución de cliente, recuento, otro. De retirada: roto, caducado, en mal estado, pérdida o robo, consumo interno, recuento, otro. Una retirada no deja existencias negativas. `clientReference` evita duplicar un escaneo reintentado.
- Todos los movimientos guardan ahora **quién** los hizo (`performed_by`, `performed_by_name`) y el **motivo** (operations `V11`). Los anteriores no tienen autor: no se inventa. Las lecturas automáticas (ventas y TPV) aparecen como «Automático».
- `GET /api/v1/stock-movements?mine=true` devuelve los del usuario de la sesión.
- Pantalla: «Añadir producto» / «Retirar producto» grandes; identificar con la cámara (barras o QR), a mano o con un lector Bluetooth emparejado; ficha con existencias por almacén; cantidad con botones grandes, cantidades rápidas y **contar por embalaje** (p. ej. 3 × caja de 24 = 72); motivo y nota; confirmación con el nuevo saldo; «Lo que he hecho hoy»; consulta de existencias; acceso a etiquetas.
- **Ubicación en el almacén** (operations `V12`): texto libre por almacén y artículo (pasillo, estante, zona, cámara: «Pasillo 3 · Estante B», «Cámara frigorífica 2»). `PUT /api/v1/stock-levels/location` (`inventory:scan` o `inventory:write`); si el artículo aún no tiene existencias en ese almacén se crea su ficha con cero unidades, para decir dónde irá antes de que llegue. Vacío la quita; no cambia existencias ni crea movimientos. El buscador de existencias encuentra también por ubicación.
- En el móvil, la ficha del artículo muestra su ubicación en el almacén elegido (y en los demás) y el operario puede indicarla o cambiarla. En Almacén (escritorio), columna «Ubicación» editable con sugerencias de las ya usadas.
- **Copiar código**: botón verde al final de cada fila del catálogo y de las existencias; copia el código del artículo (también por la red local, sin HTTPS).
- En Almacén (escritorio): columna «Quién» y motivo en el diario, origen TPV en Salidas de venta y pestaña **Actividad del día** (entradas, salidas, mermas por motivo, por origen y por persona).

### Cámara y lectores

- Con la cámara en directo se usa el lector del navegador (`BarcodeDetector`, Chrome para Android) y, si no hay, ZXing, que se descarga solo cuando hace falta. Lee EAN-13/8, UPC, Code 128/39/93, ITF, QR y Data Matrix. Vibración y pitido al leer; linterna si el móvil la tiene.
- **El móvil solo deja usar la cámara en directo desde una página segura (HTTPS o localhost).** Por la red local (`http://192.168.x.x:5173`) la aplicación pide una foto del código y la lee igual. Para probar la cámara en directo desde el móvil: `PERA_DEV_HTTPS=1` antes de arrancar Vite sirve HTTPS con un certificado propio (el navegador pedirá aceptarlo una vez).
- Lectores USB o Bluetooth: funcionan como teclado. El TPV y el almacén los detectan aunque el foco no esté en un campo (teclas muy seguidas terminadas en Intro).

## Etiquetas (`/etiquetas`)

- Para propietario, administrador, logística, catálogo y operarios de almacén. Desde el catálogo hay un botón por artículo.
- Artículos por búsqueda, escáner o cámara, con número de copias.
- Diseño: código de barras (EAN-13 si el artículo tiene uno válido; si no, Code 128 con su código), QR o ambos; logo y nombre de la empresa, nombre del artículo con tamaño ajustable, referencia, PVP con IVA, número bajo las barras, borde y alineación. El diseño se recuerda en el equipo.
- Tamaños: rollos 50 × 25, 58 × 40, 62 × 29 (Brother QL), 54 × 25 (Dymo), 100 × 50 y 100 × 150 mm, y hojas A4 de 65 y 24 etiquetas.
- Se imprime desde el navegador con el tamaño de página exacto: con una impresora de etiquetas instalada basta con elegirla (márgenes «Ninguno», escala 100 %). Sin impresión directa ZPL/EPL por ahora.

## Listados por periodo (`/impresion` › Informes de registros)

- Atajos de periodo: todo, mensual, trimestral, anual o fechas a medida, en todos los informes con fechas.
- Informes nuevos: **Cobros: pagados y pendientes** (recibos por vencimiento con situación cobrado/pendiente/vencido/devuelto y días de retraso), **Deuda por cliente** (facturado, cobrado, pendiente, vencido y vencimiento más antiguo), **Ventas TPV** (tickets y devoluciones con forma de pago e IVA) y **Movimientos de almacén** (con origen, motivo y autor).
- **Arreglo**: el listado de documentos de Ventas daba error 500 al filtrar por fechas sin cliente (parámetro nulo sin tipo para PostgreSQL). Afectaba también a «Cobros pendientes» con fechas. Ahora los filtros opcionales van con marca booleana.

## Datos de demostración

`node scripts/seed-demo-tpv-almacen.mjs` (con el entorno arrancado) crea, solo si no existen: familias de tienda, 60 artículos con código de barras interno, 12 embalajes, existencias iniciales en el almacén predeterminado, ventas del día en las dos cajas demo (con una devolución, una anulación y movimientos de efectivo) y movimientos de los operarios. También coloca cada familia en una ubicación del almacén central. Es idempotente, salvo los movimientos de ejemplo de los operarios, que se registran una vez por día para que haya «actividad de hoy». Las ventas quedan con fecha del día en que se ejecuta.

## Límites y pendientes

- **Veri*Factu**: los tickets del TPV son facturas simplificadas y **todavía no generan registro Veri*Factu** ni se remiten a la AEAT. Antes de usar el TPV con una empresa real hay que integrarlos en la cadena de registros de Ventas (o decidir otra vía).
- Los tickets no generan asiento contable ni pasan por la bandeja de Contabilidad.
- El TPV usa el precio base del catálogo, no tarifas por cliente; no hay ofertas 2×1, vales, fidelización ni cobro con datáfono integrado (se confirma a mano).
- La caja sigue sin moneda propia: euros.
- Sin apertura automática del cajón portamonedas ni impresión ESC/POS directa: el ticket se imprime con el cuadro de impresión del sistema.
- Los movimientos de stock del TPV aparecen como «Automático» (los registra la sincronización, no el cajero); el cajero está en el ticket.
- El escaneo de almacén recibe el nombre y la unidad del artículo desde el navegador, como los ajustes manuales existentes (limitación ya conocida del módulo).
