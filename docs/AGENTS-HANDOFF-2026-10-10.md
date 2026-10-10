# AGENTS.md — Contexto canónico y handoff de PERA ERP

> Última actualización: 10 de octubre de 2026.
> Este archivo es el punto de entrada para Codex, Claude, ChatGPT y cualquier persona que trabaje en el repositorio.
> Repositorio canónico: `https://github.com/CODIGOAFRX/PERA`.

## 1. Cómo usar este documento

Antes de analizar o modificar PERA:

1. Lee este archivo completo.
2. Lee `README.md`.
3. Consulta `docs/README.md` y abre solo la documentación relacionada con la tarea.
4. Ejecuta `git status --short` y revisa el historial reciente.
5. Contrasta siempre este resumen con controladores, servicios, migraciones y pruebas. El código es la fuente final sobre lo implementado.

Si se comparte este archivo sin el repositorio, debe tratarse como una fotografía operativa, no como material suficiente para implementar cambios con seguridad. No inventes contratos, requisitos ni resultados de pruebas ausentes.

En el momento de este handoff hay cambios funcionales deliberados sin confirmar en Git. No limpies el árbol, no hagas `reset` y no descartes archivos no seguidos: ejecuta `git status --short`, identifica el alcance de la tarea y conserva el trabajo ajeno. Los enlaces privados de Trello o Google Docs se comparten fuera del repositorio; no copies invitaciones con token a código o documentación versionada.

## 2. Producto y dirección

PERA es un ERP horizontal y multiempresa para pequeñas y medianas empresas, construido con Java, Spring Boot, React y PostgreSQL.

El proyecto nació del estudio de un ERP antiguo en Visual FoxPro usado por una empresa de cristalería. Esa referencia es histórica y ya no define el producto. El núcleo debe:

- servir a comercio, distribución y servicios de distintos sectores;
- usar lenguaje y modelos generalistas;
- mantener límites de dominio claros y microservicios de granularidad gruesa;
- permitir futuras verticales mediante extensiones desacopladas;
- priorizar mantenibilidad, pruebas, seguridad y evolución gradual.

El contenido de `docs/reference/` no es una especificación contractual. No copies reglas de vidrio, construcción u otro sector al núcleo.

### Contexto comercial y decisiones explícitas del usuario

- El trabajo comenzó como modernización de un ERP Visual FoxPro para una cristalería, pero el posible comprador apareció con otra solución. PERA continúa como producto propio, genérico y vendible a pequeñas y medianas empresas.
- No se debe borrar el legado sectorial sin autorización. Se conserva congelado y desacoplado, pero no recibe nuevas dependencias ni desarrollo dentro del núcleo horizontal.
- El nombre vigente del producto y del código es **PERA ERP**. La identidad visual usa una pera lineal/inteligente, no una letra `P` aislada; el mismo símbolo debe aparecer en aplicación, login y favicon.
- La interfaz debe permitir comprender la marcha económica de un vistazo: facturación reciente frente a periodos anteriores, acumulado del mes frente al ritmo esperado y desviaciones reales, sin inventar datos.
- Dirección visual vigente, solicitada en septiembre: inspiración FinanX, títulos Georgia, fondos cálidos, verdes profundos y bordes finos. Mantén la pera como símbolo y una presentación profesional y contenida. Esta decisión sustituye la preferencia anterior de verde vivo en superficies.
- Tarjetas, formularios, filtros, modales, tablas y estados vacíos deben mantener padding interior consistente. No se aceptan controles pegados al borde ni desplegables recortados por `overflow` o `z-index`.
- La barra lateral debe poder ocultarse, redimensionarse con ratón y teclado, compactarse y terminar ocultándose al llevarla al mínimo.
- La aplicación necesita perfiles de acceso por función, un propietario con control total, un administrador que gestione usuarios y perfiles especializados para economía, logística y catálogo.
- Los datos operativos deben poder extraerse mediante informes configurables e imprimibles, con columnas, filtros y orden elegidos por el usuario.

## 3. Fuentes de verdad

Ante una contradicción, aplica este orden:

1. Petición actual y explícita del usuario.
2. Este `AGENTS.md`.
3. Código, migraciones Flyway y pruebas ejecutables.
4. Documentación activa en `docs/`.
5. Material histórico de `docs/reference/`.

No conviertas una entidad JPA o una pantalla aislada en una capacidad “terminada”. Comprueba persistencia, reglas, API, permisos, frontend y pruebas según corresponda.

## 4. Mapa del repositorio

```text
backend/     Reactor Maven y módulos Spring Boot.
frontend/    React, TypeScript, Vite, Vitest, Nginx y Dockerfile.
infra/       Docker Compose, variables de ejemplo y provisión PostgreSQL.
scripts/     Arranque, parada y activación de licencia en Windows.
docs/        Visión, arquitectura, modelo, API, decisiones, auditorías y roadmap.
.runtime/    Bases, almacenamiento y logs locales; ignorado por Git.
```

El repositorio es un monorepo deliberado. No crees un microservicio por tabla ni relaciones JPA entre servicios.

## 5. Arquitectura desplegable

PERA ejecuta ocho procesos Java: siete servicios propietarios de datos y un gateway.

```text
React/Vite o Nginx :5173
          |
          v
API Gateway        :8080
   |-- Identity    :8081 --> pera_identity
   |-- Master Data :8082 --> pera_master_data
   |-- Sales       :8083 --> pera_sales
   |-- Finance     :8084 --> pera_finance
   |-- Operations  :8085 --> pera_operations
   |-- Activity    :8086 --> pera_activity
   `-- Licensing   :8087 --> pera_licensing
