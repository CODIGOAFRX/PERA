# Traspaso de contexto PERA — 23 de septiembre de 2026

Este documento resume el contexto operativo de la conversación con Codex, incluidos los cambios y las limitaciones conocidas. No es una transcripción literal ni una exportación de mensajes, imágenes o herramientas. Permite continuar sin depender del acceso a aquella conversación. No contiene secretos.

## 1. Peticiones y evolución

1. El usuario partió de la conversación de ChatGPT «Verificar subida rama» (ID `6a858dab-4830-83eb-bd72-7dbae823a3e2`). Solicitó cambiar PostgreSQL de 55432 a 15432, comprobar Git antes de editar, verificar referencias y parar/arrancar el programa. Prohibió commit/push por ahora y borrar `.runtime` o hacer reset destructivo.
2. Pidió clonar el repositorio actualizado en el Escritorio y arrancarlo. Se creó `PERA-actualizado-2026-09-23`; se conservó la carpeta original `PERA`. Se trasladaron los datos de desarrollo copiando `.runtime` con PostgreSQL parado.
3. Comunicó un error al convertir un albarán a factura: «El desglose suma una cuota de 11.74 y la factura declara 11.7390». Pidió arreglarlo y revisar otros errores.
4. Pidió terminar VeriFactu y añadir B2Brouter, al menos en pruebas. Aclaró que ya había trabajo parcial de VeriFactu y absolutamente nada de B2Brouter.
5. Se implementaron dos conexiones independientes, con envíos manuales de pruebas. NO se completaron todos los procesos fiscales ni una validación real de proveedores.
6. El usuario pidió instrucciones para emitir una factura y ver el QR «en verde» en AEAT, incluso en preproducción. Se explicó que un QR generado localmente no acredita remisión ni aceptación; hace falta registrar la factura en el mismo entorno de la AEAT al que apunta el QR.
7. Inicialmente dijo no tener certificado. Después aclaró que tiene certificado digital en la aplicación oficial **Certificado Digital FNMT** para Android. Se está guiando su exportación.
8. Petición actual: guardar el contexto en AGENTS.md y preparar la continuación con Claude Code.

## 2. Estado del repositorio

- Directorio activo: `C:\Users\raulg\Desktop\PERA-actualizado-2026-09-23`.
- Original preservado: `C:\Users\raulg\Desktop\PERA`.
- Origin: `https://github.com/CODIGOAFRX/PERA.git`.
- Rama comprobada al escribir este documento: `main`.
- HEAD: `a77e2aa` (`feat: conectar correo, ampliar terceros y mejorar impresion de documentos`).
- Todos los cambios de esta conversación siguen locales y sin commit/push. Hay archivos untracked; no basta con `git diff` para encontrarlos, revisar también `git status --short`.
- El `.gitignore` existente excluye `/AGENTS.md` (línea 22 al redactar). El archivo está creado y se puede leer localmente; no se cambió esa regla. Incluirlo expresamente si se prepara una copia para otro equipo.
- Abrir esta carpeta en Claude Code. Si se clona de nuevo desde GitHub se perderá el acceso a los cambios no publicados. No recomendar un clon nuevo como forma de continuar.
- La sesión de Codex tiene archivos temporales en `C:\Users\raulg\Documents\Codex\2026-09-22\referenced-chatgpt-conversation-this-is-an\work`. Los archivos fuente necesarios se han guardado en el repositorio; esos temporales no son una dependencia de la aplicación.

## 3. Puerto y arranque

Se cambiaron las tres referencias de 55432 a 15432:

- `scripts/start-local.ps1`: `$postgresPort`.
- `backend/sales-service/src/test/java/com/peraerp/sales/verifactu/chain/VerifactuChainConcurrencyTest.java`: dos URL de PostgreSQL.

Se comprobó `git grep -n "55432"` sin resultados y `git grep -n "15432"` con las tres referencias esperadas.

Entorno: Windows/PowerShell, JDK 21 de Eclipse Adoptium, PostgreSQL 18, Maven, frontend React/TypeScript/Vite. Bases separadas por servicio (p. ej. `pera_sales`). Consultar configuración local cuando sea necesario, sin volcar secretos.

Puertos: gateway 8080; identity 8081; master-data 8082; sales 8083; finance 8084; operations 8085; activity 8086; licensing 8087; frontend 5173.

Problemas de arranque ya diagnosticados:

