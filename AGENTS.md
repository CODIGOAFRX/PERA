# AGENTS.md — Contexto canónico de PERA ERP

> Última actualización: 10 de agosto de 2026.
> Este archivo es el punto de entrada para Codex, Claude, ChatGPT y cualquier persona que trabaje en el repositorio.

## 1. Cómo usar este documento

Antes de analizar o modificar PERA:

1. Lee este archivo completo.
2. Lee `README.md`.
3. Consulta `docs/README.md` y abre solo la documentación relacionada con la tarea.
4. Ejecuta `git status --short` y revisa el historial reciente.
5. Contrasta siempre este resumen con controladores, servicios, migraciones y pruebas. El código es la fuente final sobre lo implementado.

Si se comparte este archivo sin el repositorio, debe tratarse como una fotografía operativa, no como material suficiente para implementar cambios con seguridad. No inventes contratos, requisitos ni resultados de pruebas ausentes.

## 2. Producto y dirección

PERA es un ERP horizontal y multiempresa para pequeñas y medianas empresas, construido con Java, Spring Boot, React y PostgreSQL.

El proyecto nació del estudio de un ERP antiguo en Visual FoxPro usado por una empresa de cristalería. Esa referencia es histórica y ya no define el producto. El núcleo debe:

- servir a comercio, distribución y servicios de distintos sectores;
- usar lenguaje y modelos generalistas;
- mantener límites de dominio claros y microservicios de granularidad gruesa;
- permitir futuras verticales mediante extensiones desacopladas;
- priorizar mantenibilidad, pruebas, seguridad y evolución gradual.

El contenido de `docs/reference/` no es una especificación contractual. No copies reglas de vidrio, construcción u otro sector al núcleo.

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
| `master-data-service` | Clientes, proveedores, catálogo, jerarquía, impuestos, tarifas, pricing y embalajes. |
| `sales-service` | Documentos comerciales, presupuestos, numeraciones, cálculos y snapshots. |
| `finance-service` | Formas de pago, vencimientos, monedas, cambios y conversión. |
| `operations-service` | Workflows, transportistas, vehículos, rutas, fletes, expediciones y adjuntos. |
| `activity-service` | Historial central, exportación CSV, retención y alertas. |
| `licensing-service` | Emisión, activación, validación, rotación, suspensión y revocación de licencias. |

### Dependencias entre servicios

- Sales consulta master-data para validar clientes/productos y resolver precios; consulta identity para la moneda base y finance para el cambio aplicable.
- El gateway envía auditoría a activity y valida la licencia contra licensing.
- Operations conserva UUID y snapshots de referencias externas; no comparte entidades con maestros o ventas.
- Cada servicio propietario aplica transacciones ACID locales. No hay transacciones distribuidas.
- El outbox de ventas está persistido, pero todavía no existe publicador ni broker.

## 6. Tecnologías fijadas

Backend:

- Java 21, Spring Boot 4.1.0 y Spring Cloud 2025.1.2.
- Maven multimódulo, Spring Data JPA, Spring Security, JWT HS256, Bean Validation y Flyway.
- PostgreSQL 17, Actuator y Springdoc OpenAPI 3.0.3.
- JUnit 5 y Mockito; sin Lombok.

Frontend:

- React 19.2.8, TypeScript 7.0.2, Vite 8.2.1 y Vitest 4.1.10.
- Testing Library y Lucide React.
- Router propio basado en History API; no se usa React Router.
- Nginx sirve la SPA de producción y reenvía `/api/` al gateway.

No actualices dependencias indiscriminadamente. Justifica cualquier actualización y verifica compatibilidad, pruebas y build.

## 7. Capacidades implementadas

### Identidad y empresa

- Login con selección de empresa y JWT que contiene `company_id`, roles y permisos.
- Consulta de la empresa activa, actualización de sus datos y creación de usuarios dentro del tenant activo.
- Parámetros: país, locale, zona horaria, moneda base, nombre visible, correos, teléfono, web y dirección.
- Logo PNG/JPEG/WebP de hasta 2 MiB con firma real, SHA-256, ETag y almacenamiento aislado por empresa.
- La creación de nuevas empresas exige `platform:companies:manage`; no existe todavía un platform admin provisionado automáticamente.

### Maestros, impuestos, precios y embalajes

- Consulta, alta y edición de clientes, proveedores y productos. Sus códigos son inmutables tras el alta.
- Jerarquía `naturaleza -> supertipo -> tipo -> grupo`, validada dentro de la empresa y asociable a productos.
- Códigos fiscales por empresa con país ISO, porcentaje, vigencia, exención y estado.
- Tipos de embalaje y configuraciones por producto: unidades, niveles, dimensiones, pesos, volumen, retornable y predeterminado.
- Tarifas buscables por texto, cliente, naturaleza, supertipo, tipo, ámbito, estado y vigencia.
- Líneas de tarifa y reglas por naturaleza, supertipo, tipo, grupo o producto, opcionalmente específicas de cliente.
- Simulador/resolución de precio con traza reproducible.

### Ventas y presupuestos

- Documentos `QUOTE`, `SALES_ORDER`, `DELIVERY_NOTE`, `INVOICE` y `WORK_ORDER`.
- Creación, listado, detalle, conversión compatible y estado de cobro de factura.
- Módulo dedicado de presupuestos: borrador, envío, aceptación, rechazo, caducidad y conversión.
- Numeraciones por empresa y tipo, series y patrones con `{yyyy}`, `{yy}`, `{MM}`, `{dd}`, `{series}` y `{seq:N}`; reinicio anual, mensual, diario o nunca.
- Resolución fiable de cliente, producto y tarifa desde master-data: el navegador no decide sus snapshots.
- Snapshots de cliente, producto, tarifa/traza, cantidad solicitada/facturada, moneda/tipo de cambio e impuesto. La migración sales V6 conserva ID, código, nombre, país, porcentaje y exención fiscal.
- Outbox transaccional persistido para eventos de dominio.