```

| Módulo | Responsabilidad principal |
|---|---|
| `platform-shared` | Entidades base, conversión de permisos, excepciones y `ProblemDetail`; no se despliega ni posee datos. |
| `api-gateway` | Entrada HTTP, CORS, JWT, rutas, auditoría de mutaciones y control de licencia. |
| `identity-service` | Login, empresas, usuarios, membresías, roles, permisos, parámetros de empresa y logos. |
| `master-data-service` | Clientes y sus fichas, comerciales, proveedores, catálogo, jerarquía, impuestos, tarifas, pricing y embalajes. |
| `sales-service` | Documentos comerciales, presupuestos, numeraciones, cálculos, snapshots, comisiones, correo comercial y Veri*Factu. |
| `finance-service` | Pagos, vencimientos, monedas, recibos, remesas, caja, contabilidad inicial y auditoría durable de remesas. |
| `operations-service` | Compras, inventario, salidas por ventas, reclamaciones, agenda, workflows, logística, fletes, expediciones y adjuntos. |
| `activity-service` | Historial central, exportación CSV, retención y alertas. |
| `licensing-service` | Emisión, activación, validación, rotación, suspensión y revocación de licencias. |

### Dependencias entre servicios

- Sales consulta master-data para validar clientes/productos y resolver precios; consulta identity para la moneda base y finance para el cambio aplicable.
- Finance consulta el endpoint interno autenticado de sales al leer la bandeja contable y sincroniza snapshots de facturas por empresa. No comparte entidades ni hace joins entre bases.
- El gateway envía auditoría a activity y valida la licencia contra licensing.
- Operations conserva UUID y snapshots de referencias externas; no comparte entidades con maestros o ventas. Consulta el endpoint interno de ventas para sincronizar salidas de almacén.
- Finance notifica el cobro de facturas a Sales; esa llamada todavía no tiene reintentos durables. Publica su auditoría de remesas en Activity mediante una cola transaccional con reintentos.
- Cada servicio propietario aplica transacciones ACID locales. No hay transacciones distribuidas.
- El outbox general de eventos de dominio de ventas está persistido, pero todavía no tiene publicador ni broker. Es distinto de las colas operativas de correo, remisión a la AEAT y auditoría de remesas, que sí tienen procesamiento.

## 6. Tecnologías fijadas

Backend:

- Java 21, Spring Boot 4.1.0 y Spring Cloud 2025.1.2.
- Maven multimódulo, Spring Data JPA, Spring Security, JWT HS256, Bean Validation y Flyway.
- PostgreSQL 17, Actuator y Springdoc OpenAPI 3.0.3.
- JUnit 5 y Mockito; sin Lombok.

Frontend:

- React 19.2.8, TypeScript 7.0.2, Vite 8.2.1 y Vitest 4.1.11.
- Testing Library y Lucide React.
- Router propio basado en History API; no se usa React Router.
- Nginx sirve la SPA de producción y reenvía `/api/` al gateway.

No actualices dependencias indiscriminadamente. Justifica cualquier actualización y verifica compatibilidad, pruebas y build.

## 7. Capacidades implementadas

### Identidad y empresa

- Login con selección de empresa y JWT que contiene `company_id`, roles y permisos.
- Consulta de la empresa activa, actualización de sus datos y administración de usuarios dentro del tenant activo: listado, alta, edición, activación, contraseña y perfil.
- Perfiles base `OWNER`, `ADMIN`, `ECONOMY`, `LOGISTICS` y `CATALOG`; propietario y administrador tienen acceso completo, pero solo un propietario puede asignar o modificar otra cuenta propietaria.
- Parámetros: país, locale, zona horaria, moneda base, nombre visible, correos, teléfono, web y dirección.
- Logo PNG/JPEG/WebP de hasta 2 MiB con firma real, SHA-256, ETag y almacenamiento aislado por empresa.
- La creación de nuevas empresas exige `platform:companies:manage`; no existe todavía un platform admin provisionado automáticamente.

### Maestros, impuestos, precios y embalajes

- Consulta, alta y edición de clientes, proveedores y productos. Sus códigos son inmutables tras el alta.
- Importación masiva de clientes, proveedores y productos desde CSV o Excel, con plantilla XLSX descargable, campos opcionales, filas vacías ignoradas y resultado independiente por fila. Los identificadores fiscales importados se conservan como texto heredado sin validar su dígito de control; en artículos, código y nombre son los únicos datos obligatorios, las unidades habituales en español se normalizan y un opcional no reconocido no bloquea la fila. Los flujos normales mantienen su validación estricta.
- Jerarquía `naturaleza -> supertipo -> tipo -> grupo`, validada dentro de la empresa y asociable a productos.
- Códigos fiscales por empresa con país ISO, porcentaje, vigencia, exención y estado.
- Tipos de embalaje y configuraciones por producto: unidades, niveles, dimensiones, pesos, volumen, retornable y predeterminado.
- Tarifas buscables por texto, cliente, naturaleza, supertipo, tipo, ámbito, estado y vigencia.
- Líneas de tarifa y reglas por naturaleza, supertipo, tipo, grupo o producto, opcionalmente específicas de cliente.
- Simulador/resolución de precio con traza reproducible.

### Ficha de cliente y comerciales

- Clasificación por grupo, tipo, forma de entrega y motivo de baja; catálogos por empresa, desactivación en lugar de borrado y conservación de referencias históricas.
- Comerciales con código inmutable, datos de contacto, estado y porcentaje de comisión por defecto; asignación al cliente y filtros por comercial.
- Ficha con móvil, cuenta contable propia, tarifa, forma de pago y código con el que el cliente identifica a nuestra empresa como proveedor.
- Contactos con un único principal, direcciones de entrega con una única habitual activa y notas desactivables con marca para documentos. Las direcciones de entrega aún no se seleccionan en albaranes o facturas y las notas no implican que todas las plantillas las impriman.
- Si una petición antigua no incluye clasificación, debe conservar la existente. Guardar desde la UI no puede borrar tarifa, forma de pago o código de proveedor cuando el usuario no puede consultar sus catálogos.
- El comercial se conserva al convertir documentos y puede cambiarse después, incluso en factura expedida porque no es dato fiscal. Una comisión liquidada impide cambiarlo hasta deshacer la liquidación.

### Ventas y presupuestos

- Documentos `QUOTE`, `SALES_ORDER`, `DELIVERY_NOTE`, `INVOICE`, `RECTIFYING_INVOICE` y `WORK_ORDER`. La rectificativa tiene tipo y numeración propios; no sustituirla por una factura ordinaria con importe negativo.
- Creación, listado, detalle, conversión compatible y estado de cobro de factura.
- Módulo dedicado de presupuestos: borrador, envío, aceptación, rechazo, caducidad y conversión.
- Una línea de producto usa precio resuelto e impuesto maestro mientras el usuario no los cambie. Si el usuario edita explícitamente precio unitario o IVA, esos valores manuales son contractuales y deben persistirse; `unitPriceOverridden` y `taxPercentageOverridden` distinguen la intención. El backend sigue resolviendo y validando producto, código y descripción, pero no puede sustituir silenciosamente una edición manual por el precio base o el 21 % del catálogo.
- Solo los presupuestos `DRAFT` se pueden eliminar. La pantalla permite selección individual, selección de todos los borradores visibles y borrado múltiple con confirmación; presupuestos enviados, aceptados, rechazados, caducados o convertidos no se marcan ni se borran por esa vía.
- Los documentos ya guardados conservan sus snapshots e importes históricos: una corrección de reglas no recalcula registros antiguos de forma silenciosa. Los borradores incorrectos se eliminan y recrean de forma explícita.
- Numeraciones por empresa y tipo, series y patrones con `{yyyy}`, `{yy}`, `{MM}`, `{dd}`, `{series}` y `{seq:N}`; reinicio anual, mensual, diario o nunca.
- El generador evita colisiones con números ya usados. Un intento fallido o un borrado puede dejar huecos; nunca se renumera un documento existente para rellenarlos.
- Resolución fiable de cliente, producto y tarifa desde master-data: el navegador no decide sus snapshots.
- Snapshots de cliente, producto, tarifa/traza, cantidad solicitada/facturada, moneda/tipo de cambio e impuesto. La migración sales V6 conserva ID, código, nombre, país, porcentaje y exención fiscal.
- Outbox transaccional persistido para eventos de dominio.
- Resumen económico agregado en moneda base: seis meses, acumulado diario actual/anterior, ritmo proporcional del mes anterior y desviación a fecha de hoy.
- El listado comercial busca por número, código o nombre de cliente y por intervalo de fechas. Las fuentes convertidas se conservan en gris y tachadas; las facturas con registro Veri*Factu muestran una marca junto al número.

### Finanzas y monedas

- Formas de pago y generación idempotente de vencimientos.
- Catálogo ISO de monedas con una moneda base por empresa.
- Tipos de cambio fechados y con fuente; resolución directa o inversa y redondeo según moneda destino.
- Documentos comerciales guardan moneda base, cambio, fecha, fuente e importes convertidos.
- Generar vencimientos permite emitir sus recibos. Cartera y caja tienen flujos operativos reales, descritos a continuación; no equivalen a una contabilidad completa.

### Cartera, remesas y caja

- `/cartera`: recibos, remesas y caja para OWNER, ADMIN y ECONOMY, con `finance:read` / `finance:write`.
- Un recibo por vencimiento pendiente; emisión idempotente, cobro completo, devolución con motivo, reapertura y anulación. No hay cobro parcial de un recibo.
- Los cambios recalculan el estado de cobro de la factura y notifican a Sales. `invoiceUpdated: false` indica que la cartera está guardada pero la factura necesita revisión; no ocultes esa situación ni inventes sincronización garantizada.
- Remesas con recibos de una única moneda; los recibos de borrador quedan reservados. Presentación, abono, devoluciones y anulación aplican sus reglas y actualizan los recibos.
- **Registrar presentación** solo registra una presentación hecha fuera de PERA. El contrato conserva `send` / `SENT`, pero no hay conexión bancaria, transmisión de dinero ni fichero SEPA. El abono se registra manualmente tras verificarlo fuera de la aplicación.
- Sus cambios se auditan en la misma transacción de Finance y se publican con reintentos; los fallos de Activity no pierden el evento comprometido. Ver sección 23 y `docs/27-remesas-auditoria-y-revision.md`.
- Cajas con responsable y una sola sesión abierta, fondo inicial, cobros en efectivo, ingresos/gastos/retiradas manuales y cierre con arqueo y diferencia visible. Una salida no puede dejar saldo negativo.
- La caja todavía no guarda moneda y muestra euros. Devolver un recibo cobrado en efectivo no revierte automáticamente caja: requiere apunte manual.

### Comisiones

- `/comisiones` en Sales, permisos `commissions:read` / `commissions:write` para OWNER, ADMIN y ECONOMY.
- Reglas por comercial, producto o grupo o generales y tramos de importe; porcentaje entre 0 y 100. Gana producto, luego grupo, luego general; dentro del nivel, el tramo más estrecho; después, la comisión por defecto.
- Base: neto de la línea sin IVA en moneda base. El tramo usa el valor absoluto; las rectificativas restan. No hay reglas por tarifa ni comisiones de cobro.
- Recalcular facturas y rectificativas expedidas en un periodo de hasta un año guarda detalle por línea y origen de porcentaje. No cambia comisiones liquidadas.
- Listado con filtros, totales, selección, liquidación con fecha/nota y deshacer liquidación. Una rectificativa por sustitución resta su importe entero, sin compensación automática con la factura sustituida.

### Correo comercial y Veri*Factu

- `/conexiones`: configuración SMTP por empresa para OWNER/ADMIN, asistente, credenciales cifradas y prueba de conexión. No enviar correos reales durante pruebas sin autorización.
- Presupuestos con envío manual; facturas expedidas con envío automático opt-in y PDF, incluido QR fiscal cuando existe. Cola persistente con protección contra duplicados y revisión explícita de entregas inciertas; no reenviar a ciegas.
- Veri*Factu nativo: expedición de facturas, registros con huella, encadenamiento por emisor, XML y QR, configuración por empresa y certificado para remisión directa a AEAT.
- `/verifactu`: estados, registros que necesitan atención, conexión, intentos/respuesta y solicitud de remisión. Permisos `verifactu:read` / `verifactu:write` para propietario, administrador y economía.
- Remisión automática cada 30 segundos cuando está activada, lotes de hasta 1.000 registros, validación de esquema, espera indicada por AEAT y reintentos/reconciliación ante respuesta incierta. Ver `docs/24-verifactu-remision-automatica.md`.
- **El entorno de producción remite de verdad a la AEAT.** No activarlo, cambiar emisor/certificado o pulsar remisión real por una prueba visual. El endpoint local de pruebas se ignora en producción.
- Pendientes: subsanación y anulación fiscal de registros, validación del NIF contra AEAT y requisitos previos a comercialización. No presentar este módulo como cumplimiento fiscal completo ni confundirlo con SII o factura electrónica B2B.

### Contabilidad inicial

- Espacio autenticado `/contabilidad` dentro de `finance-service`, con permisos `accounting:read` y `accounting:write` para propietario, administrador y economía.
- Plan de cuentas base por empresa y búsqueda por número, nombre o alias frecuentes como caja, efectivo, banco, ventas, clientes, compras y proveedores.
- Bandeja contable sincronizada con facturas ordinarias y rectificativas de ventas. Los borradores notifican trabajo pendiente, pero no se pueden contabilizar hasta estar expedidos.
- Burbuja de pendientes en la navegación, propuesta automática de asiento para facturas y editor visual con Debe y Haber separados.
- Asientos manuales y procedentes de factura, siempre aislados por empresa. El servicio exige al menos dos líneas, una sola cara positiva por línea y total Debe igual a total Haber.
- Es un primer libro diario operativo, no una contabilidad general o fiscal completa: las compras operativas existen, pero no alimentan contabilidad; faltan conciliación bancaria, cierres, amortizaciones, libros oficiales y presentación tributaria.

### Compras e inventario

- `/compras` y `/almacen` en Operations para OWNER, ADMIN y LOGISTICS. Permisos `purchases:read/write` e `inventory:read/write`.
- Compras: `PURCHASE_ORDER` → `GOODS_RECEIPT` → `SUPPLIER_INVOICE`, numeración por empresa/tipo/año y estados borrador, confirmado, convertido y anulado. También se permite pedido → factura.
- Solo se edita/elimina borrador. Confirmar un albarán de entrada exige almacén y suma existencias en la misma transacción; una factura con almacén sin albarán previo también, pero no duplica la entrada del albarán.
- Confirmar factura exige número de proveedor sin duplicado vigente para ese proveedor. Importes de línea redondeados a céntimo e impuesto sobre bases agrupadas por porcentaje; no cambiar este cálculo por el de ventas.
- Anular revierte la entrada con nuevos apuntes; se rechaza si no queda stock suficiente. Las conversiones son completas: sin recepciones parciales ni agrupación de albaranes.
- Almacenes con código inmutable y un único predeterminado activo por empresa. Existencias por almacén/producto, unidad coherente, nunca negativas y nunca editadas directamente.
- Diario de movimientos acumulativo, ajustes con motivo y traspasos enlazados. Fecha del servidor; corregir mediante nuevos apuntes, sin reescribir saldos históricos.
- El albarán de venta confirmado descuenta existencias; una factura directa también; la factura del albarán no repite salida. Sincronización interna periódica y al abrir Salidas de venta, sin bloquear confirmación comercial.
- Solo productos ya inventariados. Almacén predeterminado; se descuenta cantidad solicitada, no cantidad facturada por mínimos de tarifa. Con falta de stock queda pendiente; salida completa, alternativa desde otro almacén o descarte.
- Activar inventario no debe descontar el histórico. Existe una incidencia con cambios de `updatedAt` descrita en la sección 24: no asumir que cambiar comercial representa una nueva entrega.
- Sin valoración FIFO/precio medio, reservas, mínimos, lotes, números de serie ni ubicaciones. Facturas rectificativas no devuelven stock automáticamente; devolución mediante ajuste. Facturas de proveedor todavía no generan pagos ni asiento contable.

### Reclamaciones, agenda y listín

- `/reclamaciones` para OWNER, ADMIN, ECONOMY y LOGISTICS con `claims:read/write`; clasificación configurable por empresa, productos afectados, autor, seguimiento y filtro de vencidas.
- Comentarios con autor/hora que no se editan ni borran. Cerrar exige resolución; se puede reabrir. Cerrada no se edita; solo una abierta sin comentarios se elimina.
- Documento de origen aún es número/fecha escritos, sin enlace ni importación de líneas de Ventas. Reposición se crea aparte; sin estadísticas específicas.
- `/agenda` y listín con `agenda:read/write` para todos los perfiles. Semana lunes-domingo, citas por tipo/persona/cliente/contacto/documento, todo el día u horas; máximo 62 días por consulta.
- Citas pendientes, hechas o anuladas; hechas/anuladas se reabren antes de editar. Tipos con color; persona como nombre, sin vínculo de usuario ni recordatorios automáticos.
- Listín de contactos con al menos teléfono, móvil o correo y búsqueda por nombre/empresa/contactos/población. Sin importación del legado, festivos ni vacaciones.

### Operaciones, workflows y logística

- Plantillas versionadas de workflow con pasos ordenados; publicación, nueva versión, retirada y borrado de borradores.
- Ejecuciones vinculadas a una referencia de negocio; inicio, finalización, omisión o cancelación de pasos.
- Transportistas propios/terceros, vehículos con capacidad y rutas con paradas, ventanas, distancia y duración estimada.
- Expediciones con líneas snapshot, fechas previstas/reales y estados `PLANNED`, `PACKING`, `READY`, `DISPATCHED`, `IN_TRANSIT`, `ARRIVED`, `DELIVERED`, `EXCEPTION` y `CANCELLED`.
- Tarifas de flete por ruta/transportista, moneda, vigencia, prioridad, rangos y métodos fijo, por kg, m³ o km y combinaciones fijo+variable.
- Simulación y aplicación de flete a expedición con snapshot completo del cálculo.
- Adjuntos reales de expedición mediante multipart, descarga autenticada y borrado; validan tamaño, nombre, MIME/firma, UTF-8 cuando procede y SHA-256.

### Historial, retención y alertas

- El gateway registra mutaciones autenticadas con empresa, actor, acción, recurso, resultado, correlación, duración y metadata segura.
- `audit_events` es append-only salvo la función controlada de retención.
- Búsqueda, detalle y exportación CSV, limitada a 50.000 filas y protegida contra fórmulas de hoja de cálculo.
- La vista principal traduce las mutaciones a acciones de negocio (creación, importación, conversión, contabilización, etc.), se revalida al recuperar el foco y periódicamente mientras está visible; método HTTP, ruta, tipo interno y correlación quedan dentro de “Información técnica”.
- Retención automática habilitada por defecto a 365 días, en lotes y mediante la migración activity V2.
- Reglas de alerta con evento, acción/recurso, condiciones, severidad, plantillas y cooldown.
- Canal soportado actualmente: `IN_APP`. Las ocurrencias se pueden reconocer y resolver.
- Auditoría durable específica de remesas: evento guardado junto al negocio, entrega idempotente a Activity y reintentos ante caída. El gateway evita duplicar esos éxitos y conserva intentos fallidos; otros dominios siguen con auditoría best-effort.
- Historial permite filtrar recurso y buscar número de remesa en metadata segura; detalle con importe, moneda y número de recibos. Los estados antiguos recuperados no inventan actor ni fecha de acción original.

### Informes e impresión

- Centro de informes en `/impresion` con módulos de clientes, proveedores, productos, presupuestos, ventas y cobros pendientes.
- Cada perfil solo ve los informes de las áreas que puede consultar; los servicios propietarios vuelven a aplicar permisos y aislamiento por empresa.
- El usuario elige columnas, filtros, orden y título. El navegador recorre listados paginados, muestra una vista previa y genera una composición A4 apaisada imprimible o guardable como PDF.
- Cobros pendientes combina facturas y vencimientos: usa importe menos cobrado y, si aún no existe calendario, conserva el total pendiente de la factura.
- Los maestros exponen `createdAt` de forma aditiva para poder ordenar por fecha de alta. La generación se limita operativamente a 50.000 filas por informe.
- También existen documentos comerciales completos en PDF/impresión. Distingue la impresión del documento de los informes tabulares configurables; no prometas cobertura de todos los nuevos módulos.

### Licencias

- Emisión con código de activación mostrado una sola vez; hashes con pepper y secretos no reversibles.
- Activación por instalación, límite de instalaciones, vigencia, intervalo de comprobación, gracia y features declaradas.
- Validación y rotación de token públicas con rate limit local; suspensión, reanudación y revocación administrativas.
- El gateway usa caché acotada, single-flight y gracia ante indisponibilidad; fuera de gracia falla cerrado.
- El enforcement está desactivado en desarrollo salvo configuración explícita.

## 8. Orden exacto del pricing

El resolver de `master-data-service`:

1. valida empresa, cliente, producto, jerarquía, cantidad, fecha y moneda;
2. selecciona tarifa por asignación, prioridad, especificidad, cliente y código estable;
3. construye la cadena de herencia, rechazando ciclos o monedas incompatibles;
4. parte del precio base;
5. aplica el ganador de precio fijo;
6. aplica el ganador de descuento;
7. aplica el ganador de recargo de regla;
8. aplica mínimo por pieza;
9. redondea cantidad al múltiplo configurado;
10. calcula subtotal;
11. aplica recargo general y recargo energético;
12. aplica facturación mínima;
13. redondea dinero a cuatro decimales y devuelve la traza.

La precedencia de reglas es prioridad, especificidad de cliente, especificidad del objetivo, profundidad de herencia e ID estable. No reintroduzcas multiplicadores opacos.

## 9. Rutas principales

Todos los recursos de usuario se consumen por el gateway `:8080`.

- Identity: `/api/v1/auth`, `/companies`, `/users`, `/roles`, `/company-settings`.
- Master-data: `/customers`, `/suppliers`, `/products`, sus rutas `/import` y `/import-template`, `/product-natures`, `/product-supertypes`, `/product-types`, `/product-groups`, `/tax-codes`, `/tariffs`, `/pricing`, `/packaging-types`, `/product-packaging`.
- Sales: `/documents`, `/quotes`, `/numbering-schemes`, `/sales-dashboard`.
- Finance: `/payment-methods`, `/due-dates`, `/currencies`, `/exchange-rates`, `/currency-conversions`, `/accounting/accounts`, `/accounting/inbox` y `/accounting/entries`.
- Operations: `/workflow-templates`, `/work-executions`, `/carriers`, `/vehicles`, `/delivery-routes`, `/freight-rates`, `/shipments`.
- Extensiones de Sales: `/commission-rules`, `/commissions` y recursos de correo y Veri*Factu; consulta sus controladores para las rutas exactas.
- Extensiones de Finance: `/receipts`, `/remittances`, `/cash-registers`, `/cash-sessions`.
- Extensiones de Operations: `/purchase-documents`, `/warehouses`, `/stock-levels`, `/stock-movements`, `/sales-deliveries` y recursos de reclamaciones, agenda y listín. La ficha de cliente amplía Master Data con clasificación, comerciales, contactos, direcciones y notas.
- Activity: `/history`, `/alert-rules`, `/alerts`.
- Licensing: `/api/v1/licenses` y `/public/v1/licenses`.

Consulta los controladores para método, filtros y payload exactos. No inventes rutas a partir de este índice.

## 10. Frontend

Rutas canónicas autenticadas:

| Ruta | Pantalla |
|---|---|
| `/` | Resumen |
| `/impresion` | Informes configurables e impresión/PDF |
| `/clientes` | Clientes |
| `/proveedores` | Proveedores |
| `/catalogo` | Productos |
| `/maestros` | Jerarquía, impuestos, tarifas y embalajes |
| `/presupuestos` | Presupuestos |
| `/ventas` | Documentos comerciales |
| `/compras` | Pedidos, entradas y facturas de proveedor |
| `/almacen` | Almacenes, existencias, salidas de venta y diario |
| `/finanzas` | Pagos y vencimientos |
| `/comisiones` | Cálculo, reglas y liquidación de comerciales |
| `/verifactu` | Registros y remisión fiscal |
| `/cartera` | Recibos, remesas y caja |
| `/contabilidad` | Bandeja contable, libro diario y plan de cuentas |
| `/operaciones` | Workflows, logística, fletes y expediciones |
| `/reclamaciones` | Reclamaciones y clasificación |
| `/agenda` | Citas, tipos y listín |
| `/historial` | Auditoría y alertas |
| `/conexiones` | SMTP y conexión fiscal |
| `/configuracion` | Empresa, numeraciones, monedas y licencias |
| `/usuarios` | Usuarios, estado y perfiles de acceso |

Una ruta desconocida muestra 404 y una ruta conocida fuera del perfil muestra acceso denegado. La navegación se filtra por rol, pero los servicios siguen siendo la autoridad y responden 403 si se fuerza una API sin permiso. El resumen económico ofrece comparativas reales y no inventa series. La barra lateral de escritorio se redimensiona, se compacta y se oculta al reducirla por debajo del umbral; guarda el ancho localmente y admite teclado. La UI incluye selector ES/EN persistente, envía `Accept-Language` y usa formatos `es-ES` o `en-GB`. Las reglas y validaciones devueltas por backend siguen redactadas mayoritariamente en español.

Mantén la estética empresarial vigente: fondos cálidos, títulos Georgia, verdes profundos, bordes finos y símbolo lineal de pera, navegación clara, animación contenida y responsive móvil/escritorio. Añade estados de carga, vacío y error, padding interior coherente, etiquetas accesibles, foco visible y navegación por teclado. Los menús desplegables y calendarios deben renderizarse completos por encima de su contenedor. Las rutas reales y sus perfiles están en `frontend/src/routing/routes.tsx`; las páginas se cargan de forma diferida.

## 11. Seguridad, tenancy y almacenamiento

- Obtén siempre el tenant del claim firmado `company_id`; nunca confíes en un `companyId` del cuerpo.
- Toda consulta por ID, listado y mutación debe filtrar por empresa. Una referencia de otro tenant se trata como inexistente.
- Los permisos se convierten en authorities Spring; los servicios, no solo el gateway, aplican la autorización fina.
- El endpoint interno de auditoría exige `X-PERA-SERVICE-KEY` y comparación constante.
- Los metadatos de auditoría rechazan secretos y tienen límite de tamaño.
- Logos y documentos usan namespaces `companies/{companyId}/...`, bloquean traversal y symlinks y se limpian de forma coordinada con la transacción.
- Docker Compose solo publica PostgreSQL, gateway y frontend; los servicios 8081–8087 quedan internos mediante `expose`.
- HS256 y claves compartidas son apropiados solo para desarrollo. Producción necesita TLS, secretos externos y preferiblemente OIDC o claves asimétricas rotables.
- Una configuración de gateway licenciada corresponde actualmente a una sola empresa (`PERA_LICENSE_COMPANY_ID`).
- Las credenciales SMTP y certificados fiscales requieren conservar su clave de cifrado. Nunca registrar secretos, exportar certificados privados o publicar archivos de `.runtime`.

## 12. Persistencia y migraciones

- PostgreSQL es obligatorio. Cada servicio propietario tiene su base lógica.
- Flyway es la única vía para cambiar esquemas. Hibernate usa `ddl-auto=validate`.
- Las migraciones publicadas son inmutables; añade una versión nueva.
- No hagas joins, claves foráneas ni relaciones JPA entre bases de servicios.
- Usa UUID para referencias externas y snapshots para conservar el histórico.
- Usa `BigDecimal`, nunca `double` o `float`, para dinero y cantidades precisas.
- No elimines columnas, tablas o datos heredados sin autorización y plan de migración.

## 13. Arranque local en Windows

Requisitos: JDK 21, Maven 3.9+, Node compatible con Vite 8 y PostgreSQL 17+.

```powershell
.\scripts\start-local.ps1
```

El script:

- crea o reutiliza `.runtime/postgres` en el puerto `15432` (valor actual de `scripts/start-local.ps1`);
- provisiona las siete bases;
- usa `.runtime/company-logos` y `.runtime/shipment-documents`;
- compila backend y frontend salvo `-SkipBuild`;
- levanta ocho procesos Java y Vite;
- espera los health checks.

Arranque rápido tras una verificación previa:

```powershell
.\scripts\start-local.ps1 -SkipBuild
```

Parada conservando datos:

```powershell
.\scripts\stop-local.ps1
```

Acceso de desarrollo:

- Frontend: `http://localhost:5173`.
- Gateway: `http://localhost:8080`.
- Health: `http://localhost:8080/actuator/health`.
- Contraseña común de demostración: `ChangeMe123!`.