- Con JVM activas, Maven package/repackage no puede renombrar los JARs en Windows. Parar antes de empaquetar.
- Redirigir todo el script de arranque mediante `*> archivo` produjo bloqueo después de arrancar PostgreSQL. Ejecutarlo directamente y consultar logs individuales.
- No borrar `.runtime` como solución. Contiene PostgreSQL, logs y una clave persistente de cifrado usada también por las nuevas conexiones.

## 4. Corrección monetaria y otros arreglos

Caso comunicado: albarán `ALB-2026-000002`, dos unidades a 27,95 €, base 55,90 €, IVA 21 % = 11,7390 antes de redondear; cuota esperada 11,74 €, total 67,64 €.

Cambios:

- `document/MonetaryRounding.java` (nuevo): redondeo y distribución de residuos de céntimos por restos mayores.
- `document/CommercialDocument.java`: redondear neto e impuesto total a dos decimales antes de sumar el total; coherencia de importes en moneda base.
- `verifactu/mapping/TaxBreakdownAggregator.java`: conversión a moneda base antes de redondear y distribución coherente de impuestos agrupados.
- `verifactu/VerifactuInvoicePayloadFactory.java`: pasa el tipo de cambio al desglose.
- `print/InvoicePdfService.java`: impuestos de PDF coherentes con los totales.
- `frontend/src/lib/document.ts`: previsualización con precisión de línea y totales coherentes.
- `document/DocumentService.java`: impedir conversión de presupuesto no aceptado y rectificación de factura que todavía sea borrador.
- Vitest 4.1.10 → 4.1.11; npm audit posterior informó cero vulnerabilidades.

Pruebas nuevas/ampliadas: `MonetaryRoundingTest`, `InvoiceRoundingTest`, `InvoicePdfServiceTest`, `DocumentServiceTest`, `frontend/src/lib/document.test.ts`. Incluyen el caso del usuario, varias agrupaciones fiscales, divisas y líneas con precisión inferior al céntimo.

No se hizo una migración masiva para recalcular documentos históricos ni se reescribieron registros fiscales existentes. No confundir «arreglado para nuevos cálculos» con una corrección automática de cualquier dato histórico.

## 5. VeriFactu previo y ampliación realizada

Ya existía generación del XML RegistroAlta, encadenado, huella, QR, configuración por empresa y consulta de registros. Faltaba el transporte directo a AEAT.

Código nuevo en `backend/sales-service/src/main/java/com/peraerp/sales/integration/`:

- `AeatTestTransport.java`: certificado PKCS#12 con una clave privada y vigencia; TLS cliente; SOAP 1.1; sobre `RegFactuSistemaFacturacion`; conserva el XML fiscal interno. Analiza XML sin entidades externas y relaciona la respuesta con NIF, número y fecha. Distingue aceptación, aceptación con errores, rechazo y resultado incierto. Un duplicado no se marca automáticamente aceptado.
- `AeatSchema.java`: valida contra XSD oficiales guardados en `src/main/resources/verifactu/xsd/`. Resolución local de imports, sin red. El XMLDSig local conserva las definiciones, pero se retiró su DTD y se localizó el import.
- `TestIntegrationService.java`: credenciales cifradas por empresa; configuración, comprobación, reclamación persistida del envío antes de HTTP y almacenamiento del resultado. AEAT mínimo 60 segundos entre remisiones y respeto de TiempoEsperaEnvio.
- `TestIntegrationController.java`: rutas de conexión y envío.

AEAT solo TEST. Endpoints fijos:

- Certificado personal/representante: `https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP`.
- Sello de entidad: `https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP`.

La comprobación del certificado en Conexiones es LOCAL. No demuestra que AEAT acepte la representación o el registro. No se ha enviado ninguna factura con un certificado del usuario en esta sesión.

## 6. B2Brouter añadido

- `B2bTestTransport.java`: Java HttpClient, timeouts, clave exclusivamente `test_`, API `2026-06-26`, base `https://api.b2brouter.net/`.
- Comprueba cuenta y NIF del emisor, consulta informes fiscales automáticos y los bloquea si están activados. Comprueba NIF del contacto frente al snapshot del cliente.
- POST `accounts/{account}/invoices`: crea borrador sin emisión/envío automático; la referencia externa es el UUID del documento.
- Guarda el ID remoto antes de enviar. Verifica subtotal, total y moneda contra PERA. Precio por cantidad base para preservar tarifas/descuentos sin dividir perdiendo precisión.
- POST `invoices/send_invoice/{id}`: solicita el envío. GET `invoices/{id}`: consulta posterior del estado, comprobando referencia.
- Solo factura ordinaria F1 emitida, líneas positivas e IVA ordinario. Rechaza expresamente casos fiscales no soportados.
- El sandbox de B2Brouter simula la red; no acredita registro de una factura en AEAT. No ofrecerlo como atajo para obtener un QR encontrado por AEAT.

