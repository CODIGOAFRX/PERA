# PERA ERP — contexto de trabajo

> Leer esto antes de empezar nada. Se actualiza con cada entrega: qué se hizo, qué se decidió y qué falta.

## Qué es

PERA es el ERP con el que se renueva DimproCristalWin (Visual FoxPro). No es solo para empresas de vidrio: el núcleo es horizontal, para cualquier pyme, y lo propio de un sector va como extensión opcional ([auditoría de generalización](docs/08-generalizacion-erp-horizontal.md)).

- Backend: Java 21, Spring Boot 4.1, microservicios gruesos en `backend/` (identity, master-data, sales, finance, operations, activity, licensing y api-gateway). Una base PostgreSQL por servicio, migraciones Flyway.
- Frontend: React + TypeScript + Vite en `frontend/`.
- El plan de migración desde DimproCristalWin está en [docs/19-mapa-migracion-dimprocristalwin.md](docs/19-mapa-migracion-dimprocristalwin.md) y, opción por opción del menú, en [docs/26-inventario-dimprocristalwin.md](docs/26-inventario-dimprocristalwin.md), que se regenera con `tools/inventario-dimpro`.

## Normas de trabajo

### Versión de cada entrega

Todo lo que se sube a GitHub lleva una versión con la fecha del día y el nombre de quien lo sube:

```
vAAAAMMDD.Nombre        p. ej. v20261003.Raul
```

- Va al principio del título del commit (`v20261003.Raul feat: ...`) y como etiqueta de Git sobre el último commit subido.
- Si ese día ya hay una entrega con ese nombre, la siguiente añade un contador: `v20261003.Raul.2`, `v20261003.Raul.3`.
- Las etiquetas se suben con `git push origin <etiqueta>`.

### Cómo se cambia el código

- Cambios mínimos: arreglar solo lo que falla y no tocar lo que funciona.
- Seguir el estilo del código de alrededor: entidad + repositorio + servicio + controlador + DTOs + migración Flyway nueva (nunca editar una migración ya aplicada).
- Todo filtrado por empresa (`company_id` del token). Nada de claves foráneas entre bases de servicios distintos.
- Comentarios y mensajes en español, explicando el porqué.
- Un commit por bloque terminado, y documento en `docs/` con alcance, reglas y límites.

### Cómo se verifica

```bash
mvn -f backend/pom.xml verify
cd frontend && npm test && npm run build
```

Para probar de verdad: `scripts/start-local.ps1` arranca todo (PostgreSQL en el puerto 15432, gateway en 8080, frontend en 5173) y `scripts/stop-local.ps1` lo para. Usuarios de demostración: `admin`, `administracion`, `economia`, `logistica` y `catalogo`, más `cajero1`/`cajero2` (TPV) y `almacen1`/`almacen2` (almacén móvil), con la contraseña de desarrollo del script. Datos de tienda para probarlos: `node scripts/seed-demo-tpv-almacen.mjs`. Con la aplicación arrancada en Windows no se puede reempaquetar (los JAR están bloqueados): parar antes de `mvn verify`.

## Decisiones tomadas

| Fecha | Decisión |
|---|---|
| 2026-10-01 | El plan son siete bloques en este orden: compras e inventario, cartera, facturación automática y cierre, comerciales y comisiones, reclamaciones y agenda, plantillas y etiquetas, extensión de vidrio. |
| 2026-10-01 | El albarán de venta descuenta existencias; una factura sin albarán previo también. |
| 2026-10-01 | Las remesas se gestionan sin fichero bancario por ahora. |
| 2026-10-03 | Cada entrega se versiona como `vAAAAMMDD.Nombre`. |
| 2026-10-03 | Logística puede leer clientes (para elegirlos en reclamaciones y citas), sin ver la pantalla de Clientes. |
| 2026-10-08 | El bloque 4 se parte en dos: primero la ficha de cliente (clasificación, comerciales, contactos, direcciones y notas); las comisiones después, porque tocan Ventas. |
| 2026-10-08 | El texto para documentos del cliente se guarda como nota con «mostrar en documentos», no como campo aparte. |
| 2026-10-09 | Traer a PERA todo lo que hace DimproCristalWin, leyendo su código fuente: se copia el comportamiento (opciones, campos, reglas y cálculos, comprobados con datos reales), no la estructura de su código. Base: el inventario del doc 26. |
| 2026-10-09 | `main` recibe todo lo de `migracion-dimprocristalwin` (Raúl lo pidió para que el compañero lo clone directamente). Se sigue trabajando en la rama y se pasa a `main` al terminar cada entrega. |
| 2026-10-08 | Veri*Factu se presenta de forma propia (registro, huella y cadena de PERA, remitidos con el certificado de la empresa), no a través de Verifacti o B2BRouter como en DimproCristalWin. Se completa lo que hace Dimpro: envío automático, estado por factura, seguimiento. |