| Usuario | Perfil | Acceso principal |
|---|---|---|
| `admin` | Propietario | Todo, incluida la protección del perfil propietario. |
| `administracion` | Administrador | Todo y gestión de usuarios no propietarios. |
| `economia` | Economía | Clientes, ventas, comisiones, finanzas, cartera, contabilidad, Veri*Factu y reclamaciones. |
| `logistica` | Logística | Proveedores, compras, almacén, logística, procesos y reclamaciones. |
| `catalogo` | Catálogo | Productos, jerarquía, precios, impuestos y embalajes. |

Estas credenciales y secretos de ejemplo nunca se reutilizan en producción.

Agenda e informes según permisos están disponibles para los cinco perfiles. Logística puede leer clientes para seleccionar referencias, aunque la pantalla de Clientes no aparece en su navegación. Nuevos permisos requieren volver a iniciar sesión para renovar el JWT.

## 14. Docker Compose y licencias

```powershell
if (-not (Test-Path infra/.env)) { Copy-Item infra/.env.example infra/.env }
docker compose --env-file infra/.env -f infra/docker-compose.yml up --build
```

Compose incluye provisionador idempotente para volúmenes PostgreSQL existentes, health checks, dependencias saludables y volúmenes persistentes para datos, logos y documentos de expedición.

