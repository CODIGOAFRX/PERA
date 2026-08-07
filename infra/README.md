# Infraestructura local

El Compose levanta el frontend React, el gateway, los cuatro servicios backend y una instancia PostgreSQL con cuatro bases de datos lógicas, una por servicio propietario. Esto reduce consumo local sin introducir tablas compartidas.

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

La aplicación queda disponible en `http://localhost:5173`. El Nginx del frontend reenvía `/api` al gateway, por lo que el navegador utiliza un único origen. Los puertos 8080–8084 también se exponen para depuración.

Credenciales iniciales del entorno local:

- Usuario: `admin`
- Contraseña: valor de `PERA_BOOTSTRAP_ADMIN_PASSWORD` (`ChangeMe123!` en el ejemplo)

El script de creación de bases solo se ejecuta cuando el volumen está vacío. Para reinicializar datos de desarrollo se debe retirar explícitamente el volumen con `docker compose down -v`; no se debe usar ese comando en entornos con datos que deban conservarse.