## Pendiente de decidir

- **TPV y Veri*Factu:** los tickets del TPV son facturas simplificadas y aún no generan registro Veri*Factu ni asiento. Decidir si se integran en la cadena de Ventas antes de usar el TPV con una empresa real.
- **Veri*Factu en producción:** antes de la primera empresa real, declaración responsable de PERA como sistema informático de facturación y revisión de un asesor fiscal. Siguiente fase técnica: subsanación y anulación, y validación del NIF del cliente en la AEAT.

- **Facturación automática (bloque 3):** si una factura puede agrupar varios albaranes. Hoy la conversión es uno a uno y las facturas van encadenadas en Veri*Factu.
- **Cierre de ejercicio (bloque 3):** qué hace hoy el cierre en DimproCristalWin (solo numeraciones o también asientos).
- **Demo pública:** dónde se alojan los servicios de la demo del portfolio y cómo se restauran sus datos cada noche ([docs/29](docs/29-demo-publica-portfolio.md)). Hasta entonces la página del proyecto se publica y la demo dice que no está disponible.
- **Secretos por defecto:** los servicios arrancan con claves públicas si falta la variable de entorno (JWT, clave interna, contraseña de los usuarios demo). Falta decidir si deben negarse a arrancar.

## Registro de entregas

### v20261017.Pedro — demo pública para el portfolio

- **Demo sin login** ([docs/29](docs/29-demo-publica-portfolio.md)): empresa propia `VISITA` con el usuario `visitante`, `POST /api/v1/auth/demo` (solo con `PERA_DEMO_ENABLED=true`, `scripts/start-local.ps1 -Demo`) y token marcado `demo`; la pasarela bloquea al visitante correos, conexiones, certificados, envíos a la AEAT, usuarios y datos de la empresa.
- **Frontend en modo demo** (`npm run build:demo`): se sirve bajo `/proyecto/pera-erp/app/`, API en `/proyecto/pera-erp/api/`, franja de demo y «Salir de la demo». `scripts/build-portfolio-demo.mjs` lo copia al portfolio.
- **Datos inventados**: `scripts/seed-demo-portfolio.mjs`, una distribuidora con cinco meses de actividad (facturas con Veri*Factu en pruebas, cobros, compras, TPV, almacén, agenda y reclamaciones).
- **Arreglos:** la barra lateral arrancaba oculta en la primera visita (`Number(null)` es 0); `start-local.ps1` podía elegir la carpeta `javapath` de Oracle como JDK y Maven fallaba.
- La landing del proyecto vive en el repositorio del portfolio (`proyecto/pera-erp`).

### v20261011.Raul — razón social y NIF de la empresa

- **Arreglo:** los tickets del TPV y las facturas imprimen la razón social y el NIF de la empresa, pero no había forma de cambiarlos: se quedaban con los de relleno de la empresa demo («PERA ERP Demo», `B00000000`). Ahora se editan en Configuración › Empresa; el NIF español se comprueba con su dígito de control. Las peticiones antiguas que no los envían los conservan.
- Configuración › Veri*Factu propone la razón social y el NIF de la empresa mientras no está configurado, y avisa si el NIF del obligado no coincide con el de la empresa.
- El comprobador de NIF (`SpanishTaxIdValidator`) pasa de master-data a `platform-shared`, para usarlo en clientes y en la empresa sin duplicarlo.

### v20261011.Pedro — ubicación en el almacén y copiar código

- **Ubicación** de cada artículo en cada almacén (pasillo, estante, cámara…), editable desde Almacén y desde el móvil del operario, y buscable. Migración `V12` de operations ([docs/28](docs/28-tpv-almacen-movil-etiquetas-listados.md)). El script de demo coloca los artículos de tienda.
- Botón verde **«Copiar código»** al final de cada fila del catálogo y de las existencias. Pedido por Pedro.

### v20261010.Raul — datos demo del TPV en una instalación nueva

- `scripts/seed-demo-tpv-almacen.mjs` necesitaba las cajas `DEMO-CAJA-1` y `DEMO-CAJA-2`, que nada creaba: en un repositorio recién clonado no vendía ningún ticket. Ahora las crea si faltan.

### v20261010.Pedro.2 — TPV, almacén móvil, etiquetas y listados por periodo