La sesión del 10 de octubre se arrancó de forma nativa con PostgreSQL 17 para conservar los datos locales existentes. Docker CLI está instalado y Compose se validó sintácticamente; no se validó la ejecución completa en contenedores. La nota antigua sobre virtualización deshabilitada describe una limitación histórica, no un diagnóstico actual. El volumen de Compose y `.runtime/postgres` son almacenes distintos: no cambiar de despliegue esperando ver automáticamente los mismos datos.

Para activar una licencia sin exponer el código en la línea de comandos:

```powershell
.\scripts\activate-license.ps1
```

Guarda el token resultante en un gestor de secretos y configura las variables `PERA_LICENSE_*` del gateway.

## 15. Verificación obligatoria

Antes de declarar completo un cambio relevante:

```powershell
mvn -f backend/pom.xml clean verify

Set-Location frontend
npm ci
npm test -- --run
npm run build
npm audit --omit=dev

Set-Location ..
docker compose --env-file infra/.env.example -f infra/docker-compose.yml config --quiet
```

Después prueba el flujo afectado mediante API o navegador y, si toca persistencia, sobre PostgreSQL real. Comprueba health de 8080–8087 en arranque local y la SPA en 5173.

No mantengas en este archivo un total de pruebas que no proceda de un `clean verify` y un Vitest ejecutados sobre el mismo estado del árbol. Los resultados deben actualizarse después de la verificación final, no inferirse contando anotaciones o reportes antiguos.

