# 29 · Demo pública del portfolio

PERA tiene una página de proyecto en el portfolio de Pedro (`pedrogomez.dev/proyecto/pera-erp`) y, desde ella, una demo en `pedrogomez.dev/proyecto/pera-erp/app` en la que se entra sin pantalla de acceso, con datos inventados.

## Alcance

- **Empresa propia de la demo.** `VISITA`, «Distribuciones Nevada (demo)», distinta de la `DEMO` de desarrollo para que lo que hagan los visitantes no se mezcle con las pruebas del equipo. La crea identity al arrancar si la demo está activada, con sus roles y el usuario `visitante` (rol Administrador, contraseña aleatoria que nadie conoce).
- **Entrada sin contraseña.** `POST /api/v1/auth/demo` devuelve un token del visitante con la marca `demo: true`. Sin `PERA_DEMO_ENABLED=true` el endpoint responde 404.
- **Límites en la pasarela** (`DemoGuardGlobalFilter`). Con la marca `demo` se puede leer todo y trabajar con los datos (clientes, documentos, cobros, TPV, almacén…), pero la pasarela responde 403 «No disponible en la demo» a las escrituras de:
  - usuarios, roles, empresas y datos/logo de la empresa;
  - conexiones (correo, certificados, B2Brouter) y configuración de Veri*Factu;
  - envío de facturas y presupuestos por correo, remisión a la AEAT y envío a B2Brouter;
  - licencias.
  Los tokens normales no llevan la marca y no se ven afectados.
- **Frontend en modo demo** (`npm run build:demo`, `src/lib/demo.ts`). Se sirve bajo `/proyecto/pera-erp/app/`, llama a la API en `/proyecto/pera-erp/api/` (la `/api` del portfolio es otra cosa), entra solo como visitante, muestra una franja «Demo con datos inventados» y cambia «Cerrar sesión» por «Salir de la demo».
- **Datos inventados** (`scripts/seed-demo-portfolio.mjs`). Una distribuidora de Granada con cinco meses de actividad hasta el día de la carga: 16 clientes con contactos, 5 proveedores, 62 artículos con código de barras, existencias y ubicaciones, compras, unas 55 facturas con Veri*Factu, recibos cobrados, pendientes y uno devuelto, presupuestos en todos los estados, comisiones, reclamaciones, agenda, tickets del día en dos cajas y movimientos del almacén móvil. Se puede relanzar: cada bloque comprueba lo que ya existe.
- **Veri*Factu en la demo.** Activado en pruebas para que cada factura tenga su registro, huella, cadena y QR reales; sin certificado no se remite nada, y el visitante no puede cargar uno.

## Cómo se prueba en local

```bash
scripts/start-local.ps1 -Demo
node scripts/seed-demo-portfolio.mjs
node scripts/build-portfolio-demo.mjs
```

`build-portfolio-demo.mjs` compila el frontend en modo demo y lo copia a `../../portfolio/portfolio-react/proyecto/pera-erp/app` (o a la carpeta que se le pase). En el portfolio, `node scripts/serve-local.mjs` sirve la web en `http://127.0.0.1:8010` con las cabeceras de `vercel.json` y hace de proxy de `/proyecto/pera-erp/api/*` hacia la pasarela local.

Para trabajar en la demo con recarga en caliente: `cd frontend && npm run dev:demo` y abrir `http://localhost:5173/proyecto/pera-erp/app/`.

## Límites y pendiente para publicarla

- **Servidor.** La demo necesita los ocho servicios y PostgreSQL en algún sitio accesible. Cuando lo haya, en `vercel.json` del portfolio falta la reescritura `/proyecto/pera-erp/api/:path*` → `https://<servidor>/api/:path*`. Hasta entonces, la demo publicada muestra «La demo no está disponible ahora mismo».
- **CORS.** La reescritura de Vercel envía el origen del portfolio: en ese servidor, `CORS_ALLOWED_ORIGINS` tiene que incluir `https://www.pedrogomez.dev` y `https://pedrogomez.dev`. El proxy local no lo necesita porque quita el origen.
- **Secretos.** En ese servidor, `PERA_JWT_SECRET`, `PERA_INTERNAL_SERVICE_KEY` y `PERA_BOOTSTRAP_ADMIN_PASSWORD` propios (los valores por defecto son públicos), y `PERA_DEMO_ENABLED=true`.
- **Reinicio de datos.** Los visitantes comparten la misma empresa: lo que uno cree lo ve el siguiente. Falta una tarea que restaure los datos (por ejemplo, cada noche, recrear las bases de una instalación solo de demo y volver a cargar el script). El script fecha los datos respecto al día en que se ejecuta, así que recargarlo los mantiene al día.
- **Abuso.** No hay límite de peticiones por visitante; si la demo se publica, conviene ponerlo delante (Cloudflare o el propio servidor).
