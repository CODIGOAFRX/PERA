# Infraestructura local

El Compose levanta el frontend React, el gateway, los siete servicios backend y una instancia PostgreSQL con siete bases de datos lógicas, una por servicio propietario. Esto reduce consumo local sin introducir tablas compartidas.

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

La aplicación queda disponible en `http://localhost:5173`. El Nginx del frontend reenvía `/api` al gateway, por lo que el navegador utiliza un único origen. Además del frontend, solo se publican el gateway (8080) y PostgreSQL (`POSTGRES_PORT`); los servicios 8081–8087 quedan dentro de la red de Compose.

Los valores de `.env.example` son marcadores públicos. Sustituye `PERA_JWT_SECRET`, `PERA_INTERNAL_SERVICE_KEY`, `PERA_LICENSE_HASH_PEPPER`, `POSTGRES_PASSWORD` y `PERA_BOOTSTRAP_ADMIN_PASSWORD` por valores propios antes de exponer el entorno fuera de tu equipo.

Credenciales iniciales del entorno local:

- Usuario: `admin`
- Contraseña: valor de `PERA_BOOTSTRAP_ADMIN_PASSWORD` en `infra/.env`. Usa una propia en lugar del valor de `.env.example` y cámbiala desde **Usuarios** tras el primer acceso.

El script de creación de bases solo se ejecuta cuando el volumen está vacío. Para reinicializar datos de desarrollo se debe retirar explícitamente el volumen con `docker compose down -v`; no se debe usar ese comando en entornos con datos que deban conservarse.