GitHub Actions solo verifica actualmente el backend. No hay todavía suite E2E automatizada ni Testcontainers completos de aislamiento multiempresa.

## 16. Convenciones de implementación

Backend:

- Organiza por dominio funcional y conserva controladores delgados.
- Usa DTOs, Bean Validation y reglas en servicios; no expongas entidades JPA.
- Devuelve errores mediante `ProblemDetail`; no ocultes excepciones con `try/catch` vacíos.
- Mantén códigos maestros inmutables salvo proceso explícito de renumeración.
- Protege reglas críticas y aislamiento multiempresa con pruebas.
- Evita exponer nuevos contratos paginados dependientes de la forma interna de `PageImpl`; usa respuestas estables.

Frontend:

- Componentes funcionales y TypeScript estricto.
- Centraliza HTTP en `frontend/src/lib/api.ts`; consume rutas relativas `/api`.
- Conserva el router History API salvo decisión técnica explícita.
- Localiza en ES/EN cualquier texto nuevo y usa los formatters compartidos.
- No introduzcas dependencias visuales grandes para resolver componentes simples.
- Al editar líneas comerciales, conserva los indicadores explícitos de override de precio e IVA hasta enviar el payload; cambiar de producto restablece esos indicadores y carga los valores automáticos del nuevo producto.
- Revisa el resultado visual a ancho de escritorio y móvil cuando cambies formularios, tablas, modales, navegación, `overflow` o `z-index`.
- Actualiza pruebas de componentes/utilidades cuando cambie comportamiento.

Trabajo en repositorio:

