# Decisiones técnicas

## DT-001: Java 21 y Spring Boot 4.1

Java 21 es LTS. [Spring Boot 4.1.0](https://spring.io/projects/spring-boot/) y [Spring Cloud 2025.1.2](https://spring.io/projects/spring-cloud/) son versiones compatibles al crear este esqueleto. Se fija la versión en el POM raíz para que las compilaciones sean reproducibles.

## DT-002: servicios gruesos en monorepo

Se crean cinco despliegues por contextos de negocio. El monorepo simplifica el primer desarrollo compartido de Pedro y Raúl, mantiene un único comando de build y no impide separar repositorios en el futuro.

## DT-003: PostgreSQL, base por servicio

Se descarta una base compartida entre servicios. Compose reutiliza una instancia solo como optimización local. Flyway es la única vía aceptada para cambiar esquemas.

## DT-004: JWT con empresa activa

El tenant se decide al autenticar y queda firmado en el token. HMAC es solo una simplificación local; producción migrará a OIDC o firma asimétrica sin cambiar el contrato de claims.

## DT-005: snapshots en documentos

Una factura debe conservar nombre, código, descripción, precio e impuesto usados al emitirla aunque cambien los maestros. Por eso ventas guarda snapshots y referencias UUID, no relaciones remotas en tiempo de lectura.

## DT-006: outbox antes que broker

Se modela outbox transaccional desde el inicio, pero no se obliga a operar Kafka/RabbitMQ en el MVP. Elegir el broker sin volumen, SLA ni topología confirmados añadiría coste prematuro.

## DT-007: dinero y redondeo

Todos los cálculos usan `BigDecimal`; la escala de persistencia es cuatro. En repartos de vencimientos, el último plazo absorbe el resto para que la suma sea exactamente igual a la factura.

## DT-008: sin Lombok

El esqueleto evita generación implícita de constructores/getters. El modelo queda navegable y compilable sin plugins adicionales de IDE.

## DT-009: convenciones Spring Boot 4

Las migraciones usan `spring-boot-starter-flyway` más el módulo PostgreSQL para conservar la autoconfiguración de Boot 4. El outbox usa Jackson 3 (`tools.jackson`) y la paginación REST se serializa mediante `PagedModel`, evitando contratos JSON dependientes de `PageImpl`.

## DT-010: núcleo horizontal y legado congelado

PERA se orienta a pymes de distintos sectores. El núcleo solo incorporará conceptos transversales y las futuras verticales deberán quedar detrás de módulos y contratos explícitos. Para cumplir la política de no destruir trabajo ni datos, `WorkSite` y `calculation_multiplier` no se eliminan: se marcan como obsoletos, se documentan en el esquema y se prohíbe que nuevas reglas dependan de ellos. `carrier`, `route`, unidades métricas, listas de precios, precios por cliente y partes de trabajo se mantienen porque son útiles en comercio, distribución o servicios y no presuponen cristalería.
