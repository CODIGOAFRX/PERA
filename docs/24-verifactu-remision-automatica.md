# 24 — Veri*Factu: remisión automática a la AEAT

Fecha de corte: 8 de octubre de 2026

Fase 5 del [plan de Veri*Factu](13-verifactu.md). Hasta ahora PERA generaba el registro de cada factura (huella, cadena, XML y QR), pero solo se podía remitir a mano, de uno en uno y únicamente a la AEAT de pruebas. Una empresa configurada en producción imprimía el QR de Veri*Factu sin presentar nada. Esta entrega lo cierra.

## Cómo lo hace DimproCristalWin y en qué se diferencia PERA

DimproCristalWin presenta cada factura en cuanto se graba (`documentos.scx → grabar_factura`), pero lo delega en una plataforma externa (Verifacti o B2BRouter, `PROGS\funciones_fe.prg`). Les manda los datos de la factura en JSON con una clave de API. La plataforma genera el registro, la huella y la cadena, lo presenta y devuelve un identificador y el QR. Dimpro guarda ese identificador, consulta el estado y descarga el QR.

PERA hace lo mismo para el usuario (cada factura expedida se presenta sola y su estado se ve en la factura y en un listado), pero sin intermediario. El registro, la huella y la cadena se generan dentro de la emisión y se remiten a la AEAT con el certificado de la empresa. No hay coste por factura ni dependencia de un proveedor. Decisión de Raúl del 8 de octubre de 2026.

## Cómo funciona

- **Automática.** Un proceso del servicio de ventas mira cada 30 segundos qué empresas tienen Veri*Factu activado, la conexión con la AEAT activa y registros por remitir. Se apaga con `pera.verifactu.remission.enabled=false`.
- **Pruebas o producción.** Lo decide el entorno de la empresa en Configuración › Veri*Factu. Las direcciones son las del WSDL oficial (`SistemaFacturacion.wsdl`): `www1.agenciatributaria.gob.es` con certificado normal y `www10` con sello de entidad; `prewww1.aeat.es` y `prewww10.aeat.es` en pruebas.
- **En lotes y en orden.** Hasta 1.000 registros por envío, en orden de cadena y con una sola cabecera; los de un NIF anterior van en un envío aparte. Antes de enviar, cada registro se valida contra el esquema oficial; uno que no lo supera se queda pendiente con el motivo a la vista y no bloquea a los demás.
- **Tiempo de espera.** Entre envíos se respeta el `TiempoEsperaEnvio` que devuelve la AEAT (60 segundos como mínimo). «Remitir ahora» y «Enviar ahora» solo adelantan el trabajo: no se saltan la espera.

## Sin perder ni duplicar

| Qué pasa | Qué hace PERA |
|---|---|
| La AEAT contesta | Cada registro toma su estado: aceptado, aceptado con errores o rechazado, con el código y el texto de la AEAT y el CSV del envío. |
| No llega respuesta (corte, tiempo agotado) | El registro queda «remitido, sin confirmar». Pasados dos minutos se vuelve a enviar. Si la AEAT ya lo tenía, contesta «duplicado» con el estado que guarda y con eso se cierra. Es la reconciliación que propone la propia AEAT. |
| La AEAT rechaza el envío entero (fallo SOAP) | No ha registrado nada: los registros vuelven a pendientes con el motivo y se reintenta con esperas crecientes (1, 2, 4… minutos, hasta una hora). |
| Registro rechazado | No se reenvía tal cual: su huella ya está encadenada. Queda señalado con el motivo para subsanarlo. |
| Remisión desactivada | Las facturas se emiten igual, con su registro y su QR, y esperan hasta que se active. |

Los registros se reclaman y se marcan como remitidos en una transacción que se confirma antes de la llamada a la AEAT, con bloqueo de fila. Dos procesos no pueden remitir el mismo lote.

## Pantallas

- **Factura** (Ventas): el bloque Veri*Factu enseña el estado ante la AEAT, el entorno, los intentos, el código y el texto de la respuesta, y qué va a pasar. Si aún no tiene respuesta, «Enviar ahora».
- **Veri*Factu** (`/verifactu`, nueva): estado de la conexión (activado, entorno, remisión activa, último envío, próximo envío, fallos seguidos), recuento por estado y lista de registros. Abre en «Necesitan atención»: rechazados, con errores, sin confirmar y pendientes. Botón «Remitir ahora».
- **Conexiones**: los textos dicen ya si se remite a pruebas o a producción, y el interruptor es «Remitir las facturas a la AEAT».
- **Configuración › Veri*Factu**: el entorno avisa de que en producción se presenta de verdad.

Permisos: `verifactu:read` para ver (propietario, administrador y economía) y `verifactu:write` para remitir.

## Datos

Migración `V14__verifactu_remission.sql` del servicio de ventas: código y texto de la respuesta en `verifactu_records`, y fallos seguidos, último error y último envío en `fiscal_connections`.

Para probar sin certificado real, `pera.verifactu.aeat.test-endpoint` apunta la remisión a un servicio local que hace de AEAT de pruebas. Se ignora siempre en producción.

## Prueba con la AEAT de pruebas real

El 8 de octubre de 2026 Raúl presentó una factura con su certificado personal de la FNMT en la preproducción de la AEAT: aceptada, con CSV, y el QR la encuentra al escanearlo.

Esa prueba destapó un fallo: la cadena se llevaba por empresa, y la primera factura con el NIF nuevo se encadenó con un registro de otro NIF. La AEAT la rechazó (error 1123 del bloque de Encadenamiento). La cadena es de cada emisor: ahora, si el registro anterior es de otro NIF, el nuevo empieza cadena propia como primer registro.

## Límites y siguiente paso

- **Subsanación** de registros rechazados o aceptados con errores, y **anulación**: fase 6 del plan.
- **Validación del NIF del cliente** contra la AEAT al darlo de alta, como hace Dimpro con Verifacti.
- Antes de la primera empresa en producción: declaración responsable de PERA como sistema informático de facturación y revisión de un asesor fiscal (doc 13, apartado 1).
