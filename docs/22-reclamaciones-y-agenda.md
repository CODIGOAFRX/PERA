# 22 — Reclamaciones y agenda

Fecha de corte: 3 de octubre de 2026

Quinto bloque del [mapa de migración](19-mapa-migracion-dimprocristalwin.md). Trae los menús «Reclamaciones», «Agenda» y «Agenda telefónica» de DimproCristalWin. Antes de diseñarlo se revisaron sus tablas (`reclamaciones`, `lineas_reclamacion`, `motivo`, `causa`, `area`, `cargos`, `resolucion`, `accion_preventiva`, `agenda`, `detalle_agenda`, `listin_telefonico`) y su formulario de reclamaciones. Se ha traído la estructura, no datos de clientes.

## Dónde vive

En `operations-service`, paquetes `claims` y `agenda`, con la migración `V10__claims_and_agenda.sql`. Las pantallas son `/reclamaciones` y `/agenda`.

| Permiso | Perfiles |
|---|---|
| `claims:read` / `claims:write` | Propietario, administrador, economía y logística |
| `agenda:read` / `agenda:write` | Todos los perfiles |

Logística recibe además `customers:read` para poder elegir el cliente de una reclamación o de una cita. La pantalla de Clientes sigue sin aparecerle.

## Reclamaciones

Numeración `RCL-AAAA-NNNNNN` por empresa y año.

- **Datos**: fecha, cliente, documento de origen (número y fecha del albarán o factura reclamados), qué ha pasado y, opcionalmente, los productos afectados con su cantidad. Queda constancia de quién la registra.
- **Clasificación**, como en el programa anterior: motivo, no conformidad, causa, área, responsable, resolución y acción preventiva. Todas opcionales al abrirla, porque muchas se conocen al investigar.
- **Tablas de clasificación**: cada empresa mantiene las suyas desde la pestaña «Tablas de clasificación». Un valor no se borra, se desactiva: deja de ofrecerse en reclamaciones nuevas pero se conserva en las que ya lo tenían.
- **Seguimiento**: la acción preventiva lleva un plazo en días. Al elegirla, la reclamación toma como fecha de seguimiento la suya más ese plazo, salvo que se indique otra. Las abiertas con el seguimiento vencido se señalan en rojo y tienen su propio filtro.
- **Comentarios**: notas de seguimiento con autor y hora. No se editan ni se borran.
- **Cierre**: exige resolución. Se puede reabrir. Una reclamación cerrada no se edita.
- **Eliminar**: solo si está abierta y sin comentarios; con historia, se cierra.
- **Días abierta**: hasta el cierre o, si sigue abierta, hasta hoy.

## Agenda

- **Citas**: día, hora de inicio y fin (sin hora es de todo el día), qué, tipo, quién, cliente, contacto del listín, dónde, documento relacionado y notas.
- **Estados**: pendiente, hecha (con fecha y qué se hizo, como «Realizado» del programa anterior) y anulada. Una cita hecha o anulada no se edita salvo que se reabra.
- **Vista semanal** de lunes a domingo, con filtros por persona, tipo y pendientes. Se consultan como mucho 62 días de una vez.
- **Tipos de cita** configurables con color: medición, montaje, visita comercial...

## Listín

Personas y empresas con las que se habla, sean o no clientes: nombre, empresa, teléfono, móvil, correo, dirección, población, provincia y notas. Puede vincularse a un cliente. Exige al menos un teléfono, un móvil o un correo. La búsqueda cubre nombre, empresa, teléfonos, correo y población.

## Límites conocidos

1. **«Crea documento» no está.** En el programa anterior una reclamación podía generar un documento nuevo (una reposición). Aquí se registra la resolución, pero el documento de reposición se hace aparte en Ventas.
2. **Sin listados ni estadísticas de reclamaciones** (el listado y el comparativo del programa anterior).
3. **El documento de origen es un número escrito a mano**, no un enlace al documento de Ventas, y no se importan sus líneas.
4. **La persona de una cita es un nombre**, no un usuario. No hay avisos ni recordatorios.
5. **Sin festivos ni vacaciones** en la agenda (el programa anterior los marcaba por día y usuario).
6. **Sin importación del listín** desde el programa anterior. Es la tabla más usada (miles de contactos por empresa) y convendrá un importador específico.

## Verificación

- 14 pruebas nuevas en `operations-service` (reclamaciones, agenda, listín y permisos).
- 9 pruebas nuevas en el frontend.
- Migración V10 aplicada sobre la base con V1 a V9 y servicio arrancado con validación de esquema.
- Recorrido real de 29 comprobaciones por el gateway: tablas de clasificación, reclamación registrada por logística, cierre sin resolución rechazado, comentarios, cierre y reapertura, citas con y sin hora, filtros de semana, cita hecha y reabierta, listín y permisos por perfil.
- Revisión en el navegador de las dos pantallas con datos reales.
