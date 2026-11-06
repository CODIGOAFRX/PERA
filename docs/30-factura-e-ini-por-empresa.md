# 30 — Factura electrónica: un `factura_e.ini` por empresa

Fecha de corte: 7 de noviembre de 2026

Hasta ahora la conexión con la AEAT de cada empresa se cargaba a mano en Configuración › Conexiones (certificado P12 y contraseña, guardados cifrados). En la empresa demo estaba el certificado personal de Raúl, y cada cliente nuevo habría dependido de que alguien subiera su certificado por pantalla.

DimproCristalWin lo resuelve con un `Factura_e.INI` en la raíz de cada instalación, con toda la configuración de ese cliente. PERA hace lo mismo, pero como una instalación de PERA lleva varias empresas, hay **un fichero por empresa**.

## Dónde va

```
config/empresas/<NIF del emisor>/factura_e.ini
config/empresas/<NIF del emisor>/<certificado>.p12
```

- El NIF es el del obligado de Configuración › Veri*Factu, sin espacios ni guiones (p. ej. `B12345678`).
- La carpeta base se cambia con `PERA_FISCAL_CONFIG_DIR` (por defecto `config/empresas`, relativa a la carpeta desde la que arrancan los servicios; con `scripts/start-local.ps1` es la raíz del repositorio).
- **Nada de `config/empresas` se sube a GitHub** salvo la plantilla [`config/empresas/EJEMPLO/factura_e.ini.example`](../config/empresas/EJEMPLO/factura_e.ini.example). El fichero lleva la clave del certificado, igual que el de Dimpro lleva las api keys.
- PERA vuelve a leer los ficheros cada medio minuto (con la remisión automática) y cada vez que se abre Conexiones: no hace falta reiniciar.

## Formato

El mismo que el de Dimpro: secciones entre corchetes y `clave = valor`; las líneas que empiezan por `;`, `#` o `*` son comentarios. Se lee en ANSI, como los de Dimpro, o en UTF-8 si el fichero lo es.

| Sección | Clave | Qué hace |
|---|---|---|
| `[FACTURA_E]` | `modo` | `PROPIO` (directo a la AEAT con el certificado de la empresa), `VERIFACTI` o `B2BROUTER`. Sin la clave, `PROPIO`. |
| | `modotest` | `"1"` pruebas (preproducción de la AEAT), `"0"` producción. Sin la clave, pruebas. |
| | `url_nifs`, `url_vies`, `carpeta_salida`, `risp…` | Se aceptan para que el fichero de Dimpro sirva tal cual; PERA todavía no las usa. |
| `[AEAT]` | `certificado` | Nombre del P12/PFX **dentro de la carpeta de la empresa** (no se admite una ruta fuera de ella). |
| | `clave` | Contraseña del certificado. |
| | `tipo` | `CERTIFICADO` (persona o representante) o `SELLO` (sello de entidad). |
| `[VERIFACTI]` | `url_crea`, `url_status`, `apikey_test`, `apikey_prod` | Las mismas que Dimpro. Para la fase siguiente. |
| `[B2BROUTER]` | `dominio`, `ruta`, `header` | Las mismas que Dimpro. Para la fase siguiente. |

Dimpro lee `[VERIFACTI]` y `[B2BROUTER]`, y un fichero con `[VERI_FACTI]` o `[B2B_ROUTER]` se queda vacío sin avisar (CLAUDE.md de Dimpro, sección 5). En PERA los nombres de sección y de clave no distinguen mayúsculas ni guiones bajos.

## Reglas

- **Si la empresa no tiene fichero, todo sigue como antes**: el certificado cargado en Conexiones.
- **Si lo tiene, manda el fichero**, pero lo guardado en Conexiones no se borra ni se cambia. Al quitar el fichero, la empresa vuelve exactamente a como estaba.
- Con fichero en modo `PROPIO` y completo, se remite aunque la casilla de envíos de Conexiones esté apagada: el fichero es la configuración de esa empresa.
- **El entorno del fichero tiene que coincidir con el de Configuración › Veri*Factu.** Si uno dice pruebas y el otro producción, no se remite nada y Conexiones dice por qué. El entorno de la empresa decide el QR que se imprime; así un fichero mal copiado nunca presenta facturas reales por descuido.
- Si falta el certificado, la clave no lo abre o el modo no existe, no se remite y Conexiones enseña el motivo.
- Si dos empresas tienen el mismo NIF de emisor, no se sabe de cuál es el fichero y no se usa para ninguna.
- En Conexiones, la empresa con fichero ve el fichero, el modo, el entorno y el certificado (titular, NIF, caducidad), y el botón «Comprobar certificado». No puede cargar otro certificado por pantalla: se cambia el fichero.

## Fases

1. **Hecho (esta entrega):** fichero por empresa, modo `PROPIO`.
2. **Pendiente:** modo `VERIFACTI`, como `facturae_auto_verifacti` de Dimpro (alta, consulta de estado y QR de Verifacti con la api key de pruebas o de producción). Con este modo PERA no remitirá a la AEAT por su cuenta: hoy un fichero en `VERIFACTI` deja la empresa sin remitir, y Conexiones lo avisa.
3. **Pendiente:** modo `B2BROUTER` en producción (hoy B2Brouter solo funciona en su sandbox y por pantalla).
4. **Pendiente:** comprobar el NIF de los clientes con `url_nifs` / `url_vies`, como hace Dimpro al facturar.

## Probado

En local, empresa demo (emisor `77022450G`, en pruebas), con un certificado de prueba hecho con `keytool` (nunca el de Raúl):

- Con el fichero: Conexiones da fichero, modo `PROPIO`, pruebas, titular «PRUEBA FACTURA_E INI»; «Comprobar certificado» lo abre; guardar la conexión por pantalla responde 422.
- `modotest = "0"` con la empresa en pruebas: no remite y lo explica.
- Clave mala: «Comprobar certificado» la rechaza.
- Quitando el fichero: vuelve el certificado guardado de antes, con su cifrado sin tocar.
- `git status` no ve la carpeta de la empresa ni el certificado.

No se ha remitido ninguna factura con fichero contra la AEAT: para eso hace falta el certificado real de una empresa y su clave, que solo debe escribir quien la tenga.

Código: `FacturaEIni`, `FiscalProfileService`, y los cambios en `VerifactuRemissionService`, `VerifactuRemissionWorker` y `TestIntegrationService` (sales-service); `FiscalConnections.tsx` en el frontend.