- **TPV** `/tpv` sobre las cajas de Cartera ([docs/28](docs/28-tpv-almacen-movil-etiquetas-listados.md)): selector de caja, venta con escáner/cámara, cobro en efectivo, tarjeta o mixto con cambio, ticket de 80 mm, aparcados, anulación, devoluciones, entradas y salidas de efectivo, arqueo por billetes e informes X/Z. Finance `V7`. Las ventas descuentan existencias con la sincronización de salidas de Operations (aviso inmediato y lectura periódica).
- **Ventas TPV** `/ventas-tpv`: seguimiento por día, hora, caja, cajero, artículo, IVA y turnos.
- **Almacén móvil** `/almacen-movil`: añadir/retirar escaneando (barras o QR, cámara o lector Bluetooth) o a mano, por unidades o embalajes, con motivo. Los movimientos guardan autor y motivo; pestaña «Actividad del día» en Almacén. Operations `V11`.
- **Etiquetas** `/etiquetas`: código de barras o QR, logo, tamaños de rollo y A4. Código de barras en el artículo (master-data `V12`) y búsqueda por escáner.
- **Listados** mensuales, trimestrales y anuales; informes nuevos de cobros pagados/pendientes, deuda por cliente, ventas TPV y movimientos de almacén.
- Perfiles nuevos `CASHIER` y `WAREHOUSE` (entran directo a su aplicación) y usuarios demo; `scripts/seed-demo-tpv-almacen.mjs`.
- **Arreglos:** el listado de documentos de Ventas daba 500 al filtrar por fechas; los nombres con acentos salían rotos en la interfaz (decodificación del token).

### v20261010.Pedro — auditoría duradera de remesas

- Trabajo de la sesión anterior que quedó sin subir ([docs/27](docs/27-remesas-auditoria-y-revision.md)): auditoría de remesas en la misma transacción de Finanzas con publicación a Activity y reintentos (`V5`), recuperación de estados previos sin inventar actor (`V6`), «Registrar presentación» sin transmisión bancaria e Historial con búsqueda de remesas. Incluye `docs/AGENTS-HANDOFF-2026-10-10.md`.

### v20261009.Raul.7 — inventario de DimproCristalWin

- [docs/26](docs/26-inventario-dimprocristalwin.md): las 150 opciones del menú principal del programa anterior, con lo que abre cada una, el tamaño de su código y su estado en PERA (36 hechas, 41 parciales, 29 pendientes, 20 de vidrio, 16 por revisar, 8 que no aplican), más las pantallas internas grandes. Generado por `tools/inventario-dimpro` leyendo el código fuente; al terminar cada entrega se actualiza su diccionario `STATUS` y se regenera.

### v20261009.Raul.6 — dependencia vulnerable del frontend

- La comprobación de GitHub del frontend falló al pasar a `main`: aviso nuevo de seguridad en `source-map-js` 1.2.1 (lo usan Vite y jsdom). Actualizado a 1.2.2 con `npm audit fix`; solo cambia `package-lock.json`.

### v20261009.Raul.5 — todo a `main`

- `main` avanza hasta esta rama: bloques 1, 2, 4 y 5, ficha de cliente, comisiones y Veri*Factu automático. Clonar el repositorio ya trae todo sin elegir rama.

### v20261009.Raul.4 — Veri*Factu: cabecera del seguimiento

- La tarjeta de situación de `/verifactu` (activado, entorno, remisión, último y próximo envío) se veía apelotonada y pegada al borde; ahora son cuatro datos con su título y la explicación debajo. Pedido por Raúl al verla.

### v20261009.Raul.3 — pantalla de comisiones

- Bloque 4 terminado (3 de 3): pantalla `/comisiones` con recálculo por periodo, totales, detalle por línea, liquidación y deshacer, y pestaña de reglas por comercial ([docs/25](docs/25-comisiones.md)).
- **Arreglo:** la lista y los totales de comisiones daban error 500 al filtrar sin comercial o sin cobro (un parámetro nulo sin tipo para PostgreSQL). La consulta se arma solo con los filtros que llegan, y el cálculo de todos los comerciales va en su propia consulta.

### v20261009.Raul.2 — comisiones (servidor)

- Bloque 4, segunda parte (2 de 3): reglas de comisión por comercial (artículo, grupo, tramo), cálculo por periodo sobre facturas y rectificativas con detalle por línea, liquidación y deshacer; lo liquidado no se recalcula ni deja cambiar el comercial ([docs/25](docs/25-comisiones.md)). Permisos `commissions:read/write`.

### v20261009.Raul — comercial en los documentos de venta

- Bloque 4, segunda parte (1 de 3): presupuestos, albaranes y facturas guardan su comercial, tomado de la ficha del cliente o elegido al crear; pasa al convertir y se puede cambiar después, incluso en facturas expedidas. Migración `V15` de ventas, que deja creadas las tablas de reglas y comisiones ([docs/23](docs/23-ficha-de-cliente.md)).