- Preserva cambios ajenos y revisa el diff antes de editar.
- Usa cambios mínimos y migraciones aditivas.
- No borres, resetees ni sobrescribas trabajo sin autorización.
- No confirmes `.env`, tokens, contraseñas reales, `.runtime`, logs ni binarios generados.
- No hagas commit, push, despliegues externos ni cambios en Trello salvo petición explícita vigente.
- Actualiza documentación cuando cambien contratos, arquitectura o comportamiento.

## 17. Legado congelado y núcleo horizontal

No borres ni desarrolles nuevas dependencias alrededor de estos elementos sin decisión explícita:

- `WorkSite`.
- `WorkSiteRepository`.
- tabla `work_sites`.
- `CustomerProfile.calculationMultiplier`.
- columna `calculation_multiplier`.

`calculationMultiplier` sigue apareciendo en DTOs de cliente por compatibilidad, pero está obsoleto y no participa en pricing, ventas, finanzas, identidad, operaciones ni gateway. `WorkSite` no tiene controlador activo.

No existen modelos activos de vidrio, espesores, planchas, cortes, canteados, templados o laminados. Una futura vertical debe vivir fuera del núcleo y comunicarse mediante IDs, eventos o puertos estables.

Capacidades como logística, rutas, albaranes, workflows, partes de trabajo y unidades metro/m²/m³ sí son horizontales y deben conservarse.

## 18. Limitaciones conocidas

PERA es una plataforma funcional en desarrollo, no un ERP listo para producción.

- Compras e inventario operativo existen. Faltan reservas, valoración, mínimos, lotes, series, ubicaciones, recepciones parciales y agrupación de albaranes.
- La contabilidad cubre plan base, asientos equilibrados y facturas de venta. Compras, cartera y caja no se integran en un circuito contable completo; sin pagos a proveedores, conciliación, cierres, amortizaciones ni libros oficiales.
- No hay mandatos SEPA ni fichero bancario ni conexión bancaria real. No hay cobros parciales de recibo; caja no guarda moneda ni revierte devoluciones automáticamente.
- Notificar cobros a Sales no tiene cola durable y ocurre antes del commit de Finance; revisar avisos de fallo y posibles divergencias entre servicios.
- La sincronización de salidas usa `updatedAt`: editar el comercial de una factura antigua puede hacerla aparecer como entrega nueva. Incidencia pendiente, detallada abajo.
- Compras, expediciones y recibos todavía aceptan ciertos snapshots aportados por el cliente de la API sin contrastarlos con el servicio propietario.
- Veri*Factu tiene remisión real implementada, pero faltan subsanación/anulación y requisitos para comercializar con garantía fiscal; no equivale a SII ni factura B2B.
- El platform admin y la asignación de `platform:companies:manage` no están provisionados por un flujo normal.
- Las respuestas de validación y reglas backend siguen principalmente en español.
- El gateway licenciado representa una instalación de una sola empresa; features declaradas aún no habilitan o bloquean rutas individuales.
- El outbox general de eventos de Sales no tiene publicador ni broker; no confundir con las colas específicas que sí se procesan.
- La auditoría del gateway sigue siendo best-effort en otros dominios; remesas tiene auditoría transaccional y reintentos. Esto no convierte toda la aplicación en una auditoría garantizada al 100 %. Falta definir retención/archivado del outbox de remesas publicado.
- Solo existe el canal de alerta `IN_APP`; no hay email, webhook, SMS ni push.
- Las líneas de expedición reciben IDs y snapshots de producto/documento del cliente; todavía no se contrastan con master-data o sales.
- Los cinco perfiles base son fijos; todavía no existe un diseñador de roles/permisos personalizados desde la UI.
- No hay Testcontainers/E2E completos y CI no verifica frontend.
- Docker se ha validado sintácticamente, no ejecutado en esta máquina.

## 19. Documentación útil

- `README.md`: estado y arranque rápido.
- `docs/01-vision-general.md`: visión.
- `docs/02-requisitos-funcionales.md`: requisitos base.
- `docs/03-arquitectura.md`: decisiones de límites; contrasta cualquier cifra histórica con este archivo y Compose.
- `docs/04-modelo-datos.md`: modelo inicial.
- `docs/05-api-rest.md`: contratos iniciales; los controladores son la referencia actual para rutas nuevas.
- `docs/06-decisiones-tecnicas.md`: decisiones registradas.
- `docs/07-roadmap-y-preguntas.md`: pendientes de producto.
- `docs/08-generalizacion-erp-horizontal.md`: abandono de la vertical de cristalería.
- `docs/09-inventario-clases.md`: inventario auditado.
- `docs/10-frontend-mvp.md`: dirección visual y UX.
- `docs/11-ampliacion-plataforma.md`: matriz AMP-01 a AMP-21 y evidencia de la ampliación.
- `docs/12-conexiones-correo.md`: SMTP, cifrado, colas, estados y datos de terceros.
- `docs/13-verifactu.md`: análisis y plan fiscal; contrastar estado con implementaciones posteriores y fuentes oficiales.
- `docs/18-integraciones-pruebas.md`: pruebas de integración externa.
- `docs/19-mapa-migracion-dimprocristalwin.md`: migración funcional; guía histórica, no orden de implementar todo.
- `docs/20-compras-inventario.md`: compras, almacén y sincronización de salidas.
- `docs/21-cartera-y-caja.md`: recibos, remesas, caja y límites.
- `docs/22-reclamaciones-y-agenda.md`: reclamaciones, agenda y listín.
- `docs/23-ficha-de-cliente.md`: ficha, clasificación y comerciales; su nota antigua «sin comisiones» quedó superada por el doc 25.
- `docs/24-verifactu-remision-automatica.md`: cola fiscal, reintentos y entornos.
- `docs/25-comisiones.md`: cálculo, precedencia, liquidación y límites.
- `docs/26-inventario-dimprocristalwin.md`: comparación con legado, no especificación contractual.
- `docs/27-remesas-auditoria-y-revision.md`: último cambio, evidencia real y propuestas de producto.

## 20. Checklist de entrega para asistentes

1. Identifica el flujo completo afectado: API, servicio, persistencia, permisos, frontend y pruebas.
2. Expón supuestos que puedan cambiar alcance o datos.
3. Implementa el cambio mínimo coherente con los límites existentes.
4. Añade pruebas proporcionales al riesgo y una migración nueva si cambia esquema.
5. Ejecuta verificaciones, prueba el comportamiento real y revisa aislamiento multiempresa.
6. Revisa `git diff`, artefactos y secretos.
7. Actualiza documentación y este archivo si cambia el contexto estructural.
8. Entrega un resumen honesto de cambios, evidencia y pendientes.

## 21. Incidente histórico cerrado: precio e IVA manuales

El 10 de agosto de 2026 se detectó que un presupuesto introducido con un precio superior y un IVA distinto acababa guardado como `100 + 21 % = 121`. La causa era que `SalesMasterDataService` ignoraba siempre los valores editados en el navegador y reaplicaba precio base e impuesto maestro. La corrección vigente es la descrita en Ventas y presupuestos: resolución automática por defecto y override manual explícito cuando el usuario modifica precio o IVA.

El mismo cambio añadió `DELETE /api/v1/quotes/{id}` limitado a borradores y selección múltiple en `/presupuestos`. La comprobación real usada como criterio fue `525,00 €` de base y `10 %` de IVA, con resultado `577,50 €`; el borrado de un borrador temporal respondió `204`. No uses esas cifras como datos de negocio ni borres presupuestos existentes durante una prueba visual.

## 22. Ampliación del 22 de septiembre de 2026