## 7. Persistencia, seguridad y UI

`V13__test_integrations.sql` crea:

- `fiscal_connections`: empresa/proveedor, cuenta, secreto cifrado, habilitación y próxima remisión.
- `fiscal_deliveries`: empresa/proveedor/origen únicos, estado, ID remoto, cuenta, mensaje, respuesta y fecha.

V13 ya se aplicó al PostgreSQL local durante las pruebas. Para cambios posteriores de esquema usar migración nueva; no alterar alegremente el checksum de una migración aplicada.

Se reutiliza `MailSecretCipher` (AES-GCM y clave del servidor `PERA_MAIL_ENCRYPTION_KEY`). El secreto cifrado incluye el proveedor y se comprueba al abrirlo. No copiar el contenido de `.runtime/mail-encryption.key` a documentación o a un prompt.

Permisos: `/api/v1/connections/**` OWNER/ADMIN; GET documentos `documents:read`, cambios `documents:write`; GET registros `verifactu:read`, otros métodos `verifactu:write` (regla añadida en `SecurityConfig`).

Rutas principales:

| Ruta | Uso |
| --- | --- |
| GET `/api/v1/connections/fiscal/{AEAT|B2B}` | Configuración sin secretos |
| PUT `/api/v1/connections/fiscal` | Guardar configuración |
| POST `/api/v1/connections/fiscal/{provider}/test` | Comprobar certificado local/cuenta remota |
| GET/POST `/api/v1/verifactu-records/{id}/delivery` | Consultar estado local/remitir alta |
| GET/POST `/api/v1/documents/{id}/b2b` | Consultar estado local/crear y enviar |
| POST `/api/v1/documents/{id}/b2b/refresh` | Consultar proveedor |

UI nueva: `FiscalConnections.tsx` dentro de `ConnectionsPage.tsx`; `FiscalDelivery.tsx` dentro de `VerifactuBlock.tsx` y del detalle en `SalesPage.tsx`. Accesos: `/configuracion` → pestaña Veri*Factu; `/conexiones`; `/ventas`.

## 8. Qué falta y qué no se debe sobreafirmar

- No hay credenciales AEAT cargadas ni cuenta/API key B2Brouter aportada por el usuario.
- No hay validación de extremo a extremo autenticada contra los proveedores. Los tests usan fixtures y HTTP local.
- No hay cola automática, subsanación/anulación completa, recuperación de envíos interrumpidos ni reconciliación completa de UNKNOWN/IN_PROGRESS.
- «Actualizar estado» de AEAT consulta la persistencia LOCAL, no el servicio de consulta de AEAT. No presentarlo como una consulta remota.
- Los mensajes de algunos errores de red son genéricos. Falta evaluar recuperación y diagnóstico remoto con respuestas reales.
- La implementación B2Brouter deja fuera rectificativas, simplificadas, exenciones y regímenes especiales. No afirmar soporte integral.
- Hay validación local de esquema, pero no garantiza todas las validaciones de negocio de AEAT ni cumplimiento normativo completo del producto.
- Revisar con especial cuidado: concurrencia y recuperación de claims, cambios de configuración durante envíos, aislamiento entre cuentas/empresas y snapshot del entorno fiscal para registros históricos. Son áreas pendientes de revisión, no fallos reproducidos en esta sesión.
- Las pantallas nuevas están en español; revisar coherencia con la traducción al inglés del resto de la aplicación si se amplía el alcance.
- El usuario pidió «hasta no quedar ninguno»; no es una garantía alcanzada. Se corrigieron los problemas concretos citados y se ejecutaron las suites existentes.

## 9. Verificaciones efectuadas

Última reconstrucción completa de esta sesión:

