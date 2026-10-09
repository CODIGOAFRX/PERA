# PERA ERP — contexto de trabajo

> Leer esto antes de empezar nada. Se actualiza con cada entrega: qué se hizo, qué se decidió y qué falta.

## Qué es

PERA es el ERP con el que se renueva DimproCristalWin (Visual FoxPro). No es solo para empresas de vidrio: el núcleo es horizontal, para cualquier pyme, y lo propio de un sector va como extensión opcional ([auditoría de generalización](docs/08-generalizacion-erp-horizontal.md)).

- Backend: Java 21, Spring Boot 4.1, microservicios gruesos en `backend/` (identity, master-data, sales, finance, operations, activity, licensing y api-gateway). Una base PostgreSQL por servicio, migraciones Flyway.
- Frontend: React + TypeScript + Vite en `frontend/`.
- El plan de migración desde DimproCristalWin está en [docs/19-mapa-migracion-dimprocristalwin.md](docs/19-mapa-migracion-dimprocristalwin.md).

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

Para probar de verdad: `scripts/start-local.ps1` arranca todo (PostgreSQL en el puerto 15432, gateway en 8080, frontend en 5173) y `scripts/stop-local.ps1` lo para. Usuarios de demostración: `admin`, `administracion`, `economia`, `logistica` y `catalogo`, con la contraseña de desarrollo del script. Con la aplicación arrancada en Windows no se puede reempaquetar (los JAR están bloqueados): parar antes de `mvn verify`.

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
| 2026-10-08 | Veri*Factu se presenta de forma propia (registro, huella y cadena de PERA, remitidos con el certificado de la empresa), no a través de Verifacti o B2BRouter como en DimproCristalWin. Se completa lo que hace Dimpro: envío automático, estado por factura, seguimiento. |

## Pendiente de decidir

- **Veri*Factu en producción:** antes de la primera empresa real, declaración responsable de PERA como sistema informático de facturación y revisión de un asesor fiscal. Siguiente fase técnica: subsanación y anulación, y validación del NIF del cliente en la AEAT.

- **Facturación automática (bloque 3):** si una factura puede agrupar varios albaranes. Hoy la conversión es uno a uno y las facturas van encadenadas en Veri*Factu.
- **Cierre de ejercicio (bloque 3):** qué hace hoy el cierre en DimproCristalWin (solo numeraciones o también asientos).
- **Secretos por defecto:** los servicios arrancan con claves públicas si falta la variable de entorno (JWT, clave interna, contraseña de los usuarios demo). Falta decidir si deben negarse a arrancar.

## Registro de entregas

### v20261009.Raul — comercial en los documentos de venta

- Bloque 4, segunda parte (1 de 3): presupuestos, albaranes y facturas guardan su comercial, tomado de la ficha del cliente o elegido al crear; pasa al convertir y se puede cambiar después, incluso en facturas expedidas. Migración `V15` de ventas, que deja creadas las tablas de reglas y comisiones ([docs/23](docs/23-ficha-de-cliente.md)).
- Falta: reglas, cálculo y liquidación de comisiones (servicio y pantalla). El código a medias está en la rama local `comisiones-en-curso`.

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
| 4. Comerciales, comisiones y grupos de cliente | Ficha de cliente y comercial en documentos hechos; faltan las comisiones |
| 5. Reclamaciones y agenda | Hecho |
| 6. Plantillas de documento y etiquetas | Pendiente |
| 7. Extensión de vidrio | Pendiente |
