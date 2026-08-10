# PERA ERP

PERA es un ERP horizontal para pequeñas y medianas empresas. Usa Java 21 y Spring Boot en el backend, React + TypeScript en el frontend, PostgreSQL como base de datos y servicios con límites de dominio explícitos.

## Estado del proyecto

PERA es un monorepo de microservicios de granularidad gruesa. La plataforma actual incluye:

- `api-gateway`: entrada única, CORS, JWT, auditoría de mutaciones, enrutamiento y control de licencia.
- `identity-service`: empresas, usuarios, permisos, parámetros de empresa y almacenamiento seguro de logos.
- `master-data-service`: clientes, proveedores, productos, jerarquías, impuestos, tarifas, reglas de precio y embalajes.
- `sales-service`: numeraciones configurables, presupuestos, pedidos, albaranes, facturas, snapshots y conversiones.
- `finance-service`: formas de pago, vencimientos, monedas, tipos de cambio y conversiones reproducibles.
- `operations-service`: workflows configurables, transportistas, vehículos, rutas, fletes, expediciones y archivos enviados.
- `activity-service`: historial central, exportación CSV y alertas personalizadas.
- `licensing-service`: emisión, activación, validación periódica, suspensión y revocación de licencias.
- React con rutas propias para operación y administración, selector persistente ES/EN y formatos por idioma/moneda.
- PostgreSQL 17 con una base lógica por servicio propietario, Flyway, seguridad por permisos, Actuator y OpenAPI.

La ampliación se controla con la matriz verificable de [`docs/11-ampliacion-plataforma.md`](docs/11-ampliacion-plataforma.md). PERA sigue siendo un producto en desarrollo: no debe presentarse todavía como un ERP contable/fiscal listo para producción.

## Arranque rápido

### Docker Compose

Requisitos: Docker con Compose.

```bash
cp infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/docker-compose.yml up --build
```

La aplicación queda en `http://localhost:5173`. Las credenciales del ejemplo son `admin` / `ChangeMe123!`.

### Windows sin Docker

Requisitos: JDK 21, Maven 3.9+, Node.js 20+ y PostgreSQL 17+.

```powershell
.\scripts\start-local.ps1
```

Este script crea un clúster PostgreSQL aislado en `.runtime`, conserva sus datos entre arranques y levanta la aplicación completa. Para detenerla:

```powershell
.\scripts\stop-local.ps1
```

Para verificar cada parte manualmente:

```powershell
mvn -f backend/pom.xml clean verify
cd frontend
npm test
npm run build
```

## Licencias de instalación

El control permanece desactivado en desarrollo (`PERA_LICENSE_ENFORCEMENT_ENABLED=false`). Para activar una licencia emitida sin exponer el código en la línea de comandos:

```powershell
.\scripts\activate-license.ps1
```

El script devuelve las cuatro variables que deben guardarse en un gestor de secretos y configurarse en el gateway. Si se activa el control sin credenciales válidas, el gateway falla de forma cerrada.

## Validación

La verificación de cierre ejecuta el reactor Maven completo, las pruebas Vitest, el build de Vite, validación de Docker Compose, migraciones sobre PostgreSQL real y un recorrido HTTP a través del gateway. El contexto canónico, los comandos y las limitaciones actuales están en [`AGENTS.md`](AGENTS.md).

## Documentación

La visión, requisitos, arquitectura, modelo de datos, API y decisiones están en [`docs/`](docs/README.md). El documento funcional recibido se conserva en `docs/reference/` como fuente de contexto, no como contrato definitivo.

La [auditoría de generalización](docs/08-generalizacion-erp-horizontal.md) documenta el cambio a producto horizontal. No se ha borrado ningún modelo heredado: las piezas no transversales se conservan como compatibilidad congelada y quedan fuera de nuevos flujos.

## Principios

- Aislamiento multiempresa en cada agregado mediante `company_id` obtenido del JWT.
- Propiedad de datos por servicio; no hay joins entre bases de datos de servicios.
- Importes con `BigDecimal` y snapshots comerciales en documentos emitidos.
- Migraciones versionadas; Hibernate valida el esquema y no lo crea en producción.
- El núcleo no depende de modelos de un sector concreto; cualquier vertical futuro se integrará como extensión explícita.
- Los módulos financieros avanzados quedan modelados, pero su comportamiento se implementará después del flujo comercial MVP.
