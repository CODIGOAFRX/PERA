# 34 — Puesta en marcha de PERA en un equipo nuevo

Fecha de corte: 7 de noviembre de 2026

Qué hay que hacer después de clonar o bajarse la rama para ver todo funcionando: avisos de error por correo, Veri*Factu con la AEAT de pruebas, envío de facturas por correo y series de documentos. Nada de esto va en GitHub porque lleva datos personales y claves; cada uno pone los suyos.

## 1. Bajarse el código y arrancar

```bash
git fetch origin
git checkout raul
git pull
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

PERA queda en http://localhost:5173. Las migraciones de la base de datos se aplican solas al arrancar (Ventas `V16` y `V17`, Actividad `V3` y `V4`) y no borran nada.

## 2. `config/pera.ini` — datos de la instalación

Copia `config/pera.ini.example` como `config/pera.ini`. Lo lee `scripts/start-local.ps1` al arrancar, así que después de cambiarlo hay que volver a arrancar PERA.

```ini
[PRODUCTOR]
; Tu NIF y tu nombre EXACTAMENTE como figuran en Hacienda; si no, la AEAT rechaza las facturas (error 1110).
nif = <tu NIF>
nombre = <APELLIDOS NOMBRE, como en tu certificado>
instalacion = PERA-LOCAL-DEV

[SOPORTE]
; A quién llegan los avisos de error. Uno o varios separados por comas.
correo = raulgil98@proton.me, <correo de Pedro>

[SMTP]
; Solo si la empresa NO tiene el correo configurado en Conexiones › Correo. Si lo tiene, déjalo vacío.
servidor =
puerto = 587
usuario =
clave =
remitente =
seguridad = STARTTLS
```

Sin este fichero PERA funciona igual, pero los avisos de error solo se guardan (no salen por correo) y las facturas llevan un productor de relleno que la AEAT rechaza.

## 3. Certificado para Veri*Factu — `config/empresas/<NIF>/factura_e.ini`

Hace falta un certificado reconocido. La AEAT no da uno de pruebas: el entorno de pruebas usa certificados reales y lo enviado no tiene efecto fiscal. El de persona física de la FNMT es gratis y se saca con su app del móvil. Hay que exportarlo como fichero `.p12` con una contraseña.

1. Crea la carpeta `config/empresas/<tu NIF>/` y mete dentro el `.p12`.
2. Copia ahí `config/empresas/EJEMPLO/factura_e.ini.example` como `factura_e.ini` y rellena:

```ini
[FACTURA_E]
modo = PROPIO
; "1" = pruebas de Hacienda, sin efecto fiscal. No cambiarlo a "0".
modotest = "1"

[AEAT]
certificado = <nombre del fichero>.p12
clave = <contraseña que pusiste al exportarlo>
tipo = CERTIFICADO
```

El resto de la plantilla se deja tal cual. PERA vuelve a leer el fichero sola cada medio minuto.

## 4. En la pantalla de PERA

1. **Configuración › Veri*Factu:**
   - NIF del obligado = el mismo NIF que el nombre de la carpeta.
   - Razón social = tu nombre.
   - Entorno = **Pruebas**.
   - Actívalo.
2. **Conexiones:** tiene que salir «Esta empresa se configura con su fichero factura_e.ini» con tu certificado. Pulsa **Comprobar certificado**.
3. **Conexiones › Correo**, para enviar facturas y avisos de error con el correo de la empresa:
   - Servidor `smtp.gmail.com`, puerto `587`, `STARTTLS`.
   - Usuario y remitente: tu Gmail.
   - Contraseña: una **contraseña de aplicación** de Google, que se crea en Cuenta de Google › Seguridad › Contraseñas de aplicaciones y exige la verificación en dos pasos.
   - Después **Comprobar** y activar los envíos.
4. **Clientes:** para que Hacienda acepte la factura, el cliente necesita NIF, y nombre y NIF tienen que existir en Hacienda. PERA no deja expedir una factura completa a un cliente sin NIF.

## 5. Comprobar que funciona

- **Veri*Factu:** expide una factura a un cliente con NIF. En un minuto, en la pantalla **Veri*Factu**, tiene que salir **Aceptado** con su CSV.
- **Avisos de error:** en el navegador, F12 › Consola, `setTimeout(() => { throw new Error('Prueba') })` e Intro. Sale «Error en la aplicación». Escribe algo y pulsa **Enviar a soporte**: tiene que llegar el correo a los de `[SOPORTE]`.
- **Series de documentos:** en Configuración › Series de documentos crea, por ejemplo, un albarán sin valorar que pase a una factura. Haz un albarán con esa serie, descárgalo (sale sin precios) y pásalo a factura (sale con la serie elegida).
- **Buscador:** Ctrl+K desde cualquier pantalla.

## Qué hay en la rama `raul` respecto a `main`

| Entrega | Qué trae | Documento |
|---|---|---|
| v20261107.Raul.2 | Avisos de error a soporte, buscador general, facturas sin NIF bloqueadas, versión 0.3 y arreglos | [docs/32](32-avisos-de-error-buscador-y-version.md) |
| v20261107.Raul.3 | Series de documentos (parte 1), impresión de albaranes y pedidos, versión 0.4 | [docs/33](33-series-de-documentos.md) |
| v20261107.Raul.4 | Varios correos de soporte separados por comas y esta guía | este documento |
