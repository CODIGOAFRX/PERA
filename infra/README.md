# Infraestructura local

El Compose levanta una instancia PostgreSQL para desarrollo y cuatro bases de datos lógicas, una por servicio propietario. Esto reduce consumo local sin introducir tablas compartidas.

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

Los puertos 8081–8084 se exponen para depuración, pero el frontend debe consumir únicamente el gateway en el puerto 8080.

El script de creación de bases solo se ejecuta cuando el volumen está vacío. Para reinicializar datos de desarrollo se debe retirar explícitamente el volumen con `docker compose down -v`; no se debe usar ese comando en entornos con datos que deban conservarse.
