import { AlertOctagon } from 'lucide-react'
import { Component, useEffect, useState, type ErrorInfo, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { reportError, sendErrorToSupport, type AppErrorEvent, type SavedReport } from '../lib/errorReport'
import { browserPath } from '../routing/Router'
import { Modal } from './Modal'

/**
 * Ventana «Error en la aplicación», como el formulario ver_error de DimproCristalWin: dice qué ha fallado y el
 * código para buscarlo, deja escribir qué se estaba haciendo (el «Comentario» de Dimpro) y lo manda a soporte.
 * Si se cierra sin enviarlo, el servidor lo manda solo a los pocos minutos.
 */
export function ErrorNotice() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const [current, setCurrent] = useState<AppErrorEvent | null>(null)
  const [saved, setSaved] = useState<SavedReport | null>(null)
  const [comment, setComment] = useState('')
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState('')

  useEffect(() => {
    const onError = (event: Event) => {
      const detail = (event as CustomEvent<AppErrorEvent>).detail
      setCurrent(detail); setSaved(null); setComment(''); setResult(''); setSending(false)
      void detail.saved.then(setSaved)
    }
    window.addEventListener('pera:error', onError)
    return () => window.removeEventListener('pera:error', onError)
  }, [])

  const send = async () => {
    if (!saved?.id) return
    setSending(true)
    setResult(await sendErrorToSupport(saved.id, comment))
    setSending(false)
  }
  const sent = result !== ''

  return <Modal open={current !== null} title={c('Error en la aplicación', 'Application error')} onClose={() => setCurrent(null)}>
    {current && <div className="confirm-dialog error-notice">
      <p className="confirm-message"><AlertOctagon size={20} aria-hidden="true" className="confirm-icon" /><span>{current.source === 'SERVIDOR'
        ? c('El servidor no ha podido completar la operación.', 'The server could not complete the operation.')
        : c('Algo ha fallado en esta pantalla.', 'Something went wrong on this screen.')}</span></p>
      <p className="error-notice-message">{current.message}</p>
      {current.errorId && <p className="error-notice-code">{c('Código de error', 'Error code')}: <code>{current.errorId}</code></p>}
      {saved?.id && !sent && <label className="error-notice-comment">
        <span>{c('¿Qué estabas haciendo? (opcional)', 'What were you doing? (optional)')}</span>
        <textarea value={comment} maxLength={2000} rows={3} onChange={(event) => setComment(event.target.value)} data-autofocus
          placeholder={c('Por ejemplo: estaba guardando una factura de Mercadona', 'For example: I was saving an invoice')} />
      </label>}
      <p className="error-notice-outcome" role="status">{result || (saved ? (saved.id ? c('Se mandará a soporte al enviarlo o, si cierras, en unos minutos.', 'It will be sent to support when you send it or, if you close, in a few minutes.') : saved.message) : c('Guardando el aviso…', 'Saving the report…'))}</p>
      <footer className="form-actions">
        {saved?.id && !sent && <button type="button" className="button button-primary" disabled={sending} onClick={() => void send()}>{sending ? c('Enviando…', 'Sending…') : c('Enviar a soporte', 'Send to support')}</button>}
        <button type="button" className={saved?.id && !sent ? 'button button-ghost' : 'button button-primary'} onClick={() => setCurrent(null)}>{sent || !saved?.id ? c('Aceptar', 'OK') : c('Cerrar', 'Close')}</button>
      </footer>
    </div>}
  </Modal>
}

/** Si una pantalla se rompe al pintarse, se enseña esto en vez de una página en blanco y se avisa a soporte. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean; message: string }> {
  state = { failed: false, message: '' }

  static getDerivedStateFromError(error: unknown) {
    return { failed: true, message: error instanceof Error ? error.message : String(error) }
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    void reportError('PANTALLA', error instanceof Error ? error.message : String(error),
      `${error instanceof Error ? error.stack ?? '' : ''}\n\nComponentes:${info.componentStack ?? ''}`)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return <div className="error-boundary" role="alert">
      <AlertOctagon size={34} aria-hidden="true" />
      <h1>Error en la aplicación</h1>
      <p>Esta pantalla ha fallado y se ha avisado a soporte.</p>
      <p className="error-notice-message">{this.state.message}</p>
      <div className="form-actions">
        <button type="button" className="button button-primary" onClick={() => window.location.assign(window.location.pathname)}>Volver a intentarlo</button>
        <button type="button" className="button button-ghost" onClick={() => window.location.assign(browserPath('/'))}>Ir al inicio</button>
      </div>
    </div>
  }
}