### v20261008.Raul.2 — Veri*Factu: remisión automática

- **Remisión automática a la AEAT** ([docs/24](docs/24-verifactu-remision-automatica.md)), fase 5 del plan de Veri*Factu. Cada factura expedida se presenta sola, en lotes de hasta 1.000 y respetando el tiempo de espera de la AEAT, en pruebas o en producción según la empresa. Si se pierde la respuesta se reconcilia con el «duplicado» de la AEAT; si la AEAT rechaza el envío entero se reintenta con esperas crecientes; un registro rechazado no se reenvía. Migración `V14` de ventas.
- **Arreglo:** una empresa en producción generaba el QR de Veri*Factu pero no remitía nada (el envío solo admitía pruebas y era manual).
- Pantalla nueva `/verifactu` con el estado de la conexión, el recuento por estado y la lista de lo que necesita atención. En la factura, estado ante la AEAT con el código y texto de la respuesta y «Enviar ahora».
- `FiscalDelivery` queda solo para B2Brouter.

### v20261008.Raul.3 — cadena Veri*Factu por emisor

- Probado con la AEAT de pruebas real y el certificado FNMT de Raúl: factura aceptada con CSV y QR que la encuentra.
- **Arreglo:** la cadena se llevaba por empresa; al cambiar el NIF del emisor, el primer registro se encadenaba con uno de otro NIF y la AEAT lo rechazaba (1123). Ahora un NIF nuevo empieza su propia cadena.
- Para probar contra la AEAT real en local: `PERA_VERIFACTU_DEVELOPER_TAX_ID` y `PERA_VERIFACTU_DEVELOPER_NAME` con datos reales (un NIF inventado lo rechaza la AEAT) y el certificado cargado en Conexiones.

### v20261008.Raul — ficha de cliente

- **Bloque 4, primera parte — Ficha de cliente** ([docs/23](docs/23-ficha-de-cliente.md)). Tablas de grupos, tipos, formas de entrega y motivos de baja; maestro de comerciales; en la ficha, comercial, móvil y cuenta contable; contactos con principal único, direcciones de entrega con habitual única y notas. La lista filtra por grupo, comercial y estado. Migración `V11` de master-data.
- **Arreglo:** al guardar un cliente desde la pantalla se borraban su tarifa, su forma de pago y su código de proveedor.
- Quien solo tiene `customers:read` ve clientes y fichas sin botones de edición.

### v20261003.Raul — subida de la rama y bloque 5

Rama `migracion-dimprocristalwin` subida a GitHub por primera vez (sin PR, sin tocar `main` ni `raul`).

- **Bloque 5 — Reclamaciones, agenda y listín** ([docs/22](docs/22-reclamaciones-y-agenda.md)). Reclamaciones con las siete tablas de clasificación del programa anterior, seguimiento con comentarios, cierre con resolución. Agenda semanal de citas con tipos, persona, cliente y contacto. Listín de contactos. Pantallas `/reclamaciones` y `/agenda`.
- Arreglos tras la prueba de Raúl: abrir una sesión de caja ya no salta al diario; la botonera de los detalles no saca barra horizontal; singulares en «1 apunte», «1 recibo».

### 2026-10-01 — limpieza y bloques 1 y 2 (subidos con la entrega anterior)

- **Limpieza:** dependencia vulnerable del frontend (`undici`) corregida; `start-local.ps1` exige JDK 21; CI de frontend; documentación desfasada y enlaces rotos corregidos.
- **Bloque 1 — Compras e inventario** ([docs/20](docs/20-compras-inventario.md)). Pedido a proveedor, albarán de entrada y factura de proveedor; almacenes, existencias, diario, ajustes y traspasos; el albarán de venta descuenta existencias. Pantallas `/compras` y `/almacen`.
- **Bloque 2 — Cartera y caja** ([docs/21](docs/21-cartera-y-caja.md)). Recibos desde los vencimientos, cobro, devolución, remesas sin fichero bancario y cajas con sesiones y arqueo. El cobro actualiza el estado de la factura en Ventas. Pantalla `/cartera`.

## Estado de los bloques

| Bloque | Estado |
|---|---|
| 1. Compras e inventario | Hecho |
| 2. Cartera y caja | Hecho |
| 3. Facturación automática y cierre | Pendiente de decisiones |
| 4. Comerciales, comisiones y grupos de cliente | Hecho (sin cobradores) |
| 5. Reclamaciones y agenda | Hecho |
| 6. Plantillas de documento y etiquetas | Etiquetas hechas; plantillas pendientes |
| 7. Extensión de vidrio | Pendiente |
