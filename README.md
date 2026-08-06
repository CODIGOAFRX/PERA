# PERA ERP

PERA es un ERP horizontal para pequeñas y medianas empresas. El backend se construye con Java 21, Spring Boot, PostgreSQL y servicios con límites de dominio explícitos. El frontend React + Vite se abordará en una fase posterior.

## Estado del proyecto

Este primer hito entrega un esqueleto backend compilable con:

- `api-gateway`: entrada única, enrutamiento y validación JWT.
- `identity-service`: empresas, usuarios, roles, permisos y autenticación.
- `master-data-service`: clientes, proveedores, artículos, tarifas, notas y precios por cliente.
- `sales-service`: presupuestos, albaranes, facturas, líneas, conversión y estado de cobro.
- `finance-service`: formas de pago, vencimientos, recibos, remesas, riesgo, movimientos y caja.
- PostgreSQL con una base de datos lógica por servicio en desarrollo local.
- Flyway, Bean Validation, Spring Security, Actuator, OpenAPI y pruebas unitarias de reglas críticas.

## Arranque rápido

Requisitos: JDK 21, Maven 3.9+ y Docker con Compose.

```bash
cp infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/docker-compose.yml up --build
```

Una vez levantado, el gateway escucha en `http://localhost:8080`. Las credenciales de desarrollo iniciales son `admin` y el valor de `PERA_BOOTSTRAP_ADMIN_PASSWORD`.

Para compilar sin Docker:

```bash
mvn -f backend/pom.xml clean verify
```

## Validación del hito

El esqueleto se ha verificado con una compilación limpia de los siete módulos, las pruebas unitarias de importes y vencimientos, y una ejecución real sobre PostgreSQL 17. En esa ejecución se aplicaron las cuatro migraciones Flyway, arrancaron los servicios, se probó el login/JWT, el rechazo sin token, los endpoints protegidos y el enrutamiento del gateway.

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
