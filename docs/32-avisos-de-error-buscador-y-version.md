# 32 — Avisos de error a soporte, buscador general, versión y arreglos de uso

Fecha de corte: 7 de noviembre de 2026

Pedidos por Raúl tras probar la factura electrónica con fichero ([docs/30](30-factura-e-ini-por-empresa.md)).

## Avisos de error a soporte

Como en DimproCristalWin, donde `MisErrores` (en `funciones.prg`) guarda cada error en `ERRORES.LOG` y abre el formulario `ver_error` («Error en la Aplicación»), que lo manda a Dimpro.

- **Qué se avisa:**
  - **Fallos de pantalla**: errores de programación en el navegador, promesas sin tratar o una pantalla que se rompe al pintarse.
  - **Errores internos del servidor**: respuestas 500.
  - Los errores normales (un dato que falta, un permiso) **no** se avisan: los explica cada pantalla.
- **Qué ve el usuario:**
  - La ventana «Error en la aplicación», con el mensaje, el código de error (si viene del servidor) y si el aviso ha llegado a soporte.
  - Si se rompe una pantalla entera, en lugar de una página en blanco sale «Error en la aplicación» con «Volver a intentarlo» e «Ir al inicio».
- **Código de error del servidor:**
  - Cada error interno lleva un código (p. ej. `MH7X-A1B`) en la respuesta y en el registro del servicio, junto con el detalle técnico completo.
  - El detalle técnico nunca se manda al navegador.
  - Para ver qué pasó, se busca el código en `.runtime/logs/<servicio>.out.log`.
- **Dónde se guardan:**
  - Servicio de Actividad, tabla `error_reports` (migración `V3`).
  - Guarda empresa, usuario, pantalla, versión, código, mensaje, detalle, navegador, veces y si se mandó el correo.
  - Se consultan con `GET /api/v1/error-reports` (permiso `history:read`); la pantalla para verlos queda pendiente.
- **Cómo sale el correo**, igual que en Dimpro (`ver_error.envio_email_chilkat`):
  - **Remitente:** primero, el **correo de la empresa** de Conexiones › Correo, el mismo con el que manda las facturas; lo envía Ventas con `/internal/v1/support-mail`. Si la empresa no lo tiene activo, la **cuenta de la instalación** (`[SMTP]` de `config/pera.ini`), que hace el papel de la cuenta de Dimpro (`smtp.ionos.es`).
  - **Destinatario:** el de `[SOPORTE] correo` (en Dimpro, `errores_ejecucion_dimprowin@dimpro.es`).
  - **Asunto y cuerpo:** «Error <empresa> · PERA v0.3.0 · <pantalla>». El cuerpo empieza por la empresa y el **comentario** del usuario, y luego usuario, día y hora, versión, pantalla, código, error y detalle.
  - **Cuándo:** en la ventana el usuario escribe qué estaba haciendo y pulsa «Enviar a soporte». Si cierra sin enviarlo, sale solo a los 3 minutos sin comentario. Cada aviso se manda una sola vez.
  - Si no hay remitente posible, el aviso queda guardado con el motivo y la ventana lo dice.
  - La captura de pantalla que adjunta Dimpro no se hace todavía.
- **Repetidos:** cada error manda su correo aunque se repita (decisión de Raúl). Solo hay un tope de 60 correos por usuario y hora, para que un fallo en bucle no mande miles; el resto se guarda igual.

## Configuración de la instalación: `config/pera.ini`

Igual que el `factura_e.ini` de cada empresa, pero para toda la instalación. La plantilla es [`config/pera.ini.example`](../config/pera.ini.example). El fichero no se sube a GitHub.

| Sección | Clave | Para qué |
|---|---|---|
| `[PRODUCTOR]` | `nif`, `nombre`, `instalacion` | Quien hace PERA ante la AEAT (bloque SistemaInformatico). El nombre tiene que ser el del censo para ese NIF. |
| `[SOPORTE]` | `correo` | A quién llegan los avisos de error. Uno o varios separados por comas (p. ej. el de Raúl y el de Pedro): todos reciben el mismo aviso. |
| `[SMTP]` | `servidor`, `puerto`, `usuario`, `clave`, `remitente`, `seguridad` | Correo con el que se mandan los avisos. |

