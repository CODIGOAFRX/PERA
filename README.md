# PERA ERP

PERA es un ERP horizontal para pequeñas y medianas empresas. Usa Java 21 y Spring Boot en el backend, React + TypeScript en el frontend, PostgreSQL como base de datos y servicios con límites de dominio explícitos.

## Estado del proyecto

El MVP actual entrega:

- `api-gateway`: entrada única, enrutamiento y validación JWT.
- `identity-service`: empresas, usuarios, roles, permisos y autenticación.
- `master-data-service`: clientes, proveedores, artículos, tarifas, notas y precios por cliente.
- `sales-service`: presupuestos, albaranes, facturas, líneas, conversión y estado de cobro.
- `finance-service`: formas de pago, vencimientos, recibos, remesas, riesgo, movimientos y caja.
- PostgreSQL con una base de datos lógica por servicio en desarrollo local.
- Flyway, Bean Validation, Spring Security, Actuator, OpenAPI y pruebas unitarias de reglas críticas.
- Una interfaz React responsive para login, resumen, clientes, proveedores, catálogo, ventas y finanzas.

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

## Validación del hito

El MVP se ha verificado con compilaciones de backend y frontend, 38 pruebas backend, 10 pruebas frontend y una ejecución real sobre PostgreSQL 17. El recorrido integrado cubre login/JWT, maestros, presupuesto, albarán, factura, vencimientos, cobro, validaciones 400/422 y enrutamiento del gateway. También se revisó visualmente en escritorio y móvil.

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
