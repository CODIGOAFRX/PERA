# Conexiones, correo comercial y datos de terceros

La ruta `/conexiones` permite al propietario o administrador configurar una conexión SMTP por empresa: servidor, puerto, STARTTLS o TLS, usuario, contraseña y remitente autorizado. El asistente guía la preparación, guardado, comprobación de autenticación y activación. La comprobación no envía mensajes ni garantiza que el proveedor acepte un remitente concreto.

Las credenciales se cifran con AES-256-GCM, nonce aleatorio y empresa como dato autenticado. Nunca se devuelven al navegador. `PERA_MAIL_ENCRYPTION_KEY` contiene 32 bytes aleatorios codificados en Base64; en Windows el arranque genera una clave persistente en `.runtime/mail-encryption.key`. En despliegues se suministra mediante gestor de secretos y se conserva con las copias de seguridad. Cambiarla sin migrar las credenciales impide descifrarlas. No usar la clave JWT como clave de correo.

## Gmail y Google Workspace

El selector Gmail configura `smtp.gmail.com`, puerto 587 y STARTTLS. Solicita dirección completa, nombre del remitente y contraseña de aplicación de Google (16 caracteres; elimina espacios al pegar). Incluye enlaces para activar la verificación en dos pasos y generar esa contraseña. No implementa OAuth ni el botón «Iniciar sesión con Google». Algunas cuentas administradas o con Protección Avanzada no permiten contraseñas de aplicación; el asistente enlaza la ayuda oficial. Las conexiones Gmail existentes se reconocen por su servidor y conservan su configuración.

Tener un dominio web no determina el servidor SMTP: los datos los facilita el proveedor que aloja el correo. Un buzón de dominio propio alojado en Google Workspace puede usar la opción Gmail si la política de su organización lo permite.

## Envío

- Presupuestos: botón «Enviar por correo» en el detalle. Prepara y encola un PDF con título de presupuesto; un borrador pasa a enviado al encolarlo. El estado comercial no confirma la entrega SMTP: el bloque de correo presenta su propio estado.
- Facturas ordinarias y rectificativas: envío manual desde el detalle y automático al expedir o convertir, únicamente si la empresa activa ambas opciones. No se envían borradores ni se recorren facturas antiguas al activar la opción.
- El adjunto es el mismo PDF imprimible; si existe un registro Veri*Factu, incluye su QR. Enviar correo al cliente no remite registros a la AEAT ni altera su estado.
- Cola persistente local al servicio de ventas, procesada después de confirmar la transacción, con bloqueo de trabajo y unicidad por empresa/documento. Un fallo de preparación queda visible. Una entrega incierta exige comprobar el SMTP y confirmar expresamente el reintento; no hay reintentos ciegos que puedan duplicar correos.
- `SENT` significa aceptación por el servidor SMTP, no lectura ni entrega garantizada al buzón. Los adjuntos aceptados se eliminan de la cola; permanece el estado. Desconectar elimina la credencial y pausa los trabajos pendientes.
- Los textos de la interfaz están en ES/EN; el correo y el PDF comercial están en español.

API por gateway: `GET/PUT/DELETE /api/v1/connections/email`, `POST /api/v1/connections/email/test`; `GET/POST /api/v1/quotes/{id}/email` y `/api/v1/documents/{id}/email`; `POST .../email/retry` con `confirmedNotDelivered: true`. Conexiones exige OWNER/ADMIN; documentos y presupuestos conservan permisos de lectura/escritura y filtro por empresa.

## Dirección y banco

Clientes, proveedores y transportistas admiten `details` opcional: `addressLine1`, `city`, `region`, `postalCode`, `countryCode`, `iban`, `bankAccountHolder`. Se normaliza IBAN sin espacios y en mayúsculas, con control módulo 97 y longitud española. Omitir `details` conserva los datos al editar; enviarlo con campos vacíos los limpia. La dirección y el correo se congelan al crear documentos y se conservan al convertir. Los documentos históricos sin dirección mantienen el aviso de domicilio pendiente.

Migraciones aditivas: master-data V10, operations V6, sales V12. Los modelos heredados permanecen intactos. No se integra cobro bancario ni domiciliación por almacenar el IBAN.

## Verificación

Reactor Maven `clean verify`, Vitest y build Vite; Compose validado sintácticamente y auditoría npm de producción sin vulnerabilidades. Prueba real con PostgreSQL y SMTP TLS local en una empresa QA aislada: autenticación, cifrado, aislamiento, permisos, persistencia de los tres terceros, rechazo de IBAN inválido, presupuesto, facturas ordinarias y Veri*Factu, PDFs y bloqueo de duplicados. No se enviaron correos externos. Revisión visual de escritorio y móvil a 390 px.

## Centro de impresión

`/impresion` abre para propietario, administrador y economía la opción de documentos completos: facturas, rectificativas y presupuestos, buscables por número o cliente y paginados. La vista previa, apertura para imprimir y descarga usan el PDF binario generado por Java en `/api/v1/documents/{id}/invoice.pdf`, con sus líneas, datos de las partes, impuestos, totales y QR cuando existe. Los borradores de factura deben expedirse antes de imprimir desde este centro. Los informes configurables de registros siguen disponibles en su opción propia; imprimir un listado no sustituye imprimir una factura. No cambia los datos históricos ni completa domicilios ausentes.

Los textos fuente se conservan en UTF-8. Una prueba del frontend detecta secuencias de codificación dañada; el PDF normaliza las tildes Unicode combinadas antes de dibujarlas para conservar nombres y descripciones en español.
