import { Mail } from 'lucide-react'
import { useEffect, useState } from 'react'
import { apiFetch, errorMessage } from '../lib/api'
import { useTranslation } from '../i18n/I18nProvider'
type Status = { status: string; recipient: string | null; error: string | null; sentAt: string | null; connectionEnabled: boolean }
export function DocumentEmail({ id, quote = false, disabled = false, onQueued }: { id: string; quote?: boolean; disabled?: boolean; onQueued?: () => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const path = `/api/v1/${quote ? 'quotes' : 'documents'}/${id}/email`
  const [status, setStatus] = useState<Status | null>(null)
  const [checked, setChecked] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    const refresh = () => { if (document.visibilityState === 'visible') void apiFetch<Status>(path).then(s => { if (active) { setStatus(s); setError('') } }).catch(e => { if (active) setError(errorMessage(e)) }) }
    refresh(); const timer = window.setInterval(refresh, 5000)
    return () => { active = false; window.clearInterval(timer) }
  }, [path])
  const labels: Record<string, string> = { NOT_SENT: c('Sin enviar', 'Not sent'), PENDING: c('En cola', 'Queued'), SENDING: c('Enviando…', 'Sending…'), SENT: c('Aceptado por el servidor SMTP', 'Accepted by the SMTP server'), FAILED: c('No se pudo preparar', 'Preparation failed'), UNKNOWN: c('Entrega pendiente de comprobar', 'Delivery needs checking') }
  async function send(retry = false) {
    setBusy(true); setError('')
    try { setStatus(await apiFetch<Status>(retry ? `${path}/retry` : path, { method: 'POST', ...(retry ? { body: JSON.stringify({ confirmedNotDelivered: checked }) } : {}) })); setChecked(false); onQueued?.() } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  return <section className="document-email" aria-label={c('Envío por correo', 'Email delivery')}>
    <div><strong><Mail size={16} /> {c('Correo del cliente', 'Customer email')}</strong><p>{status?.recipient || c('Se comprobará el correo de la ficha del cliente al enviar.', 'The customer email will be checked when sending.')}</p>{status && <small>{labels[status.status] ?? status.status}</small>}</div>
    {status && !status.connectionEnabled && <p>{c('Un administrador debe activar el correo en Conexiones.', 'An administrator must enable email in Connections.')}</p>}
    <button type="button" className="button button-secondary" disabled={busy || disabled || !status?.connectionEnabled || !['NOT_SENT', 'FAILED'].includes(status.status)} onClick={() => void send()}><Mail size={16} />{c('Enviar por correo', 'Send by email')}</button>
    {status?.status === 'UNKNOWN' && <div><label className="switch-row"><input type="checkbox" checked={checked} onChange={event => setChecked(event.target.checked)} /><span>{c('He comprobado en el servidor SMTP que no se entregó.', 'I checked the SMTP server and confirmed it was not delivered.')}</span></label><button type="button" className="button button-secondary" disabled={!checked || busy || !status.connectionEnabled} onClick={() => void send(true)}>{c('Reintentar envío', 'Retry delivery')}</button></div>}
    {(error || status?.error) && <p className="inline-error" role="alert">{error || status?.error}</p>}
  </section>
}