- Conexiones SMTP por empresa en `/conexiones`, acceso OWNER/ADMIN, asistente y credenciales cifradas. Presupuestos con envío manual; facturas expedidas con envío automático opt-in y PDF, incluido QR Veri*Factu cuando existe. Cola persistente con protección contra duplicados y revisión explícita de entregas inciertas.
- Clientes, proveedores y transportistas admiten dirección postal y cuenta IBAN opcional. Los documentos nuevos conservan dirección/correo históricos.
- Estética inspirada en FinanX: títulos Georgia, fondos cálidos, verdes profundos y bordes finos; distribución conservada. Esta petición sustituye la preferencia previa de verde vivo en superficies, conservando la pera como símbolo.
- Contratos, clave de cifrado, semántica de estados y límites en `docs/12-conexiones-correo.md`. El correo comercial no amplía los canales de alertas, que siguen siendo IN_APP.

## 23. Remesas y auditoría del 10 de octubre de 2026

- `send` de remesas registra una presentación manual fuera de PERA; no conecta con un banco ni genera ficheros. UI: «Registrar presentación» / «Presentación registrada».
- Finance V5 persiste la auditoría de altas, ediciones, presentación, abono, anulación y devoluciones asociadas junto al cambio local. El publicador reintenta hacia Activity con el mismo eventId y clave interna, sin duplicar alertas. El gateway conserva los fallos y omite estos éxitos ya auditados. Los otros dominios siguen con auditoría best-effort.
- Finance V6 recupera explícitamente estados previos no borrador como `RECOVER_STATE`, con fecha de recuperación y autor original desconocido; nunca inventar actor o transmisión bancaria. Los eventos publicados se conservan en el outbox; falta definir su archivado.
- Historial filtra por recurso, busca números de remesa y muestra el importe y los recibos en detalle. Alcance, límites y propuestas de revisión en `docs/27-remesas-auditoria-y-revision.md`.

## 24. Handoff operativo para Claude — 10 de octubre de 2026

### Estado de Git y trabajo que hay que conservar

- Directorio de trabajo actual: `C:\Users\USUARIO\Desktop\CODE\pera_erp`.
- Rama actual `main`, HEAD `287f387` (`v20261009.Raul.7`). Se hizo pull con avance limpio desde `a77e2aa`; no se han hecho commits ni pushes de los cambios de esta sesión.
- Hay cambios deliberados sin confirmar y archivos nuevos sin seguir. **Untracked no significa basura.** Antes de un nuevo pull, revisar el diff y conservarlos; no hacer reset, clean, checkout de archivos ni stash indiscriminado.
- `AGENTS.md` está ignorado por Git en este checkout. Se entrega además una copia portable en `docs/AGENTS-HANDOFF-2026-10-10.md`. La raíz es la guía operativa vigente; la copia es una fotografía de esta entrega.
- La petición actual es entregar este contexto. Las ideas de mejora que se enumeran abajo no son autorización para implementarlas todas ni para cambiar datos.

Los cambios de aplicación pendientes corresponden al bloque de remesas y auditoría:

| Área | Archivos y propósito |
|---|---|
| Finance | `remittance/RemittanceAuditRecorder.java`, `RemittanceAuditPublisher.java`, `RemittanceService.java`, `receivable/ReceiptService.java`: persistencia transaccional del evento y publicación con reintentos. |
| Migraciones Finance | `V5__remittance_audit_outbox.sql` y `V6__recover_existing_remittance_states.sql`. Ya aplicadas sobre el PostgreSQL local; no editar su contenido ni reutilizar versión. |
| Configuración | `FinanceServiceApplication.java`, `finance-service/src/main/resources/application.yml` e `infra/docker-compose.yml`: scheduling y URL interna de Activity. |
| Gateway | `GatewayAuditEventFactory.java`: evita duplicar éxitos de remesas ya auditados y conserva intentos fallidos. |
| Activity | `audit/AuditService.java`: búsqueda en metadata segura para localizar números de remesa. |
| Frontend | `components/collections/RemittancesTab.tsx`, `lib/collections.ts`, `pages/HistoryPage.tsx` y `styles/index.css`: significado honesto de presentación, filtros y detalle de historial. |
| Pruebas | Pruebas de recorder/publisher, servicios de recibos/remesas, factory del gateway, `HistoryPage.test.tsx` y `CollectionsPage.test.tsx`. |
| Documentación | `README.md`, `docs/README.md`, `docs/21-cartera-y-caja.md`, `docs/27-remesas-auditoria-y-revision.md` y este handoff. |

Las rutas Java de la tabla son relativas al paquete propietario bajo `backend/<servicio>/src/main/java/com/peraerp/`; las migraciones están en `src/main/resources/db/migration`. Consultar `git status --short` para el listado exacto, que puede evolucionar después de esta fotografía.

### Contrato de la auditoría nueva que no debe degradarse

- El recorder participa obligatoriamente en la transacción de negocio de Finance. Guardar el cambio y su evento debe ser atómico; no sustituirlo por una llamada HTTP sin persistencia.
- Acciones: `CREATE`, `UPDATE`, `SEND`, `SETTLE`, `CANCEL` y `RETURN` asociado a remesa. Tenant y actor proceden del contexto autenticado; metadata con número, importe, moneda, cantidad de recibos, estados y fechas, sin cuentas bancarias ni credenciales.
- El publisher procesa lotes limitados con bloqueo `FOR UPDATE SKIP LOCKED`. Publica por endpoint interno autenticado, marca enviado solo después de confirmación y reintenta con espera creciente, manteniendo el mismo `eventId`.
- Activity ingiere idempotentemente por ID de evento: reintentar una entrega incierta no debe duplicar historial ni alertas.
- `bankTransmission: false` declara que la acción es un registro local. No cambiar ese valor para aparentar una integración bancaria.
- V6 registra `RECOVER_STATE` de estados antiguos no borrador con fecha de recuperación y actor desconocido. No es reconstrucción del acto original ni prueba de quién lo ejecutó.
- Los eventos ya publicados siguen en el outbox. Su retención/archivado requiere una decisión y migración/proceso específico; no borrar pendientes.
- Las alertas de presentación, abono y anulación configuradas en la empresa demo son reglas `IN_APP`; no se han creado alertas retroactivas para fingir avisos antiguos.

### Servidores, datos y secretos locales

- Se dejó el stack nativo arrancado: PostgreSQL 17 en `15432`, Java en `8080`–`8087`, Vite en `5173`. Revalidar health si ha pasado tiempo; no asumir que siguen vivos tras reinicio de Windows.
- La empresa usada es **PERA ERP Demo**, con usuario de demostración `admin` (OWNER). La contraseña de demo se indica en la sección 13; no sirve como credencial de producción.
- Bases y almacenamiento existentes viven en `.runtime`. No borrar ni recrear `.runtime/postgres`, logos, adjuntos, certificados o datos para conseguir un arranque limpio.
- El script conserva secretos locales en `.runtime/jwt-secret.key`, `.runtime/internal-service.key` y `.runtime/mail-encryption.key`. No mostrar su contenido ni regenerarlos: pueden invalidar sesiones, llamadas internas o descifrado.
- Copias completas previas: `.runtime/backups/before-pull-startup-20261010-172442.sql` y `.runtime/backups/before-remittance-audit-20261010.sql`. No restaurarlas encima del estado actual sin una petición y plan concretos; contienen datos anteriores a cambios del usuario.
- En Windows, un `clean` puede fallar por archivos generados de `target` con atributo ReadOnly o bloqueados por Java. Se resolvió quitando ReadOnly únicamente dentro de los `target` verificados y deteniendo procesos propios cuando correspondía; nunca borrando fuentes ni datos.
- Invocar `start-local.ps1` directamente. En esta sesión redirigir toda su salida a un archivo hizo quedar esperando el arranque alrededor de `pg_ctl`; los logs individuales ya los gestiona el script.
- No detener procesos ajenos ni matar todos los Java/Node de la máquina. Identificar los procesos de este checkout y usar los scripts del proyecto.

### Datos de demostración ya creados: no duplicarlos

Se rellenaron módulos vacíos con datos coherentes usando maestros existentes. Los datos pueden haber cambiado desde la carga; volver a consultar la aplicación antes de actuar.