### Finanzas y monedas

- Formas de pago y generación idempotente de vencimientos.
- Catálogo ISO de monedas con una moneda base por empresa.
- Tipos de cambio fechados y con fuente; resolución directa o inversa y redondeo según moneda destino.
- Documentos comerciales guardan moneda base, cambio, fecha, fuente e importes convertidos.
- Recibos, remesas, riesgo, movimientos y caja tienen modelos iniciales, pero no flujos completos.

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
- Retención automática habilitada por defecto a 365 días, en lotes y mediante la migración activity V2.
- Reglas de alerta con evento, acción/recurso, condiciones, severidad, plantillas y cooldown.
- Canal soportado actualmente: `IN_APP`. Las ocurrencias se pueden reconocer y resolver.

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

- Identity: `/api/v1/auth`, `/companies`, `/users`, `/company-settings`.
- Master-data: `/customers`, `/suppliers`, `/products`, `/product-natures`, `/product-supertypes`, `/product-types`, `/product-groups`, `/tax-codes`, `/tariffs`, `/pricing`, `/packaging-types`, `/product-packaging`.
- Sales: `/documents`, `/quotes`, `/numbering-schemes`.
- Finance: `/payment-methods`, `/due-dates`, `/currencies`, `/exchange-rates`, `/currency-conversions`.
- Operations: `/workflow-templates`, `/work-executions`, `/carriers`, `/vehicles`, `/delivery-routes`, `/freight-rates`, `/shipments`.
- Activity: `/history`, `/alert-rules`, `/alerts`.
- Licensing: `/api/v1/licenses` y `/public/v1/licenses`.

Consulta los controladores para método, filtros y payload exactos. No inventes rutas a partir de este índice.

## 10. Frontend

Rutas canónicas autenticadas:

| Ruta | Pantalla |
|---|---|
| `/` | Resumen |
| `/clientes` | Clientes |
| `/proveedores` | Proveedores |
| `/catalogo` | Productos |
| `/maestros` | Jerarquía, impuestos, tarifas y embalajes |
| `/presupuestos` | Presupuestos |
| `/ventas` | Documentos comerciales |
| `/finanzas` | Pagos y vencimientos |
| `/operaciones` | Workflows, logística, fletes y expediciones |
| `/historial` | Auditoría y alertas |
| `/configuracion` | Empresa, numeraciones, monedas y licencias |

Una ruta desconocida muestra 404. La UI incluye selector ES/EN persistente, envía `Accept-Language` y usa formatos `es-ES` o `en-GB`. Las reglas y validaciones devueltas por backend siguen redactadas mayoritariamente en español.

Mantén la estética empresarial y minimalista: blanco/crema, verde pera como acento, navegación clara, animación contenida y responsive móvil/escritorio. Añade estados de carga, vacío y error, etiquetas accesibles, foco visible y navegación por teclado.

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

- crea o reutiliza `.runtime/postgres` en el puerto `55432`;
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
- Usuario: `admin`.
- Contraseña: `ChangeMe123!`.

Estas credenciales y secretos de ejemplo nunca se reutilizan en producción.

## 14. Docker Compose y licencias

```powershell
Copy-Item infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/docker-compose.yml up --build
```

Compose incluye provisionador idempotente para volúmenes PostgreSQL existentes, health checks, dependencias saludables y volúmenes persistentes para datos, logos y documentos de expedición.

En el equipo original Docker Desktop no puede ejecutar contenedores porque la virtualización está deshabilitada. `docker compose config` sí se valida; no confundas esta limitación de la máquina con un fallo de la configuración.

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

- No hay compras ni inventario operativo, reservas o movimientos de stock.
- Contabilidad, fiscalidad legal, factura electrónica, recibos, remesas, riesgo, ledger y caja no forman flujos completos.
- El platform admin y la asignación de `platform:companies:manage` no están provisionados por un flujo normal.
- Las respuestas de validación y reglas backend siguen principalmente en español.
- El gateway licenciado representa una instalación de una sola empresa; features declaradas aún no habilitan o bloquean rutas individuales.
- El outbox no tiene publicador, consumidor ni broker.
- La auditoría del gateway es best-effort: si activity está caído, la operación de negocio no se revierte.
- Solo existe el canal de alerta `IN_APP`; no hay email, webhook, SMS ni push.
- Las líneas de expedición reciben IDs y snapshots de producto/documento del cliente; todavía no se contrastan con master-data o sales.
- El menú frontend no se filtra por permisos; el backend sigue siendo la autoridad.
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

## 20. Checklist de entrega para asistentes

1. Identifica el flujo completo afectado: API, servicio, persistencia, permisos, frontend y pruebas.
2. Expón supuestos que puedan cambiar alcance o datos.
3. Implementa el cambio mínimo coherente con los límites existentes.
4. Añade pruebas proporcionales al riesgo y una migración nueva si cambia esquema.
5. Ejecuta verificaciones, prueba el comportamiento real y revisa aislamiento multiempresa.
6. Revisa `git diff`, artefactos y secretos.
7. Actualiza documentación y este archivo si cambia el contexto estructural.
8. Entrega un resumen honesto de cambios, evidencia y pendientes.
