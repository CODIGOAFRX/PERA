import { apiUrl, getStoredToken } from './api'

/**
 * Avisos de error a soporte, como la ventana «Error en la Aplicación» de DimproCristalWin: cuando algo
 * falla se enseña al usuario y se manda a soporte con quién, dónde, cuándo y qué versión.
 *
 * - PANTALLA: un fallo del propio navegador (un error de programación en la pantalla).
 * - SERVIDOR: la API ha contestado con un error interno (500). Lleva el código que el servidor ha dejado
 *   en su registro, que es donde está el detalle técnico.
 */
export type ErrorSource = 'PANTALLA' | 'SERVIDOR'

export interface AppErrorEvent {
  source: ErrorSource
  message: string
  errorId?: string
  /** El aviso guardado (para mandarlo después con el comentario) o por qué no se ha podido guardar. */
  saved: Promise<SavedReport>
}

export interface SavedReport { id?: string; message: string }

export function reportError(source: ErrorSource, message: string, detail?: string, errorId?: string): Promise<SavedReport> {
  const screen = window.location.pathname
  // Cada error se manda, aunque se repita: soporte prefiere recibirlos todos.
  const saved = save(source, screen, message, detail, errorId)
  window.dispatchEvent(new CustomEvent<AppErrorEvent>('pera:error', { detail: { source, message, errorId, saved } }))
  return saved
}

async function save(source: ErrorSource, screen: string, message: string, detail?: string, errorId?: string): Promise<SavedReport> {
  const token = getStoredToken()
  // Sin sesión no se puede identificar a nadie: el aviso se queda en la pantalla.
  if (!token) return { message: 'No se ha podido avisar a soporte porque no hay una sesión iniciada.' }
  let companyName: string | undefined
  try { companyName = (JSON.parse(sessionStorage.getItem('pera.auth.company') ?? 'null') as { name?: string } | null)?.name } catch { companyName = undefined }
  try {
    const response = await fetch(apiUrl('/api/v1/error-reports'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        source, companyName, screen: screen.slice(0, 300), appVersion: __APP_VERSION__, errorId,
        message: message.slice(0, 1000), detail: detail?.slice(0, 20000), userAgent: navigator.userAgent.slice(0, 400),
      }),
    })
    if (!response.ok) return { message: 'No se ha podido guardar el aviso.' }
    const body = (await response.json()) as { id: string; message?: string }
    return { id: body.id, message: body.message ?? '' }
  } catch {
    return { message: 'No se ha podido guardar el aviso.' }
  }
}

/**
 * «Enviar a soporte» de la ventana de error, con lo que el usuario estaba haciendo (el «Comentario» de Dimpro).
 * Si nadie lo pulsa, el servidor lo manda solo a los pocos minutos.
 */
export async function sendErrorToSupport(id: string, comment: string): Promise<string> {
  const token = getStoredToken()
  if (!token) return 'No hay una sesión iniciada.'
  try {
    const response = await fetch(apiUrl(`/api/v1/error-reports/${id}/send`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ comment: comment.trim().slice(0, 2000) || null }),
    })
    if (!response.ok) return 'No se ha podido mandar el aviso; se reintentará solo en unos minutos.'
    return ((await response.json()) as { message?: string }).message ?? 'Enviado a soporte.'
  } catch {
    return 'No se ha podido mandar el aviso; se reintentará solo en unos minutos.'
  }
}

/** Errores que no captura ninguna pantalla: fallos de programación y promesas rechazadas sin tratar. */
export function installGlobalErrorReporting() {
  // apiFetch avisa con este evento cuando el servidor contesta con un error interno (ver api.ts).
  window.addEventListener('pera:server-error', (event) => {
    const { path, message, errorId, exception } = (event as CustomEvent<{ path: string; message: string; errorId?: string; exception?: string }>).detail
    void reportError('SERVIDOR', message, `Petición: ${path}${exception ? `
Excepción: ${exception}` : ''}`, errorId)
  })
  window.addEventListener('error', (event) => {
    if (!event.error && !event.message) return
    const error = event.error instanceof Error ? event.error : null
    void reportError('PANTALLA', error?.message ?? event.message, error?.stack ?? `${event.filename}:${event.lineno}:${event.colno}`)
  })
  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    // Los errores de la API ya los enseña cada pantalla; los internos del servidor los avisa apiFetch.
    if (reason instanceof Error && reason.name === 'ApiError') return
    void reportError('PANTALLA', reason instanceof Error ? reason.message : String(reason), reason instanceof Error ? reason.stack : undefined)
  })
}