| Módulo | Datos añadidos |
|---|---|
| Compras | 15 documentos: 5 pedidos, 5 albaranes de entrada y 5 facturas de proveedor; 4 facturas confirmadas y 1 borrador. |
| Almacén | 2 almacenes `DEMO-CENTRAL` y `DEMO-EXPEDICION`, 15 productos existentes inventariados, 18 fichas de existencias y 37 movimientos tras la carga. |
| Salidas de venta | 5 albaranes nuevos `ALB-2026-000003` a `ALB-2026-000007` con salida registrada. |
| Cartera | 6 vencimientos y 6 recibos sobre 2 facturas pendientes ya existentes; 2 remesas. |
| Caja | 2 cajas y 6 movimientos manuales; una sesión abierta con 152,50 EUR y otra cerrada con 82,00 EUR, diferencia cero. |
| Comisiones | 2 comerciales demo, 3 reglas y 3 comisiones: 2 pendientes y 1 liquidada. Se añadió el comercial a 3 facturas existentes sin alterar líneas, importes, fechas ni estado. |
| Logística | 2 transportistas, 2 vehículos, 2 rutas y 3 expediciones en estados planificada, preparada y entregada. |
| Reclamaciones | 2 reclamaciones, una abierta con seguimiento el 13/10/2026 y otra cerrada con resolución y comentario. |

- Clientes, proveedores y productos existentes no se rellenaron de nuevo ni se modificaron como parte de la carga.
- Las comisiones demo corresponden a facturas de agosto/septiembre. El filtro inicial de octubre puede ocultarlas; ampliar Desde antes de concluir que no hay datos.
- `REM-2026-0001` permanece en borrador en la última comprobación. **El usuario registró la presentación de `REM-2026-0002`**, que quedó `SENT`, fecha 10/10/2026, importe 847 EUR y 3 recibos. No hubo envío bancario real.
- Las cuentas de remesa demo indican expresamente que no existe conexión bancaria. No usarlas para enviar dinero, crear mandatos reales o probar servicios externos.
- Se crearon reglas demo de alerta `REM_PRESENTATION`, `REM_SETTLEMENT` y `REM_CANCELLATION`, de tipo `BUSINESS_ACTIVITY`, recurso `REMITTANCE`, canal `IN_APP` y condición `bankTransmission = false`.
- **Contabilidad se conservó sin cambios**: comparación con backup de 9 cuentas, 1 asiento y 3 líneas, repetida tras el arreglo de auditoría.

Artefactos locales de la carga y comprobaciones:

- `.runtime/demo-seed-20261010-resumen.md`: resumen de carga original; su nota «dos remesas en borrador» precede a la presentación posterior del usuario y quedó superada por este handoff.
- `.runtime/seed-demo-20261010.mjs` y `seed-demo-20261010-state.json`: script y checkpoints. No volver a ejecutar la carga indiscriminadamente ni restaurar estados guardados sobre cambios posteriores.
- `.runtime/demo-seed-before-20261010.json` y `demo-seed-after-20261010.json`: instantáneas de comprobación, no fuente actual de negocio.
- `.runtime/accounting-after-demo-20261010.sql`: comprobación contable.
- `.runtime/check-remittance-audit.mjs` y `remittance-audit-live-check.json`: prueba real de cola e idempotencia.
- `.runtime/qa-generated/revision-pantallas-20261010.json` y `historial-remesas-corregido-20261010.jpg`: revisión de pantallas y evidencia visual.

Estos archivos están ignorados y pueden contener datos de negocio: no versionarlos ni compartir el directorio entero.

### Incidencia de almacén pendiente de corregir

La sincronización desde Sales usa `updatedAt`. Cambiar el comercial de facturas antiguas para calcular comisiones hizo aparecer referencias históricas como si fueran entregas recientes:

- `FAC-2026-000002`: sin producto inventariado, quedó `NOT_APPLICABLE`.
- `FAC-2026-000003`: salida pendiente de 200 unidades frente a 54 disponibles. Se **descartó esa salida histórica sin generar movimiento**, evitando que una reposición futura descuente de nuevo mercancía antigua.

Tras la carga había 7 referencias de salida: 5 `POSTED`, 1 `DISMISSED` y 1 `NOT_APPLICABLE`. El código de esta incidencia no se corrigió. No reactivar ni contabilizar la referencia descartada como prueba. Una corrección futura debe distinguir confirmación/entrega de ediciones administrativas, respetar el corte de activación y proteger contra dobles salidas con pruebas.

### Autorizaciones y límites de la sesión que se heredan

- El usuario autorizó crear datos demo en compras, almacén, salidas, diario, comisiones, recibos, remesas, caja, logística y reclamaciones.
- Autorizó efectos o dependencias necesarias en Finanzas, como generar los vencimientos requeridos por recibos/remesas. Eso no autoriza cambiar formas de pago, monedas, tipos de cambio o parámetros arbitrarios.
- **Pidió expresamente no tocar contabilidad** durante la carga/revisión. No crear, borrar, recalcular ni modificar asientos o cuentas sin una petición posterior que autorice ese trabajo. Consultar documentación/código no modifica datos.
- Pidió poder ver actuaciones importantes en Historial y alertas. Se corrigió remesas; no afirmar que todos los módulos tienen ya la misma garantía durable.
- No autorizó correos externos, movimientos bancarios reales, cambios en AEAT de producción, commits, pushes, despliegues externos ni cambios en Trello.
- La solicitud de este handoff no autoriza nuevas cargas ni cambios funcionales. Una nueva petición concreta puede ampliar el alcance; aplicar entonces las reglas del proyecto y preservar datos existentes.

### Última verificación funcional completa

Ejecutada el 10/10/2026 sobre el árbol con la corrección de remesas, antes de esta actualización documental:

- `mvn -f backend/pom.xml clean verify`: 590 pruebas, módulos correctos.
- `npm ci`, `npm test -- --run`: 34 archivos, 118 pruebas correctas.
- `npm run build`: correcto. `npm audit --omit=dev`: cero vulnerabilidades notificadas.
- Docker Compose `config --quiet`: correcto; no equivale a arranque real en Docker.
- Migraciones Finance V5/V6 aplicadas sobre PostgreSQL real; health `8080`–`8087` correcto y frontend disponible en `5173`.
- Prueba de caída de Activity: edición sin cambio de contenido de remesa en borrador, evento pendiente con reintentos, reinicio de Finance y recuperación de Activity; un único evento central, actor/número correctos y sin duplicado del gateway. Remesas y recibos quedaron iguales antes/después.
- V6 recuperó el estado previo de la remesa presentada sin inventar actor original. Se revisaron el historial y el modal de presentación en escritorio y móvil de 390 px.
- Se consultaron las pantallas principales de la aplicación; no se enviaron correos, órdenes bancarias ni remisiones fiscales reales durante esa revisión.

Estas cifras describen esa ejecución. No reutilizarlas como pruebas de cambios posteriores. Para esta entrega de documentación no se volvió a compilar la aplicación.

### Mejoras propuestas, aún sin implementar

1. Panel de trabajo pendiente con cobros vencidos, seguimientos de reclamación, salidas bloqueadas y expediciones próximas.
2. Vencimientos/pagos a proveedores y conexión explícita entre compras, finanzas y contabilidad.
3. Conciliación bancaria; después, mandatos y ficheros SEPA con validaciones y confirmación clara de cada paso real.
4. Stock mínimo, reposición, reservas y recepciones/salidas parciales.
5. Cronología enlazada presupuesto → albarán → factura → recibo → remesa → reclamación, con permisos por dominio.
6. Extender auditoría durable a más operaciones críticas y definir retención de colas publicadas.
7. Corregir primero la detección de entregas basada en `updatedAt` antes de ampliar automatismos de stock.

Antes de implementar una idea, concretar su alcance con la petición vigente. No convertir este listado en un mandato de reescribir la aplicación.
