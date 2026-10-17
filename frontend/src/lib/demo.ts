/**
 * Demo pública del portfolio (`npm run build:demo`).
 *
 * La aplicación se sirve dentro de otra web, en `/proyecto/pera-erp/app/`, y su API cuelga de
 * `/proyecto/pera-erp/api/` para no chocar con la `/api` del portfolio. Se entra sin pantalla de
 * acceso, como visitante de una empresa con datos inventados. Fuera de la demo todo vale '' y la
 * aplicación funciona como siempre, en la raíz y con `/api`.
 */
export const DEMO_MODE = import.meta.env.MODE === 'demo'

/** Prefijo de la aplicación en la barra de direcciones, sin barra final ('' en la raíz). */
export const APP_BASE = import.meta.env.BASE_URL.replace(/\/$/, '')

/** Prefijo que se antepone a las rutas `/api/...` de las llamadas al servidor. */
export const API_BASE = (import.meta.env.VITE_API_BASE ?? (DEMO_MODE ? '/proyecto/pera-erp' : '')).replace(/\/$/, '')

/** A dónde vuelve el visitante al salir de la demo: la página del proyecto. */
export const DEMO_EXIT_URL = import.meta.env.VITE_DEMO_EXIT_URL ?? '/proyecto/pera-erp/'