- Maven reactor de todos los servicios: BUILD SUCCESS.
- 474 tests backend: 473 aprobados, 1 omitido por entorno, 0 fallos/errores.
- 39 tests frontend aprobados (14 archivos).
- npm ci: cero vulnerabilidades reportadas; build frontend correcto. Aviso de bundle grande, no error.
- `AeatSchemaTest`: registro generado por el serializador real válido contra los XSD oficiales; rechazo al quitar campo obligatorio.
- `AeatTestTransportTest`: estados, identidad de factura, espera, duplicados inciertos, rechazo de XML con DTD/certificado inválido y sobre SOAP.
- `B2bTestTransportTest`: payload de borrador, preservación de cantidades/importes, bloqueo de diferencias y clave de producción, cabeceras en servidor HTTP local.
- `FiscalDelivery.test.tsx`: envío solo por acción del usuario y con contacto válido, bloqueo de segundo envío incierto, mensajes de validación.
- Tras arrancar: 8080..8087 UP, frontend HTTP 200.
- GET de ambas conexiones autenticado respondió configured=false, encryptionReady=true, enabled=false, environment=TEST.
- GET de conexión sin autenticación respondió 401.
- `git diff --check` pasó.

Son resultados históricos de esta sesión, no del próximo arranque. Verificar de nuevo solo lo necesario para cambios nuevos o dudas concretas.

## 10. Punto exacto con el certificado del usuario

El usuario tiene Android y dice haber obtenido el certificado mediante la app oficial «Certificado Digital FNMT».

Capturas aportadas:

- Menú general con Importar certificados, Firmar archivos, Alertas, Preguntas frecuentes, Servicio técnico; versión visible 1.2.21(187).
- Pantalla principal con Solicitar Certificado Digital, Mis Certificados Solicitados y Mis Certificados Instalados.

Última instrucción enviada: pulsar **Mis Certificados Instalados**, seleccionar el certificado y comunicar las opciones, tapando nombre y DNI en capturas. Todavía no se recibió la pantalla siguiente. No sabemos si existe una copia exportable ni si recuerda su contraseña.

Se explicó que FNMT documenta una copia `.p12` en Descargas/Download que se puede trasladar por USB. Si no existe, guiar la exportación desde las opciones REALES que muestre la aplicación; no inventar botones. No solicitar un certificado nuevo sin comprobar lo que ya tiene.

Objetivo inmediato: que el usuario cargue por sí mismo su P12/PFX y contraseña en Conexiones. Después verificar identidad autorizada y TEST, emitir una factura de pruebas con datos coherentes, revisar respuesta AEAT y probar QR contra el mismo entorno. No usar identidad demo inventada como si correspondiera a su certificado.

## 11. Fuentes para continuar

- [FNMT: exportar certificado Android a PC](https://www.sede.fnmt.gob.es/preguntas-frecuentes/obtener-certificado-con-dispositivo-movil/-/asset_publisher/d4LNbR8CDMOM/content/certificado-descargado.-exportarlo-e-un-pc)
- [AEAT: resultado del cotejo de facturas](https://sede.agenciatributaria.gob.es/Sede/ayuda/consultas-informaticas/presentacion-declaraciones-ayuda-tecnica/aplicacion-gratuita-verifactu-aeat/consulta-facturas.html)
- [AEAT: preguntas para desarrolladores](https://sede.agenciatributaria.gob.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/FAQs-Desarrolladores.pdf)
- [AEAT: WSDL de preproducción](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SistemaFacturacion.wsdl)
- [B2Brouter: sandbox](https://docs.b2brouter.net/en/developers/testing/sandbox/)
- [B2Brouter: crear factura](https://developer.b2brouter.net/reference/create-invoice)
- [B2Brouter: enviar factura](https://developer.b2brouter.net/reference/send-invoice)

Consultar fuentes oficiales actuales antes de ampliar protocolos o guiar pasos que no se vean en las capturas.

## 12. Cómo continuar en otro agente

Abrir la misma carpeta del proyecto. Leer AGENTS.md, este documento y docs/18-integraciones-pruebas.md. Comprobar Git y distinguir cambios ya hechos de pendientes. No hace falta trasladar cookies, credenciales, archivos de configuración privados de Codex o todo `.runtime` al contexto de Claude.

Los documentos son contexto resumido; no importan automáticamente la conversación a Claude. Si el usuario quiere conservar mensajes literales adicionales, puede copiarlos manualmente como anexo, revisando que no contengan secretos. El enlace de una conversación por sí solo no entrega a Claude los cambios locales del repositorio.