`scripts/start-local.ps1` lo lee al arrancar. Una variable de entorno ya definida manda sobre el fichero.

Motivo: la FAC-2026-000022 la rechazó la AEAT (error 1110) porque PERA arrancó sin el nombre real del productor y puso el de relleno. Con el fichero ya no depende de cómo se arranque.

## Facturas completas sin NIF del cliente

- **Nueva regla:** con Veri*Factu activo, una factura completa (F1, F3) o una rectificativa de una completa (R1 a R4) no se expide si el cliente no tiene NIF.
  - El mensaje dice qué falta y qué hacer.
  - No se crea nada ni se gasta número.
  - Antes la AEAT la rechazaba (error 1189, la FAC-2026-000021), y un registro rechazado ya no se puede reenviar.
- **Borradores y albaranes:** si el cliente no tenía NIF al crearlos y luego se completa en su ficha, la factura toma el NIF de la ficha al expedirse.
- **Rechazos explicados:** si aun así la AEAT rechaza o admite con errores un registro, debajo del texto técnico de la AEAT sale **«Por qué»** y **«Qué hacer»** en palabras del usuario. También sale en la lista de `/verifactu`.
  - Casos reconocidos: falta el NIF del cliente, NIF o nombre del cliente que no están en el censo, datos del productor del programa, cadena de registros y certificado no autorizado.

## Buscador general

- **Cómo se abre:** con **Ctrl+K** (o Cmd+K) desde cualquier pantalla, o con «Buscar…» en el menú.
- **Qué encuentra:**
  - **Pantallas** por su nombre.
  - **Opciones de configuración** por lo que escribiría el usuario: «certificado», «formas de pago», «razón social», «numeración», «usuarios»…
  - **Datos**: clientes, proveedores, artículos y documentos de venta, por código, nombre, NIF o número.
- **Permisos:** solo enseña lo que el perfil del usuario puede abrir; un cajero no ve clientes.
- **Al elegir un dato** se abre su lista ya filtrada (`/clientes?q=C0001`, `/ventas?q=FAC-2026-000022`).

## Versión

- La versión vive en `frontend/package.json` y es la que sale abajo del menú («PERA ERP v0.3») y en los avisos de error.
- Regla:
  - Un cambio pequeño sube el segundo número: 0.3, 0.4…
  - Un cambio importante sube el primero: 1.0.
  - Se cambia con `npm version 0.4.0 --no-git-tag-version` en `frontend/`.
- Esta entrega pasa de 0.2 a **0.3**.

## Arreglos de uso

- **Cerrar sesión y entrar con otro usuario:**
  - Antes, al cerrar sesión en el TPV (o en cualquier pantalla), la dirección seguía siendo `/tpv`, y el siguiente usuario entraba directamente ahí.
  - Ahora cerrar sesión, o que caduque, vuelve al inicio, y cada usuario entra en el suyo.
  - F5 sigue dejando la pantalla donde estaba, como cualquier web.
- **Fechas «Desde / Hasta»:** heredaban la letra del rótulo (10 px y negrita) y se veían diminutas. Ahora tienen la misma letra que los desplegables de al lado.
- **Conexiones:** con fichero ya no dice «el certificado se guarda cifrado», porque está en el `factura_e.ini`.

## Probado

En local, con la aplicación arrancada y los datos de la empresa demo:

- **Factura sin NIF:** a «Cliente ficha 45486» responde 422 con el mensaje, y el número de documentos no cambia (35 antes y 35 después).
- **Aviso de error:**
  - Se guarda; sin correo configurado no se manda.
  - Sin correo configurado lo dice.
  - El admin lo consulta y un cajero no (403).
  - Un error de pantalla provocado a mano abre la ventana «Error en la aplicación».
- **Buscador:**
  - «merca» encuentra a Mercadona y sus dos facturas.
  - «000022» abre Ventas filtrada con esa factura, también estando ya en Ventas.
- **Cerrar sesión desde `/tpv`** deja la dirección en `/`.
- **Pruebas automáticas:** 149 del frontend, la verificación completa del backend y las nuevas de este documento.

No probado: el envío real del correo. El destinatario ya es `raulgil98@proton.me`, pero ni la empresa demo tiene correo en Conexiones ni la instalación tiene `[SMTP]`: el aviso se guarda y explica exactamente eso.
