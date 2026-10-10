# Remesas, auditoría y revisión funcional — 10 de octubre de 2026

## Qué hace la presentación de una remesa

Una remesa agrupa recibos pendientes para gestionar su cobro. `POST /api/v1/remittances/{id}/send` no envía instrucciones, dinero ni ficheros a un banco: cambia la remesa a `SENT`, guarda la fecha y marca sus recibos como `REMITTED`. El abono es otra operación manual que marca los recibos no devueltos como cobrados y actualiza sus vencimientos y las facturas.

La interfaz llama ahora a esa acción **Registrar presentación**, muestra **Presentación registrada** y explica la ausencia de transmisión bancaria antes de confirmar. Los recibos muestran **Remesado**. Se mantienen los contratos y códigos de estado para compatibilidad.

## Garantía de auditoría de remesas

El historial de la instalación contenía las altas, pero no el cambio a `SENT` de una remesa. No hay evidencia suficiente para atribuir la pérdida a una excepción concreta: no apareció un error del publicador en los logs. El transporte anterior desde el gateway era best-effort y no conservaba los fallos.

Finanzas guarda ahora el evento junto al cambio de negocio, usando la misma transacción PostgreSQL y `remittance_audit_outbox` (Flyway V5). Si no se puede guardar la auditoría, no se confirma el cambio. Incluye empresa, usuario firmado, nombre, fecha, correlación, identificador y número de remesa, importe, moneda, cantidad de recibos y estados anterior/nuevo. No guarda credenciales, cuenta bancaria ni el cuerpo completo de la petición.

Se cubren alta, edición, presentación, abono, anulación y devolución de recibos asociados. Un publicador consulta eventos confirmados cada cinco segundos, bloquea cada fila con `FOR UPDATE SKIP LOCKED` y reintenta fallos con espera creciente hasta cinco minutos. Solo marca publicado tras respuesta satisfactoria de Activity. Si el proceso cae después de recibir esa respuesta, se reenvía el mismo `eventId`: Activity ya ingiere de forma idempotente y no vuelve a generar la alerta. Los eventos publicados se conservan localmente; su archivado/retención queda pendiente.

El gateway omite los éxitos de las operaciones de remesa que ya registra Finanzas, evitando duplicados. Conserva los intentos fallidos/denegados de las peticiones que alcanzan su filtro. El transporte de los demás dominios sigue siendo best-effort; este cambio no promete garantía duradera para toda la aplicación, peticiones rechazadas antes del filtro ni actividad ocurrida antes de instalarlo. Los eventos serán visibles cuando Activity vuelva a estar disponible, sujetos a su retención configurada.

Flyway V6 añade una **fotografía explícita del estado previo** de las remesas existentes que no están en borrador. Su acción es `RECOVER_STATE`, con fecha de recuperación y autor original desconocido. No reconstruye una operación perdida ni atribuye un envío a nadie. La fecha de presentación guardada se conserva como dato del estado.

Historial permite filtrar por recurso y buscar por número de remesa. Muestra número, persona y acción comprensibles; el detalle incluye importe y número de recibos. Un intento fallido no se describe como una operación completada. Las alertas siguen dependiendo de reglas activas; publicar de nuevo el mismo evento no las duplica.

En la empresa de demostración se activaron las reglas `REM_PRESENTATION`, `REM_SETTLEMENT` y `REM_CANCELLATION` para futuras operaciones manuales de remesa. Exigen el metadato `bankTransmission=false`; los intentos fallidos del gateway no activan estas reglas. No se fabrican avisos retrospectivos del envío perdido.

## Comprobación de esta entrega

