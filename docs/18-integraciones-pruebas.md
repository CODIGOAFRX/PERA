# AEAT y B2Brouter: integración de pruebas

La pantalla **Conexiones** permite guardar por empresa un certificado PKCS#12 para AEAT y una cuenta/clave sandbox B2Brouter. Los secretos se cifran con AES-GCM usando la clave persistente del servidor (`PERA_MAIL_ENCRYPTION_KEY`); las respuestas de configuración nunca devuelven las credenciales. Mantener una copia segura de la clave del servidor es necesario para recuperar estas conexiones.

## VeriFactu

Configurar la identidad fiscal en VeriFactu, con entorno TEST, y cargar un P12/PFX vigente con su contraseña en Conexiones. La comprobación del certificado es local: no acredita representación ni autorización ante AEAT. Una vez guardado, Conexiones muestra el titular, el NIF personal y el de entidad (si lo hay), el emisor y la fecha de caducidad. También avisa si caduca en 30 días o menos y si ningún NIF del certificado coincide con el emisor configurado en VeriFactu. Ese aviso es orientativo: la AEAT puede aceptar a un apoderado. La remisión efectiva usa TLS con certificado cliente y exclusivamente los endpoints de preproducción (certificado personal/representante o sello de entidad).

Desde el detalle de la factura, **Enviar a pruebas** remite el XML inmutable que ya está encadenado. Antes se valida contra el XSD oficial incluido en el programa. Se conserva la respuesta, el CSV y el estado de aceptación, aceptación con errores o rechazo. Se respeta el tiempo de espera de AEAT, con un mínimo de 60 segundos entre peticiones de la empresa.

Los envíos son manuales. Los estados IN_PROGRESS o UNKNOWN requieren reconciliación con AEAT; no hay reintento automático ni una falsa aceptación de duplicados. Esta entrega no incorpora el proceso completo de subsanación/anulación ni el envío automático. No está habilitada la remisión a producción.

## B2Brouter

Crear una cuenta con acceso API, activar sandbox y obtener una clave con prefijo `test_`. El sandbox usa `https://api.b2brouter.net/`: el prefijo de la clave determina el entorno. Introducir el identificador de cuenta, guardar y comprobar. El NIF de la cuenta debe coincidir con el emisor de PERA. Desactivar los informes fiscales automáticos en B2Brouter para evitar una segunda remisión de VeriFactu.

Configurar un contacto de pruebas en B2Brouter con el mismo NIF que el cliente de la factura. Introducir su ID en el detalle de la factura. PERA crea un borrador remoto, guarda su ID y compara base, total y moneda antes de solicitar el envío. **Actualizar estado** consulta la situación remota sin crear otra factura.

El alcance inicial son facturas ordinarias F1 emitidas, con líneas positivas de IVA ordinario. Se bloquean explícitamente rectificativas, simplificadas, exenciones y regímenes especiales pendientes de mapear. Una discrepancia de importes deja el borrador remoto sin enviar y requiere revisión. Un resultado incierto queda bloqueado para evitar duplicados; si no llegó el ID remoto, hay que localizar la factura en el proveedor por número/referencia antes de reconciliarla.

## Validación y límites

Las pruebas automatizadas usan respuestas controladas y un servidor HTTP local; no sustituyen la prueba autenticada con los proveedores. Para completar esa aceptación se necesitan el certificado autorizado de la empresa y una cuenta sandbox B2Brouter. No deben usarse facturas de producción para estas comprobaciones.

Fuentes oficiales consultadas el 23-09-2026:

- [WSDL AEAT](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SistemaFacturacion.wsdl)
- [Sandbox B2Brouter](https://docs.b2brouter.net/en/developers/testing/sandbox/)
- [Crear factura](https://developer.b2brouter.net/reference/create-invoice)
- [Enviar factura](https://developer.b2brouter.net/reference/send-invoice)

Los XSD se descargaron del directorio oficial del WSDL. La importación de XMLDSig se ha cambiado a una ruta local y se ha eliminado su DTD, sin modificar las definiciones del esquema, para validar sin acceso externo.