- `mvn -f backend/pom.xml clean verify`: 590 pruebas, sin fallos.
- `npm ci`, Vitest: 118 pruebas; build y audit de producción correctos, sin vulnerabilidades notificadas.
- Configuración Compose validada; ejecución real mediante arranque nativo y PostgreSQL local, con migraciones V5/V6 aplicadas. Los ocho servicios respondieron `UP`.
- Activity detenido: guardar un borrador sin cambiar su contenido devolvió éxito y dejó su auditoría pendiente, con tres intentos observados. Se reinició también Finance. Tras recuperar Activity, el evento se publicó una sola vez con actor, número e importe, y mantuvo la fecha original del cambio. Remesas y recibos se compararon antes/después sin cambios de negocio.
- Búsqueda real por número de remesa y registro `RECOVER_STATE` comprobados en API y pantalla. Se revisaron el historial y los formularios de presentación a ancho de escritorio y a 390 px, sin confirmar otra presentación o abono.
- Recorrido de consulta por las pantallas principales de maestros, documentos, compras, almacén, finanzas, comisiones, cartera, Veri*Factu, logística, reclamaciones, agenda, configuración, usuarios, conexiones e impresión. Contabilidad se revisó en código/documentación sin operar sobre sus registros.

## Revisión y propuestas

La revisión cruza pantallas, contratos actuales y documentación de los módulos. Es una revisión funcional, no una certificación exhaustiva de todos sus flujos, permisos o integraciones externas. No se han realizado envíos bancarios, de correo ni a la AEAT como parte de ella.

| Área | Situación observada | Mejora propuesta |
|---|---|---|
| Inicio | Comparativas reales de ventas y contadores de maestros/documentos. | Un panel «Qué requiere atención hoy»: cobros vencidos, reclamaciones sin atender, expediciones pendientes y salidas sin stock; cada tarjeta abre el filtro correspondiente. |
| Clientes, proveedores y catálogo | Maestros, importación, clasificación, contactos, precios y tarifas. | Ficha con recorrido comercial completo: presupuesto → albarán → factura → recibo → remesa → reclamación, con documentos y responsables enlazados. Evitar duplicar la ficha de contactos que ya existe. |
| Presupuestos y ventas | Conversiones, numeración, PDF/correo y seguimiento de cobros. | Entregas parciales y agrupación de varios albaranes en una factura; dirección de entrega elegible desde el documento. |
| Compras | Pedido, entrada y factura de proveedor; la entrada actualiza stock. | Completar el circuito de pagos al proveedor: vencimientos, pendientes y propuestas de pago. Hoy la factura de compra no alimenta la cartera ni la bandeja contable. |
| Almacén | Existencias, movimientos, traspasos y salidas automáticas. | Stock mínimo y punto de reposición por almacén, reservas de pedidos y recepción parcial; proponer compras con revisión humana. Antes, corregir que editar el comercial de una factura antigua puede incorporarla a la sincronización de salidas, al usar `updatedAt` como señal. |
| Cartera y caja | Cobros, remesas y arqueos; la presentación bancaria es manual. | Mandatos de cliente, fichero bancario validado, justificante de presentación y conciliación de extractos con recibos. Separar preparado, descargado, presentado y aceptado por el banco. |
| Finanzas y contabilidad | Vencimientos, divisas y diario inicial; pagos a proveedores y conciliación incompletos. | Previsión de tesorería real a 30/60/90 días cuando estén completos cobros y pagos. No equiparar facturación a saldo bancario. |
| Comisiones | Reglas, cálculo y liquidación por periodo. | Indicar claramente el periodo y avisar si hay comisiones fuera del filtro; propuesta de liquidación según facturas cobradas. |
| Logística, agenda y reclamaciones | Rutas, expediciones, pasos y reclamaciones con responsables/seguimiento. | Cola diaria por responsable y plazos vencidos; vincular reclamaciones a tareas y expediciones, con justificantes de entrega. |
| Historial, usuarios y configuración | Auditoría central, reglas y perfiles fijos; remesas con persistencia duradera desde esta entrega. | Extender la auditoría transaccional a cobros, caja, stock y cambios de permisos. Mostrar cambios antes/después seguros, números de documentos y enlaces. Añadir permisos a medida y aprobación de operaciones sensibles cuando se defina la política. |
| Informes e impresión | Informes configurables de maestros, presupuestos, ventas y cobros. | Guardar filtros/columnas favoritos y añadir compras, valoración de almacén, remesas y comisiones; exportación consistente con permisos. |

Orden sugerido: auditoría fiable en todas las operaciones críticas y corrección de sincronización histórica; panel de pendientes; pagos a proveedores y conciliación; reposición de almacén; recorrido completo de cada documento. Estas propuestas no están implementadas por esta entrega.
